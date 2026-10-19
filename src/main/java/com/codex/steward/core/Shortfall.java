package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/**
 * What a pinned Codex Navigator goal still needs that the player does not own: for every
 * unfinished quest or diary on the way to it, each item Quest Helper says to bring, less what
 * the bank and pack already hold. The other half of the Needed page, which lists what the bank
 * holds back for unfinished work.
 *
 * <p>Only firm needs count; an item a quest merely mentions is not something to go and get. A
 * need any of several items can meet is met by whichever is owned, and only counts as short when
 * none is owned in the amount asked; broad choices such as "any food" are left out. When several
 * quests on the way want the same item, the largest amount any one asks for is what is needed,
 * since an item is usually kept from one quest to the next. The quest cape goal covers every
 * quest and would list half the game, so it is left out.</p>
 *
 * <p>Untradeable items are left out and only counted: nearly all are handed out by a quest on
 * the way (the gas mask, Radimus notes, a Dramen staff), so they are not something to buy.</p>
 */
public final class Shortfall
{
	/** At most this many lines per goal are kept; the rest are counted. */
	public static final int MAX_LINES = 200;

	/** One item to go and get. */
	public static final class Line
	{
		private final int itemId;
		private final int needed;
		private final long have;
		private final int choices;
		private final List<String> tasks;
		private final long price;

		Line(int itemId, int needed, long have, int choices, List<String> tasks, long price)
		{
			this.itemId = itemId;
			this.needed = needed;
			this.have = have;
			this.choices = choices;
			this.tasks = Collections.unmodifiableList(tasks);
			this.price = price;
		}

		/** The item to get: the choice already partly owned, else the first Quest Helper names. */
		public int getItemId()
		{
			return itemId;
		}

		public int getNeeded()
		{
			return needed;
		}

		public long getHave()
		{
			return have;
		}

		public long getMissing()
		{
			return Math.max(0, needed - have);
		}

		/** How many items could meet the need; 1 when only this one will do. */
		public int getChoices()
		{
			return choices;
		}

		/** The unfinished quests and diaries that want it, in catalog order. */
		public List<String> getTasks()
		{
			return tasks;
		}

		/** The Grand Exchange price of one; 0 when it has none. */
		public long getPrice()
		{
			return price;
		}

		/** What buying the missing amount costs. */
		public long getCost()
		{
			return price * getMissing();
		}
	}

	/** One goal and what it is short of. */
	public static final class Goal
	{
		private final String name;
		private final List<Line> lines;
		private final int more;
		private final int tasksLeft;
		private final int untradeable;

		Goal(String name, List<Line> lines, int more, int tasksLeft, int untradeable)
		{
			this.name = name;
			this.lines = Collections.unmodifiableList(lines);
			this.more = more;
			this.tasksLeft = tasksLeft;
			this.untradeable = untradeable;
		}

		/** Untradeable items the goal's quests want that are not owned, left off the lines. */
		public int getUntradeable()
		{
			return untradeable;
		}

		public String getName()
		{
			return name;
		}

		/** Most costly first. */
		public List<Line> getLines()
		{
			return lines;
		}

		/** Lines left out past {@link #MAX_LINES}. */
		public int getMore()
		{
			return more;
		}

		/** Unfinished quests and diaries on the way to the goal. */
		public int getTasksLeft()
		{
			return tasksLeft;
		}

		public long getCost()
		{
			long total = 0;
			for (Line l : lines)
			{
				total += l.getCost();
			}
			return total;
		}
	}

	private Shortfall()
	{
	}

	/**
	 * @param owned item to count across the bank, the pack and worn gear
	 * @param price the Grand Exchange price of one
	 * @param tradeable whether an item can be bought
	 */
	public static List<Goal> of(Catalog catalog, Plans plans, boolean[] done, Map<Integer, Long> owned, IntToLongFunction price,
		IntPredicate tradeable)
	{
		List<Goal> out = new ArrayList<>();
		List<Task> tasks = catalog.getTasks();
		for (String key : plans.getGoals())
		{
			if (key.startsWith("cape:"))
			{
				continue;
			}
			boolean[] open = new boolean[tasks.size()];
			int left = 0;
			for (int t : catalog.goalTasks(key))
			{
				if (t >= 0 && t < tasks.size() && !(t < done.length && done[t]) && !open[t])
				{
					open[t] = true;
					left++;
				}
			}
			if (left == 0)
			{
				continue;
			}
			Map<Integer, int[]> largest = new LinkedHashMap<>();
			Map<Integer, List<String>> wantedBy = new LinkedHashMap<>();
			Map<Integer, Integer> choices = new LinkedHashMap<>();
			Map<Integer, Long> have = new LinkedHashMap<>();
			for (Catalog.Need need : catalog.getNeeds())
			{
				int t = need.getTask();
				int[] items = need.getItems();
				if (!need.isFirm() || t < 0 || t >= open.length || !open[t] || items.length == 0 || items.length > Catalog.BROAD)
				{
					continue;
				}
				// Whichever choice is owned in the largest amount stands for the need.
				int pick = items[0];
				long best = 0;
				for (int item : items)
				{
					long n = owned.getOrDefault(item, 0L);
					if (n > best)
					{
						best = n;
						pick = item;
					}
				}
				if (best >= need.getQuantity())
				{
					continue;
				}
				int[] q = largest.computeIfAbsent(pick, k -> new int[1]);
				q[0] = Math.max(q[0], need.getQuantity());
				have.put(pick, best);
				choices.merge(pick, items.length, Math::max);
				List<String> by = wantedBy.computeIfAbsent(pick, k -> new ArrayList<>());
				String name = tasks.get(t).getName();
				if (!by.contains(name))
				{
					by.add(name);
				}
			}
			List<Line> lines = new ArrayList<>();
			int untradeable = 0;
			for (Map.Entry<Integer, int[]> e : largest.entrySet())
			{
				int id = e.getKey();
				if (!tradeable.test(id))
				{
					untradeable++;
					continue;
				}
				lines.add(new Line(id, e.getValue()[0], have.get(id), choices.get(id), wantedBy.get(id), Math.max(0, price.applyAsLong(id))));
			}
			lines.sort((a, b) -> a.getCost() != b.getCost() ? Long.compare(b.getCost(), a.getCost()) : Integer.compare(a.getItemId(), b.getItemId()));
			int more = Math.max(0, lines.size() - MAX_LINES);
			out.add(new Goal(Plans.goalName(key), new ArrayList<>(lines.subList(0, Math.min(lines.size(), MAX_LINES))), more, left, untradeable));
		}
		return out;
	}
}
