package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.steward.core.BankedXp.Line;
import com.codex.steward.core.BankedXp.Method;
import com.codex.steward.core.BankedXp.Report;
import com.codex.steward.core.BankedXp.Skilled;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class BankedXpTest
{
	private static Report report(Map<Integer, Long> owned, Map<Skill, Integer> levels, Method method)
	{
		return BankedXp.of(owned, s -> Experience.getXpForLevel(levels.getOrDefault(s, 1)), true, method);
	}

	private static Skilled skill(Report r, Skill skill)
	{
		for (Skilled s : r.getSkills())
		{
			if (s.getSkill() == skill)
			{
				return s;
			}
		}
		return null;
	}

	private static Map<Skill, Integer> at(Skill skill, int level)
	{
		Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		m.put(skill, level);
		return m;
	}

	@Test
	public void shortSecondariesAreSaidAndTheRestStillCleans()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.UNIDENTIFIED_RANARR, 100L);
		owned.put(ItemID.VIAL_WATER, 100L);
		owned.put(ItemID.SNAPE_GRASS, 60L);
		Skilled h = skill(report(owned, at(Skill.HERBLORE, 40), Method.BURY), Skill.HERBLORE);
		// 60 cleaned and made into prayer potions, 40 only cleaned: no white berries for defence potions.
		assertEquals(60 * (7.5 + 87.5) + 40 * 7.5, h.getXp(), 0.001);
		Line ranarr = h.getLines().get(0);
		assertEquals("Prayer potion(3)", ranarr.getBest().getName());
		assertEquals(40L, (long) ranarr.getMissing().get(ItemID.SNAPE_GRASS));
		assertNull(ranarr.getMissing().get(ItemID.VIAL_WATER));
		assertEquals(40 * 87.5, ranarr.getHeldBack(), 0.001);
		assertEquals(0, ranarr.idle());
		// Only the potions are timed, at the wiki's 2,500 an hour; the cleaning is said to be untimed.
		assertEquals(60 / 2500.0, h.getHours(), 1e-9);
		assertTrue(h.getUntimed().contains("cleaning herbs"));
	}

	@Test
	public void theRichestHerbTakesTheVialsFirst()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.GUAM_LEAF, 50L);
		owned.put(ItemID.RANARR_WEED, 50L);
		owned.put(ItemID.VIAL_WATER, 60L);
		owned.put(ItemID.EYE_OF_NEWT, 50L);
		owned.put(ItemID.SNAPE_GRASS, 50L);
		Skilled h = skill(report(owned, at(Skill.HERBLORE, 50), Method.BURY), Skill.HERBLORE);
		assertEquals(50 * 87.5 + 10 * 25, h.getXp(), 0.001);
		assertEquals(40L, (long) h.getMissing().get(ItemID.VIAL_WATER));
		assertEquals(ItemID.RANARR_WEED, h.getLines().get(0).getItemId());
	}

	@Test
	public void bonesFollowThePlaceChosenAndTheComparisonShowsEvery()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.DRAGON_BONES, 100L);
		Report gilded = report(owned, at(Skill.PRAYER, 43), Method.GILDED_ALTAR);
		assertEquals(25_200, skill(gilded, Skill.PRAYER).getXp(), 0.001);
		assertEquals(7_200, gilded.getPrayer().get(Method.BURY), 0.001);
		assertEquals(50_400, gilded.getPrayer().get(Method.CHAOS_ALTAR), 0.001);
		// No pots or slime: the Ectofuntus can take none of them, and says what it lacks.
		assertEquals(0, gilded.getPrayer().get(Method.ECTOFUNTUS), 0.001);
		Skilled ecto = skill(report(owned, at(Skill.PRAYER, 43), Method.ECTOFUNTUS), Skill.PRAYER);
		assertEquals(100L, (long) ecto.getMissing().get(ItemID.BUCKET_ECTOPLASM));
		assertEquals(100L, (long) ecto.getMissing().get(ItemID.POT_EMPTY));
		assertEquals(100, ecto.getLines().get(0).idle());
		// 2,550 bones an hour at the gilded altar; the chaos altar's 2,000 offerings take two a bone.
		assertEquals(100 / 2550.0, skill(gilded, Skill.PRAYER).getHours(), 1e-9);
		Skilled chaos = skill(report(owned, at(Skill.PRAYER, 43), Method.CHAOS_ALTAR), Skill.PRAYER);
		assertEquals(200 / 2000.0, chaos.getHours(), 1e-9);
	}

	@Test
	public void superiorBonesWaitForSeventyPrayer()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.DRAGON_BONES_SUPERIOR, 10L);
		Skilled p = skill(report(owned, at(Skill.PRAYER, 69), Method.BURY), Skill.PRAYER);
		assertEquals(0, p.getXp(), 0);
		Line l = p.getLines().get(0);
		assertNull(l.getBest());
		assertEquals(70, l.getNeedsLevel());
		assertEquals(70, l.getNext().getLevel());
		assertEquals(10, l.idle());
	}

	@Test
	public void levelsReachedComeFromTheExperienceTable()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.RAW_SHARK, 10_000L);
		Skilled c = skill(report(owned, at(Skill.COOKING, 80), Method.BURY), Skill.COOKING);
		assertEquals(2_100_000, c.getXp(), 0.001);
		assertEquals(80, c.levelNow());
		long after = Experience.getXpForLevel(80) + 2_100_000L;
		assertEquals(Experience.getLevelForXp((int) after), c.levelAfter());
		assertTrue(c.levels() > 0);
		assertTrue(c.towardNext() >= 0 && c.towardNext() < 1);
		assertEquals(10_000 / 1365.0, c.getHours(), 1e-9);
	}

	@Test
	public void ninetyNineIsTheTop()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.RAW_SHARK, 100_000L);
		Skilled c = skill(report(owned, at(Skill.COOKING, 98), Method.BURY), Skill.COOKING);
		assertEquals(99, c.levelAfter());
		assertEquals(1, c.towardNext(), 0);
	}

	@Test
	public void steelWithoutCoalFallsBackToIron()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.IRON_ORE, 100L);
		owned.put(ItemID.COAL, 100L);
		Skilled s = skill(report(owned, at(Skill.SMITHING, 50), Method.BURY), Skill.SMITHING);
		Line iron = s.getLines().get(0);
		assertEquals("Steel platebody", iron.getBest().getName());
		// 50 ores have their two coal each and go to steel; the other 50 are iron platebodies.
		assertEquals(50 * (17.5 + 37.5) + 50 * (12.5 + 25), s.getXp(), 0.001);
		assertEquals(100L, (long) iron.getMissing().get(ItemID.COAL));
		assertTrue(s.getUntimed().contains("smelting"));
	}

	@Test
	public void logsAreStrungWhileTheBowStringsLast()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.YEW_LOGS, 10L);
		owned.put(ItemID.BOW_STRING, 4L);
		Skilled f = skill(report(owned, at(Skill.FLETCHING, 70), Method.BURY), Skill.FLETCHING);
		assertEquals(4 * 150 + 6 * 75, f.getXp(), 0.001);
		// Below 70 the yew shortbow is the best there is, and the longbow is the next step up.
		Line low = skill(report(owned, at(Skill.FLETCHING, 66), Method.BURY), Skill.FLETCHING).getLines().get(0);
		assertEquals("Yew shortbow", low.getBest().getName());
		assertEquals(70, low.getNext().getLevel());
		// The same logs count in Firemaking too.
		assertFalse(skill(report(owned, at(Skill.FIREMAKING, 70), Method.BURY), Skill.FIREMAKING).getLines().isEmpty());
	}

	@Test
	public void leatherGoesToBodiesAndHidesAreTannedFirst()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.DRAGONHIDE_BLACK, 30L);
		Skilled c = skill(report(owned, at(Skill.CRAFTING, 84), Method.BURY), Skill.CRAFTING);
		assertEquals(30 * 86, c.getXp(), 0.001);
		assertEquals("Black d'hide body", c.getLines().get(0).main().getName());
		assertEquals(10 / 1705.0, c.getHours(), 1e-9);
	}

	@Test
	public void nothingOwnedIsAnEmptyReport()
	{
		Report r = report(new HashMap<>(), new EnumMap<>(Skill.class), Method.GILDED_ALTAR);
		assertTrue(r.isEmpty());
		assertNull(r.biggest());
	}

	@Test
	public void theBiggestGainIsBySkillLevels()
	{
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(ItemID.RAW_SHRIMP, 10L);
		owned.put(ItemID.LOGS, 5000L);
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.COOKING, 1);
		levels.put(Skill.FIREMAKING, 1);
		Report r = report(owned, levels, Method.BURY);
		assertEquals(Skill.FIREMAKING, r.biggest().getSkill());
	}
}
