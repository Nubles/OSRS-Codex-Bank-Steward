package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Empty placeholders worth releasing. A placeholder holds a bank slot for an item that is gone,
 * and it counts against the bank's space like the item did. One is worth releasing when the
 * quests and diaries that wanted its item are all done, or when it has stood empty past the dust
 * time with nothing still owed it.
 *
 * <p>A placeholder is never listed while its item is carried or worn, a Codex Quartermaster kit
 * takes it, an unfinished quest or diary names it, it is a lasting reward (a lost one is worth
 * the reminder), or the player kept it.</p>
 */
public final class Placeholders
{
	/** Why a placeholder can go. */
	public enum Why
	{
		/** Every quest and diary that wanted its item is done. */
		DONE,
		/** It has stood empty past the dust time. */
		IDLE
	}

	/** One placeholder worth releasing. */
	public static final class Line
	{
		private final int itemId;
		private final Why why;
		private final long days;
		private final List<String> tasks;
		private final boolean sinceWatching;

		Line(int itemId, Why why, long days, List<String> tasks, boolean sinceWatching)
		{
			this.itemId = itemId;
			this.why = why;
			this.days = days;
			this.tasks = Collections.unmodifiableList(tasks);
			this.sinceWatching = sinceWatching;
		}

		/** The item the placeholder holds a slot for. */
		public int getItemId()
		{
			return itemId;
		}

		public Why getWhy()
		{
			return why;
		}

		/** Whole days it has stood empty. */
		public long getDays()
		{
			return days;
		}

		/** For {@link Why#DONE}, the quests and diaries its item served. */
		public List<String> getTasks()
		{
			return tasks;
		}

		/** It was already empty when the Steward began watching, so it may be older still. */
		public boolean isSinceWatching()
		{
			return sinceWatching;
		}
	}

	private Placeholders()
	{
	}

	/**
	 * @param held items carried or worn now
	 * @param done for each catalog task, whether it is finished
	 * @return the placeholders worth releasing: those whose work is done first, then the longest empty
	 */
	public static List<Line> old(Catalog catalog, Memory memory, Set<Integer> held, boolean[] done, Plans plans,
		Reckoning.Rules rules, long today)
	{
		Map<Integer, List<String>> kits = plans.kitsByItem();
		Set<Integer> pinned = plans.pinnedTasks(catalog);
		List<Task> tasks = catalog.getTasks();
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : memory.getPlaceholders().entrySet())
		{
			int id = e.getKey();
			if (held.contains(id) || kits.containsKey(id) || memory.isKept(id) || catalog.isLasting(id))
			{
				continue;
			}
			long days = Math.max(0, today - e.getValue());
			boolean sinceWatching = memory.hasSeenBank() && e.getValue() <= memory.getSince();
			List<Catalog.Need> needs = catalog.needsOf(id);
			boolean owed = false;
			boolean asked = false;
			List<String> served = new ArrayList<>();
			for (Catalog.Need need : needs)
			{
				boolean finished = need.getTask() >= 0 && need.getTask() < done.length && done[need.getTask()];
				if (!finished)
				{
					// As on the Needed page, a broad or passing mention of a general good holds
					// nothing back, unless it is on the way to a pinned goal.
					boolean passing = catalog.isGeneral(id) && (!need.isFirm() || need.getItems().length > Catalog.BROAD);
					if (!passing || pinned.contains(need.getTask()) && need.getItems().length <= Catalog.BROAD)
					{
						owed = true;
						break;
					}
					continue;
				}
				if (need.isFirm())
				{
					asked = true;
					String name = tasks.get(need.getTask()).getName();
					if (!served.contains(name))
					{
						served.add(name);
					}
				}
			}
			if (owed)
			{
				continue;
			}
			// A general good's quests finishing says nothing about whether it is still wanted.
			boolean doneWith = asked && !catalog.isGeneral(id) && days >= rules.graceDays;
			if (doneWith)
			{
				lines.add(new Line(id, Why.DONE, days, served, sinceWatching));
			}
			else if (days >= rules.dustDays)
			{
				lines.add(new Line(id, Why.IDLE, days, Collections.emptyList(), sinceWatching));
			}
		}
		lines.sort(Comparator.comparing(Line::getWhy).thenComparing(Comparator.comparingLong(Line::getDays).reversed())
			.thenComparingInt(Line::getItemId));
		return lines;
	}
}
