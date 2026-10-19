package com.codex.steward.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.List;
import java.util.Map;
import java.util.function.IntToLongFunction;

/**
 * The upgrade the player is saving for in Codex Gear Roadmap, and how far selling the clear-out
 * list takes them toward it. Only read, never written: Gear Roadmap saves the goal under its RS
 * profile key {@code codexgearroadmap.goal} as {@code {"name":"Toxic blowpipe","ids":[12926]}},
 * one item id per piece, and anything missing or unreadable is simply no goal.
 */
public final class SavingsGoal
{
	/** Gear Roadmap's config group and key. */
	public static final String GROUP = "codexgearroadmap";
	public static final String KEY = "goal";

	private static final int COINS = 995;
	private static final int PLATINUM = 13204;

	/** The fields Gear Roadmap saves; the rest is ignored. */
	private static final class Saved
	{
		private String name;
		private List<Integer> ids;
	}

	private final String name;
	private final long cost;
	private final long coins;
	private final long gain;

	private SavingsGoal(String name, long cost, long coins, long gain)
	{
		this.name = name;
		this.cost = cost;
		this.coins = coins;
		this.gain = gain;
	}

	/**
	 * @param json what Gear Roadmap saved, or null
	 * @param owned every item the player holds, in the bank, pack and worn, with how many
	 * @param price the Grand Exchange price of one
	 * @param gain what clearing the list brings in gold
	 * @return the goal as it stands, or null when there is none, every piece is owned, or nothing is known of its price
	 */
	public static SavingsGoal read(Gson gson, String json, Map<Integer, Long> owned, IntToLongFunction price, long gain)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}
		Saved saved;
		try
		{
			saved = gson.fromJson(json, Saved.class);
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
		if (saved == null || saved.name == null || saved.name.isEmpty() || saved.ids == null || saved.ids.isEmpty())
		{
			return null;
		}
		long cost = 0;
		for (Integer id : saved.ids)
		{
			if (id == null || owned.getOrDefault(id, 0L) > 0)
			{
				continue;
			}
			long each = price.applyAsLong(id);
			if (each <= 0)
			{
				// A piece with no price cannot be saved for; the goal says nothing useful.
				return null;
			}
			cost += each;
		}
		if (cost <= 0)
		{
			return null;
		}
		long coins = owned.getOrDefault(COINS, 0L) + owned.getOrDefault(PLATINUM, 0L) * 1000L;
		return new SavingsGoal(saved.name, cost, coins, Math.max(0, gain));
	}

	public String getName()
	{
		return name;
	}

	/** What the pieces not yet owned cost. */
	public long getCost()
	{
		return cost;
	}

	/** Coins held now, platinum tokens counted at a thousand. */
	public long getCoins()
	{
		return coins;
	}

	/** What clearing the list brings. */
	public long getGain()
	{
		return gain;
	}

	/** Share of the cost the coins cover, 0 to 1. */
	public double coinsShare()
	{
		return Math.min(1, coins / (double) cost);
	}

	/** Share of the cost the coins and the cleared list cover together, 0 to 1. */
	public double withListShare()
	{
		return Math.min(1, (coins + gain) / (double) cost);
	}

	/** The coins already pay for it, list or no list. */
	public boolean isAffordable()
	{
		return coins >= cost;
	}

	/** Clearing the list is what makes it affordable. */
	public boolean listBuysIt()
	{
		return !isAffordable() && coins + gain >= cost;
	}

	/** What is still short once the list is cleared, 0 when nothing is. */
	public long shortAfterList()
	{
		return Math.max(0, cost - coins - gain);
	}

	/** Worth showing: the list moves the goal and the coins alone do not already cover it. */
	public boolean isWorthShowing()
	{
		return gain > 0 && !isAffordable();
	}
}
