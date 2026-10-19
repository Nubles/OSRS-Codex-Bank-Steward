package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The bank laid down in layers by age, like rock: how many kinds of item moved this week, this
 * month and so on back, and the few that have sat longest at the bottom of the vault. Ages come from
 * the Steward's own watching, so an item that has not moved since watching began is only known to be
 * at least that old, and the layers stop at the one watching has reached.
 *
 * <p>Immutable, so Swing can keep it while the client thread works out the next.</p>
 */
public final class Strata
{
	/** Where each layer starts, in days untouched. */
	static final int[] FROM = {0, 7, 30, 90, 180, 365};
	/** Each layer's name. */
	static final String[] NAMES = {"This week", "This month", "1 to 3 months", "3 to 6 months", "6 to 12 months", "A year or more"};
	/** Each layer's name when it is the deepest watching has reached, so its items may be older still. */
	static final String[] OPEN = {"Today or more", "A week or more", "A month or more", "3 months or more", "6 months or more", "A year or more"};
	/** Untouched this long counts as deep in the vault, for the headline share. */
	public static final int DEEP = 90;
	/** How many of the oldest items are named. */
	public static final int OLDEST = 10;
	/** Coins move all the time and say nothing about the bank's age. */
	private static final int COINS = 995;

	/** One layer. */
	public static final class Layer
	{
		private final String name;
		private final int from;
		private final int count;
		private final boolean open;

		Layer(String name, int from, int count, boolean open)
		{
			this.name = name;
			this.from = from;
			this.count = count;
			this.open = open;
		}

		public String getName()
		{
			return name;
		}

		/** Days untouched where the layer starts. */
		public int getFrom()
		{
			return from;
		}

		/** Kinds of item in it. */
		public int getCount()
		{
			return count;
		}

		/** The deepest layer watching has reached: its items may be older still. */
		public boolean isOpen()
		{
			return open;
		}
	}

	/** One of the longest-untouched items. */
	public static final class Old
	{
		private final int itemId;
		private final long days;
		private final boolean atLeast;

		Old(int itemId, long days, boolean atLeast)
		{
			this.itemId = itemId;
			this.days = days;
			this.atLeast = atLeast;
		}

		public int getItemId()
		{
			return itemId;
		}

		public long getDays()
		{
			return days;
		}

		/** It has not moved since watching began, so it may be older. */
		public boolean isAtLeast()
		{
			return atLeast;
		}
	}

	public static final Strata EMPTY = new Strata(Collections.emptyList(), Collections.emptyList(), 0, 0, 0);

	private final List<Layer> layers;
	private final List<Old> oldest;
	private final int total;
	private final int deep;
	private final long watched;

	private Strata(List<Layer> layers, List<Old> oldest, int total, int deep, long watched)
	{
		this.layers = Collections.unmodifiableList(layers);
		this.oldest = Collections.unmodifiableList(oldest);
		this.total = total;
		this.deep = deep;
		this.watched = watched;
	}

	/**
	 * @param bank the bank as last seen, by item; empty placeholders are left out
	 * @param today the day, as an epoch day
	 */
	public static Strata of(Memory memory, Map<Integer, Long> bank, long today)
	{
		if (memory == null || bank == null || !memory.hasSeenBank())
		{
			return EMPTY;
		}
		long watched = Math.max(0, today - memory.getSince());
		int[] counts = new int[FROM.length];
		int total = 0;
		int deep = 0;
		List<Old> all = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : bank.entrySet())
		{
			int id = e.getKey();
			if (e.getValue() == null || e.getValue() <= 0 || id == COINS)
			{
				continue;
			}
			long days = memory.untouchedDays(id, today);
			counts[layer(days)]++;
			total++;
			if (days >= DEEP)
			{
				deep++;
			}
			all.add(new Old(id, days, memory.lastMoved(id) < 0));
		}
		if (total == 0)
		{
			return EMPTY;
		}
		// Oldest first; among equals, the lower id, so the list holds still from one look to the next.
		all.sort((a, b) -> a.days != b.days ? Long.compare(b.days, a.days) : Integer.compare(a.itemId, b.itemId));
		List<Old> oldest = new ArrayList<>(all.subList(0, Math.min(OLDEST, all.size())));
		int reached = layer(watched);
		List<Layer> layers = new ArrayList<>();
		for (int i = 0; i <= reached; i++)
		{
			boolean open = i == reached && i < FROM.length - 1;
			layers.add(new Layer(open ? OPEN[i] : NAMES[i], FROM[i], counts[i], open));
		}
		return new Strata(layers, oldest, total, deep, watched);
	}

	/** Which layer an age falls in. */
	static int layer(long days)
	{
		int i = FROM.length - 1;
		while (i > 0 && days < FROM[i])
		{
			i--;
		}
		return i;
	}

	/** The layers from newest down, as far as watching reaches. */
	public List<Layer> getLayers()
	{
		return layers;
	}

	/** The longest-untouched items, oldest first. */
	public List<Old> getOldest()
	{
		return oldest;
	}

	/** Kinds of item counted. */
	public int getTotal()
	{
		return total;
	}

	/** Kinds of item untouched for {@link #DEEP} days or more. */
	public int getDeep()
	{
		return deep;
	}

	/** Days the Steward has watched this bank. */
	public long getWatched()
	{
		return watched;
	}

	public boolean isEmpty()
	{
		return total == 0;
	}

	/** Changes whenever anything shown changes, so the panel is only redrawn when it must be. */
	public String fingerprint()
	{
		StringBuilder out = new StringBuilder().append(total).append('/').append(deep).append('/').append(watched);
		for (Layer l : layers)
		{
			out.append(',').append(l.count);
		}
		for (Old o : oldest)
		{
			out.append(';').append(o.itemId).append(':').append(o.days);
		}
		return out.toString();
	}
}
