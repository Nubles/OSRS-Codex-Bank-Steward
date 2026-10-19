package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
	/** A clear-out under way, or null. */
	private Session session;
	/** Mining and Woodcutting levels as last read, so tools are judged while logged out; 0 until read. */
	private int mining;
	private int woodcutting;

	/** One item a clear-out set out to clear, as it was when the clear-out began. */
	public static final class Planned
	{
		private int item;
		private long qty;
		private String route;
		private long gain;

		public Planned()
		{
		}

		public Planned(int item, long qty, ClearOut.Route route, long gain)
		{
			this.item = item;
			this.qty = qty;
			this.route = route.name();
			this.gain = gain;
		}

		public int getItemId()
		{
			return item;
		}

		public long getQuantity()
		{
			return qty;
		}

		public ClearOut.Route getRoute()
		{
			try
			{
				return route == null ? ClearOut.Route.DROP : ClearOut.Route.valueOf(route);
			}
			catch (IllegalArgumentException e)
			{
				return ClearOut.Route.DROP;
			}
		}

		public long getGain()
		{
			return gain;
		}
	}

	/** A clear-out the player began: when, and what it set out to clear, in order. */
	public static final class Session
	{
		private long started;
		private List<Planned> items = new ArrayList<>();
		/** The chat has said the clear-out is done. */
		private boolean announced;

		public Session()
		{
		}

		public Session(long started, List<Planned> items)
		{
			this.started = started;
			this.items = new ArrayList<>(items);
		}

		public long getStarted()
		{
			return started;
		}

		public List<Planned> getItems()
		{
			return items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
		}

		public boolean isAnnounced()
		{
			return announced;
		}

		public void setAnnounced(boolean announced)
		{
			this.announced = announced;
		}

		public Planned get(int itemId)
		{
			for (Planned p : getItems())
			{
				if (p.getItemId() == itemId)
				{
					return p;
				}
			}
			return null;
		}
	}

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
		if (session != null && session.items == null)
		{
			session = null;
		}
		if (session != null)
		{
			session.items.removeIf(p -> p == null || p.item <= 0);
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

	/**
	 * Forgets moves of items no longer in the bank. They are of no use: an item coming back to
	 * the bank is marked as moved on the day it returns. Keeps the saved copy from growing with
	 * every item ever carried.
	 */
	public void prune()
	{
		moved.keySet().retainAll(bank.keySet());
	}

	/** The clear-out under way, or null. */
	public Session getSession()
	{
		return session;
	}

	public void setSession(Session session)
	{
		this.session = session;
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

	/** The levels that judge tools, as last read. */
	public Tools.Levels getLevels()
	{
		return new Tools.Levels(mining, woodcutting);
	}

	/** @return whether they changed */
	public boolean setLevels(int mining, int woodcutting)
	{
		if (mining <= 0 || woodcutting <= 0 || this.mining == mining && this.woodcutting == woodcutting)
		{
			return false;
		}
		this.mining = mining;
		this.woodcutting = woodcutting;
		return true;
	}
}
