package com.taz.xpassist.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.PlayerProfile;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Progress seen in game (menus, chat, action bar), saved per SkyBlock profile in
 * config/taz/xpassist/progress.json. It is merged with the Hypixel API data, so the list
 * updates the moment you do something instead of waiting for the API.
 */
public final class LocalProgress {
	public static final class Profile {
		public double skyblockXp = -1;
		public long xpUpdatedAt;
		public Set<String> museumDonated = new LinkedHashSet<>();
		/** API style ids, e.g. "FLOWER_11". */
		public Set<String> craftedMinions = new LinkedHashSet<>();
		/** Normalized contact names, e.g. "madredstoneengineer". */
		public Set<String> abiphoneContacts = new LinkedHashSet<>();
		/** Consumable item id -> times used. */
		public Map<String, Integer> consumed = new LinkedHashMap<>();
		/** Bank upgrade keys you have, seen in /skyblockxp > Core > Bank Upgrades; null until that menu is opened. */
		public Set<String> bankUpgrades;
		/** Every task seen in the /skyblockxp task menus, keyed by "menu|task". */
		public Map<String, MenuTask> menuTasks = new LinkedHashMap<>();
	}

	/** One task from Hypixel's SkyBlock Leveling menus, as last seen. */
	public static final class MenuTask {
		public String menu;
		public String name;
		/** SkyBlock XP mentioned on the item (what it gives or what is left), or -1 if none was shown. */
		public int xp = -1;
		public String progress;
		public boolean done;
		public long seenAt;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, Profile> profiles = new LinkedHashMap<>();
	private static String activeProfile = "default";

	private LocalProgress() {
	}

	private static Path file() {
		return ModConfig.dir().resolve("progress.json");
	}

	public static synchronized void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, Profile> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, Profile>>() {
			}.getType());
			if (loaded != null) {
				profiles = loaded;
			}
		} catch (Exception e) {
			SkyblockXpAssist.LOGGER.error("Could not read progress.json", e);
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(ModConfig.dir());
			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(profiles, writer);
			}
		} catch (Exception e) {
			SkyblockXpAssist.LOGGER.error("Could not save progress.json", e);
		}
	}

	/** The SkyBlock profile name from the tab list ("Profile: Pomegranate"), or from the API. */
	public static synchronized void setActiveProfile(String name) {
		if (name != null && !name.isBlank()) {
			activeProfile = name.trim();
		}
	}

	public static synchronized String activeProfile() {
		return activeProfile;
	}

	public static synchronized void forgetCurrent() {
		profiles.remove(activeProfile.toLowerCase());
		save();
	}

	public static synchronized Profile current() {
		return profiles.computeIfAbsent(activeProfile.toLowerCase(), k -> new Profile());
	}

	/** Incomplete tasks seen in the /skyblockxp menus, for the "Other XP" tab. */
	public static synchronized java.util.List<MenuTask> openMenuTasks() {
		java.util.List<MenuTask> out = new java.util.ArrayList<>();
		for (MenuTask task : current().menuTasks.values()) {
			if (!task.done) {
				out.add(task);
			}
		}
		return out;
	}

	public static synchronized boolean hasData() {
		Profile p = profiles.get(activeProfile.toLowerCase());
		return p != null && (p.skyblockXp >= 0 || !p.museumDonated.isEmpty() || !p.craftedMinions.isEmpty()
			|| !p.abiphoneContacts.isEmpty() || !p.consumed.isEmpty());
	}

	/**
	 * Combines API data (may be null) with what was seen in game. Returns null only when we know
	 * nothing at all about the player.
	 */
	public static synchronized PlayerProfile merge(PlayerProfile api) {
		if (api == null && !hasData()) {
			return null;
		}
		Profile local = current();
		PlayerProfile merged = new PlayerProfile();
		if (api != null) {
			merged.profileId = api.profileId;
			merged.profileName = api.profileName;
			merged.skyblockXp = api.skyblockXp;
			merged.craftedMinions.addAll(api.craftedMinions);
			merged.museumDonated = api.museumDonated != null ? new java.util.HashSet<>(api.museumDonated) : null;
			merged.abiphoneContacts.addAll(api.abiphoneContacts);
			merged.accessories = api.accessories;
			merged.bestPets.putAll(api.bestPets);
			merged.member = api.member;
			merged.fromApi = true;
			merged.source = api.source;
			merged.abiphoneKnown = api.abiphoneKnown;
			merged.consumed.putAll(api.consumed);
			merged.collectionTiers = api.collectionTiers;
			merged.slayerLevels = api.slayerLevels;
			merged.reputation = api.reputation;
			merged.essencePerks = api.essencePerks;
			merged.coins = api.coins;
			merged.bagUpgrades = api.bagUpgrades;
			merged.knownConsumables.addAll(api.knownConsumables);
			merged.collectionAmounts = api.collectionAmounts;
			merged.bankUpgrades = api.bankUpgrades != null ? new java.util.HashSet<>(api.bankUpgrades) : null;
		} else {
			merged.profileName = activeProfile;
			merged.fromApi = false;
			merged.source = "in-game";
		}
		// Live XP from the action bar / tab list / menus is newer than the API unless the API loaded later.
		if (local.skyblockXp >= 0 && (api == null || local.xpUpdatedAt >= api.loadedAt || local.skyblockXp > api.skyblockXp)) {
			merged.skyblockXp = local.skyblockXp;
		}
		merged.craftedMinions.addAll(local.craftedMinions);
		if (!local.museumDonated.isEmpty()) {
			if (merged.museumDonated == null) {
				merged.museumDonated = new java.util.HashSet<>();
				merged.museumFromGameOnly = true;
			}
			merged.museumDonated.addAll(local.museumDonated);
		}
		merged.abiphoneContacts.addAll(local.abiphoneContacts);
		for (Map.Entry<String, Integer> entry : local.consumed.entrySet()) {
			merged.consumed.merge(entry.getKey(), entry.getValue(), Math::max);
			// Counts read from the Consumable Items menu (or seen being used) are real.
			merged.knownConsumables.add(entry.getKey());
		}
		if (local.bankUpgrades != null) {
			merged.bankUpgrades = new java.util.HashSet<>(local.bankUpgrades);
		}
		return merged;
	}
}
