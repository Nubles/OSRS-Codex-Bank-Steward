package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntToLongFunction;

/**
 * The Steward's reckoning of a bank: what can go because its quests and diaries are done, what
 * has gathered dust, what is still owed to unfinished work, and what the player chose to keep.
 *
 * <p>An item is spent only when a task asks for it, every task naming it in any way is done, it
 * is not a general good or a lasting reward, the stack is no bigger than leftovers from those
 * tasks, and it has not moved within the grace period. An "any of" need of an unfinished task
 * holds one item: the first of its choices the player owns, so a spare pickaxe is not held back
 * because some quest wants a pickaxe. Its other choices are still never called spent.</p>
 *
 * <p>Dust is only called on tradeable items: an untradeable item nothing explains may be a
 * reward that cannot be had again, so the Steward never suggests dropping it on age alone.</p>
 */
public final class Reckoning
{
	/** How the Steward judges; both in days. */
	public static final class Rules
	{
		final int dustDays;
		final int graceDays;

		public Rules(int dustDays, int graceDays)
		{
			this.dustDays = Math.max(1, dustDays);
			this.graceDays = Math.max(0, graceDays);
		}
	}

	private final List<Advice> spent;
	private final List<Advice> dust;
	private final List<Advice> kept;
	private final Map<String, List<Advice>> needed;
	private final long watchedDays;
	private final int dustDays;
	private final int tasksDone;
	private final int tasksTotal;

	private Reckoning(List<Advice> spent, List<Advice> dust, List<Advice> kept, Map<String, List<Advice>> needed,
		long watchedDays, int dustDays, int tasksDone, int tasksTotal)
	{
		this.spent = spent;
		this.dust = dust;
		this.kept = kept;
		this.needed = needed;
		this.watchedDays = watchedDays;
		this.dustDays = dustDays;
		this.tasksDone = tasksDone;
		this.tasksTotal = tasksTotal;
	}

	/**
	 * @param bank item to count, as in the bank now
	 * @param held items carried or worn, which can meet a need in place of the bank's
	 * @param done for each catalog task, whether it is finished
	 * @param price the Grand Exchange price of one
	 * @param tradeable whether an item can be sold
	 */
	public static Reckoning of(Catalog catalog, Memory memory, Map<Integer, Long> bank, Set<Integer> held, boolean[] done,
		IntToLongFunction price, IntPredicate tradeable, Rules rules, long today)
	{
		List<Task> tasks = catalog.getTasks();
		int doneCount = 0;
		for (int i = 0; i < tasks.size() && i < done.length; i++)
		{
			if (done[i])
			{
				doneCount++;
			}
		}

		// Which owned item each unfinished need holds back, and for which tasks.
		Map<Integer, Set<Integer>> holding = new HashMap<>();
		for (Catalog.Need need : catalog.getNeeds())
		{
			if (isDone(done, need.getTask()))
			{
				continue;
			}
			for (int item : need.getItems())
			{
				// A soft mention of a tool or supply would bury the page in food and teleports.
				if (!need.isFirm() && catalog.isGeneral(item))
				{
					continue;
				}
				if (held.contains(item) || bank.getOrDefault(item, 0L) > 0)
				{
					holding.computeIfAbsent(item, k -> new HashSet<>()).add(need.getTask());
					break;
				}
			}
		}

		List<Advice> spent = new ArrayList<>();
		List<Advice> dust = new ArrayList<>();
		List<Advice> kept = new ArrayList<>();
		Map<Integer, List<Advice>> neededByTask = new HashMap<>();
		long watched = memory.hasSeenBank() ? Math.max(0, today - memory.getSince()) : 0;

		for (Map.Entry<Integer, Long> e : bank.entrySet())
		{
			int id = e.getKey();
			long qty = e.getValue();
			if (qty <= 0)
			{
				continue;
			}
			boolean canSell = tradeable.test(id);
			long each = canSell ? Math.max(0, price.applyAsLong(id)) : 0;
			long untouched = memory.untouchedDays(id, today);

			if (memory.isKept(id))
			{
				kept.add(new Advice(id, qty, each, canSell, Advice.Verdict.KEPT, null, untouched));
				continue;
			}
			Set<Integer> wanting = holding.get(id);
			if (wanting != null)
			{
				List<String> names = names(tasks, wanting);
				Advice advice = new Advice(id, qty, each, canSell, Advice.Verdict.NEEDED, names, untouched);
				for (int t : wanting)
				{
					neededByTask.computeIfAbsent(t, k -> new ArrayList<>()).add(advice);
				}
				continue;
			}
			if (isSpent(catalog, memory, id, qty, done, rules, today))
			{
				Set<Integer> served = new HashSet<>();
				for (Catalog.Need need : catalog.needsOf(id))
				{
					if (need.isFirm())
					{
						served.add(need.getTask());
					}
				}
				spent.add(new Advice(id, qty, each, canSell, Advice.Verdict.SPENT, names(tasks, served), untouched));
				continue;
			}
			// An untradeable item nothing explains is not known to be safe to drop, so dust is only
			// ever called on what can be sold back.
			if (canSell && watched >= rules.dustDays && untouched >= rules.dustDays && !catalog.isLasting(id))
			{
				dust.add(new Advice(id, qty, each, canSell, Advice.Verdict.DUST, null, untouched));
			}
		}

		Comparator<Advice> byValue = Comparator.comparingLong(Advice::getValue).reversed()
			.thenComparing(Comparator.comparingLong(Advice::getUntouchedDays).reversed())
			.thenComparingInt(Advice::getItemId);
		spent.sort(byValue);
		dust.sort(byValue);
		kept.sort(byValue);

		// Unfinished tasks in catalog order, each with what it holds back, most valuable first.
		Map<String, List<Advice>> needed = new LinkedHashMap<>();
		for (int t = 0; t < tasks.size(); t++)
		{
			List<Advice> list = neededByTask.get(t);
			if (list != null)
			{
				list.sort(byValue);
				needed.put(tasks.get(t).getName(), Collections.unmodifiableList(list));
			}
		}
		return new Reckoning(Collections.unmodifiableList(spent), Collections.unmodifiableList(dust),
			Collections.unmodifiableList(kept), Collections.unmodifiableMap(needed), watched, rules.dustDays, doneCount, tasks.size());
	}

	/** The most a bank may hold of an item and still be leftovers from the tasks that wanted it. */
	static long leftoverLimit(int largestNeed)
	{
		return largestNeed * 2L + 3;
	}

	private static boolean isSpent(Catalog catalog, Memory memory, int id, long qty, boolean[] done, Rules rules, long today)
	{
		List<Catalog.Need> needs = catalog.needsOf(id);
		if (needs.isEmpty() || catalog.isGeneral(id) || catalog.isLasting(id))
		{
			return false;
		}
		int largest = 0;
		for (Catalog.Need need : needs)
		{
			if (!isDone(done, need.getTask()))
			{
				return false;
			}
			if (need.isFirm())
			{
				largest = Math.max(largest, need.getQuantity());
			}
		}
		// Only named in passing, never asked for: no quest gave it a purpose to have finished.
		if (largest == 0)
		{
			return false;
		}
		// A stack far beyond what any quest asked for was gathered for something else, like skilling.
		if (qty > leftoverLimit(largest))
		{
			return false;
		}
		long last = memory.lastMoved(id);
		return last < 0 || today - last >= rules.graceDays;
	}

	private static boolean isDone(boolean[] done, int task)
	{
		return task >= 0 && task < done.length && done[task];
	}

	private static List<String> names(List<Task> tasks, Set<Integer> indexes)
	{
		List<Integer> sorted = new ArrayList<>(indexes);
		Collections.sort(sorted);
		List<String> names = new ArrayList<>();
		for (int i : sorted)
		{
			names.add(tasks.get(i).getName());
		}
		return names;
	}

	public List<Advice> getSpent()
	{
		return spent;
	}

	public List<Advice> getDust()
	{
		return dust;
	}

	public List<Advice> getKept()
	{
		return kept;
	}

	/** Unfinished task name to the bank items it holds back. */
	public Map<String, List<Advice>> getNeeded()
	{
		return needed;
	}

	/** Days since the Steward began watching this bank. */
	public long getWatchedDays()
	{
		return watchedDays;
	}

	public int getDustDays()
	{
		return dustDays;
	}

	public int getTasksDone()
	{
		return tasksDone;
	}

	public int getTasksTotal()
	{
		return tasksTotal;
	}

	/** What selling everything on the list would bring. */
	public long freedValue()
	{
		long total = 0;
		for (Advice a : spent)
		{
			total += a.getValue();
		}
		for (Advice a : dust)
		{
			total += a.getValue();
		}
		return total;
	}

	/** Bank slots the list would free. */
	public int freedSlots()
	{
		return spent.size() + dust.size();
	}
}
