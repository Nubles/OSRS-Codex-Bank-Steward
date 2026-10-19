package com.codex.steward.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * What the player's other Codex plugins say they have plans for: the supply kits saved in Codex
 * Quartermaster and the goals pinned in Codex Navigator. Both are read, never written, from what
 * those plugins save in the RuneLite profile, and anything missing or unreadable is simply no plan.
 *
 * <p>Quartermaster's {@code kitsV1} key lists every item each kit counts. Older Quartermasters
 * save only {@code quarters}, which names pooled supplies like "FOOD"; those are looked up in a
 * copy of Quartermaster's own supply groups below.</p>
 */
public final class Plans
{
	/** Quartermaster's config group and keys. */
	public static final String QUARTERMASTER = "codexquartermaster";
	public static final String KITS_KEY = "kitsV1";
	public static final String QUARTERS_KEY = "quarters";
	/** Navigator's config group and key. */
	public static final String NAVIGATOR = "codexnavigator";
	public static final String GOALS_KEY = "goals";

	private static final Plans NONE = new Plans(Collections.emptyList(), Collections.emptyList());

	/** One kit and every item it counts. */
	public static final class Kit
	{
		private final String name;
		private final Set<Integer> items;

		public Kit(String name, Set<Integer> items)
		{
			this.name = name;
			this.items = Collections.unmodifiableSet(new LinkedHashSet<>(items));
		}

		public String getName()
		{
			return name;
		}

		public Set<Integer> getItems()
		{
			return items;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Kit && name.equals(((Kit) o).name) && items.equals(((Kit) o).items);
		}

		@Override
		public int hashCode()
		{
			return name.hashCode() * 31 + items.hashCode();
		}
	}

	private final List<Kit> kits;
	private final List<String> goals;

	public Plans(List<Kit> kits, List<String> goals)
	{
		this.kits = Collections.unmodifiableList(new ArrayList<>(kits));
		this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
	}

	public static Plans none()
	{
		return NONE;
	}

	public List<Kit> getKits()
	{
		return kits;
	}

	/** Navigator goal keys in Navigator's order, such as {@code quest:Dragon Slayer II}. */
	public List<String> getGoals()
	{
		return goals;
	}

	public boolean isEmpty()
	{
		return kits.isEmpty() && goals.isEmpty();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Plans && kits.equals(((Plans) o).kits) && goals.equals(((Plans) o).goals);
	}

	@Override
	public int hashCode()
	{
		return kits.hashCode() * 31 + goals.hashCode();
	}

	/** Every kit that takes each item, in kit order. */
	public Map<Integer, List<String>> kitsByItem()
	{
		Map<Integer, List<String>> by = new HashMap<>();
		for (Kit kit : kits)
		{
			for (int id : kit.getItems())
			{
				List<String> names = by.computeIfAbsent(id, k -> new ArrayList<>());
				if (!names.contains(kit.getName()))
				{
					names.add(kit.getName());
				}
			}
		}
		return by;
	}

	/** Each task some pinned goal covers, with the goals that cover it, in Navigator's order. */
	public Map<Integer, List<String>> goalsByTask(Catalog catalog)
	{
		Map<Integer, List<String>> by = new HashMap<>();
		int size = catalog.getTasks().size();
		for (String key : goals)
		{
			String name = goalName(key);
			for (int task : catalog.goalTasks(key))
			{
				if (task >= 0 && task < size)
				{
					List<String> names = by.computeIfAbsent(task, k -> new ArrayList<>());
					if (!names.contains(name))
					{
						names.add(name);
					}
				}
			}
		}
		return by;
	}

	/**
	 * The tasks a goal of its own covers, leaving out the quest cape. The cape covers every quest,
	 * and holding everything every quest so much as mentions would bury the Needed page.
	 */
	public Set<Integer> pinnedTasks(Catalog catalog)
	{
		Set<Integer> out = new HashSet<>();
		int size = catalog.getTasks().size();
		for (String key : goals)
		{
			if (key.startsWith("cape:"))
			{
				continue;
			}
			for (int task : catalog.goalTasks(key))
			{
				if (task >= 0 && task < size)
				{
					out.add(task);
				}
			}
		}
		return out;
	}

	/** "Dragon Slayer II" for {@code quest:Dragon Slayer II}; "Quest cape" for the cape goal. */
	public static String goalName(String key)
	{
		if (key.startsWith("cape:"))
		{
			return "Quest cape";
		}
		int split = key.indexOf(':');
		return split < 0 ? key : key.substring(split + 1).trim();
	}

	/**
	 * Reads Navigator's pinned goals: a JSON list of keys. Keys of a kind the Steward does not
	 * know are kept; they cover no tasks.
	 */
	public static List<String> readGoals(Gson gson, String json)
	{
		List<String> out = new ArrayList<>();
		JsonElement root = parse(gson, json);
		if (root == null || !root.isJsonArray())
		{
			return out;
		}
		for (JsonElement e : root.getAsJsonArray())
		{
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString())
			{
				String key = e.getAsString().trim();
				if (key.indexOf(':') > 0 && !out.contains(key))
				{
					out.add(key);
				}
			}
		}
		return out;
	}

	/**
	 * Reads Quartermaster's kits: every saved kit, and the chosen kit when it is a preset, since
	 * the player is using it. Prefers {@code kitsV1}, falling back on the older {@code quarters}.
	 *
	 * <p>Older Quartermasters always named an active kit in {@code kitsV1}: with none chosen and
	 * none saved, the first preset. That preset is not the player's choice, so a preset only counts
	 * when {@code quarters} says it was chosen; {@code kitsV1}'s word is taken only when
	 * {@code quarters} cannot be read. Newer ones leave {@code active} out until a kit is chosen.</p>
	 */
	public static List<Kit> readKits(Gson gson, String kitsV1, String quarters)
	{
		JsonElement v1 = parse(gson, kitsV1);
		JsonElement old = parse(gson, quarters);
		boolean oldOk = old != null && old.isJsonObject();
		if (v1 != null && v1.isJsonObject())
		{
			JsonObject root = v1.getAsJsonObject();
			String chosen = oldOk ? string(old.getAsJsonObject(), "active") : string(root, "active");
			return readV1(root, chosen);
		}
		if (oldOk)
		{
			return readQuarters(old.getAsJsonObject());
		}
		return new ArrayList<>();
	}

	/** @param active the preset the player chose, or null */
	private static List<Kit> readV1(JsonObject root, String active)
	{
		List<Kit> out = new ArrayList<>();
		for (JsonObject kit : objects(root, "kits"))
		{
			String id = string(kit, "id");
			String name = string(kit, "name");
			boolean preset = kit.has("preset") && kit.get("preset").isJsonPrimitive() && kit.get("preset").getAsBoolean();
			if (name == null || preset && (id == null || !id.equals(active)))
			{
				continue;
			}
			Set<Integer> items = new LinkedHashSet<>();
			for (JsonObject need : objects(kit, "needs"))
			{
				JsonElement map = need.get("items");
				if (map != null && map.isJsonObject())
				{
					for (String key : map.getAsJsonObject().keySet())
					{
						Integer itemId = integer(key);
						if (itemId != null && itemId > 0)
						{
							items.add(itemId);
						}
					}
				}
				else
				{
					items.addAll(supplyItems(string(need, "supply")));
				}
			}
			add(out, name, items);
		}
		return out;
	}

	private static List<Kit> readQuarters(JsonObject root)
	{
		List<Kit> out = new ArrayList<>();
		for (JsonObject kit : objects(root, "kits"))
		{
			String name = string(kit, "name");
			if (name == null)
			{
				continue;
			}
			Set<Integer> items = new LinkedHashSet<>();
			for (JsonObject need : objects(kit, "needs"))
			{
				items.addAll(supplyItems(string(need, "supply")));
			}
			add(out, name, items);
		}
		return out;
	}

	private static void add(List<Kit> out, String name, Set<Integer> items)
	{
		if (!items.isEmpty())
		{
			out.add(new Kit(name.length() > 40 ? name.substring(0, 40) : name, items));
		}
	}

	/** The items one of Quartermaster's supply keys counts: "item:ID" or a pooled group. */
	static List<Integer> supplyItems(String supply)
	{
		List<Integer> out = new ArrayList<>();
		if (supply == null)
		{
			return out;
		}
		if (supply.startsWith("item:"))
		{
			Integer id = integer(supply.substring(5));
			if (id != null && id > 0)
			{
				out.add(id);
			}
			return out;
		}
		int[] group = GROUPS.get(supply);
		if (group != null)
		{
			for (int id : group)
			{
				out.add(id);
			}
		}
		return out;
	}

	private static JsonElement parse(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}

	private static List<JsonObject> objects(JsonObject parent, String key)
	{
		List<JsonObject> out = new ArrayList<>();
		JsonElement e = parent.get(key);
		if (e != null && e.isJsonArray())
		{
			JsonArray array = e.getAsJsonArray();
			for (JsonElement item : array)
			{
				if (item != null && item.isJsonObject())
				{
					out.add(item.getAsJsonObject());
				}
			}
		}
		return out;
	}

	private static String string(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static Integer integer(String s)
	{
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Quartermaster's pooled supplies as it saved them before {@code kitsV1}, copied from its
	 * Supplies class. A group added later is read from {@code kitsV1} instead.
	 */
	private static final Map<String, int[]> GROUPS = new LinkedHashMap<>();

	static
	{
		GROUPS.put("FOOD", new int[]{ItemID.MANTARAY, ItemID.ANGLERFISH, ItemID.DARK_CRAB, ItemID.POTATO_TUNA_SWEETCORN,
			ItemID.SEATURTLE, ItemID.SHARK, ItemID.TBWT_COOKED_KARAMBWAN, ItemID.MONKFISH, ItemID.POTATO_CHEESE,
			ItemID.SWORDFISH, ItemID.BASS, ItemID.CAKE, ItemID.LOBSTER, ItemID.JUG_WINE, ItemID.PARTIAL_CAKE, ItemID.CAKE_SLICE});
		GROUPS.put("BREW", new int[]{ItemID._4DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFSARADOMIN,
			ItemID._2DOSEPOTIONOFSARADOMIN, ItemID._1DOSEPOTIONOFSARADOMIN});
		GROUPS.put("PRAYER", new int[]{ItemID._4DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE, ItemID._2DOSEPRAYERRESTORE,
			ItemID._1DOSEPRAYERRESTORE, ItemID._4DOSE2RESTORE, ItemID._3DOSE2RESTORE, ItemID._2DOSE2RESTORE, ItemID._1DOSE2RESTORE,
			ItemID.SANFEW_SALVE_4_DOSE, ItemID.SANFEW_SALVE_3_DOSE, ItemID.SANFEW_SALVE_2_DOSE, ItemID.SANFEW_SALVE_1_DOSE});
		GROUPS.put("RANGING", new int[]{ItemID._4DOSEDIVINERANGE, ItemID._3DOSEDIVINERANGE, ItemID._2DOSEDIVINERANGE,
			ItemID._1DOSEDIVINERANGE, ItemID._4DOSERANGERSPOTION, ItemID._3DOSERANGERSPOTION, ItemID._2DOSERANGERSPOTION,
			ItemID._1DOSERANGERSPOTION, ItemID._4DOSEDIVINEBASTION, ItemID._3DOSEDIVINEBASTION, ItemID._2DOSEDIVINEBASTION,
			ItemID._1DOSEDIVINEBASTION, ItemID._4DOSEBASTION, ItemID._3DOSEBASTION, ItemID._2DOSEBASTION, ItemID._1DOSEBASTION});
		GROUPS.put("MAGIC", new int[]{ItemID._4DOSEDIVINEMAGIC, ItemID._3DOSEDIVINEMAGIC, ItemID._2DOSEDIVINEMAGIC,
			ItemID._1DOSEDIVINEMAGIC, ItemID._4DOSE1MAGIC, ItemID._3DOSE1MAGIC, ItemID._2DOSE1MAGIC, ItemID._1DOSE1MAGIC});
		GROUPS.put("COMBAT", new int[]{ItemID._4DOSEDIVINECOMBAT, ItemID._3DOSEDIVINECOMBAT, ItemID._2DOSEDIVINECOMBAT,
			ItemID._1DOSEDIVINECOMBAT, ItemID._4DOSE2COMBAT, ItemID._3DOSE2COMBAT, ItemID._2DOSE2COMBAT, ItemID._1DOSE2COMBAT});
		int[] superAntifire = {ItemID._4DOSE4ANTIDRAGON, ItemID._3DOSE4ANTIDRAGON, ItemID._2DOSE4ANTIDRAGON,
			ItemID._1DOSE4ANTIDRAGON, ItemID._4DOSE3ANTIDRAGON, ItemID._3DOSE3ANTIDRAGON, ItemID._2DOSE3ANTIDRAGON,
			ItemID._1DOSE3ANTIDRAGON};
		GROUPS.put("SUPER_ANTIFIRE", superAntifire);
		int[] antifire = new int[superAntifire.length + 8];
		System.arraycopy(superAntifire, 0, antifire, 0, superAntifire.length);
		System.arraycopy(new int[]{ItemID._4DOSE2ANTIDRAGON, ItemID._3DOSE2ANTIDRAGON, ItemID._2DOSE2ANTIDRAGON,
			ItemID._1DOSE2ANTIDRAGON, ItemID._4DOSE1ANTIDRAGON, ItemID._3DOSE1ANTIDRAGON, ItemID._2DOSE1ANTIDRAGON,
			ItemID._1DOSE1ANTIDRAGON}, 0, antifire, superAntifire.length, 8);
		GROUPS.put("ANTIFIRE", antifire);
		GROUPS.put("ANTIVENOM", new int[]{ItemID.ANTIVENOM_4, ItemID.ANTIVENOM_3, ItemID.ANTIVENOM_2, ItemID.ANTIVENOM_1,
			ItemID.ANTIVENOM4, ItemID.ANTIVENOM3, ItemID.ANTIVENOM2, ItemID.ANTIVENOM1});
		GROUPS.put("STAMINA", new int[]{ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA});
		GROUPS.put("DRAGON_BOLTS", new int[]{ItemID.DRAGON_BOLTS_ENCHANTED_RUBY, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND});
	}
}
