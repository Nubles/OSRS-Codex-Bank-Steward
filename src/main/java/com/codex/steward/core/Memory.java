package com.codex.steward.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the Steward remembers about one account: the bank as last seen, the day each item last
 * moved, the day it began watching and the items the player chose to keep. Saved per profile.
 *
 * <p>An item has moved when its count in the bank changes, or whenever it is carried or worn.
 * Items already in the bank on the first day have no move of their own; they count as
 * untouched since the Steward began watching.</p>
 */
public final class Memory
{
	/** The epoch day watching began, or -1 before the bank has been seen. */
	private long since = -1;
	private Map<Integer, Long> bank = new HashMap<>();
	private Map<Integer, Long> moved = new HashMap<>();
	private Set<Integer> keep = new HashSet<>();

	/** Fills in anything a saved copy from an older version lacks. */
	public Memory repair()
	{
		if (bank == null)
		{
			bank = new HashMap<>();
		}
		if (moved == null)
		{
			moved = new HashMap<>();
		}
		if (keep == null)
		{
			keep = new HashSet<>();
		}
		return this;
	}

	public boolean hasSeenBank()
	{
		return since >= 0;
	}

	public long getSince()
	{
		return since;
	}

	/** The bank as last seen: item to count. */
	public Map<Integer, Long> getBank()
	{
		return bank;
	}

	/**
	 * Records a look at the bank and marks every item whose count changed since the last look.
	 *
	 * @return true when anything changed
	 */
	public boolean observeBank(Map<Integer, Long> now, long today)
	{
		if (since < 0)
		{
			since = today;
			bank = new HashMap<>(now);
			return true;
		}
		boolean changed = false;
		for (Map.Entry<Integer, Long> e : now.entrySet())
		{
			Long before = bank.get(e.getKey());
			if (before == null || !before.equals(e.getValue()))
			{
				changed |= touch(e.getKey(), today);
			}
		}
		for (Integer id : bank.keySet())
		{
			if (!now.containsKey(id))
			{
				changed |= touch(id, today);
			}
		}
		if (!bank.equals(now))
		{
			bank = new HashMap<>(now);
			changed = true;
		}
		return changed;
	}

	/** Marks items carried or worn as in use today. */
	public boolean observeHeld(Iterable<Integer> items, long today)
	{
		boolean changed = false;
		for (Integer id : items)
		{
			changed |= touch(id, today);
		}
		return changed;
	}

	private boolean touch(int id, long today)
	{
		Long before = moved.put(id, today);
		return before == null || before != today;
	}

	/** The day the item last moved, or -1 if it has not moved while watched. */
	public long lastMoved(int id)
	{
		Long day = moved.get(id);
		return day == null ? -1 : day;
	}

	/** Whole days since the item last moved, counting from the start of watching if it never has. */
	public long untouchedDays(int id, long today)
	{
		long last = lastMoved(id);
		long from = last >= 0 ? last : since;
		return from < 0 ? 0 : Math.max(0, today - from);
	}

	public boolean isKept(int id)
	{
		return keep.contains(id);
	}

	public Set<Integer> getKept()
	{
		return keep;
	}

	public void setKept(int id, boolean kept)
	{
		if (kept)
		{
			keep.add(id);
		}
		else
		{
			keep.remove(id);
		}
	}
}
