package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Pins what Codex Gear Roadmap saves as its savings goal ({@code Savings.Goal} written with Gson
 * under {@code codexgearroadmap.goal}): a name and one item id per piece.
 */
public class SavingsGoalContractTest
{
	/** Exactly as Gear Roadmap's Gson writes {@code new Savings.Goal("Bandos chestplate and tassets", [11832, 11834])}. */
	private static final String WRITTEN = "{\"name\":\"Bandos chestplate and tassets\",\"ids\":[11832,11834]}";
	private static final Gson GSON = new Gson();

	private static long price(int id)
	{
		return id == 11832 ? 15_000_000 : id == 11834 ? 20_000_000 : 0;
	}

	@Test
	public void readsGearRoadmapsGoalAndPricesThePiecesStillToBuy()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(995, 9_000_000L);
		owned.put(13204, 2_000L);
		owned.put(11832, 1L);
		SavingsGoal goal = SavingsGoal.read(GSON, WRITTEN, owned, SavingsGoalContractTest::price, 1_500_000);
		assertEquals("Bandos chestplate and tassets", goal.getName());
		assertEquals("the chestplate is owned", 20_000_000, goal.getCost());
		assertEquals(11_000_000, goal.getCoins());
		assertEquals(0.55, goal.coinsShare(), 1e-9);
		assertEquals(0.625, goal.withListShare(), 1e-9);
		assertEquals(7_500_000, goal.shortAfterList());
		assertTrue(goal.isWorthShowing());
		assertFalse(goal.listBuysIt());
	}

	@Test
	public void theListCanBeWhatBuysIt()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(995, 34_000_000L);
		SavingsGoal goal = SavingsGoal.read(GSON, WRITTEN, owned, SavingsGoalContractTest::price, 1_200_000);
		assertTrue(goal.listBuysIt());
		assertEquals(0, goal.shortAfterList());
		owned.put(995, 36_000_000L);
		assertFalse("the coins already cover it", SavingsGoal.read(GSON, WRITTEN, owned, SavingsGoalContractTest::price, 1).isWorthShowing());
	}

	@Test
	public void noGoalOrNothingToBuyOrNoPriceIsNoGoal()
	{
		Map<Integer, Long> owned = new HashMap<>();
		assertNull(SavingsGoal.read(GSON, null, owned, SavingsGoalContractTest::price, 1));
		assertNull(SavingsGoal.read(GSON, "", owned, SavingsGoalContractTest::price, 1));
		assertNull(SavingsGoal.read(GSON, "not json", owned, SavingsGoalContractTest::price, 1));
		assertNull(SavingsGoal.read(GSON, "{\"name\":\"\",\"ids\":[1]}", owned, SavingsGoalContractTest::price, 1));
		assertNull(SavingsGoal.read(GSON, "{\"name\":\"Untradeable\",\"ids\":[4]}", owned, SavingsGoalContractTest::price, 1));
		owned.put(11832, 1L);
		owned.put(11834, 1L);
		assertNull("every piece owned", SavingsGoal.read(GSON, WRITTEN, owned, SavingsGoalContractTest::price, 1));
	}
}
