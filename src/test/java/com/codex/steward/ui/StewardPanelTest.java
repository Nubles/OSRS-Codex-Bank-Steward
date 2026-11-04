package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import com.codex.steward.core.Architect;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Reckoning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class StewardPanelTest
{
	@Test
	public void theKeptCountTakesInKeptPlaceholders()
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(4151, 1L);
		Memory memory = new Memory();
		memory.observeBank(bank, 100);
		memory.setKept(4151, true);
		Reckoning reckoning = Reckoning.of(new Catalog(new Catalog.Data()), memory, bank, Collections.emptySet(), new boolean[0],
			id -> 100, id -> true, new Reckoning.Rules(90, 14), 200);
		assertEquals(1, reckoning.getKept().size());

		StewardPanel panel = new StewardPanel((id, done) -> null, null);
		// The Kept page lists one kept item and one kept placeholder (a fire cape's), so the header says 2.
		panel.update(reckoning, null, Collections.emptyList(), Collections.emptyMap(), "Gilded Owl", StewardPanel.State.READY, false, false,
			new StewardPanel.Extras(Collections.emptyList(), Collections.singletonList(6570), Collections.emptyList(), null));
		assertEquals(2, panel.keptCount());
	}

	@Test
	public void copyingADesignedTabPutsItsBankTagsImportOnTheClipboardAndMovesOn()
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(995, 100L);
		bank.put(4151, 1L);
		bank.put(1004, 1L);
		Map<Integer, Architect.Facts> facts = new HashMap<>();
		facts.put(995, Architect.Facts.item("Coins", true));
		facts.put(4151, new Architect.Facts("Abyssal whip", true, 3, 164, 0, 0, 0, false, false));
		facts.put(1004, Architect.Facts.item("Pet rock", false));
		Architect.Design design = Architect.design(new Architect.Inputs().bank(bank).facts(facts::get));
		Memory memory = new Memory();
		memory.observeBank(bank, 100);
		Reckoning reckoning = Reckoning.of(new Catalog(new Catalog.Data()), memory, bank, Collections.emptySet(), new boolean[0],
			id -> 100, id -> true, new Reckoning.Rules(90, 14), 110);
		List<String> clipboard = new ArrayList<>();
		StewardPanel panel = new StewardPanel((id, done) -> null, new StewardPanel.Actions()
		{
			@Override
			public void keep(int itemId, boolean kept)
			{
			}

			@Override
			public void startClearOut()
			{
			}

			@Override
			public void endClearOut()
			{
			}

			@Override
			public void copyToClipboard(String text)
			{
				clipboard.add(text);
			}
		});
		panel.update(reckoning, null, Collections.emptyList(), Collections.emptyMap(), "Gilded Owl", StewardPanel.State.READY, false, false,
			new StewardPanel.Extras(null, null, null, null, null, design));
		panel.showTab(3);
		assertEquals(0, panel.designTab());
		panel.copyShown();
		assertEquals(design.getTabs().get(0).export(), clipboard.get(0));
		// On to the next tab not yet copied.
		assertEquals(1, panel.designTab());
		panel.copyShown();
		panel.copyShown();
		assertEquals(3, clipboard.size());
		assertEquals(design.getTabs().get(2).export(), clipboard.get(2));
		// Every tab copied: the page stays where it is.
		assertEquals(2, panel.designTab());
	}
}
