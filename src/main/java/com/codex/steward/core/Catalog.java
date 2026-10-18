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
 * first. A need with several items is an "any of" choice, met by keeping one of them.</p>
 */
public final class Catalog
{
	/** The file as stored; Gson fills it in. */
	public static final class Data
	{
		String source;
		List<Task> tasks;
		List<int[]> needs;
		int[] general;
		int[] lasting;
	}

	/** One requirement: a task, a quantity and the items that meet it. */
	public static final class Need
	{
		private final int task;
		private final int quantity;
		private final int[] items;

		Need(int task, int quantity, int[] items)
		{
			this.task = task;
			this.quantity = quantity;
			this.items = items;
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

	public Catalog(Data data)
	{
		tasks = data.tasks == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(data.tasks));
		if (data.needs != null)
		{
			for (int[] raw : data.needs)
			{
				if (raw.length < 3 || raw[0] < 0 || raw[0] >= tasks.size())
				{
					continue;
				}
				int[] items = new int[raw.length - 2];
				System.arraycopy(raw, 2, items, 0, items.length);
				Need need = new Need(raw[0], Math.max(1, raw[1]), items);
				needs.add(need);
				for (int item : items)
				{
					byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(need);
				}
			}
		}
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
	}

	public List<Task> getTasks()
	{
		return tasks;
	}

	public List<Need> getNeeds()
	{
		return Collections.unmodifiableList(needs);
	}

	/** Every need the item can meet, empty when no quest or diary names it. */
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

	/** A reward or access item with a use long after the task that names it. */
	public boolean isLasting(int itemId)
	{
		return lasting.contains(itemId);
	}
}
