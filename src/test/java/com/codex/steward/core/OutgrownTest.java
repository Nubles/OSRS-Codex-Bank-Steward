package com.codex.steward.core;

import static com.codex.steward.core.GearFixture.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class OutgrownTest
{
	private static final long TODAY = 20_000;
	private static final Set<Outgrown.Style> MELEE = EnumSet.of(Outgrown.Style.MELEE);
	private static final Set<Outgrown.Style> MELEE_RANGED = EnumSet.of(Outgrown.Style.MELEE, Outgrown.Style.RANGED);
	private static final Set<Outgrown.Style> ALL = EnumSet.allOf(Outgrown.Style.class);

	private static Map<Integer, Long> wornLongAgo(int... ids)
	{
		Map<Integer, Long> worn = new HashMap<>();
		for (int id : ids)
		{
			worn.put(id, TODAY - 400);
		}
		return worn;
	}

	private static Map<Integer, Outgrown.Verdict> judge(List<Integer> bank, Set<Outgrown.Style> styles, Map<Integer, Long> worn,
		Integer... carried)
	{
		Set<Integer> owned = new HashSet<>(bank);
		owned.addAll(Arrays.asList(carried));
		Outgrown.Account account = new Outgrown.Account(styles, worn, TODAY, Collections.emptySet(), null);
		return Outgrown.judge(bank, owned, ITEMS::get, account, id -> Collections.emptyList(), id -> false);
	}

	@Test
	public void aWhipBeatsTheScimitarsForMelee()
	{
		assertTrue(Outgrown.beats(stats(WHIP), stats(RUNE_SCIMITAR), MELEE));
		assertTrue("a weapon's point of defence does not count", Outgrown.beats(stats(WHIP), stats(MITHRIL_SCIMITAR), ALL));
		assertFalse("never the other way", Outgrown.beats(stats(RUNE_SCIMITAR), stats(WHIP), MELEE));
		assertFalse("nothing to beat it on for magic alone", Outgrown.beats(stats(WHIP), stats(RUNE_SCIMITAR), EnumSet.of(Outgrown.Style.MAGIC)));
	}

	@Test
	public void aMaceIsJudgedOnCrush()
	{
		assertFalse("the whip has no crush", Outgrown.beats(stats(WHIP), stats(RUNE_MACE), MELEE));
	}

	@Test
	public void aTwoHandedWeaponNeverBeatsAOneHandedOne()
	{
		assertFalse(Outgrown.beats(stats(GRANITE_MAUL), stats(RUNE_MACE), MELEE));
		assertTrue("bow against bow", Outgrown.beats(stats(MAGIC_SHORTBOW), stats(MAPLE_SHORTBOW), MELEE_RANGED));
	}

	@Test
	public void magicBonusesCountOnlyForAMagicUser()
	{
		assertTrue(Outgrown.beats(stats(FACEGUARD), stats(MYSTIC_HAT), MELEE));
		assertFalse(Outgrown.beats(stats(FACEGUARD), stats(MYSTIC_HAT), ALL));
		// A strength amulet keeps its place beside fury: 10 strength to 8.
		assertFalse(Outgrown.beats(stats(FURY), stats(AMULET_OF_STRENGTH), MELEE));
		assertTrue(Outgrown.beats(stats(FURY), stats(AMULET_OF_POWER), ALL));
	}

	@Test
	public void defencesAlwaysCountOffTheWeaponSlot()
	{
		// The defender attacks better but defends far worse than a kiteshield.
		assertFalse(Outgrown.beats(stats(DEFENDER), stats(RUNE_KITESHIELD), MELEE));
		assertFalse("different slots", Outgrown.beats(stats(BANDOS_CHESTPLATE), stats(RUNE_PLATELEGS), MELEE));
	}

	@Test
	public void outgrownOnlyByAPieceSeenWorn()
	{
		Map<Integer, Outgrown.Verdict> v = judge(Arrays.asList(RUNE_SCIMITAR, ADAMANT_SCIMITAR, RUNE_PLATEBODY, ADAMANT_PLATEBODY),
			MELEE, wornLongAgo(WHIP), WHIP, BANDOS_CHESTPLATE);
		assertEquals(Outgrown.Why.OUTGROWN, v.get(RUNE_SCIMITAR).getWhy());
		assertEquals(WHIP, v.get(RUNE_SCIMITAR).getBetter());
		assertEquals(Outgrown.Why.OUTGROWN, v.get(ADAMANT_SCIMITAR).getWhy());
		// Bandos is carried but never seen worn: it may need levels the player lacks.
		assertEquals(Outgrown.Why.BETTER_UNWORN, v.get(RUNE_PLATEBODY).getWhy());
		assertEquals(BANDOS_CHESTPLATE, v.get(RUNE_PLATEBODY).getBetter());
		// The rune platebody beats the adamant one, but it has not been worn either.
		assertEquals(Outgrown.Why.BETTER_UNWORN, v.get(ADAMANT_PLATEBODY).getWhy());
	}

	@Test
	public void theStrongestWornPieceIsNamed()
	{
		Map<Integer, Outgrown.Verdict> v = judge(Arrays.asList(MITHRIL_SCIMITAR, ADAMANT_SCIMITAR, RUNE_SCIMITAR), MELEE,
			wornLongAgo(WHIP, RUNE_SCIMITAR), WHIP);
		assertEquals(WHIP, v.get(MITHRIL_SCIMITAR).getBetter());
		assertEquals(WHIP, v.get(RUNE_SCIMITAR).getBetter());
	}

	@Test
	public void piecesWithSpecialUsesAreKeptWithTheirReason()
	{
		Map<Integer, Outgrown.Verdict> v = judge(Arrays.asList(GLORY, DEFENDER, GRANITE_MAUL, STAFF_OF_FIRE, SARADOMIN_DHIDE_BODY,
			DHAROKS_PLATEBODY, BLACK_MASK, CHEF_HAT, MAPLE_SHORTBOW, FIGHTER_TORSO), ALL,
			wornLongAgo(FURY, WHIP, BLACK_DHIDE_BODY, FACEGUARD, MAGIC_SHORTBOW), FURY, WHIP, BLACK_DHIDE_BODY, FACEGUARD, MAGIC_SHORTBOW);
		assertEquals(Outgrown.Why.PASSIVE, v.get(GLORY).getWhy());
		assertEquals(Outgrown.Why.UNTRADEABLE, v.get(DEFENDER).getWhy());
		assertEquals(Outgrown.Why.UNTRADEABLE, v.get(FIGHTER_TORSO).getWhy());
		assertEquals(Outgrown.Why.SPECIAL_ATTACK, v.get(GRANITE_MAUL).getWhy());
		assertEquals(Outgrown.Why.RUNES, v.get(STAFF_OF_FIRE).getWhy());
		assertEquals(Outgrown.Why.GOD, v.get(SARADOMIN_DHIDE_BODY).getWhy());
		assertEquals("Saradomin", v.get(SARADOMIN_DHIDE_BODY).getDetail());
		assertEquals(Outgrown.Why.SET, v.get(DHAROKS_PLATEBODY).getWhy());
		assertEquals(Outgrown.Why.PASSIVE, v.get(BLACK_MASK).getWhy());
		assertEquals(Outgrown.Why.NO_STATS, v.get(CHEF_HAT).getWhy());
		assertEquals(Outgrown.Why.OUTGROWN, v.get(MAPLE_SHORTBOW).getWhy());
	}

	@Test
	public void anOptionOfItsOwnKeepsAPiece()
	{
		Map<Integer, Outgrown.Item> items = new HashMap<>(ITEMS);
		items.put(1, new Outgrown.Item("Some cape", true, false, new String[]{null, "Wear", "Teleport", null, "Drop"},
			new Outgrown.Stats(1, false, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)));
		items.put(2, new Outgrown.Item("Better cape", true, false, new String[]{null, "Wear", null, null, "Drop"},
			new Outgrown.Stats(1, false, 0, 0, 0, 0, 0, 9, 9, 9, 9, 9, 0, 0, 0, 0, 0)));
		Outgrown.Account account = new Outgrown.Account(MELEE, wornLongAgo(2), TODAY, Collections.emptySet(), null);
		Map<Integer, Outgrown.Verdict> v = Outgrown.judge(Collections.singletonList(1), Arrays.asList(1, 2), items::get, account,
			id -> Collections.emptyList(), id -> false);
		assertEquals(Outgrown.Why.OPTION, v.get(1).getWhy());
		assertEquals("Teleport", v.get(1).getDetail());
	}

	@Test
	public void wornLatelyQuestsRoadmapAndUnseenStylesKeepAPiece()
	{
		Map<Integer, Long> worn = wornLongAgo(WHIP);
		worn.put(RUNE_SCIMITAR, TODAY - 10);
		Map<Integer, Outgrown.Verdict> v = judge(Arrays.asList(RUNE_SCIMITAR), MELEE, worn, WHIP);
		assertEquals(Outgrown.Why.WORN, v.get(RUNE_SCIMITAR).getWhy());
		assertEquals("Worn 10 days ago", Outgrown.shortText(v.get(RUNE_SCIMITAR), NAMES::get));

		Outgrown.Account planned = new Outgrown.Account(MELEE, wornLongAgo(WHIP), TODAY, Collections.singleton(ADAMANT_SCIMITAR), "Whip road");
		Map<Integer, Outgrown.Verdict> p = Outgrown.judge(Arrays.asList(ADAMANT_SCIMITAR, MITHRIL_SCIMITAR, RUNE_SCIMITAR),
			Arrays.asList(ADAMANT_SCIMITAR, MITHRIL_SCIMITAR, RUNE_SCIMITAR, WHIP), ITEMS::get, planned,
			id -> id == MITHRIL_SCIMITAR ? Collections.singletonList("Some Quest") : Collections.emptyList(), id -> id == RUNE_SCIMITAR);
		assertEquals(Outgrown.Why.ROADMAP, p.get(ADAMANT_SCIMITAR).getWhy());
		assertEquals(Outgrown.Why.QUEST, p.get(MITHRIL_SCIMITAR).getWhy());
		assertEquals("Some Quest", Outgrown.shortText(p.get(MITHRIL_SCIMITAR), NAMES::get));
		assertEquals(Outgrown.Why.LASTING, p.get(RUNE_SCIMITAR).getWhy());

		Map<Integer, Outgrown.Verdict> none = judge(Arrays.asList(RUNE_SCIMITAR), Collections.emptySet(), wornLongAgo(WHIP), WHIP);
		assertEquals(Outgrown.Why.NO_STYLES, none.get(RUNE_SCIMITAR).getWhy());
	}

	@Test
	public void ammunitionStacksAndToolsAreNotJudged()
	{
		Map<Integer, Outgrown.Item> items = new HashMap<>();
		items.put(1, new Outgrown.Item("Rune arrow", true, true, null, new Outgrown.Stats(13, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 49, 0, 0, 0)));
		items.put(2, new Outgrown.Item("Adamant dart", true, true, null, new Outgrown.Stats(3, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 3)));
		items.put(net.runelite.api.gameval.ItemID.RUNE_PICKAXE, new Outgrown.Item("Rune pickaxe", true, false, null,
			new Outgrown.Stats(3, false, 26, -2, 24, 0, 0, 0, 1, 0, 0, 0, 29, 0, 0, 0, 5)));
		Outgrown.Account account = new Outgrown.Account(MELEE, new HashMap<>(), TODAY, null, null);
		assertTrue(Outgrown.judge(Arrays.asList(1, 2, net.runelite.api.gameval.ItemID.RUNE_PICKAXE), items.keySet(), items::get,
			account, id -> Collections.emptyList(), id -> false).isEmpty());
	}

	@Test
	public void wordsSayWhy()
	{
		Outgrown.Verdict beaten = new Outgrown.Verdict(Outgrown.Why.OUTGROWN, WHIP, null).styles(MELEE_RANGED);
		assertEquals("Beaten by Abyssal whip", Outgrown.shortText(beaten, NAMES::get));
		assertTrue(Outgrown.longText(beaten, NAMES::get).contains("melee and ranged"));
		for (Outgrown.Why why : Outgrown.Why.values())
		{
			Outgrown.Verdict v = new Outgrown.Verdict(why, WHIP, "3");
			assertFalse(why.name(), Outgrown.shortText(v, NAMES::get).isEmpty());
			assertFalse(why.name(), Outgrown.longText(v, NAMES::get).isEmpty());
			assertFalse(why.name(), Outgrown.groupText(v).isEmpty());
		}
		assertEquals("melee, ranged and magic", Outgrown.styles(ALL));
	}

	@Test
	public void theSpecialListsAreLowerCase()
	{
		for (String[] list : Arrays.asList(Outgrown.SPECIAL_ATTACK, Outgrown.PASSIVE, Outgrown.SETS, Outgrown.RUNE_STAVES, Outgrown.GODS))
		{
			for (String name : list)
			{
				assertEquals(name, name.toLowerCase(java.util.Locale.ROOT), name);
			}
		}
		assertFalse("the plain amulet of fury has no passive effect", Arrays.asList(Outgrown.PASSIVE).contains("amulet of fury"));
	}

	@Test
	public void memoryKeepsWornGearAndStylesThroughASave()
	{
		Memory memory = new Memory();
		assertTrue(memory.observeWorn(Arrays.asList(WHIP, FURY), TODAY));
		assertFalse(memory.observeWorn(Arrays.asList(WHIP), TODAY));
		assertTrue(memory.observeStyle(Outgrown.Style.RANGED, TODAY - 100));
		assertTrue(memory.observeStyle(Outgrown.Style.MELEE, TODAY - 5));
		Gson gson = new Gson();
		Memory read = Memory.read(gson, gson.toJson(memory));
		assertEquals(Long.valueOf(TODAY), read.getWorn().get(WHIP));
		assertEquals(EnumSet.of(Outgrown.Style.MELEE), read.stylesUsed(TODAY, Outgrown.WORN_DAYS));
		assertEquals(MELEE_RANGED, read.stylesUsed(TODAY, 365));

		Memory damaged = Memory.read(gson, "{\"since\":1,\"worn\":{\"4151\":5,\"-3\":9,\"6585\":\"x\"},\"styles\":{\"MELEE\":4,\"FLYING\":2,\"MAGIC\":-8}}");
		assertEquals(Collections.singletonMap(WHIP, 5L), damaged.getWorn());
		assertEquals(EnumSet.of(Outgrown.Style.MELEE), damaged.stylesUsed(10, 90));
		assertTrue(Memory.read(gson, "{\"since\":1}").getWorn().isEmpty());
	}

	@Test
	public void gearRoadmapsGoalPiecesAreRead()
	{
		Gson gson = new Gson();
		assertEquals(new HashSet<>(Arrays.asList(4151, 6585)),
			SavingsGoal.pieces(gson, "{\"name\":\"Whip and fury\",\"ids\":[4151,6585,null,-1]}"));
		assertEquals("Whip and fury", SavingsGoal.name(gson, "{\"name\":\"Whip and fury\",\"ids\":[4151]}"));
		assertTrue(SavingsGoal.pieces(gson, null).isEmpty());
		assertTrue(SavingsGoal.pieces(gson, "not json").isEmpty());
		assertNull(SavingsGoal.name(gson, "{\"ids\":[1]}"));
	}

	@Test
	public void theReckoningListsOutgrownGearAndWhyTheRestIsKept()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Collections.singletonList(new Task("Some Quest", Task.QUEST, "X", 0, 0));
		data.needs = Collections.singletonList(new int[]{0, 1, 999});
		Catalog catalog = new Catalog(data);
		Map<Integer, Long> bank = new HashMap<>();
		for (int id : new int[]{RUNE_SCIMITAR, ADAMANT_SCIMITAR, MITHRIL_SCIMITAR, RUNE_PLATEBODY, GRANITE_MAUL, RUNE_KITESHIELD})
		{
			bank.put(id, 1L);
		}
		Memory memory = new Memory();
		memory.observeBank(bank, TODAY - 30);
		memory.observeWorn(Arrays.asList(WHIP, BANDOS_CHESTPLATE, DEFENDER), TODAY - 200);
		memory.observeStyle(Outgrown.Style.MELEE, TODAY - 1);
		Set<Integer> held = new HashSet<>(Arrays.asList(WHIP, BANDOS_CHESTPLATE, DEFENDER, MITHRIL_SCIMITAR));
		Set<Integer> owned = new HashSet<>(bank.keySet());
		owned.addAll(held);
		Outgrown.Account account = new Outgrown.Account(memory.stylesUsed(TODAY, Outgrown.WORN_DAYS), memory.getWorn(), TODAY,
			Collections.emptySet(), null);
		Map<Integer, Outgrown.Verdict> gear = Reckoning.judgeGear(catalog, bank, owned, ITEMS::get, account);
		Plans plans = new Plans(Collections.singletonList(new Plans.Kit("Slayer", Collections.singleton(ADAMANT_SCIMITAR))),
			Collections.emptyList());
		Reckoning r = Reckoning.of(catalog, memory, bank, held, new boolean[1], id -> 1000, id -> 1000, id -> 500, id -> ITEMS.get(id).tradeable,
			new Reckoning.Rules(90, 14), plans, Tools.Levels.UNKNOWN, gear, TODAY);

		Set<Integer> outgrown = new HashSet<>();
		for (Advice a : r.getOutgrown())
		{
			outgrown.add(a.getItemId());
			assertEquals(Advice.Verdict.OUTGROWN, a.getVerdict());
			assertTrue(ClearOut.listed(a));
			assertTrue(r.adviceFor(a.getItemId()) == a);
		}
		assertEquals(new HashSet<>(Arrays.asList(RUNE_SCIMITAR, RUNE_PLATEBODY)), outgrown);
		assertEquals(WHIP, r.adviceFor(RUNE_SCIMITAR).getBetter());
		assertEquals(BANDOS_CHESTPLATE, r.adviceFor(RUNE_PLATEBODY).getBetter());
		assertEquals(2, r.freedSlots());
		assertEquals(2000, r.freedValue());

		Map<Integer, Advice> kept = new HashMap<>();
		for (Advice a : r.getGearKept())
		{
			kept.put(a.getItemId(), a);
			assertFalse(ClearOut.listed(a));
		}
		assertEquals("a kit takes it", "Kit: Slayer", kept.get(ADAMANT_SCIMITAR).getClaims().get(0).shortText());
		assertEquals(Outgrown.Why.CARRIED, kept.get(MITHRIL_SCIMITAR).getGear().getWhy());
		assertEquals(Outgrown.Why.SPECIAL_ATTACK, kept.get(GRANITE_MAUL).getGear().getWhy());
		assertEquals(Outgrown.Why.BEST, kept.get(RUNE_KITESHIELD).getGear().getWhy());
		// Beaten pieces lead the kept list, the rest follow.
		Set<Integer> first = new HashSet<>(Arrays.asList(r.getGearKept().get(0).getItemId(), r.getGearKept().get(1).getItemId()));
		assertEquals(new HashSet<>(Arrays.asList(ADAMANT_SCIMITAR, MITHRIL_SCIMITAR)), first);
	}
}
