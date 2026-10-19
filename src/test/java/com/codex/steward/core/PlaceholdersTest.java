package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class PlaceholdersTest
{
	private static final int HAM_ROBE = 4300;
	private static final int GHOSTSPEAK = 552;
	private static final int SHARK = 385;
	private static final int ROPE = 954;
	private static final int OLD_BOOT = 9999;
	private static final Reckoning.Rules RULES = new Reckoning.Rules(90, 14);

	private static Catalog catalog()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Arrays.asList(
			new Task("Death to the Dorgeshuun", Task.QUEST, "X", 0, 0),
			new Task("The Restless Ghost", Task.QUEST, "X", 0, 0),
			new Task("Dragon Slayer II", Task.QUEST, "X", 0, 0));
		data.needs = Arrays.asList(
			new int[]{0, 1, HAM_ROBE},
			new int[]{1, 1, GHOSTSPEAK},
			new int[]{2, 3, ROPE});
		data.soft = Collections.emptyList();
		data.general = new int[]{SHARK, ROPE};
		data.lasting = new int[]{GHOSTSPEAK};
		return new Catalog(data);
	}

	private static Memory memory(long firstLook, Set<Integer> empty, long today)
	{
		Memory m = new Memory();
		m.observeBank(Collections.singletonMap(SHARK, 10L), firstLook);
		m.observePlaceholders(empty, today);
		return m;
	}

	private static Set<Integer> set(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	@Test
	public void aPlaceholderWhoseQuestIsDoneCanGoOnceTheGraceIsOver()
	{
		Memory m = memory(100, set(HAM_ROBE), 100);
		boolean[] done = {true, false, false};
		assertTrue("emptied just now: grace", Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 105).isEmpty());
		List<Placeholders.Line> lines = Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 120);
		assertEquals(1, lines.size());
		assertEquals(Placeholders.Why.DONE, lines.get(0).getWhy());
		assertEquals(Collections.singletonList("Death to the Dorgeshuun"), lines.get(0).getTasks());
		assertTrue(Placeholders.old(catalog(), m, Collections.emptySet(), new boolean[3], Plans.none(), RULES, 120).isEmpty());
	}

	@Test
	public void anIdlePlaceholderGoesAfterTheDustTime()
	{
		Memory m = memory(100, set(SHARK, OLD_BOOT), 100);
		boolean[] done = new boolean[3];
		assertTrue(Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 189).isEmpty());
		List<Placeholders.Line> lines = Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 190);
		assertEquals(2, lines.size());
		assertEquals(Placeholders.Why.IDLE, lines.get(0).getWhy());
		assertTrue("empty since the first look", lines.get(0).isSinceWatching());
	}

	@Test
	public void kitsGoalsCarriedLastingAndKeptPlaceholdersStay()
	{
		Memory m = memory(100, set(SHARK, GHOSTSPEAK, ROPE, OLD_BOOT, HAM_ROBE), 100);
		m.setKept(OLD_BOOT, true);
		boolean[] done = {true, true, false};
		Plans plans = new Plans(Collections.singletonList(new Plans.Kit("Vorkath", set(SHARK))),
			Collections.singletonList("quest:Dragon Slayer II"));
		Map<String, int[]> goals = new HashMap<>();
		Catalog.Data data = new Catalog.Data();
		data.tasks = catalog().getTasks();
		data.needs = Arrays.asList(new int[]{0, 1, HAM_ROBE}, new int[]{1, 1, GHOSTSPEAK}, new int[]{2, 3, ROPE});
		data.soft = Collections.emptyList();
		data.general = new int[]{SHARK, ROPE};
		data.lasting = new int[]{GHOSTSPEAK};
		goals.put("quest:Dragon Slayer II", new int[]{2});
		data.goals = goals;
		List<Placeholders.Line> lines = Placeholders.old(new Catalog(data), m, set(HAM_ROBE), done, plans, RULES, 400);
		// The shark is a kit's, the ghostspeak amulet lasting, the rope owed to Dragon Slayer II,
		// the boot kept and the ham robe carried.
		assertTrue(lines.isEmpty());
	}

	@Test
	public void aGeneralGoodIsNeverDoneWithJustBecauseItsQuestIs()
	{
		Memory m = memory(100, set(ROPE), 100);
		boolean[] done = {false, false, true};
		assertTrue(Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 150).isEmpty());
		assertEquals(Placeholders.Why.IDLE, Placeholders.old(catalog(), m, Collections.emptySet(), done, Plans.none(), RULES, 190).get(0).getWhy());
	}

	@Test
	public void aFilledOrReleasedPlaceholderIsForgotten()
	{
		Memory m = memory(100, set(SHARK, OLD_BOOT), 100);
		assertTrue(m.observePlaceholders(set(OLD_BOOT), 150));
		assertFalse(m.getPlaceholders().containsKey(SHARK));
		assertFalse(m.observePlaceholders(set(OLD_BOOT), 160));
		// Emptied again later, it starts afresh.
		assertTrue(m.observePlaceholders(set(OLD_BOOT, SHARK), 170));
		assertEquals(Long.valueOf(170), m.getPlaceholders().get(SHARK));
		assertEquals(Long.valueOf(100), m.getPlaceholders().get(OLD_BOOT));
	}
}
