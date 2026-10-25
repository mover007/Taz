package com.taz.xpassist.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.util.Http;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Game rules that no API exposes. Bundled, but replaceable by a local file or a URL. */
public final class Rules {
	public final JsonObject json;

	private Rules(JsonObject json) {
		this.json = json;
	}

	public static Rules load() {
		ModConfig config = ModConfig.get();
		if (config.dataSources.rulesUrl != null && !config.dataSources.rulesUrl.isBlank()) {
			try {
				return new Rules(JsonParser.parseString(Http.getString(config.dataSources.rulesUrl)).getAsJsonObject());
			} catch (Exception e) {
				SkyblockXpAssist.LOGGER.warn("Could not load rules from {}, falling back", config.dataSources.rulesUrl, e);
			}
		}
		Path local = ModConfig.dir().resolve("rules.json");
		if (Files.exists(local)) {
			try {
				return new Rules(JsonParser.parseString(Files.readString(local, StandardCharsets.UTF_8)).getAsJsonObject());
			} catch (Exception e) {
				SkyblockXpAssist.LOGGER.warn("Could not read {}, using bundled rules", local, e);
			}
		}
		return bundled();
	}

	public static Rules bundled() {
		try (InputStream in = Rules.class.getResourceAsStream("/assets/taz/xpassist/rules.json")) {
			return new Rules(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (Exception e) {
			throw new IllegalStateException("Bundled rules.json is missing", e);
		}
	}

	public JsonObject object(String key) {
		return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
	}

	public JsonArray array(String key) {
		return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray();
	}

	public int xpPerLevel() {
		return json.has("xp_per_level") ? json.get("xp_per_level").getAsInt() : 100;
	}

	public List<String> rarityOrder() {
		List<String> out = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("rarity_order")) {
			out.add(element.getAsString());
		}
		return out;
	}

	/** Rarity after a recombobulator, e.g. RARE -> EPIC. */
	public String upgradeRarity(String rarity) {
		List<String> order = rarityOrder();
		int index = order.indexOf(rarity);
		if (index < 0) {
			return rarity;
		}
		if (rarity.equals("SPECIAL")) {
			return "VERY_SPECIAL";
		}
		if (index + 1 < order.size() && !order.get(index + 1).equals("SPECIAL")) {
			return order.get(index + 1);
		}
		return rarity;
	}

	public int magicalPower(String rarity) {
		JsonObject table = json.getAsJsonObject("magical_power");
		return rarity != null && table.has(rarity) ? table.get(rarity).getAsInt() : 0;
	}

	public int petScore(String rarity) {
		JsonObject table = json.getAsJsonObject("pet_score");
		return rarity != null && table.has(rarity) ? table.get(rarity).getAsInt() : 0;
	}

	public JsonObject fallbackXp() {
		return json.getAsJsonObject("fallback_xp");
	}

	public JsonArray consumables() {
		return json.has("consumables") ? json.getAsJsonArray("consumables") : new JsonArray();
	}

	public String areaAlias(String normalizedArea) {
		JsonObject aliases = json.getAsJsonObject("area_aliases");
		if (aliases != null && aliases.has(normalizedArea)) {
			return aliases.get(normalizedArea).getAsString().toLowerCase(Locale.ROOT);
		}
		return null;
	}
}
