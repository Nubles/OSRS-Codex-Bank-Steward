package com.codex.steward.core;

import java.util.Objects;

/**
 * A plan the player has for an item, which keeps it off the list: a supply kit saved in Codex
 * Quartermaster, a goal pinned in Codex Navigator, or a clue STASH unit it fills.
 */
public final class Claim
{
	public enum Kind
	{
		/** A Codex Quartermaster kit takes it on every trip. */
		KIT,
		/** A quest or diary on the way to a pinned Codex Navigator goal needs it. */
		GOAL,
		/** An emote clue's STASH unit takes it. */
		STASH
	}

	private final Kind kind;
	private final String name;

	public Claim(Kind kind, String name)
	{
		this.kind = kind;
		this.name = name;
	}

	public Kind getKind()
	{
		return kind;
	}

	/** The kit's name, the goal's name, or the clue tiers, such as "medium, hard". */
	public String getName()
	{
		return name;
	}

	/** A few words for a row, like "Kit: Vorkath". */
	public String shortText()
	{
		switch (kind)
		{
			case KIT:
				return "Kit: " + name;
			case GOAL:
				return "Goal: " + name;
			default:
				return "STASH: " + name;
		}
	}

	/** A sentence for a tooltip, like "Kept for your kit Vorkath". */
	public String longText()
	{
		switch (kind)
		{
			case KIT:
				return "Kept for your kit " + name + " (Codex Quartermaster)";
			case GOAL:
				return "Kept for your goal " + name + " (Codex Navigator)";
			default:
				return "Clue STASH gear for " + name + " clues";
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Claim))
		{
			return false;
		}
		Claim other = (Claim) o;
		return kind == other.kind && name.equals(other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(kind, name);
	}

	@Override
	public String toString()
	{
		return shortText();
	}
}
