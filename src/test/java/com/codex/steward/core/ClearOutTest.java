package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;

public class ClearOutTest
{
	private static Advice item(int id, long qty, long ge, long alch, boolean tradeable)
	{
		return new Advice(id, qty, ge, tradeable, Advice.Verdict.SPENT, null, 0).prices(ge, alch);
	}

	@Test
	public void theExchangeTakesTwoPercentCappedAndNothingUnderFifty()
	{
		assertEquals(49, ClearOut.afterTax(49));
		assertEquals(49, ClearOut.afterTax(50));
		assertEquals(980, ClearOut.afterTax(1000));
		assertEquals(1_000_000_000 - 5_000_000, ClearOut.afterTax(1_000_000_000));
		assertEquals(0, ClearOut.afterTax(0));
	}

	@Test
	public void eachItemTakesTheRouteThatBringsMost()
	{
		long nature = 100;
		// Sells for 1,000 (980 after tax), alchs for 600 - 100.
		assertEquals(ClearOut.Route.SELL, ClearOut.step(item(1, 2, 1000, 600, true), false, nature).getRoute());
		assertEquals(1960, ClearOut.step(item(1, 2, 1000, 600, true), false, nature).getGain());
		// The Exchange price has fallen below alchemy.
		ClearOut.Step alch = ClearOut.step(item(2, 3, 300, 600, true), false, nature);
		assertEquals(ClearOut.Route.ALCH, alch.getRoute());
		assertEquals(1500, alch.getGain());
		// An ironman alchs what is worth the rune and drops the rest.
		assertEquals(ClearOut.Route.ALCH, ClearOut.step(item(1, 1, 1000, 600, true), true, nature).getRoute());
		assertEquals(ClearOut.Route.DROP, ClearOut.step(item(3, 1, 1000, 60, true), true, nature).getRoute());
		// Untradeable or worthless: drop.
		assertEquals(ClearOut.Route.DROP, ClearOut.step(item(4, 1, 0, 5000, false), false, nature).getRoute());
		assertEquals(ClearOut.Route.DROP, ClearOut.step(item(5, 1, 0, 0, true), false, nature).getRoute());
	}

	@Test
	public void thePlanPutsTheMostValuableFirstAndTotalsEachRoute()
	{
		ClearOut plan = ClearOut.plan(Arrays.asList(item(1, 1, 100, 0, true), item(2, 1, 0, 0, false), item(3, 1, 10_000, 0, true),
			item(4, 1, 300, 900, true)), false, 100);
		assertEquals(Arrays.asList(3, 4, 1, 2), Arrays.asList(plan.getSteps().get(0).getAdvice().getItemId(),
			plan.getSteps().get(1).getAdvice().getItemId(), plan.getSteps().get(2).getAdvice().getItemId(),
			plan.getSteps().get(3).getAdvice().getItemId()));
		assertEquals(2, plan.count(ClearOut.Route.SELL));
		assertEquals(9800 + 98, plan.gain(ClearOut.Route.SELL));
		assertEquals(800, plan.gain(ClearOut.Route.ALCH));
		assertEquals(1, plan.count(ClearOut.Route.DROP));
		assertEquals(9800 + 98 + 800, plan.totalGain());
	}

	@Test
	public void anItemIsGoneOnlyWhenNeitherBankedNorCarried()
	{
		Memory.Session session = new Memory.Session(100, Arrays.asList(
			new Memory.Planned(1, 1, ClearOut.Route.SELL, 500),
			new Memory.Planned(2, 1, ClearOut.Route.DROP, 0),
			new Memory.Planned(3, 1, ClearOut.Route.ALCH, 50)));
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(3, 1L);
		// Item 1 was withdrawn to sell and is still in the pack.
		assertEquals(Collections.singletonList(2), ClearOut.cleared(session, bank, new HashSet<>(Collections.singletonList(1))));
		assertEquals(Arrays.asList(1, 2), ClearOut.cleared(session, bank, Collections.emptySet()));
	}

	@Test
	public void aSessionSurvivesBeingSavedAndAnOldSaveHasNone()
	{
		Gson gson = new Gson();
		Memory memory = new Memory();
		memory.setSession(new Memory.Session(100, Collections.singletonList(new Memory.Planned(7, 3, ClearOut.Route.ALCH, 90))));
		Memory read = gson.fromJson(gson.toJson(memory), Memory.class).repair();
		Memory.Planned p = read.getSession().get(7);
		assertEquals(3, p.getQuantity());
		assertEquals(ClearOut.Route.ALCH, p.getRoute());
		assertEquals(null, gson.fromJson("{\"since\":5}", Memory.class).repair().getSession());
		// A route this version does not know is read as a drop rather than failing.
		Memory odd = gson.fromJson("{\"since\":5,\"session\":{\"started\":5,\"items\":[{\"item\":7,\"qty\":1,\"route\":\"BURN\",\"gain\":0},null]}}", Memory.class).repair();
		assertEquals(1, odd.getSession().getItems().size());
		assertEquals(ClearOut.Route.DROP, odd.getSession().get(7).getRoute());
	}

	@Test
	public void aClearOutFinishesWhenEverythingIsGoneOrLeftOff()
	{
		Memory.Session session = new Memory.Session(100, Arrays.asList(
			new Memory.Planned(1, 1, ClearOut.Route.SELL, 500),
			new Memory.Planned(2, 1, ClearOut.Route.DROP, 0)));
		// Item 2 is still in the bank, but the player has kept it since.
		Memory memory = new Memory();
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(2, 1L);
		memory.observeBank(bank, 0);
		memory.setKept(2, true);
		Catalog catalog = new Catalog(new Catalog.Data());
		Reckoning r = Reckoning.of(catalog, memory, bank, Collections.emptySet(), new boolean[0], id -> 1, id -> true,
			new Reckoning.Rules(90, 14), 200);
		java.util.List<Integer> cleared = ClearOut.cleared(session, bank, Collections.emptySet());
		java.util.List<Integer> off = ClearOut.leftOff(session, r, cleared);
		assertEquals(Collections.singletonList(1), cleared);
		assertEquals(Collections.singletonList(2), off);
		assertEquals(true, ClearOut.finished(session, cleared, off));
		// Nothing gone yet: not finished, even if all were left off.
		assertEquals(false, ClearOut.finished(session, Collections.emptyList(), Arrays.asList(1, 2)));
	}
}
