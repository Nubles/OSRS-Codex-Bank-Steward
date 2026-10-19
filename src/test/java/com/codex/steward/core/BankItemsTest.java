package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.util.Map;
import org.junit.Test;

public class BankItemsTest
{
	private static final int SHARK = 385;
	private static final int SHARK_NOTED = 386;
	private static final int SHARK_PLACEHOLDER = 17000;

	@Test
	public void fillersPlaceholdersAndEmptySlotsAreNotItems()
	{
		int[] ids = {SHARK, -1, BankItems.BANK_FILLER, BankItems.BANK_FILLER, SHARK_PLACEHOLDER, 995};
		int[] qty = {240, 0, 1, 1, 0, 1_000};
		Map<Integer, Long> items = BankItems.count(ids, qty, id -> id == SHARK_PLACEHOLDER, id -> id);
		assertEquals(2, items.size());
		assertEquals(240L, (long) items.get(SHARK));
		assertFalse("a bank filler only holds a slot", items.containsKey(BankItems.BANK_FILLER));
	}

	@Test
	public void notedFormsFoldIntoTheItem()
	{
		Map<Integer, Long> items = BankItems.count(new int[]{SHARK, SHARK_NOTED}, new int[]{4, 6}, id -> false,
			id -> id == SHARK_NOTED ? SHARK : id);
		assertEquals(10L, (long) items.get(SHARK));
	}
}
