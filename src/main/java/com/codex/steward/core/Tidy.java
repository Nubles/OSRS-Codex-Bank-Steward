package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bank slots won back without selling anything: potions kept in several dose sizes, which
 * decanting into four-dose potions folds into one slot (two with a part-dose left over).
 *
 * <p>A potion is an item the game lets you drink whose name ends in a dose count from one to
 * four, such as "Prayer potion(3)". Jewellery and other charged items share the bracket but are
 * never drunk, and Barbarian mixes come only in two doses and are never decanted, so neither is
 * counted.</p>
 */
public final class Tidy
{
	private static final Pattern DOSE = Pattern.compile("^(.+?)\\s*\\(([1-4])\\)$");

	/** One potion held in more slots than its doses need. */
	public static final class Decant
	{
		private final String name;
		private final Map<Integer, Integer> doseOf;
		private final Map<Integer, Long> held;
		private final long doses;
		private final int slots;

		Decant(String name, Map<Integer, Integer> doseOf, Map<Integer, Long> held, long doses, int slots)
		{
			this.name = name;
			this.doseOf = doseOf;
			this.held = held;
			this.doses = doses;
			this.slots = slots;
		}

		/** The potion's name without its dose count, such as "Prayer potion". */
		public String getName()
		{
			return name;
		}

		/** Every dose size held, by item id, with how many. */
		public Map<Integer, Long> getHeld()
		{
			return Collections.unmodifiableMap(held);
		}

		/** The item that best stands for the potion: its largest dose size held. */
		public int getIcon()
		{
			int best = -1;
			int bestDose = 0;
			for (int id : held.keySet())
			{
				int dose = doseOf.get(id);
				if (dose > bestDose)
				{
					best = id;
					bestDose = dose;
				}
			}
			return best;
		}

		public long getDoses()
		{
			return doses;
		}

		/** Slots the potion fills now. */
		public int getSlots()
		{
			return slots;
		}

		/** Four-dose potions the doses make, and the doses of the one part-filled potion left. */
		public long getFull()
		{
			return doses / 4;
		}

		public int getLeftover()
		{
			return (int) (doses % 4);
		}

		/** Slots the potion fills once decanted. */
		public int getSlotsAfter()
		{
			return (getFull() > 0 ? 1 : 0) + (getLeftover() > 0 ? 1 : 0);
		}

		public int getFreed()
		{
			return slots - getSlotsAfter();
		}
	}

	private Tidy()
	{
	}

	/**
	 * @param bank item to count, as in the bank now
	 * @param name an item's name, or null when unknown
	 * @param drinkable whether the game offers to drink the item
	 * @return each potion decanting would win slots back from, most slots first
	 */
	public static List<Decant> decants(Map<Integer, Long> bank, IntFunction<String> name, IntPredicate drinkable)
	{
		Map<String, Map<Integer, Long>> byPotion = new TreeMap<>();
		Map<Integer, Integer> doseOf = new TreeMap<>();
		for (Map.Entry<Integer, Long> e : bank.entrySet())
		{
			if (e.getValue() == null || e.getValue() <= 0)
			{
				continue;
			}
			int id = e.getKey();
			String n = name.apply(id);
			if (n == null)
			{
				continue;
			}
			Matcher m = DOSE.matcher(n.trim());
			if (!m.matches())
			{
				continue;
			}
			String base = m.group(1).trim();
			if (base.toLowerCase().endsWith(" mix") || !drinkable.test(id))
			{
				continue;
			}
			doseOf.put(id, Integer.parseInt(m.group(2)));
			byPotion.computeIfAbsent(base, k -> new TreeMap<>()).put(id, e.getValue());
		}
		List<Decant> list = new ArrayList<>();
		for (Map.Entry<String, Map<Integer, Long>> e : byPotion.entrySet())
		{
			long doses = 0;
			for (Map.Entry<Integer, Long> h : e.getValue().entrySet())
			{
				doses += doseOf.get(h.getKey()) * h.getValue();
			}
			Decant d = new Decant(e.getKey(), doseOf, e.getValue(), doses, e.getValue().size());
			if (d.getFreed() > 0)
			{
				list.add(d);
			}
		}
		list.sort(Comparator.comparingInt(Decant::getFreed).reversed().thenComparing(Decant::getName));
		return list;
	}

	/** Slots every decant together wins back. */
	public static int freed(List<Decant> decants)
	{
		int n = 0;
		for (Decant d : decants)
		{
			n += d.getFreed();
		}
		return n;
	}
}
