package com.taz.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.hud.HudPanel;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * First start of Taz: copies the data and settings of the separate mods it replaces
 * (Dungeon Run Predictor, Skyblock XP Assist) so nothing is lost. The old folders are left as they were.
 */
public final class Migration {
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir();
	private static final Path OLD_DUNGEONS = CONFIG.resolve("dungeonpredictor");
	private static final Path OLD_XP = CONFIG.resolve("skyblockxpassist");

	private Migration() {
	}

	/** Run history, triggers, XP progress and caches → config/taz/dungeons and config/taz/xpassist. */
	public static void copyOldData() {
		copyTree(OLD_DUNGEONS, Taz.CONFIG_DIR.resolve("dungeons"));
		copyTree(OLD_XP, Taz.CONFIG_DIR.resolve("xpassist"));
	}

	/** Every file except the old config.json (its settings are imported instead); never overwrites. */
	private static void copyTree(Path from, Path to) {
		if (!Files.isDirectory(from)) return;
		try (Stream<Path> files = Files.walk(from)) {
			for (Path src : (Iterable<Path>) files::iterator) {
				Path rel = from.relativize(src);
				if (rel.toString().equals("config.json")) continue;
				Path dst = to.resolve(rel.toString());
				if (Files.isDirectory(src)) {
					Files.createDirectories(dst);
				} else if (!Files.exists(dst)) {
					Files.createDirectories(dst.getParent());
					Files.copy(src, dst);
				}
			}
			Taz.LOGGER.info("Copied {} into {}", from, to);
		} catch (IOException e) {
			Taz.LOGGER.error("Could not copy {} into {}", from, to, e);
		}
	}

	/**
	 * Moves settings in an existing config/taz/config.json to where the current layout keeps them,
	 * before it is loaded, so nothing resets when the menu is reorganised. The old file is kept as
	 * config.json.bak-v&lt;version&gt;.
	 */
	public static void upgradeConfigFile(Path file) {
		JsonObject root = read(file);
		if (root == null) return;
		int version = root.has("configVersion") ? root.get("configVersion").getAsInt() : 1;
		if (version >= TazConfig.CURRENT_VERSION) return;
		try {
			Files.copy(file, file.resolveSibling("config.json.bak-v" + version), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			if (version < 2) upgradeToV2(root);
			if (version < 3) upgradeToV3(root);
			if (version < 4) upgradeToV4(root);
			root.addProperty("configVersion", TazConfig.CURRENT_VERSION);
			Files.writeString(file, ModConfig.GSON.toJson(root), StandardCharsets.UTF_8);
			Taz.LOGGER.info("Upgraded {} from layout {} to {}", file, version, TazConfig.CURRENT_VERSION);
		} catch (Exception e) {
			Taz.LOGGER.error("Could not upgrade {}", file, e);
		}
	}

	/** v2: every feature is one dropdown in its category (Run Predictor, Next Tree Pointer, XP Assist). */
	public static void upgradeToV2(JsonObject root) {
		JsonObject d = object(root, "dungeons");
		if (d != null) {
			JsonObject advanced = new JsonObject();
			move(d, "resultSeconds", advanced, "resultSeconds");
			move(d, "historyCutoff", advanced, "historyCutoff");
			d.add("advanced", advanced);
			JsonObject tools = new JsonObject();
			move(d, "toolsFloor", tools, "floor");
			d.add("tools", tools);
			if (!d.has("enabled")) d.addProperty("enabled", true); // features start off now, but this one was on
			JsonObject category = new JsonObject();
			category.add("runPredictor", d);
			root.add("dungeons", category);
		}
		JsonObject g = object(root, "galatea");
		if (g != null) {
			if (g.has("pointer")) move(g, "pointer", g, "enabled");
			else if (g.has("highlight")) move(g, "highlight", g, "enabled");
			if (!g.has("enabled")) g.addProperty("enabled", true);
			JsonObject category = new JsonObject();
			category.add("treePointer", g);
			root.add("galatea", category);
		}
		JsonObject x = object(root, "xpAssist");
		if (x != null) {
			JsonObject sources = new JsonObject();
			for (String key : new String[]{"profileRefreshMinutes", "priceRefreshMinutes", "repoCheckHours", "hypixelApiBase", "pricesUrl",
					"neuRepoOwner", "neuRepoName", "neuRepoBranch", "rulesUrl"}) {
				move(x, key, sources, key);
			}
			x.add("dataSources", sources);
			if (!x.has("enabled")) x.addProperty("enabled", true);
			root.remove("xpAssist");
			JsonObject category = new JsonObject();
			category.add("xpAssist", x);
			root.add("skyblockXp", category);
		}
	}

	/** v3: one HUD editor for every HUD, so its key moved from Dungeons > Run Predictor to About. */
	public static void upgradeToV3(JsonObject root) {
		JsonObject dungeons = object(root, "dungeons");
		JsonObject predictor = dungeons == null ? null : object(dungeons, "runPredictor");
		if (predictor == null || !predictor.has("hudEditorKey")) return;
		JsonObject about = object(root, "about");
		if (about == null) {
			about = new JsonObject();
			root.add("about", about);
		}
		move(predictor, "hudEditorKey", about, "hudEditorKey");
	}

	/**
	 * v4: every feature starts off. The General, Inventory, Slayer and Fishing features came out on (version 3),
	 * so they're switched off; turn on the ones you want in /taz. Older features keep their setting.
	 */
	public static void upgradeToV4(JsonObject root) {
		for (String category : new String[]{"general", "inventory", "slayer", "fishing"}) {
			JsonObject c = object(root, category);
			if (c == null) continue;
			for (String feature : c.keySet()) {
				JsonObject f = object(c, feature);
				if (f != null) f.addProperty("enabled", false);
			}
		}
	}

	private static JsonObject object(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
	}

	private static void move(JsonObject from, String key, JsonObject to, String newKey) {
		JsonElement e = from.remove(key);
		if (e != null) to.add(newKey, e);
	}

	public static void importOldSettings(TazConfig cfg) {
		JsonObject drp = read(OLD_DUNGEONS.resolve("config.json"));
		if (drp != null) {
			try {
				importDungeons(drp, cfg.dungeons.runPredictor);
				Taz.LOGGER.info("Imported Dungeon Run Predictor settings");
			} catch (RuntimeException e) {
				Taz.LOGGER.error("Could not import Dungeon Run Predictor settings", e);
			}
		}
		JsonObject xp = read(OLD_XP.resolve("config.json"));
		if (xp != null) {
			try {
				importXp(xp, cfg.skyblockXp.xpAssist);
				cfg.skyblockXp.xpAssist.enabled = true; // you were using it
				Taz.LOGGER.info("Imported Skyblock XP Assist settings");
			} catch (RuntimeException e) {
				Taz.LOGGER.error("Could not import Skyblock XP Assist settings", e);
			}
		}
	}

	private static JsonObject read(Path file) {
		if (!Files.exists(file)) return null;
		try {
			JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception e) {
			Taz.LOGGER.error("Could not read {}", file, e);
			return null;
		}
	}

	private static void importDungeons(JsonObject o, ModConfig c) {
		c.enabled = bool(o, "enabled", true); // you were using it
		c.advanced.resultSeconds = integer(o, "resultSeconds", c.advanced.resultSeconds);
		c.advanced.historyCutoff = string(o, "historyCutoff", c.advanced.historyCutoff);
		if (o.has("features") && o.get("features").isJsonObject()) {
			JsonObject features = o.getAsJsonObject("features");
			for (Map.Entry<String, JsonElement> e : features.entrySet()) {
				Feature f;
				try {
					f = Feature.valueOf(e.getKey());
				} catch (IllegalArgumentException unknown) {
					continue;
				}
				c.setOn(f, e.getValue().getAsBoolean());
			}
		}
		if (o.has("panels") && o.get("panels").isJsonObject()) {
			JsonObject panels = o.getAsJsonObject("panels");
			for (HudPanel p : HudPanel.values()) {
				if (!panels.has(p.id) || !panels.get(p.id).isJsonObject()) continue;
				JsonObject po = panels.getAsJsonObject(p.id);
				ModConfig.PanelConfig pc = c.panel(p);
				pc.x = dbl(po, "x", pc.x);
				pc.y = dbl(po, "y", pc.y);
				pc.scale = (float) dbl(po, "scale", pc.scale);
				c.setPanelVisible(p, bool(po, "visible", c.panelVisible(p)));
			}
		}
	}

	private static void importXp(JsonObject o, com.taz.xpassist.config.ModConfig c) {
		c.profile.hypixelApiKey = string(o, "hypixelApiKey", c.profile.hypixelApiKey);
		c.profile.profileName = string(o, "profileName", c.profile.profileName);
		c.profile.useEliteBot = bool(o, "useEliteBot", c.profile.useEliteBot);
		c.profile.eliteApiBase = string(o, "eliteApiBase", c.profile.eliteApiBase);
		c.hud.hudEnabled = bool(o, "hudEnabled", c.hud.hudEnabled);
		c.hud.hudOnlyOnSkyblock = bool(o, "hudOnlyOnSkyblock", c.hud.hudOnlyOnSkyblock);
		c.hud.hudX = integer(o, "hudX", c.hud.hudX);
		c.hud.hudY = integer(o, "hudY", c.hud.hudY);
		c.hud.hudScale = (float) dbl(o, "hudScale", c.hud.hudScale);
		c.hud.hudRows = integer(o, "hudRows", c.hud.hudRows);
		c.hud.planLevels = integer(o, "planLevels", c.hud.planLevels);
		c.hud.hudShoppingHints = bool(o, "hudShoppingHints", c.hud.hudShoppingHints);
		c.xpList.showUnpriced = bool(o, "showUnpriced", c.xpList.showUnpriced);
		c.xpList.useNpcShops = bool(o, "useNpcShops", c.xpList.useNpcShops);
		c.xpList.useCraftCosts = bool(o, "useCraftCosts", c.xpList.useCraftCosts);
		if ("BUY_ORDER".equalsIgnoreCase(string(o, "bazaarMode", ""))) {
			c.xpList.bazaarMode = com.taz.xpassist.config.ModConfig.BazaarMode.BUY_ORDER;
		}
		c.tracker.trackerGlow = bool(o, "trackerGlow", c.tracker.trackerGlow);
		c.tracker.trackerBeam = bool(o, "trackerBeam", c.tracker.trackerBeam);
		c.tracker.trackerTracer = bool(o, "trackerTracer", c.tracker.trackerTracer);
		if (o.has("trackerColor")) {
			int rgb = o.get("trackerColor").getAsInt();
			c.tracker.trackerColor = ChromaColour.Companion.fromStaticRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
		}
		c.dataSources.profileRefreshMinutes = integer(o, "profileRefreshMinutes", c.dataSources.profileRefreshMinutes);
		c.dataSources.priceRefreshMinutes = integer(o, "priceRefreshMinutes", c.dataSources.priceRefreshMinutes);
		c.dataSources.repoCheckHours = integer(o, "repoCheckHours", c.dataSources.repoCheckHours);
		c.dataSources.hypixelApiBase = string(o, "hypixelApiBase", c.dataSources.hypixelApiBase);
		c.dataSources.pricesUrl = string(o, "pricesUrl", c.dataSources.pricesUrl);
		c.dataSources.neuRepoOwner = string(o, "neuRepoOwner", c.dataSources.neuRepoOwner);
		c.dataSources.neuRepoName = string(o, "neuRepoName", c.dataSources.neuRepoName);
		c.dataSources.neuRepoBranch = string(o, "neuRepoBranch", c.dataSources.neuRepoBranch);
		c.dataSources.rulesUrl = string(o, "rulesUrl", c.dataSources.rulesUrl);
		if (o.has("disabledCategories")) o.getAsJsonArray("disabledCategories").forEach(e -> c.disabledCategories.add(e.getAsString()));
		if (o.has("hiddenTasks")) o.getAsJsonArray("hiddenTasks").forEach(e -> c.hiddenTasks.add(e.getAsString()));
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : def;
	}

	private static int integer(JsonObject o, String key, int def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
	}

	private static double dbl(JsonObject o, String key, double def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
	}

	private static String string(JsonObject o, String key, String def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
	}
}
