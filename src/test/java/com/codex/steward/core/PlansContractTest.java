package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Reads exactly what the other Codex plugins save. Each fixture was written by the writer's own
 * code (Quartermaster's {@code KitBook.of} and {@code Quarters}, Navigator's {@code GoalStore}),
 * serialised with Gson as their plugins do, so a change to their shape shows up here.
 */
public class PlansContractTest
{
	private static final Gson GSON = new Gson();

	private static String fixture(String name) throws Exception
	{
		try (InputStream in = PlansContractTest.class.getResourceAsStream(name))
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

	@Test
	public void groupAndKeyNamesMatchTheWriters()
	{
		// CodexQuartermasterConfig.GROUP, KitBook.KEY and the plugin's QUARTERS_KEY; both are
		// written per RS profile with setConfiguration(GROUP, rsProfileKey, KEY, json).
		assertEquals("codexquartermaster", Plans.QUARTERMASTER);
		assertEquals("kitsV1", Plans.KITS_KEY);
		assertEquals("quarters", Plans.QUARTERS_KEY);
		// CodexNavigatorConfig.GROUP and GOALS_KEY, written with setRSProfileConfiguration.
		assertEquals("codexnavigator", Plans.NAVIGATOR);
		assertEquals("goals", Plans.GOALS_KEY);
	}

	@Test
	public void savedKitIsReadFromKitsV1WithEveryItemItCounts() throws Exception
	{
		List<Plans.Kit> kits = Plans.readKits(GSON, fixture("quartermaster-saved-kitsV1.json"),
			fixture("quartermaster-saved-quarters.json"));
		// The kit the player saved, and no preset: none was chosen.
		assertEquals(1, kits.size());
		Plans.Kit kit = kits.get(0);
		assertEquals("Slayer \"Konar\"", kit.getName());
		assertTrue(kit.getItems().containsAll(Arrays.asList(ItemID.SHARK, ItemID.MANTARAY, ItemID.DEATHRUNE, ItemID.SPADE,
			ItemID._4DOSEPRAYERRESTORE, ItemID.SANFEW_SALVE_1_DOSE)));
		assertFalse("coins are never a supply", kit.getItems().contains(ItemID.COINS));
	}

	@Test
	public void olderSaveGivesTheSameItemsAsKitsV1() throws Exception
	{
		List<Plans.Kit> v1 = Plans.readKits(GSON, fixture("quartermaster-saved-kitsV1.json"), null);
		List<Plans.Kit> old = Plans.readKits(GSON, null, fixture("quartermaster-saved-quarters.json"));
		assertEquals(v1, old);
	}

	@Test
	public void aPresetCountsOnlyOnceThePlayerChoseIt() throws Exception
	{
		// A fresh Quartermaster names its first preset active in kitsV1 although nothing was chosen.
		String freshV1 = fixture("quartermaster-fresh-kitsV1.json");
		assertTrue(freshV1.contains("\"active\":\"preset:vorkath\""));
		assertTrue(Plans.readKits(GSON, freshV1, fixture("quartermaster-fresh-quarters.json")).isEmpty());

		List<Plans.Kit> chosen = Plans.readKits(GSON, fixture("quartermaster-preset-kitsV1.json"),
			fixture("quartermaster-preset-quarters.json"));
		assertEquals(2, chosen.size());
		assertEquals("Vorkath", chosen.get(1).getName());
		assertTrue(chosen.get(1).getItems().contains(ItemID.DRAGON_BOLTS_ENCHANTED_RUBY));
		assertTrue(chosen.get(1).getItems().contains(ItemID.ANTIVENOM4));

		// Without a readable quarters, kitsV1's own word is taken.
		assertEquals(1, Plans.readKits(GSON, freshV1, null).size());
	}

	@Test
	public void navigatorGoalsMapOntoTheBundledQuestsAndDiaries() throws Exception
	{
		String json = fixture("navigator-goals.json");
		// Gson escapes the apostrophe; the reader must undo it to find the quest.
		assertTrue(json.contains("\\u0027"));
		List<String> goals = Plans.readGoals(GSON, json);
		assertEquals(Arrays.asList("diary:Ardougne Hard", "quest:Monkey Madness II", "quest:Pirate's Treasure",
			"cape:Quest point cape"), goals);

		Catalog catalog;
		try (Reader reader = new InputStreamReader(PlansContractTest.class.getResourceAsStream("/com/codex/steward/steward-data.json"),
			StandardCharsets.UTF_8))
		{
			catalog = new Catalog(GSON.fromJson(reader, Catalog.Data.class));
		}
		for (String key : goals.subList(0, 3))
		{
			assertTrue(key + " covers no task", catalog.goalTasks(key).length > 0);
		}
		Map<Integer, List<String>> byTask = new Plans(java.util.Collections.emptyList(), goals).goalsByTask(catalog);
		boolean named = false;
		for (List<String> names : byTask.values())
		{
			named |= names.contains("Pirate's Treasure");
		}
		assertTrue(named);
	}
}
