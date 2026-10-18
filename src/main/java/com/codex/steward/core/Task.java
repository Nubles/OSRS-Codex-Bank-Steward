package com.codex.steward.core;

/**
 * A quest, miniquest or achievement diary tier that needs items. Quests are read through
 * RuneLite's Quest enum by name. A diary tier is done once its reward varbit reaches its value;
 * a miniquest RuneLite has no Quest for is done when its varbit holds exactly its final value,
 * as Quest Helper reads it.
 */
public final class Task
{
	public static final String QUEST = "quest";
	public static final String MINIQUEST = "miniquest";
	public static final String DIARY = "diary";

	private String name;
	private String kind;
	private String quest;
	private int varbit;
	private int value;

	public Task()
	{
	}

	public Task(String name, String kind, String quest, int varbit, int value)
	{
		this.name = name;
		this.kind = kind;
		this.quest = quest;
		this.varbit = varbit;
		this.value = value;
	}

	public String getName()
	{
		return name;
	}

	public String getKind()
	{
		return kind;
	}

	public boolean isDiary()
	{
		return DIARY.equals(kind);
	}

	/** Read from a varbit rather than RuneLite's Quest enum. */
	public boolean usesVarbit()
	{
		return quest == null || quest.isEmpty();
	}

	/** Whether the task's varbit, read as {@code value}, means it is finished. */
	public boolean isFinishedAt(int varbitValue)
	{
		if (value <= 0)
		{
			return false;
		}
		return isDiary() ? varbitValue >= value : varbitValue == value;
	}

	/** The name of the net.runelite.api.Quest constant, or null when read from a varbit. */
	public String getQuest()
	{
		return quest;
	}

	public int getVarbit()
	{
		return varbit;
	}

	public int getValue()
	{
		return value;
	}

	/** "Quest", "Miniquest" or "Diary", for the panel. */
	public String kindLabel()
	{
		return isDiary() ? "Diary" : MINIQUEST.equals(kind) ? "Miniquest" : "Quest";
	}
}
