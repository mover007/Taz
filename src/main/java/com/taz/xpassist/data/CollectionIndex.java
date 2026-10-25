package com.taz.xpassist.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.util.TextUtil;

import java.util.HashMap;
import java.util.Map;

/** Collection names and ids from Hypixel's collections resource ("Mycelium" -> "MYCEL"). */
public final class CollectionIndex {
	private final Map<String, String> idsByName = new HashMap<>();
	private final Map<String, String> namesById = new HashMap<>();

	public static CollectionIndex parse(String body) {
		CollectionIndex index = new CollectionIndex();
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		if (!root.has("collections")) {
			return index;
		}
		for (Map.Entry<String, JsonElement> category : root.getAsJsonObject("collections").entrySet()) {
			JsonObject items = category.getValue().getAsJsonObject().getAsJsonObject("items");
			if (items == null) {
				continue;
			}
			for (Map.Entry<String, JsonElement> item : items.entrySet()) {
				String name = item.getValue().getAsJsonObject().get("name").getAsString();
				index.idsByName.put(TextUtil.normalize(name), item.getKey());
				index.namesById.put(item.getKey(), name);
			}
		}
		return index;
	}

	public String idForName(String name) {
		return idsByName.get(TextUtil.normalize(name));
	}

	public String name(String id) {
		return namesById.getOrDefault(id, id);
	}
}
