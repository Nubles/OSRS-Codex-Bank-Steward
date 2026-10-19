package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class BankViewTest
{
	private static Advice item(int id, long ge)
	{
		return new Advice(id, 1, ge, true, Advice.Verdict.SPENT, null, 0).prices(ge, 0);
	}

	private static final ClearOut PLAN = ClearOut.plan(Arrays.asList(item(4300, 1000), item(4298, 2000), item(1119, 3000)), false, 100);

	@Test
	public void beforeAClearOutTheWholePlanShows()
	{
		BankView view = BankView.clearOut(PLAN, null, null, null);
		assertEquals(3, view.size());
		assertTrue(view.contains(4300, 4300));
		assertFalse(view.contains(385, 385));
	}

	@Test
	public void duringAClearOutOnlyWhatIsLeftToClearShows()
	{
		Memory.Session session = new Memory.Session(100, Arrays.asList(
			new Memory.Planned(4300, 1, ClearOut.Route.SELL, 980),
			new Memory.Planned(4298, 1, ClearOut.Route.SELL, 1960)));
		// The plan has moved on (Steel platebody joined it) but the clear-out set out with two items, one gone.
		BankView view = BankView.clearOut(PLAN, session, Collections.singletonList(4300), Collections.emptyList());
		assertEquals(1, view.size());
		assertTrue(view.contains(4298, 4298));
		assertFalse("cleared already", view.contains(4300, 4300));
		assertFalse("not part of the clear-out under way", view.contains(1119, 1119));
		// Kept since the clear-out began: it stays in the bank, so it is not shown as one to take.
		assertTrue(BankView.clearOut(PLAN, session, Collections.singletonList(4300), Collections.singletonList(4298)).isEmpty());
	}

	@Test
	public void onlyTheStewardsOwnViewsAreClosed()
	{
		assertTrue(BankView.isOurs(BankView.CLEAR_OUT));
		assertTrue(BankView.isOurs(BankView.TIDY));
		// A layer shown from the layers card closes with the bank like the others.
		assertTrue(BankView.isOurs(BankView.LAYER));
		assertTrue(BankView.shouldClose("vorkath", BankView.LAYER));
		assertFalse("the player's own tab stays remembered", BankView.isOurs("vorkath"));
		assertFalse(BankView.isOurs(null));
		assertFalse(BankView.isOurs(""));
	}

	@Test
	public void aPlaceholderOrNotedFormMatchesThroughItsItem()
	{
		BankView view = BankView.clearOut(PLAN, null, null, null);
		// The bank shows a placeholder by its own id; the Steward keeps the item's.
		assertTrue(view.contains(14512, 4300));
	}

	@Test
	public void tidyingShowsEveryDoseAndThePlaceholdersOwnIds()
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(2434, 1L);
		bank.put(139, 2L);
		bank.put(143, 1L);
		Map<Integer, String> names = new HashMap<>();
		names.put(2434, "Prayer potion(4)");
		names.put(139, "Prayer potion(3)");
		names.put(143, "Prayer potion(1)");
		BankView view = BankView.tidy(Collections.singletonList(17000), Tidy.decants(bank, names::get, id -> true));
		assertTrue(view.contains(2434, 2434));
		assertTrue(view.contains(139, 139));
		assertTrue(view.contains(143, 143));
		assertTrue(view.contains(17000, 17000));
		assertEquals(4, view.size());
	}

	@Test
	public void nothingToShowIsSaidBeforeTheBankIsAskedFor()
	{
		assertEquals(BankView.Refusal.NOTHING, BankView.clearOut(null, null, null, null).refusal(true, true));
		assertEquals(BankView.Refusal.TAGS_OFF, BankView.clearOut(PLAN, null, null, null).refusal(true, false));
		assertEquals(BankView.Refusal.BANK_CLOSED, BankView.clearOut(PLAN, null, null, null).refusal(false, true));
		assertNull(BankView.clearOut(PLAN, null, null, null).refusal(true, true));
		assertTrue(BankView.explain(BankView.Refusal.BANK_CLOSED, BankView.CLEAR_OUT).contains("tag:steward"));
	}

	@Test
	public void theTagNamesDoNotCollide()
	{
		// Bank Tags matches a typed tag search by prefix against the player's own tags, but a
		// registered tag by its whole name: "tag:steward" must not open the tidy view.
		assertFalse(BankView.CLEAR_OUT.equals(BankView.TIDY));
		assertEquals(BankView.CLEAR_OUT, BankView.CLEAR_OUT.toLowerCase());
		assertEquals(BankView.TIDY, BankView.TIDY.toLowerCase());
	}

	@Test
	public void aViewRememberedForTheNextVisitIsClosedToo()
	{
		// Bank Tags clears the open tag as the bank closes, before the Steward hears of it, but
		// keeps its name to open again next visit.
		assertTrue(BankView.shouldClose(null, BankView.CLEAR_OUT));
		assertTrue(BankView.shouldClose(BankView.TIDY, ""));
		assertFalse(BankView.shouldClose("slayer", "slayer"));
		assertFalse(BankView.shouldClose(null, null));
	}

	@Test
	public void closingAViewGivesThePlayerTheirOwnTabBack()
	{
		assertEquals("vorkath", BankView.tabAfterClose("vorkath"));
		assertEquals("pressed from the main bank", "", BankView.tabAfterClose(null));
		assertEquals("one view opened from the other", "", BankView.tabAfterClose(BankView.CLEAR_OUT));
	}

	@Test
	public void anOpenViewIsLaidOutAgainAsItsListMoves()
	{
		Memory.Session session = new Memory.Session(100, Arrays.asList(
			new Memory.Planned(4300, 1, ClearOut.Route.SELL, 980),
			new Memory.Planned(4298, 1, ClearOut.Route.SELL, 1960)));
		BankView before = BankView.clearOut(PLAN, session, Collections.emptyList(), Collections.emptyList());
		BankView after = BankView.clearOut(PLAN, session, Collections.singletonList(4300), Collections.emptyList());
		BankView done = BankView.clearOut(PLAN, session, Arrays.asList(4300, 4298), Collections.emptyList());
		// Open: deposit one and the view lays out again; clear the last and the whole bank comes back.
		assertEquals(BankView.Change.LAYOUT, BankView.change(BankView.CLEAR_OUT, BankView.CLEAR_OUT, before, after));
		assertEquals(BankView.Change.CLOSE, BankView.change(BankView.CLEAR_OUT, BankView.CLEAR_OUT, after, done));
		// The same list, another view open, or none: nothing is touched.
		assertEquals(BankView.Change.NONE, BankView.change(BankView.CLEAR_OUT, BankView.CLEAR_OUT, after, after));
		assertEquals(BankView.Change.NONE, BankView.change(BankView.TIDY, BankView.CLEAR_OUT, before, after));
		assertEquals(BankView.Change.NONE, BankView.change(null, BankView.CLEAR_OUT, before, after));
		assertEquals(BankView.Change.NONE, BankView.change("my-tag", BankView.CLEAR_OUT, before, done));
	}

	@Test
	public void aLayerViewHoldsItsItemsAndSaysHowToOpenIt()
	{
		BankView layer = BankView.layer(java.util.Arrays.asList(4151, 1127));
		assertTrue(layer.contains(4151, 4151));
		assertFalse(layer.contains(385, 385));
		assertTrue(BankView.layer(java.util.Collections.emptySet()).isEmpty());
		assertEquals(BankView.Change.LAYOUT, BankView.change(BankView.LAYER, BankView.LAYER, layer,
			BankView.layer(java.util.Collections.singletonList(1127))));
		assertEquals(BankView.Change.CLOSE, BankView.change(BankView.LAYER, BankView.LAYER, layer, BankView.EMPTY));
		assertTrue(BankView.explain(BankView.Refusal.BANK_CLOSED, BankView.LAYER).contains("click a layer"));
		assertTrue(BankView.explain(BankView.Refusal.NOTHING, BankView.LAYER).contains("that layer"));
	}
}
