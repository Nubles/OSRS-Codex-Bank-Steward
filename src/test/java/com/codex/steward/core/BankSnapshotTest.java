package com.codex.steward.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.codex.steward.CodexBankStewardConfig;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The full-bank snapshot other Codex plugins read. The group, key and JSON shape are a contract:
 * Plunder Pilot, Pest Control Pilot and Quartermaster carry a copy of
 * {@code bank-steward-bankV1.json} in their own tests, so the writer here must keep producing
 * exactly it.
 */
public class BankSnapshotTest
{
	private static final Gson GSON = new Gson();
	static final long SAVED_AT = 1_760_000_000_000L;

	/** The bank the shared fixture was written from. */
	static Map<Integer, Long> fixtureBank()
	{
		Map<Integer, Long> bank = new LinkedHashMap<>();
		bank.put(ItemID.SHARK, 240L);
		bank.put(ItemID.COINS, 1_234_567L);
		bank.put(ItemID.PHARAOHS_SCEPTRE_CHARGED_INITIAL, 1L);
		bank.put(ItemID.PEST_SEAL_8, 1L);
		bank.put(ItemID.PEST_VOID_KNIGHT_TOP, 1L);
		bank.put(ItemID._4DOSEPRAYERRESTORE, 30L);
		return bank;
	}

	private static String fixture(String name) throws Exception
	{
		try (InputStream in = BankSnapshotTest.class.getResourceAsStream(name))
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0; )
			{
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
		}
	}

	@Test
	public void publishedWhereTheReadersLook()
	{
		assertEquals("codexbanksteward", CodexBankStewardConfig.GROUP);
		assertEquals("bankV1", BankSnapshot.KEY);
		assertEquals(1, BankSnapshot.VERSION);
	}

	@Test
	public void writerProducesExactlyTheSharedFixture() throws Exception
	{
		String json = GSON.toJson(BankSnapshot.of(fixtureBank(), SAVED_AT));
		assertEquals(fixture("bank-steward-bankV1.json"), json);
		// Items in ascending id order, quantities as numbers, the fresh sceptre and the seal kept.
		assertTrue(json.startsWith("{\"version\":1,\"savedAt\":1760000000000,\"items\":{\"385\":240,\"995\":1234567,"));
		assertTrue(json.contains("\"26950\":1"));
		assertTrue(json.contains("\"11666\":1"));
	}

	@Test
	public void readsBackWhatItWrote() throws Exception
	{
		BankSnapshot read = BankSnapshot.read(GSON, fixture("bank-steward-bankV1.json"));
		assertEquals(SAVED_AT, read.getSavedAt());
		assertEquals(fixtureBank(), new HashMap<>(read.getItems()));
		assertFalse(read.isTruncated());
		assertTrue(read.sameItems(BankSnapshot.of(fixtureBank(), 0)));
	}

	@Test
	public void leavesOutEmptyStacksAndStaysBounded()
	{
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(ItemID.SHARK, 0L);
		bank.put(-1, 5L);
		bank.put(ItemID.COINS, 10L);
		assertEquals("{\"995\":10}", GSON.toJson(BankSnapshot.of(bank, 1).getItems()));

		Map<Integer, Long> huge = new HashMap<>();
		for (int id = 1; id <= BankSnapshot.MAX_ITEMS + 50; id++)
		{
			huge.put(id, 1L);
		}
		BankSnapshot capped = BankSnapshot.of(huge, 1);
		assertEquals(BankSnapshot.MAX_ITEMS, capped.getItems().size());
		assertTrue(capped.isTruncated());
		assertTrue("lowest ids are kept", capped.getItems().containsKey(1));
	}

	@Test
	public void unreadableSnapshotsAreNone()
	{
		assertNull(BankSnapshot.read(GSON, null));
		assertNull(BankSnapshot.read(GSON, ""));
		assertNull(BankSnapshot.read(GSON, "{\"items\":[1,2]"));
		assertNull(BankSnapshot.read(GSON, "[1,2,3]"));
		assertNull(BankSnapshot.read(GSON, "{\"version\":1}"));
	}
}
