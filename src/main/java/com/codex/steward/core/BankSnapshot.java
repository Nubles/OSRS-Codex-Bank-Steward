package com.codex.steward.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The whole bank as last seen, published for the other Codex plugins under the RS profile key
 * {@link #KEY} of the Steward's config group ({@code codexbanksteward}). Codex Gear Roadmap's
 * saved bank holds only the gear it knows; this one holds everything, so a reader can find a
 * fresh Pharaoh's sceptre or a stack of void seals without seeing the bank itself.
 *
 * <p>The shape is a contract and only grows; a breaking change would get a new key:</p>
 * <pre>
 * {"version":1,"savedAt":1760000000000,"items":{"995":1234567,"26950":1},"truncated":false}
 * </pre>
 * <ul>
 * <li>{@code items}: every item in the bank by its canonical item id (placeholders and noted
 *     forms folded into the item, as {@code ItemManager.canonicalize} does), with its quantity.
 *     Potion storage and the inventory are not included. Keys are in ascending id order.</li>
 * <li>{@code savedAt}: when the bank was last seen, in epoch milliseconds.</li>
 * <li>{@code truncated}: true only if the bank held more than {@link #MAX_ITEMS} kinds of
 *     item and the highest ids were left out. A real bank never comes near it.</li>
 * </ul>
 */
public final class BankSnapshot
{
	public static final String KEY = "bankV1";
	public static final int VERSION = 1;
	/** The most kinds of item kept, so a corrupt container can never make the save unbounded. */
	public static final int MAX_ITEMS = 2000;

	private int version = VERSION;
	private long savedAt;
	private Map<Integer, Long> items = new TreeMap<>();
	private boolean truncated;

	public BankSnapshot()
	{
	}

	/** The bank as seen at {@code savedAt}; empty or unknown quantities are left out. */
	public static BankSnapshot of(Map<Integer, Long> bank, long savedAt)
	{
		BankSnapshot s = new BankSnapshot();
		s.savedAt = savedAt;
		for (Map.Entry<Integer, Long> e : new TreeMap<>(bank).entrySet())
		{
			Long qty = e.getValue();
			if (e.getKey() == null || e.getKey() <= 0 || qty == null || qty <= 0)
			{
				continue;
			}
			if (s.items.size() >= MAX_ITEMS)
			{
				s.truncated = true;
				break;
			}
			s.items.put(e.getKey(), qty);
		}
		return s;
	}

	/** Reads a published snapshot; null when there is none or it cannot be read. */
	public static BankSnapshot read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject() || root.getAsJsonObject().get("items") == null
				|| !root.getAsJsonObject().get("items").isJsonObject())
			{
				return null;
			}
			BankSnapshot s = gson.fromJson(root, BankSnapshot.class);
			if (s == null || s.items == null)
			{
				return null;
			}
			s.items.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
			return s;
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}

	public int getVersion()
	{
		return version;
	}

	public long getSavedAt()
	{
		return savedAt;
	}

	public Map<Integer, Long> getItems()
	{
		return Collections.unmodifiableMap(items);
	}

	public boolean isTruncated()
	{
		return truncated;
	}

	/** Same items and quantities, whenever each was seen. */
	public boolean sameItems(BankSnapshot other)
	{
		return other != null && items.equals(other.items) && truncated == other.truncated;
	}
}
