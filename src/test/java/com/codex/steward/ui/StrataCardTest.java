package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Strata;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class StrataCardTest
{
	@Test
	public void theDeepShareNeverRoundsToAllOrNoneWhenItIsNot()
	{
		assertEquals("99%", StrataCard.share(199, 200));
		assertEquals("<1%", StrataCard.share(1, 400));
		assertEquals("100%", StrataCard.share(200, 200));
		assertEquals("0%", StrataCard.share(0, 200));
		assertEquals("0%", StrataCard.share(0, 0));
		assertEquals("38%", StrataCard.share(3, 8));
	}

	@Test
	public void theFirstDayIsCalledDayOneNotToday()
	{
		// "today watched so far" read as a slip on every first look at the card.
		assertEquals("Day 1", StrataCard.watched(0)[0]);
		assertEquals("of watching", StrataCard.watched(0)[1]);
		assertEquals("40 days", StrataCard.watched(40)[0]);
		assertEquals("watched so far", StrataCard.watched(40)[1]);
	}

	@Test
	public void theHeadlineAndTheFootNeverSayTheSameTime()
	{
		// Before three months the headline gives the watching time, so the foot looks ahead instead.
		String[] head = StrataCard.watched(40);
		String foot = StrataCard.foot(40);
		assertFalse(foot, foot.contains(head[0]));
		assertEquals("The 3-month share shows in 50 days", foot);
		assertEquals("The 3-month share shows in 90 days", StrataCard.foot(0));
		assertEquals("The 3-month share shows in 1 day", StrataCard.foot(89));
		// After, the headline is a share and the foot gives how far back ages are known.
		assertEquals("Watching for 200 days", StrataCard.foot(200));
	}

	@Test
	public void oneAgeSharedByAllTheDeepestIsSaidOnce()
	{
		Memory memory = new Memory();
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(4151, 1L);
		bank.put(385, 100L);
		bank.put(1127, 1L);
		memory.observeBank(bank, 0);
		Strata still = Strata.of(memory, memory.getBank(), 200);
		assertEquals("6mo+", StrataCard.sharedAge(still.getOldest()));
		memory.observeHeld(Arrays.asList(385), 150);
		assertNull("ages differ", StrataCard.sharedAge(Strata.of(memory, memory.getBank(), 200).getOldest()));
	}
}
