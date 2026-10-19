package com.codex.steward.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * What an item container holds, as the Steward counts it: real items only, by canonical id.
 * Empty slots, placeholders and bank fillers are not items. A filler only holds a slot open, and
 * left in it would be called untradeable dust and handed to the other Codex plugins as part of the
 * bank.
 */
public final class BankItems
{
	/** The bank filler, which holds an empty slot in place. */
	public static final int BANK_FILLER = 20594;

	private BankItems()
	{
	}

	/**
	 * @param ids each slot's item id, -1 for an empty slot
	 * @param quantities each slot's quantity
	 * @param placeholder whether an id is a bank placeholder
	 * @param canonical folds noted and placeholder forms into the item
	 */
	public static Map<Integer, Long> count(int[] ids, int[] quantities, IntPredicate placeholder, IntUnaryOperator canonical)
	{
		Map<Integer, Long> items = new HashMap<>();
		for (int i = 0; i < ids.length && i < quantities.length; i++)
		{
			int id = ids[i];
			if (id < 0 || quantities[i] <= 0 || id == BANK_FILLER || placeholder.test(id))
			{
				continue;
			}
			items.merge(canonical.applyAsInt(id), (long) quantities[i], Long::sum);
		}
		return items;
	}
}
