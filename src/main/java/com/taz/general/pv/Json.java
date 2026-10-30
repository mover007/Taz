package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Reading SkyCrypt's JSON without trusting its shape: a missing or wrong-typed field gives an empty value. */
final class Json {
	private Json() {
	}

	/** The element at this path of keys, or null. */
	static JsonElement at(JsonElement e, String... path) {
		for (String key : path) {
			if (e == null || !e.isJsonObject()) return null;
			e = e.getAsJsonObject().get(key);
		}
		return e == null || e.isJsonNull() ? null : e;
	}

	static JsonObject obj(JsonElement e, String... path) {
		JsonElement v = at(e, path);
		return v != null && v.isJsonObject() ? v.getAsJsonObject() : new JsonObject();
	}

	static JsonArray arr(JsonElement e, String... path) {
		JsonElement v = at(e, path);
		return v != null && v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
	}

	static double num(JsonElement e, String... path) {
		JsonElement v = at(e, path);
		try {
			return v != null && v.isJsonPrimitive() ? v.getAsDouble() : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	static String str(JsonElement e, String... path) {
		JsonElement v = at(e, path);
		return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
	}

	static boolean bool(JsonElement e, String... path) {
		JsonElement v = at(e, path);
		return v != null && v.isJsonPrimitive() && v.getAsBoolean();
	}
}
