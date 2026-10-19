package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class StrataTest
{
	private static Map<Integer, Long> bank(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void layersByLastMoveAndTheOldestAtTheBottom()
	{
		Memory memory = new Memory();
		long start = 10_000;
		memory.observeBank(bank(385, 100, 2434, 10, 4151, 1, 1119, 1, 995, 5_000_000), start);
		// Sharks moved 3 days ago, prayer potions 40 days ago; the whip and platebody never.
		memory.observeHeld(Arrays.asList(2434), start + 160);
		memory.observeHeld(Arrays.asList(385), start + 197);
		Strata s = Strata.of(memory, memory.getBank(), start + 200);
		assertEquals("coins are left out", 4, s.getTotal());
		assertEquals(200, s.getWatched());
		// Watching has reached the 6 to 12 months layer, so the layers stop there.
		assertEquals(5, s.getLayers().size());
		assertEquals(1, s.getLayers().get(0).getCount());
		assertEquals(1, s.getLayers().get(2).getCount());
		assertEquals(2, s.getLayers().get(4).getCount());
		assertTrue(s.getLayers().get(4).isOpen());
		assertEquals("its items may be older still", "6 months or more", s.getLayers().get(4).getName());
		assertEquals("This week", s.getLayers().get(0).getName());
		assertFalse(s.getLayers().get(0).isOpen());
		assertEquals("the whip and platebody have sat 90 days or more", 2, s.getDeep());
		// Oldest first, equal ages by id: platebody 1119 before whip 4151, both "at least".
		assertEquals(1119, s.getOldest().get(0).getItemId());
		assertEquals(4151, s.getOldest().get(1).getItemId());
		assertTrue(s.getOldest().get(0).isAtLeast());
		assertEquals(2434, s.getOldest().get(2).getItemId());
		assertFalse(s.getOldest().get(2).isAtLeast());
		assertEquals(385, s.getOldest().get(3).getItemId());
	}

	@Test
	public void aYearOfWatchingClosesTheLastLayer()
	{
		Memory memory = new Memory();
		memory.observeBank(bank(4151, 1), 0);
		Strata s = Strata.of(memory, memory.getBank(), 500);
		assertEquals(6, s.getLayers().size());
		assertFalse("a year or more has no further layer to hide", s.getLayers().get(5).isOpen());
		assertEquals(1, s.getLayers().get(5).getCount());
	}

	@Test
	public void placeholdersAndAnUnseenBankAreNothing()
	{
		assertTrue(Strata.of(new Memory(), bank(4151, 1), 5).isEmpty());
		assertTrue(Strata.of(null, null, 5).isEmpty());
		Memory memory = new Memory();
		memory.observeBank(bank(4151, 0), 0);
		assertTrue(Strata.of(memory, memory.getBank(), 5).isEmpty());
	}

	@Test
	public void layerBoundaries()
	{
		assertEquals(0, Strata.layer(6));
		assertEquals(1, Strata.layer(7));
		assertEquals(3, Strata.layer(90));
		assertEquals(5, Strata.layer(365));
		assertEquals(5, Strata.layer(9_999));
	}

	@Test
	public void theFingerprintMovesWithWhatIsShown()
	{
		Memory memory = new Memory();
		memory.observeBank(bank(4151, 1, 385, 10), 0);
		String a = Strata.of(memory, memory.getBank(), 100).fingerprint();
		assertEquals(a, Strata.of(memory, memory.getBank(), 100).fingerprint());
		memory.observeHeld(Arrays.asList(385), 100);
		assertFalse(a.equals(Strata.of(memory, memory.getBank(), 100).fingerprint()));
	}
}
