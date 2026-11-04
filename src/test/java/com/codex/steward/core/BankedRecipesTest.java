package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.codex.steward.core.BankedXp.Method;
import com.codex.steward.core.BankedXp.Recipe;
import com.codex.steward.core.BankedXp.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.ConstructionAction;
import net.runelite.client.plugins.skillcalculator.skills.CookingAction;
import net.runelite.client.plugins.skillcalculator.skills.CraftingAction;
import net.runelite.client.plugins.skillcalculator.skills.FiremakingAction;
import net.runelite.client.plugins.skillcalculator.skills.FletchingAction;
import net.runelite.client.plugins.skillcalculator.skills.HerbloreAction;
import net.runelite.client.plugins.skillcalculator.skills.ItemSkillAction;
import net.runelite.client.plugins.skillcalculator.skills.PrayerAction;
import net.runelite.client.plugins.skillcalculator.skills.PrayerBonus;
import net.runelite.client.plugins.skillcalculator.skills.SkillAction;
import net.runelite.client.plugins.skillcalculator.skills.SmithingAction;
import org.junit.Test;

/**
 * The facts test: every level and experience figure in the banked recipes, read on the OSRS Wiki,
 * checked against RuneLite's own skill calculator data (and the altar multipliers against its prayer
 * bonuses), so a figure typed wrong, or one the game changes and RuneLite follows, fails the build.
 */
public class BankedRecipesTest
{
	/**
	 * Where the wiki and RuneLite's calculator differ, the wiki stands: stringing a willow shortbow
	 * gives 33.2 and a magic shortbow 83.2 (OSRS Wiki: Fletching, the strung bows table), where
	 * RuneLite gives the cutting figure for both.
	 */
	private static final Map<Integer, Double> WIKI_XP = new HashMap<>();

	static
	{
		WIKI_XP.put(ItemID.WILLOW_SHORTBOW, 33.2);
		WIKI_XP.put(ItemID.MAGIC_SHORTBOW, 83.2);
	}

	private static SkillAction[] actions(Skill s)
	{
		switch (s)
		{
			case HERBLORE:
				return HerbloreAction.values();
			case COOKING:
				return CookingAction.values();
			case CRAFTING:
				return CraftingAction.values();
			case FLETCHING:
				return FletchingAction.values();
			case SMITHING:
				return SmithingAction.values();
			case CONSTRUCTION:
				return ConstructionAction.values();
			case FIREMAKING:
				return FiremakingAction.values();
			case PRAYER:
				return PrayerAction.values();
			default:
				return new SkillAction[0];
		}
	}

	private static List<SkillAction> find(Skill skill, Step step)
	{
		List<SkillAction> out = new ArrayList<>();
		for (SkillAction a : actions(skill))
		{
			if (a instanceof ItemSkillAction ? ((ItemSkillAction) a).getItemId() == step.getProduct()
				: a.getIcon() == step.getProduct() && a.getName(null).equalsIgnoreCase(step.getName()))
			{
				out.add(a);
			}
		}
		return out;
	}

	/** Every step of every recipe, once, with its skill. */
	private static Map<Step, Skill> steps()
	{
		Map<Step, Skill> out = new java.util.LinkedHashMap<>();
		for (Skill skill : BankedXp.SKILLS)
		{
			for (List<Recipe> recipes : BankedRecipes.forSkill(skill, Method.BURY).values())
			{
				for (Recipe r : recipes)
				{
					for (Step s : r.getSteps())
					{
						out.put(s, skill);
					}
				}
			}
		}
		return out;
	}

	@Test
	public void everyLevelAndExperienceIsTheWikisAndRuneLites()
	{
		int checked = 0;
		for (Map.Entry<Step, Skill> e : steps().entrySet())
		{
			Step s = e.getKey();
			if (s.getXp() == 0)
			{
				// Unfinished potions and tanning give nothing, and use nothing up for time.
				assertEquals(s.getName(), 0, s.getActionsPerUnit(), 0);
				continue;
			}
			double perAction = s.getXp() / s.getActionsPerUnit();
			List<SkillAction> found = find(e.getValue(), s);
			assertFalse(s.getName() + " (" + s.getProduct() + ") is not in RuneLite's " + e.getValue() + " calculator", found.isEmpty());
			Double wiki = WIKI_XP.get(s.getProduct());
			boolean agrees = false;
			for (SkillAction a : found)
			{
				if (wiki != null)
				{
					assertTrue(s.getName() + ": RuneLite now agrees with the wiki, drop it from WIKI_XP", Math.abs(a.getXp() - wiki) > 0.05);
					agrees |= a.getLevel() == s.getLevel() && Math.abs(perAction - wiki) < 0.01;
				}
				else
				{
					agrees |= a.getLevel() == s.getLevel() && Math.abs(a.getXp() - perAction) < 0.01;
				}
			}
			assertTrue(s.getName() + ": level " + s.getLevel() + ", " + perAction + " xp is not RuneLite's "
				+ found.get(0).getLevel() + ", " + found.get(0).getXp(), agrees);
			checked++;
		}
		assertTrue("checked " + checked, checked > 120);
	}

	@Test
	public void theAltarsMultiplyAsRuneLitesBonusesDo()
	{
		assertEquals(PrayerBonus.LIT_GILDED_ALTAR.getValue(), Method.GILDED_ALTAR.getMultiplier(), 0.001);
		assertEquals(PrayerBonus.ECTOFUNTUS.getValue(), Method.ECTOFUNTUS.getMultiplier(), 0.001);
		assertEquals(PrayerBonus.CHAOS_ALTAR.getValue(), Method.CHAOS_ALTAR.getMultiplier(), 0.001);
		assertEquals(1, Method.BURY.getMultiplier(), 0);
		for (Method m : Method.values())
		{
			for (Map.Entry<Integer, double[]> bone : BankedRecipes.bones().entrySet())
			{
				Recipe r = BankedRecipes.forSkill(Skill.PRAYER, m).get(bone.getKey()).get(0);
				assertEquals(bone.getValue()[1] * m.getMultiplier(), r.getXp(), 0.001);
				assertEquals((int) bone.getValue()[0], r.getLevel());
			}
		}
		// Only the Ectofuntus uses anything up besides the bone: a pot for the bonemeal and a bucket of slime.
		Recipe ecto = BankedRecipes.forSkill(Skill.PRAYER, Method.ECTOFUNTUS).get(ItemID.DRAGON_BONES).get(0);
		assertEquals(2, ecto.getNeeds().size());
		assertEquals(1, (int) ecto.getNeeds().get(ItemID.POT_EMPTY));
		assertEquals(1, (int) ecto.getNeeds().get(ItemID.BUCKET_ECTOPLASM));
		assertTrue(BankedRecipes.forSkill(Skill.PRAYER, Method.GILDED_ALTAR).get(ItemID.DRAGON_BONES).get(0).getNeeds().isEmpty());
	}

	@Test
	public void ashesAreOnlyEverScattered()
	{
		for (int ashes : new int[]{ItemID.FIENDISH_ASHES, ItemID.VILE_ASHES, ItemID.MALICIOUS_ASHES, ItemID.ABYSSAL_ASHES, ItemID.INFERNAL_ASHES})
		{
			double scatter = BankedRecipes.forSkill(Skill.PRAYER, Method.BURY).get(ashes).get(0).getXp();
			for (Method m : Method.values())
			{
				assertEquals(scatter, BankedRecipes.forSkill(Skill.PRAYER, m).get(ashes).get(0).getXp(), 0);
			}
		}
	}

	@Test
	public void ratesAreTheWikisFigures()
	{
		assertEquals(2500, BankedXp.Rates.POTIONS.getPerHour(), 0);
		assertEquals(1365, BankedXp.Rates.FISH.getPerHour(), 0);
		assertEquals(1385, BankedXp.Rates.KARAMBWAN.getPerHour(), 0);
		assertEquals(1485, BankedXp.Rates.LOGS.getPerHour(), 0);
		assertEquals(2000, BankedXp.Rates.CUT.getPerHour(), 0.01);
		assertEquals(3000, BankedXp.Rates.STRING.getPerHour(), 0.01);
		assertEquals(2780, BankedXp.Rates.GEMS.getPerHour(), 0);
		assertEquals(1705, BankedXp.Rates.BODIES.getPerHour(), 0);
		assertEquals(1750, BankedXp.Rates.GLASS.getPerHour(), 0);
		assertEquals(1200, BankedXp.Rates.ANVIL.getPerHour(), 0.01);
		assertEquals(9166, BankedXp.Rates.OAK.getPerHour(), 0);
		assertEquals(7856, BankedXp.Rates.MAHOGANY.getPerHour(), 0);
		assertEquals(2550, BankedXp.Rates.GILDED.getPerHour(), 0);
		assertEquals(2000, BankedXp.Rates.CHAOS.getPerHour(), 0);
		assertEquals(180, BankedXp.Rates.ECTO.getPerHour(), 0);
	}

	@Test
	public void secondariesAreTheWikisIngredients()
	{
		// Prayer potion: "Vial of water, Ranarr weed, Snape grass" (OSRS Wiki: Herblore).
		Map<Integer, Integer> prayer = null;
		for (Recipe r : BankedRecipes.forSkill(Skill.HERBLORE, Method.BURY).get(ItemID.RANARR_WEED))
		{
			if (r.getName().equals("Prayer potion(3)"))
			{
				prayer = r.getNeeds();
			}
		}
		assertEquals(2, prayer.size());
		assertEquals(1, (int) prayer.get(ItemID.VIAL_WATER));
		assertEquals(1, (int) prayer.get(ItemID.SNAPE_GRASS));
		// Steel: "Iron ore x1, Coal x2"; runite: "Runite ore x1, Coal x8" (OSRS Wiki: Smithing).
		boolean steel = false;
		for (Recipe r : BankedRecipes.forSkill(Skill.SMITHING, Method.BURY).get(ItemID.IRON_ORE))
		{
			if (r.getSteps().get(0).getName().equals("Steel bar"))
			{
				steel = true;
				assertEquals(2, (int) r.getNeeds().get(ItemID.COAL));
			}
		}
		assertTrue(steel);
		assertEquals(8, (int) BankedRecipes.forSkill(Skill.SMITHING, Method.BURY).get(ItemID.RUNITE_ORE).get(0).getNeeds().get(ItemID.COAL));
		// Wooden bookcase: 4 planks and 4 steel nails.
		assertEquals(1, (int) BankedRecipes.forSkill(Skill.CONSTRUCTION, Method.BURY).get(ItemID.WOODPLANK).get(0).getNeeds().get(ItemID.NAILS));
	}
}
