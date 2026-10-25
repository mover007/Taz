package com.taz.xpassist.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.util.Http;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Your SkyBlock progress, from the Hypixel API (your own key) or from EliteBot (no key needed). */
public final class PlayerProfile {
	public String profileId;
	public String profileName;
	/** Where this came from, e.g. "Hypixel API" or "EliteBot". */
	public String source = "Hypixel API";
	public double skyblockXp;
	public final Set<String> craftedMinions = new HashSet<>();
	/** Museum item / armor set ids already donated, or null if the museum API is off. */
	public Set<String> museumDonated;
	/** Normalized contact ids (lowercase, letters and digits only). */
	public final Set<String> abiphoneContacts = new HashSet<>();
	/** False when the source has no Abiphone data (EliteBot). */
	public boolean abiphoneKnown = true;
	/** Accessory id -> rarity in your accessory bag, or null if the inventory API is off. */
	public Map<String, String> accessories;
	/** Pet type -> best rarity you own. */
	public final Map<String, String> bestPets = new HashMap<>();
	public JsonObject member = new JsonObject();
	public final long loadedAt = System.currentTimeMillis();
	/** False when this progress only comes from what the mod saw in game. */
	public boolean fromApi = true;
	/** True when museum data comes only from donations seen in game, so it is incomplete. */
	public boolean museumFromGameOnly;
	/** Consumable item id -> times used (from EliteBot or seen in game). */
	public Map<String, Integer> consumed = new HashMap<>();
	/** Collection id (e.g. "MYCEL") -> unlocked tier, or null when unknown. */
	public Map<String, Integer> collectionTiers;
	/** Slayer ("zombie", "spider", ...) -> level, or null when unknown. */
	public Map<String, Integer> slayerLevels;
	/** "MAGE" / "BARBARIAN" -> Crimson Isle reputation, or null when unknown. */
	public Map<String, Integer> reputation;
	/** Essence shop perk id -> tier bought, or null when unknown. */
	public Map<String, Integer> essencePerks;
	/** Purse + bank, or -1 when unknown. */
	public double coins = -1;
	/** Accessory bag upgrades bought from Jacobus, or -1 when unknown. */
	public int bagUpgrades = -1;
	/** Consumables whose "times used" really comes from the data source (others are a guess). */
	public final Set<String> knownConsumables = new HashSet<>();
	/** Collection id -> amount collected, or null when unknown. */
	public Map<String, Long> collectionAmounts;
	/** Bank upgrade keys you have (e.g. "BANK_UPGRADE_GOLD"), or null when unknown. */
	public Set<String> bankUpgrades;

	public static PlayerProfile fetch(UUID uuid, Rules rules, HypixelItems items) throws IOException {
		ModConfig config = ModConfig.get();
		Map<String, String> headers = Map.of("API-Key", config.profile.hypixelApiKey.trim());
		String undashed = uuid.toString().replace("-", "");
		JsonObject root = JsonParser.parseString(
			Http.getString(config.dataSources.hypixelApiBase + "/skyblock/profiles?uuid=" + undashed, headers)).getAsJsonObject();
		if (!root.has("profiles") || root.get("profiles").isJsonNull()) {
			throw new IOException("This account has no SkyBlock profiles");
		}
		JsonObject chosen = null;
		for (JsonElement element : root.getAsJsonArray("profiles")) {
			JsonObject profile = element.getAsJsonObject();
			String cute = profile.has("cute_name") ? profile.get("cute_name").getAsString() : "";
			if (!config.profile.profileName.isBlank()) {
				if (cute.equalsIgnoreCase(config.profile.profileName.trim())) {
					chosen = profile;
				}
			} else if (profile.has("selected") && profile.get("selected").getAsBoolean()) {
				chosen = profile;
			}
		}
		if (chosen == null) {
			if (!config.profile.profileName.isBlank()) {
				throw new IOException("No profile named " + config.profile.profileName);
			}
			chosen = root.getAsJsonArray("profiles").get(0).getAsJsonObject();
		}

		PlayerProfile result = new PlayerProfile();
		result.profileId = chosen.get("profile_id").getAsString();
		result.profileName = chosen.has("cute_name") ? chosen.get("cute_name").getAsString() : "?";
		JsonObject members = chosen.getAsJsonObject("members");
		if (members == null || !members.has(undashed)) {
			throw new IOException("You are not a member of profile " + result.profileName);
		}
		result.member = members.getAsJsonObject(undashed);
		result.readMember(rules, items);
		double bank = chosen.has("banking") && chosen.getAsJsonObject("banking").has("balance")
			? chosen.getAsJsonObject("banking").get("balance").getAsDouble() : 0;
		JsonElement purse = result.path("currencies.coin_purse");
		if (purse != null) {
			result.coins = purse.getAsDouble() + bank;
		}

		try {
			JsonObject museum = JsonParser.parseString(Http.getString(
				config.dataSources.hypixelApiBase + "/skyblock/museum?profile=" + result.profileId, headers)).getAsJsonObject();
			JsonObject museumMembers = museum.getAsJsonObject("members");
			if (museumMembers != null && museumMembers.has(undashed) && museumMembers.get(undashed).isJsonObject()) {
				JsonObject mine = museumMembers.getAsJsonObject(undashed);
				if (mine.has("items") && mine.get("items").isJsonObject()) {
					result.museumDonated = new HashSet<>(mine.getAsJsonObject("items").keySet());
				}
			}
		} catch (IOException e) {
			SkyblockXpAssist.LOGGER.warn("Could not load museum data", e);
		}
		return result;
	}

	private void readMember(Rules rules, HypixelItems items) {
		skyblockXp = number(path("leveling.experience"));

		JsonElement crafted = path("player_data.crafted_generators");
		if (crafted != null && crafted.isJsonArray()) {
			for (JsonElement element : crafted.getAsJsonArray()) {
				craftedMinions.add(element.getAsString());
			}
		}

		JsonElement contacts = path("nether_island_player_data.abiphone.active_contacts");
		if (contacts != null && contacts.isJsonArray()) {
			for (JsonElement element : contacts.getAsJsonArray()) {
				abiphoneContacts.add(TextUtil.normalize(element.getAsString()));
			}
		}
		JsonElement completed = path("leveling.completed_tasks");
		if (completed != null && completed.isJsonArray()) {
			for (JsonElement element : completed.getAsJsonArray()) {
				String task = element.getAsString();
				if (task.startsWith("ABIPHONE_")) {
					abiphoneContacts.add(TextUtil.normalize(task.substring("ABIPHONE_".length())));
				}
			}
		}

		JsonElement pets = path("pets_data.pets");
		if (pets != null && pets.isJsonArray()) {
			for (JsonElement element : pets.getAsJsonArray()) {
				JsonObject pet = element.getAsJsonObject();
				if (!pet.has("type") || !pet.has("tier")) {
					continue;
				}
				String held = pet.has("heldItem") && !pet.get("heldItem").isJsonNull() ? pet.get("heldItem").getAsString() : null;
				addPet(pet.get("type").getAsString(), pet.get("tier").getAsString(), held, rules);
			}
		}

		JsonElement bagUpgradesElement = path("accessory_bag_storage.bag_upgrades_purchased");
		if (bagUpgradesElement != null) {
			bagUpgrades = (int) number(bagUpgradesElement);
		}
		JsonElement collection = path("collection");
		if (collection != null && collection.isJsonObject()) {
			collectionAmounts = new HashMap<>();
			for (Map.Entry<String, JsonElement> entry : collection.getAsJsonObject().entrySet()) {
				collectionAmounts.put(entry.getKey(), (long) number(entry.getValue()));
			}
		}
		// The Hypixel API has every consumable counter, so all of them are known.
		for (JsonElement rule : rules.consumables()) {
			knownConsumables.add(rule.getAsJsonObject().get("item").getAsString());
		}

		JsonElement unlocked = path("player_data.unlocked_coll_tiers");
		if (unlocked != null && unlocked.isJsonArray()) {
			collectionTiers = new HashMap<>();
			for (JsonElement element : unlocked.getAsJsonArray()) {
				String entry = element.getAsString();
				int split = entry.lastIndexOf('_');
				if (split > 0) {
					try {
						collectionTiers.merge(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)), Math::max);
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		JsonElement bosses = path("slayer.slayer_bosses");
		if (bosses != null && bosses.isJsonObject()) {
			slayerLevels = new HashMap<>();
			for (Map.Entry<String, JsonElement> boss : bosses.getAsJsonObject().entrySet()) {
				int level = 0;
				JsonElement claimed = boss.getValue().isJsonObject() ? boss.getValue().getAsJsonObject().get("claimed_levels") : null;
				if (claimed != null && claimed.isJsonObject()) {
					for (String key : claimed.getAsJsonObject().keySet()) {
						try {
							level = Math.max(level, Integer.parseInt(key.replaceAll("\\D", "")));
						} catch (NumberFormatException ignored) {
						}
					}
				}
				slayerLevels.put(boss.getKey(), level);
			}
		}
		JsonElement mages = path("nether_island_player_data.mages_reputation");
		JsonElement barbarians = path("nether_island_player_data.barbarians_reputation");
		if (mages != null || barbarians != null) {
			reputation = new HashMap<>();
			reputation.put("MAGE", (int) number(mages));
			reputation.put("BARBARIAN", (int) number(barbarians));
		}
		JsonElement perks = path("player_data.perks");
		if (perks != null && perks.isJsonObject()) {
			essencePerks = new HashMap<>();
			for (Map.Entry<String, JsonElement> perk : perks.getAsJsonObject().entrySet()) {
				if (perk.getValue().isJsonPrimitive() && perk.getValue().getAsJsonPrimitive().isNumber()) {
					essencePerks.put(perk.getKey(), perk.getValue().getAsInt());
				}
			}
		}

		JsonElement bag = path("inventory.bag_contents.talisman_bag.data");
		if (bag != null && bag.isJsonPrimitive()) {
			try {
				accessories = readAccessories(bag.getAsString(), rules, items);
			} catch (Exception e) {
				SkyblockXpAssist.LOGGER.warn("Could not decode accessory bag", e);
			}
		}
	}

	private static Map<String, String> readAccessories(String base64, Rules rules, HypixelItems items) throws IOException {
		Map<String, String> result = new HashMap<>();
		byte[] bytes = Base64.getDecoder().decode(base64);
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
		ListTag list = root.getListOrEmpty("i");
		for (Tag entry : list) {
			if (!(entry instanceof CompoundTag stack)) {
				continue;
			}
			CompoundTag extra = stack.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
			String id = extra.getStringOr("id", "");
			if (!id.isEmpty()) {
				addAccessory(result, id, extra.getIntOr("rarity_upgrades", 0) > 0, rules, items);
			}
		}
		return result;
	}

	/** Keeps the best rarity per pet type; a Tier Boost counts as one rarity higher. */
	public void addPet(String type, String tier, String heldItem, Rules rules) {
		String rarity = "PET_ITEM_TIER_BOOST".equals(heldItem) ? rules.upgradeRarity(tier) : tier;
		String current = bestPets.get(type);
		if (current == null || rules.petScore(rarity) > rules.petScore(current)) {
			bestPets.put(type, rarity);
		}
	}

	/** Adds an accessory with its rarity (one higher when recombobulated). */
	public static void addAccessory(Map<String, String> result, String id, boolean recombobulated, Rules rules, HypixelItems items) {
		HypixelItems.Item item = items.get(id);
		String rarity = item != null && item.tier != null ? item.tier : "COMMON";
		if (recombobulated) {
			rarity = rules.upgradeRarity(rarity);
		}
		String previous = result.get(id);
		if (previous == null || rules.magicalPower(rarity) > rules.magicalPower(previous)) {
			result.put(id, rarity);
		}
	}

	/** Reads a dotted path such as "leveling.experience" from the member object. */
	public JsonElement path(String dotted) {
		JsonElement current = member;
		for (String part : dotted.split("\\.")) {
			if (current == null || !current.isJsonObject() || !current.getAsJsonObject().has(part)) {
				return null;
			}
			current = current.getAsJsonObject().get(part);
		}
		return current;
	}

	private static double number(JsonElement element) {
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsDouble() : 0;
	}

	public int intAt(String dotted) {
		return (int) number(path(dotted));
	}
}
