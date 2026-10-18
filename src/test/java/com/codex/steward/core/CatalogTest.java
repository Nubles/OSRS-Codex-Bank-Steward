package com.codex.steward.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Quest;
import org.junit.Test;

public class CatalogTest
{
	private static Catalog load() throws Exception
	{
		try (Reader reader = new InputStreamReader(CatalogTest.class.getResourceAsStream("/com/codex/steward/steward-data.json"), StandardCharsets.UTF_8))
		{
			return new Catalog(new Gson().fromJson(reader, Catalog.Data.class));
		}
	}

	private static int taskIndex(Catalog catalog, String name)
	{
		for (int i = 0; i < catalog.getTasks().size(); i++)
		{
			if (catalog.getTasks().get(i).getName().equals(name))
			{
				return i;
			}
		}
		throw new AssertionError("no task " + name);
	}

	@Test
	public void bundledDataLoadsAndEveryTaskCanBeRead() throws Exception
	{
		Catalog catalog = load();
		assertTrue(catalog.getTasks().size() > 250);
		assertTrue(catalog.getNeeds().size() > 1000);
		for (Task task : catalog.getTasks())
		{
			if (task.usesVarbit())
			{
				assertTrue(task.getName(), task.getVarbit() > 0 && task.getValue() > 0);
			}
			else
			{
				assertNotNull(task.getName(), Quest.valueOf(task.getQuest()));
			}
		}
	}

	@Test
	public void questsWhoseItemsOnlyShowInTheirStepsStillHoldItems() throws Exception
	{
		Catalog catalog = load();
		// Quest Helper lists no upfront items for these, so their items come from the steps.
		for (String name : new String[]{"Cabin Fever", "Zogre Flesh Eaters", "The Restless Ghost", "Haunted Mine"})
		{
			int index = taskIndex(catalog, name);
			boolean any = false;
			for (Catalog.Need need : catalog.getNeeds())
			{
				any |= need.getTask() == index;
			}
			assertTrue(name, any);
		}
		// Recipe for Disaster's parts are read as the whole quest, so nothing is spent until it is all done.
		assertTrue(catalog.getTasks().get(taskIndex(catalog, "RFD - Evil Dave")).getQuest().equals("RECIPE_FOR_DISASTER"));
	}

	@Test
	public void staplesAndLastingRewardsAreGuarded() throws Exception
	{
		Catalog catalog = load();
		assertTrue(catalog.isGeneral(995));
		assertTrue(catalog.isGeneral(561));
		assertTrue(catalog.isLasting(552));
		assertTrue(catalog.isLasting(4251));
		assertFalse(catalog.isGeneral(4300));
		assertFalse(catalog.isLasting(4300));
		assertFalse(catalog.needsOf(4300).isEmpty());
	}

	/** Items an earlier build called done with that a player still has a use for. */
	@Test
	public void toolsMaterialsAndAccessItemsAreNeverSpent() throws Exception
	{
		Catalog catalog = load();
		int[] guarded = {
			3157, // Karambwan vessel: the karambwan fishing tool
			2887, // Battered key: the way into the Elemental Workshop
			19685, // Dark totem: Skotizo's key
			2166, // Gnomebowl mould
			4, // Ammo mould: cannonballs
			6746, // Darklight: becomes Arclight
			4023, // Monkey talisman: greegrees
			294, // Glarial's pebble: Glarial's tomb
			3678, // Flamtaer hammer: the Mort'ton temple
			4155, // Enchanted gem
			12934, // Zulrah's scales
			1601, // Diamond
			2363, // Runite bar
			237, // Unicorn horn
			4286, // Bucket of slime: the Ectofuntus
			7225, // Iron spit
			954, // Rope
		};
		Set<Integer> unsafe = new HashSet<>();
		for (int id : guarded)
		{
			if (!catalog.isGeneral(id) && !catalog.isLasting(id))
			{
				unsafe.add(id);
			}
		}
		assertTrue("could be called spent: " + unsafe, unsafe.isEmpty());
	}
}
