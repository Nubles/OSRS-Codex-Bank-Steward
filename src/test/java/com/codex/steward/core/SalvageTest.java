package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** A damaged field costs only that field, whatever shape the damage takes. */
public class SalvageTest
{
	private static final Gson GSON = new Gson();

	static final class Saved
	{
		int kept;
		List<String> names;
		Map<String, Integer> counts;
	}

	@Test
	public void aListWhereAMapBelongsCostsOnlyThatField()
	{
		// A map where the list belongs once emptied every field, as "{}" does not read as a list.
		Saved s = Salvage.read(GSON, "{\"kept\":5,\"names\":{\"x\":1},\"counts\":{\"a\":1}}", Saved.class);
		assertEquals(5, s.kept);
		assertNull(s.names);
		assertEquals(1, (int) s.counts.get("a"));
		s = Salvage.read(GSON, "{\"kept\":5,\"names\":[\"a\"],\"counts\":[1,2]}", Saved.class);
		assertEquals(5, s.kept);
		assertEquals("a", s.names.get(0));
		// Gson reads a map from a list of pairs, so this one comes back empty rather than missing.
		assertTrue(s.counts == null || s.counts.isEmpty());
	}

	@Test
	public void badEntriesCostOnlyThemselves()
	{
		Saved s = Salvage.read(GSON, "{\"kept\":\"five\",\"names\":[\"a\",{},\"b\"],\"counts\":{\"a\":1,\"b\":\"x\"}}", Saved.class);
		assertEquals(0, s.kept);
		assertEquals(2, s.names.size());
		assertEquals(1, s.counts.size());
		assertTrue(Salvage.read(GSON, "[1]", Saved.class) == null);
	}

	@Test
	public void aSaveThisVersionKeepsWholeLosesNothing()
	{
		String own = "{\"kept\":5,\"names\":[\"a\"],\"counts\":{\"a\":1}}";
		assertFalse(Salvage.wouldLose(GSON, own, Salvage.read(GSON, own, Saved.class)));
		// An older save that lacks a field, or nothing saved at all, loses nothing either.
		String older = "{\"kept\":5}";
		assertFalse(Salvage.wouldLose(GSON, older, Salvage.read(GSON, older, Saved.class)));
		assertFalse(Salvage.wouldLose(GSON, null, null));
		assertFalse(Salvage.wouldLose(GSON, "", null));
	}

	@Test
	public void aNewerOrDamagedSaveWouldLoseWhatItHolds()
	{
		// A field a newer version added, a value of a shape this one cannot read, and garbage.
		String newer = "{\"kept\":5,\"names\":[\"a\"],\"added\":{\"x\":1}}";
		assertTrue(Salvage.wouldLose(GSON, newer, Salvage.read(GSON, newer, Saved.class)));
		String damaged = "{\"kept\":\"five\",\"names\":[\"a\",{},\"b\"]}";
		assertTrue(Salvage.wouldLose(GSON, damaged, Salvage.read(GSON, damaged, Saved.class)));
		assertTrue(Salvage.wouldLose(GSON, "{\"kept\":5", null));
	}
}
