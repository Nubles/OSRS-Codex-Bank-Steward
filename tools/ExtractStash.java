package net.runelite.client.plugins.cluescrolls.clues;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.plugins.cluescrolls.clues.item.AllRequirementsCollection;
import net.runelite.client.plugins.cluescrolls.clues.item.AnyRequirementCollection;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

/**
 * Prints every item that can fill a clue STASH unit, with the clue tiers that use it, as JSON
 * lines of {@code [itemId, tierMask]}: beginner 1, easy 2, medium 4, hard 8, elite 16, master 32. It sits in
 * RuneLite's own clue package so it can read the emote clue list, and is run against a RuneLite
 * build; tools/README.md has the steps. Not part of the plugin.
 *
 * <p>With the argument {@code units} it prints each STASH unit instead, one per emote clue, as
 * {@code [objectId, tierMask, "Place", [ids of part 1], [ids of part 2], ...]}: every part of
 * the set the unit holds, each with the items that can fill it. A set item that is "all of"
 * several items becomes one part per item; equipment slots that must stay empty are left out, and
 * so is a unit whose set is a choice between whole sets (any Barrows set, say).</p>
 */
public class ExtractStash
{
	private static final String[] TIERS = {"BEGINNER", "EASY", "MEDIUM", "HARD", "ELITE", "MASTER"};
	private static final int MAX_ITEM = 40_000;

	public static void main(String[] args) throws Exception
	{
		Map<Integer, String> objectNames = new TreeMap<>();
		for (Field f : ObjectID.class.getFields())
		{
			if (f.getType() == int.class && f.getName().startsWith("HH_"))
			{
				f.setAccessible(true);
				objectNames.put(f.getInt(null), f.getName());
			}
		}
		if (args.length > 0 && "units".equals(args[0]))
		{
			units(objectNames);
			return;
		}
		Map<Integer, Integer> masks = new TreeMap<>();
		for (EmoteClue clue : EmoteClue.CLUES)
		{
			STASHUnit unit = clue.getStashUnit();
			if (unit == null)
			{
				continue;
			}
			String name = objectNames.getOrDefault(unit.getObjectId(), "");
			int mask = 0;
			for (int t = 0; t < TIERS.length; t++)
			{
				if (name.startsWith("HH_" + TIERS[t]))
				{
					mask = 1 << t;
				}
			}
			if (mask == 0)
			{
				System.err.println("No tier for " + unit + " " + name);
				continue;
			}
			for (ItemRequirement req : clue.getItemRequirements())
			{
				for (int id = 0; id < MAX_ITEM; id++)
				{
					if (req.fulfilledBy(id))
					{
						masks.merge(id, mask, (a, b) -> a | b);
					}
				}
			}
		}
		StringBuilder out = new StringBuilder("[");
		masks.forEach((id, mask) -> out.append(out.length() > 1 ? "," : "").append("[").append(id).append(",").append(mask).append("]"));
		System.out.println(out.append("]"));
	}

	private static int tier(Map<Integer, String> objectNames, int objectId)
	{
		String name = objectNames.getOrDefault(objectId, "");
		int mask = 0;
		for (int t = 0; t < TIERS.length; t++)
		{
			if (name.startsWith("HH_" + TIERS[t]))
			{
				mask = 1 << t;
			}
		}
		return mask;
	}

	private static void units(Map<Integer, String> objectNames) throws Exception
	{
		StringBuilder out = new StringBuilder("[");
		for (EmoteClue clue : EmoteClue.CLUES)
		{
			STASHUnit unit = clue.getStashUnit();
			if (unit == null)
			{
				continue;
			}
			int mask = tier(objectNames, unit.getObjectId());
			if (mask == 0)
			{
				System.err.println("No tier for " + unit);
				continue;
			}
			List<ItemRequirement> parts = new ArrayList<>();
			boolean plain = true;
			for (ItemRequirement req : clue.getItemRequirements())
			{
				plain &= flatten(req, parts);
			}
			if (!plain)
			{
				// "Any one of these whole sets" cannot be told as parts; leave the unit out.
				System.err.println("Left out " + unit + ": a choice of whole sets");
				continue;
			}
			StringBuilder row = new StringBuilder();
			row.append("[").append(unit.getObjectId()).append(",").append(mask).append(",\"")
				.append(place(unit)).append("\"");
			for (ItemRequirement part : parts)
			{
				List<Integer> ids = new ArrayList<>();
				for (int id = 0; id < MAX_ITEM; id++)
				{
					if (part.fulfilledBy(id))
					{
						ids.add(id);
					}
				}
				if (ids.isEmpty())
				{
					continue;
				}
				row.append(",").append(ids.toString().replace(" ", ""));
			}
			out.append(out.length() > 1 ? ",\n" : "").append(row).append("]");
		}
		System.out.println(out.append("]"));
	}

	private static final java.util.Set<String> SMALL = new java.util.HashSet<>(java.util.Arrays.asList(
		"a", "an", "the", "in", "of", "to", "by", "on", "at", "and", "off", "from"));

	/** The unit's place from its RuneLite name: NEAR_A_SHED_IN_LUMBRIDGE_SWAMP is "Near a Shed in Lumbridge Swamp". */
	private static String place(STASHUnit unit)
	{
		StringBuilder out = new StringBuilder();
		for (String word : unit.name().replaceAll("^_+", "").toLowerCase().split("_"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			boolean small = out.length() > 0 && SMALL.contains(word);
			out.append(out.length() > 0 ? " " : "").append(small ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		String place = out.toString().replaceAll(" \\d+$", "");
		for (String[] fix : POSSESSIVES)
		{
			place = place.replace(fix[0], fix[1]);
		}
		return place;
	}

	/** Enum names drop apostrophes; these put them back. */
	private static final String[][] POSSESSIVES = {
		{"Iorwerths", "Iorwerth's"}, {"Emirs", "Emir's"}, {"Hicktons", "Hickton's"}, {"Harrys", "Harry's"},
		{"Daves", "Dave's"}, {"Tsutsaroths", "Tsutsaroth's"}, {"Auburys", "Aubury's"}, {"Herquins", "Herquin's"},
		{"Dragons Lair", "Dragon's Lair"}, {"Noterazzos", "Noterazzo's"}, {"Mudknuckles", "Mudknuckles'"},
		{"Operators", "Operator's"}, {"Lathas", "Lathas'"}, {"Bob Axes", "Bob's Axes"}, {"Warriors Guild", "Warriors' Guild"},
		{"Legends Guild", "Legends' Guild"}, {"Seers Village", "Seers' Village"}, {"Wizards Tower", "Wizards' Tower"}};

	/**
	 * An "all of" collection is one part per item; anything else is one part.
	 *
	 * @return false when a choice is between whole sets, which parts cannot express
	 */
	private static boolean flatten(ItemRequirement req, List<ItemRequirement> parts) throws Exception
	{
		if (req instanceof AllRequirementsCollection)
		{
			Field f = AllRequirementsCollection.class.getDeclaredField("requirements");
			f.setAccessible(true);
			for (ItemRequirement r : (ItemRequirement[]) f.get(req))
			{
				if (!flatten(r, parts))
				{
					return false;
				}
			}
			return true;
		}
		if (req instanceof AnyRequirementCollection)
		{
			Field f = AnyRequirementCollection.class.getDeclaredField("requirements");
			f.setAccessible(true);
			for (ItemRequirement r : (ItemRequirement[]) f.get(req))
			{
				if (r instanceof AllRequirementsCollection)
				{
					return false;
				}
			}
		}
		parts.add(req);
		return true;
	}
}
