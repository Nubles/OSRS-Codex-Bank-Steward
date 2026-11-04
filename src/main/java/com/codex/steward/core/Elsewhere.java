package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Bank slots won back by keeping items where the game has a home for them: farming tools with the
 * Tool Leprechaun, seeds and saplings in the Farming Guild's seed vault, and a clue's emote gear in
 * its STASH unit. Only stores this account can use are offered, and an item goes only when its
 * whole stack fits, since a part-moved stack frees no slot.
 *
 * <p>What a store will take is from the Old School wiki (Tool Leprechaun, Seed vault, STASH units
 * and Farming Guild pages); the STASH sets are RuneLite's own emote clue list. The Steward cannot
 * see inside a store, so it never claims one is empty: a leprechaun already holding 100 rakes, or a
 * STASH unit already stocked, is for the player to know.</p>
 */
public final class Elsewhere
{
	/** Where an item can live instead of the bank. */
	public enum Kind
	{
		LEPRECHAUN,
		SEED_VAULT,
		STASH
	}

	/** Farming level to enter the Farming Guild, where the seed vault stands; boosts count. */
	public static final int GUILD_FARMING = 45;

	/** Construction level to build a STASH unit of each clue tier, in {@link Catalog#CLUE_TIERS} order. */
	static final int[] STASH_CONSTRUCTION = {12, 27, 42, 55, 77, 88};
	/** What each tier's unit is built from, besides a hammer, a saw and ten nails of one metal. */
	static final String[] STASH_MATERIALS = {"2 planks", "2 planks", "2 oak planks", "2 teak planks", "2 mahogany planks",
		"2 mahogany planks and a gold leaf"};

	/** One kind of thing the leprechaun keeps, with every item that counts as it and how many fit. */
	static final class Slot
	{
		final String name;
		final int capacity;
		final int[] items;

		Slot(String name, int capacity, int... items)
		{
			this.name = name;
			this.capacity = capacity;
			this.items = items;
		}
	}

	/** The Tool Leprechaun's storage, as the wiki lists it. */
	static final List<Slot> LEPRECHAUN = Collections.unmodifiableList(Arrays.asList(
		new Slot("rakes", 100, ItemID.RAKE),
		new Slot("spades", 100, ItemID.SPADE),
		new Slot("seed dibbers", 100, ItemID.DIBBER),
		new Slot("secateurs", 100, ItemID.SECATEURS, ItemID.FAIRY_ENCHANTED_SECATEURS),
		new Slot("gardening trowels", 100, ItemID.GARDENING_TROWEL),
		new Slot("watering can", 1, ItemID.WATERING_CAN_8, ItemID.WATERING_CAN_7, ItemID.WATERING_CAN_6,
			ItemID.WATERING_CAN_5, ItemID.WATERING_CAN_4, ItemID.WATERING_CAN_3, ItemID.WATERING_CAN_2,
			ItemID.WATERING_CAN_1, ItemID.WATERING_CAN_0, ItemID.ZEAH_WATERINGCAN),
		new Slot("bottomless compost bucket", 1, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, ItemID.BOTTOMLESS_COMPOST_BUCKET),
		new Slot("plant cures", 1000, ItemID.PLANT_CURE),
		new Slot("empty buckets", 1000, ItemID.BUCKET_EMPTY),
		new Slot("compost", 1000, ItemID.BUCKET_COMPOST),
		new Slot("supercompost", 1000, ItemID.BUCKET_SUPERCOMPOST),
		new Slot("ultracompost", 1000, ItemID.BUCKET_ULTRACOMPOST)));

	/** Every seed, spore, sapling and coral frag the seed vault takes: all 104 of its places. */
	static final int[] SEED_VAULT = {
		// Allotment
		ItemID.POTATO_SEED, ItemID.ONION_SEED, ItemID.CABBAGE_SEED, ItemID.TOMATO_SEED, ItemID.SWEETCORN_SEED,
		ItemID.STRAWBERRY_SEED, ItemID.WATERMELON_SEED, ItemID.SNAPE_GRASS_SEED,
		// Flowers
		ItemID.MARIGOLD_SEED, ItemID.ROSEMARY_SEED, ItemID.NASTURTIUM_SEED, ItemID.WOAD_SEED, ItemID.LIMPWURT_SEED,
		ItemID.WHITE_LILY_SEED,
		// Herbs
		ItemID.GUAM_SEED, ItemID.MARRENTILL_SEED, ItemID.TARROMIN_SEED, ItemID.HARRALANDER_SEED, ItemID.RANARR_SEED,
		ItemID.TOADFLAX_SEED, ItemID.IRIT_SEED, ItemID.AVANTOE_SEED, ItemID.KWUARM_SEED, ItemID.SNAPDRAGON_SEED,
		ItemID.HUASCA_SEED, ItemID.CADANTINE_SEED, ItemID.LANTADYME_SEED, ItemID.DWARF_WEED_SEED, ItemID.TORSTOL_SEED,
		// Hops
		ItemID.BARLEY_SEED, ItemID.HAMMERSTONE_HOP_SEED, ItemID.ASGARNIAN_HOP_SEED, ItemID.JUTE_SEED,
		ItemID.YANILLIAN_HOP_SEED, ItemID.FLAX_SEED, ItemID.KRANDORIAN_HOP_SEED, ItemID.WILDBLOOD_HOP_SEED,
		ItemID.HEMP_SEED, ItemID.COTTON_SEED,
		// Bushes
		ItemID.REDBERRY_BUSH_SEED, ItemID.CADAVABERRY_BUSH_SEED, ItemID.DWELLBERRY_BUSH_SEED,
		ItemID.JANGERBERRY_BUSH_SEED, ItemID.WHITEBERRY_BUSH_SEED, ItemID.POISONIVY_BUSH_SEED,
		// Trees
		ItemID.ACORN, ItemID.WILLOW_SEED, ItemID.TEAK_SEED, ItemID.MAPLE_SEED, ItemID.MAHOGANY_SEED, ItemID.YEW_SEED,
		ItemID.CAMPHOR_SEED, ItemID.MAGIC_TREE_SEED, ItemID.IRONWOOD_SEED, ItemID.REDWOOD_TREE_SEED, ItemID.ROSEWOOD_SEED,
		// Fruit trees
		ItemID.APPLE_TREE_SEED, ItemID.BANANA_TREE_SEED, ItemID.ORANGE_TREE_SEED, ItemID.CURRY_TREE_SEED,
		ItemID.PINEAPPLE_TREE_SEED, ItemID.PAPAYA_TREE_SEED, ItemID.PALM_TREE_SEED, ItemID.DRAGONFRUIT_TREE_SEED,
		// Exotic
		ItemID.SEAWEED_SEED, ItemID.GRAPE_SEED, ItemID.MUSHROOM_SEED, ItemID.CACTUS_SEED, ItemID.BELLADONNA_SEED,
		ItemID.POTATO_CACTUS_SEED, ItemID.HESPORI_SEED, ItemID.CALQUAT_TREE_SEED, ItemID.CRYSTAL_TREE_SEED,
		ItemID.SPIRIT_TREE_SEED, ItemID.CELASTRUS_TREE_SEED,
		// Anima
		ItemID.ATTAS_SEED, ItemID.IASOR_SEED, ItemID.KRONOS_SEED,
		// Saplings
		ItemID.PLANTPOT_OAK_SAPLING, ItemID.PLANTPOT_APPLE_SAPLING, ItemID.PLANTPOT_WILLOW_SAPLING,
		ItemID.PLANTPOT_BANANA_SAPLING, ItemID.PLANTPOT_TEAK_SAPLING, ItemID.PLANTPOT_ORANGE_SAPLING,
		ItemID.PLANTPOT_CURRY_SAPLING, ItemID.PLANTPOT_MAPLE_SAPLING, ItemID.PLANTPOT_PINEAPPLE_SAPLING,
		ItemID.PLANTPOT_MAHOGANY_SAPLING, ItemID.PLANTPOT_PAPAYA_SAPLING, ItemID.PLANTPOT_YEW_SAPLING,
		ItemID.PLANTPOT_CAMPHOR_SAPLING, ItemID.PLANTPOT_PALM_SAPLING, ItemID.PLANTPOT_CALQUAT_SAPLING,
		ItemID.PLANTPOT_CRYSTAL_TREE_SAPLING, ItemID.PLANTPOT_MAGIC_TREE_SAPLING, ItemID.PLANTPOT_IRONWOOD_SAPLING,
		ItemID.PLANTPOT_DRAGONFRUIT_SAPLING, ItemID.PLANTPOT_SPIRIT_TREE_SAPLING, ItemID.PLANTPOT_CELASTRUS_TREE_SAPLING,
		ItemID.PLANTPOT_REDWOOD_TREE_SAPLING, ItemID.PLANTPOT_ROSEWOOD_SAPLING,
		// Coral
		ItemID.CORAL_ELKHORN_FRAG, ItemID.CORAL_PILLAR_FRAG, ItemID.CORAL_UMBRAL_FRAG,
	};

	/** The bundled STASH sets, as Gson reads stash-units.json. */
	public static final class UnitsData
	{
		String source;
		/** Each unit: object id, tier bit, place, then each part's items. */
		List<List<Object>> units;
	}

	/** One STASH unit and the set it holds: every part must go in together. */
	public static final class Unit
	{
		private final int objectId;
		private final int tier;
		private final String place;
		private final List<int[]> parts;

		public Unit(int objectId, int tier, String place, List<int[]> parts)
		{
			this.objectId = objectId;
			this.tier = tier;
			this.place = place;
			this.parts = parts;
		}

		public int getObjectId()
		{
			return objectId;
		}

		/** The clue tier, as an index into {@link Catalog#CLUE_TIERS}. */
		public int getTier()
		{
			return tier;
		}

		public String getPlace()
		{
			return place;
		}

		List<int[]> getParts()
		{
			return parts;
		}

		public int getConstruction()
		{
			return STASH_CONSTRUCTION[tier];
		}

		public String getMaterials()
		{
			return STASH_MATERIALS[tier];
		}
	}

	/** Reads the bundled sets, leaving out any row it cannot read. */
	public static List<Unit> units(UnitsData data)
	{
		List<Unit> list = new ArrayList<>();
		if (data == null || data.units == null)
		{
			return list;
		}
		for (List<Object> row : data.units)
		{
			try
			{
				int objectId = ((Number) row.get(0)).intValue();
				int mask = ((Number) row.get(1)).intValue();
				int tier = Integer.numberOfTrailingZeros(mask);
				if (mask == 0 || Integer.bitCount(mask) != 1 || tier >= STASH_CONSTRUCTION.length)
				{
					continue;
				}
				String place = (String) row.get(2);
				List<int[]> parts = new ArrayList<>();
				for (int i = 3; i < row.size(); i++)
				{
					List<?> raw = (List<?>) row.get(i);
					int[] ids = new int[raw.size()];
					for (int j = 0; j < ids.length; j++)
					{
						ids[j] = ((Number) raw.get(j)).intValue();
					}
					if (ids.length > 0)
					{
						parts.add(ids);
					}
				}
				if (!parts.isEmpty() && place != null)
				{
					list.add(new Unit(objectId, tier, place, parts));
				}
			}
			catch (RuntimeException e)
			{
				// A row in a shape this version does not know: leave it out.
			}
		}
		return list;
	}

	/** What the account can use, as last read in the game. */
	public static final class Access
	{
		public static final Access UNKNOWN = new Access(false, 0, 0, Collections.emptyMap());

		private final boolean members;
		private final int farming;
		private final int construction;
		private final Map<Integer, Boolean> built;

		/**
		 * @param members on a members' world, where the leprechaun and the seed vault are open
		 * @param farming Farming level
		 * @param construction Construction level
		 * @param built each STASH unit's object id to whether it is built; a unit missing is unknown
		 */
		public Access(boolean members, int farming, int construction, Map<Integer, Boolean> built)
		{
			this.members = members;
			this.farming = farming;
			this.construction = construction;
			this.built = built == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(built));
		}

		public boolean isMembers()
		{
			return members;
		}

		public int getFarming()
		{
			return farming;
		}

		public int getConstruction()
		{
			return construction;
		}

		Boolean built(int objectId)
		{
			return built.get(objectId);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Access))
			{
				return false;
			}
			Access a = (Access) o;
			return members == a.members && farming == a.farming && construction == a.construction && built.equals(a.built);
		}

		@Override
		public int hashCode()
		{
			return ((Boolean.hashCode(members) * 31 + farming) * 31 + construction) * 31 + built.hashCode();
		}
	}

	/** One item to take to a store. */
	public static final class Move
	{
		private final int itemId;
		private final long quantity;
		private final long held;
		private final boolean freesSlot;

		Move(int itemId, long quantity, long held)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.held = held;
			this.freesSlot = quantity >= held;
		}

		public int getItemId()
		{
			return itemId;
		}

		/** How many go. */
		public long getQuantity()
		{
			return quantity;
		}

		/** How many the bank holds. */
		public long getHeld()
		{
			return held;
		}

		/** The whole stack goes, so its bank slot comes free. */
		public boolean freesSlot()
		{
			return freesSlot;
		}
	}

	/** One store and what to take to it. */
	public static final class Store
	{
		private final Kind kind;
		private final Unit unit;
		private final boolean toBuild;
		private final List<Move> moves;

		Store(Kind kind, Unit unit, boolean toBuild, List<Move> moves)
		{
			this.kind = kind;
			this.unit = unit;
			this.toBuild = toBuild;
			this.moves = Collections.unmodifiableList(moves);
		}

		public Kind getKind()
		{
			return kind;
		}

		/** The STASH unit, or null for the leprechaun and the seed vault. */
		public Unit getUnit()
		{
			return unit;
		}

		/** A STASH unit the player can build but has not yet. */
		public boolean isToBuild()
		{
			return toBuild;
		}

		public List<Move> getMoves()
		{
			return moves;
		}

		/** Bank slots this store frees. */
		public int getFreed()
		{
			int n = 0;
			for (Move m : moves)
			{
				n += m.freesSlot ? 1 : 0;
			}
			return n;
		}

		public String getName()
		{
			switch (kind)
			{
				case LEPRECHAUN:
					return "Tool Leprechaun";
				case SEED_VAULT:
					return "Seed vault";
				default:
					return unit.getPlace();
			}
		}

		/** Where the store is and how to use it, in one line. */
		public String getWhere()
		{
			switch (kind)
			{
				case LEPRECHAUN:
					return "At any farming patch. It takes them noted, so withdraw as notes.";
				case SEED_VAULT:
					return "In the Farming Guild, west of the bank chest. Needs " + GUILD_FARMING + " Farming.";
				default:
					String tier = Catalog.CLUE_TIERS[unit.getTier()];
					String cap = Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
					return toBuild
						? cap + " STASH unit, not built: Construction " + unit.getConstruction() + ", " + unit.getMaterials() + "."
						: cap + " STASH unit. The whole set goes in at once.";
			}
		}
	}

	private Elsewhere()
	{
	}

	/**
	 * Works out what in the bank has a better home.
	 *
	 * @param bank items to consider, as in the bank now, without anything already on the clear-out list
	 * @param access what the account can use, as last read
	 * @param units the bundled STASH sets
	 * @param hold items that stay in the bank whatever: what a kit takes
	 * @param stashHold items no STASH unit should lock away: those, and what unfinished quests and diaries need
	 * @return each store with something to take to it that frees a slot: the leprechaun, the seed
	 * vault, built STASH units, then units the player could build; empty when the account is not known
	 */
	public static List<Store> plan(Map<Integer, Long> bank, Access access, List<Unit> units, Set<Integer> hold, Set<Integer> stashHold)
	{
		List<Store> stores = new ArrayList<>();
		if (bank == null || bank.isEmpty() || access == null)
		{
			return stores;
		}
		Map<Integer, Long> left = new HashMap<>();
		bank.forEach((id, q) ->
		{
			if (q != null && q > 0 && (hold == null || !hold.contains(id)))
			{
				left.put(id, q);
			}
		});
		if (access.members)
		{
			List<Move> moves = new ArrayList<>();
			for (Slot slot : LEPRECHAUN)
			{
				long room = slot.capacity;
				for (int id : slot.items)
				{
					Long q = left.get(id);
					if (q != null && q <= room)
					{
						moves.add(new Move(id, q, q));
						room -= q;
						left.remove(id);
					}
				}
			}
			add(stores, Kind.LEPRECHAUN, null, false, moves);
		}
		if (access.members && access.farming >= GUILD_FARMING)
		{
			List<Move> moves = new ArrayList<>();
			for (int id : SEED_VAULT)
			{
				Long q = left.remove(id);
				if (q != null)
				{
					moves.add(new Move(id, q, q));
				}
			}
			add(stores, Kind.SEED_VAULT, null, false, moves);
		}
		if (units != null)
		{
			Map<Integer, Long> forStash = new HashMap<>(left);
			if (stashHold != null)
			{
				forStash.keySet().removeAll(stashHold);
			}
			List<Store> built = new ArrayList<>();
			List<Store> buildable = new ArrayList<>();
			// Built units claim items first: a set for a unit not yet built must not take a part a built one needs.
			for (boolean wantBuilt : new boolean[]{true, false})
			{
				for (Unit unit : units)
				{
					Boolean state = access.built(unit.objectId);
					if (state == null || state != wantBuilt || (!wantBuilt && access.construction < unit.getConstruction()))
					{
						continue;
					}
					List<Move> moves = fill(unit, forStash);
					if (moves != null)
					{
						add(wantBuilt ? built : buildable, Kind.STASH, unit, !wantBuilt, moves);
					}
				}
			}
			Comparator<Store> most = Comparator.comparingInt(Store::getFreed).reversed()
				.thenComparing(s -> s.unit.tier).thenComparing(s -> s.unit.place);
			built.sort(most);
			buildable.sort(most);
			stores.addAll(built);
			stores.addAll(buildable);
		}
		return stores;
	}

	/** Takes one item for each part of the unit's set from what is left, or null when a part is missing. */
	private static List<Move> fill(Unit unit, Map<Integer, Long> left)
	{
		Map<Integer, Long> taken = new LinkedHashMap<>();
		for (int[] part : unit.parts)
		{
			int pick = -1;
			for (int id : part)
			{
				long have = left.getOrDefault(id, 0L) - taken.getOrDefault(id, 0L);
				if (have <= 0)
				{
					continue;
				}
				// The last one of an item frees its slot, so it is the better pick.
				if (pick == -1 || (have == 1 && left.getOrDefault(pick, 0L) - taken.getOrDefault(pick, 0L) != 1))
				{
					pick = id;
				}
			}
			if (pick == -1)
			{
				return null;
			}
			taken.merge(pick, 1L, Long::sum);
		}
		List<Move> moves = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : taken.entrySet())
		{
			moves.add(new Move(e.getKey(), e.getValue(), left.get(e.getKey())));
		}
		boolean frees = false;
		for (Move m : moves)
		{
			frees |= m.freesSlot;
		}
		if (!frees)
		{
			return null;
		}
		for (Move m : moves)
		{
			long rest = left.get(m.itemId) - m.quantity;
			if (rest <= 0)
			{
				left.remove(m.itemId);
			}
			else
			{
				left.put(m.itemId, rest);
			}
		}
		return moves;
	}

	private static void add(List<Store> stores, Kind kind, Unit unit, boolean toBuild, List<Move> moves)
	{
		if (moves.isEmpty())
		{
			return;
		}
		Store store = new Store(kind, unit, toBuild, moves);
		if (store.getFreed() > 0)
		{
			stores.add(store);
		}
	}

	/** Slots every store together frees, built STASH units and all; units still to build are left out. */
	public static int freed(List<Store> stores)
	{
		int n = 0;
		for (Store s : stores)
		{
			n += s.toBuild ? 0 : s.getFreed();
		}
		return n;
	}

	/** Every item to take somewhere, units still to build included. */
	public static Set<Integer> items(List<Store> stores)
	{
		Set<Integer> ids = new java.util.HashSet<>();
		for (Store s : stores)
		{
			for (Move m : s.moves)
			{
				ids.add(m.itemId);
			}
		}
		return ids;
	}

	/** A short key of the plan, so an unchanged one is not drawn again. */
	public static String key(List<Store> stores)
	{
		StringBuilder key = new StringBuilder();
		for (Store s : stores)
		{
			key.append(s.kind.ordinal()).append(s.unit == null ? "" : ":" + s.unit.objectId).append(s.toBuild ? "b" : "").append('[');
			for (Move m : s.moves)
			{
				key.append(m.itemId).append('x').append(m.quantity).append(m.freesSlot ? "f" : "").append(',');
			}
			key.append(']');
		}
		return key.toString();
	}
}
