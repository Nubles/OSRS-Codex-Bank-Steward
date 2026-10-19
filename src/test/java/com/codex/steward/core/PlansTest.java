package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PlansTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void navigatorGoalsAreReadAndJunkIsIgnored()
	{
		List<String> goals = Plans.readGoals(GSON, "[\"quest:Dragon Slayer II\",\"diary:Ardougne Easy\",\"cape:Quest point cape\",42,null,\"nonsense\",\"quest:Dragon Slayer II\",\"skill:Herblore 70\"]");
		// Unknown kinds are kept; they simply cover no tasks.
		assertEquals(Arrays.asList("quest:Dragon Slayer II", "diary:Ardougne Easy", "cape:Quest point cape", "skill:Herblore 70"), goals);
		assertTrue(Plans.readGoals(GSON, null).isEmpty());
		assertTrue(Plans.readGoals(GSON, "").isEmpty());
		assertTrue(Plans.readGoals(GSON, "{\"goals\":[]}").isEmpty());
		assertTrue(Plans.readGoals(GSON, "[\"quest:Unclosed").isEmpty());
		assertEquals("Quest cape", Plans.goalName("cape:Quest point cape"));
		assertEquals("Ardougne Easy", Plans.goalName("diary:Ardougne Easy"));
	}

	@Test
	public void kitsV1GivesEveryItemOfSavedKitsAndOnlyTheChosenPreset()
	{
		String v1 = "{\"version\":1,\"active\":\"preset:vorkath\",\"target\":10,\"future\":{\"x\":1},\"kits\":["
			+ "{\"id\":\"kit:3\",\"name\":\"Slayer\",\"preset\":false,\"tripsLeft\":14,\"needs\":["
			+ "{\"supply\":\"FOOD\",\"perTrip\":200,\"items\":{\"385\":20,\"13441\":22}},"
			+ "{\"supply\":\"item:8013\",\"perTrip\":1,\"items\":{\"8013\":1}},"
			+ "{\"supply\":\"NEW_GROUP\",\"perTrip\":4,\"items\":{\"99999\":1,\"bad\":2}}]},"
			+ "{\"id\":\"preset:vorkath\",\"name\":\"Vorkath\",\"preset\":true,\"needs\":[{\"supply\":\"item:11865\",\"items\":{\"11865\":1}}]},"
			+ "{\"id\":\"preset:zulrah\",\"name\":\"Zulrah\",\"preset\":true,\"needs\":[{\"supply\":\"item:12938\",\"items\":{\"12938\":1}}]}]}";
		List<Plans.Kit> kits = Plans.readKits(GSON, v1, "{\"active\":\"preset:vorkath\",\"kits\":[{\"id\":\"kit:9\",\"name\":\"Ignored\",\"needs\":[]}]}");
		assertEquals(2, kits.size());
		assertEquals("Slayer", kits.get(0).getName());
		assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(385, 13441, 8013, 99999)), kits.get(0).getItems());
		assertEquals("Vorkath", kits.get(1).getName());

		Map<Integer, List<String>> by = new Plans(kits, Collections.emptyList()).kitsByItem();
		assertEquals(Collections.singletonList("Slayer"), by.get(385));
		assertEquals(null, by.get(12938));
	}

	@Test
	public void olderQuartermasterSavesAreReadThroughItsSupplyGroups()
	{
		String quarters = "{\"bank\":{\"385\":100},\"active\":\"kit:1\",\"kits\":["
			+ "{\"id\":\"kit:1\",\"name\":\"Barrows\",\"needs\":[{\"supply\":\"PRAYER\",\"perTrip\":12},{\"supply\":\"item:19629\",\"perTrip\":1},{\"supply\":\"MYSTERY\",\"perTrip\":1}]},"
			+ "{\"id\":\"kit:2\",\"name\":null,\"needs\":[{\"supply\":\"FOOD\",\"perTrip\":10}]},"
			+ "{\"id\":\"kit:3\",\"name\":\"Empty\",\"needs\":[{\"supply\":\"MYSTERY\",\"perTrip\":1}]}]}";
		List<Plans.Kit> kits = Plans.readKits(GSON, null, quarters);
		assertEquals(1, kits.size());
		assertTrue(kits.get(0).getItems().contains(ItemID._4DOSEPRAYERRESTORE));
		assertTrue(kits.get(0).getItems().contains(ItemID.SANFEW_SALVE_1_DOSE));
		assertTrue(kits.get(0).getItems().contains(19629));
		assertTrue(Plans.readKits(GSON, "not json {", "[1,2]").isEmpty());
		assertTrue(Plans.readKits(GSON, null, null).isEmpty());
	}
}
