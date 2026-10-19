package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
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
}
