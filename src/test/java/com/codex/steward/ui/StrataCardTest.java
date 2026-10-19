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
}
