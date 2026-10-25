package com.taz.foraging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import net.minecraft.core.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Routes (ordered waypoints) for Tree Route Assist, kept in config/taz/foraging/routes.json. They import and export in
 * Skyblocker's waypoint format: "[Skyblocker-Waypoint-Data-V1]" + Base64 of gzipped JSON, a list of groups
 * {"name", "island", "waypoints": [{"pos": [x, y, z], "name", "colorComponents": [r, g, b], "alpha"}]}. A group whose
 * island is "unknown" gets the island you're on when you import it (like Skyblocker does), and a route only shows there.
 */
public final class TreeRoutes {
	public static final String PREFIX = "[Skyblocker-Waypoint-Data-V1]";
	private static final Path FILE = Taz.CONFIG_DIR.resolve("foraging").resolve("routes.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Kind {
		TREE("Tree"), WARP("Etherwarp"), TELEPORT("Teleport"), STOP("");

		public final String label;

		Kind(String label) {
			this.label = label;
		}

		/** "3" is a tree, "E3" an etherwarp spot, "TP3" a teleport; anything else is a named stop ("Miria"). */
		static Kind of(String name) {
			if (name.matches("\\d+")) return TREE;
			if (name.matches("(?i)e\\d+")) return WARP;
			if (name.matches("(?i)tp\\d+")) return TELEPORT;
			return STOP;
		}
	}

	public record Point(BlockPos pos, String name, int rgb, float alpha) {
		public Kind kind() {
			return Kind.of(name);
		}

		/** "Tree 3", "Etherwarp E4", "Miria". */
		public String describe() {
			Kind k = kind();
			return k == Kind.STOP ? name : k.label + " " + name;
		}
	}

	public static final class Route {
		public String name;
		public String island;
		public List<Point> points = new ArrayList<>();
		/** Best lap and the time to each point on it (ms from the lap start), or 0 / empty. */
		public long bestLapMs;
		public long[] bestSplits = new long[0];

		Route(String name, String island) {
			this.name = name;
			this.island = island;
		}
	}

	private static final List<Route> ROUTES = new ArrayList<>();
	/** island -> name of the route in use there. */
	private static final Map<String, String> ACTIVE = new HashMap<>();
	private static boolean loaded;

	private TreeRoutes() {
	}

	public static List<Route> all() {
		if (!loaded) load();
		return ROUTES;
	}

	/** The route in use on the island you're on, or null. */
	public static Route active() {
		Route first = null;
		for (Route r : all()) {
			if (!Islands.here(r.island)) continue;
			if (first == null) first = r;
			if (r.name.equals(ACTIVE.get(r.island))) return r;
		}
		return first;
	}

	/** Use the next route on this island; returns it, or null when there's none. */
	public static Route cycle() {
		List<Route> here = all().stream().filter(r -> Islands.here(r.island)).toList();
		if (here.isEmpty()) return null;
		Route cur = active();
		Route next = here.get((here.indexOf(cur) + 1) % here.size());
		ACTIVE.put(next.island, next.name);
		save();
		return next;
	}

	public static void delete(Route r) {
		all().remove(r);
		save();
	}

	// ---- Skyblocker format

	/** Routes read from clipboard text: Skyblocker's export, or its plain JSON. Throws if it isn't either. */
	public static List<Route> parse(String text, String defaultIsland) throws Exception {
		String t = text.trim();
		String json;
		if (t.startsWith(PREFIX)) {
			byte[] packed = Base64.getDecoder().decode(t.substring(PREFIX.length()).trim());
			try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
				json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
		} else {
			json = t;
		}
		JsonElement root = JsonParser.parseString(json);
		JsonArray groups = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
		if (root.isJsonObject()) groups.add(root);
		List<Route> out = new ArrayList<>();
		for (JsonElement g : groups) {
			JsonObject o = g.getAsJsonObject();
			String island = o.has("island") ? o.get("island").getAsString() : Islands.UNKNOWN;
			Route r = new Route(o.has("name") ? o.get("name").getAsString() : "Route", island);
			for (JsonElement w : o.getAsJsonArray("waypoints")) {
				JsonObject p = w.getAsJsonObject();
				JsonArray pos = p.getAsJsonArray("pos");
				int rgb = 0x00FF00;
				if (p.has("colorComponents")) {
					JsonArray c = p.getAsJsonArray("colorComponents");
					rgb = Math.round(c.get(0).getAsFloat() * 255) << 16 | Math.round(c.get(1).getAsFloat() * 255) << 8 | Math.round(c.get(2).getAsFloat() * 255);
				}
				float alpha = p.has("alpha") ? p.get("alpha").getAsFloat() : 0.5f;
				r.points.add(new Point(new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()),
						p.has("name") ? p.get("name").getAsString() : String.valueOf(r.points.size() + 1), rgb, alpha));
			}
			if (Islands.UNKNOWN.equals(r.island)) {
				r.island = defaultIsland;
				if (Islands.UNKNOWN.equals(r.island) && !r.points.isEmpty()) r.island = Islands.guess(r.points.get(0).pos().getX(), r.points.get(0).pos().getZ());
			}
			if (!r.points.isEmpty()) out.add(r);
		}
		if (out.isEmpty()) throw new IllegalArgumentException("no waypoints in it");
		return out;
	}

	/** Adds or replaces (same name and island) the routes; best times stay if the points didn't change. */
	public static void add(List<Route> routes) {
		for (Route r : routes) {
			Route old = all().stream().filter(o -> o.name.equals(r.name) && o.island.equals(r.island)).findFirst().orElse(null);
			if (old != null) {
				if (old.points.equals(r.points)) {
					r.bestLapMs = old.bestLapMs;
					r.bestSplits = old.bestSplits;
				}
				ROUTES.set(ROUTES.indexOf(old), r);
			} else {
				ROUTES.add(r);
			}
			ACTIVE.put(r.island, r.name);
		}
		save();
	}

	/** One route in Skyblocker's export format. */
	public static String export(Route r) throws Exception {
		JsonArray groups = new JsonArray();
		groups.add(toJson(r, false));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
			gz.write(groups.toString().getBytes(StandardCharsets.UTF_8));
		}
		return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	private static JsonObject toJson(Route r, boolean withTimes) {
		JsonObject o = new JsonObject();
		o.addProperty("name", r.name);
		o.addProperty("island", r.island);
		JsonArray points = new JsonArray();
		for (Point p : r.points) {
			JsonObject w = new JsonObject();
			JsonArray pos = new JsonArray();
			pos.add(p.pos().getX());
			pos.add(p.pos().getY());
			pos.add(p.pos().getZ());
			w.add("pos", pos);
			w.addProperty("name", p.name());
			JsonArray c = new JsonArray();
			c.add((p.rgb() >> 16 & 0xFF) / 255f);
			c.add((p.rgb() >> 8 & 0xFF) / 255f);
			c.add((p.rgb() & 0xFF) / 255f);
			w.add("colorComponents", c);
			w.addProperty("alpha", p.alpha());
			w.addProperty("shouldRender", true);
			points.add(w);
		}
		o.add("waypoints", points);
		o.addProperty("ordered", true);
		if (withTimes) {
			o.addProperty("bestLapMs", r.bestLapMs);
			JsonArray splits = new JsonArray();
			for (long s : r.bestSplits) splits.add(s);
			o.add("bestSplits", splits);
		}
		return o;
	}

	// ---- file

	private static void load() {
		loaded = true;
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Route r : parse(root.getAsJsonArray("routes").toString(), Islands.UNKNOWN)) ROUTES.add(r);
			JsonArray saved = root.getAsJsonArray("routes");
			for (int i = 0; i < saved.size() && i < ROUTES.size(); i++) {
				JsonObject o = saved.get(i).getAsJsonObject();
				Route r = ROUTES.get(i);
				r.bestLapMs = o.has("bestLapMs") ? o.get("bestLapMs").getAsLong() : 0;
				if (o.has("bestSplits")) {
					JsonArray s = o.getAsJsonArray("bestSplits");
					r.bestSplits = new long[s.size()];
					for (int k = 0; k < s.size(); k++) r.bestSplits[k] = s.get(k).getAsLong();
				}
			}
			if (root.has("active")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("active").entrySet()) ACTIVE.put(e.getKey(), e.getValue().getAsString());
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	public static void save() {
		try {
			JsonObject root = new JsonObject();
			JsonArray routes = new JsonArray();
			for (Route r : ROUTES) routes.add(toJson(r, true));
			root.add("routes", routes);
			JsonObject active = new JsonObject();
			ACTIVE.forEach(active::addProperty);
			root.add("active", active);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
