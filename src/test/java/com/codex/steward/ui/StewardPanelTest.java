package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Reckoning;
import java.util.Collections;
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
}
