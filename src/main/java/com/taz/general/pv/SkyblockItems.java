package com.taz.general.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.xpassist.util.Http;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Hypixel's list of SkyBlock items (api.hypixel.net/v2/resources/skyblock/items, no key needed): each item's category, the
 * essence its stars cost, its gemstone slots and prestige costs. The networth prices upgrades with it. Kept a day in
 * config/taz/prices/items.json.
 */
public final class SkyblockItems {
	private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final long MAX_AGE_MS = 24 * 3_600_000L;
	private static volatile Map<String, JsonObject> items;
	/** Test hook: items to use instead of downloading. */
	public static volatile Map<String, JsonObject> testItems;

	private SkyblockItems() {
	}

	/** The items by id; empty when they can't be had. Blocking: call off the game thread. */
	static synchronized Map<String, JsonObject> get() {
		if (testItems != null) return testItems;
		if (items != null) return items;
		Map<String, JsonObject> out = new HashMap<>();
		Path file = Taz.CONFIG_DIR.resolve("prices").resolve("items.json");
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file) || System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > MAX_AGE_MS) {
				try {
					Http.download(URL, file);
				} catch (Exception e) {
					Taz.LOGGER.warn("Networth: couldn't download Hypixel's item list ({})", e.toString());
				}
			}
			if (Files.exists(file)) {
				JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				for (JsonElement e : Json.arr(json, "items")) {
					if (e.isJsonObject()) out.put(Json.str(e, "id"), e.getAsJsonObject());
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Networth: couldn't read Hypixel's item list", e);
		}
		if (!out.isEmpty()) items = out;
		return out;
	}
}
