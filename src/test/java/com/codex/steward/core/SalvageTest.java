package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
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
}
