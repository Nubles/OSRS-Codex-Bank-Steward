package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToLongFunction;

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
	static final String[] OPEN = {"Since watching began", "A week or more", "A month or more", "3 months or more", "6 months or more", "A year or more"};
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
		private final Set<Integer> items;

		Layer(String name, int from, int count, boolean open, Set<Integer> items)
		{
			this.name = name;
			this.from = from;
			this.count = count;
			this.open = open;
			this.items = Collections.unmodifiableSet(items);
		}

		/** The items in it, by id, for showing the layer in the bank. */
		public Set<Integer> getItems()
		{
			return items;
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
		private final long value;

		Old(int itemId, long days, boolean atLeast, long value)
		{
			this.itemId = itemId;
			this.days = days;
			this.atLeast = atLeast;
			this.value = value;
		}

		/** What the stack is worth at the exchange, 0 when it has no price. */
		public long getValue()
		{
			return value;
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
		return of(memory, bank, today, id -> 0);
	}

	/**
	 * @param price each item's exchange price, which ranks items that have lain still equally
	 *     long: the most valuable stack first
	 */
	public static Strata of(Memory memory, Map<Integer, Long> bank, long today, IntToLongFunction price)
	{
		if (memory == null || bank == null || !memory.hasSeenBank())
		{
			return EMPTY;
		}
		long watched = Math.max(0, today - memory.getSince());
		int[] counts = new int[FROM.length];
		List<Set<Integer>> items = new ArrayList<>();
		for (int i = 0; i < FROM.length; i++)
		{
			items.add(new HashSet<>());
		}
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
			items.get(layer(days)).add(id);
			total++;
			if (days >= DEEP)
			{
				deep++;
			}
			if (days > 0)
			{
				// Moved today is not deep, even on the first day when nothing has had time to settle.
				long each = Math.max(0, price.applyAsLong(id));
				long value = each > 0 && e.getValue() > Long.MAX_VALUE / each ? Long.MAX_VALUE : each * e.getValue();
				all.add(new Old(id, days, memory.lastMoved(id) < 0, value));
			}
		}
		if (total == 0)
		{
			return EMPTY;
		}
		// Oldest first. Everything unmoved since watching began ties on age, so among equals the
		// most valuable stack comes first: the gold lying idle is what the deepest are worth
		// knowing for. Then the lower id, so the list holds still from one look to the next.
		all.sort((a, b) -> a.days != b.days ? Long.compare(b.days, a.days)
			: a.value != b.value ? Long.compare(b.value, a.value) : Integer.compare(a.itemId, b.itemId));
		List<Old> oldest = new ArrayList<>(all.subList(0, Math.min(OLDEST, all.size())));
		int reached = layer(watched);
		List<Layer> layers = new ArrayList<>();
		for (int i = 0; i <= reached; i++)
		{
			boolean open = i == reached && i < FROM.length - 1;
			layers.add(new Layer(open ? OPEN[i] : NAMES[i], FROM[i], counts[i], open, items.get(i)));
		}
		return new Strata(layers, oldest, total, deep, watched);
	}

	/**
	 * The items of the layer that starts at {@code from} days, or none when watching has not
	 * reached it: what the bank shows when that layer is clicked.
	 */
	public Set<Integer> itemsFrom(int from)
	{
		for (Layer l : layers)
		{
			if (l.from == from)
			{
				return l.items;
			}
		}
		return Collections.emptySet();
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
			out.append(';').append(o.itemId).append(':').append(o.days).append(':').append(o.value);
		}
		return out.toString();
	}
}
