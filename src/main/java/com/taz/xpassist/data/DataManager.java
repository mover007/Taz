package com.taz.xpassist.data;

import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.progress.LocalProgress;
import com.taz.xpassist.util.Http;
import com.taz.xpassist.xp.TripPlanner;
import com.taz.xpassist.xp.XpCalculator;
import com.taz.xpassist.xp.XpResult;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Downloads, caches and refreshes all data on a background thread. The game thread only reads. */
public final class DataManager {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SkyblockXpAssist-Data");
		thread.setDaemon(true);
		return thread;
	});
	private static final long ITEMS_MAX_AGE = 12 * 60 * 60 * 1000L;

	private static volatile Rules rules;
	private static volatile NeuRepo neu;
	private static volatile HypixelItems items;
	private static volatile CollectionIndex collections;
	private static volatile OwnedItems owned = OwnedItems.EMPTY;
	private static volatile TripPlanner.Plan plan;
	private static volatile Prices prices;
	private static volatile PlayerProfile profile;
	private static volatile PlayerProfile effectiveProfile;
	private static volatile XpResult result;
	private static volatile String status = "Starting up";
	private static volatile String error;
	private static volatile boolean busy;
	private static volatile long lastProfileAttempt;
	private static volatile long lastPriceAttempt;
	private static volatile long lastRefreshStart;

	private DataManager() {
	}

	public static Rules rules() {
		return rules;
	}

	public static NeuRepo neu() {
		return neu;
	}

	public static HypixelItems items() {
		return items;
	}

	public static PlayerProfile profile() {
		return profile;
	}

	/** API data merged with what was seen in game; null when nothing is known. */
	public static PlayerProfile effectiveProfile() {
		return effectiveProfile;
	}

	public static Prices prices() {
		return prices;
	}

	public static XpResult result() {
		return result;
	}

	/** The cheapest way to the next few levels, grouped into shopping stops (the Level plan screen). */
	public static TripPlanner.Plan plan() {
		return plan;
	}

	public static OwnedItems owned() {
		return owned;
	}

	/** Called when your inventory changes; items you carry make their upgrades free. */
	public static void setOwned(OwnedItems now) {
		if (!now.equals(owned)) {
			owned = now;
			recompute();
		}
	}

	/** Rebuilds the plan for a different number of levels without recomputing prices. */
	public static void replan() {
		EXECUTOR.execute(() -> {
			if (result != null && neu != null) {
				plan = TripPlanner.build(result, neu, rules, ModConfig.get().hud.planLevels);
			}
		});
	}

	public static String status() {
		return status;
	}

	public static String error() {
		return error;
	}

	public static boolean busy() {
		return busy;
	}

	private static Path cache(String name) {
		return ModConfig.dir().resolve("cache").resolve(name);
	}

	/**
	 * Refreshes everything that is stale (or everything when {@code force}). {@code done} runs on the
	 * game thread afterwards with an error message, or null on success.
	 */
	public static void refresh(boolean force, Consumer<String> done) {
		lastRefreshStart = System.currentTimeMillis();
		EXECUTOR.execute(() -> {
			busy = true;
			String failure = null;
			try {
				error = null;
				status = "Loading rules";
				rules = Rules.load();
				loadRepo(force);
				loadItems(force);
				loadPrices(force);
				failure = loadProfile(force);
				recomputeNow();
				status = "Ready";
			} catch (Exception e) {
				SkyblockXpAssist.LOGGER.error("Refresh failed", e);
				failure = e.getMessage() != null ? e.getMessage() : e.toString();
				error = failure;
				status = "Error";
			} finally {
				busy = false;
			}
			if (done != null) {
				String message = failure;
				Minecraft.getInstance().execute(() -> done.accept(message));
			}
		});
	}

	/** Makes the next automatic refresh reload your profile after {@code delayMillis} (the API lags behind the game). */
	public static void scheduleProfileRefresh(long delayMillis) {
		ModConfig config = ModConfig.get();
		if (!profileSourceEnabled()) {
			return;
		}
		long due = System.currentTimeMillis() + delayMillis - config.dataSources.profileRefreshMinutes * 60_000L;
		if (due < lastProfileAttempt) {
			lastProfileAttempt = due;
		}
	}

	/** Re-sorts the list with the current data, e.g. after hiding a task or toggling a category. */
	public static void recompute() {
		EXECUTOR.execute(DataManager::recomputeNow);
	}

	/** Called every client tick; refreshes prices and profile on their configured intervals. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (busy || now - lastRefreshStart < 60_000L) {
			return;
		}
		ModConfig config = ModConfig.get();
		boolean pricesDue = now - lastPriceAttempt > config.dataSources.priceRefreshMinutes * 60_000L;
		boolean profileDue = profileSourceEnabled() && now - lastProfileAttempt > config.dataSources.profileRefreshMinutes * 60_000L;
		if (pricesDue || profileDue) {
			refresh(false, null);
		}
	}

	private static void recomputeNow() {
		if (neu == null || items == null || prices == null || rules == null) {
			return;
		}
		try {
			PlayerProfile effective = LocalProgress.merge(profile);
			effectiveProfile = effective;
			result = XpCalculator.compute(neu, items, prices, effective, rules, ModConfig.get(), collections, owned);
			plan = TripPlanner.build(result, neu, rules, ModConfig.get().hud.planLevels);
		} catch (Exception e) {
			SkyblockXpAssist.LOGGER.error("Could not build the XP list", e);
			error = "Could not build the XP list: " + e;
		}
	}

	private static void loadRepo(boolean force) throws IOException {
		ModConfig config = ModConfig.get();
		Path zip = cache("neu-repo.zip");
		Path shaFile = cache("neu-repo.sha");
		Path checkedFile = cache("neu-repo.checked");
		String localSha = Files.exists(shaFile) ? Files.readString(shaFile).trim() : null;
		long checkedAt = 0;
		if (Files.exists(checkedFile)) {
			try {
				checkedAt = Long.parseLong(Files.readString(checkedFile).trim());
			} catch (NumberFormatException ignored) {
			}
		}
		boolean haveZip = Files.exists(zip) && localSha != null;
		boolean due = force || !haveZip || System.currentTimeMillis() - checkedAt > config.dataSources.repoCheckHours * 3_600_000L;
		String sha = localSha;
		if (due) {
			status = "Checking the NEU repo for updates";
			String repo = config.dataSources.neuRepoOwner + "/" + config.dataSources.neuRepoName;
			try {
				String remoteSha = Http.getString("https://api.github.com/repos/" + repo + "/commits/" + config.dataSources.neuRepoBranch,
					Map.of("Accept", "application/vnd.github.sha")).trim();
				if (!remoteSha.equals(localSha) || !Files.exists(zip)) {
					status = "Downloading the NEU repo (about 20 MB)";
					Http.download("https://github.com/" + repo + "/archive/" + remoteSha + ".zip", zip);
					Files.writeString(shaFile, remoteSha);
				}
				sha = remoteSha;
				Files.writeString(checkedFile, String.valueOf(System.currentTimeMillis()));
			} catch (IOException e) {
				if (!haveZip) {
					// GitHub API can rate limit; the branch archive does not need it.
					status = "Downloading the NEU repo (about 20 MB)";
					Http.download("https://github.com/" + repo + "/archive/refs/heads/" + config.dataSources.neuRepoBranch + ".zip", zip);
					sha = config.dataSources.neuRepoBranch + "@" + System.currentTimeMillis();
					Files.writeString(shaFile, sha);
				} else {
					SkyblockXpAssist.LOGGER.warn("Could not check the NEU repo, using the cached copy", e);
				}
			}
		}
		if (neu == null || !neu.commit.equals(sha)) {
			status = "Reading the NEU repo";
			neu = NeuRepo.load(zip, sha);
			SkyblockXpAssist.LOGGER.info("Loaded NEU repo {}: {} NPCs, {} recipes", sha, neu.npcs.size(), neu.craftRecipes.size());
		}
	}

	private static void loadItems(boolean force) throws IOException {
		Path file = cache("items.json");
		boolean fresh = Files.exists(file) && System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < ITEMS_MAX_AGE;
		if (force || !fresh) {
			status = "Downloading Hypixel item data";
			try {
				Http.download(ModConfig.get().dataSources.hypixelApiBase + "/resources/skyblock/items", file);
			} catch (IOException e) {
				if (!Files.exists(file)) {
					throw e;
				}
				SkyblockXpAssist.LOGGER.warn("Could not refresh Hypixel items, using the cached copy", e);
			}
			items = null;
		}
		if (items == null) {
			items = HypixelItems.parse(Files.readString(file, StandardCharsets.UTF_8));
		}
		Path collectionsFile = cache("collections.json");
		boolean collectionsFresh = Files.exists(collectionsFile)
			&& System.currentTimeMillis() - Files.getLastModifiedTime(collectionsFile).toMillis() < ITEMS_MAX_AGE;
		if (force || !collectionsFresh || collections == null) {
			try {
				if (force || !collectionsFresh) {
					Http.download(ModConfig.get().dataSources.hypixelApiBase + "/resources/skyblock/collections", collectionsFile);
				}
				collections = CollectionIndex.parse(Files.readString(collectionsFile, StandardCharsets.UTF_8));
			} catch (IOException e) {
				SkyblockXpAssist.LOGGER.warn("Could not load collections; recipe unlock checks are off", e);
			}
		}
	}

	private static void loadPrices(boolean force) throws IOException {
		ModConfig config = ModConfig.get();
		boolean due = force || prices == null || System.currentTimeMillis() - lastPriceAttempt > config.dataSources.priceRefreshMinutes * 60_000L;
		if (!due) {
			return;
		}
		lastPriceAttempt = System.currentTimeMillis();
		status = "Downloading prices";
		Path marketFile = cache("prices.json");
		Path bazaarFile = cache("bazaar.json");
		try {
			Http.download(config.dataSources.pricesUrl, marketFile);
		} catch (IOException e) {
			SkyblockXpAssist.LOGGER.warn("Could not refresh auction prices", e);
		}
		try {
			Http.download(config.dataSources.hypixelApiBase + "/skyblock/bazaar", bazaarFile);
		} catch (IOException e) {
			SkyblockXpAssist.LOGGER.warn("Could not refresh bazaar prices", e);
		}
		if (!Files.exists(marketFile) && !Files.exists(bazaarFile)) {
			throw new IOException("Could not download any prices");
		}
		prices = Prices.parse(
			Files.exists(marketFile) ? Files.readString(marketFile, StandardCharsets.UTF_8) : null,
			Files.exists(bazaarFile) ? Files.readString(bazaarFile, StandardCharsets.UTF_8) : null);
	}

	/** Returns an error message, or null. Profile problems do not stop the rest of the list. */
	private static boolean profileSourceEnabled() {
		ModConfig config = ModConfig.get();
		return config.hasApiKey() || config.profile.useEliteBot;
	}

	private static String loadProfile(boolean force) {
		ModConfig config = ModConfig.get();
		if (!profileSourceEnabled()) {
			profile = null;
			return null;
		}
		boolean due = force || profile == null || System.currentTimeMillis() - lastProfileAttempt > config.dataSources.profileRefreshMinutes * 60_000L;
		if (!due) {
			return null;
		}
		lastProfileAttempt = System.currentTimeMillis();
		status = "Loading your SkyBlock profile";
		UUID uuid = Minecraft.getInstance().getUser().getProfileId();
		boolean hypixel = config.hasApiKey();
		try {
			if (hypixel) {
				profile = PlayerProfile.fetch(uuid, rules, items);
			} else {
				// Ask for the profile you are playing on (from the tab list), falling back to the selected one.
				String wanted = !config.profile.profileName.isBlank() ? config.profile.profileName
					: !LocalProgress.activeProfile().equals("default") ? LocalProgress.activeProfile() : "";
				profile = EliteProfile.fetch(uuid, rules, items, neu, wanted);
			}
			if (config.profile.profileName.isBlank() && LocalProgress.activeProfile().equals("default")) {
				LocalProgress.setActiveProfile(profile.profileName);
			}
			return null;
		} catch (Http.HttpStatusException e) {
			String name = hypixel ? "Hypixel API" : "EliteBot";
			String message = switch (e.status) {
				case 403 -> hypixel ? "Hypixel rejected the API key. Development keys expire after 3 days; make a new one at developer.hypixel.net"
					: "EliteBot refused the request (403)";
				case 404 -> name + " has no SkyBlock data for this account yet";
				case 429 -> name + " rate limit hit, will retry later";
				default -> name + " error " + e.status;
			};
			error = message;
			return message;
		} catch (Exception e) {
			SkyblockXpAssist.LOGGER.warn("Could not load profile", e);
			error = "Could not load your profile: " + e.getMessage();
			return error;
		}
	}
}
