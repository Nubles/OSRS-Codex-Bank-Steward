package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MemoryTest
{
	@Test
	public void firstLookIsTheBaselineAndLaterChangesAreMoves()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(1, 5L);
		bank.put(2, 1L);
		assertTrue(memory.observeBank(bank, 10));
		assertEquals(-1, memory.lastMoved(1));
		assertEquals(4, memory.untouchedDays(1, 14));

		Map<Integer, Long> next = new HashMap<>(bank);
		next.put(1, 4L);
		next.remove(2);
		next.put(3, 1L);
		assertTrue(memory.observeBank(next, 12));
		assertEquals(12, memory.lastMoved(1));
		assertEquals(12, memory.lastMoved(2));
		assertEquals(12, memory.lastMoved(3));
		assertFalse(memory.observeBank(next, 12));

		memory.observeHeld(Collections.singletonList(7), 13);
		assertEquals(13, memory.lastMoved(7));

		// Only items in the bank need their moves remembered.
		memory.prune();
		assertEquals(-1, memory.lastMoved(7));
		assertEquals(-1, memory.lastMoved(2));
		assertEquals(12, memory.lastMoved(1));
		Map<Integer, Long> back = new HashMap<>(next);
		back.put(7, 1L);
		memory.observeBank(back, 20);
		assertEquals(20, memory.lastMoved(7));
	}
}
