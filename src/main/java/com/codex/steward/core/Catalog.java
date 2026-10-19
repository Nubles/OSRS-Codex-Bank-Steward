package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which quests and diaries need which items, from the bundled steward-data.json.
 *
 * <p>Each need is one requirement of one task: a quantity and the items that satisfy it, best
 * first. A need with several items is an "any of" choice, met by keeping one of them. A firm need
 * is something the task asks you to bring; a soft one is anything else it names (items picked up
 * along the way, recommended items, items sharing a name with one it asks for). Both hold an item
 * back while the task is unfinished, but only a firm need can make an item spent.</p>
 */
public final class Catalog
{
	/** An "any of" need with more choices than this is a broad one, like "any food". */
	public static final int BROAD = 4;

	/** The file as stored; Gson fills it in. */
	public static final class Data
	{
		String source;
		List<Task> tasks;
		List<int[]> needs;
		List<int[]> soft;
		int[] general;
		int[] lasting;
		/** Clue STASH gear: item and a mask of the clue tiers whose STASH units take it. */
		List<int[]> stash;
		/** Each Codex Navigator goal key to the tasks it covers, its own and every quest before it. */
		Map<String, int[]> goals;
	}

	/** Clue tiers, in the order of the bits of a STASH mask. */
	public static final String[] CLUE_TIERS = {"beginner", "easy", "medium", "hard", "elite", "master"};

	/** One requirement: a task, a quantity and the items that meet it. */
	public static final class Need
	{
		private final int task;
		private final int quantity;
		private final int[] items;
		private final boolean firm;

		Need(int task, int quantity, int[] items, boolean firm)
		{
			this.task = task;
			this.quantity = quantity;
			this.items = items;
			this.firm = firm;
		}

		/** Asked for by the task, rather than only named by it. */
		public boolean isFirm()
		{
			return firm;
		}

		public int getTask()
		{
			return task;
		}

		public int getQuantity()
		{
			return quantity;
		}

		public int[] getItems()
		{
			return items;
		}
	}

	private final List<Task> tasks;
	private final List<Need> needs = new ArrayList<>();
	private final Map<Integer, List<Need>> byItem = new HashMap<>();
	private final Set<Integer> general = new HashSet<>();
	private final Set<Integer> lasting = new HashSet<>();
	private final Map<Integer, Integer> stash = new HashMap<>();
	private final Map<String, int[]> goals = new HashMap<>();

	public Catalog(Data data)
	{
		tasks = data.tasks == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(data.tasks));
		read(data.needs, true);
		read(data.soft, false);
		if (data.general != null)
		{
			for (int id : data.general)
			{
				general.add(id);
			}
		}
		if (data.lasting != null)
		{
			for (int id : data.lasting)
			{
				lasting.add(id);
			}
		}
		if (data.stash != null)
		{
			for (int[] pair : data.stash)
			{
				if (pair != null && pair.length == 2 && pair[1] != 0)
				{
					stash.merge(pair[0], pair[1], (a, b) -> a | b);
				}
			}
		}
		if (data.goals != null)
		{
			data.goals.forEach((key, list) ->
			{
				if (key != null && list != null)
				{
					goals.put(key, list.clone());
				}
			});
		}
	}

	private void read(List<int[]> list, boolean firm)
	{
		if (list == null)
		{
			return;
		}
		for (int[] raw : list)
		{
			if (raw == null || raw.length < 3 || raw[0] < 0 || raw[0] >= tasks.size())
			{
				continue;
			}
			int[] items = new int[raw.length - 2];
			System.arraycopy(raw, 2, items, 0, items.length);
			Need need = new Need(raw[0], Math.max(1, raw[1]), items, firm);
			needs.add(need);
			for (int item : items)
			{
				byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(need);
			}
		}
	}

	public List<Task> getTasks()
	{
		return tasks;
	}

	public List<Need> getNeeds()
	{
		return Collections.unmodifiableList(needs);
	}

	/** Every need, firm or soft, the item can meet; empty when no quest or diary names it. */
	public List<Need> needsOf(int itemId)
	{
		List<Need> list = byItem.get(itemId);
		return list == null ? Collections.emptyList() : list;
	}

	/** A tool, rune, food or other good that stays useful whatever is done. */
	public boolean isGeneral(int itemId)
	{
		return general.contains(itemId);
	}

	/** The clue tiers whose STASH units take this item, as a mask of {@link #CLUE_TIERS}; 0 for none. */
	public int stashTiers(int itemId)
	{
		return stash.getOrDefault(itemId, 0);
	}

	/**
	 * The tasks a Codex Navigator goal covers, such as {@code quest:Dragon Slayer II}: the quest or
	 * diary itself and every quest it needs first. Navigator's quest cape goal covers every quest.
	 * Empty for a goal the Steward does not know.
	 */
	public int[] goalTasks(String key)
	{
		if (key != null && key.startsWith("cape:"))
		{
			List<Integer> all = new ArrayList<>();
			for (int i = 0; i < tasks.size(); i++)
			{
				if (Task.QUEST.equals(tasks.get(i).getKind()))
				{
					all.add(i);
				}
			}
			return all.stream().mapToInt(Integer::intValue).toArray();
		}
		int[] list = key == null ? null : goals.get(key);
		return list == null ? new int[0] : list;
	}

	/** A reward or access item with a use long after the task that names it. */
	public boolean isLasting(int itemId)
	{
		return lasting.contains(itemId);
	}
}
