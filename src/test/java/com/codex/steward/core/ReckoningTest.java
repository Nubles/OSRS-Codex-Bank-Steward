package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ReckoningTest
{
	private static final int BAT_BONES = 530;
	private static final int GHOSTSPEAK = 552;
	private static final int PICK_RUNE = 1275;
	private static final int PICK_ADDY = 1271;
	private static final int NATURE = 561;
	private static final int OLD_BOOT = 9999;
	private static final int QUEST_NOTE = 7777;
	private static final int TROPHY = 6666;

	private static Catalog catalog()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Arrays.asList(
			new Task("Merlin's Crystal", Task.QUEST, "MERLINS_CRYSTAL", 0, 0),
			new Task("Watchtower", Task.QUEST, "WATCHTOWER", 0, 0),
			new Task("The Restless Ghost", Task.QUEST, "THE_RESTLESS_GHOST", 0, 0),
			new Task("Mining Thing", Task.QUEST, "X", 0, 0));
		data.needs = Arrays.asList(
			new int[]{0, 1, BAT_BONES},
			new int[]{1, 1, BAT_BONES},
			new int[]{2, 1, GHOSTSPEAK},
			new int[]{3, 1, PICK_RUNE, PICK_ADDY},
			new int[]{3, 5, NATURE});
		// Bat bones are also picked up along the way in Mining Thing; the note is only ever named.
		data.soft = Arrays.asList(
			new int[]{3, 1, BAT_BONES},
			new int[]{0, 1, QUEST_NOTE},
			new int[]{3, 1, NATURE});
		data.general = new int[]{NATURE};
		data.lasting = new int[]{GHOSTSPEAK};
		return new Catalog(data);
	}

	private static final Reckoning.Rules RULES = new Reckoning.Rules(90, 14);

	private static Reckoning reckon(Memory memory, Map<Integer, Long> bank, Set<Integer> held, boolean[] done, long today)
	{
		return Reckoning.of(catalog(), memory, bank, held, done, id -> 100, id -> id != GHOSTSPEAK && id != TROPHY, RULES, today);
	}

	private static Map<Integer, Long> bank(int... pairs)
	{
		Map<Integer, Long> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put(pairs[i], (long) pairs[i + 1]);
		}
		return map;
	}

	@Test
	public void spentOnlyWhenEveryTaskIsDone()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(BAT_BONES, 3);
		memory.observeBank(bank, 100);

		Reckoning half = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, false, false, false}, 100);
		assertTrue(half.getSpent().isEmpty());
		assertEquals(Arrays.asList("Watchtower", "Mining Thing"), half.getNeeded().get("Watchtower").get(0).getTasks());

		// Mining Thing only names the bones in passing, but while it is unfinished they stay.
		Reckoning soft = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, false, false}, 100);
		assertTrue(soft.getSpent().isEmpty());
		assertEquals(Collections.singletonList("Mining Thing"), soft.getNeeded().get("Mining Thing").get(0).getTasks());

		Reckoning all = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, false, true}, 100);
		assertEquals(1, all.getSpent().size());
		assertEquals(300, all.freedValue());
		assertEquals(Arrays.asList("Merlin's Crystal", "Watchtower"), all.getSpent().get(0).getTasks());
	}

	@Test
	public void aStackFarBeyondTheQuestsIsNotLeftovers()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(BAT_BONES, 900);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, false, true}, 100);
		assertTrue(r.getSpent().isEmpty());
	}

	@Test
	public void anItemOnlyNamedInPassingIsNeverSpent()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(QUEST_NOTE, 1);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, true, true}, 100);
		assertTrue(r.getSpent().isEmpty());
	}

	@Test
	public void softMentionsOfGeneralGoodsDoNotCrowdTheNeededPage()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(NATURE, 1);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, true, false}, 100);
		// The firm need for 5 nature runes still holds them.
		assertEquals(NATURE, r.getNeeded().get("Mining Thing").get(0).getItemId());
		Reckoning done = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, true, true}, 100);
		assertTrue(done.getNeeded().isEmpty());
	}

	@Test
	public void untradeableItemsAreNeverCalledDust()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(TROPHY, 1, OLD_BOOT, 1);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[4], 400);
		assertEquals(1, r.getDust().size());
		assertEquals(OLD_BOOT, r.getDust().get(0).getItemId());
	}

	@Test
	public void varbitTasksFinishAsQuestHelperReadsThem()
	{
		Task diary = new Task("Diary", Task.DIARY, null, 4499, 1);
		assertTrue(diary.usesVarbit());
		assertTrue(diary.isFinishedAt(1));
		assertTrue(diary.isFinishedAt(2));
		assertFalse(diary.isFinishedAt(0));
		Task mini = new Task("A Ruff Situation", Task.MINIQUEST, null, 15889, 120);
		assertTrue(mini.isFinishedAt(120));
		assertFalse(mini.isFinishedAt(119));
		assertFalse(mini.isFinishedAt(121));
		assertFalse(new Task("Quest", Task.QUEST, "COOKS_ASSISTANT", 0, 0).usesVarbit());
	}

	@Test
	public void lastingAndGeneralItemsAreNeverSpent()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(GHOSTSPEAK, 1, NATURE, 500);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[]{true, true, true, true}, 100);
		assertTrue(r.getSpent().isEmpty());
	}

	@Test
	public void anyOfNeedHoldsOnlyTheBestOwnedChoice()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(PICK_RUNE, 1, PICK_ADDY, 1);
		memory.observeBank(bank, 100);
		Reckoning r = reckon(memory, bank, Collections.emptySet(), new boolean[4], 100);
		assertEquals(1, r.getNeeded().get("Mining Thing").size());
		assertEquals(PICK_RUNE, r.getNeeded().get("Mining Thing").get(0).getItemId());

		// Carrying the rune pickaxe meets the need, so the bank's spare is not held back by it.
		Reckoning carrying = reckon(memory, bank(PICK_ADDY, 1), new HashSet<>(Collections.singletonList(PICK_RUNE)), new boolean[4], 100);
		assertTrue(carrying.getNeeded().isEmpty());
	}

	@Test
	public void dustNeedsLongEnoughWatching()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = bank(OLD_BOOT, 1, BAT_BONES, 2);
		memory.observeBank(bank, 100);
		assertTrue(reckon(memory, bank, Collections.emptySet(), new boolean[4], 150).getDust().isEmpty());

		memory.observeBank(bank(OLD_BOOT, 1, BAT_BONES, 1), 180);
		Reckoning later = reckon(memory, bank(OLD_BOOT, 1, BAT_BONES, 1), Collections.emptySet(), new boolean[4], 195);
		assertEquals(1, later.getDust().size());
		assertEquals(OLD_BOOT, later.getDust().get(0).getItemId());
		assertEquals(95, later.getDust().get(0).getUntouchedDays());
	}

	@Test
	public void recentlyUsedItemsAreNotSpentAndKeptItemsStayKept()
	{
		Memory memory = new Memory();
		memory.observeBank(bank(BAT_BONES, 3), 100);
		memory.observeBank(bank(BAT_BONES, 2), 110);
		boolean[] done = {true, true, false, true};
		assertTrue(reckon(memory, bank(BAT_BONES, 2), Collections.emptySet(), done, 115).getSpent().isEmpty());
		assertEquals(1, reckon(memory, bank(BAT_BONES, 2), Collections.emptySet(), done, 124).getSpent().size());

		memory.setKept(BAT_BONES, true);
		Reckoning r = reckon(memory, bank(BAT_BONES, 2), Collections.emptySet(), done, 124);
		assertTrue(r.getSpent().isEmpty());
		assertEquals(1, r.getKept().size());
	}
}
