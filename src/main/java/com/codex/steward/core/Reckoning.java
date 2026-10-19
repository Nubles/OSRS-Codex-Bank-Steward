package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Arrays;
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
	/** How the Steward judges. */
	public static final class Rules
	{
		final int dustDays;
		final int graceDays;
		final boolean guardStash;
		final boolean untradeableDust;

		public Rules(int dustDays, int graceDays)
		{
			this(dustDays, graceDays, true, false);
		}

		/**
		 * @param dustDays days untouched before an item is dusty
		 * @param graceDays days since last use before a done-with item is listed
		 * @param guardStash clue STASH gear is never called done with
		 * @param untradeableDust untradeable items untouched that long are listed apart, with a warning
		 */
		public Rules(int dustDays, int graceDays, boolean guardStash, boolean untradeableDust)
		{
			this.dustDays = Math.max(1, dustDays);
			this.graceDays = Math.max(0, graceDays);
			this.guardStash = guardStash;
			this.untradeableDust = untradeableDust;
		}
	}

	private final List<Advice> spent;
	private final List<Advice> dust;
	private final List<Advice> kept;
	private final List<Advice> spared;
	private final List<Advice> untradeableDust;
	private final List<Advice> outclassed;
	private final Map<String, List<Advice>> needed;
	private final Map<String, List<String>> neededGoals;
	private final long watchedDays;
	private final int dustDays;
	private final int tasksDone;
	private final int tasksTotal;

	private Reckoning(List<Advice> spent, List<Advice> dust, List<Advice> kept, List<Advice> spared, List<Advice> untradeableDust,
		List<Advice> outclassed, Map<String, List<Advice>> needed, Map<String, List<String>> neededGoals, long watchedDays, int dustDays,
		int tasksDone, int tasksTotal)
	{
		this.outclassed = outclassed;
		this.spent = spent;
		this.dust = dust;
		this.kept = kept;
		this.spared = spared;
		this.untradeableDust = untradeableDust;
		this.needed = needed;
		this.neededGoals = neededGoals;
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
		return of(catalog, memory, bank, held, done, price, price, id -> 0, tradeable, rules, Plans.none(), today);
	}

	/**
	 * @param value what one is worth on the list: the Grand Exchange price or high alchemy
	 * @param grandExchange the Grand Exchange price of one
	 * @param highAlchemy what one brings by high alchemy
	 * @param plans kits and goals from the other Codex plugins, which keep their items off the list
	 */
	public static Reckoning of(Catalog catalog, Memory memory, Map<Integer, Long> bank, Set<Integer> held, boolean[] done,
		IntToLongFunction value, IntToLongFunction grandExchange, IntToLongFunction highAlchemy, IntPredicate tradeable,
		Rules rules, Plans plans, long today)
	{
		return of(catalog, memory, bank, held, done, value, grandExchange, highAlchemy, tradeable, rules, plans, Tools.Levels.UNKNOWN, today);
	}

	/**
	 * @param levels Mining and Woodcutting levels, which say which owned tools outclass the rest;
	 *     no tool is called outclassed while they are unknown
	 */
	public static Reckoning of(Catalog catalog, Memory memory, Map<Integer, Long> bank, Set<Integer> held, boolean[] done,
		IntToLongFunction value, IntToLongFunction grandExchange, IntToLongFunction highAlchemy, IntPredicate tradeable,
		Rules rules, Plans plans, Tools.Levels levels, long today)
	{
		Set<Integer> owned = new HashSet<>(bank.keySet());
		owned.addAll(held);
		Map<Tools.Kind, Integer> bestTools = Tools.best(owned, levels);
		List<Task> tasks = catalog.getTasks();
		Map<Integer, List<String>> goalsByTask = plans.goalsByTask(catalog);
		Map<Integer, List<String>> kitsByItem = plans.kitsByItem();
		Set<Integer> pinned = plans.pinnedTasks(catalog);
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
		// General goods that quests on the way to a pinned goal mention, with those goals.
		Map<Integer, List<String>> mentioned = new HashMap<>();
		for (Catalog.Need need : catalog.getNeeds())
		{
			if (isDone(done, need.getTask()))
			{
				continue;
			}
			for (int item : need.getItems())
			{
				// A soft mention of a tool or supply, or a broad "any food" choice, would bury the
				// page in food and teleports; such goods are never called spent anyway.
				if (catalog.isGeneral(item) && (!need.isFirm() || need.getItems().length > Catalog.BROAD))
				{
					// On the way to a pinned goal such a mention still keeps the item from being
					// called dusty, without crowding the Needed page.
					if (pinned.contains(need.getTask()) && need.getItems().length <= Catalog.BROAD)
					{
						for (String goal : goalsByTask.get(need.getTask()))
						{
							List<String> list = mentioned.computeIfAbsent(item, k -> new ArrayList<>());
							if (!list.contains(goal))
							{
								list.add(goal);
							}
						}
					}
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
		List<Advice> spared = new ArrayList<>();
		List<Advice> untradeable = new ArrayList<>();
		List<Advice> outclassed = new ArrayList<>();
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
			long ge = canSell ? Math.max(0, grandExchange.applyAsLong(id)) : 0;
			long alch = Math.max(0, highAlchemy.applyAsLong(id));
			long each = canSell ? Math.max(0, value.applyAsLong(id)) : 0;
			long untouched = memory.untouchedDays(id, today);

			if (memory.isKept(id))
			{
				kept.add(new Advice(id, qty, each, canSell, Advice.Verdict.KEPT, null, untouched).prices(ge, alch));
				continue;
			}
			Set<Integer> wanting = holding.get(id);
			if (wanting != null)
			{
				List<String> names = names(tasks, wanting);
				Advice advice = new Advice(id, qty, each, canSell, Advice.Verdict.NEEDED, names, untouched)
					.prices(ge, alch).claims(goalClaims(goalsByTask, wanting));
				for (int t : wanting)
				{
					neededByTask.computeIfAbsent(t, k -> new ArrayList<>()).add(advice);
				}
				continue;
			}

			List<Claim> kits = new ArrayList<>();
			for (String kit : kitsByItem.getOrDefault(id, Collections.emptyList()))
			{
				kits.add(new Claim(Claim.Kind.KIT, kit));
			}
			int tiers = rules.guardStash ? catalog.stashTiers(id) : 0;
			Claim stash = tiers == 0 ? null : new Claim(Claim.Kind.STASH, tierNames(tiers));

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
				// A kit still takes it, or a clue still asks for it, whatever the quests say.
				List<Claim> keeping = new ArrayList<>(kits);
				if (stash != null)
				{
					keeping.add(stash);
				}
				Advice.Verdict verdict = keeping.isEmpty() ? Advice.Verdict.SPENT : Advice.Verdict.SPARED;
				Advice advice = new Advice(id, qty, each, canSell, verdict, names(tasks, served), untouched).prices(ge, alch).claims(keeping);
				(keeping.isEmpty() ? spent : spared).add(advice);
				continue;
			}
			boolean old = watched >= rules.dustDays && untouched >= rules.dustDays && !catalog.isLasting(id);
			if (!old)
			{
				// A slower tool beside a faster one the player can use. Never while carried or
				// worn, nor when a kit takes it or a clue STASH unit wants it.
				Integer better = Tools.outclassedBy(id, bestTools);
				if (better != null && canSell && !held.contains(id) && kits.isEmpty() && stash == null
					&& !mentioned.containsKey(id))
				{
					outclassed.add(new Advice(id, qty, each, true, Advice.Verdict.OUTCLASSED, null, untouched).prices(ge, alch).better(better));
				}
				continue;
			}
			// An untradeable item nothing explains is not known to be safe to drop, so dust is only
			// ever called on what can be sold back; the rest is listed apart only when asked for.
			if (!canSell && !rules.untradeableDust)
			{
				continue;
			}
			List<Claim> keeping = new ArrayList<>(kits);
			for (String goal : mentioned.getOrDefault(id, Collections.emptyList()))
			{
				keeping.add(new Claim(Claim.Kind.GOAL, goal));
			}
			if (!keeping.isEmpty())
			{
				spared.add(new Advice(id, qty, each, canSell, Advice.Verdict.SPARED, null, untouched).prices(ge, alch).claims(keeping));
				continue;
			}
			// STASH gear can still be dusty: it may be worth a lot, and the row says what it is for.
			Advice advice = new Advice(id, qty, each, canSell, Advice.Verdict.DUST, null, untouched).prices(ge, alch)
				.claims(stash == null ? null : Collections.singletonList(stash));
			(canSell ? dust : untradeable).add(advice);
		}

		Comparator<Advice> byValue = Comparator.comparingLong(Advice::getValue).reversed()
			.thenComparing(Comparator.comparingLong(Advice::getUntouchedDays).reversed())
			.thenComparingInt(Advice::getItemId);
		spent.sort(byValue);
		dust.sort(byValue);
		kept.sort(byValue);
		spared.sort(byValue);
		outclassed.sort(byValue);
		untradeable.sort(Comparator.comparingLong(Advice::getUntouchedDays).reversed().thenComparingInt(Advice::getItemId));

		// Unfinished tasks on the way to a pinned goal first, then the rest in catalog order, each
		// with what it holds back, most valuable first.
		Map<String, List<Advice>> needed = new LinkedHashMap<>();
		Map<String, List<String>> neededGoals = new LinkedHashMap<>();
		for (int pass = 0; pass < 2; pass++)
		{
			for (int t = 0; t < tasks.size(); t++)
			{
				List<Advice> list = neededByTask.get(t);
				List<String> goals = goalsByTask.get(t);
				if (list == null || (pass == 0) != (goals != null))
				{
					continue;
				}
				list.sort(byValue);
				needed.put(tasks.get(t).getName(), Collections.unmodifiableList(list));
				if (goals != null)
				{
					neededGoals.put(tasks.get(t).getName(), Collections.unmodifiableList(goals));
				}
			}
		}
		return new Reckoning(Collections.unmodifiableList(spent), Collections.unmodifiableList(dust),
			Collections.unmodifiableList(kept), Collections.unmodifiableList(spared), Collections.unmodifiableList(untradeable),
			Collections.unmodifiableList(outclassed), Collections.unmodifiableMap(needed), Collections.unmodifiableMap(neededGoals), watched, rules.dustDays, doneCount, tasks.size());
	}

	/** The pinned goals the tasks are on the way to, each once. */
	private static List<Claim> goalClaims(Map<Integer, List<String>> goalsByTask, Set<Integer> tasks)
	{
		List<Integer> sorted = new ArrayList<>(tasks);
		Collections.sort(sorted);
		List<Claim> claims = new ArrayList<>();
		for (int t : sorted)
		{
			for (String goal : goalsByTask.getOrDefault(t, Collections.emptyList()))
			{
				Claim claim = new Claim(Claim.Kind.GOAL, goal);
				if (!claims.contains(claim))
				{
					claims.add(claim);
				}
			}
		}
		return claims;
	}

	/** "medium, hard" for a mask of clue tiers. */
	static String tierNames(int mask)
	{
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < Catalog.CLUE_TIERS.length; i++)
		{
			if ((mask & 1 << i) != 0)
			{
				out.append(out.length() > 0 ? ", " : "").append(Catalog.CLUE_TIERS[i]);
			}
		}
		return out.toString();
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

	/** Items a kit, goal or STASH unit keeps off the list, each with what keeps it. */
	public List<Advice> getSpared()
	{
		return spared;
	}

	/**
	 * Untradeable items untouched past the dust threshold, listed apart only when the player asks.
	 * They are never in the gold or slot totals, since one may be a reward that cannot be had again.
	 */
	public List<Advice> getUntradeableDust()
	{
		return untradeableDust;
	}

	/** Slower skilling tools a faster owned one makes needless, each with the tool that does. */
	public List<Advice> getOutclassed()
	{
		return outclassed;
	}

	/** What the Steward makes of an item in the bank now, or null when it is not in the bank. */
	public Advice adviceFor(int itemId)
	{
		for (List<Advice> list : Arrays.asList(spent, dust, kept, spared, untradeableDust, outclassed))
		{
			for (Advice a : list)
			{
				if (a.getItemId() == itemId)
				{
					return a;
				}
			}
		}
		for (List<Advice> list : needed.values())
		{
			for (Advice a : list)
			{
				if (a.getItemId() == itemId)
				{
					return a;
				}
			}
		}
		return null;
	}

	/** The pinned goals an unfinished task is on the way to; empty when none. */
	public List<String> goalsOf(String task)
	{
		List<String> goals = neededGoals.get(task);
		return goals == null ? Collections.emptyList() : goals;
	}

	/** Unfinished task name to the bank items it holds back, tasks for pinned goals first. */
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

	/**
	 * A hash of everything the panel shows, so an unchanged reckoning is not drawn again. The
	 * inventory changes every tick while skilling, and each change reckons afresh.
	 */
	public long fingerprint()
	{
		long h = 17;
		for (List<Advice> list : Arrays.asList(spent, dust, kept, spared, untradeableDust, outclassed))
		{
			h = h * 31 + list.size();
			for (Advice a : list)
			{
				h = mix(h, a);
			}
		}
		for (Map.Entry<String, List<Advice>> e : needed.entrySet())
		{
			h = h * 31 + e.getKey().hashCode();
			h = h * 31 + goalsOf(e.getKey()).hashCode();
			for (Advice a : e.getValue())
			{
				h = mix(h, a);
			}
		}
		return ((h * 31 + watchedDays) * 31 + dustDays) * 31 + tasksDone;
	}

	private static long mix(long h, Advice a)
	{
		h = h * 31 + a.getItemId();
		h = h * 31 + a.getQuantity();
		h = h * 31 + a.getPrice();
		h = h * 31 + a.getUntouchedDays();
		h = h * 31 + a.getVerdict().ordinal();
		h = h * 31 + a.getGrandExchange();
		h = h * 31 + a.getHighAlchemy();
		h = h * 31 + a.getClaims().hashCode();
		h = h * 31 + a.getBetter();
		return h * 31 + a.getTasks().hashCode();
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
		for (Advice a : outclassed)
		{
			total += a.getValue();
		}
		return total;
	}

	/** Bank slots the list would free. */
	public int freedSlots()
	{
		return spent.size() + dust.size() + outclassed.size();
	}
}
