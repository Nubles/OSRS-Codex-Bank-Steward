package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gear the player has outgrown: a piece in the bank that something else they own beats in the same
 * slot, for every combat style they actually use. The stats are RuneLite's own (ItemManager's item
 * stats); the Steward never keeps a table of its own.
 *
 * <p>A piece is beaten only when the other piece is at least as good on every bonus that counts and
 * better on one: prayer always, and all five defences for any piece but a weapon; for melee, strength and the piece's own best
 * melee attack (a scimitar is judged on slash, a mace on crush); for ranged, ranged attack and
 * ranged strength; for magic, magic attack and magic damage. A weapon must also be at least as fast,
 * and a two-handed weapon never beats a one-handed one, since the shield slot goes with it. The
 * better piece must be one the player has been seen wearing, so the Steward knows they can.</p>
 *
 * <p>Many pieces are worth more than their numbers, so these are never called outgrown, each with
 * the reason it is kept: untradeable pieces; pieces with a special attack or a passive or set effect
 * as the OSRS Wiki lists them; elemental staves, which stand in for runes; pieces aligned with a god
 * of the God Wars Dungeon; pieces with an option of their own, such as Rub or Teleport; pieces a quest,
 * miniquest or diary names, and lasting rewards; anything on the Gear Roadmap savings goal; and
 * anything worn in the last {@link #WORN_DAYS} days.</p>
 */
public final class Outgrown
{
	/** Worn this recently, a piece is still in use, whatever beats it. */
	public static final int WORN_DAYS = 90;

	/** The combat styles the Steward tells apart. */
	public enum Style
	{
		MELEE("melee"),
		RANGED("ranged"),
		MAGIC("magic");

		private final String word;

		Style(String word)
		{
			this.word = word;
		}

		public String word()
		{
			return word;
		}
	}

	/** RuneLite's equipment slots (net.runelite.api.EquipmentInventorySlot). */
	static final int WEAPON = 3;
	static final int SHIELD = 5;
	static final int AMMO = 13;

	/** An item's equipment stats, as RuneLite's ItemEquipmentStats gives them. */
	public static final class Stats
	{
		final int slot;
		final boolean twoHanded;
		final int astab;
		final int aslash;
		final int acrush;
		final int amagic;
		final int arange;
		final int dstab;
		final int dslash;
		final int dcrush;
		final int dmagic;
		final int drange;
		final int str;
		final int rstr;
		/** Magic damage in tenths of a percent, so 2.5% is 25. */
		final int mdmg;
		final int prayer;
		/** Attack speed in ticks; 0 for a piece that is not a weapon. */
		final int aspeed;

		public Stats(int slot, boolean twoHanded, int astab, int aslash, int acrush, int amagic, int arange,
			int dstab, int dslash, int dcrush, int dmagic, int drange, int str, int rstr, float mdmg, int prayer, int aspeed)
		{
			this.slot = slot;
			this.twoHanded = twoHanded;
			this.astab = astab;
			this.aslash = aslash;
			this.acrush = acrush;
			this.amagic = amagic;
			this.arange = arange;
			this.dstab = dstab;
			this.dslash = dslash;
			this.dcrush = dcrush;
			this.dmagic = dmagic;
			this.drange = drange;
			this.str = str;
			this.rstr = rstr;
			this.mdmg = Math.round(mdmg * 10);
			this.prayer = prayer;
			this.aspeed = aspeed;
		}

		public int getSlot()
		{
			return slot;
		}

		/** No bonus at all: worn for looks or for what it does, never for its numbers. */
		boolean isBlank()
		{
			return astab == 0 && aslash == 0 && acrush == 0 && amagic == 0 && arange == 0 && dstab == 0 && dslash == 0
				&& dcrush == 0 && dmagic == 0 && drange == 0 && str == 0 && rstr == 0 && mdmg == 0 && prayer == 0;
		}

		private int bestMelee()
		{
			return Math.max(astab, Math.max(aslash, acrush));
		}

		/** The style a weapon is for: the attack bonus it is strongest in, or null for one with none. */
		Style style()
		{
			int melee = bestMelee();
			if (arange > melee && arange >= amagic && arange > 0)
			{
				return Style.RANGED;
			}
			if (amagic > melee && amagic > arange && amagic > 0)
			{
				return Style.MAGIC;
			}
			return melee > 0 || str > 0 ? Style.MELEE : null;
		}
	}

	/** What the Steward knows of one item, read from RuneLite. */
	public static final class Item
	{
		final String name;
		final boolean tradeable;
		final boolean stackable;
		final List<String> actions;
		final Stats stats;

		/**
		 * @param actions the item's inventory options, as the game names them (nulls allowed)
		 * @param stats its equipment stats, or null when it cannot be worn
		 */
		public Item(String name, boolean tradeable, boolean stackable, String[] actions, Stats stats)
		{
			this.name = name == null ? "" : name;
			this.tradeable = tradeable;
			this.stackable = stackable;
			List<String> list = new ArrayList<>();
			if (actions != null)
			{
				for (String a : actions)
				{
					if (a != null && !a.trim().isEmpty())
					{
						list.add(a.trim());
					}
				}
			}
			this.actions = Collections.unmodifiableList(list);
			this.stats = stats;
		}

		public boolean isTradeable()
		{
			return tradeable;
		}
	}

	/** The style a wielded weapon is for, or null for an item that is not a weapon or has no attack bonus. */
	public static Style styleOfWeapon(Item item)
	{
		return item == null || item.stats == null || item.stats.slot != WEAPON ? null : item.stats.style();
	}

	/** Why a piece is kept, or that it is not. */
	public enum Why
	{
		/** Something the player owns and wears beats it for every style they use. */
		OUTGROWN,
		/** Nothing the player owns beats it. */
		BEST,
		/** Something beats it, but the player has not been seen wearing that piece. */
		BETTER_UNWORN,
		/** Worn within {@link #WORN_DAYS} days. */
		WORN,
		/** Beaten, but carried or worn right now. */
		CARRIED,
		NO_STATS,
		UNTRADEABLE,
		SPECIAL_ATTACK,
		PASSIVE,
		SET,
		RUNES,
		GOD,
		OPTION,
		QUEST,
		LASTING,
		ROADMAP,
		/** The Steward has not yet seen the player fight, so it cannot tell which styles count. */
		NO_STYLES
	}

	/** The Steward's judgement of one piece. */
	public static final class Verdict
	{
		private final Why why;
		private final int better;
		private final String detail;
		private Set<Style> styles = Collections.emptySet();

		public Verdict(Why why, int better, String detail)
		{
			this.why = why;
			this.better = better;
			this.detail = detail;
		}

		Verdict styles(Set<Style> used)
		{
			styles = Collections.unmodifiableSet(EnumSet.copyOf(used));
			return this;
		}

		/** The styles the piece was judged for; empty when it was kept before stats were weighed. */
		public Set<Style> getStyles()
		{
			return styles;
		}

		public Why getWhy()
		{
			return why;
		}

		/** For OUTGROWN and BETTER_UNWORN, the piece that beats it; -1 otherwise. */
		public int getBetter()
		{
			return better;
		}

		/** A word or name the reason refers to: the option, the god, the quest, the days since worn. */
		public String getDetail()
		{
			return detail;
		}

		public boolean isOutgrown()
		{
			return why == Why.OUTGROWN;
		}
	}

	/** What the judgement needs to know of the account. */
	public static final class Account
	{
		final Set<Style> styles;
		/** Item to the epoch day it was last worn, for every piece seen worn. */
		final Map<Integer, Long> worn;
		final long today;
		/** Items on the Gear Roadmap savings goal. */
		final Set<Integer> planned;
		final String plannedName;

		public Account(Set<Style> styles, Map<Integer, Long> worn, long today, Collection<Integer> planned, String plannedName)
		{
			this.styles = styles.isEmpty() ? EnumSet.noneOf(Style.class) : EnumSet.copyOf(styles);
			this.worn = worn;
			this.today = today;
			this.planned = planned == null ? Collections.emptySet() : new HashSet<>(planned);
			this.plannedName = plannedName;
		}
	}

	/** Reads what the Steward knows of an item; null for an id it knows nothing of. */
	public interface Items
	{
		Item get(int itemId);
	}

	/**
	 * Special attack weapons, by the start of their lower-case names: the OSRS Wiki's
	 * "Category:Weapons with Special attacks" (129 pages), less the tools the outclassed-tools list
	 * judges and the Last Man Standing, Deadman and bounty hunter copies, which a bank does not hold.
	 */
	static final String[] SPECIAL_ATTACK = {
		"abyssal bludgeon", "abyssal dagger", "abyssal tentacle", "abyssal whip", "accursed sceptre", "ancient godsword",
		"ancient mace", "arclight", "armadyl crossbow", "armadyl godsword", "bandos godsword", "barrelchest anchor",
		"blisterwood flail", "blue moon spear", "bone dagger", "brine sabre", "burning claws", "crimson kisten", "crystal halberd",
		"dark bow", "darklight", "dawnbringer", "dinh's bulwark", "dorgeshuun crossbow", "dragon 2h sword", "dragon battleaxe",
		"dragon candle dagger", "dragon claws", "dragon crossbow", "dragon dagger", "dragon halberd", "dragon hasta",
		"dragon knife", "dragon longsword", "dragon mace", "dragon scimitar", "dragon spear", "dragon sword", "dragon thrownaxe",
		"dragon warhammer", "dual macuahuitl", "eclipse atlatl", "elder maul", "eldritch nightmare staff", "emberlight",
		"excalibur", "eye of ayak", "granite hammer", "granite maul", "hallowed flail", "heavy ballista", "ivandis flail",
		"keris partisan of corruption", "keris partisan of the sun", "light ballista", "magic comp bow", "magic longbow",
		"magic shortbow", "noxious halberd", "osmumten's fang", "purging staff", "rod of ivandis", "rondache", "rosewood blowpipe",
		"rune claws", "rune thrownaxe", "saradomin godsword", "saradomin sword", "saradomin's blessed sword", "scorching bow",
		"seercull", "soulflame horn", "soulreaper axe", "staff of balance", "staff of light", "staff of the dead", "sunspear",
		"tonalztics of ralos", "toxic blowpipe", "toxic staff of the dead", "ursine chainmace", "vesta's blighted longsword",
		"voidwaker", "volatile nightmare staff", "webweaver bow", "zamorak godsword", "zamorakian hasta", "zamorakian spear",
		"zaryte crossbow", "zorya's tome",
	};

	/**
	 * Pieces with a passive effect, by the start of their lower-case names: the OSRS Wiki's "Passive
	 * effect" page, its weapons, shields, headgear, handwear, amulets and rings. The amulet of fury is
	 * left out: the page says the plain amulet "does not have a passive effect". Set pieces are in
	 * {@link #SETS}.
	 */
	static final String[] PASSIVE = {
		"twisted bow", "zaryte crossbow", "dragon hunter crossbow", "dragon hunter lance", "dragon hunter wand", "craw's bow",
		"webweaver bow", "thammaron's sceptre", "accursed sceptre", "viggora's chainmace", "ursine chainmace", "staff of the dead",
		"staff of light", "toxic staff of the dead", "kodai wand", "sanguinesti staff", "silverlight", "darklight", "arclight",
		"emberlight", "scorching bow", "purging staff", "keris", "wolfbane", "colossal blade", "leaf-bladed battleaxe",
		"osmumten's fang", "tumeken's shadow", "zuriel's staff", "ancient sceptre", "blood ancient sceptre", "ice ancient sceptre",
		"shadow ancient sceptre", "smoke ancient sceptre", "bone mace", "bone shortbow", "bone staff", "barronite mace",
		"granite hammer", "elysian spirit shield", "spectral spirit shield", "dinh's bulwark", "anti-dragon shield",
		"elemental shield", "mind shield", "dragonfire shield", "ancient wyvern shield", "dragonfire ward", "broodoo shield",
		"black mask", "slayer helmet", "serpentine helm", "chaos gauntlets", "regen bracelet", "necklace of faith",
		"phoenix necklace", "amulet of glory", "salve amulet", "berserker necklace", "amulet of the damned", "amulet of avarice",
		"amulet of blood fury", "ring of life", "ring of wealth", "ring of recoil", "ring of suffering", "brimstone ring",
	};

	/** The wiki's blackjacks are named for their wood, so they are found by the word. */
	private static final String BLACKJACK = "blackjack";

	/**
	 * Set pieces, by the start of their lower-case names: the armour sets and equipment sets of the
	 * OSRS Wiki's "Passive effect" page. A Barrows set's effect needs all four pieces, so every piece
	 * of each brother's set is kept. Obsidian weapons are the four the page names with the berserker
	 * necklace (sword, dagger, maul and mace); crystal armour counts with the crystal bow and the bow
	 * of Faerdhinen.
	 */
	static final String[] SETS = {
		"void ", "elite void", "shayzien ", "justiciar ", "inquisitor's ", "virtus ",
		"ahrim's ", "dharok's ", "guthan's ", "karil's ", "torag's ", "verac's ",
		"obsidian ", "toktz-xil-ak", "toktz-xil-ek", "tzhaar-ket-om", "tzhaar-ket-em",
		"crystal helm", "crystal body", "crystal legs", "crystal bow", "bow of faerdhinen",
		"eclipse moon ", "blue moon ", "blood moon ",
	};

	/** Elemental staves, which give unlimited runes: the OSRS Wiki's "Elemental staff" page. */
	static final String[] RUNE_STAVES = {
		"staff of air", "staff of water", "staff of earth", "staff of fire",
		"air battlestaff", "water battlestaff", "earth battlestaff", "fire battlestaff", "lava battlestaff", "mud battlestaff",
		"steam battlestaff", "smoke battlestaff", "mist battlestaff", "dust battlestaff",
		"mystic air staff", "mystic water staff", "mystic earth staff", "mystic fire staff", "mystic lava staff",
		"mystic mud staff", "mystic steam staff", "mystic smoke staff", "mystic mist staff", "mystic dust staff",
		"twinflame staff",
	};

	/**
	 * The gods whose followers in the God Wars Dungeon leave alone a player wearing their gear, as the
	 * OSRS Wiki's "Passive effect" page names them. Gear is matched by the god's name in its own.
	 */
	static final String[] GODS = {"saradomin", "zamorak", "armadyl", "bandos", "zaros"};

	/** Options that only wear, drop or look after a piece; any other is something the piece does. */
	private static final Set<String> PLAIN_OPTIONS = new HashSet<>(Arrays.asList(
		"wield", "wear", "equip", "drop", "destroy", "release", "check", "check-charges", "uncharge", "unload", "dismantle",
		"revert", "remove", "inspect"));

	private Outgrown()
	{
	}

	/** Lower case, with the ornament and charge tails a name can carry kept; matching is by start. */
	private static String lower(String name)
	{
		return name.toLowerCase(Locale.ROOT).replace(' ', ' ');
	}

	private static boolean startsWithAny(String lower, String[] prefixes)
	{
		for (String p : prefixes)
		{
			if (lower.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Judges every piece of equipment in the bank that the outgrown-gear check covers. Ammunition,
	 * stacks such as darts, and the skilling tools the outclassed-tools list judges are left out.
	 *
	 * @param bank the bank's items
	 * @param owned everything the player owns: bank, pack and worn
	 * @param quests for an item, the quests, miniquests or diaries that name it, empty when none
	 * @param lasting whether an item is a lasting reward
	 * @return item to verdict, for each piece judged
	 */
	public static Map<Integer, Verdict> judge(Collection<Integer> bank, Collection<Integer> owned, Items items, Account account,
		java.util.function.IntFunction<List<String>> quests, java.util.function.IntPredicate lasting)
	{
		Map<Integer, Verdict> out = new HashMap<>();
		// The pieces that could beat others: owned, with stats, and worn at some time.
		List<Integer> candidates = new ArrayList<>();
		for (int id : new HashSet<>(owned))
		{
			Item item = items.get(id);
			if (item != null && item.stats != null && !item.stackable)
			{
				candidates.add(id);
			}
		}
		Collections.sort(candidates);
		for (int id : bank)
		{
			Item item = items.get(id);
			if (!judged(id, item))
			{
				continue;
			}
			out.put(id, judgeOne(id, item, candidates, items, account, quests, lasting));
		}
		return out;
	}

	/** Whether a piece is one the outgrown-gear check covers. */
	static boolean judged(int id, Item item)
	{
		if (item == null || item.stats == null || item.stackable || item.stats.slot == AMMO || item.stats.slot < 0)
		{
			return false;
		}
		return !Architect.isTool(id, lower(item.name));
	}

	private static Verdict judgeOne(int id, Item item, List<Integer> candidates, Items items, Account account,
		java.util.function.IntFunction<List<String>> quests, java.util.function.IntPredicate lasting)
	{
		String lower = lower(item.name);
		Long worn = account.worn.get(id);
		if (worn != null && account.today - worn < WORN_DAYS)
		{
			return new Verdict(Why.WORN, -1, String.valueOf(Math.max(0, account.today - worn)));
		}
		if (account.planned.contains(id))
		{
			return new Verdict(Why.ROADMAP, -1, account.plannedName);
		}
		List<String> named = quests.apply(id);
		if (named != null && !named.isEmpty())
		{
			return new Verdict(Why.QUEST, -1, named.get(0));
		}
		if (lasting.test(id))
		{
			return new Verdict(Why.LASTING, -1, null);
		}
		if (!item.tradeable)
		{
			return new Verdict(Why.UNTRADEABLE, -1, null);
		}
		if (startsWithAny(lower, SPECIAL_ATTACK))
		{
			return new Verdict(Why.SPECIAL_ATTACK, -1, null);
		}
		if (startsWithAny(lower, SETS))
		{
			return new Verdict(Why.SET, -1, null);
		}
		if (startsWithAny(lower, PASSIVE) || lower.contains(BLACKJACK))
		{
			return new Verdict(Why.PASSIVE, -1, null);
		}
		if (startsWithAny(lower, RUNE_STAVES))
		{
			return new Verdict(Why.RUNES, -1, null);
		}
		for (String god : GODS)
		{
			if (lower.contains(god))
			{
				return new Verdict(Why.GOD, -1, Character.toUpperCase(god.charAt(0)) + god.substring(1));
			}
		}
		for (String action : item.actions)
		{
			if (!PLAIN_OPTIONS.contains(action.toLowerCase(Locale.ROOT)))
			{
				return new Verdict(Why.OPTION, -1, action);
			}
		}
		if (item.stats.isBlank())
		{
			return new Verdict(Why.NO_STATS, -1, null);
		}
		if (account.styles.isEmpty())
		{
			return new Verdict(Why.NO_STYLES, -1, null);
		}
		int bestWorn = -1;
		int bestUnworn = -1;
		for (int other : candidates)
		{
			if (other == id)
			{
				continue;
			}
			Item o = items.get(other);
			if (o == null || !beats(o.stats, item.stats, account.styles))
			{
				continue;
			}
			if (account.worn.containsKey(other))
			{
				bestWorn = stronger(bestWorn, other, items, account.styles);
			}
			else
			{
				bestUnworn = stronger(bestUnworn, other, items, account.styles);
			}
		}
		if (bestWorn >= 0)
		{
			return new Verdict(Why.OUTGROWN, bestWorn, null).styles(account.styles);
		}
		if (bestUnworn >= 0)
		{
			return new Verdict(Why.BETTER_UNWORN, bestUnworn, null).styles(account.styles);
		}
		return new Verdict(Why.BEST, -1, null).styles(account.styles);
	}

	/** Of two pieces that both beat a third, the one with more of what counts; the lower id on a tie. */
	private static int stronger(int current, int other, Items items, Set<Style> styles)
	{
		if (current < 0)
		{
			return other;
		}
		int a = score(items.get(current).stats, styles);
		int b = score(items.get(other).stats, styles);
		return b > a || b == a && other < current ? other : current;
	}

	private static int score(Stats s, Set<Style> styles)
	{
		int total = s.dstab + s.dslash + s.dcrush + s.dmagic + s.drange + s.prayer;
		if (styles.contains(Style.MELEE))
		{
			total += s.bestMelee() + s.str;
		}
		if (styles.contains(Style.RANGED))
		{
			total += s.arange + s.rstr;
		}
		if (styles.contains(Style.MAGIC))
		{
			total += s.amagic + s.mdmg / 10;
		}
		return total;
	}

	/**
	 * Whether {@code b} beats {@code a} for every style used: the same slot, at least as good on each
	 * bonus that counts, and better on one.
	 */
	static boolean beats(Stats b, Stats a, Set<Style> styles)
	{
		if (b == null || a == null || b.slot != a.slot || styles.isEmpty())
		{
			return false;
		}
		if (a.slot == WEAPON && b.twoHanded && !a.twoHanded)
		{
			return false;
		}
		Compare c = new Compare();
		if (a.slot != WEAPON)
		{
			// A weapon is judged on what it hits with, not the point or two of defence some carry.
			c.at(b.dstab, a.dstab);
			c.at(b.dslash, a.dslash);
			c.at(b.dcrush, a.dcrush);
			c.at(b.dmagic, a.dmagic);
			c.at(b.drange, a.drange);
		}
		c.at(b.prayer, a.prayer);
		if (styles.contains(Style.MELEE))
		{
			c.at(b.str, a.str);
			int best = a.bestMelee();
			if (best > 0)
			{
				// Judged on the piece's own best melee attack: a mace on crush, a scimitar on slash.
				if (a.astab == best)
				{
					c.at(b.astab, a.astab);
				}
				if (a.aslash == best)
				{
					c.at(b.aslash, a.aslash);
				}
				if (a.acrush == best)
				{
					c.at(b.acrush, a.acrush);
				}
			}
		}
		if (styles.contains(Style.RANGED))
		{
			c.at(b.arange, a.arange);
			c.at(b.rstr, a.rstr);
		}
		if (styles.contains(Style.MAGIC))
		{
			c.at(b.amagic, a.amagic);
			c.at(b.mdmg, a.mdmg);
		}
		if (a.slot == WEAPON && a.aspeed > 0 && b.aspeed > 0)
		{
			// Fewer ticks is faster.
			c.at(-b.aspeed, -a.aspeed);
		}
		return c.atLeast && c.better;
	}

	private static final class Compare
	{
		boolean atLeast = true;
		boolean better;

		void at(int b, int a)
		{
			if (b < a)
			{
				atLeast = false;
			}
			else if (b > a)
			{
				better = true;
			}
		}
	}

	/** A few words for a row, like "Kept: special attack". */
	public static String shortText(Verdict v, java.util.function.IntFunction<String> names)
	{
		switch (v.why)
		{
			case OUTGROWN:
				return "Beaten by " + names.apply(v.better);
			case BETTER_UNWORN:
				return names.apply(v.better) + " unworn";
			case BEST:
				return "Best you own";
			case CARRIED:
				return "Carried now";
			case WORN:
				return "0".equals(v.detail) ? "Worn today" : "Worn " + days(v.detail) + " ago";
			case NO_STATS:
				return "No combat stats";
			case UNTRADEABLE:
				return "Untradeable";
			case SPECIAL_ATTACK:
				return "Special attack";
			case PASSIVE:
				return "Passive effect";
			case SET:
				return "Set piece";
			case RUNES:
				return "Gives runes";
			case GOD:
				return v.detail + " gear";
			case OPTION:
				return "Has \"" + v.detail + "\"";
			case QUEST:
				return v.detail;
			case LASTING:
				return "Lasting reward";
			case ROADMAP:
				return "On your roadmap";
			default:
				return "Not seen you fight";
		}
	}

	/** A few lower-case words for counting kept pieces by reason, like "untradeable". */
	public static String groupText(Verdict v)
	{
		switch (v.why)
		{
			case BEST:
				return "the best you own";
			case BETTER_UNWORN:
				return "beaten only by gear not yet worn";
			case WORN:
				return "worn lately";
			case CARRIED:
				return "carried now";
			case NO_STATS:
				return "with no combat stats";
			case UNTRADEABLE:
				return "untradeable";
			case SPECIAL_ATTACK:
				return "with a special attack";
			case PASSIVE:
				return "with a passive effect";
			case SET:
				return "set pieces";
			case RUNES:
				return "giving runes";
			case GOD:
				return "god gear";
			case OPTION:
				return "with options of their own";
			case QUEST:
				return "named by quests";
			case LASTING:
				return "lasting rewards";
			case ROADMAP:
				return "on your roadmap";
			default:
				return "waiting to see you fight";
		}
	}

	/** A sentence for a tooltip that says honestly why the piece stays or goes. */
	public static String longText(Verdict v, java.util.function.IntFunction<String> names)
	{
		Set<Style> styles = v.styles;
		switch (v.why)
		{
			case OUTGROWN:
				return names.apply(v.better) + " is at least as good on every bonus for the way you fight (" + styles(styles)
					+ "), and you have worn it.";
			case BETTER_UNWORN:
				return "Kept because " + names.apply(v.better) + " beats it, but you have not been seen wearing that yet."
					+ " Wear it once and the Steward will know you can.";
			case BEST:
				return "Kept because nothing you own beats it for the way you fight (" + styles(styles) + ").";
			case CARRIED:
				return "Kept for now because you are carrying or wearing it"
					+ (v.better >= 0 ? ", though " + names.apply(v.better) + " beats it" : "") + ".";
			case WORN:
				return "Kept because you wore it " + ("0".equals(v.detail) ? "today" : days(v.detail) + " ago") + ". Gear worn in the last " + WORN_DAYS
					+ " days is still in use.";
			case NO_STATS:
				return "Kept because it has no combat stats: it is worn for how it looks or what it does, not its numbers.";
			case UNTRADEABLE:
				return "Kept because it cannot be traded, so it cannot be bought back if you need it again.";
			case SPECIAL_ATTACK:
				return "Kept because it has a special attack (OSRS Wiki), which a switch may still want.";
			case PASSIVE:
				return "Kept because the OSRS Wiki lists a passive effect for it, which its stats do not show.";
			case SET:
				return "Kept because it is part of a set whose effect needs the whole set (OSRS Wiki).";
			case RUNES:
				return "Kept because it gives unlimited runes while worn (OSRS Wiki).";
			case GOD:
				return "Kept because " + v.detail + " gear can keep that god's followers calm in the God Wars Dungeon (OSRS Wiki).";
			case OPTION:
				return "Kept because it has its own \"" + v.detail + "\" option, so it does more than its stats.";
			case QUEST:
				return "Kept because " + v.detail + " names it.";
			case LASTING:
				return "Kept because it is a lasting reward.";
			case ROADMAP:
				return "Kept because it is on your Gear Roadmap goal" + (v.detail == null ? "" : ", " + v.detail) + ".";
			default:
				return "Kept because the Steward has not yet seen you fight, so it cannot tell which styles you use.";
		}
	}

	private static String days(String detail)
	{
		try
		{
			long d = Long.parseLong(detail);
			return d == 1 ? "1 day" : d + " days";
		}
		catch (NumberFormatException e)
		{
			return "recently";
		}
	}

	/** "melee and ranged". */
	public static String styles(Set<Style> styles)
	{
		List<String> words = new ArrayList<>();
		for (Style s : Style.values())
		{
			if (styles.contains(s))
			{
				words.add(s.word());
			}
		}
		if (words.isEmpty())
		{
			return "no style yet";
		}
		if (words.size() == 1)
		{
			return words.get(0);
		}
		return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
	}
}
