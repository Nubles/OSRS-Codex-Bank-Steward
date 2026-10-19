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
