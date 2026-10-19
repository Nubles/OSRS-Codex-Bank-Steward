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
		// Each ask here is a new callback, as if from a new row: held to a bound all the same.
		assertEquals(LoadingIcons.MOST_WAITING, repaints.get());
	}

	@Test
	public void everyRowWaitingIsToldHoweverOftenAnotherRepaints()
	{
		// A row passes the same callback each paint; one painting a thousand times while the picture
		// loads must not crowd out another row waiting on the same picture.
		AsyncBufferedImage image = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		AtomicInteger first = new AtomicInteger();
		AtomicInteger second = new AtomicInteger();
		Runnable rowA = first::incrementAndGet;
		Runnable rowB = second::incrementAndGet;
		LoadingIcons icons = new LoadingIcons(id -> image, Runnable::run);
		for (int i = 0; i < 1_000; i++)
		{
			icons.icon(4151, rowA);
		}
		icons.icon(4151, rowB);
		image.loaded();
		assertEquals(1, first.get());
		assertEquals(1, second.get());
	}

	@Test
	public void aPictureHandedOutAfreshForTheSameItemIsWatchedAfresh()
	{
		// The item manager keeps few pictures and hands out a new, blank one for an item it has let
		// go: that one must be watched too, not taken as arrived because the item's first one was.
		AsyncBufferedImage[] current = {new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB)};
		AtomicInteger repaints = new AtomicInteger();
		Runnable row = repaints::incrementAndGet;
		LoadingIcons icons = new LoadingIcons(id -> current[0], Runnable::run);
		icons.icon(4151, row);
		current[0].loaded();
		assertEquals(1, repaints.get());
		current[0] = new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		assertSame(current[0], icons.icon(4151, row));
		current[0].loaded();
		assertEquals(2, repaints.get());
	}
}
