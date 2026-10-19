package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** How kits, goals and STASH units keep items off the list. */
public class PlansReckoningTest
{
	private static final int HAM_ROBE = 4300;
	private static final int STEEL_BODY = 1119;
	private static final int SHARK = 385;
	private static final int TELEPORT = 8013;
	private static final int ANCIENT_TOKEN = 5555;
	private static final int OLD_SHIELD = 4444;

	private static Catalog catalog()
	{
		Catalog.Data data = new Catalog.Data();
		data.tasks = Arrays.asList(
			new Task("Death to the Dorgeshuun", Task.QUEST, "DEATH_TO_THE_DORGESHUUN", 0, 0),
			new Task("Dragon Slayer I", Task.QUEST, "DRAGON_SLAYER_I", 0, 0),
			new Task("Dragon Slayer II", Task.QUEST, "DRAGON_SLAYER_II", 0, 0),
			new Task("Varrock Easy Diary", Task.DIARY, null, 1, 1));
		data.needs = Arrays.asList(
			new int[]{0, 1, HAM_ROBE},
			new int[]{0, 1, STEEL_BODY},
			new int[]{1, 1, OLD_SHIELD},
			new int[]{3, 1, ANCIENT_TOKEN});
		// Dragon Slayer II only mentions the teleport, a general good.
		data.soft = Collections.singletonList(new int[]{2, 1, TELEPORT});
		data.general = new int[]{SHARK, TELEPORT};
		data.stash = Collections.singletonList(new int[]{STEEL_BODY, 4 | 8});
		Map<String, int[]> goals = new HashMap<>();
		goals.put("quest:Dragon Slayer II", new int[]{1, 2});
		goals.put("quest:Bad Data", new int[]{-1, 99});
		data.goals = goals;
		return new Catalog(data);
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

	private static Reckoning reckon(Map<Integer, Long> bank, boolean[] done, Plans plans, Reckoning.Rules rules, long today)
	{
		Memory memory = new Memory();
		memory.observeBank(bank, 0);
		return Reckoning.of(catalog(), memory, bank, Collections.emptySet(), done, id -> 100, id -> 120, id -> 60,
			id -> id != ANCIENT_TOKEN, rules, plans, today);
	}

	private static Plans kit(String name, Integer... items)
	{
		return new Plans(Collections.singletonList(new Plans.Kit(name, new HashSet<>(Arrays.asList(items)))), Collections.emptyList());
	}

	@Test
	public void aKitKeepsDustAndSaysWhich()
	{
		Map<Integer, Long> bank = bank(SHARK, 300, HAM_ROBE, 1);
		boolean[] done = {true, false, false, false};
		Reckoning without = reckon(bank, done, Plans.none(), new Reckoning.Rules(90, 14), 200);
		assertEquals(1, without.getDust().size());
		assertEquals(SHARK, without.getDust().get(0).getItemId());

		Reckoning with = reckon(bank, done, kit("Vorkath", SHARK), new Reckoning.Rules(90, 14), 200);
		assertTrue(with.getDust().isEmpty());
		assertEquals(1, with.getSpared().size());
		Advice shark = with.getSpared().get(0);
		assertEquals(Advice.Verdict.SPARED, shark.getVerdict());
		assertEquals(Collections.singletonList(new Claim(Claim.Kind.KIT, "Vorkath")), shark.getClaims());
		assertEquals("Kept for your kit Vorkath (Codex Quartermaster)", shark.getClaims().get(0).longText());
		// The spared item is in no total.
		assertEquals(100, with.freedValue());
		assertEquals(1, with.freedSlots());
	}

	@Test
	public void aKitKeepsAnItemWhoseQuestsAreDone()
	{
		Reckoning r = reckon(bank(HAM_ROBE, 1), new boolean[]{true, false, false, false}, kit("Costume", HAM_ROBE), new Reckoning.Rules(90, 14), 50);
		assertTrue(r.getSpent().isEmpty());
		assertEquals(HAM_ROBE, r.getSpared().get(0).getItemId());
		assertEquals(Collections.singletonList("Death to the Dorgeshuun"), r.getSpared().get(0).getTasks());
	}

	@Test
	public void stashGearIsNeverDoneWithButCanStillBeDusty()
	{
		boolean[] done = {true, false, false, false};
		Reckoning guarded = reckon(bank(STEEL_BODY, 1), done, Plans.none(), new Reckoning.Rules(90, 14), 50);
		assertTrue(guarded.getSpent().isEmpty());
		Advice body = guarded.getSpared().get(0);
		assertEquals(Collections.singletonList(new Claim(Claim.Kind.STASH, "medium, hard")), body.getClaims());

		Reckoning off = reckon(bank(STEEL_BODY, 1), done, Plans.none(), new Reckoning.Rules(90, 14, false, false), 50);
		assertEquals(STEEL_BODY, off.getSpent().get(0).getItemId());

		// Fifty is more than leftovers, so not done with; untouched for 200 days it is dusty, marked as STASH gear.
		Reckoning dusty = reckon(bank(STEEL_BODY, 50), done, Plans.none(), new Reckoning.Rules(90, 14), 200);
		assertEquals(STEEL_BODY, dusty.getDust().get(0).getItemId());
		assertEquals(Claim.Kind.STASH, dusty.getDust().get(0).getClaims().get(0).getKind());
	}

	@Test
	public void aGoalComesFirstAndKeepsWhatItsQuestsMentionFromDust()
	{
		Map<Integer, Long> bank = bank(TELEPORT, 5, OLD_SHIELD, 1, HAM_ROBE, 1);
		boolean[] done = new boolean[4];
		Reckoning plain = reckon(bank, done, Plans.none(), new Reckoning.Rules(90, 14), 200);
		// Without a goal the mentioned teleport holds nothing and gathers dust.
		assertEquals(Arrays.asList("Death to the Dorgeshuun", "Dragon Slayer I"), new ArrayList<>(plain.getNeeded().keySet()));
		assertTrue(plain.getDust().stream().anyMatch(a -> a.getItemId() == TELEPORT));

		Plans goal = new Plans(Collections.emptyList(), Arrays.asList("quest:Dragon Slayer II", "quest:Bad Data", "quest:Unknown"));
		Reckoning r = reckon(bank, done, goal, new Reckoning.Rules(90, 14), 200);
		List<String> order = new ArrayList<>(r.getNeeded().keySet());
		// Only what quests ask for is on the Needed page, goal work first.
		assertEquals(Arrays.asList("Dragon Slayer I", "Death to the Dorgeshuun"), order);
		assertEquals(Collections.singletonList("Dragon Slayer II"), r.goalsOf("Dragon Slayer I"));
		assertTrue(r.goalsOf("Death to the Dorgeshuun").isEmpty());
		assertEquals(Collections.singletonList(new Claim(Claim.Kind.GOAL, "Dragon Slayer II")),
			r.getNeeded().get("Dragon Slayer I").get(0).getClaims());
		// The teleport Dragon Slayer II mentions is kept from dust, and says why.
		assertTrue(r.getDust().stream().noneMatch(a -> a.getItemId() == TELEPORT));
		Advice teleport = r.getSpared().get(0);
		assertEquals(TELEPORT, teleport.getItemId());
		assertEquals(Collections.singletonList(new Claim(Claim.Kind.GOAL, "Dragon Slayer II")), teleport.getClaims());
	}

	@Test
	public void untradeableDustIsListedApartOnlyWhenAskedAndNeverCounted()
	{
		Map<Integer, Long> bank = bank(ANCIENT_TOKEN, 1);
		boolean[] done = new boolean[4];
		// The diary still needs the token: held, not dust.
		assertTrue(reckon(bank, done, Plans.none(), new Reckoning.Rules(90, 14, true, true), 200).getUntradeableDust().isEmpty());
		done[3] = true;
		assertTrue(reckon(bank, done, Plans.none(), new Reckoning.Rules(90, 14, true, false), 200).getUntradeableDust().isEmpty());
		Reckoning shown = reckon(bank, done, Plans.none(), new Reckoning.Rules(90, 14, true, true), 200);
		// Done with by its diary, so it is listed as spent, not as dust.
		assertEquals(ANCIENT_TOKEN, shown.getSpent().get(0).getItemId());

		Map<Integer, Long> odd = bank(9999, 1);
		Memory memory = new Memory();
		memory.observeBank(odd, 0);
		Reckoning r = Reckoning.of(catalog(), memory, odd, Collections.emptySet(), done, id -> 0, id -> 0, id -> 0,
			id -> false, new Reckoning.Rules(90, 14, true, true), Plans.none(), 200);
		assertEquals(9999, r.getUntradeableDust().get(0).getItemId());
		assertEquals(0, r.freedSlots());
		assertTrue(r.getDust().isEmpty());
	}

	@Test
	public void theQuestCapeGoalTagsQuestsButDoesNotHoldWhatTheyOnlyMention()
	{
		Map<Integer, Long> bank = bank(TELEPORT, 5, OLD_SHIELD, 1);
		Plans cape = new Plans(Collections.emptyList(), Collections.singletonList("cape:Quest point cape"));
		Reckoning r = reckon(bank, new boolean[4], cape, new Reckoning.Rules(90, 14), 200);
		assertEquals(Collections.singletonList("Quest cape"), r.goalsOf("Dragon Slayer I"));
		assertTrue(r.getNeeded().get("Dragon Slayer II") == null);
		assertTrue(r.getDust().stream().anyMatch(a -> a.getItemId() == TELEPORT));
	}

	@Test
	public void pinningABigGoalDoesNotBuryTheNeededPageInStaples() throws Exception
	{
		Catalog real;
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/com/codex/steward/steward-data.json"),
			StandardCharsets.UTF_8))
		{
			real = new Catalog(new Gson().fromJson(reader, Catalog.Data.class));
		}
		int coins = ItemID.COINS;
		int hammer = ItemID.HAMMER;
		int logs = ItemID.LOGS;
		Map<Integer, Long> bank = bank(coins, 5_000_000, hammer, 1, logs, 50);
		Memory memory = new Memory();
		memory.observeBank(bank, 0);
		Plans goal = new Plans(Collections.emptyList(), Collections.singletonList("quest:Dragon Slayer II"));
		boolean[] done = new boolean[real.getTasks().size()];
		Reckoning without = Reckoning.of(real, memory, bank, Collections.emptySet(), done, id -> 1, id -> 1, id -> 0,
			id -> true, new Reckoning.Rules(90, 14), Plans.none(), 200);
		Reckoning with = Reckoning.of(real, memory, bank, Collections.emptySet(), done, id -> 1, id -> 1, id -> 0,
			id -> true, new Reckoning.Rules(90, 14), goal, 200);
		// The goal reorders and tags the page, but adds nothing quests only mention, such as coins.
		assertEquals(items(without), items(with));
		assertTrue(!items(with).contains(coins));
	}

	private static Set<Integer> items(Reckoning r)
	{
		Set<Integer> out = new HashSet<>();
		r.getNeeded().values().forEach(list -> list.forEach(a -> out.add(a.getItemId())));
		return out;
	}
}
