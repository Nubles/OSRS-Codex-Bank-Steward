package com.codex.steward.core;

import com.codex.steward.core.BankedXp.Method;
import com.codex.steward.core.BankedXp.Rate;
import com.codex.steward.core.BankedXp.Rates;
import com.codex.steward.core.BankedXp.Recipe;
import com.codex.steward.core.BankedXp.Step;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * What each banked material can be made into, and the experience it gives. Every level and
 * experience figure was read on the OSRS Wiki and BankedRecipesTest checks each against RuneLite's
 * skill calculator; item ids are RuneLite's own. The set is kept to what was checked both ways: a
 * figure the two sources disagree on (cutting zenyte) is left out rather than guessed.
 */
public final class BankedRecipes
{
	private static final Map<Skill, Map<Integer, List<Recipe>>> BOOK = new EnumMap<>(Skill.class);
	/** Bones and what burying each gives; the place they go multiplies it. */
	private static final Map<Integer, double[]> BONES = new LinkedHashMap<>();
	private static final Map<Method, Map<Integer, List<Recipe>>> PRAYER = new EnumMap<>(Method.class);

	static
	{
		herblore();
		cooking();
		fletching();
		firemaking();
		crafting();
		smithing();
		construction();
		prayer();
	}

	private BankedRecipes()
	{
	}

	/** Every material of a skill and its recipes. For Prayer, bones as they go to the method picked. */
	public static Map<Integer, List<Recipe>> forSkill(Skill skill, Method method)
	{
		if (skill == Skill.PRAYER)
		{
			return PRAYER.get(method);
		}
		Map<Integer, List<Recipe>> book = BOOK.get(skill);
		return book == null ? Collections.emptyMap() : book;
	}

	/** The materials of a skill: what the bank is searched for. */
	public static java.util.Set<Integer> materials(Skill skill)
	{
		return forSkill(skill, Method.BURY).keySet();
	}

	// ---- building blocks ----

	private static Map<Integer, Integer> needs(int... pairs)
	{
		Map<Integer, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return Collections.unmodifiableMap(m);
	}

	private static Step step(String name, int level, double xp, int product, Rate rate, int... needs)
	{
		return new Step(name, level, xp, 1, product, rate, needs(needs));
	}

	/** A step that makes one thing from several units: a platebody from five bars. Experience is per unit. */
	private static Step perUnit(String name, int level, double xpEach, int units, int product, Rate rate, int... needs)
	{
		return new Step(name, level, xpEach / units, 1.0 / units, product, rate, needs(needs));
	}

	private static void add(Skill skill, int material, Step... steps)
	{
		BOOK.computeIfAbsent(skill, k -> new LinkedHashMap<>())
			.computeIfAbsent(material, k -> new ArrayList<>())
			.add(new Recipe(skill, Arrays.asList(steps)));
	}

	private static Step[] join(Step[] head, Step... tail)
	{
		Step[] out = Arrays.copyOf(head, head.length + tail.length);
		System.arraycopy(tail, 0, out, head.length, tail.length);
		return out;
	}

	// ---- Herblore (OSRS Wiki: Herblore, the potions and herb cleaning tables) ----

	private static void herb(int grimy, int clean, int unf, String name, int cleanLevel, double cleanXp, Step... potions)
	{
		Step cleaning = step("Clean", cleanLevel, cleanXp, clean, null);
		// "Experience: 0" for an unfinished potion (OSRS Wiki: Ranarr potion (unf)); one vial of water each.
		Step unfinished = new Step(name + " potion (unf)", 1, 0, 0, unf, null, needs(ItemID.VIAL_WATER, 1));
		for (Step potion : potions)
		{
			add(Skill.HERBLORE, grimy, cleaning, unfinished, potion);
			add(Skill.HERBLORE, clean, unfinished, potion);
			add(Skill.HERBLORE, unf, potion);
		}
		// Without the potion's secondaries a grimy herb still gives its cleaning.
		add(Skill.HERBLORE, grimy, cleaning);
	}

	private static Step potion(String name, int level, double xp, int product, int secondary)
	{
		return step(name, level, xp, product, Rates.POTIONS, secondary, 1);
	}

	private static void herblore()
	{
		herb(ItemID.UNIDENTIFIED_GUAM, ItemID.GUAM_LEAF, ItemID.GUAMVIAL, "Guam", 3, 2.5,
			potion("Attack potion(3)", 3, 25, ItemID._3DOSE1ATTACK, ItemID.EYE_OF_NEWT));
		herb(ItemID.UNIDENTIFIED_MARENTILL, ItemID.MARENTILL, ItemID.MARRENTILLVIAL, "Marrentill", 5, 3.8,
			potion("Antipoison(3)", 5, 37.5, ItemID._3DOSEANTIPOISON, ItemID.UNICORN_HORN_DUST));
		herb(ItemID.UNIDENTIFIED_TARROMIN, ItemID.TARROMIN, ItemID.TARROMINVIAL, "Tarromin", 11, 5,
			potion("Strength potion(3)", 12, 50, ItemID._3DOSE1STRENGTH, ItemID.LIMPWURT_ROOT));
		herb(ItemID.UNIDENTIFIED_HARRALANDER, ItemID.HARRALANDER, ItemID.HARRALANDERVIAL, "Harralander", 20, 6.3,
			potion("Restore potion(3)", 22, 62.5, ItemID._3DOSESTATRESTORE, ItemID.RED_SPIDERS_EGGS),
			potion("Energy potion(3)", 26, 67.5, ItemID._3DOSE1ENERGY, ItemID.CHOCOLATE_DUST),
			potion("Combat potion(3)", 36, 84, ItemID._3DOSECOMBAT, ItemID.GROUND_DESERT_GOAT_HORN));
		herb(ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, ItemID.RANARRVIAL, "Ranarr", 25, 7.5,
			potion("Defence potion(3)", 30, 75, ItemID._3DOSE1DEFENSE, ItemID.WHITE_BERRIES),
			potion("Prayer potion(3)", 38, 87.5, ItemID._3DOSEPRAYERRESTORE, ItemID.SNAPE_GRASS));
		herb(ItemID.UNIDENTIFIED_TOADFLAX, ItemID.TOADFLAX, ItemID.TOADFLAXVIAL, "Toadflax", 30, 8,
			potion("Agility potion(3)", 34, 80, ItemID._3DOSE1AGILITY, ItemID.TOADS_LEGS),
			potion("Saradomin brew(3)", 81, 180, ItemID._3DOSEPOTIONOFSARADOMIN, ItemID.CRUSHED_BIRD_NEST));
		herb(ItemID.UNIDENTIFIED_IRIT, ItemID.IRIT_LEAF, ItemID.IRITVIAL, "Irit", 40, 8.8,
			potion("Super attack(3)", 45, 100, ItemID._3DOSE2ATTACK, ItemID.EYE_OF_NEWT),
			potion("Superantipoison(3)", 48, 106.3, ItemID._3DOSE2ANTIPOISON, ItemID.UNICORN_HORN_DUST));
		herb(ItemID.UNIDENTIFIED_AVANTOE, ItemID.AVANTOE, ItemID.AVANTOEVIAL, "Avantoe", 48, 10,
			potion("Fishing potion(3)", 50, 112.5, ItemID._3DOSEFISHERSPOTION, ItemID.SNAPE_GRASS),
			potion("Super energy(3)", 52, 117.5, ItemID._3DOSE2ENERGY, ItemID.MORTMYREMUSHROOM),
			potion("Hunter potion(3)", 53, 120, ItemID._3DOSEHUNTING, ItemID.HUNTINGBEAST_SABRETEETH_DUST));
		herb(ItemID.UNIDENTIFIED_KWUARM, ItemID.KWUARM, ItemID.KWUARMVIAL, "Kwuarm", 54, 11.3,
			potion("Super strength(3)", 55, 125, ItemID._3DOSE2STRENGTH, ItemID.LIMPWURT_ROOT),
			potion("Weapon poison", 60, 137.5, ItemID.WEAPON_POISON, ItemID.DRAGON_SCALE_DUST));
		herb(ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.SNAPDRAGON, ItemID.SNAPDRAGONVIAL, "Snapdragon", 59, 11.8,
			potion("Super restore(3)", 63, 142.5, ItemID._3DOSE2RESTORE, ItemID.RED_SPIDERS_EGGS));
		herb(ItemID.UNIDENTIFIED_CADANTINE, ItemID.CADANTINE, ItemID.CADANTINEVIAL, "Cadantine", 65, 12.5,
			potion("Super defence(3)", 66, 150, ItemID._3DOSE2DEFENSE, ItemID.WHITE_BERRIES));
		herb(ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME, ItemID.LANTADYMEVIAL, "Lantadyme", 67, 13.1,
			potion("Antifire potion(3)", 69, 157.5, ItemID._3DOSE1ANTIDRAGON, ItemID.DRAGON_SCALE_DUST),
			potion("Magic potion(3)", 76, 172.5, ItemID._3DOSE1MAGIC, ItemID.CACTUS_POTATO));
		herb(ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.DWARF_WEED, ItemID.DWARFWEEDVIAL, "Dwarf weed", 70, 13.8,
			potion("Ranging potion(3)", 72, 162.5, ItemID._3DOSERANGERSPOTION, ItemID.WINE_OF_ZAMORAK));
		herb(ItemID.UNIDENTIFIED_TORSTOL, ItemID.TORSTOL, ItemID.TORSTOLVIAL, "Torstol", 75, 15,
			potion("Zamorak brew(3)", 78, 175, ItemID._3DOSEPOTIONOFZAMORAK, ItemID.JANGERBERRIES));
	}

	// ---- Cooking (OSRS Wiki: Cooking, the fish table; a burnt fish gives nothing) ----

	private static void fish(int raw, String name, int level, double xp, int cooked)
	{
		add(Skill.COOKING, raw, step(name, level, xp, cooked, Rates.FISH));
	}

	private static void cooking()
	{
		fish(ItemID.RAW_SHRIMP, "Shrimps", 1, 30, ItemID.SHRIMP);
		fish(ItemID.RAW_ANCHOVIES, "Anchovies", 1, 30, ItemID.ANCHOVIES);
		fish(ItemID.RAW_SARDINE, "Sardine", 1, 40, ItemID.SARDINE);
		fish(ItemID.RAW_HERRING, "Herring", 5, 50, ItemID.HERRING);
		fish(ItemID.RAW_MACKEREL, "Mackerel", 10, 60, ItemID.MACKEREL);
		fish(ItemID.RAW_TROUT, "Trout", 15, 70, ItemID.TROUT);
		fish(ItemID.RAW_COD, "Cod", 18, 75, ItemID.COD);
		fish(ItemID.RAW_PIKE, "Pike", 20, 80, ItemID.PIKE);
		fish(ItemID.RAW_SALMON, "Salmon", 25, 90, ItemID.SALMON);
		fish(ItemID.RAW_TUNA, "Tuna", 30, 100, ItemID.TUNA);
		// "Cooking a karambwan requires level 30 Cooking, and grants 190 experience when successful."
		add(Skill.COOKING, ItemID.TBWT_RAW_KARAMBWAN, step("Cooked karambwan", 30, 190, ItemID.TBWT_COOKED_KARAMBWAN, Rates.KARAMBWAN));
		fish(ItemID.RAW_LOBSTER, "Lobster", 40, 120, ItemID.LOBSTER);
		fish(ItemID.RAW_BASS, "Bass", 43, 130, ItemID.BASS);
		fish(ItemID.RAW_SWORDFISH, "Swordfish", 45, 140, ItemID.SWORDFISH);
		fish(ItemID.RAW_MONKFISH, "Monkfish", 62, 150, ItemID.MONKFISH);
		fish(ItemID.RAW_SHARK, "Shark", 80, 210, ItemID.SHARK);
		fish(ItemID.RAW_SEATURTLE, "Sea turtle", 82, 211.3, ItemID.SEATURTLE);
		fish(ItemID.RAW_ANGLERFISH, "Anglerfish", 84, 230, ItemID.ANGLERFISH);
		fish(ItemID.RAW_DARK_CRAB, "Dark crab", 90, 215, ItemID.DARK_CRAB);
		fish(ItemID.RAW_MANTARAY, "Manta ray", 91, 216.3, ItemID.MANTARAY);
	}

	// ---- Fletching (OSRS Wiki: Fletching, the unstrung and strung bow tables) ----

	private static void bows(int logs, String wood, int shortLevel, double shortCut, double shortString, int shortU, int shortbow,
		int longLevel, double longCut, double longString, int longU, int longbow)
	{
		Step cutShort = step(wood + "shortbow (u)", shortLevel, shortCut, shortU, Rates.CUT);
		Step cutLong = step(wood + "longbow (u)", longLevel, longCut, longU, Rates.CUT);
		Step stringShort = step(wood + "shortbow", shortLevel, shortString, shortbow, Rates.STRING, ItemID.BOW_STRING, 1);
		Step stringLong = step(wood + "longbow", longLevel, longString, longbow, Rates.STRING, ItemID.BOW_STRING, 1);
		add(Skill.FLETCHING, logs, cutLong, stringLong);
		add(Skill.FLETCHING, logs, cutShort, stringShort);
		add(Skill.FLETCHING, logs, cutLong);
		add(Skill.FLETCHING, logs, cutShort);
		add(Skill.FLETCHING, longU, stringLong);
		add(Skill.FLETCHING, shortU, stringShort);
	}

	private static void fletching()
	{
		bows(ItemID.LOGS, "", 5, 5, 5, ItemID.UNSTRUNG_SHORTBOW, ItemID.SHORTBOW,
			10, 10, 10, ItemID.UNSTRUNG_LONGBOW, ItemID.LONGBOW);
		bows(ItemID.OAK_LOGS, "Oak ", 20, 16.5, 16.5, ItemID.UNSTRUNG_OAK_SHORTBOW, ItemID.OAK_SHORTBOW,
			25, 25, 25, ItemID.UNSTRUNG_OAK_LONGBOW, ItemID.OAK_LONGBOW);
		bows(ItemID.WILLOW_LOGS, "Willow ", 35, 33.3, 33.2, ItemID.UNSTRUNG_WILLOW_SHORTBOW, ItemID.WILLOW_SHORTBOW,
			40, 41.5, 41.5, ItemID.UNSTRUNG_WILLOW_LONGBOW, ItemID.WILLOW_LONGBOW);
		bows(ItemID.MAPLE_LOGS, "Maple ", 50, 50, 50, ItemID.UNSTRUNG_MAPLE_SHORTBOW, ItemID.MAPLE_SHORTBOW,
			55, 58.3, 58.2, ItemID.UNSTRUNG_MAPLE_LONGBOW, ItemID.MAPLE_LONGBOW);
		bows(ItemID.YEW_LOGS, "Yew ", 65, 67.5, 67.5, ItemID.UNSTRUNG_YEW_SHORTBOW, ItemID.YEW_SHORTBOW,
			70, 75, 75, ItemID.UNSTRUNG_YEW_LONGBOW, ItemID.YEW_LONGBOW);
		bows(ItemID.MAGIC_LOGS, "Magic ", 80, 83.3, 83.2, ItemID.UNSTRUNG_MAGIC_SHORTBOW, ItemID.MAGIC_SHORTBOW,
			85, 91.5, 91.5, ItemID.UNSTRUNG_MAGIC_LONGBOW, ItemID.MAGIC_LONGBOW);
	}

	// ---- Firemaking (OSRS Wiki: Firemaking, the list of combustible logs) ----

	private static void firemaking()
	{
		burn(ItemID.LOGS, "Logs", 1, 40);
		burn(ItemID.OAK_LOGS, "Oak logs", 15, 60);
		burn(ItemID.WILLOW_LOGS, "Willow logs", 30, 90);
		burn(ItemID.TEAK_LOGS, "Teak logs", 35, 105);
		burn(ItemID.MAPLE_LOGS, "Maple logs", 45, 135);
		burn(ItemID.MAHOGANY_LOGS, "Mahogany logs", 50, 157.5);
		burn(ItemID.YEW_LOGS, "Yew logs", 60, 202.5);
		burn(ItemID.MAGIC_LOGS, "Magic logs", 75, 303.8);
		burn(ItemID.REDWOOD_LOGS, "Redwood logs", 90, 350);
	}

	private static void burn(int logs, String name, int level, double xp)
	{
		add(Skill.FIREMAKING, logs, step("Burn", level, xp, logs, Rates.LOGS));
	}

	// ---- Crafting (OSRS Wiki: the uncut gem pages, Crafting's dragonhide table, Molten glass) ----

	private static void gem(int uncut, String name, int level, double xp, int cut)
	{
		add(Skill.CRAFTING, uncut, step(name, level, xp, cut, Rates.GEMS));
	}

	/** Dragon leather into vambraces (1), chaps (2) or a body (3): the same experience a leather. */
	private static Step[] leather(String colour, int vambLevel, double vambXp, int vamb, int chapsLevel, int chaps, int bodyLevel, int body)
	{
		return new Step[]{
			perUnit(colour + " d'hide body", bodyLevel, vambXp * 3, 3, body, Rates.BODIES),
			perUnit(colour + " d'hide chaps", chapsLevel, vambXp * 2, 2, chaps, null),
			perUnit(colour + " d'hide vambraces", vambLevel, vambXp, 1, vamb, null),
		};
	}

	private static void hide(int hide, int leather, Step[] made)
	{
		// Tanning gives no experience; the tanner does it for coins.
		Step tan = new Step("Tan the hide", 1, 0, 0, leather, null, needs());
		for (Step s : made)
		{
			add(Skill.CRAFTING, leather, s);
			add(Skill.CRAFTING, hide, tan, s);
		}
	}

	private static Step[] blown()
	{
		return new Step[]{
			step("Empty light orb", 87, 70, ItemID.DORGESH_LIGHT_BULB, Rates.GLASS),
			step("Lantern lens", 49, 55, ItemID.BULLSEYE_LANTERN_LENS, Rates.GLASS),
			step("Unpowered orb", 46, 52.5, ItemID.STAFFORB, Rates.GLASS),
			step("Empty fishbowl", 42, 42.5, ItemID.FISHBOWL_WATER, Rates.GLASS),
			step("Vial", 33, 35, ItemID.VIAL_EMPTY, Rates.GLASS),
			step("Empty oil lamp", 12, 25, ItemID.OIL_LAMP_EMPTY, Rates.GLASS),
			step("Empty candle lantern", 4, 19, ItemID.CANDLE_LANTERN_EMPTY, Rates.GLASS),
			step("Beer glass", 1, 17.5, ItemID.BEER_GLASS, Rates.GLASS),
		};
	}

	private static void crafting()
	{
		gem(ItemID.UNCUT_SAPPHIRE, "Sapphire", 20, 50, ItemID.SAPPHIRE);
		gem(ItemID.UNCUT_EMERALD, "Emerald", 27, 67.5, ItemID.EMERALD);
		gem(ItemID.UNCUT_RUBY, "Ruby", 34, 85, ItemID.RUBY);
		gem(ItemID.UNCUT_DIAMOND, "Diamond", 43, 107.5, ItemID.DIAMOND);
		gem(ItemID.UNCUT_DRAGONSTONE, "Dragonstone", 55, 137.5, ItemID.DRAGONSTONE);
		gem(ItemID.UNCUT_ONYX, "Onyx", 67, 167.5, ItemID.ONYX);
		hide(ItemID.DRAGONHIDE_GREEN, ItemID.DRAGON_LEATHER,
			leather("Green", 57, 62, ItemID.DRAGON_VAMBRACES, 60, ItemID.DRAGONHIDE_CHAPS, 63, ItemID.DRAGONHIDE_BODY));
		hide(ItemID.DRAGONHIDE_BLUE, ItemID.DRAGON_LEATHER_BLUE,
			leather("Blue", 66, 70, ItemID.BLUE_DRAGON_VAMBRACES, 68, ItemID.BLUE_DRAGONHIDE_CHAPS, 71, ItemID.BLUE_DRAGONHIDE_BODY));
		hide(ItemID.DRAGONHIDE_RED, ItemID.DRAGON_LEATHER_RED,
			leather("Red", 73, 78, ItemID.RED_DRAGON_VAMBRACES, 75, ItemID.RED_DRAGONHIDE_CHAPS, 77, ItemID.RED_DRAGONHIDE_BODY));
		hide(ItemID.DRAGONHIDE_BLACK, ItemID.DRAGON_LEATHER_BLACK,
			leather("Black", 79, 86, ItemID.BLACK_DRAGON_VAMBRACES, 82, ItemID.BLACK_DRAGONHIDE_CHAPS, 84, ItemID.BLACK_DRAGONHIDE_BODY));
		// "using a bucket of sand and soda ash on a furnace", "granting 20 Crafting experience".
		Step smelt = step("Molten glass", 1, 20, ItemID.MOLTEN_GLASS, null, ItemID.SODA_ASH, 1);
		for (Step b : blown())
		{
			add(Skill.CRAFTING, ItemID.MOLTEN_GLASS, b);
			add(Skill.CRAFTING, ItemID.BUCKET_SAND, smelt, b);
		}
		add(Skill.CRAFTING, ItemID.BUCKET_SAND, smelt);
	}

	// ---- Smithing (OSRS Wiki: Smithing's bar table, and each dagger and platebody) ----

	/** A bar at the anvil: a dagger takes one, a platebody five, for the same experience a bar. */
	private static Step[] anvil(String metal, int daggerLevel, double perBar, int dagger, int bodyLevel, int body)
	{
		return new Step[]{
			perUnit(metal + " platebody", bodyLevel, perBar * 5, 5, body, Rates.ANVIL),
			step(metal + " dagger", daggerLevel, perBar, dagger, null),
		};
	}

	private static void metal(int ore, int bar, Step smelt, Step[] items)
	{
		for (Step item : items)
		{
			add(Skill.SMITHING, bar, item);
			add(Skill.SMITHING, ore, smelt, item);
		}
		add(Skill.SMITHING, ore, smelt);
	}

	private static void smithing()
	{
		metal(ItemID.COPPER_ORE, ItemID.BRONZE_BAR, step("Bronze bar", 1, 6.2, ItemID.BRONZE_BAR, null, ItemID.TIN_ORE, 1),
			anvil("Bronze", 1, 12.5, ItemID.BRONZE_DAGGER, 18, ItemID.BRONZE_PLATEBODY));
		// Iron ore has its own steel recipe below; both come from the same ore.
		Step[] iron = anvil("Iron", 15, 25, ItemID.IRON_DAGGER, 33, ItemID.IRON_PLATEBODY);
		Step[] steel = anvil("Steel", 30, 37.5, ItemID.STEEL_DAGGER, 48, ItemID.STEEL_PLATEBODY);
		metal(ItemID.IRON_ORE, ItemID.IRON_BAR, step("Iron bar", 15, 12.5, ItemID.IRON_BAR, null), iron);
		metal(ItemID.IRON_ORE, ItemID.STEEL_BAR, step("Steel bar", 30, 17.5, ItemID.STEEL_BAR, null, ItemID.COAL, 2), steel);
		metal(ItemID.MITHRIL_ORE, ItemID.MITHRIL_BAR, step("Mithril bar", 50, 30, ItemID.MITHRIL_BAR, null, ItemID.COAL, 4),
			anvil("Mithril", 50, 50, ItemID.MITHRIL_DAGGER, 68, ItemID.MITHRIL_PLATEBODY));
		metal(ItemID.ADAMANTITE_ORE, ItemID.ADAMANTITE_BAR, step("Adamantite bar", 70, 37.5, ItemID.ADAMANTITE_BAR, null, ItemID.COAL, 6),
			anvil("Adamant", 70, 62.5, ItemID.ADAMANT_DAGGER, 88, ItemID.ADAMANT_PLATEBODY));
		metal(ItemID.RUNITE_ORE, ItemID.RUNITE_BAR, step("Runite bar", 85, 50, ItemID.RUNITE_BAR, null, ItemID.COAL, 8),
			anvil("Rune", 85, 75, ItemID.RUNE_DAGGER, 99, ItemID.RUNE_PLATEBODY));
		add(Skill.SMITHING, ItemID.SILVER_ORE, step("Silver bar", 20, 13.7, ItemID.SILVER_BAR, null));
		add(Skill.SMITHING, ItemID.GOLD_ORE, step("Gold bar", 40, 22.5, ItemID.GOLD_BAR, null));
	}

	// ---- Construction (OSRS Wiki: each piece of furniture's page) ----

	private static void construction()
	{
		// "It requires 4 Construction to build and gives 115 experience when built": 4 planks, 4 steel nails.
		add(Skill.CONSTRUCTION, ItemID.WOODPLANK, perUnit("Wooden bookcase", 4, 115, 4, ItemID.POH_BOOKCASE_1, null, ItemID.NAILS, 1));
		add(Skill.CONSTRUCTION, ItemID.PLANK_OAK, perUnit("Oak larder", 33, 480, 8, ItemID.POH_LARDER_2, Rates.OAK));
		add(Skill.CONSTRUCTION, ItemID.PLANK_OAK, perUnit("Oak chair", 19, 120, 2, ItemID.POH_ARMCHAIR_4, Rates.OAK));
		add(Skill.CONSTRUCTION, ItemID.PLANK_TEAK, perUnit("Teak table", 38, 360, 4, ItemID.POH_DINING_TABLE_4, null));
		add(Skill.CONSTRUCTION, ItemID.PLANK_MAHOGANY, perUnit("Mahogany table", 52, 840, 6, ItemID.POH_DINING_TABLE_6, Rates.MAHOGANY));
	}

	// ---- Prayer (OSRS Wiki: each bone's page, Demonic ashes, the altars) ----

	private static void bone(int id, String name, int level, double xp)
	{
		BONES.put(id, new double[]{level, xp});
		for (Method m : Method.values())
		{
			Map<Integer, List<Recipe>> book = PRAYER.computeIfAbsent(m, k -> new LinkedHashMap<>());
			Step s;
			switch (m)
			{
				case GILDED_ALTAR:
					s = step("Gilded altar", level, xp * m.getMultiplier(), id, Rates.GILDED);
					break;
				case ECTOFUNTUS:
					// Bonemeal "consists of a pot filled with any kind of bones", worshipped "along with a bucket of slime".
					s = step("Ectofuntus", level, xp * m.getMultiplier(), id, Rates.ECTO,
						ItemID.POT_EMPTY, 1, ItemID.BUCKET_ECTOPLASM, 1);
					break;
				case CHAOS_ALTAR:
					// Half the offerings keep their bone: two offerings a bone, on average.
					s = new Step("Chaos altar", level, xp * m.getMultiplier(), 2, id, Rates.CHAOS, needs());
					break;
				default:
					s = step("Bury", level, xp, id, null);
			}
			book.computeIfAbsent(id, k -> new ArrayList<>()).add(new Recipe(Skill.PRAYER, Collections.singletonList(s)));
		}
	}

	private static void ashes(int id, String name, double xp)
	{
		for (Method m : Method.values())
		{
			PRAYER.computeIfAbsent(m, k -> new LinkedHashMap<>())
				.computeIfAbsent(id, k -> new ArrayList<>())
				.add(new Recipe(Skill.PRAYER, Collections.singletonList(step("Scatter", 1, xp, id, null))));
		}
	}

	private static void prayer()
	{
		bone(ItemID.BONES, "Bones", 1, 4.5);
		bone(ItemID.BIG_BONES, "Big bones", 1, 15);
		bone(ItemID.BABYDRAGON_BONES, "Babydragon bones", 1, 30);
		bone(ItemID.WYRM_BONES, "Wyrm bones", 1, 50);
		bone(ItemID.DRAGON_BONES, "Dragon bones", 1, 72);
		bone(ItemID.WYVERN_BONES, "Wyvern bones", 1, 72);
		bone(ItemID.DRAKE_BONES, "Drake bones", 1, 80);
		// 85 when buried away from Lava Dragon Isle, where they give 340: the island is not counted.
		bone(ItemID.LAVA_DRAGON_BONES, "Lava dragon bones", 1, 85);
		bone(ItemID.HYDRA_BONES, "Hydra bones", 1, 110);
		bone(ItemID.DAGANNOTH_KING_BONES, "Dagannoth bones", 1, 125);
		// "require a Prayer level of 70 (unboostable) to use".
		bone(ItemID.DRAGON_BONES_SUPERIOR, "Superior dragon bones", 70, 150);
		ashes(ItemID.FIENDISH_ASHES, "Fiendish ashes", 10);
		ashes(ItemID.VILE_ASHES, "Vile ashes", 25);
		ashes(ItemID.MALICIOUS_ASHES, "Malicious ashes", 65);
		ashes(ItemID.ABYSSAL_ASHES, "Abyssal ashes", 85);
		ashes(ItemID.INFERNAL_ASHES, "Infernal ashes", 110);
	}

	/** Bones and their burying experience, for the facts test. */
	static Map<Integer, double[]> bones()
	{
		return Collections.unmodifiableMap(BONES);
	}
}
