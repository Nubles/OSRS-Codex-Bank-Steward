package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import net.runelite.api.gameval.ItemID;

/**
 * Bank Architect: designs a whole tidy bank from what the Steward knows, as RuneLite Bank Tags
 * tag tabs with layouts the player imports. Nothing in the game is moved: the design is a set of
 * tabs, each a 2D layout eight items wide, the way the bank draws them.
 *
 * <p>Every item has one home tab: what the clear-out lists goes to Sell, teleport jewellery and
 * tablets to Teleports, combat gear to Melee, Ranged or Magic by its strongest bonus, what an
 * unfinished quest or diary needs to Quests, food, potions and runes to Supplies, tools and
 * resources to Skilling and what is left to Keepsakes. Codex Quartermaster's kits get a tab of
 * their own on top, each kit a block four wide, the shape of the inventory, in the kit's order.</p>
 *
 * <p>Bank Tags reads a tab as {@code banktags,1,<name>,<icon item>,layout,<slot>,<item>,...}
 * (its TabInterface's import and export); {@link Tab#export()} writes exactly that.</p>
 */
public final class Architect
{
	/** Items in a bank row, as Bank Tags lays them out (BankTagsPlugin.BANK_ITEMS_PER_ROW). */
	public static final int COLUMNS = 8;
	/** The inventory's width: a kit is laid out in blocks this wide. */
	public static final int KIT_WIDTH = 4;
	/** Starts every tag this design makes, so it never lands on a tab the player already has. */
	public static final String PREFIX = "codex ";

	/** What the Architect needs to know about one item. */
	public static final class Facts
	{
		/** Not equipped anywhere. */
		public static final int NO_SLOT = -1;

		final String name;
		final boolean tradeable;
		final int slot;
		final int melee;
		final int ranged;
		final int magic;
		final int defence;
		final boolean food;
		final boolean potion;

		/**
		 * @param slot the equipment slot (as {@code EquipmentInventorySlot}), or {@link #NO_SLOT}
		 * @param melee best melee attack bonus plus melee strength
		 * @param ranged ranged attack plus ranged strength
		 * @param magic magic attack plus magic damage
		 * @param defence every defence bonus plus prayer, together
		 * @param food eaten
		 * @param potion drunk
		 */
		public Facts(String name, boolean tradeable, int slot, int melee, int ranged, int magic, int defence, boolean food, boolean potion)
		{
			this.name = name == null ? "" : name;
			this.tradeable = tradeable;
			this.slot = slot;
			this.melee = melee;
			this.ranged = ranged;
			this.magic = magic;
			this.defence = defence;
			this.food = food;
			this.potion = potion;
		}

		/** A plain item that is not worn, eaten or drunk. */
		public static Facts item(String name, boolean tradeable)
		{
			return new Facts(name, tradeable, NO_SLOT, 0, 0, 0, 0, false, false);
		}

		boolean equipable()
		{
			return slot >= 0;
		}

		/**
		 * The combat style the item serves best, or null when it gives no combat bonus. Armour with
		 * defence but no attack bonus goes to the style it hinders least: a rune platebody, which
		 * takes from magic and ranged attack, is melee armour.
		 */
		Purpose style()
		{
			if (!equipable())
			{
				return null;
			}
			int best = Math.max(melee, Math.max(ranged, magic));
			if (best <= 0 && defence <= 0)
			{
				return null;
			}
			return melee == best ? Purpose.MELEE : ranged == best ? Purpose.RANGED : Purpose.MAGIC;
		}

		int score(Purpose style)
		{
			return style == Purpose.RANGED ? ranged : style == Purpose.MAGIC ? magic : melee;
		}

		String lower()
		{
			return name.toLowerCase(Locale.ROOT);
		}
	}

	/** What a tab is for, in the order the tabs are laid out. */
	public enum Purpose
	{
		KITS("Kits"),
		MELEE("Melee"),
		RANGED("Ranged"),
		MAGIC("Magic"),
		SUPPLIES("Supplies"),
		TELEPORTS("Teleports"),
		SKILLING("Skilling"),
		QUESTS("Quests"),
		KEEPSAKES("Keepsakes"),
		SELL("Sell");

		private final String label;

		Purpose(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		/** The Bank Tags tag: lower case, as Bank Tags keeps every tag. */
		public String tag()
		{
			return PREFIX + label.toLowerCase(Locale.ROOT);
		}
	}

	/** A named block of a tab, such as one kit. Cells are columns and rows of the tab. */
	public static final class Block
	{
		private final String name;
		private final int column;
		private final int row;
		private final int width;
		private final int height;

		Block(String name, int column, int row, int width, int height)
		{
			this.name = name;
			this.column = column;
			this.row = row;
			this.width = width;
			this.height = height;
		}

		public String getName()
		{
			return name;
		}

		public int getColumn()
		{
			return column;
		}

		public int getRow()
		{
			return row;
		}

		public int getWidth()
		{
			return width;
		}

		public int getHeight()
		{
			return height;
		}
	}

	/** One designed tag tab: its layout, slot by slot, with -1 for an empty slot. */
	public static final class Tab
	{
		private final Purpose purpose;
		private final int[] layout;
		private final List<Block> blocks;

		Tab(Purpose purpose, int[] layout, List<Block> blocks)
		{
			this.purpose = purpose;
			this.layout = layout;
			this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
		}

		public Purpose getPurpose()
		{
			return purpose;
		}

		public String getTag()
		{
			return purpose.tag();
		}

		/** The layout: index {@code row * COLUMNS + column} holds an item id, or -1. */
		public int[] getLayout()
		{
			return layout.clone();
		}

		public int itemAt(int index)
		{
			return index >= 0 && index < layout.length ? layout[index] : -1;
		}

		public int size()
		{
			return layout.length;
		}

		public int rows()
		{
			return (layout.length + COLUMNS - 1) / COLUMNS;
		}

		/** The items in the tab, counting an item shown twice once. */
		public int items()
		{
			Set<Integer> seen = new HashSet<>();
			for (int id : layout)
			{
				if (id > 0)
				{
					seen.add(id);
				}
			}
			return seen.size();
		}

		/** The tab's icon: its first item. */
		public int icon()
		{
			for (int id : layout)
			{
				if (id > 0)
				{
					return id;
				}
			}
			return ItemID.BANK_FILLER;
		}

		public List<Block> getBlocks()
		{
			return blocks;
		}

		/**
		 * The tab as Bank Tags imports it from the clipboard: the header, the tag, the icon, then
		 * every filled slot of the layout as slot and item.
		 */
		public String export()
		{
			List<String> parts = new ArrayList<>();
			parts.add("banktags");
			parts.add("1");
			parts.add(getTag());
			parts.add(String.valueOf(icon()));
			parts.add("layout");
			for (int i = 0; i < layout.length; i++)
			{
				if (layout[i] > 0)
				{
					parts.add(String.valueOf(i));
					parts.add(String.valueOf(layout[i]));
				}
			}
			return String.join(",", parts);
		}
	}

	/** The whole design: its tabs, and how far the bank as it sits is from it. */
	public static final class Design
	{
		public static final Design EMPTY = new Design(Collections.emptyList(), 0, -1, 0);

		private final List<Tab> tabs;
		private final int total;
		private final int moves;
		private final int compared;

		Design(List<Tab> tabs, int total, int moves, int compared)
		{
			this.tabs = Collections.unmodifiableList(new ArrayList<>(tabs));
			this.total = total;
			this.moves = moves;
			this.compared = compared;
		}

		public List<Tab> getTabs()
		{
			return tabs;
		}

		public boolean isEmpty()
		{
			return tabs.isEmpty();
		}

		/** Kinds of item the design places. */
		public int getTotal()
		{
			return total;
		}

		/**
		 * Items that would have to be dragged to put the bank in the design's order by hand, or -1
		 * when the order the bank sits in is not known.
		 */
		public int getMoves()
		{
			return moves;
		}

		/** Items whose place in the bank as it sits was compared. */
		public int getCompared()
		{
			return compared;
		}

		/** Items already in the design's order. */
		public int getInPlace()
		{
			return moves < 0 ? 0 : compared - moves;
		}

		public Tab tab(Purpose purpose)
		{
			for (Tab t : tabs)
			{
				if (t.purpose == purpose)
				{
					return t;
				}
			}
			return null;
		}

		/** Changes when anything shown changes. */
		public long fingerprint()
		{
			long h = total * 31L + moves;
			for (Tab t : tabs)
			{
				h = h * 131 + t.purpose.ordinal();
				h = h * 31 + Arrays.hashCode(t.layout);
			}
			return h;
		}
	}

	/** What the design is built from. */
	public static final class Inputs
	{
		Map<Integer, Long> bank = Collections.emptyMap();
		IntFunction<Facts> facts = id -> Facts.item("Item " + id, false);
		List<Plans.Kit> kits = Collections.emptyList();
		Map<String, List<Integer>> needed = Collections.emptyMap();
		List<Integer> sell = Collections.emptyList();
		List<Integer> order = Collections.emptyList();

		/** The bank: item to count. */
		public Inputs bank(Map<Integer, Long> bank)
		{
			this.bank = bank;
			return this;
		}

		public Inputs facts(IntFunction<Facts> facts)
		{
			this.facts = facts;
			return this;
		}

		/** Quartermaster's kits, in its order. */
		public Inputs kits(List<Plans.Kit> kits)
		{
			this.kits = kits;
			return this;
		}

		/** What each unfinished quest or diary needs from the bank, most pressing first. */
		public Inputs needed(Map<String, List<Integer>> needed)
		{
			this.needed = needed;
			return this;
		}

		/** What the clear-out lists, in its order. */
		public Inputs sell(List<Integer> sell)
		{
			this.sell = sell;
			return this;
		}

		/** The bank's items in the order they sit now. */
		public Inputs order(List<Integer> order)
		{
			this.order = order;
			return this;
		}
	}

	/**
	 * Worn slots in the order a gear tab lays them out: weapon and shield first, then head to
	 * feet, then cape, neck, ring and ammunition (slot numbers as EquipmentInventorySlot).
	 */
	static final int[] SLOT_ORDER = {3, 5, 0, 4, 7, 9, 10, 1, 2, 12, 13};

	/** Names (lower case) of teleport jewellery and other teleport items a name alone cannot tell. */
	static final List<String> TELEPORT_NAMES = Arrays.asList(
		"ring of dueling", "games necklace", "amulet of glory", "skills necklace", "combat bracelet", "ring of wealth",
		"digsite pendant", "necklace of passage", "burning amulet", "slayer ring", "ring of returning", "ectophial",
		"royal seed pod", "xeric's talisman", "drakan's medallion", "enchanted lyre", "camulet", "pharaoh's sceptre",
		"ring of the elements", "kharedst's memoirs", "book of the dead");

	/** Skilling tools that are not pickaxes or axes, by exact name (lower case). */
	static final Set<String> TOOL_NAMES = new HashSet<>(Arrays.asList(
		"hammer", "imcando hammer", "chisel", "knife", "saw", "crystal saw", "tinderbox", "needle", "spade", "rake",
		"secateurs", "magic secateurs", "seed dibber", "gardening trowel", "pestle and mortar", "glassblowing pipe",
		"lobster pot", "small fishing net", "big fishing net", "fishing rod", "fly fishing rod", "oily fishing rod",
		"barbarian rod", "harpoon", "dragon harpoon", "infernal harpoon", "crystal harpoon", "karambwan vessel"));

	/** Herbs once cleaned, by exact name (lower case); grimy herbs say so in their name. */
	static final Set<String> HERB_NAMES = new HashSet<>(Arrays.asList(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf", "avantoe", "kwuarm",
		"huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol"));

	/** Cut gems, by exact name (lower case); uncut ones say so in their name. */
	static final Set<String> GEM_NAMES = new HashSet<>(Arrays.asList(
		"opal", "jade", "red topaz", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "zenyte"));

	/** The groups of the Skilling tab, in order: tools and outfits, then resources. */
	enum Craft
	{
		TOOLS, OUTFITS, ORES, LOGS, HERBS, SEEDS, FISH, GEMS, HIDES, ESSENCE, OTHER
	}

	private Architect()
	{
	}

	/** Designs the bank. */
	public static Design design(Inputs in)
	{
		Map<Integer, Long> bank = in.bank;
		Map<Integer, Facts> facts = new HashMap<>();
		for (int id : bank.keySet())
		{
			Facts f = in.facts.apply(id);
			facts.put(id, f == null ? Facts.item("Item " + id, false) : f);
		}
		Set<Integer> placed = new HashSet<>();

		// Sell first: what the clear-out lists goes, whatever else it is.
		List<Integer> sell = new ArrayList<>();
		for (int id : in.sell)
		{
			if (bank.containsKey(id) && placed.add(id))
			{
				sell.add(id);
			}
		}

		Map<Purpose, List<List<List<Integer>>>> sections = new LinkedHashMap<>();
		// Teleports: jewellery, then tablets and scrolls.
		List<Integer> jewellery = new ArrayList<>();
		List<Integer> tablets = new ArrayList<>();
		// Gear: by style, then by worn slot.
		Map<Purpose, Map<Integer, List<Integer>>> gear = new LinkedHashMap<>();
		for (Purpose p : new Purpose[]{Purpose.MELEE, Purpose.RANGED, Purpose.MAGIC})
		{
			gear.put(p, new HashMap<>());
		}
		Map<Craft, List<Integer>> crafts = new LinkedHashMap<>();
		for (Craft c : Craft.values())
		{
			crafts.put(c, new ArrayList<>());
		}
		List<Integer> coins = new ArrayList<>();
		List<Integer> food = new ArrayList<>();
		Map<String, List<Integer>> potions = new HashMap<>();
		List<Integer> runes = new ArrayList<>();
		List<Integer> keepsakes = new ArrayList<>();

		List<Integer> ids = new ArrayList<>(bank.keySet());
		Collections.sort(ids);
		Set<Integer> questItems = new HashSet<>();
		for (List<Integer> list : in.needed.values())
		{
			questItems.addAll(list);
		}
		Set<Integer> quests = new HashSet<>();
		for (int id : ids)
		{
			if (placed.contains(id))
			{
				continue;
			}
			Facts f = facts.get(id);
			String name = f.lower();
			Purpose style = f.style();
			if (isTeleport(name))
			{
				(f.equipable() ? jewellery : tablets).add(id);
			}
			else if (isTool(id, name))
			{
				crafts.get(Craft.TOOLS).add(id);
			}
			else if (style != null)
			{
				gear.get(style).computeIfAbsent(f.slot, k -> new ArrayList<>()).add(id);
			}
			else if (questItems.contains(id))
			{
				quests.add(id);
			}
			else if (id == ItemID.COINS || id == ItemID.PLATINUM)
			{
				coins.add(id);
			}
			else if (f.food)
			{
				food.add(id);
			}
			else if (f.potion)
			{
				potions.computeIfAbsent(family(f.name), k -> new ArrayList<>()).add(id);
			}
			else if (!f.equipable() && name.endsWith(" rune"))
			{
				runes.add(id);
			}
			else if (f.equipable())
			{
				crafts.get(Craft.OUTFITS).add(id);
			}
			else if (f.tradeable)
			{
				crafts.get(craft(name)).add(id);
			}
			else
			{
				keepsakes.add(id);
			}
			placed.add(id);
		}

		Comparator<Integer> byName = Comparator.comparing((Integer id) -> facts.get(id).lower()).thenComparing(id -> id);
		Comparator<Integer> byCount = Comparator.comparing((Integer id) -> -bank.getOrDefault(id, 0L)).thenComparing(byName);

		// Gear: one section a style, a group a worn slot, strongest first.
		for (Map.Entry<Purpose, Map<Integer, List<Integer>>> e : gear.entrySet())
		{
			Purpose style = e.getKey();
			List<List<Integer>> groups = new ArrayList<>();
			for (int slot : SLOT_ORDER)
			{
				List<Integer> list = e.getValue().get(slot);
				if (list != null)
				{
					list.sort(Comparator.comparing((Integer id) -> -facts.get(id).score(style)).thenComparing(byName));
					groups.add(list);
				}
			}
			for (Map.Entry<Integer, List<Integer>> other : e.getValue().entrySet())
			{
				if (Arrays.stream(SLOT_ORDER).noneMatch(s -> s == other.getKey()))
				{
					other.getValue().sort(byName);
					groups.add(other.getValue());
				}
			}
			section(sections, style, groups);
		}

		// Supplies: coins, food, potions by kind with their doses together, then runes.
		food.sort(byCount);
		List<String> families = new ArrayList<>(potions.keySet());
		Collections.sort(families);
		// One run of potions: by kind, each kind's doses side by side from the fullest.
		List<Integer> potionRun = new ArrayList<>();
		for (String family : families)
		{
			List<Integer> doses = potions.get(family);
			doses.sort(Comparator.comparing((Integer id) -> -doses(facts.get(id).name)).thenComparing(byName));
			potionRun.addAll(doses);
		}
		runes.sort(Comparator.naturalOrder());
		section(sections, Purpose.SUPPLIES, Arrays.asList(coins, food));
		section(sections, Purpose.SUPPLIES, groupsOf(potionRun));
		section(sections, Purpose.SUPPLIES, groupsOf(runes));

		jewellery.sort(byName);
		tablets.sort(byName);
		section(sections, Purpose.TELEPORTS, groupsOf(jewellery));
		section(sections, Purpose.TELEPORTS, groupsOf(tablets));

		// Skilling: tools and outfits, then resources a kind to a group.
		List<List<Integer>> kit = new ArrayList<>();
		List<List<Integer>> resources = new ArrayList<>();
		for (Map.Entry<Craft, List<Integer>> e : crafts.entrySet())
		{
			e.getValue().sort(byName);
			(e.getKey() == Craft.TOOLS || e.getKey() == Craft.OUTFITS ? kit : resources).add(e.getValue());
		}
		section(sections, Purpose.SKILLING, kit);
		section(sections, Purpose.SKILLING, resources);

		// Quests: each quest's items together, the most pressing quest first; an item once.
		List<List<Integer>> questGroups = new ArrayList<>();
		Set<Integer> shown = new HashSet<>();
		for (List<Integer> list : in.needed.values())
		{
			List<Integer> group = new ArrayList<>();
			for (int id : list)
			{
				if (quests.contains(id) && shown.add(id))
				{
					group.add(id);
				}
			}
			if (!group.isEmpty())
			{
				questGroups.add(group);
			}
		}
		section(sections, Purpose.QUESTS, questGroups);

		keepsakes.sort(byName);
		section(sections, Purpose.KEEPSAKES, groupsOf(keepsakes));
		section(sections, Purpose.SELL, groupsOf(sell));

		List<Tab> tabs = new ArrayList<>();
		Tab kits = kits(in.kits, bank);
		if (kits != null)
		{
			tabs.add(kits);
		}
		for (Purpose p : Purpose.values())
		{
			List<List<List<Integer>>> s = sections.get(p);
			if (s != null)
			{
				tabs.add(new Tab(p, pack(s), Collections.emptyList()));
			}
		}

		// The home order: every tab but Kits, which repeats items homed elsewhere.
		List<Integer> home = new ArrayList<>();
		for (Tab t : tabs)
		{
			if (t.purpose == Purpose.KITS)
			{
				continue;
			}
			for (int id : t.layout)
			{
				if (id > 0)
				{
					home.add(id);
				}
			}
		}
		int[] moves = moves(home, in.order);
		return new Design(tabs, home.size(), moves[0], moves[1]);
	}

	/** Adds a section, which starts a row of its own, of groups packed along it. Empty groups are dropped. */
	private static void section(Map<Purpose, List<List<List<Integer>>>> sections, Purpose p, List<List<Integer>> groups)
	{
		List<List<Integer>> kept = new ArrayList<>();
		for (List<Integer> g : groups)
		{
			if (!g.isEmpty())
			{
				kept.add(g);
			}
		}
		if (!kept.isEmpty())
		{
			sections.computeIfAbsent(p, k -> new ArrayList<>()).add(kept);
		}
	}

	private static List<List<Integer>> groupsOf(List<Integer> items)
	{
		return Collections.singletonList(items);
	}

	/**
	 * Lays sections out eight to a row. A section starts a new row; within it, each group follows
	 * the last after one empty slot when it fits in the row, or starts the next row when it does
	 * not. A group longer than a row runs on over as many rows as it needs.
	 */
	static int[] pack(List<List<List<Integer>>> sections)
	{
		List<Integer> cells = new ArrayList<>();
		for (List<List<Integer>> section : sections)
		{
			padToRow(cells);
			for (List<Integer> group : section)
			{
				int col = cells.size() % COLUMNS;
				if (col != 0)
				{
					if (group.size() <= COLUMNS && col + 1 + group.size() <= COLUMNS)
					{
						cells.add(-1);
					}
					else
					{
						padToRow(cells);
					}
				}
				cells.addAll(group);
			}
		}
		int[] out = new int[cells.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = cells.get(i);
		}
		return out;
	}

	private static void padToRow(List<Integer> cells)
	{
		while (cells.size() % COLUMNS != 0)
		{
			cells.add(-1);
		}
	}

	/**
	 * The Kits tab: each kit a block four wide, two side by side, a blank row between each pair.
	 * A kit's items follow its supplies in order; of the items a pooled supply counts, those the
	 * bank holds most of come first. Only items in the bank are placed.
	 */
	static Tab kits(List<Plans.Kit> kits, Map<Integer, Long> bank)
	{
		List<String> names = new ArrayList<>();
		List<List<Integer>> blocks = new ArrayList<>();
		for (Plans.Kit kit : kits)
		{
			List<Integer> items = new ArrayList<>();
			Set<Integer> seen = new HashSet<>();
			for (List<Integer> need : kit.getNeeds())
			{
				List<Integer> held = new ArrayList<>();
				for (int id : need)
				{
					if (bank.getOrDefault(id, 0L) > 0 && !seen.contains(id))
					{
						held.add(id);
					}
				}
				held.sort(Comparator.comparing((Integer id) -> -bank.get(id)).thenComparing(id -> id));
				for (int id : held)
				{
					if (seen.add(id))
					{
						items.add(id);
					}
				}
			}
			if (!items.isEmpty())
			{
				names.add(kit.getName());
				blocks.add(items);
			}
		}
		if (blocks.isEmpty())
		{
			return null;
		}
		List<Integer> cells = new ArrayList<>();
		List<Block> placed = new ArrayList<>();
		int row = 0;
		for (int i = 0; i < blocks.size(); i += 2)
		{
			int height = 0;
			for (int side = 0; side < 2 && i + side < blocks.size(); side++)
			{
				List<Integer> items = blocks.get(i + side);
				int rows = (items.size() + KIT_WIDTH - 1) / KIT_WIDTH;
				height = Math.max(height, rows);
				for (int k = 0; k < items.size(); k++)
				{
					int index = (row + k / KIT_WIDTH) * COLUMNS + side * KIT_WIDTH + k % KIT_WIDTH;
					while (cells.size() <= index)
					{
						cells.add(-1);
					}
					cells.set(index, items.get(k));
				}
				placed.add(new Block(names.get(i + side), side * KIT_WIDTH, row, KIT_WIDTH, rows));
			}
			row += height + 1;
		}
		int[] layout = new int[cells.size()];
		for (int i = 0; i < layout.length; i++)
		{
			layout[i] = cells.get(i);
		}
		return new Tab(Purpose.KITS, layout, placed);
	}

	/**
	 * How many items would have to be dragged to bring the bank as it sits into the design's
	 * order: every item outside the longest run already in the design's order, in the order it
	 * sits (the longest increasing subsequence).
	 *
	 * @return the moves, or -1 when the order is unknown, and how many items were compared
	 */
	static int[] moves(List<Integer> home, List<Integer> order)
	{
		Map<Integer, Integer> rank = new HashMap<>();
		for (int i = 0; i < home.size(); i++)
		{
			rank.putIfAbsent(home.get(i), i);
		}
		List<Integer> ranks = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		for (int id : order)
		{
			Integer r = rank.get(id);
			if (r != null && seen.add(id))
			{
				ranks.add(r);
			}
		}
		if (ranks.isEmpty())
		{
			return new int[]{-1, 0};
		}
		// Patience sorting: tails[k] is the smallest last rank of a run of k + 1.
		int[] tails = new int[ranks.size()];
		int length = 0;
		for (int r : ranks)
		{
			int lo = 0;
			int hi = length;
			while (lo < hi)
			{
				int mid = (lo + hi) >>> 1;
				if (tails[mid] < r)
				{
					lo = mid + 1;
				}
				else
				{
					hi = mid;
				}
			}
			tails[lo] = r;
			if (lo == length)
			{
				length++;
			}
		}
		return new int[]{ranks.size() - length, ranks.size()};
	}

	static boolean isTeleport(String lower)
	{
		if (lower.contains("teleport"))
		{
			return true;
		}
		for (String name : TELEPORT_NAMES)
		{
			if (lower.startsWith(name))
			{
				return true;
			}
		}
		return false;
	}

	static boolean isTool(int id, String lower)
	{
		return Tools.kindOf(id) != null || TOOL_NAMES.contains(lower) || lower.startsWith("watering can");
	}

	/** Which row of the Skilling tab a tradeable resource belongs in, from its name. */
	static Craft craft(String lower)
	{
		if (lower.endsWith(" ore") || lower.endsWith(" bar") || lower.equals("coal"))
		{
			return Craft.ORES;
		}
		if (lower.endsWith("logs") || lower.contains("plank"))
		{
			return Craft.LOGS;
		}
		if (lower.startsWith("grimy ") || HERB_NAMES.contains(lower))
		{
			return Craft.HERBS;
		}
		if (lower.endsWith(" seed") || lower.endsWith(" sapling") || lower.endsWith(" seedling"))
		{
			return Craft.SEEDS;
		}
		if (lower.startsWith("raw "))
		{
			return Craft.FISH;
		}
		if (lower.startsWith("uncut ") || GEM_NAMES.contains(lower))
		{
			return Craft.GEMS;
		}
		if (lower.contains("hide") || lower.contains("leather"))
		{
			return Craft.HIDES;
		}
		if (lower.endsWith("essence"))
		{
			return Craft.ESSENCE;
		}
		return Craft.OTHER;
	}

	/** A potion's name without its doses: "Prayer potion(4)" is "Prayer potion". */
	static String family(String name)
	{
		int open = name.lastIndexOf('(');
		return (open > 0 && name.endsWith(")") ? name.substring(0, open) : name).trim().toLowerCase(Locale.ROOT);
	}

	/** The doses a potion's name says, or 0. */
	static int doses(String name)
	{
		int open = name.lastIndexOf('(');
		if (open < 0 || !name.endsWith(")"))
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(name.substring(open + 1, name.length() - 1).trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
