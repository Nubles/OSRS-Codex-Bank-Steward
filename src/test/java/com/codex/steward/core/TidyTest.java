package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class TidyTest
{
	private static final Map<Integer, String> NAMES = new HashMap<>();

	static
	{
		NAMES.put(2434, "Prayer potion(4)");
		NAMES.put(139, "Prayer potion(3)");
		NAMES.put(141, "Prayer potion(2)");
		NAMES.put(143, "Prayer potion(1)");
		NAMES.put(3024, "Super restore(4)");
		NAMES.put(3030, "Super restore(1)");
		NAMES.put(1712, "Amulet of glory(4)");
		NAMES.put(1710, "Amulet of glory(3)");
		NAMES.put(11465, "Prayer mix(2)");
		NAMES.put(11467, "Prayer mix(1)");
		NAMES.put(12695, "Super combat potion(4)");
	}

	private static List<Tidy.Decant> decants(Map<Integer, Long> bank)
	{
		// Glory amulets are rubbed, never drunk.
		return Tidy.decants(bank, NAMES::get, id -> id != 1712 && id != 1710);
	}

	private static Map<Integer, Long> bank(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void decantingFoldsEveryDoseSizeIntoFourDosePotions()
	{
		// 4*10 + 3*3 + 2*1 + 1*2 = 53 doses: 13 four-dose potions and a one-dose, two slots where there were four.
		List<Tidy.Decant> list = decants(bank(2434, 10, 139, 3, 141, 1, 143, 2));
		assertEquals(1, list.size());
		Tidy.Decant d = list.get(0);
		assertEquals("Prayer potion", d.getName());
		assertEquals(53, d.getDoses());
		assertEquals(13, d.getFull());
		assertEquals(1, d.getLeftover());
		assertEquals(4, d.getSlots());
		assertEquals(2, d.getSlotsAfter());
		assertEquals(2, d.getFreed());
		assertEquals(2434, d.getIcon());
	}

	@Test
	public void aPotionThatGainsNothingIsLeftOut()
	{
		// Ten four-dose and one one-dose: decanting still leaves two slots.
		assertTrue(decants(bank(3024, 10, 3030, 1)).isEmpty());
		// One dose size alone is already as tidy as it gets.
		assertTrue(decants(bank(139, 30)).isEmpty());
		// Four one-dose and four four-dose make five four-dose potions: one slot.
		assertEquals(1, decants(bank(3024, 4, 3030, 4)).get(0).getFreed());
	}

	@Test
	public void jewelleryAndBarbarianMixesAreNotDecanted()
	{
		assertTrue(decants(bank(1712, 3, 1710, 5)).isEmpty());
		assertTrue(decants(bank(11465, 4, 11467, 3)).isEmpty());
	}

	@Test
	public void theMostSlotsComeFirst()
	{
		List<Tidy.Decant> list = decants(bank(2434, 1, 139, 1, 141, 1, 143, 1, 3024, 4, 3030, 4, 12695, 3));
		assertEquals("Prayer potion", list.get(0).getName());
		assertEquals("Super restore", list.get(1).getName());
		assertEquals(2 + 1, Tidy.freed(list));
	}
}
