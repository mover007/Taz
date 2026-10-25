package com.taz.xpassist.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.util.TextUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hypixel's official item list (api.hypixel.net/v2/resources/skyblock/items, no key needed).
 * It includes the SkyBlock XP each item gives when donated to the museum.
 */
public final class HypixelItems {
	public static final class Item {
		public String id;
		public String name;
		public String tier;
		public String category;
		public String origin;
		public int museumXp;
		public String museumCategory;
		public String museumParent;
		public final Map<String, Integer> armorSets = new LinkedHashMap<>();
	}

	public final Map<String, Item> items = new LinkedHashMap<>();
	private final Map<String, String> idsByName = new HashMap<>();

	public static HypixelItems parse(String body) {
		HypixelItems result = new HypixelItems();
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		for (JsonElement element : root.getAsJsonArray("items")) {
			JsonObject json = element.getAsJsonObject();
			Item item = new Item();
			item.id = json.get("id").getAsString();
			item.name = json.has("name") ? TextUtil.strip(json.get("name").getAsString()) : TextUtil.titleCase(item.id);
			item.tier = json.has("tier") ? json.get("tier").getAsString() : null;
			item.category = json.has("category") ? json.get("category").getAsString() : null;
			item.origin = json.has("origin") ? json.get("origin").getAsString() : null;
			if (json.has("museum_data") && json.get("museum_data").isJsonObject()) {
				JsonObject museum = json.getAsJsonObject("museum_data");
				item.museumXp = museum.has("donation_xp") ? museum.get("donation_xp").getAsInt() : 0;
				item.museumCategory = museum.has("category") ? museum.get("category").getAsString() : null;
				if (museum.has("parent") && museum.get("parent").isJsonObject()) {
					for (Map.Entry<String, JsonElement> parent : museum.getAsJsonObject("parent").entrySet()) {
						item.museumParent = parent.getValue().getAsString();
					}
				}
				if (museum.has("armor_set_donation_xp") && museum.get("armor_set_donation_xp").isJsonObject()) {
					for (Map.Entry<String, JsonElement> set : museum.getAsJsonObject("armor_set_donation_xp").entrySet()) {
						item.armorSets.put(set.getKey(), set.getValue().getAsInt());
					}
				}
			}
			result.items.put(item.id, item);
			result.idsByName.putIfAbsent(TextUtil.normalize(item.name), item.id);
		}
		return result;
	}

	public Item get(String id) {
		return items.get(id);
	}

	public String name(String id) {
		Item item = items.get(id);
		return item != null ? item.name : null;
	}

	/** Looks an item up by its display name, also trying simple singular forms ("Pearls" -> "Pearl"). */
	public String idForName(String name) {
		String key = TextUtil.normalize(name);
		String[] candidates = {
			key,
			key.endsWith("ies") ? key.substring(0, key.length() - 3) + "y" : null,
			key.endsWith("es") ? key.substring(0, key.length() - 2) : null,
			key.endsWith("s") ? key.substring(0, key.length() - 1) : null,
		};
		for (String candidate : candidates) {
			if (candidate != null && idsByName.containsKey(candidate)) {
				return idsByName.get(candidate);
			}
		}
		return null;
	}
}
