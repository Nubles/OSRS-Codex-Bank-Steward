package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ElsewhereTest
{
	private static final int SHED = 1;
	private static final int BRIDGE = 2;
	private static final int PALACE = 3;

	/** Three made-up units: an easy one wanting a bronze axe and leather boots, a medium one, a hard one. */
	private static final List<Elsewhere.Unit> UNITS = Arrays.asList(
		new Elsewhere.Unit(SHED, 1, "Near a Shed", Arrays.asList(new int[]{ItemID.BRONZE_AXE}, new int[]{ItemID.LEATHER_BOOTS})),
		new Elsewhere.Unit(BRIDGE, 2, "On the Bridge", Arrays.asList(new int[]{ItemID.STAFF_OF_AIR}, new int[]{ItemID.BRONZE_FULL_HELM})),
		new Elsewhere.Unit(PALACE, 3, "Palace", Arrays.asList(new int[]{ItemID.RUNE_PLATEBODY, ItemID.MITHRIL_PLATEBODY},
			new int[]{ItemID.BRONZE_AXE})));

	private static Map<Integer, Long> bank(long... pairs)
	{
		Map<Integer, Long> bank = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			bank.put((int) pairs[i], pairs[i + 1]);
		}
		return bank;
	}

	private static Map<Integer, Boolean> built(int... ids)
	{
		Map<Integer, Boolean> map = new HashMap<>();
		for (Elsewhere.Unit u : UNITS)
		{
			map.put(u.getObjectId(), false);
		}
		for (int id : ids)
		{
			map.put(id, true);
		}
		return map;
	}

	private static Elsewhere.Store find(List<Elsewhere.Store> stores, Elsewhere.Kind kind)
	{
		for (Elsewhere.Store s : stores)
		{
			if (s.getKind() == kind)
			{
				return s;
			}
		}
		return null;
	}

	private static Set<Integer> moved(Elsewhere.Store store)
	{
		Set<Integer> ids = new HashSet<>();
		for (Elsewhere.Move m : store.getMoves())
		{
			ids.add(m.getItemId());
		}
		return ids;
	}

	@Test
	public void theLeprechaunTakesWholeStacksThatFitAndOneWateringCan()
	{
		Map<Integer, Long> bank = bank(ItemID.RAKE, 1, ItemID.SECATEURS, 1, ItemID.FAIRY_ENCHANTED_SECATEURS, 1,
			ItemID.WATERING_CAN_8, 1, ItemID.WATERING_CAN_0, 1, ItemID.BUCKET_COMPOST, 1200, ItemID.BUCKET_SUPERCOMPOST, 40,
			ItemID.ABYSSAL_WHIP, 1);
		List<Elsewhere.Store> stores = Elsewhere.plan(bank, new Elsewhere.Access(true, 1, 1, null), UNITS, null, null);
		Elsewhere.Store lep = find(stores, Elsewhere.Kind.LEPRECHAUN);
		assertNotNull(lep);
		// 1,200 compost is more than the 1,000 it holds: part of a stack frees no slot, so none goes.
		// One watering can fits, the fullest first; the whip has no home.
		assertEquals(new HashSet<>(Arrays.asList(ItemID.RAKE, ItemID.SECATEURS, ItemID.FAIRY_ENCHANTED_SECATEURS,
			ItemID.WATERING_CAN_8, ItemID.BUCKET_SUPERCOMPOST)), moved(lep));
		assertEquals(5, lep.getFreed());
		assertEquals(5, Elsewhere.freed(stores));
	}

	@Test
	public void theLeprechaunAndVaultAreForMembersAndTheVaultForTheGuild()
	{
		Map<Integer, Long> bank = bank(ItemID.RAKE, 1, ItemID.RANARR_SEED, 12, ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 2);
		assertTrue(Elsewhere.plan(bank, new Elsewhere.Access(false, 99, 99, null), UNITS, null, null).isEmpty());

		List<Elsewhere.Store> low = Elsewhere.plan(bank, new Elsewhere.Access(true, Elsewhere.GUILD_FARMING - 1, 1, null), UNITS, null, null);
		assertNotNull(find(low, Elsewhere.Kind.LEPRECHAUN));
		assertNull(find(low, Elsewhere.Kind.SEED_VAULT));

		List<Elsewhere.Store> guild = Elsewhere.plan(bank, new Elsewhere.Access(true, Elsewhere.GUILD_FARMING, 1, null), UNITS, null, null);
		Elsewhere.Store vault = find(guild, Elsewhere.Kind.SEED_VAULT);
		assertEquals(new HashSet<>(Arrays.asList(ItemID.RANARR_SEED, ItemID.PLANTPOT_MAGIC_TREE_SAPLING)), moved(vault));
		assertEquals(2, vault.getFreed());
	}

	@Test
	public void kitItemsStayInTheBank()
	{
		Map<Integer, Long> bank = bank(ItemID.RAKE, 1, ItemID.SPADE, 1, ItemID.RANARR_SEED, 5);
		Set<Integer> kit = new HashSet<>(Arrays.asList(ItemID.SPADE, ItemID.RANARR_SEED));
		List<Elsewhere.Store> stores = Elsewhere.plan(bank, new Elsewhere.Access(true, 99, 1, null), UNITS, kit, kit);
		assertEquals(Collections.singleton(ItemID.RAKE), Elsewhere.items(stores));
	}

	@Test
	public void aBuiltUnitTakesItsWholeSetAndFreesTheSlotsOfLastOnes()
	{
		Map<Integer, Long> bank = bank(ItemID.BRONZE_AXE, 1, ItemID.LEATHER_BOOTS, 3);
		List<Elsewhere.Store> stores = Elsewhere.plan(bank, new Elsewhere.Access(false, 1, 1, built(SHED)), UNITS, null, null);
		assertEquals(1, stores.size());
		Elsewhere.Store shed = stores.get(0);
		assertEquals(Elsewhere.Kind.STASH, shed.getKind());
		assertEquals("Near a Shed", shed.getName());
		assertFalse(shed.isToBuild());
		// The axe was the only one; one pair of boots of three goes, so its slot stays.
		assertEquals(1, shed.getFreed());
		for (Elsewhere.Move m : shed.getMoves())
		{
			assertEquals(1, m.getQuantity());
			assertEquals(m.getItemId() == ItemID.BRONZE_AXE, m.freesSlot());
		}
		assertTrue(shed.getWhere().startsWith("Easy STASH unit"));
	}

	@Test
	public void aSetMissingAPartOrFreeingNothingIsLeftOut()
	{
		Map<Integer, Long> onlyAxe = bank(ItemID.BRONZE_AXE, 1);
		assertTrue(Elsewhere.plan(onlyAxe, new Elsewhere.Access(false, 1, 1, built(SHED)), UNITS, null, null).isEmpty());
		Map<Integer, Long> stacks = bank(ItemID.BRONZE_AXE, 4, ItemID.LEATHER_BOOTS, 2);
		assertTrue(Elsewhere.plan(stacks, new Elsewhere.Access(false, 1, 1, built(SHED)), UNITS, null, null).isEmpty());
	}

	@Test
	public void itemsAnUnfinishedQuestNeedsAreNeverLockedInAUnit()
	{
		Map<Integer, Long> bank = bank(ItemID.BRONZE_AXE, 1, ItemID.LEATHER_BOOTS, 1);
		Set<Integer> needed = Collections.singleton(ItemID.LEATHER_BOOTS);
		assertTrue(Elsewhere.plan(bank, new Elsewhere.Access(false, 1, 1, built(SHED)), UNITS, null, needed).isEmpty());
	}

	@Test
	public void unbuiltUnitsAreOfferedOnlyAtTheirConstructionLevelAndAfterBuiltOnes()
	{
		Map<Integer, Long> bank = bank(ItemID.BRONZE_AXE, 1, ItemID.LEATHER_BOOTS, 1, ItemID.RUNE_PLATEBODY, 1,
			ItemID.STAFF_OF_AIR, 1, ItemID.BRONZE_FULL_HELM, 1);
		// Nothing built, Construction 41: the easy unit (27) can be built, the medium one (42) cannot.
		List<Elsewhere.Store> stores = Elsewhere.plan(bank, new Elsewhere.Access(false, 1, 41, built()), UNITS, null, null);
		assertEquals(1, stores.size());
		assertTrue(stores.get(0).isToBuild());
		assertEquals("Near a Shed", stores.get(0).getName());
		assertTrue(stores.get(0).getWhere().contains("Construction 27, 2 planks"));
		// A unit still to build frees nothing yet, so it is not in the total.
		assertEquals(0, Elsewhere.freed(stores));

		// The hard unit built: it takes the one bronze axe first, so the unbuilt easy unit cannot have it.
		List<Elsewhere.Store> withPalace = Elsewhere.plan(bank, new Elsewhere.Access(false, 1, 99, built(PALACE)), UNITS, null, null);
		assertEquals("Palace", withPalace.get(0).getName());
		assertFalse(withPalace.get(0).isToBuild());
		assertEquals(2, withPalace.get(0).getFreed());
		assertEquals(2, withPalace.size());
		assertEquals("On the Bridge", withPalace.get(1).getName());
		assertTrue(withPalace.get(1).isToBuild());
	}

	@Test
	public void anUnknownAccountOffersNothingFromStash()
	{
		Map<Integer, Long> bank = bank(ItemID.BRONZE_AXE, 1, ItemID.LEATHER_BOOTS, 1);
		assertTrue(Elsewhere.plan(bank, Elsewhere.Access.UNKNOWN, UNITS, null, null).isEmpty());
	}

	@Test
	public void leprechaunAndVaultComeBeforeStashAndTakeTheirItemsFirst()
	{
		Elsewhere.Unit digger = new Elsewhere.Unit(9, 0, "Dig", Arrays.asList(new int[]{ItemID.SPADE}, new int[]{ItemID.BRONZE_AXE}));
		Map<Integer, Long> bank = bank(ItemID.SPADE, 1, ItemID.BRONZE_AXE, 1);
		Map<Integer, Boolean> b = Collections.singletonMap(9, true);
		List<Elsewhere.Store> stores = Elsewhere.plan(bank, new Elsewhere.Access(true, 1, 1, b), Collections.singletonList(digger), null, null);
		assertEquals(1, stores.size());
		assertEquals(Elsewhere.Kind.LEPRECHAUN, stores.get(0).getKind());
	}

	@Test
	public void theBundledUnitsAllLoad() throws Exception
	{
		Elsewhere.UnitsData data;
		try (Reader r = new InputStreamReader(Elsewhere.class.getResourceAsStream("/com/codex/steward/stash-units.json"), StandardCharsets.UTF_8))
		{
			data = new Gson().fromJson(r, Elsewhere.UnitsData.class);
		}
		List<Elsewhere.Unit> units = Elsewhere.units(data);
		assertEquals(data.units.size(), units.size());
		assertTrue(units.size() > 100);
		Set<Integer> ids = new HashSet<>();
		int[] perTier = new int[6];
		for (Elsewhere.Unit u : units)
		{
			assertTrue("one row per unit", ids.add(u.getObjectId()));
			assertFalse(u.getPlace().isEmpty());
			assertFalse(u.getParts().isEmpty());
			perTier[u.getTier()]++;
		}
		for (int n : perTier)
		{
			assertTrue(n > 0);
		}
	}

	@Test
	public void theStoresListEachItemOnce()
	{
		Set<Integer> vault = new HashSet<>();
		for (int id : Elsewhere.SEED_VAULT)
		{
			assertTrue(vault.add(id));
		}
		// The vault holds 104 kinds, every one there is.
		assertEquals(104, vault.size());
		Set<Integer> lep = new HashSet<>();
		for (Elsewhere.Slot slot : Elsewhere.LEPRECHAUN)
		{
			for (int id : slot.items)
			{
				assertTrue(lep.add(id));
				assertFalse(vault.contains(id));
			}
		}
	}

	@Test
	public void unreadableRowsAreLeftOut()
	{
		Elsewhere.UnitsData data = new Gson().fromJson("{\"units\":[[5,4,\"Fine\",[1,2]],[6,3,\"Two tiers\",[1]],[\"x\"],[7,2,\"No parts\"]]}",
			Elsewhere.UnitsData.class);
		List<Elsewhere.Unit> units = Elsewhere.units(data);
		assertEquals(1, units.size());
		assertEquals(2, units.get(0).getTier());
	}

	@Test
	public void theKeyChangesWithThePlan()
	{
		Map<Integer, Long> bank = bank(ItemID.RAKE, 1);
		Elsewhere.Access members = new Elsewhere.Access(true, 1, 1, null);
		String one = Elsewhere.key(Elsewhere.plan(bank, members, UNITS, null, null));
		bank.put(ItemID.SPADE, 1L);
		assertFalse(one.equals(Elsewhere.key(Elsewhere.plan(bank, members, UNITS, null, null))));
		assertEquals(BankView.elsewhere(Collections.singleton(ItemID.RAKE)).size(), 1);
		assertTrue(BankView.isOurs(BankView.ELSEWHERE));
	}
}
