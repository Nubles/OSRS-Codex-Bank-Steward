package net.runelite.client.plugins.cluescrolls.clues;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;

/**
 * Prints every item that can fill a clue STASH unit, with the clue tiers that use it, as JSON
 * lines of {@code [itemId, tierMask]}: beginner 1, easy 2, medium 4, hard 8, elite 16, master 32. It sits in
 * RuneLite's own clue package so it can read the emote clue list, and is run against a RuneLite
 * build; tools/README.md has the steps. Not part of the plugin.
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
}
