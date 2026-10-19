package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Every saved value the Steward reads survives damage: its own {@code memory} and {@code bankV1},
 * Quartermaster's {@code kitsV1} and {@code quarters}, Navigator's {@code goals} and Gear Roadmap's
 * savings {@code goal}. Whatever the profile holds, nothing throws, the list still reckons to
 * sensible figures, and damage to one part does not cost the rest.
 */
public class DamagedDataTest
{
	private static final Gson GSON = new Gson();
	private static final long TODAY = 20_380;
	private static final int BAT_BONES = 530;
	private static final int GHOSTSPEAK = 552;
	private static final int SHARK = 385;
	private static final Reckoning.Rules RULES = new Reckoning.Rules(90, 14, true, true);
	private static Catalog catalog;
	private static boolean[] done;

	@BeforeClass
	public static void load() throws Exception
	{
		try (Reader reader = new InputStreamReader(DamagedDataTest.class.getResourceAsStream("/com/codex/steward/steward-data.json"), StandardCharsets.UTF_8))
		{
			catalog = new Catalog(GSON.fromJson(reader, Catalog.Data.class));
		}
		done = new boolean[catalog.getTasks().size()];
		Arrays.fill(done, true);
	}

	static String fixture(String name) throws Exception
	{
		try (InputStream in = DamagedDataTest.class.getResourceAsStream(name))
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0; )
			{
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
		}
	}

	private static Map<Integer, Long> bank(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	/** An account watched for half a year: moves, keeps, placeholders, a clear-out and levels. */
	static Memory good()
	{
		Memory m = new Memory();
		m.observeBank(bank(BAT_BONES, 3, GHOSTSPEAK, 1, SHARK, 200, 995, 5_000_000), TODAY - 200);
		m.observeBank(bank(BAT_BONES, 3, GHOSTSPEAK, 1, SHARK, 150, 995, 5_000_000), TODAY - 20);
		m.observeHeld(Collections.singletonList(SHARK), TODAY - 2);
		m.observePlaceholders(new HashSet<>(Arrays.asList(1_215, 4_587)), TODAY - 120);
		m.setKept(GHOSTSPEAK, true);
		m.setLevels(70, 80);
		m.setSession(new Memory.Session(TODAY * 86_400_000L, Collections.singletonList(
			new Memory.Planned(BAT_BONES, 3, ClearOut.Route.SELL, 300))));
		return m;
	}

	@Test
	public void everyDamagedMemoryReadsAndReckons()
	{
		String good = GSON.toJson(good());
		int tried = 0;
		for (String damaged : Damaged.of(good))
		{
			try
			{
				use(Memory.read(GSON, damaged));
			}
			catch (RuntimeException | AssertionError | StackOverflowError e)
			{
				throw new AssertionError("memory " + Damaged.show(damaged) + ": " + e, e);
			}
			tried++;
		}
		assertTrue(tried > 100);
	}

	/** Everything the plugin works out from an account's memory, each figure checked for sense. */
	static void use(Memory m)
	{
		assertNotNull(m);
		Map<Integer, Long> bank = new HashMap<>(m.getBank());
		for (Map.Entry<Integer, Long> e : bank.entrySet())
		{
			assertTrue(e.getKey() != null && e.getKey() > 0);
			assertTrue(e.getValue() != null && e.getValue() > 0);
		}
		Set<Integer> held = Collections.singleton(SHARK);
		Reckoning r = Reckoning.of(catalog, m, bank, held, done, id -> 1_000, id -> 1_000, id -> 600, id -> true, RULES,
			Plans.none(), m.getLevels(), TODAY);
		assertTrue(r.freedSlots() >= 0);
		assertTrue(r.freedValue() >= 0);
		assertTrue(r.getWatchedDays() >= 0);
		for (Advice a : r.getDust())
		{
			assertTrue(a.getUntouchedDays() >= 0 && a.getUntouchedDays() <= TODAY);
		}
		for (int id : bank.keySet())
		{
			assertTrue(m.untouchedDays(id, TODAY) >= 0);
		}
		Strata.of(m, bank, TODAY, id -> 1_000);
		Placeholders.old(catalog, m, held, done, Plans.none(), RULES, TODAY);
		List<Advice> going = new java.util.ArrayList<>(r.getSpent());
		going.addAll(r.getDust());
		ClearOut plan = ClearOut.plan(going, false, 100);
		assertTrue(plan.totalGain() >= 0);
		Memory.Session s = m.getSession();
		if (s != null)
		{
			ClearOut.finished(s, ClearOut.cleared(s, bank, held), ClearOut.leftOff(s, r, ClearOut.cleared(s, bank, held)));
		}
		// The bank seen again, and the memory saved and read back, still work.
		m.observeBank(bank(SHARK, 100), TODAY);
		m.observeHeld(Collections.singletonList(BAT_BONES), TODAY);
		m.prune();
		Memory back = Memory.read(GSON, GSON.toJson(m));
		assertEquals(m.getBank(), back.getBank());
	}

	@Test
	public void oneDamagedFieldCostsOnlyThatField()
	{
		String good = GSON.toJson(good());
		// The levels saved as words: the bank, moves, keeps and clear-out all stay.
		Memory m = Memory.read(GSON, good.replace("\"mining\":70", "\"mining\":\"seventy\""));
		assertEquals(4, m.getBank().size());
		assertTrue(m.isKept(GHOSTSPEAK));
		assertNotNull(m.getSession());
		assertEquals(TODAY - 200, m.getSince());
		assertEquals(TODAY - 2, m.lastMoved(SHARK));

		// One bank count too big for any number loses that item only.
		m = Memory.read(GSON, good.replace("\"" + SHARK + "\":150", "\"" + SHARK + "\":1e40"));
		assertFalse(m.getBank().containsKey(SHARK));
		assertEquals(3L, (long) m.getBank().get(BAT_BONES));
		assertTrue(m.isKept(GHOSTSPEAK));
	}

	@Test
	public void daysInTheFutureAreBroughtBackToToday()
	{
		String good = GSON.toJson(good());
		// A watch start or a move dated years ahead would keep every item from ever gathering dust.
		Memory m = Memory.read(GSON, good.replace("\"since\":" + (TODAY - 200), "\"since\":9000000000")
			.replace("\"" + BAT_BONES + "\":" + (TODAY - 200), "\"" + BAT_BONES + "\":9000000000"));
		m.observeBank(m.getBank(), TODAY);
		assertEquals(TODAY, m.getSince());
		assertTrue(m.lastMoved(BAT_BONES) <= TODAY);
		m.observeBank(m.getBank(), TODAY + 100);
		assertEquals(100, m.untouchedDays(BAT_BONES, TODAY + 100));
	}

	@Test
	public void damagedCountsAndLevelsAreDropped()
	{
		String good = GSON.toJson(good());
		Memory m = Memory.read(GSON, good.replace("\"" + SHARK + "\":150", "\"" + SHARK + "\":-150"));
		assertFalse(m.getBank().containsKey(SHARK));
		m = Memory.read(GSON, good.replace("\"mining\":70", "\"mining\":-4").replace("\"woodcutting\":80", "\"woodcutting\":5000"));
		assertEquals(Tools.Levels.UNKNOWN, m.getLevels());
		m = Memory.read(GSON, good.replace("\"qty\":3", "\"qty\":-3"));
		assertTrue(m.getSession() == null || m.getSession().getItems().isEmpty());
	}

	@Test
	public void everyDamagedSharedBankReadsSafely() throws Exception
	{
		for (String damaged : Damaged.of(fixture("bank-steward-bankV1.json")))
		{
			try
			{
				BankSnapshot s = BankSnapshot.read(GSON, damaged);
				if (s != null)
				{
					s.sameItems(BankSnapshot.of(bank(SHARK, 1), 1));
					s.getItems().forEach((id, q) -> assertTrue(id != null && q != null));
				}
			}
			catch (RuntimeException | AssertionError | StackOverflowError e)
			{
				throw new AssertionError("bankV1 " + Damaged.show(damaged) + ": " + e, e);
			}
		}
	}

	@Test
	public void everyDamagedKitAndGoalReadsSafely() throws Exception
	{
		String[] names = {"saved", "preset", "fresh"};
		for (String name : names)
		{
			String v1 = fixture("quartermaster-" + name + "-kitsV1.json");
			String quarters = fixture("quartermaster-" + name + "-quarters.json");
			for (String damaged : Damaged.of(v1))
			{
				kits(damaged, quarters);
				kits(damaged, null);
			}
			for (String damaged : Damaged.of(quarters))
			{
				kits(v1, damaged);
				kits(null, damaged);
			}
		}
		for (String damaged : Damaged.of(fixture("navigator-goals.json")))
		{
			try
			{
				List<String> goals = Plans.readGoals(GSON, damaged);
				new Plans(Collections.emptyList(), goals).pinnedTasks(catalog);
				goals.forEach(g -> assertTrue(g.indexOf(':') > 0));
			}
			catch (RuntimeException | AssertionError | StackOverflowError e)
			{
				throw new AssertionError("goals " + Damaged.show(damaged) + ": " + e, e);
			}
		}
	}

	private static void kits(String v1, String quarters)
	{
		try
		{
			List<Plans.Kit> kits = Plans.readKits(GSON, v1, quarters);
			Plans plans = new Plans(kits, Collections.emptyList());
			plans.kitsByItem();
			for (Plans.Kit k : kits)
			{
				assertTrue(k.getName().length() <= 40 && !k.getItems().isEmpty());
				k.getItems().forEach(id -> assertTrue(id > 0));
			}
			Reckoning.of(catalog, good(), good().getBank(), Collections.emptySet(), done, id -> 1, id -> 1, id -> 1,
				id -> true, RULES, plans, Tools.Levels.UNKNOWN, TODAY);
		}
		catch (RuntimeException | AssertionError | StackOverflowError e)
		{
			throw new AssertionError("kitsV1 " + Damaged.show(v1) + " quarters " + Damaged.show(quarters) + ": " + e, e);
		}
	}

	@Test
	public void everyDamagedSavingsGoalReadsSafely()
	{
		String good = "{\"name\":\"Bandos chestplate and tassets\",\"ids\":[11832,11834]}";
		Map<Integer, Long> owned = bank(995, 9_000_000, 13_204, 2_000);
		for (String damaged : Damaged.of(good))
		{
			try
			{
				// Gear Roadmap's ids reach the client's item lookups: only real ones may.
				SavingsGoal goal = SavingsGoal.read(GSON, damaged, owned, id ->
				{
					assertTrue("asked the price of item " + id, id > 0);
					return 5_000_000;
				}, 1_000_000);
				if (goal != null)
				{
					assertTrue(goal.getCost() > 0 && goal.getCoins() >= 0);
					assertTrue(goal.coinsShare() >= 0 && goal.coinsShare() <= 1);
				}
			}
			catch (RuntimeException | AssertionError | StackOverflowError e)
			{
				throw new AssertionError("goal " + Damaged.show(damaged) + ": " + e, e);
			}
		}
		assertNull(SavingsGoal.read(GSON, "{\"name\":\"x\",\"ids\":[-1]}", owned, id -> 5, 1));
	}

	@Test
	public void aBankOfFifteenHundredItemsReckonsInAFewMilliseconds()
	{
		// A maxed account seen for a year, with a long memory of moves.
		Memory m = good();
		Map<Integer, Long> big = new HashMap<>(m.getBank());
		for (Catalog.Need need : catalog.getNeeds())
		{
			for (int id : need.getItems())
			{
				if (big.size() < 1_000)
				{
					big.putIfAbsent(id, 2L);
				}
			}
		}
		for (int id = 20_000; big.size() < 1_500; id++)
		{
			big.put(id, 7L);
		}
		m.observeBank(big, TODAY - 365);
		Map<Integer, Long> moved = new HashMap<>(big);
		moved.replaceAll((id, q) -> q + 1);
		m.observeBank(moved, TODAY - 30);
		Set<Integer> held = Collections.singleton(SHARK);
		Runnable reckon = () ->
		{
			Reckoning r = Reckoning.of(catalog, m, moved, held, done, id -> 1_000, id -> 1_000, id -> 600, id -> true, RULES,
				Plans.none(), m.getLevels(), TODAY);
			List<Advice> going = new java.util.ArrayList<>(r.getSpent());
			going.addAll(r.getDust());
			ClearOut.plan(going, false, 100);
			Placeholders.old(catalog, m, held, done, Plans.none(), RULES, TODAY);
			Strata.of(m, moved, TODAY, id -> 1_000);
		};
		Runnable save = () -> GSON.toJson(m);
		double reckonMs = time(reckon);
		double saveMs = time(save);
		System.out.printf("Bank Steward, 1,500-item bank: reckon %.2f ms, save %.2f ms%n", reckonMs, saveMs);
		// Bounds leave room for a slow, busy build machine; the figures printed are what matters.
		assertTrue("reckon took " + reckonMs + " ms", reckonMs < 50);
		assertTrue("save took " + saveMs + " ms", saveMs < 50);
	}

	static double time(Runnable run)
	{
		for (int i = 0; i < 10; i++)
		{
			run.run();
		}
		// The quickest of five batches, so a busy shared build machine cannot fail the check.
		double best = Double.MAX_VALUE;
		for (int batch = 0; batch < 5; batch++)
		{
			int runs = 30;
			long t0 = System.nanoTime();
			for (int i = 0; i < runs; i++)
			{
				run.run();
			}
			best = Math.min(best, (System.nanoTime() - t0) / 1e6 / runs);
		}
		return best;
	}

	@Test
	public void onlyASaveThisVersionCannotKeepWholeIsPutAside()
	{
		// The plugin's own saves, however full, are never copied aside at login.
		for (Memory own : new Memory[]{new Memory(), good()})
		{
			String json = GSON.toJson(own);
			assertFalse(Salvage.wouldLose(GSON, json, Memory.read(GSON, json)));
		}
		// A newer version's field, or a damaged one, would be lost at the next save: kept aside first.
		String json = GSON.toJson(good());
		String newer = json.replaceFirst("\\{", "{\"addedLater\":[1,2],");
		assertTrue(Salvage.wouldLose(GSON, newer, Memory.read(GSON, newer)));
		String damaged = json.replaceFirst("\"since\":-?[0-9]+", "\"since\":{\"x\":true}");
		assertTrue(Salvage.wouldLose(GSON, damaged, Memory.read(GSON, damaged)));
	}
}
