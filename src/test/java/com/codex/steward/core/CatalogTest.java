package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
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
		// Recipe for Disaster's parts are each read on their own, so a finished part frees its items.
		assertTrue(catalog.getTasks().get(taskIndex(catalog, "RFD - Evil Dave")).getQuest().equals("RECIPE_FOR_DISASTER__EVIL_DAVE"));
		assertTrue(catalog.getTasks().get(taskIndex(catalog, "RFD - Finale")).getQuest().equals("RECIPE_FOR_DISASTER__CULINAROMANCER"));
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
			5282, // Mushroom spore: a farming seed
			22818, // Fish chunks: aerial fishing bait
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

	/**
	 * A choice coins can meet ("2 kegs of beer or 650 coins", "telegrab or 10,000 coins") does not
	 * ask for the item, and once took the coins' quantity: up to 20,003 telegrab tablets were
	 * called done with after Dragon Slayer.
	 */
	@Test
	public void choicesCoinsCanMeetNeverMakeAnItemSpent() throws Exception
	{
		Catalog catalog = load();
		for (int id : new int[]{8022, 3801})
		{
			for (Catalog.Need need : catalog.needsOf(id))
			{
				assertFalse(id + " for " + catalog.getTasks().get(need.getTask()).getName(), need.isFirm());
			}
			assertFalse(catalog.needsOf(id).isEmpty());
		}
	}

	@Test
	public void bundledDataKnowsStashGearAndNavigatorGoals() throws Exception
	{
		Catalog catalog = load();
		// Steel platebody fills a medium STASH unit; a lobster fills none.
		assertTrue((catalog.stashTiers(net.runelite.api.gameval.ItemID.STEEL_PLATEBODY) & 4) != 0);
		assertTrue(catalog.stashTiers(net.runelite.api.gameval.ItemID.LOBSTER) == 0);
		// Dragon Slayer II covers itself and the quests before it, all the way down.
		Set<Integer> ds2 = new HashSet<>();
		for (int t : catalog.goalTasks("quest:Dragon Slayer II"))
		{
			ds2.add(t);
		}
		assertTrue(ds2.contains(taskIndex(catalog, "Dragon Slayer II")));
		assertTrue(ds2.contains(taskIndex(catalog, "Dragon Slayer I")));
		assertTrue(ds2.contains(taskIndex(catalog, "Legends' Quest")));
		assertTrue(ds2.contains(taskIndex(catalog, "Shield of Arrav - Phoenix Gang")));
		assertFalse(ds2.contains(taskIndex(catalog, "Cook's Assistant")));
		// Navigator names diaries without "Diary", and some areas shorter.
		int[] kourend = catalog.goalTasks("diary:Kourend Easy");
		assertTrue(kourend.length > 0);
		assertTrue(catalog.goalTasks("diary:Lumbridge Elite").length > 0);
		// Every quest for the cape, and nothing for a goal the Steward does not know.
		assertTrue(catalog.goalTasks("cape:Quest point cape").length > 150);
		assertTrue(catalog.goalTasks("quest:Not A Quest").length == 0);
		for (int[] list : new int[][]{catalog.goalTasks("quest:Desert Treasure II - The Fallen Empire"), kourend})
		{
			for (int t : list)
			{
				assertTrue(t >= 0 && t < catalog.getTasks().size());
			}
		}
	}

	@Test
	public void navigatorGoalsForQuestsQuestHelperLacksStillMatch() throws Exception
	{
		Catalog catalog = load();
		// Quest Helper has no helper for these yet, so they hold no items, but Navigator can pin
		// them: "quest:Learning the Ropes" used to cover nothing at all and never matched.
		for (String name : new String[]{"Learning the Ropes", "The Frozen Door", "Into the Tombs"})
		{
			int own = taskIndex(catalog, name);
			Task task = catalog.getTasks().get(own);
			assertEquals(name, Quest.valueOf(task.getQuest()).getName());
			boolean covered = false;
			for (int t : catalog.goalTasks("quest:" + name))
			{
				covered |= t == own;
			}
			assertTrue(name, covered);
		}
		// Every quest goal Navigator can save is named as RuneLite names the quest.
		Set<String> runelite = new HashSet<>();
		for (Quest q : Quest.values())
		{
			runelite.add(q.getName());
		}
		for (Task task : catalog.getTasks())
		{
			if (!task.usesVarbit() && catalog.goalTasks("quest:" + task.getName()).length > 0)
			{
				assertTrue(task.getName(), runelite.contains(task.getName()));
			}
		}
	}
}
