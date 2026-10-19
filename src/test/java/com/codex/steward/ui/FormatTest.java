package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void amountsSideBySideKeepTheSamePrecision()
	{
		assertEquals("8,240", Format.amount(8_240));
		assertEquals("41.2k", Format.amount(41_200));
		assertEquals("412k", Format.amount(412_000));
		assertEquals("1.24M", Format.amount(1_240_000));
		// Three figures throughout: a round amount keeps its zero instead of reading coarser.
		assertEquals("3.60M", Format.amount(3_600_000));
		assertEquals("26.0M", Format.amount(26_000_000));
		assertEquals("812M", Format.amount(812_400_000));
		assertEquals("1.50B", Format.amount(1_500_000_000L));
		// Rounding that reaches the next band moves into it.
		assertEquals("100k", Format.amount(99_960));
		assertEquals("1.00M", Format.amount(999_600));
		assertEquals("10.0M", Format.amount(9_996_000));
		assertEquals("-45.2k", Format.coins(-45_200));
		assertEquals("+880k", Format.signed(880_000));
	}
}
