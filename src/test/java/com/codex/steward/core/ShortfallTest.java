package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ShortfallTest
{
	private static final int ROPE = 954;
	private static final int TINDERBOX = 590;
	private static final int BRONZE_PICK = 1265;
	private static final int RUNE_PICK = 1275;
	private static final int GAS_MASK = 1506;
	private static final int SHARK = 385;
	private static final int LOBSTER = 379;
	private static final int TUNA = 361;
	private static final int SALMON = 329;
	private static final int TROUT = 333;
	private static final int NOTE = 7777;

	private static Catalog catalog()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Arrays.asList(
			new Task("Plague City", Task.QUEST, "PLAGUE_CITY", 0, 0),
			new Task("Biohazard", Task.QUEST, "BIOHAZARD", 0, 0),
			new Task("Underground Pass", Task.QUEST, "UNDERGROUND_PASS", 0, 0),
			new Task("Cook's Assistant", Task.QUEST, "COOKS_ASSISTANT", 0, 0));
		data.needs = Arrays.asList(
			new int[]{0, 1, ROPE},
			new int[]{2, 2, ROPE},
			new int[]{2, 1, TINDERBOX},
			new int[]{2, 1, BRONZE_PICK, RUNE_PICK},
			new int[]{1, 1, GAS_MASK},
			// "Any food": too broad to be a shopping item.
			new int[]{2, 5, SHARK, LOBSTER, TUNA, SALMON, TROUT},
			new int[]{3, 1, TINDERBOX});
		// Only named in passing: never something to get.
		data.soft = Collections.singletonList(new int[]{2, 1, NOTE});
		Map<String, int[]> goals = new HashMap<>();
		goals.put("quest:Underground Pass", new int[]{0, 1, 2});
		goals.put("quest:Cook's Assistant", new int[]{3});
		data.goals = goals;
		return new Catalog(data);
	}

	private static List<Shortfall.Goal> of(boolean[] done, Map<Integer, Long> owned, String... goals)
	{
		return Shortfall.of(catalog(), new Plans(Collections.emptyList(), Arrays.asList(goals)), done, owned,
			id -> id == ROPE ? 150 : id == TINDERBOX ? 2 : id == RUNE_PICK ? 18_000 : 40, id -> id != GAS_MASK);
	}

	@Test
	public void listsWhatTheGoalsQuestsSayToBringLessWhatIsOwned()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ROPE, 1L);
		List<Shortfall.Goal> goals = of(new boolean[4], owned, "quest:Underground Pass");
		assertEquals(1, goals.size());
		Shortfall.Goal g = goals.get(0);
		assertEquals("Underground Pass", g.getName());
		assertEquals(3, g.getTasksLeft());
		// Most costly first: the second rope, a pickaxe (the first choice, none owned), a tinderbox.
		assertEquals(Arrays.asList(ROPE, BRONZE_PICK, TINDERBOX), ids(g));
		Shortfall.Line rope = g.getLines().get(0);
		assertEquals(2, rope.getNeeded());
		assertEquals(1, rope.getHave());
		assertEquals(1, rope.getMissing());
		// Plague City's one rope is owned; Underground Pass wants two, the most one quest asks, not three.
		assertEquals(Collections.singletonList("Underground Pass"), rope.getTasks());
		assertEquals(2, g.getLines().get(1).getChoices());
		// The gas mask is handed out by a quest: counted, not listed.
		assertEquals(1, g.getUntradeable());
		assertEquals(40 + 150 + 2, g.getCost());
	}

	@Test
	public void ownedChoicesAndFinishedQuestsAreNotShort()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(RUNE_PICK, 1L);
		owned.put(ROPE, 2L);
		owned.put(TINDERBOX, 1L);
		Shortfall.Goal g = of(new boolean[4], owned, "quest:Underground Pass").get(0);
		assertTrue(g.getLines().isEmpty());

		// Every quest on the way done: the goal is not listed at all; the cape is never listed.
		assertTrue(of(new boolean[]{true, true, true, false}, new HashMap<>(), "quest:Underground Pass", "cape:Quest point cape").isEmpty());
		// Two goals, each on its own.
		assertEquals(2, of(new boolean[4], new HashMap<>(), "quest:Underground Pass", "quest:Cook's Assistant").size());
	}

	private static List<Integer> ids(Shortfall.Goal g)
	{
		List<Integer> out = new java.util.ArrayList<>();
		for (Shortfall.Line l : g.getLines())
		{
			out.add(l.getItemId());
		}
		return out;
	}
}
