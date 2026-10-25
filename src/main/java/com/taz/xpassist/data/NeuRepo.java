package com.taz.xpassist.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.util.TextUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Parsed copy of the NotEnoughUpdates community data repo
 * (github.com/NotEnoughUpdates/NotEnoughUpdates-REPO). It is still updated daily by the
 * community (and by current mods such as Firmament), even though the old NEU mod itself is not.
 */
public final class NeuRepo {
	public static final String COIN = "SKYBLOCK_COIN";

	public record Recipe(Map<String, Double> inputs, int outputCount) {
	}

	public record ShopOffer(String npcId, String resultId, int resultCount, Map<String, Double> costs) {
	}

	public record Warp(String name, List<String> aliases, String mode, int x, int y, int z) {
	}

	public final Map<String, String> displayNames = new HashMap<>();
	public final Map<String, Recipe> craftRecipes = new HashMap<>();
	public final Map<String, String> craftText = new HashMap<>();
	public final Map<String, String> reputationReq = new HashMap<>();
	public final Map<String, List<ShopOffer>> offersByItem = new HashMap<>();
	public final Map<String, Npc> npcs = new LinkedHashMap<>();
	public final Map<String, String> areaNames = new LinkedHashMap<>();
	public final List<Warp> warps = new ArrayList<>();
	public JsonObject sblevels = new JsonObject();
	public JsonObject abiphone = new JsonObject();
	public JsonObject misc = new JsonObject();
	public JsonObject museum = new JsonObject();
	public JsonObject essenceShops = new JsonObject();
	public final String commit;

	private NeuRepo(String commit) {
		this.commit = commit;
	}

	public static NeuRepo load(Path zipFile, String commit) throws IOException {
		NeuRepo repo = new NeuRepo(commit);
		List<JsonObject> npcFiles = new ArrayList<>();
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
					continue;
				}
				// Strip the "<repo>-<branch>/" folder GitHub puts at the root of archives.
				String name = entry.getName();
				int slash = name.indexOf('/');
				String path = slash >= 0 ? name.substring(slash + 1) : name;
				if (!path.startsWith("items/") && !path.startsWith("constants/")) {
					continue;
				}
				JsonObject json;
				try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
					JsonElement element = JsonParser.parseReader(reader);
					if (!element.isJsonObject()) {
						continue;
					}
					json = element.getAsJsonObject();
				} catch (Exception e) {
					continue;
				}
				if (path.startsWith("constants/")) {
					switch (path) {
						case "constants/sblevels.json" -> repo.sblevels = json;
						case "constants/abiphone.json" -> repo.abiphone = json;
						case "constants/misc.json" -> repo.misc = json;
						case "constants/museum.json" -> repo.museum = json;
						case "constants/essenceshops.json" -> repo.essenceShops = json;
						case "constants/islands.json" -> repo.readIslands(json);
						default -> {
						}
					}
				} else if (path.lastIndexOf('/') == "items".length()) {
					if (repo.readItem(json)) {
						npcFiles.add(json);
					}
				}
			}
		}
		for (JsonObject npc : npcFiles) {
			repo.readNpcShop(npc);
		}
		repo.addAbiphoneContacts();
		return repo;
	}

	/** Returns true when the file is an NPC. */
	private boolean readItem(JsonObject json) {
		String id = string(json, "internalname");
		if (id == null) {
			return false;
		}
		String display = TextUtil.strip(string(json, "displayname"));
		displayNames.put(id, display);
		if (json.has("crafttext")) {
			String text = TextUtil.strip(string(json, "crafttext"));
			if (!text.isBlank()) {
				craftText.put(id, text);
			}
		}
		if (json.has("reputation_req")) {
			reputationReq.put(id, string(json, "reputation_req"));
		}
		if (json.has("recipe") && json.get("recipe").isJsonObject()) {
			JsonObject grid = json.getAsJsonObject("recipe");
			int count = grid.has("count") && grid.get("count").isJsonPrimitive() ? Math.max(1, grid.get("count").getAsInt()) : 1;
			Recipe recipe = parseGrid(grid, count);
			if (recipe != null) {
				craftRecipes.put(id, recipe);
			}
		}
		if (json.has("recipes") && json.get("recipes").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("recipes")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject recipe = element.getAsJsonObject();
				if ("crafting".equals(string(recipe, "type")) && !craftRecipes.containsKey(id)) {
					String output = recipe.has("overrideOutputId") ? string(recipe, "overrideOutputId") : id;
					int count = recipe.has("count") ? Math.max(1, recipe.get("count").getAsInt()) : 1;
					Recipe parsed = parseGrid(recipe, count);
					if (parsed != null && output != null) {
						craftRecipes.putIfAbsent(output, parsed);
					}
				}
			}
		}
		if (id.endsWith("_NPC")) {
			String name = display.replaceFirst("\\s*\\(NPC\\)$", "").trim();
			List<String> lore = new ArrayList<>();
			if (json.has("lore") && json.get("lore").isJsonArray()) {
				for (JsonElement line : json.getAsJsonArray("lore")) {
					String text = TextUtil.strip(line.getAsString());
					if (!text.isBlank()) {
						lore.add(text);
					}
				}
			}
			String island = json.has("island") ? string(json, "island") : null;
			int x = json.has("x") ? (int) Math.floor(json.get("x").getAsDouble()) : 0;
			int y = json.has("y") ? (int) Math.floor(json.get("y").getAsDouble()) : 0;
			int z = json.has("z") ? (int) Math.floor(json.get("z").getAsDouble()) : 0;
			npcs.put(id, new Npc(id, name, island, x, y, z, Collections.unmodifiableList(lore), "NPC"));
			return true;
		}
		return false;
	}

	private void readNpcShop(JsonObject json) {
		String npcId = string(json, "internalname");
		if (!json.has("recipes") || !json.get("recipes").isJsonArray()) {
			return;
		}
		for (JsonElement element : json.getAsJsonArray("recipes")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject recipe = element.getAsJsonObject();
			if (!"npc_shop".equals(string(recipe, "type")) || !recipe.has("result") || !recipe.has("cost")) {
				continue;
			}
			String[] result = splitIngredient(string(recipe, "result"));
			if (result == null) {
				continue;
			}
			Map<String, Double> costs = new LinkedHashMap<>();
			for (JsonElement cost : recipe.getAsJsonArray("cost")) {
				String[] parts = splitIngredient(cost.getAsString());
				if (parts != null) {
					costs.merge(parts[0], Double.parseDouble(parts[1]), Double::sum);
				}
			}
			int resultCount = (int) Math.max(1, Math.round(Double.parseDouble(result[1])));
			offersByItem.computeIfAbsent(result[0], k -> new ArrayList<>())
				.add(new ShopOffer(npcId, result[0], resultCount, costs));
		}
	}

	private void readIslands(JsonObject json) {
		if (json.has("area_names")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("area_names").entrySet()) {
				areaNames.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		if (json.has("island_warps")) {
			for (JsonElement element : json.getAsJsonArray("island_warps")) {
				JsonObject warp = element.getAsJsonObject();
				List<String> aliases = new ArrayList<>();
				if (warp.has("aliases")) {
					for (JsonElement alias : warp.getAsJsonArray("aliases")) {
						aliases.add(alias.getAsString());
					}
				}
				warps.add(new Warp(string(warp, "warp"), aliases, string(warp, "mode"),
					warp.get("x").getAsInt(), warp.get("y").getAsInt(), warp.get("z").getAsInt()));
			}
		}
	}

	/** Abiphone contacts you have to visit in person. Reuse the NPC entry when one exists. */
	private void addAbiphoneContacts() {
		for (Map.Entry<String, JsonElement> entry : abiphone.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject contact = entry.getValue().getAsJsonObject();
			if (!contact.has("island") || !contact.has("x") || !contact.has("y") || !contact.has("z")
				|| findNpcByExactName(entry.getKey()) != null) {
				continue;
			}
			String id = "ABIPHONE:" + entry.getKey();
			List<String> lore = new ArrayList<>();
			lore.add("Abiphone contact");
			npcs.put(id, new Npc(id, entry.getKey(), string(contact, "island"),
				contact.get("x").getAsInt(), contact.get("y").getAsInt(), contact.get("z").getAsInt(), lore, "Abiphone"));
		}
	}

	public Npc findNpcByExactName(String name) {
		String wanted = TextUtil.normalize(name);
		for (Npc npc : npcs.values()) {
			if (TextUtil.normalize(npc.name()).equals(wanted)) {
				return npc;
			}
		}
		return null;
	}

	/** Best fuzzy matches for a typed NPC name: exact, then prefix, then contains. */
	public List<Npc> searchNpcs(String query) {
		String wanted = TextUtil.normalize(query);
		List<Npc> exact = new ArrayList<>();
		List<Npc> prefix = new ArrayList<>();
		List<Npc> contains = new ArrayList<>();
		for (Npc npc : npcs.values()) {
			String name = TextUtil.normalize(npc.name());
			if (name.equals(wanted)) {
				exact.add(npc);
			} else if (name.startsWith(wanted)) {
				prefix.add(npc);
			} else if (name.contains(wanted)) {
				contains.add(npc);
			}
		}
		List<Npc> out = new ArrayList<>(exact);
		out.addAll(prefix);
		out.addAll(contains);
		return out;
	}

	public String islandName(String mode) {
		if (mode == null) {
			return "Unknown island";
		}
		String name = areaNames.get(mode);
		return name != null ? name : TextUtil.titleCase(mode);
	}

	/** The warp closest to a location on an island, e.g. "/warp drag" for Gregory. */
	public Warp bestWarp(String mode, int x, int y, int z) {
		Warp best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Warp warp : warps) {
			if (!warp.mode().equals(mode)) {
				continue;
			}
			double dx = warp.x() - x;
			double dy = warp.y() - y;
			double dz = warp.z() - z;
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = warp;
			}
		}
		return best;
	}

	/** Maps a tab list "Area: X" name to an island id. */
	public String modeForArea(String area, Rules rules) {
		String wanted = TextUtil.normalize(area);
		if (wanted.isEmpty()) {
			return null;
		}
		for (Map.Entry<String, String> entry : areaNames.entrySet()) {
			String name = TextUtil.normalize(entry.getValue());
			if (name.equals(wanted) || name.equals("the" + wanted) || ("the" + name).equals(wanted)) {
				return entry.getKey();
			}
		}
		for (Warp warp : warps) {
			if (TextUtil.normalize(warp.name()).equals(wanted)) {
				return warp.mode();
			}
			for (String alias : warp.aliases()) {
				if (TextUtil.normalize(alias).equals(wanted)) {
					return warp.mode();
				}
			}
		}
		return rules.areaAlias(wanted);
	}

	public String name(String itemId) {
		String name = displayNames.get(itemId);
		return name != null ? name : TextUtil.titleCase(itemId);
	}

	private Map<String, String> idsByName;

	/** Item id for a display name, skipping NPCs. Used when Hypixel's item list doesn't know the name. */
	public String idForName(String name) {
		if (idsByName == null) {
			Map<String, String> map = new HashMap<>();
			for (Map.Entry<String, String> entry : displayNames.entrySet()) {
				if (!entry.getKey().endsWith("_NPC")) {
					map.putIfAbsent(TextUtil.normalize(entry.getValue()), entry.getKey());
				}
			}
			idsByName = map;
		}
		String key = TextUtil.normalize(name);
		for (String candidate : new String[]{key, key.replaceFirst("s$", ""), key.replaceFirst("es$", "")}) {
			if (idsByName.containsKey(candidate)) {
				return idsByName.get(candidate);
			}
		}
		return null;
	}

	private static Recipe parseGrid(JsonObject grid, int outputCount) {
		Map<String, Double> inputs = new LinkedHashMap<>();
		for (String row : new String[]{"A", "B", "C"}) {
			for (int col = 1; col <= 3; col++) {
				String slot = row + col;
				if (!grid.has(slot) || !grid.get(slot).isJsonPrimitive()) {
					continue;
				}
				String[] parts = splitIngredient(grid.get(slot).getAsString());
				if (parts != null) {
					inputs.merge(parts[0], Double.parseDouble(parts[1]), Double::sum);
				}
			}
		}
		return inputs.isEmpty() ? null : new Recipe(inputs, outputCount);
	}

	/** "MYCEL:10" -> ["MYCEL", "10"]; "DECENT_BOW" -> ["DECENT_BOW", "1"]. */
	private static String[] splitIngredient(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		int colon = text.lastIndexOf(':');
		if (colon < 0) {
			return new String[]{text.trim(), "1"};
		}
		String count = text.substring(colon + 1).trim();
		try {
			Double.parseDouble(count);
		} catch (NumberFormatException e) {
			return new String[]{text.trim(), "1"};
		}
		return new String[]{text.substring(0, colon).trim(), count};
	}

	private static String string(JsonObject json, String key) {
		JsonElement element = json.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
	}

	public JsonObject sbObject(String category) {
		return sblevels.has(category) && sblevels.get(category).isJsonObject() ? sblevels.getAsJsonObject(category) : new JsonObject();
	}

	public JsonArray miscArray(String key) {
		return misc.has(key) && misc.get(key).isJsonArray() ? misc.getAsJsonArray(key) : new JsonArray();
	}

	public JsonObject miscObject(String key) {
		return misc.has(key) && misc.get(key).isJsonObject() ? misc.getAsJsonObject(key) : new JsonObject();
	}
}
