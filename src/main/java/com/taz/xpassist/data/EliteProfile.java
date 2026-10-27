package com.taz.xpassist.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.util.Http;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loads your progress from EliteBot (api.elitebot.dev), a public SkyBlock stats service that mirrors the Hypixel API,
 * so no API key is needed.
 */
public final class EliteProfile {
	private EliteProfile() {
	}

	public static PlayerProfile fetch(UUID uuid, Rules rules, HypixelItems items, NeuRepo neu, String wantedProfile) throws IOException {
		ModConfig config = ModConfig.get();
		String base = config.profile.eliteApiBase.replaceAll("/+$", "");
		String player = uuid.toString().replace("-", "");

		String profilePath = "Selected";
		if (wantedProfile != null && !wantedProfile.isBlank()) {
			JsonArray profiles = JsonParser.parseString(Http.getString(base + "/Profiles/" + player)).getAsJsonArray();
			profilePath = null;
			for (JsonElement element : profiles) {
				JsonObject profile = element.getAsJsonObject();
				if (profile.get("profileName").getAsString().equalsIgnoreCase(wantedProfile.trim())) {
					profilePath = profile.get("profileId").getAsString();
				}
			}
			if (profilePath == null) {
				profilePath = "Selected";
			}
		}
		JsonObject member = JsonParser.parseString(Http.getString(base + "/Profile/" + player + "/" + profilePath)).getAsJsonObject();

		PlayerProfile result = new PlayerProfile();
		result.source = "EliteBot";
		result.abiphoneKnown = false;
		result.profileId = string(member, "profileId");
		result.profileName = string(member, "profileName");
		result.skyblockXp = member.has("skyblockXp") ? member.get("skyblockXp").getAsDouble() : 0;

		// Crafted minions: type -> bit mask where bit N means tier N was crafted.
		if (member.has("craftedMinions") && member.get("craftedMinions").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : member.getAsJsonObject("craftedMinions").entrySet()) {
				long mask = entry.getValue().getAsLong();
				for (int tier = 1; tier < 31; tier++) {
					if ((mask >> tier & 1) == 1) {
						result.craftedMinions.add(entry.getKey() + "_" + tier);
					}
				}
			}
		}

		if (member.has("pets") && member.get("pets").isJsonArray()) {
			for (JsonElement element : member.getAsJsonArray("pets")) {
				JsonObject pet = element.getAsJsonObject();
				if (pet.has("type") && pet.has("tier")) {
					result.addPet(string(pet, "type"), string(pet, "tier"), pet.has("heldItem") && !pet.get("heldItem").isJsonNull() ? string(pet, "heldItem") : null, rules);
				}
			}
		}

		// What you have unlocked: collection tiers, slayer levels, Crimson Isle reputation, essence perks, coins.
		if (member.has("collectionTiers") && member.get("collectionTiers").isJsonObject()) {
			result.collectionTiers = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : member.getAsJsonObject("collectionTiers").entrySet()) {
				result.collectionTiers.put(entry.getKey(), entry.getValue().getAsInt());
			}
		}
		JsonObject bosses = path(member, "stats.slayer.bosses");
		if (bosses != null) {
			result.slayerLevels = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : bosses.entrySet()) {
				if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("level")) {
					result.slayerLevels.put(entry.getKey().toLowerCase(), entry.getValue().getAsJsonObject().get("level").getAsInt());
				}
			}
		}
		JsonObject crimson = path(member, "stats.crimsonIsle");
		if (crimson != null) {
			result.reputation = new HashMap<>();
			if (crimson.has("magesReputation")) {
				result.reputation.put("MAGE", crimson.get("magesReputation").getAsInt());
			}
			if (crimson.has("barbariansReputation")) {
				result.reputation.put("BARBARIAN", crimson.get("barbariansReputation").getAsInt());
			}
		}
		JsonObject perks = path(member, "unparsed.perks");
		if (perks != null) {
			result.essencePerks = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : perks.entrySet()) {
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
					result.essencePerks.put(entry.getKey(), entry.getValue().getAsInt());
				}
			}
		}
		JsonObject accessoryStats = path(member, "stats.accessories");
		if (accessoryStats != null && accessoryStats.has("bagUpgradesPurchased")) {
			result.bagUpgrades = accessoryStats.get("bagUpgradesPurchased").getAsInt();
		}
		if (member.has("collections") && member.get("collections").isJsonObject()) {
			result.collectionAmounts = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : member.getAsJsonObject("collections").entrySet()) {
				result.collectionAmounts.put(entry.getKey(), entry.getValue().getAsLong());
			}
		}
		if (member.has("purse")) {
			result.coins = member.get("purse").getAsDouble() + (member.has("bankBalance") ? member.get("bankBalance").getAsDouble() : 0);
		}

		JsonObject consumed = path(member, "unparsed.consumed");
		if (consumed != null) {
			putConsumed(result, "WRIGGLING_LARVA", consumed, "wriggling_larva");
			putConsumed(result, "REFINED_DARK_CACAO_TRUFFLE", consumed, "refined_dark_cacao_truffles");
		}
		JsonObject chocolate = path(member, "chocolateFactory");
		if (chocolate != null && chocolate.has("refinedTrufflesConsumed")) {
			result.consumed.merge("REFINED_DARK_CACAO_TRUFFLE", chocolate.get("refinedTrufflesConsumed").getAsInt(), Math::max);
		}

		JsonObject apiSettings = path(member, "api");
		boolean museumApi = apiSettings == null || !apiSettings.has("museum") || apiSettings.get("museum").getAsBoolean();
		boolean inventoryApi = apiSettings == null || !apiSettings.has("inventories") || apiSettings.get("inventories").getAsBoolean();
		Map<String, String> inventoryIds = new HashMap<>();
		if (member.has("inventories") && member.get("inventories").isJsonArray()) {
			for (JsonElement element : member.getAsJsonArray("inventories")) {
				JsonObject inventory = element.getAsJsonObject();
				inventoryIds.put(string(inventory, "name"), string(inventory, "id"));
			}
		}
		String inventoryBase = base + "/profile/" + player + "/" + result.profileId + "/inventories/";

		if (museumApi && inventoryIds.containsKey("museum")) {
			try {
				JsonObject museum = JsonParser.parseString(Http.getString(inventoryBase + inventoryIds.get("museum"))).getAsJsonObject();
				Set<String> donated = new HashSet<>();
				Map<String, String> mapped = new HashMap<>();
				if (neu.museum.has("mapped_ids") && neu.museum.get("mapped_ids").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : neu.museum.getAsJsonObject("mapped_ids").entrySet()) {
						mapped.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
				for (Map.Entry<String, JsonElement> entry : itemsOf(museum).entrySet()) {
					// Slot keys are the museum ids: "END_SWORD:0", or "SKELETON_MASTER:2" for a piece of a set.
					String key = entry.getKey();
					donated.add(key.contains(":") ? key.substring(0, key.lastIndexOf(':')) : key);
					if (entry.getValue().isJsonObject()) {
						String id = string(entry.getValue().getAsJsonObject(), "skyblockId");
						if (id != null) {
							donated.add(id);
							donated.add(mapped.getOrDefault(id, id));
						}
					}
				}
				result.museumDonated = donated;
			} catch (IOException e) {
				SkyblockXpAssist.LOGGER.warn("Could not load museum from EliteBot", e);
			}
		}

		if (inventoryApi && inventoryIds.containsKey("talisman_bag")) {
			try {
				JsonObject bag = JsonParser.parseString(Http.getString(inventoryBase + inventoryIds.get("talisman_bag"))).getAsJsonObject();
				Map<String, String> accessories = new HashMap<>();
				for (JsonElement value : itemsOf(bag).values()) {
					if (!value.isJsonObject()) {
						continue;
					}
					JsonObject item = value.getAsJsonObject();
					String id = string(item, "skyblockId");
					if (id == null) {
						continue;
					}
					JsonObject attributes = item.has("attributes") && item.get("attributes").isJsonObject() ? item.getAsJsonObject("attributes") : null;
					boolean recombobulated = attributes != null && attributes.has("rarity_upgrades")
						&& !"0".equals(attributes.get("rarity_upgrades").getAsString());
					PlayerProfile.addAccessory(accessories, id, recombobulated, rules, items);
				}
				result.accessories = accessories;
			} catch (IOException e) {
				SkyblockXpAssist.LOGGER.warn("Could not load accessory bag from EliteBot", e);
			}
		}
		return result;
	}

	private static Map<String, JsonElement> itemsOf(JsonObject inventory) {
		Map<String, JsonElement> out = new HashMap<>();
		if (inventory.has("items") && inventory.get("items").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : inventory.getAsJsonObject("items").entrySet()) {
				if (!entry.getValue().isJsonNull()) {
					out.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return out;
	}

	private static void putConsumed(PlayerProfile result, String itemId, JsonObject consumed, String key) {
		if (consumed.has(key) && consumed.get(key).isJsonPrimitive()) {
			result.consumed.merge(itemId, consumed.get(key).getAsInt(), Math::max);
			result.knownConsumables.add(itemId);
		}
	}

	private static JsonObject path(JsonObject root, String dotted) {
		JsonElement current = root;
		for (String part : dotted.split("\\.")) {
			if (current == null || !current.isJsonObject() || !current.getAsJsonObject().has(part)) {
				return null;
			}
			current = current.getAsJsonObject().get(part);
		}
		return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
	}

	private static String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}
}
