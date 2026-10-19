package com.codex.steward.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Damaged forms of a good saved value, for proving a reader survives what a profile can really
 * hold: nothing, a value cut short, the wrong type, numbers too big or below zero, a newer
 * version, and plain garbage. Each form is tried on its own, so a failure names the one at fault.
 */
final class Damaged
{
	/** Stand-ins for one number: too big for any type, below zero, a fraction, and the wrong type. */
	private static final String[] NUMBERS = {
		"9223372036854775807", "-9223372036854775808", "99999999999999999999999", "-1", "0", "1e308", "2.5",
		"\"7\"", "null", "true", "[]", "{}"
	};
	/** Stand-ins for one string. */
	private static final String[] STRINGS = {"\"\"", "123", "-5", "null", "{}", "[]", "\"\\u0000\""};
	/** Stand-ins for a map key that names a number, as item ids do. */
	private static final String[] KEYS = {"\"x\"", "\"-1\"", "\"0\"", "\"99999999999\"", "\"\""};
	/** Most single-token forms tried per kind, so a large value still runs quickly. */
	private static final int MOST = 150;

	private Damaged()
	{
	}

	/** Damage that has nothing to do with any particular value. */
	static List<String> anyValue()
	{
		return new ArrayList<>(Arrays.asList(null, "", "   ", "null", "[]", "{}", "[{}]", "[null]", "\"text\"", "0", "-1",
			"true", "{{{", "}", "{\"", "<html>", "\u0000\u00ff", "{\"version\":99}", "{\"version\":-1}",
			repeat("[", 20_000), repeat("{\"a\":", 10_000), "[" + repeat("1,", 200_000) + "1]",
			"\"" + repeat("x", 1_000_000) + "\""));
	}

	/** Every damaged form of {@code good} worth trying, the general ones first. */
	static List<String> of(String good)
	{
		Set<String> out = new LinkedHashSet<>();
		for (int i = 1; i < 24; i++)
		{
			out.add(good.substring(0, good.length() * i / 24));
		}
		out.add(good + "garbage");
		out.add(good + good);
		out.add(good.replace("\"version\":1", "\"version\":2"));
		out.add(good.replace("\"version\":1", "\"version\":99"));
		List<int[]> numbers = new ArrayList<>();
		List<int[]> strings = new ArrayList<>();
		List<int[]> keys = new ArrayList<>();
		tokens(good, numbers, strings, keys);
		for (String n : NUMBERS)
		{
			out.add(replaceAll(good, numbers, n));
		}
		for (int[] t : first(numbers))
		{
			for (String n : NUMBERS)
			{
				out.add(good.substring(0, t[0]) + n + good.substring(t[1]));
			}
		}
		for (int[] t : first(strings))
		{
			for (String s : STRINGS)
			{
				out.add(good.substring(0, t[0]) + s + good.substring(t[1]));
			}
		}
		for (int[] t : first(keys))
		{
			for (String k : KEYS)
			{
				out.add(good.substring(0, t[0]) + k + good.substring(t[1]));
			}
		}
		List<String> all = anyValue();
		all.addAll(out);
		return all;
	}

	private static List<int[]> first(List<int[]> tokens)
	{
		return tokens.subList(0, Math.min(MOST, tokens.size()));
	}

	private static String replaceAll(String s, List<int[]> tokens, String with)
	{
		StringBuilder b = new StringBuilder();
		int at = 0;
		for (int[] t : tokens)
		{
			b.append(s, at, t[0]).append(with);
			at = t[1];
		}
		return b.append(s.substring(at)).toString();
	}

	/** Finds the numbers, string values and numeric keys of a JSON text, as [start, end) spans. */
	private static void tokens(String s, List<int[]> numbers, List<int[]> strings, List<int[]> keys)
	{
		int i = 0;
		while (i < s.length())
		{
			char c = s.charAt(i);
			if (c == '"')
			{
				int end = i + 1;
				while (end < s.length() && s.charAt(end) != '"')
				{
					end += s.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(end + 1, s.length());
				int next = end;
				while (next < s.length() && Character.isWhitespace(s.charAt(next)))
				{
					next++;
				}
				boolean key = next < s.length() && s.charAt(next) == ':';
				if (!key)
				{
					strings.add(new int[]{i, end});
				}
				else if (s.substring(i + 1, end - 1).matches("-?\\d+"))
				{
					keys.add(new int[]{i, end});
				}
				i = end;
			}
			else if (c == '-' || Character.isDigit(c))
			{
				int end = i + 1;
				while (end < s.length() && "0123456789.eE+-".indexOf(s.charAt(end)) >= 0)
				{
					end++;
				}
				numbers.add(new int[]{i, end});
				i = end;
			}
			else
			{
				i++;
			}
		}
	}

	static String repeat(String s, int n)
	{
		StringBuilder b = new StringBuilder(s.length() * n);
		for (int i = 0; i < n; i++)
		{
			b.append(s);
		}
		return b.toString();
	}

	/** A short, printable form of a damaged value for a failure message. */
	static String show(String s)
	{
		if (s == null)
		{
			return "null";
		}
		return s.length() <= 120 ? s : s.substring(0, 60) + "..." + s.substring(s.length() - 40) + " (" + s.length() + " chars)";
	}
}
