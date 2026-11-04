package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * Banked experience: what the bank, the pack and what is worn would give if every herb, fish, log,
 * gem, hide, ore, plank and bone in them were processed, skill by skill, with the best recipe the
 * player's level allows today and only the secondaries they actually own. What a short secondary
 * holds back is said, with how many more would make the rest the best way.
 *
 * <p>Every level and experience figure is the OSRS Wiki's and is checked against RuneLite's own skill
 * calculator by BankedRecipesTest; the hourly rates are the wiki's training guides', and a step the
 * wiki gives no rate for is left untimed rather than guessed.
 */
public final class BankedXp
{
	/** The skills the Steward banks experience for, in the order the page shows them. */
	public static final List<Skill> SKILLS = Collections.unmodifiableList(java.util.Arrays.asList(
		Skill.PRAYER, Skill.HERBLORE, Skill.COOKING, Skill.FLETCHING, Skill.FIREMAKING, Skill.CRAFTING, Skill.SMITHING,
		Skill.CONSTRUCTION));

	/** Where bones go. Ashes are only ever scattered: the wiki says they "cannot be used on altars". */
	public enum Method
	{
		/** Buried by hand, at the bones' own experience. */
		BURY("Bury", 1, null),
		/** "When both incense burners are lit, the altar gives 350% experience per bone" (OSRS Wiki). */
		GILDED_ALTAR("Gilded altar", 3.5, Rates.GILDED),
		/** "four times the Prayer experience that would be gained from burying the bones" (OSRS Wiki). */
		ECTOFUNTUS("Ectofuntus", 4, Rates.ECTO),
		/**
		 * "the same 350% experience per bone as the gilded altar" with "an additional 50% chance for each
		 * offering to not be consumed" (OSRS Wiki): about two offerings, so seven times, a bone.
		 */
		CHAOS_ALTAR("Chaos altar", 7, Rates.CHAOS);

		private final String label;
		private final double multiplier;
		private final Rate rate;

		Method(String label, double multiplier, Rate rate)
		{
			this.label = label;
			this.multiplier = multiplier;
			this.rate = rate;
		}

		public String getLabel()
		{
			return label;
		}

		/** Experience a banked bone gives here, as a multiple of burying it. */
		public double getMultiplier()
		{
			return multiplier;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** A typical speed for one step, from the wiki's training guides, and how it is said. */
	public static final class Rate
	{
		final double perHour;
		/** Counted in the material itself (planks, bones) rather than in things made. */
		final boolean inUnits;
		final String text;

		Rate(double perHour, boolean inUnits, String text)
		{
			this.perHour = perHour;
			this.inUnits = inUnits;
			this.text = text;
		}

		public double getPerHour()
		{
			return perHour;
		}

		public String getText()
		{
			return text;
		}
	}

	/** The wiki's rates. Each text says what it counts, and that it is the wiki's. */
	static final class Rates
	{
		/** Herblore training: "they can expect to make around 2,500 potions per hour." */
		static final Rate POTIONS = new Rate(2500, false, "2,500 potions an hour");
		/** Pay-to-play Cooking training: "1,365 fish cooked per hour in the Hosidius Kitchen". */
		static final Rate FISH = new Rate(1365, false, "1,365 fish an hour");
		/** Pay-to-play Cooking training: "cooking 1,385 karambwan per hour at the Hosidius kitchen". */
		static final Rate KARAMBWAN = new Rate(1385, false, "1,385 karambwan an hour");
		/** Pay-to-play Firemaking training: "assumes 1,485 logs are burned per hour". */
		static final Rate LOGS = new Rate(1485, false, "1,485 logs an hour");
		/** Magic longbow (u), creation ticks "3 (1.8s)": the speed alone, before banking. */
		static final Rate CUT = new Rate(3600 / 1.8, false, "a bow cut every 3 ticks");
		/** Magic longbow, stringing ticks "2 (1.2s)": the speed alone, before banking. */
		static final Rate STRING = new Rate(3600 / 1.2, false, "a bow strung every 2 ticks, before banking");
		/** Pay-to-play Crafting training: "The rates assume players cut 2780 gems per hour." */
		static final Rate GEMS = new Rate(2780, false, "2,780 gems an hour");
		/** Pay-to-play Crafting training: "up to 1705 d'hide bodies per hour" without tick manipulation. */
		static final Rate BODIES = new Rate(1705, false, "1,705 d'hide bodies an hour");
		/** Pay-to-play Crafting training: "Assumes 1,750 glass items blown per hour." */
		static final Rate GLASS = new Rate(1750, false, "1,750 glass items an hour");
		/** Rune platebody, creation ticks "5 (3s)": the speed alone, before banking. */
		static final Rate ANVIL = new Rate(3600 / 3.0, false, "a platebody every 5 ticks, before banking");
		/** Construction training: "550,000 XP per hour with oak planks (9,166 planks per hour)". */
		static final Rate OAK = new Rate(9166, true, "up to 9,166 oak planks an hour");
		/** Construction training: "1,100,000 XP per hour with mahogany planks (7,856 planks per hour)". */
		static final Rate MAHOGANY = new Rate(7856, true, "up to 7,856 mahogany planks an hour");
		/** Pay-to-play Prayer training: "players can offer 2,550 bones per hour". */
		static final Rate GILDED = new Rate(2550, false, "2,550 bones an hour");
		/** Pay-to-play Prayer training: "players can offer up to around 2,000 bones per hour". */
		static final Rate CHAOS = new Rate(2000, false, "about 2,000 offerings an hour");
		/** Ectofuntus: "a player could worship approximately 180 times per hour". */
		static final Rate ECTO = new Rate(180, false, "about 180 offerings an hour");

		private Rates()
		{
		}
	}

	/**
	 * One step of turning a material into experience: cleaning a herb, mixing the potion, smelting the
	 * ore, smithing the bar. Figures are per unit of the material the recipe starts from.
	 */
	public static final class Step
	{
		final String name;
		final int level;
		/** Experience for one unit of the material. */
		final double xp;
		/** Things made from one unit: a fifth of a platebody from a bar, two offerings from a bone at the chaos altar. */
		final double actionsPerUnit;
		/** The item made, for RuneLite's calculator to check and for the picture; -1 for none. */
		final int product;
		final Rate rate;
		/** Secondaries used up per unit of the material, by item. */
		final Map<Integer, Integer> needs;

		Step(String name, int level, double xp, double actionsPerUnit, int product, Rate rate, Map<Integer, Integer> needs)
		{
			this.name = name;
			this.level = level;
			this.xp = xp;
			this.actionsPerUnit = actionsPerUnit;
			this.product = product;
			this.rate = rate;
			this.needs = needs;
		}

		public String getName()
		{
			return name;
		}

		public int getLevel()
		{
			return level;
		}

		public double getXp()
		{
			return xp;
		}

		public int getProduct()
		{
			return product;
		}

		public double getActionsPerUnit()
		{
			return actionsPerUnit;
		}

		public Rate getRate()
		{
			return rate;
		}

		/** Hours for one unit, or NaN when the wiki gives no rate for this step. */
		double hoursPerUnit()
		{
			if (rate == null)
			{
				return Double.NaN;
			}
			return (rate.inUnits ? 1 : actionsPerUnit) / rate.perHour;
		}
	}

	/** A way to turn one material into experience: one or more steps, the last naming it. */
	public static final class Recipe
	{
		final Skill skill;
		final List<Step> steps;

		Recipe(Skill skill, List<Step> steps)
		{
			this.skill = skill;
			this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
		}

		public Skill getSkill()
		{
			return skill;
		}

		public List<Step> getSteps()
		{
			return steps;
		}

		/** What it makes in the end: "Prayer potion(3)". */
		public String getName()
		{
			return steps.get(steps.size() - 1).name;
		}

		public int getProduct()
		{
			return steps.get(steps.size() - 1).product;
		}

		public int getLevel()
		{
			int level = 1;
			for (Step s : steps)
			{
				level = Math.max(level, s.level);
			}
			return level;
		}

		public double getXp()
		{
			double xp = 0;
			for (Step s : steps)
			{
				xp += s.xp;
			}
			return xp;
		}

		/** Secondaries per unit of the material, all steps together. */
		public Map<Integer, Integer> getNeeds()
		{
			Map<Integer, Integer> out = new LinkedHashMap<>();
			for (Step s : steps)
			{
				s.needs.forEach((id, n) -> out.merge(id, n, Integer::sum));
			}
			return out;
		}

		double actions()
		{
			double a = 0;
			for (Step s : steps)
			{
				a += s.rate != null && s.rate.inUnits ? 1 : s.actionsPerUnit;
			}
			return a;
		}

		@Override
		public String toString()
		{
			return getName();
		}
	}

	/** How one recipe was used on part of a material. */
	public static final class Use
	{
		final Recipe recipe;
		final long units;

		Use(Recipe recipe, long units)
		{
			this.recipe = recipe;
			this.units = units;
		}

		public Recipe getRecipe()
		{
			return recipe;
		}

		public long getUnits()
		{
			return units;
		}
	}

	/** One material the player owns, and what it comes to. */
	public static final class Line
	{
		final int itemId;
		final long count;
		final List<Use> uses;
		final double xp;
		/** The best recipe the level allows, secondaries or not; null when the level allows none. */
		final Recipe best;
		/** Secondaries the best recipe would still need to take the whole stack. */
		final Map<Integer, Long> missing;
		/** Experience the missing secondaries would add. */
		final double heldBack;
		/** A better recipe a higher level opens, or null. */
		final Recipe next;
		/** The lowest level any recipe for it needs, for a material the level does not allow yet. */
		final int needsLevel;

		Line(int itemId, long count, List<Use> uses, double xp, Recipe best, Map<Integer, Long> missing, double heldBack, Recipe next,
			int needsLevel)
		{
			this.itemId = itemId;
			this.count = count;
			this.uses = uses;
			this.xp = xp;
			this.best = best;
			this.missing = missing;
			this.heldBack = heldBack;
			this.next = next;
			this.needsLevel = needsLevel;
		}

		public int getItemId()
		{
			return itemId;
		}

		public long getCount()
		{
			return count;
		}

		public List<Use> getUses()
		{
			return uses;
		}

		public double getXp()
		{
			return xp;
		}

		public Recipe getBest()
		{
			return best;
		}

		public Map<Integer, Long> getMissing()
		{
			return missing;
		}

		public double getHeldBack()
		{
			return heldBack;
		}

		public Recipe getNext()
		{
			return next;
		}

		public int getNeedsLevel()
		{
			return needsLevel;
		}

		/** The recipe most of it goes to, or null when none of it can be processed yet. */
		public Recipe main()
		{
			Use top = null;
			for (Use u : uses)
			{
				if (top == null || u.units > top.units)
				{
					top = u;
				}
			}
			return top == null ? null : top.recipe;
		}

		/** Units that cannot be processed at all now, for want of level or secondaries. */
		public long idle()
		{
			long used = 0;
			for (Use u : uses)
			{
				used += u.units;
			}
			return count - used;
		}
	}

	/** One skill's banked experience. */
	public static final class Skilled
	{
		final Skill skill;
		final int xpNow;
		final double xp;
		final List<Line> lines;
		final Map<Integer, Long> missing;
		final double heldBack;
		final double hours;
		/** Steps with no wiki rate that some of it goes through, by name. */
		final Set<String> untimed;
		final Set<String> rates;

		Skilled(Skill skill, int xpNow, double xp, List<Line> lines, Map<Integer, Long> missing, double heldBack, double hours,
			Set<String> untimed, Set<String> rates)
		{
			this.skill = skill;
			this.xpNow = xpNow;
			this.xp = xp;
			this.lines = lines;
			this.missing = missing;
			this.heldBack = heldBack;
			this.hours = hours;
			this.untimed = untimed;
			this.rates = rates;
		}

		public Skill getSkill()
		{
			return skill;
		}

		public int getXpNow()
		{
			return xpNow;
		}

		public double getXp()
		{
			return xp;
		}

		public List<Line> getLines()
		{
			return lines;
		}

		/** Secondaries short, across the skill, to make everything the best way. */
		public Map<Integer, Long> getMissing()
		{
			return missing;
		}

		public double getHeldBack()
		{
			return heldBack;
		}

		/** Hours at the wiki's rates for the timed steps. */
		public double getHours()
		{
			return hours;
		}

		public Set<String> getUntimed()
		{
			return untimed;
		}

		public Set<String> getRates()
		{
			return rates;
		}

		public int levelNow()
		{
			return level(xpNow);
		}

		public int levelAfter()
		{
			return level(xpAfter());
		}

		public long xpAfter()
		{
			return Math.min(Experience.MAX_SKILL_XP, xpNow + (long) Math.floor(xp));
		}

		/** How far past the level reached the experience goes toward the next, 0 to 1; 1 at 99. */
		public double towardNext()
		{
			int level = levelAfter();
			if (level >= Experience.MAX_REAL_LEVEL)
			{
				return 1;
			}
			int from = Experience.getXpForLevel(level);
			int to = Experience.getXpForLevel(level + 1);
			return (xpAfter() - from) / (double) (to - from);
		}

		public int levels()
		{
			return levelAfter() - levelNow();
		}
	}

	/** The whole reckoning. */
	public static final class Report
	{
		public static final Report EMPTY = new Report(Collections.emptyList(), Method.GILDED_ALTAR, Collections.emptyMap(), false);

		final List<Skilled> skills;
		final Method method;
		/** Prayer experience each place for the bones would give, for the comparison. */
		final Map<Method, Double> prayer;
		final boolean levelsKnown;

		Report(List<Skilled> skills, Method method, Map<Method, Double> prayer, boolean levelsKnown)
		{
			this.skills = skills;
			this.method = method;
			this.prayer = prayer;
			this.levelsKnown = levelsKnown;
		}

		public List<Skilled> getSkills()
		{
			return skills;
		}

		public Method getMethod()
		{
			return method;
		}

		public Map<Method, Double> getPrayer()
		{
			return prayer;
		}

		/** Whether the account's experience was read; without it levels are not shown. */
		public boolean isLevelsKnown()
		{
			return levelsKnown;
		}

		public boolean isEmpty()
		{
			return skills.isEmpty();
		}

		/** The skill the bank moves furthest, by levels and then experience; null when none. */
		public Skilled biggest()
		{
			Skilled top = null;
			for (Skilled s : skills)
			{
				if (top == null || s.levels() > top.levels() || s.levels() == top.levels() && s.xp > top.xp)
				{
					top = s;
				}
			}
			return top;
		}

		/** Everything the page shows, so the panel is rebuilt only when it changes. */
		public String fingerprint()
		{
			StringBuilder b = new StringBuilder(method.name()).append(levelsKnown);
			for (Skilled s : skills)
			{
				b.append('|').append(s.skill).append(s.xpNow).append(':').append(Math.round(s.xp));
				for (Line l : s.lines)
				{
					b.append(',').append(l.itemId).append('x').append(l.count);
				}
				b.append(s.missing);
			}
			return b.toString();
		}
	}

	private BankedXp()
	{
	}

	/** The real level for an amount of experience, 1 to 99. */
	public static int level(long xp)
	{
		return Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp((int) Math.min(Experience.MAX_SKILL_XP, Math.max(0, xp))));
	}

	/**
	 * @param owned what the bank, pack and worn items hold, by unnoted item
	 * @param xpOf the player's experience in a skill; levels follow from it
	 * @param levelsKnown whether xpOf answers for this account, or every skill reads as level 1
	 * @param method where bones go
	 */
	public static Report of(Map<Integer, Long> owned, ToIntFunction<Skill> xpOf, boolean levelsKnown, Method method)
	{
		List<Skilled> skills = new ArrayList<>();
		Map<Method, Double> prayer = new EnumMap<>(Method.class);
		for (Skill skill : SKILLS)
		{
			int xpNow = levelsKnown ? xpOf.applyAsInt(skill) : 0;
			Skilled s = skill(skill, owned, xpNow, method);
			if (s != null)
			{
				skills.add(s);
			}
			if (skill == Skill.PRAYER && s != null)
			{
				for (Method m : Method.values())
				{
					prayer.put(m, m == method ? s.xp : skill(skill, owned, xpNow, m).xp);
				}
			}
		}
		return new Report(Collections.unmodifiableList(skills), method, Collections.unmodifiableMap(prayer), levelsKnown);
	}

	/** One skill's lines, secondaries going to the most valuable material first; null when none is owned. */
	static Skilled skill(Skill skill, Map<Integer, Long> owned, int xpNow, Method method)
	{
		int level = level(xpNow);
		Map<Integer, List<Recipe>> book = BankedRecipes.forSkill(skill, method);
		List<Integer> materials = new ArrayList<>();
		for (Integer id : book.keySet())
		{
			if (owned.getOrDefault(id, 0L) > 0)
			{
				materials.add(id);
			}
		}
		if (materials.isEmpty())
		{
			return null;
		}
		// The richest material takes its secondaries first: ranarr before guam when the vials run short.
		materials.sort(Comparator.comparingDouble((Integer id) -> -bestXp(book.get(id), level)).thenComparing(id -> id));
		Map<Integer, Long> left = new HashMap<>();
		for (List<Recipe> recipes : book.values())
		{
			for (Recipe r : recipes)
			{
				for (Integer need : r.getNeeds().keySet())
				{
					left.put(need, owned.getOrDefault(need, 0L));
				}
			}
		}
		List<Line> lines = new ArrayList<>();
		Map<Integer, Long> missing = new LinkedHashMap<>();
		double total = 0;
		double heldBack = 0;
		double hours = 0;
		Set<String> untimed = new LinkedHashSet<>();
		Set<String> rates = new LinkedHashSet<>();
		for (int id : materials)
		{
			long count = owned.get(id);
			List<Recipe> recipes = new ArrayList<>(book.get(id));
			// Most experience first; of two alike, the one with fewer actions (platebodies before daggers).
			recipes.sort(Comparator.comparingDouble((Recipe r) -> -r.getXp()).thenComparingDouble(Recipe::actions));
			Recipe best = null;
			Recipe next = null;
			int lowest = Integer.MAX_VALUE;
			for (Recipe r : recipes)
			{
				lowest = Math.min(lowest, r.getLevel());
				if (r.getLevel() <= level && best == null)
				{
					best = r;
				}
			}
			// The nearest level up that does better than today's best.
			for (Recipe r : recipes)
			{
				if (r.getLevel() > level && (best == null || r.getXp() > best.getXp())
					&& (next == null || r.getLevel() < next.getLevel() || r.getLevel() == next.getLevel() && r.getXp() > next.getXp()))
				{
					next = r;
				}
			}
			List<Use> uses = new ArrayList<>();
			long remaining = count;
			double xp = 0;
			for (Recipe r : recipes)
			{
				if (remaining == 0)
				{
					break;
				}
				if (r.getLevel() > level)
				{
					continue;
				}
				long n = remaining;
				for (Map.Entry<Integer, Integer> need : r.getNeeds().entrySet())
				{
					n = Math.min(n, left.getOrDefault(need.getKey(), 0L) / need.getValue());
				}
				if (n <= 0)
				{
					continue;
				}
				for (Map.Entry<Integer, Integer> need : r.getNeeds().entrySet())
				{
					left.merge(need.getKey(), -n * need.getValue(), Long::sum);
				}
				uses.add(new Use(r, n));
				xp += n * r.getXp();
				remaining -= n;
				for (Step s : r.steps)
				{
					double h = s.hoursPerUnit();
					if (Double.isNaN(h))
					{
						if (s.xp > 0 || s.actionsPerUnit > 0)
						{
							untimed.add(untimed(s.name));
						}
					}
					else
					{
						hours += n * h;
						rates.add(s.rate.text);
					}
				}
			}
			Map<Integer, Long> short_ = new LinkedHashMap<>();
			double back = 0;
			if (best != null)
			{
				long viaBest = 0;
				for (Use u : uses)
				{
					if (u.recipe == best)
					{
						viaBest = u.units;
					}
				}
				long without = count - viaBest;
				if (without > 0)
				{
					for (Map.Entry<Integer, Integer> need : best.getNeeds().entrySet())
					{
						long want = without * need.getValue();
						long have = Math.max(0, left.getOrDefault(need.getKey(), 0L));
						// The spare ones are spoken for: the next material cannot count them too.
						left.merge(need.getKey(), -Math.min(want, have), Long::sum);
						if (want > have)
						{
							short_.put(need.getKey(), want - have);
							missing.merge(need.getKey(), want - have, Long::sum);
						}
					}
					back = count * best.getXp() - xp;
				}
			}
			heldBack += back;
			total += xp;
			lines.add(new Line(id, count, Collections.unmodifiableList(uses), xp, best, Collections.unmodifiableMap(short_), back, next,
				lowest == Integer.MAX_VALUE ? 0 : lowest));
		}
		lines.sort(Comparator.comparingDouble((Line l) -> -l.xp).thenComparingDouble(l -> -l.heldBack).thenComparingInt(l -> l.itemId));
		return new Skilled(skill, xpNow, total, Collections.unmodifiableList(lines), Collections.unmodifiableMap(missing), heldBack, hours,
			Collections.unmodifiableSet(untimed), Collections.unmodifiableSet(rates));
	}

	/** What a step with no wiki rate is called where the page says it is not timed. */
	static String untimed(String step)
	{
		String lower = step.toLowerCase(java.util.Locale.ENGLISH);
		if (lower.equals("clean"))
		{
			return "cleaning herbs";
		}
		if (lower.equals("bury"))
		{
			return "burying";
		}
		if (lower.equals("scatter"))
		{
			return "scattering ashes";
		}
		if (lower.endsWith(" bar"))
		{
			return "smelting";
		}
		if (lower.equals("molten glass"))
		{
			return "making molten glass";
		}
		if (lower.endsWith(" dagger"))
		{
			return "smithing daggers";
		}
		if (lower.endsWith(" chaps") || lower.endsWith(" vambraces"))
		{
			return "chaps and vambraces";
		}
		return lower.endsWith("s") ? lower : lower + "s";
	}

	private static double bestXp(List<Recipe> recipes, int level)
	{
		double best = 0;
		for (Recipe r : recipes)
		{
			if (r.getLevel() <= level)
			{
				best = Math.max(best, r.getXp());
			}
		}
		return best;
	}
}
