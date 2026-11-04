package com.codex.steward.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabInterface;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.util.Text;
import org.junit.Test;
import org.mockito.Answers;

/**
 * Feeds every tab the Architect designs through RuneLite Bank Tags' own import code, the
 * private {@code TabInterface.importTag} the "Import tag tab" menu option runs on the clipboard,
 * and checks the tab, its icon, every tag and the saved layout come out as designed. If Bank
 * Tags changes how it reads a tab, this fails.
 */
public class BankTagsImportTest
{
	/** What Bank Tags did with one import. */
	private static final class Imported
	{
		TagTab tab;
		Layout layout;
		final Map<Integer, List<String>> tags = new LinkedHashMap<>();
		final List<Boolean> variations = new ArrayList<>();
	}

	private static Imported importTab(String clipboard) throws Exception
	{
		Imported out = new Imported();
		TagManager tagManager = mock(TagManager.class);
		doAnswer(inv ->
		{
			out.tags.computeIfAbsent(inv.getArgument(0), k -> new ArrayList<>()).add(inv.getArgument(1));
			out.variations.add(inv.getArgument(2));
			return null;
		}).when(tagManager).addTag(anyInt(), anyString(), anyBoolean());
		// TabManager.load is package-private: answered by name.
		TabManager tabManager = mock(TabManager.class, withSettings().defaultAnswer(inv ->
		{
			if (inv.getMethod().getName().equals("load"))
			{
				TagTab tab = new TagTab();
				tab.setTag(Text.standardize(inv.getArgument(0)));
				return tab;
			}
			return Answers.RETURNS_DEFAULTS.answer(inv);
		}));
		LayoutManager layoutManager = mock(LayoutManager.class);
		doAnswer(inv ->
		{
			out.layout = inv.getArgument(0);
			return null;
		}).when(layoutManager).saveLayout(org.mockito.ArgumentMatchers.any(Layout.class));

		Constructor<?> ctor = TabInterface.class.getDeclaredConstructors()[0];
		ctor.setAccessible(true);
		Object[] args = new Object[ctor.getParameterCount()];
		Class<?>[] types = ctor.getParameterTypes();
		for (int i = 0; i < types.length; i++)
		{
			args[i] = types[i] == TagManager.class ? tagManager : types[i] == TabManager.class ? tabManager
				: types[i] == LayoutManager.class ? layoutManager : null;
		}
		TabInterface tabs = (TabInterface) ctor.newInstance(args);
		Method importTag = TabInterface.class.getDeclaredMethod("importTag", Iterator.class);
		importTag.setAccessible(true);
		// As the menu option reads the clipboard: trimmed, split on commas by RuneLite's own splitter.
		String data = clipboard.trim();
		Iterator<String> it = Text.fromCSV(data).iterator();
		out.tab = (TagTab) importTag.invoke(tabs, it);
		return out;
	}

	private static Architect.Design sampleDesign()
	{
		Map<Integer, Architect.Facts> facts = new HashMap<>();
		Map<Integer, Long> bank = new LinkedHashMap<>();
		facts.put(ItemID.COINS, Architect.Facts.item("Coins", true));
		facts.put(ItemID.SHARK, new Architect.Facts("Shark", true, -1, 0, 0, 0, 0, true, false));
		facts.put(ItemID._4DOSEPRAYERRESTORE, new Architect.Facts("Prayer potion(4)", true, -1, 0, 0, 0, 0, false, true));
		facts.put(ItemID._3DOSEPRAYERRESTORE, new Architect.Facts("Prayer potion(3)", true, -1, 0, 0, 0, 0, false, true));
		facts.put(ItemID.ABYSSAL_WHIP, new Architect.Facts("Abyssal whip", true, 3, 164, 0, 0, 0, false, false));
		facts.put(ItemID.RUNE_PLATEBODY, new Architect.Facts("Rune platebody", true, 4, 0, -10, -30, 247, false, false));
		facts.put(ItemID.MAGIC_SHORTBOW, new Architect.Facts("Magic shortbow", true, 3, 0, 69, 0, 0, false, false));
		facts.put(ItemID.POH_TABLET_TELEPORTTOHOUSE, Architect.Facts.item("Teleport to house", true));
		facts.put(ItemID.RUNITE_ORE, Architect.Facts.item("Runite ore", true));
		facts.put(ItemID.NATURERUNE, Architect.Facts.item("Nature rune", true));
		facts.put(1002, Architect.Facts.item("Barcrawl card", false));
		facts.put(1003, Architect.Facts.item("Spent key", true));
		for (int id : facts.keySet())
		{
			bank.put(id, 10L);
		}
		bank.put(ItemID._4DOSEPRAYERRESTORE, 20L);
		Plans.Kit kit = new Plans.Kit("Vorkath", new LinkedHashSet<>(), Arrays.asList(Arrays.asList(ItemID.SHARK),
			Arrays.asList(ItemID._4DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE), Arrays.asList(ItemID.POH_TABLET_TELEPORTTOHOUSE)));
		Map<String, List<Integer>> needed = new LinkedHashMap<>();
		needed.put("Dragon Slayer II", Collections.singletonList(1002));
		return Architect.design(new Architect.Inputs().bank(bank).facts(facts::get).kits(Collections.singletonList(kit))
			.needed(needed).sell(Collections.singletonList(1003)));
	}

	@Test
	public void everyDesignedTabImportsThroughBankTagsExactlyAsDesigned() throws Exception
	{
		Architect.Design design = sampleDesign();
		// Every tab but Magic and Keepsakes has something in the sample.
		assertEquals(8, design.getTabs().size());
		for (Architect.Tab tab : design.getTabs())
		{
			Imported got = importTab(tab.export());
			assertNotNull(tab.getTag(), got.tab);
			assertEquals(tab.getTag(), got.tab.getTag());
			assertEquals(tab.icon(), got.tab.getIconItemId());
			assertNotNull("a layout is saved for " + tab.getTag(), got.layout);
			assertEquals(tab.getTag(), got.layout.getTag());
			assertArrayEquals(tab.getTag(), tab.getLayout(), got.layout.getLayout());
			// Every item in the layout is tagged with the tab, as itself and not as a variation.
			Set<Integer> placed = new LinkedHashSet<>();
			for (int id : tab.getLayout())
			{
				if (id > 0)
				{
					placed.add(id);
				}
			}
			assertEquals(placed, got.tags.keySet());
			for (List<String> names : got.tags.values())
			{
				for (String name : names)
				{
					assertEquals(tab.getTag(), name);
				}
			}
			for (boolean variation : got.variations)
			{
				assertEquals(false, variation);
			}
		}
	}

	@Test
	public void aKitShownTwiceInTheKitsTabKeepsBothSlots() throws Exception
	{
		Architect.Tab kits = sampleDesign().tab(Architect.Purpose.KITS);
		Imported got = importTab(kits.export());
		assertArrayEquals(new int[]{ItemID.SHARK, ItemID._4DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE, ItemID.POH_TABLET_TELEPORTTOHOUSE},
			got.layout.getLayout());
	}

	@Test
	public void tagsAreSafeForBankTags()
	{
		for (Architect.Purpose p : Architect.Purpose.values())
		{
			String tag = p.tag();
			// Bank Tags drops these characters from an imported name, and a comma would split the import.
			for (char c : "</>:,".toCharArray())
			{
				assertEquals(tag + " holds " + c, -1, tag.indexOf(c));
			}
			assertEquals(Text.standardize(tag), tag);
		}
	}
}
