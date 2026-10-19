package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A guided clear-out: everything on the list put in the order worth doing, each with the best way
 * to be rid of it. Sell on the Grand Exchange, after its 2% tax; high alch, after the nature rune;
 * or drop it when neither brings anything. The most valuable go first.
 *
 * <p>When the list is valued at high alchemy, as on ironman accounts, nothing is sold: an item is
 * alched when that brings more than the rune costs, and dropped otherwise. Untradeable items are
 * always dropped, since the Steward cannot tell which of them the game lets you alch.</p>
 */
public final class ClearOut
{
	/** How an item is best cleared. */
	public enum Route
	{
		SELL("Sell"),
		ALCH("Alch"),
		DROP("Drop");

		private final String label;

		Route(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	/** The Grand Exchange takes 2% of each sale, at most 5M an item; under 50 gp is free. */
	static final long TAX_CAP = 5_000_000;

	/** One item to clear and how. */
	public static final class Step
	{
		private final Advice advice;
		private final Route route;
		private final long gain;

		Step(Advice advice, Route route, long gain)
		{
			this.advice = advice;
			this.route = route;
			this.gain = gain;
		}

		public Advice getAdvice()
		{
			return advice;
		}

		public Route getRoute()
		{
			return route;
		}

		/** What clearing the whole stack this way brings, after tax or runes. */
		public long getGain()
		{
			return gain;
		}
	}

	private final List<Step> steps;
	private final Map<Route, long[]> totals;

	private ClearOut(List<Step> steps)
	{
		this.steps = Collections.unmodifiableList(steps);
		totals = new EnumMap<>(Route.class);
		for (Route r : Route.values())
		{
			totals.put(r, new long[2]);
		}
		for (Step s : steps)
		{
			long[] t = totals.get(s.route);
			t[0]++;
			t[1] += s.gain;
		}
	}

	/**
	 * @param items what can go: the done-with and dusty lists
	 * @param byAlchemy the list is valued at high alchemy, so nothing is sold
	 * @param naturePrice what a nature rune costs
	 */
	public static ClearOut plan(List<Advice> items, boolean byAlchemy, long naturePrice)
	{
		List<Step> steps = new ArrayList<>();
		for (Advice a : items)
		{
			steps.add(step(a, byAlchemy, naturePrice));
		}
		steps.sort(Comparator.comparingLong(Step::getGain).reversed()
			.thenComparing(Comparator.comparingLong((Step s) -> s.advice.getUntouchedDays()).reversed())
			.thenComparingInt(s -> s.advice.getItemId()));
		return new ClearOut(steps);
	}

	static Step step(Advice a, boolean byAlchemy, long naturePrice)
	{
		long qty = a.getQuantity();
		if (!a.isTradeable())
		{
			return new Step(a, Route.DROP, 0);
		}
		long alch = Math.max(0, a.getHighAlchemy() - Math.max(0, naturePrice));
		long sell = byAlchemy ? 0 : afterTax(a.getGrandExchange());
		if (alch > sell && alch > 0)
		{
			return new Step(a, Route.ALCH, alch * qty);
		}
		if (sell > 0)
		{
			return new Step(a, Route.SELL, sell * qty);
		}
		return new Step(a, Route.DROP, 0);
	}

	/** What one sells for once the Grand Exchange has taken its tax. */
	static long afterTax(long price)
	{
		if (price <= 0)
		{
			return 0;
		}
		return price - Math.min(price / 50, TAX_CAP);
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	public int count(Route route)
	{
		return (int) totals.get(route)[0];
	}

	public long gain(Route route)
	{
		return totals.get(route)[1];
	}

	public long totalGain()
	{
		long total = 0;
		for (long[] t : totals.values())
		{
			total += t[1];
		}
		return total;
	}

	public boolean isEmpty()
	{
		return steps.isEmpty();
	}

	/**
	 * Items of a session still in the bank that the Steward no longer lists: kept since, needed
	 * again, or kept for a plan. They are left off, so the clear-out can finish without them.
	 */
	public static List<Integer> leftOff(Memory.Session session, Reckoning reckoning, List<Integer> cleared)
	{
		List<Integer> off = new ArrayList<>();
		for (Memory.Planned p : session.getItems())
		{
			Advice now = reckoning.adviceFor(p.getItemId());
			if (now != null && !cleared.contains(p.getItemId()) && !listed(now))
			{
				off.add(p.getItemId());
			}
		}
		return off;
	}

	/** On the list to clear: done with, dusty or outclassed. */
	public static boolean listed(Advice a)
	{
		return a.getVerdict() == Advice.Verdict.SPENT || a.getVerdict() == Advice.Verdict.DUST
			|| a.getVerdict() == Advice.Verdict.OUTCLASSED;
	}

	/** The clear-out has nothing left to do: every item is gone or left off, and something went. */
	public static boolean finished(Memory.Session session, List<Integer> cleared, List<Integer> leftOff)
	{
		return !cleared.isEmpty() && cleared.size() + leftOff.size() >= session.getItems().size();
	}

	/**
	 * Which items of a session are gone: out of the bank and not carried. An item withdrawn to sell
	 * is still carried, as a note or not, so it only counts once it has gone to the Exchange.
	 */
	public static List<Integer> cleared(Memory.Session session, Map<Integer, Long> bank, Set<Integer> held)
	{
		List<Integer> gone = new ArrayList<>();
		for (Memory.Planned p : session.getItems())
		{
			if (bank.getOrDefault(p.getItemId(), 0L) <= 0 && !held.contains(p.getItemId()))
			{
				gone.add(p.getItemId());
			}
		}
		return gone;
	}
}
