package com.codex.steward.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ArchitectTest
{
	/** Worn slots, as EquipmentInventorySlot numbers them. */
	private static final int HEAD = 0;
	private static final int WEAPON = 3;
	private static final int BODY = 4;
	private static final int SHIELD = 5;
	private static final int LEGS = 7;
	private static final int AMMO = 13;
	private static final int AMULET = 2;
	private static final int RING = 12;

	private final Map<Integer, Architect.Facts> facts = new HashMap<>();
	private final Map<Integer, Long> bank = new LinkedHashMap<>();

	private void item(int id, String name, boolean tradeable, long qty)
	{
		facts.put(id, Architect.Facts.item(name, tradeable));
		bank.put(id, qty);
	}

	private void worn(int id, String name, int slot, int melee, int ranged, int magic, int defence)
	{
		facts.put(id, new Architect.Facts(name, true, slot, melee, ranged, magic, defence, false, false));
		bank.put(id, 1L);
	}

	private void food(int id, String name, long qty)
	{
		facts.put(id, new Architect.Facts(name, true, -1, 0, 0, 0, 0, true, false));
		bank.put(id, qty);
	}

	private void potion(int id, String name, long qty)
	{
		facts.put(id, new Architect.Facts(name, true, -1, 0, 0, 0, 0, false, true));
		bank.put(id, qty);
	}

	private Architect.Inputs inputs()
	{
		return new Architect.Inputs().bank(bank).facts(facts::get);
	}

	@Test
	public void gearGoesToTheStyleOfItsStrongestBonusAndArmourToTheStyleItHindersLeast()
	{
		worn(ItemID.ABYSSAL_WHIP, "Abyssal whip", WEAPON, 82 + 82, 0, 0, 0);
		worn(ItemID.MAGIC_SHORTBOW, "Magic shortbow", WEAPON, 0, 69, 0, 0);
		worn(ItemID.MYSTIC_HAT, "Mystic hat", HEAD, 0, 0, 4, 4);
		// A rune platebody gives no attack, and takes from magic and ranged: melee armour.
		worn(ItemID.RUNE_PLATEBODY, "Rune platebody", BODY, 0, -10, -30, 247);
		worn(ItemID.RUNE_PLATELEGS, "Rune platelegs", LEGS, 0, -11, -21, 196);
		// Worn with no bonus at all: a skilling outfit.
		worn(ItemID.GRACEFUL_HOOD, "Graceful hood", HEAD, 0, 0, 0, 0);
		Architect.Design d = Architect.design(inputs());
		assertArrayEquals(new int[]{ItemID.ABYSSAL_WHIP, -1, ItemID.RUNE_PLATEBODY, -1, ItemID.RUNE_PLATELEGS}, d.tab(Architect.Purpose.MELEE).getLayout());
		assertArrayEquals(new int[]{ItemID.MAGIC_SHORTBOW}, d.tab(Architect.Purpose.RANGED).getLayout());
		assertArrayEquals(new int[]{ItemID.MYSTIC_HAT}, d.tab(Architect.Purpose.MAGIC).getLayout());
		assertEquals(ItemID.GRACEFUL_HOOD, d.tab(Architect.Purpose.SKILLING).itemAt(0));
	}

	@Test
	public void gearRowsRunWeaponFirstThenHeadToFeetStrongestFirst()
	{
		worn(ItemID.RUNE_FULL_HELM, "Rune full helm", HEAD, 0, -6, -6, 120);
		worn(ItemID.DRAGON_PARRYINGDAGGER, "Dragon defender", SHIELD, 25 + 6, -50, -3, 100);
		worn(ItemID.DRAGON_SCIMITAR, "Dragon scimitar", WEAPON, 67 + 66, 0, 0, 0);
		worn(ItemID.ABYSSAL_WHIP, "Abyssal whip", WEAPON, 82 + 82, 0, 0, 0);
		worn(ItemID.ENCHANTED_ONYX_AMULET, "Amulet of fury", AMULET, 18, 10, 10, 67);
		Architect.Tab melee = Architect.design(inputs()).tab(Architect.Purpose.MELEE);
		// Weapons, whip before scimitar; then the shield; then the helm; the amulet after a gap.
		assertArrayEquals(new int[]{ItemID.ABYSSAL_WHIP, ItemID.DRAGON_SCIMITAR, -1, ItemID.DRAGON_PARRYINGDAGGER, -1, ItemID.RUNE_FULL_HELM, -1,
			ItemID.ENCHANTED_ONYX_AMULET}, melee.getLayout());
	}

	@Test
	public void teleportsAndToolsComeBeforeGearForWhatIsBoth()
	{
		worn(ItemID.AMULET_OF_GLORY_4, "Amulet of glory(4)", AMULET, 16, 10, 10, 9);
		worn(ItemID.RING_OF_DUELING_8, "Ring of dueling(8)", RING, 0, 0, 0, 0);
		worn(ItemID.DRAGON_PICKAXE, "Dragon pickaxe", WEAPON, 38 + 42, 0, 0, 0);
		worn(ItemID.DRAGON_HARPOON, "Dragon harpoon", WEAPON, 38 + 42, 0, 0, 0);
		item(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Teleport to house", true, 40);
		item(ItemID.HAMMER, "Hammer", true, 1);
		Architect.Design d = Architect.design(inputs());
		assertNull(d.tab(Architect.Purpose.MELEE));
		// Jewellery on the first row, tablets on their own row.
		assertArrayEquals(new int[]{ItemID.AMULET_OF_GLORY_4, ItemID.RING_OF_DUELING_8, -1, -1, -1, -1, -1, -1, ItemID.POH_TABLET_TELEPORTTOHOUSE},
			d.tab(Architect.Purpose.TELEPORTS).getLayout());
		assertArrayEquals(new int[]{ItemID.DRAGON_HARPOON, ItemID.DRAGON_PICKAXE, ItemID.HAMMER}, d.tab(Architect.Purpose.SKILLING).getLayout());
	}

	@Test
	public void suppliesHoldCoinsAndFoodThenPotionsByKindThenRunes()
	{
		item(ItemID.COINS, "Coins", true, 1_000_000);
		food(ItemID.SHARK, "Shark", 200);
		food(ItemID.MANTARAY, "Manta ray", 500);
		potion(ItemID._1DOSEPRAYERRESTORE, "Prayer potion(1)", 3);
		potion(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 50);
		potion(ItemID._4DOSESTAMINA, "Stamina potion(4)", 5);
		item(ItemID.NATURERUNE, "Nature rune", true, 3000);
		item(ItemID.AIRRUNE, "Air rune", true, 9000);
		Architect.Tab supplies = Architect.design(inputs()).tab(Architect.Purpose.SUPPLIES);
		int[] want = {
			ItemID.COINS, -1, ItemID.MANTARAY, ItemID.SHARK, -1, -1, -1, -1,
			ItemID._4DOSEPRAYERRESTORE, ItemID._1DOSEPRAYERRESTORE, ItemID._4DOSESTAMINA, -1, -1, -1, -1, -1,
			ItemID.AIRRUNE, ItemID.NATURERUNE};
		assertArrayEquals(want, supplies.getLayout());
	}

	@Test
	public void sellTakesWhatTheClearOutListsAndQuestsTakeWhatUnfinishedWorkNeeds()
	{
		item(ItemID.COINS, "Coins", true, 10);
		item(1001, "Old quest thing", true, 1);
		item(1002, "Barcrawl card", false, 1);
		item(1003, "Spent key", false, 1);
		item(1004, "Pet rock", false, 1);
		worn(ItemID.ABYSSAL_WHIP, "Abyssal whip", WEAPON, 164, 0, 0, 0);
		Map<String, List<Integer>> needed = new LinkedHashMap<>();
		needed.put("Dragon Slayer II", Arrays.asList(1002, 1001, ItemID.ABYSSAL_WHIP));
		needed.put("Monkey Madness II", Arrays.asList(1002));
		Architect.Design d = Architect.design(inputs().sell(Arrays.asList(1003, 1001, 4242)).needed(needed));
		// The clear-out wins over a quest, and the whip stays with the gear.
		assertArrayEquals(new int[]{1003, 1001}, d.tab(Architect.Purpose.SELL).getLayout());
		assertArrayEquals(new int[]{1002}, d.tab(Architect.Purpose.QUESTS).getLayout());
		assertArrayEquals(new int[]{1004}, d.tab(Architect.Purpose.KEEPSAKES).getLayout());
		assertEquals(ItemID.ABYSSAL_WHIP, d.tab(Architect.Purpose.MELEE).itemAt(0));
		assertEquals(6, d.getTotal());
	}

	@Test
	public void tabsComeInPurposeOrderAndEmptyOnesAreLeftOut()
	{
		item(ItemID.COINS, "Coins", true, 10);
		item(1004, "Pet rock", false, 1);
		List<Architect.Purpose> order = new ArrayList<>();
		for (Architect.Tab t : Architect.design(inputs()).getTabs())
		{
			order.add(t.getPurpose());
		}
		assertEquals(Arrays.asList(Architect.Purpose.SUPPLIES, Architect.Purpose.KEEPSAKES), order);
		assertTrue(Architect.design(new Architect.Inputs()).isEmpty());
	}

	@Test
	public void everyItemHasExactlyOneHomeOutsideTheKitsTab()
	{
		food(ItemID.SHARK, "Shark", 200);
		potion(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 50);
		worn(ItemID.ABYSSAL_WHIP, "Abyssal whip", WEAPON, 164, 0, 0, 0);
		item(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Teleport to house", true, 40);
		Plans.Kit kit = new Plans.Kit("Slayer", new java.util.LinkedHashSet<>(Arrays.asList(ItemID.SHARK, ItemID._4DOSEPRAYERRESTORE)),
			Arrays.asList(Arrays.asList(ItemID.SHARK), Arrays.asList(ItemID._4DOSEPRAYERRESTORE)));
		Architect.Design d = Architect.design(inputs().kits(Collections.singletonList(kit)));
		Map<Integer, Integer> homes = new HashMap<>();
		for (Architect.Tab t : d.getTabs())
		{
			if (t.getPurpose() == Architect.Purpose.KITS)
			{
				continue;
			}
			for (int id : t.getLayout())
			{
				if (id > 0)
				{
					homes.merge(id, 1, Integer::sum);
				}
			}
		}
		assertEquals(bank.keySet(), homes.keySet());
		assertTrue(homes.values().stream().allMatch(n -> n == 1));
		assertEquals(Architect.Purpose.KITS, d.getTabs().get(0).getPurpose());
		assertEquals(4, d.getTotal());
	}

	@Test
	public void kitsAreInventoryWideBlocksInTheKitsOrderMostHeldFirstWithinASupply()
	{
		food(ItemID.SHARK, "Shark", 50);
		food(ItemID.MANTARAY, "Manta ray", 400);
		potion(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 50);
		potion(ItemID._4DOSERANGERSPOTION, "Ranging potion(4)", 8);
		item(ItemID.POH_TABLET_TELEPORTTOHOUSE, "Teleport to house", true, 40);
		item(ItemID.TELETAB_BARROWS, "Barrows teleport", true, 5);
		// FOOD pools many items; the bank holds two, the one held most goes first. Anglerfish isn't held.
		Plans.Kit vorkath = new Plans.Kit("Vorkath", new java.util.LinkedHashSet<>(), Arrays.asList(
			Arrays.asList(ItemID.ANGLERFISH, ItemID.SHARK, ItemID.MANTARAY),
			Arrays.asList(ItemID._4DOSEPRAYERRESTORE),
			Arrays.asList(ItemID._4DOSERANGERSPOTION),
			Arrays.asList(ItemID.POH_TABLET_TELEPORTTOHOUSE),
			Arrays.asList(ItemID.SHARK)));
		Plans.Kit barrows = new Plans.Kit("Barrows", new java.util.LinkedHashSet<>(), Arrays.asList(
			Arrays.asList(ItemID.SHARK), Arrays.asList(ItemID._4DOSEPRAYERRESTORE), Arrays.asList(ItemID.TELETAB_BARROWS)));
		Plans.Kit nothing = new Plans.Kit("Nothing held", new java.util.LinkedHashSet<>(), Arrays.asList(Arrays.asList(ItemID.ANGLERFISH)));
		Plans.Kit third = new Plans.Kit("Third", new java.util.LinkedHashSet<>(), Arrays.asList(Arrays.asList(ItemID.TELETAB_BARROWS)));
		Architect.Tab kits = Architect.design(inputs().kits(Arrays.asList(vorkath, barrows, nothing, third))).tab(Architect.Purpose.KITS);
		assertNotNull(kits);
		int[] want = {
			ItemID.MANTARAY, ItemID.SHARK, ItemID._4DOSEPRAYERRESTORE, ItemID._4DOSERANGERSPOTION,
			ItemID.SHARK, ItemID._4DOSEPRAYERRESTORE, ItemID.TELETAB_BARROWS, -1,
			ItemID.POH_TABLET_TELEPORTTOHOUSE, -1, -1, -1, -1, -1, -1, -1,
			-1, -1, -1, -1, -1, -1, -1, -1,
			ItemID.TELETAB_BARROWS};
		assertArrayEquals(want, kits.getLayout());
		assertEquals(3, kits.getBlocks().size());
		assertEquals("Vorkath", kits.getBlocks().get(0).getName());
		assertEquals(2, kits.getBlocks().get(0).getHeight());
		assertEquals(4, kits.getBlocks().get(1).getColumn());
		assertEquals(3, kits.getBlocks().get(2).getRow());
		// Shown twice, counted once.
		assertEquals(6, kits.items());
	}

	@Test
	public void noKitsHeldMeansNoKitsTab()
	{
		food(ItemID.SHARK, "Shark", 50);
		Plans.Kit kit = new Plans.Kit("Zulrah", new java.util.LinkedHashSet<>(), Arrays.asList(Arrays.asList(ItemID.ANGLERFISH)));
		assertNull(Architect.design(inputs().kits(Collections.singletonList(kit))).tab(Architect.Purpose.KITS));
	}

	@Test
	public void kitsReadFromQuartermasterKeepEachSupplysItemsInOrder()
	{
		String v1 = "{\"version\":1,\"kits\":[{\"id\":\"kit:1\",\"name\":\"Vorkath\",\"needs\":["
			+ "{\"supply\":\"FOOD\",\"items\":{\"13441\":22,\"385\":20}},{\"supply\":\"item:8013\",\"items\":{\"8013\":1}},"
			+ "{\"supply\":\"GONE\",\"items\":{}}]}]}";
		List<Plans.Kit> kits = Plans.readKits(new Gson(), v1, null);
		assertEquals(Arrays.asList(Arrays.asList(13441, 385), Arrays.asList(8013)), kits.get(0).getNeeds());
	}

	@Test
	public void packingLeavesAGapBetweenGroupsAndStartsEachSectionOnANewRow()
	{
		List<List<List<Integer>>> sections = Arrays.asList(
			Arrays.asList(Arrays.asList(1, 2, 3), Arrays.asList(4, 5), Arrays.asList(6, 7, 8)),
			Arrays.asList(Arrays.asList(9, 10, 11, 12, 13, 14, 15, 16, 17, 18), Arrays.asList(19)));
		int[] want = {
			1, 2, 3, -1, 4, 5, -1, -1,
			6, 7, 8, -1, -1, -1, -1, -1,
			9, 10, 11, 12, 13, 14, 15, 16,
			17, 18, -1, 19};
		assertArrayEquals(want, Architect.pack(sections));
	}

	@Test
	public void movesAreTheItemsOutsideTheLongestRunAlreadyInOrder()
	{
		List<Integer> home = Arrays.asList(1, 2, 3, 4, 5, 6);
		assertArrayEquals(new int[]{0, 6}, Architect.moves(home, Arrays.asList(1, 2, 3, 4, 5, 6)));
		// Reversed: only one can stay.
		assertArrayEquals(new int[]{5, 6}, Architect.moves(home, Arrays.asList(6, 5, 4, 3, 2, 1)));
		// One item out of place is one drag; items the design does not place are not counted.
		assertArrayEquals(new int[]{1, 6}, Architect.moves(home, Arrays.asList(1, 2, 6, 99, 3, 4, 5)));
		assertArrayEquals(new int[]{-1, 0}, Architect.moves(home, Collections.emptyList()));
	}

	@Test
	public void theDesignSaysHowFarTheBankAsItSitsIsFromIt()
	{
		item(ItemID.COINS, "Coins", true, 10);
		food(ItemID.SHARK, "Shark", 50);
		item(1004, "Pet rock", false, 1);
		Architect.Design d = Architect.design(inputs().order(Arrays.asList(1004, ItemID.SHARK, ItemID.COINS)));
		assertEquals(2, d.getMoves());
		assertEquals(1, d.getInPlace());
		assertEquals(-1, Architect.design(inputs()).getMoves());
	}

	@Test
	public void exportIsBankTagsOwnFormatWithTheIconAndEveryFilledSlot()
	{
		item(ItemID.COINS, "Coins", true, 10);
		food(ItemID.SHARK, "Shark", 50);
		Architect.Tab supplies = Architect.design(inputs()).tab(Architect.Purpose.SUPPLIES);
		assertEquals("banktags,1,codex supplies," + ItemID.COINS + ",layout,0," + ItemID.COINS + ",2," + ItemID.SHARK, supplies.export());
	}

	@Test
	public void namesTellTheirRow()
	{
		assertEquals(Architect.Craft.ORES, Architect.craft("runite ore"));
		assertEquals(Architect.Craft.ORES, Architect.craft("coal"));
		assertEquals(Architect.Craft.LOGS, Architect.craft("magic logs"));
		assertEquals(Architect.Craft.HERBS, Architect.craft("grimy ranarr weed"));
		assertEquals(Architect.Craft.HERBS, Architect.craft("ranarr weed"));
		assertEquals(Architect.Craft.SEEDS, Architect.craft("ranarr seed"));
		assertEquals(Architect.Craft.FISH, Architect.craft("raw shark"));
		assertEquals(Architect.Craft.GEMS, Architect.craft("uncut diamond"));
		assertEquals(Architect.Craft.HIDES, Architect.craft("green dragonhide"));
		assertEquals(Architect.Craft.ESSENCE, Architect.craft("pure essence"));
		assertEquals(Architect.Craft.OTHER, Architect.craft("eye of newt"));
		assertTrue(Architect.isTeleport("varrock teleport"));
		assertTrue(Architect.isTeleport("games necklace(8)"));
		assertFalse(Architect.isTeleport("amulet of fury"));
		assertEquals("prayer potion", Architect.family("Prayer potion(4)"));
		assertEquals(4, Architect.doses("Prayer potion(4)"));
		assertEquals(0, Architect.doses("Shark"));
	}
}
