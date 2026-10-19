package com.codex.steward.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Skilling tools that a better one makes needless: pickaxes, woodcutting axes and Forestry's
 * felling axes. A tool is outclassed when the player owns a faster one of the same kind that
 * their level lets them use. Speeds and levels are from the OSRS Wiki.
 *
 * <p>Cosmetic and trophy versions (gilded, 3rd age, the dragon pickaxe's ornament kit) count
 * as the tool they work as, so they can outclass others, but are never called outclassed
 * themselves: their worth is not their speed. Untradeable tools are never called outclassed
 * either, since what cannot be sold may not come back.</p>
 */
public final class Tools
{
	/** A family of tools that stand in for one another. */
	public enum Kind
	{
		PICKAXE,
		AXE,
		FELLING_AXE
	}

	/** One tool: its kind, speed tier and the skill level that uses it. */
	static final class Tool
	{
		final Kind kind;
		final int tier;
		final int level;
		final boolean trophy;

		Tool(Kind kind, int tier, int level, boolean trophy)
		{
			this.kind = kind;
			this.tier = tier;
			this.level = level;
			this.trophy = trophy;
		}
	}

	/** Levels that use the tools; 0 for a level not yet read. */
	public static final class Levels
	{
		public static final Levels UNKNOWN = new Levels(0, 0);

		final int mining;
		final int woodcutting;

		public Levels(int mining, int woodcutting)
		{
			this.mining = mining;
			this.woodcutting = woodcutting;
		}

		boolean known()
		{
			return mining > 0 && woodcutting > 0;
		}

		int of(Kind kind)
		{
			return kind == Kind.PICKAXE ? mining : woodcutting;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Levels && ((Levels) o).mining == mining && ((Levels) o).woodcutting == woodcutting;
		}

		@Override
		public int hashCode()
		{
			return mining * 127 + woodcutting;
		}
	}

	/** The level each tier needs: bronze and iron 1, steel 6, black 11, mithril 21, adamant 31, rune 41, dragon 61, crystal 71. */
	private static final int[] LEVELS = {0, 1, 1, 6, 11, 21, 31, 41, 61, 71};

	private static final Map<Integer, Tool> TOOLS = new HashMap<>();

	static
	{
		// Tier: bronze 1, iron 2, steel 3, black 4, mithril 5, adamant 6, rune 7, dragon 8 (infernal
		// works at dragon speed), crystal 9.
		tools(Kind.PICKAXE, ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_PICKAXE, ItemID.BLACK_PICKAXE,
			ItemID.MITHRIL_PICKAXE, ItemID.ADAMANT_PICKAXE, ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE, ItemID.CRYSTAL_PICKAXE);
		// A black pickaxe swings every 5 ticks, as a mithril one does: neither outclasses the other.
		TOOLS.put(ItemID.BLACK_PICKAXE, new Tool(Kind.PICKAXE, 5, LEVELS[4], false));
		tools(Kind.AXE, ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE,
			ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE, ItemID.RUNE_AXE, ItemID.DRAGON_AXE, ItemID.CRYSTAL_AXE);
		tools(Kind.FELLING_AXE, ItemID.BRONZE_AXE_2H, ItemID.IRON_AXE_2H, ItemID.STEEL_AXE_2H, ItemID.BLACK_AXE_2H,
			ItemID.MITHRIL_AXE_2H, ItemID.ADAMANT_AXE_2H, ItemID.RUNE_AXE_2H, ItemID.DRAGON_AXE_2H, ItemID.CRYSTAL_AXE_2H);
		// Variants that work at another tool's speed. Infernal and crystal tools with no charge left
		// still cut and mine as a dragon tool does.
		trophy(Kind.PICKAXE, 7, ItemID.TRAIL_GILDED_PICKAXE);
		trophy(Kind.PICKAXE, 8, ItemID.DRAGON_PICKAXE_PRETTY, ItemID._3A_PICKAXE, ItemID.INFERNAL_PICKAXE,
			ItemID.INFERNAL_PICKAXE_EMPTY, ItemID.CRYSTAL_PICKAXE_INACTIVE);
		trophy(Kind.AXE, 7, ItemID.TRAIL_GILDED_AXE);
		trophy(Kind.AXE, 8, ItemID._3A_AXE, ItemID.INFERNAL_AXE, ItemID.INFERNAL_AXE_EMPTY, ItemID.CRYSTAL_AXE_INACTIVE);
		trophy(Kind.FELLING_AXE, 8, ItemID._3A_AXE_2H, ItemID.CRYSTAL_AXE_2H_INACTIVE);
	}

	private static void tools(Kind kind, int... ids)
	{
		for (int i = 0; i < ids.length; i++)
		{
			TOOLS.put(ids[i], new Tool(kind, i + 1, LEVELS[i + 1], false));
		}
	}

	private static void trophy(Kind kind, int tier, int... ids)
	{
		for (int id : ids)
		{
			TOOLS.put(id, new Tool(kind, tier, LEVELS[tier], true));
		}
	}

	private Tools()
	{
	}

	/** The kind of tool an item is, or null when it is none. */
	public static Kind kindOf(int itemId)
	{
		Tool t = TOOLS.get(itemId);
		return t == null ? null : t.kind;
	}

	/**
	 * For each kind, the fastest tool owned that the player's level can use: the tool that makes
	 * slower ones needless. Empty when the levels are not known.
	 */
	public static Map<Kind, Integer> best(Collection<Integer> owned, Levels levels)
	{
		Map<Kind, Integer> best = new HashMap<>();
		if (!levels.known())
		{
			return best;
		}
		for (int id : owned)
		{
			Tool t = TOOLS.get(id);
			if (t == null || levels.of(t.kind) < t.level)
			{
				continue;
			}
			Integer current = best.get(t.kind);
			Tool c = current == null ? null : TOOLS.get(current);
			// Of two at the same speed, the working tool over the trophy, then the lower id, so the
			// answer does not depend on iteration order.
			if (c == null || t.tier > c.tier || t.tier == c.tier && (c.trophy && !t.trophy || c.trophy == t.trophy && id < current))
			{
				best.put(t.kind, id);
			}
		}
		return best;
	}

	/**
	 * The owned tool that outclasses this one, or null when it is not a tool, is a trophy, or
	 * nothing owned is strictly faster and usable.
	 */
	public static Integer outclassedBy(int itemId, Map<Kind, Integer> best)
	{
		Tool t = TOOLS.get(itemId);
		if (t == null || t.trophy)
		{
			return null;
		}
		Integer better = best.get(t.kind);
		return better != null && TOOLS.get(better).tier > t.tier ? better : null;
	}
}
