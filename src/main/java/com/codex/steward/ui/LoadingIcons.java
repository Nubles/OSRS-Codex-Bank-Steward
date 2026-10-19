package com.codex.steward.ui;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.function.IntFunction;
import javax.swing.SwingUtilities;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Item pictures from RuneLite's item manager, with the callback handed over only until the picture
 * arrives. Once an image has loaded, {@link AsyncBufferedImage#onLoaded} runs a callback again for
 * every call, so a row asking for its icon while it paints, with a repaint to follow, would paint
 * forever. Here a picture is watched once; every row that asked before it arrived is told when it
 * does, and a row asking after is just handed the picture.
 *
 * <p>A row should hand over the same callback each time it asks, such as a repaint kept in a field,
 * so a row painting many times while a picture loads is told once and never crowds out another.</p>
 *
 * <p>Asked on the Swing thread, where the callbacks also run.</p>
 */
public final class LoadingIcons implements ItemIcons
{
	private final IntFunction<AsyncBufferedImage> source;
	private final Executor swing;
	/** Pictures that have arrived; weak, so the item manager's cache decides how long they live. */
	private final Set<AsyncBufferedImage> arrived = Collections.newSetFromMap(new WeakHashMap<>());
	/** For each picture still loading, the callbacks waiting on it. */
	private final Map<AsyncBufferedImage, Set<Runnable>> waiting = new WeakHashMap<>();
	/** Most callbacks kept for one picture, so one that never arrives cannot gather them without end. */
	static final int MOST_WAITING = 256;

	public LoadingIcons(IntFunction<AsyncBufferedImage> source)
	{
		this(source, SwingUtilities::invokeLater);
	}

	LoadingIcons(IntFunction<AsyncBufferedImage> source, Executor swing)
	{
		this.source = source;
		this.swing = swing;
	}

	@Override
	public BufferedImage icon(int itemId, Runnable onLoaded)
	{
		AsyncBufferedImage image = source.apply(itemId);
		if (image == null || onLoaded == null || arrived.contains(image))
		{
			return image;
		}
		Set<Runnable> queue = waiting.get(image);
		if (queue == null)
		{
			Set<Runnable> fresh = new LinkedHashSet<>();
			waiting.put(image, fresh);
			queue = fresh;
			// Runs on the client thread when the picture arrives; the rows hear of it on Swing's.
			image.onLoaded(() -> swing.execute(() ->
			{
				arrived.add(image);
				Set<Runnable> told = waiting.remove(image);
				if (told != null)
				{
					told.forEach(Runnable::run);
				}
			}));
		}
		// A row painting while the picture loads asks each frame; it need only be told once.
		if (queue.size() < MOST_WAITING)
		{
			queue.add(onLoaded);
		}
		return image;
	}
}
