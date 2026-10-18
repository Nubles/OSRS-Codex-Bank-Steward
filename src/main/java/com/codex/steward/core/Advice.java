package com.codex.steward.core;

import java.util.Collections;
import java.util.List;

/** One item in the bank and what the Steward makes of it. */
public final class Advice
{
	/** Why an item is on the list. */
	public enum Verdict
	{
		/** Every quest and diary that needs it is done. */
		SPENT,
		/** Untouched for longer than the player's dust threshold. */
		DUST,
		/** Still needed by a quest or diary that is not done. */
		NEEDED,
		/** The player chose to keep it. */
		KEPT
	}

	private final int itemId;
	private final long quantity;
	private final long price;
	private final boolean tradeable;
	private final Verdict verdict;
	private final List<String> tasks;
	private final long untouchedDays;

	public Advice(int itemId, long quantity, long price, boolean tradeable, Verdict verdict, List<String> tasks, long untouchedDays)
	{
		this.itemId = itemId;
		this.quantity = quantity;
		this.price = price;
		this.tradeable = tradeable;
		this.verdict = verdict;
		this.tasks = tasks == null ? Collections.emptyList() : Collections.unmodifiableList(tasks);
		this.untouchedDays = untouchedDays;
	}

	public int getItemId()
	{
		return itemId;
	}

	public long getQuantity()
	{
		return quantity;
	}

	/** The Grand Exchange price of one, or 0 when it cannot be traded. */
	public long getPrice()
	{
		return price;
	}

	/** What selling the whole stack would bring. */
	public long getValue()
	{
		return tradeable ? price * quantity : 0;
	}

	public boolean isTradeable()
	{
		return tradeable;
	}

	public Verdict getVerdict()
	{
		return verdict;
	}

	/** For SPENT, the finished tasks it served; for NEEDED, the unfinished tasks that want it. */
	public List<String> getTasks()
	{
		return tasks;
	}

	public long getUntouchedDays()
	{
		return untouchedDays;
	}
}
