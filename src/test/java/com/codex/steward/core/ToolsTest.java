package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ToolsTest
{
	private static final Tools.Levels HIGH = new Tools.Levels(80, 80);

	@Test
	public void theFastestUsableToolOutclassesTheRest()
	{
		Map<Tools.Kind, Integer> best = Tools.best(Arrays.asList(ItemID.BRONZE_PICKAXE, ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE,
			ItemID.STEEL_AXE), HIGH);
		assertEquals(ItemID.DRAGON_PICKAXE, (int) best.get(Tools.Kind.PICKAXE));
		assertEquals((Integer) ItemID.DRAGON_PICKAXE, Tools.outclassedBy(ItemID.RUNE_PICKAXE, best));
		assertEquals((Integer) ItemID.DRAGON_PICKAXE, Tools.outclassedBy(ItemID.BRONZE_PICKAXE, best));
		assertNull("the best is never outclassed", Tools.outclassedBy(ItemID.DRAGON_PICKAXE, best));
		assertNull("a lone axe has nothing faster", Tools.outclassedBy(ItemID.STEEL_AXE, best));
		assertNull("not a tool", Tools.outclassedBy(ItemID.SHARK, best));
	}

	@Test
	public void blackAndMithrilPickaxesMineAtOneSpeed()
	{
		// Both roll every 5 ticks (OSRS Wiki, Pickaxe): owning one never makes the other needless.
		Map<Tools.Kind, Integer> best = Tools.best(Arrays.asList(ItemID.BLACK_PICKAXE, ItemID.MITHRIL_PICKAXE, ItemID.STEEL_PICKAXE), HIGH);
		assertNull(Tools.outclassedBy(ItemID.BLACK_PICKAXE, best));
		assertNull(Tools.outclassedBy(ItemID.MITHRIL_PICKAXE, best));
		assertEquals(best.get(Tools.Kind.PICKAXE), Tools.outclassedBy(ItemID.STEEL_PICKAXE, best));
		// Black axes do differ: a mithril axe cuts faster.
		Map<Tools.Kind, Integer> axes = Tools.best(Arrays.asList(ItemID.BLACK_AXE, ItemID.MITHRIL_AXE), HIGH);
		assertEquals((Integer) ItemID.MITHRIL_AXE, Tools.outclassedBy(ItemID.BLACK_AXE, axes));
	}

	@Test
	public void aToolTheLevelCannotUseOutclassesNothing()
	{
		// Level 50 Mining cannot swing a dragon pickaxe, so the rune one is still the one in use.
		Map<Tools.Kind, Integer> best = Tools.best(Arrays.asList(ItemID.RUNE_PICKAXE, ItemID.DRAGON_PICKAXE, ItemID.ADAMANT_PICKAXE),
			new Tools.Levels(50, 50));
		assertNull(Tools.outclassedBy(ItemID.RUNE_PICKAXE, best));
		assertEquals((Integer) ItemID.RUNE_PICKAXE, Tools.outclassedBy(ItemID.ADAMANT_PICKAXE, best));
		assertTrue("unknown levels judge nothing", Tools.best(Arrays.asList(ItemID.RUNE_PICKAXE, ItemID.BRONZE_PICKAXE),
			Tools.Levels.UNKNOWN).isEmpty());
	}

	@Test
	public void trophiesCountAsTheirToolButAreNeverOutclassed()
	{
		// An infernal axe works at dragon speed: a rune axe is outclassed, a dragon axe is not.
		Map<Tools.Kind, Integer> best = Tools.best(Arrays.asList(ItemID.INFERNAL_AXE, ItemID.DRAGON_AXE, ItemID.RUNE_AXE,
			ItemID.TRAIL_GILDED_AXE), HIGH);
		assertEquals((Integer) ItemID.DRAGON_AXE, best.get(Tools.Kind.AXE));
		assertEquals((Integer) ItemID.DRAGON_AXE, Tools.outclassedBy(ItemID.RUNE_AXE, best));
		assertNull("gilded is kept for its worth", Tools.outclassedBy(ItemID.TRAIL_GILDED_AXE, best));
		// A crystal pickaxe outclasses a 3rd age one's speed, but 3rd age is never listed.
		Map<Tools.Kind, Integer> crystal = Tools.best(Arrays.asList(ItemID.CRYSTAL_PICKAXE, ItemID._3A_PICKAXE), HIGH);
		assertNull(Tools.outclassedBy(ItemID._3A_PICKAXE, crystal));
		// Felling axes are their own kind: a dragon axe does not make a rune felling axe needless.
		Map<Tools.Kind, Integer> mixed = Tools.best(Arrays.asList(ItemID.DRAGON_AXE, ItemID.RUNE_AXE_2H), HIGH);
		assertNull(Tools.outclassedBy(ItemID.RUNE_AXE_2H, mixed));
	}

	private static Catalog catalog()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Arrays.asList(new Task("Quest A", Task.QUEST, "X", 0, 0), new Task("Quest B", Task.QUEST, "Y", 0, 0));
		// Quest A asks for any pickaxe, bronze first; Quest B is done and wanted a steel axe.
		data.needs = Arrays.asList(new int[]{0, 1, ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE},
			new int[]{1, 1, ItemID.STEEL_AXE});
		data.general = new int[]{ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_AXE};
		data.stash = Collections.singletonList(new int[]{ItemID.MITHRIL_PICKAXE, 4});
		return new Catalog(data);
	}

	private static Reckoning reckon(Map<Integer, Long> bank, Set<Integer> held, boolean[] done, Tools.Levels levels, Memory memory)
	{
		return Reckoning.of(catalog(), memory, bank, held, done, id -> 1000, id -> 1000, id -> 300, id -> true,
			new Reckoning.Rules(90, 14), Plans.none(), levels, 100);
	}

	@Test
	public void outclassedToolsJoinTheClearOutUnlessSomethingHoldsThem()
	{
		Map<Integer, Long> bank = new HashMap<>();
		for (int id : new int[]{ItemID.BRONZE_PICKAXE, ItemID.IRON_PICKAXE, ItemID.MITHRIL_PICKAXE, ItemID.ADAMANT_PICKAXE,
			ItemID.STEEL_AXE, ItemID.RUNE_AXE})
		{
			bank.put(id, 1L);
		}
		Memory memory = new Memory();
		memory.observeBank(bank, 100);
		// The dragon pickaxe is in the pack.
		Set<Integer> held = new HashSet<>(Arrays.asList(ItemID.DRAGON_PICKAXE));
		Reckoning r = reckon(bank, held, new boolean[]{false, true}, HIGH, memory);
		List<Integer> out = ids(r.getOutclassed());
		// Bronze is held for unfinished Quest A; mithril fills a STASH unit; the rune axe is the best axe.
		assertEquals(sorted(Arrays.asList(ItemID.ADAMANT_PICKAXE, ItemID.IRON_PICKAXE, ItemID.STEEL_AXE)), sorted(out));
		Advice addy = r.adviceFor(ItemID.ADAMANT_PICKAXE);
		assertEquals(Advice.Verdict.OUTCLASSED, addy.getVerdict());
		assertEquals(ItemID.DRAGON_PICKAXE, addy.getBetter());
		assertEquals(3, r.freedSlots());
		assertEquals(3000, r.freedValue());

		// Kept by the player, or carried right now: off the list.
		memory.setKept(ItemID.IRON_PICKAXE, true);
		Set<Integer> carrying = new HashSet<>(Arrays.asList(ItemID.DRAGON_PICKAXE, ItemID.STEEL_AXE));
		assertEquals(Collections.singletonList(ItemID.ADAMANT_PICKAXE), ids(reckon(bank, carrying, new boolean[]{false, true}, HIGH, memory).getOutclassed()));

		// Before the levels are read, no tool is judged.
		assertTrue(reckon(bank, held, new boolean[]{false, true}, Tools.Levels.UNKNOWN, memory).getOutclassed().isEmpty());
	}

	@Test
	public void levelsAreRememberedForTheLoggedOutPanel()
	{
		Memory memory = new Memory();
		assertEquals(Tools.Levels.UNKNOWN, memory.getLevels());
		assertTrue(memory.setLevels(61, 45));
		assertTrue(!memory.setLevels(61, 45));
		assertTrue("a failed read keeps what was known", !memory.setLevels(0, 0));
		Memory back = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(memory), Memory.class);
		assertEquals(new Tools.Levels(61, 45), back.getLevels());
	}

	private static List<Integer> ids(List<Advice> list)
	{
		List<Integer> out = new java.util.ArrayList<>();
		for (Advice a : list)
		{
			out.add(a.getItemId());
		}
		return out;
	}

	private static List<Integer> sorted(List<Integer> list)
	{
		List<Integer> out = new java.util.ArrayList<>(list);
		Collections.sort(out);
		return out;
	}
}
