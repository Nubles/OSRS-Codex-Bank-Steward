package com.codex.steward.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Reads a saved value that may be damaged, keeping as much of it as can be read. A field of the
 * wrong type, or a number too big for its field, costs only that field; a damaged entry of a list
 * or map costs only that entry. A value that is not an object at all, or cannot be parsed, reads
 * as null.
 */
public final class Salvage
{
	private Salvage()
	{
	}

	/** @return what could be read, or null when nothing could */
	public static <T> T read(Gson gson, String json, Class<T> type)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		JsonElement root;
		try
		{
			root = gson.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			// Cut short, not JSON, or nested past what can be read: nothing is kept.
			return null;
		}
		if (root == null || !root.isJsonObject())
		{
			return null;
		}
		T whole = parse(gson, root, type);
		if (whole != null)
		{
			return whole;
		}
		JsonObject kept = new JsonObject();
		for (Map.Entry<String, JsonElement> field : root.getAsJsonObject().entrySet())
		{
			JsonElement value = field.getValue();
			if (fits(gson, field.getKey(), value, type))
			{
				kept.add(field.getKey(), value);
				continue;
			}
			JsonElement trimmed = trim(gson, field.getKey(), value, type);
			// A list where a map belongs trims to an empty list that still does not fit; then the
			// field is lost alone, not with everything else.
			if (trimmed != null && fits(gson, field.getKey(), trimmed, type))
			{
				kept.add(field.getKey(), trimmed);
			}
		}
		return parse(gson, kept, type);
	}

	/** A list or map field with only the entries that can be read, or null for any other field. */
	private static <T> JsonElement trim(Gson gson, String name, JsonElement value, Class<T> type)
	{
		if (value.isJsonArray())
		{
			JsonArray out = new JsonArray();
			for (JsonElement e : value.getAsJsonArray())
			{
				JsonArray one = new JsonArray();
				one.add(e);
				if (fits(gson, name, one, type))
				{
					out.add(e);
				}
			}
			return out;
		}
		if (value.isJsonObject())
		{
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet())
			{
				JsonObject one = new JsonObject();
				one.add(e.getKey(), e.getValue());
				if (fits(gson, name, one, type))
				{
					out.add(e.getKey(), e.getValue());
				}
			}
			return out;
		}
		return null;
	}

	/**
	 * True when saving {@code kept}, read from {@code json}, in place of {@code json} would lose
	 * something {@code json} holds: a field or value this version does not know (one a newer
	 * version wrote), or one it had to mend or leave out. A field {@code json} lacks, as an older
	 * save does, loses nothing. A value that cannot be parsed at all loses everything.
	 */
	public static boolean wouldLose(Gson gson, String json, Object kept)
	{
		if (json == null || json.trim().isEmpty())
		{
			return false;
		}
		JsonElement was;
		try
		{
			was = gson.fromJson(json, JsonElement.class);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return true;
		}
		if (was == null || was.isJsonNull())
		{
			return false;
		}
		try
		{
			return kept == null || !covers(was, gson.toJsonTree(kept));
		}
		catch (StackOverflowError e)
		{
			return true;
		}
	}

	/** True when {@code now} holds everything {@code was} does: every field, list entry and value. */
	private static boolean covers(JsonElement was, JsonElement now)
	{
		if (was.isJsonNull())
		{
			// Gson leaves an empty field out.
			return true;
		}
		if (was.isJsonObject())
		{
			if (!now.isJsonObject())
			{
				return false;
			}
			JsonObject have = now.getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : was.getAsJsonObject().entrySet())
			{
				JsonElement value = have.get(e.getKey());
				if (!e.getValue().isJsonNull() && (value == null || !covers(e.getValue(), value)))
				{
					return false;
				}
			}
			return true;
		}
		if (was.isJsonArray())
		{
			JsonArray a = was.getAsJsonArray();
			if (!now.isJsonArray() || now.getAsJsonArray().size() != a.size())
			{
				return false;
			}
			JsonArray b = now.getAsJsonArray();
			for (int i = 0; i < a.size(); i++)
			{
				if (!covers(a.get(i), b.get(i)))
				{
					return false;
				}
			}
			return true;
		}
		return was.equals(now);
	}

	private static <T> boolean fits(Gson gson, String name, JsonElement value, Class<T> type)
	{
		JsonObject one = new JsonObject();
		one.add(name, value);
		return parse(gson, one, type) != null;
	}

	private static <T> T parse(Gson gson, JsonElement json, Class<T> type)
	{
		try
		{
			return gson.fromJson(json, type);
		}
		catch (RuntimeException | StackOverflowError e)
		{
			return null;
		}
	}
}
