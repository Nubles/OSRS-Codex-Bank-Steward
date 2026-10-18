package com.codex.steward.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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

	@Test
	public void bundledDataLoadsAndEveryQuestResolves() throws Exception
	{
		Catalog catalog = load();
		assertTrue(catalog.getTasks().size() > 200);
		assertTrue(catalog.getNeeds().size() > 1000);
		for (Task task : catalog.getTasks())
		{
			if (task.isDiary())
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
	public void staplesAndLastingRewardsAreGuarded() throws Exception
	{
		Catalog catalog = load();
		assertTrue(catalog.isGeneral(995));
		assertTrue(catalog.isGeneral(561));
		assertTrue(catalog.isLasting(552));
		assertTrue(catalog.isLasting(4251));
		assertFalse(catalog.isGeneral(38));
		assertFalse(catalog.needsOf(38).isEmpty());
	}
}
