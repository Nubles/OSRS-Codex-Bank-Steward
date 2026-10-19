package com.codex.steward.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Coins, changes, durations and dates in the short forms the panel uses. */
public final class Format
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

	private Format()
	{
	}

	private static final String[] UNITS = {"k", "M", "B"};

	/**
	 * "8,240", "41.2k", "412k", "1.24M", "3.60M", "26.0M": in full below 10,000, otherwise always
	 * three figures, so amounts side by side on a card read to the same precision (a trailing
	 * zero is kept rather than dropped, which would turn "26.0M" beside "98.7M" into "26M").
	 */
	public static String amount(long n)
	{
		if (n < 10_000)
		{
			return String.format(Locale.ENGLISH, "%,d", n);
		}
		double value = n / 1_000.0;
		int unit = 0;
		while (value >= 999.5 && unit < UNITS.length - 1)
		{
			value /= 1_000;
			unit++;
		}
		// Rounding can carry into the next band (9.996M is "10.0M", not "10.00M").
		int places = value >= 99.95 ? 0 : value >= 9.995 ? 1 : 2;
		return String.format(Locale.ENGLISH, "%." + places + "f", value) + UNITS[unit];
	}

	/** Coins in the short form, negative amounts with a minus: "812M", "-45.2k". */
	public static String coins(long gp)
	{
		return gp < 0 ? "-" + amount(-gp) : amount(gp);
	}

	/** A change with its sign: "+3.2M", "-412k", "0". */
	public static String signed(long gp)
	{
		return gp > 0 ? "+" + amount(gp) : gp < 0 ? "-" + amount(-gp) : "0";
	}

	/** A quantity change with its sign, written in full: "+1,240", "-6". */
	public static String count(long n)
	{
		return (n > 0 ? "+" : n < 0 ? "-" : "") + String.format(Locale.ENGLISH, "%,d", Math.abs(n));
	}

	/** A share as a whole percent, or "<1%". */
	public static String percent(double share)
	{
		if (share > 0 && share < 0.01)
		{
			return "<1%";
		}
		return Math.round(share * 100) + "%";
	}

	/** "just now", "12m ago", "3h ago", "2d ago". */
	public static String ago(long ms)
	{
		long minutes = Math.max(0, ms) / 60_000;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		if (minutes < 48 * 60)
		{
			return minutes / 60 + "h ago";
		}
		return minutes / (24 * 60) + "d ago";
	}

	/** "2h 14m", "38m", "under a minute". */
	public static String duration(long ms)
	{
		long minutes = ms / 60_000;
		if (minutes < 1)
		{
			return "under a minute";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return minutes / 60 + "h " + minutes % 60 + "m";
	}

	/** "14:02" in the player's own time. */
	public static String time(long epochMs)
	{
		return CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Thu 16 Oct". */
	public static String day(long epochMs)
	{
		return DAY.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "16 Oct". */
	public static String shortDay(long epochMs)
	{
		return SHORT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Oct 26". */
	public static String month(long epochMs)
	{
		return MONTH.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Thu 16 Oct" for a day book date. */
	public static String day(java.time.LocalDate date)
	{
		return DAY.format(date);
	}

	/** "Thursday 16 October 2026". */
	public static String date(long epochMs)
	{
		return DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** An item stack the way the game writes it: "950", "45K", "12M". */
	public static String stack(long n)
	{
		if (n < 100_000)
		{
			return String.valueOf(n);
		}
		if (n < 10_000_000)
		{
			return n / 1_000 + "K";
		}
		return n / 1_000_000 + "M";
	}

	/** The colour the game writes a stack of that size in. */
	public static java.awt.Color stackColor(long n)
	{
		return n < 100_000 ? Theme.QTY_YELLOW : n < 10_000_000 ? Theme.QTY_WHITE : Theme.QTY_GREEN;
	}

	/** A count in full with separators: "12,400". */
	public static String quantity(long n)
	{
		return String.format(Locale.ENGLISH, "%,d", n);
	}

	/** "today", "1 day", "134 days". */
	public static String days(long n)
	{
		return n <= 0 ? "today" : n == 1 ? "1 day" : n + " days";
	}
}
