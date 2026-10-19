package com.codex.steward.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A list the Steward can show in the bank itself, through RuneLite's Bank Tags plugin: the bank is
 * filtered to only the items on it, as a tag tab would be, so the player can withdraw the clear-out
 * without hunting for it. It is a view and nothing more: the player's own tags are never touched,
 * and no item is ever moved.
 *
 * <p>Each view answers to its tag name in the bank search too, so {@code tag:steward} shows the
 * clear-out without the panel. Immutable, so the client thread can read it while the plugin
 * hands a new one over.</p>
 */
public final class BankView
{
	/** The clear-out: everything done with, dusty or outclassed, or what a clear-out under way still has to clear. */
	public static final String CLEAR_OUT = "steward";
	/** Potions to decant and empty placeholders to release. */
	public static final String TIDY = "steward-tidy";

	public static final BankView EMPTY = new BankView(Collections.emptySet());
	/** The key under which Bank Tags remembers the tab open when the bank closed, to open it again next visit. */
	public static final String REMEMBERED_TAB_KEY = "tab";

	/** Why the bank could not be shown filtered, if it could not. */
	public enum Refusal
	{
		/** The bank window is not open. */
		BANK_CLOSED,
		/** RuneLite's Bank Tags plugin is turned off, so the bank cannot be filtered. */
		TAGS_OFF,
		/** The list is empty: there is nothing to show. */
		NOTHING
	}

	private final Set<Integer> ids;

	private BankView(Set<Integer> ids)
	{
		this.ids = Collections.unmodifiableSet(ids);
	}

	/**
	 * The clear-out as the bank should show it. While a clear-out is under way it is the items the
	 * clear-out set out to clear that are not gone yet, nor left off it since (kept, or needed
	 * again), since the list itself moves as items go; before one begins it is the whole plan.
	 *
	 * @param plan the clear-out plan, or null before there is one
	 * @param session the clear-out under way, or null
	 * @param cleared items of the session already gone
	 * @param leftOff items of the session no longer to clear
	 */
	public static BankView clearOut(ClearOut plan, Memory.Session session, Collection<Integer> cleared, Collection<Integer> leftOff)
	{
		Set<Integer> ids = new HashSet<>();
		if (session != null)
		{
			for (Memory.Planned p : session.getItems())
			{
				int id = p.getItemId();
				if ((cleared == null || !cleared.contains(id)) && (leftOff == null || !leftOff.contains(id)))
				{
					ids.add(id);
				}
			}
		}
		else if (plan != null)
		{
			for (ClearOut.Step step : plan.getSteps())
			{
				ids.add(step.getAdvice().getItemId());
			}
		}
		return ids.isEmpty() ? EMPTY : new BankView(ids);
	}

	/**
	 * Tidying as the bank should show it: every dose of each potion worth decanting, and the
	 * placeholders worth releasing.
	 *
	 * @param placeholders the placeholders' own item ids, as the bank holds them (not the items they wait for)
	 */
	public static BankView tidy(Collection<Integer> placeholders, List<Tidy.Decant> decants)
	{
		Set<Integer> ids = new HashSet<>(placeholders);
		for (Tidy.Decant d : decants)
		{
			ids.addAll(d.getHeld().keySet());
		}
		return ids.isEmpty() ? EMPTY : new BankView(ids);
	}

	/** Whether a bank tag is one of the Steward's views. */
	public static boolean isOurs(String tag)
	{
		return CLEAR_OUT.equals(tag) || TIDY.equals(tag);
	}

	/**
	 * Whether the bank's filtered view should be closed: one of the Steward's is open now, or Bank
	 * Tags remembers one to open on the next visit. Bank Tags forgets the open view as the bank
	 * closes but keeps its name for the next visit, and a client that quit without warning keeps
	 * it across a restart, so both are checked.
	 *
	 * @param active the tag open in the bank now, or null
	 * @param remembered the tag Bank Tags will open on the next visit, or null or empty
	 */
	public static boolean shouldClose(String active, String remembered)
	{
		return isOurs(active) || isOurs(remembered);
	}

	/**
	 * The tab Bank Tags should remember for the next visit once a Steward view is closed: the tab the
	 * player had open before pressing Show in bank, so a look at the clear-out does not cost them
	 * their own remembered tab. Bank Tags itself would forget it, since opening a view replaces it.
	 *
	 * @param before the tag open when Show in bank was pressed, or null for the main bank
	 * @return the tag to remember, or empty for none
	 */
	public static String tabAfterClose(String before)
	{
		return before == null || isOurs(before) ? "" : before;
	}

	/**
	 * Whether a bank item is on the list.
	 *
	 * @param itemId the id as the bank holds it
	 * @param canonical the same item with noted and placeholder forms folded in, as the Steward keeps its bank
	 */
	public boolean contains(int itemId, int canonical)
	{
		return ids.contains(itemId) || ids.contains(canonical);
	}

	public boolean isEmpty()
	{
		return ids.isEmpty();
	}

	public int size()
	{
		return ids.size();
	}

	/**
	 * Whether the bank can be shown filtered to this list now, and if not, why.
	 *
	 * @param bankOpen the bank window is open
	 * @param tagsOn RuneLite's Bank Tags plugin is on
	 * @return null when it can be shown
	 */
	public Refusal refusal(boolean bankOpen, boolean tagsOn)
	{
		if (!tagsOn)
		{
			return Refusal.TAGS_OFF;
		}
		if (isEmpty())
		{
			return Refusal.NOTHING;
		}
		return bankOpen ? null : Refusal.BANK_CLOSED;
	}

	/** The line the chat box says when the bank could not be shown filtered. */
	public static String explain(Refusal refusal, String tag)
	{
		switch (refusal)
		{
			case TAGS_OFF:
				return "turn on RuneLite's Bank Tags plugin to show the list in your bank.";
			case NOTHING:
				return "there is nothing on that list to show.";
			default:
				return "open your bank first, then press Show in bank. Searching tag:" + tag + " in the bank shows it too.";
		}
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof BankView && ((BankView) o).ids.equals(ids);
	}

	@Override
	public int hashCode()
	{
		return ids.hashCode();
	}
}
