package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

public class LoadingIconsTest
{
	@Test
	public void aPictureIsWatchedOnlyUntilItArrives()
	{
		// No client thread: the image would need one only if it were asked to call back after it
		// had loaded, which is the loop this guards against.
		AsyncBufferedImage image = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		AtomicInteger repaints = new AtomicInteger();
		LoadingIcons icons = new LoadingIcons(id -> image, Runnable::run);
		for (int i = 0; i < 3; i++)
		{
			assertSame(image, icons.icon(4151, repaints::incrementAndGet));
		}
		assertEquals(0, repaints.get());
		image.loaded();
		assertEquals(3, repaints.get());
		// A repaint asks again; after the picture is in, that must not ask for yet another.
		for (int i = 0; i < 5; i++)
		{
			assertSame(image, icons.icon(4151, repaints::incrementAndGet));
		}
		assertEquals(3, repaints.get());
	}

	@Test
	public void aRowPaintingWhileThePictureLoadsDoesNotPileUpCallbacks()
	{
		AsyncBufferedImage image = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		AtomicInteger repaints = new AtomicInteger();
		LoadingIcons icons = new LoadingIcons(id -> image, Runnable::run);
		for (int i = 0; i < 1_000; i++)
		{
			icons.icon(4151, repaints::incrementAndGet);
		}
		image.loaded();
		assertEquals(64, repaints.get());
	}
}
