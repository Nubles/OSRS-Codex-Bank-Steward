package com.codex.steward;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

/**
 * Every setting reads well in RuneLite's config panel: a short name, one plain sentence saying
 * what it does, a section it belongs in, and its own place in that section's order.
 */
public class SettingsTest
{
	@Test
	public void everySettingIsNamedAndExplainedInOneSentence()
	{
		Set<String> sections = new HashSet<>();
		for (Field f : CodexBankStewardConfig.class.getDeclaredFields())
		{
			ConfigSection s = f.getAnnotation(ConfigSection.class);
			if (s != null)
			{
				sections.add(f.getName());
				assertTrue(s.description().length() > 0);
			}
		}
		Set<String> keys = new HashSet<>();
		Set<String> names = new HashSet<>();
		Map<String, Set<Integer>> places = new HashMap<>();
		int items = 0;
		for (Method m : CodexBankStewardConfig.class.getDeclaredMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			items++;
			String where = item.keyName() + ": ";
			assertTrue(where + "key used once", keys.add(item.keyName()));
			assertTrue(where + "name used once", names.add(item.name()));
			assertTrue(where + "a short name", item.name().length() > 0 && item.name().length() <= 28);
			String d = item.description();
			assertTrue(where + "described", d.length() >= 20);
			assertTrue(where + "one sentence, no full stop: " + d, !d.endsWith(".") && !d.matches(".*[.!?] [A-Z].*"));
			assertTrue(where + "starts with a capital", Character.isUpperCase(d.charAt(0)));
			assertFalse(where + "in a section", item.section().isEmpty());
			assertTrue(where + "a known section", sections.contains(item.section()) || sectionValue(item.section()));
			assertTrue(where + "its own place in its section",
				places.computeIfAbsent(item.section(), k -> new HashSet<>()).add(item.position()));
		}
		assertTrue(items > 0);
		assertNotNull(sections);
	}

	/** A section is named by the value of its field, which may differ from the field's name. */
	private static boolean sectionValue(String value)
	{
		for (Field f : CodexBankStewardConfig.class.getDeclaredFields())
		{
			try
			{
				if (f.getAnnotation(ConfigSection.class) != null && value.equals(f.get(null)))
				{
					return true;
				}
			}
			catch (IllegalAccessException e)
			{
				return false;
			}
		}
		return false;
	}

	@Test
	public void choicesReadAsWords()
	{
		for (Method m : CodexBankStewardConfig.class.getDeclaredMethods())
		{
			if (m.getAnnotation(ConfigItem.class) != null && m.getReturnType().isEnum())
			{
				for (Object choice : m.getReturnType().getEnumConstants())
				{
					String shown = choice.toString();
					// "AT_THE_BANK" or "ALWAYS" is the code's name for a choice, not words a player reads.
					assertFalse(m.getName() + " shows " + shown, shown.length() > 1 && shown.equals(shown.toUpperCase()));
					assertEquals(m.getName() + " shows " + shown, shown.trim(), shown);
				}
			}
		}
	}

	@Test
	public void savedSettingsKeepTheirKeys()
	{
		// A setting's key is where its value is saved: renaming one quietly resets it for every player who changed it.
		// Every key on main and every one added since is pinned.
		Set<String> keys = new HashSet<>();
		for (Method m : CodexBankStewardConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keys.add(item.keyName());
			}
		}
		for (String key : new String[]{"bankMarks", "bankSummary", "codexPlans", "dustDays", "graceDays", "guardStash", "shareBank", "untradeableDust", "valueBy"})
		{
			assertTrue(key, keys.contains(key));
		}
	}
}
