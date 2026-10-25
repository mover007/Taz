package com.taz.dungeons.secrets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Secret routes in Odin's dungeon waypoint format: a map of room name to waypoints, each with a block position relative
 * to the room (see {@link DungeonRooms}), a colour ("#RRGGBBAA"), a box, an optional title and type. Odin shares them as
 * Base64 of gzipped JSON, or plain JSON. A route starts at its red waypoint; titles starting with a number ("3",
 * "4 item", "5 down") are its steps in order; the rest are hints. See {@link Kind} for the colours.
 *
 * Taz comes with a set of routes (3ppopka's, from Odin). Imported packs are kept in config/taz/dungeons/secret-routes/
 * as Odin files, in import order; for each room the last imported pack that has it wins, then Taz's own.
 */
public final class SecretRoutes {
	private static final Path DIR = Taz.CONFIG_DIR.resolve("dungeons").resolve("secret-routes");
	private static final Path ORDER = DIR.resolve("order.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Pattern STEP = Pattern.compile("^\\s*(\\d+)\\s*(.*)$");

	/**
	 * What a waypoint is for, from 3ppopka's colour legend: red the start, purple an etherwarp spot, green a chest /
	 * lever / essence, yellow where to aim a normal teleport, aqua a prince; fully see-through ones are text to read.
	 * An Odin type (SECRET, ETHERWARP, NORMAL) wins over the colour; with neither, small boxes are secrets.
	 */
	public enum Kind {
		START("Start", "§c"), ETHERWARP("Etherwarp", "§d"), SECRET("Secret", "§a"), AIM("Aim", "§e"), PRINCE("Prince", "§b"),
		TEXT("Note", "§7"), SPOT("Spot", "§f");

		public final String label;
		public final String color;

		Kind(String label, String color) {
			this.label = label;
			this.color = color;
		}

		/** Somewhere you stand: reaching it ticks its step off. */
		public boolean spot() {
			return this == START || this == ETHERWARP || this == SPOT;
		}
	}

	private static final int[][] LEGEND = {
			{0xFF5555, 0}, {0xAA00AA, 1}, {0x55FF55, 2}, {0x00FF00, 2}, {0xFFF255, 3}, {0x55A7CE, 4}, {0x00F7FF, 4}};
	private static final Kind[] LEGEND_KINDS = {Kind.START, Kind.ETHERWARP, Kind.SECRET, Kind.AIM, Kind.PRINCE};

	/** What a step's note asks for, from the route legend ("" when it's just a direction like "down"). */
	public static String explain(Kind kind, String note) {
		String n = note.toLowerCase();
		if (n.contains("breakwarp")) return "teleport normally, break the block, then etherwarp";
		if (kind == Kind.AIM || n.contains("just tp") || n.contains("normal")) return "aim here and right-click to teleport (not etherwarp)";
		if (n.contains("bat")) return "stand here and kill the bat with Hyperion";
		if (n.matches(".*\\bline\\b.*")) return "mine only the line";
		if (n.contains("item")) return "pick up the item";
		return "";
	}

	public record Waypoint(BlockPos pos, int argb, boolean filled, boolean depth, AABB box, String title, String type) {
		public Kind kind() {
			if (!visible()) return Kind.TEXT;
			if ("SECRET".equals(type)) return Kind.SECRET;
			if ("ETHERWARP".equals(type)) return Kind.ETHERWARP;
			if ("NORMAL".equals(type)) return Kind.SPOT;
			int rgb = argb & 0xFFFFFF;
			for (int[] c : LEGEND) {
				int dr = (rgb >> 16 & 0xFF) - (c[0] >> 16 & 0xFF), dg = (rgb >> 8 & 0xFF) - (c[0] >> 8 & 0xFF), db = (rgb & 0xFF) - (c[0] & 0xFF);
				if (dr * dr + dg * dg + db * db < 40 * 40) return LEGEND_KINDS[c[1]];
			}
			return small() ? Kind.SECRET : Kind.SPOT;
		}

		/** A bat to kill or an item to pick up here: standing on it isn't enough. */
		public boolean needsSecret() {
			String n = note().toLowerCase();
			return kind().spot() && (n.contains("bat") || n.contains("item"));
		}

		/** The route step this is ("4 item" is step 4), or -1 for a hint. */
		public int step() {
			if (title == null) return -1;
			Matcher m = STEP.matcher(title);
			return m.matches() && m.group(1).length() < 4 ? Integer.parseInt(m.group(1)) : -1;
		}

		/** The title without its step number ("item" for "4 item"). */
		public String note() {
			if (title == null) return "";
			Matcher m = STEP.matcher(title);
			return (m.matches() ? m.group(2) : title).replaceFirst("^[|+:.\\-\\s]+", "").trim();
		}

		/** A chest, lever, skull or other part block: something to click, not somewhere to stand. */
		public boolean small() {
			return "SECRET".equals(type) || box.getXsize() < 0.99 || box.getYsize() < 0.99 || box.getZsize() < 0.99;
		}

		public boolean visible() {
			return (argb >>> 24) != 0;
		}
	}

	public record Pack(String name, Map<String, List<Waypoint>> rooms) {
		public int waypoints() {
			return rooms.values().stream().mapToInt(List::size).sum();
		}
	}

	private static Pack defaults;
	private static final List<Pack> IMPORTED = new ArrayList<>();
	private static boolean loaded;
	/** Goes up whenever the imported packs change. */
	private static int version;

	private SecretRoutes() {
	}

	public static int version() {
		return version;
	}

	public static Pack defaults() {
		if (!loaded) load();
		return defaults;
	}

	public static List<Pack> imported() {
		if (!loaded) load();
		return IMPORTED;
	}

	/** The route for a room: the last imported pack that has it, else Taz's own (when turned on), else none. */
	public static List<Waypoint> forRoom(String room) {
		for (int i = imported().size() - 1; i >= 0; i--) {
			List<Waypoint> w = IMPORTED.get(i).rooms().get(room);
			if (w != null && !w.isEmpty()) return w;
		}
		if (SecretsConfig.get().defaultRoutes) {
			List<Waypoint> w = defaults().rooms().get(room);
			if (w != null) return w;
		}
		return List.of();
	}

	/** Every room's route in use, merged the same way, for export. */
	public static Map<String, List<Waypoint>> active() {
		Map<String, List<Waypoint>> out = new LinkedHashMap<>();
		if (SecretsConfig.get().defaultRoutes) out.putAll(defaults().rooms());
		for (Pack p : imported()) p.rooms().forEach((room, w) -> {
			if (!w.isEmpty()) out.put(room, w);
		});
		return out;
	}

	// ---- files

	private static void load() {
		loaded = true;
		defaults = new Pack("Taz", Map.of());
		try (InputStream in = SecretRoutes.class.getResourceAsStream("/assets/taz/dungeons/default-secret-routes.json")) {
			defaults = new Pack("Taz", parseJson(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read Taz's secret routes", e);
		}
		IMPORTED.clear();
		try {
			if (!Files.exists(ORDER)) return;
			List<String> names = GSON.fromJson(Files.readString(ORDER), new TypeToken<List<String>>() {}.getType());
			for (String name : names) {
				Path file = DIR.resolve(name + ".json");
				if (Files.exists(file)) IMPORTED.add(new Pack(name, parseJson(Files.readString(file))));
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read your secret routes", e);
		}
	}

	private static void saveOrder() throws Exception {
		Files.createDirectories(DIR);
		Files.writeString(ORDER, GSON.toJson(IMPORTED.stream().map(Pack::name).toList()));
	}

	/** Adds (or replaces) an imported pack and saves it as an Odin file. */
	public static Pack add(String name, Map<String, List<Waypoint>> rooms) throws Exception {
		imported().removeIf(p -> p.name().equals(name));
		Pack pack = new Pack(name, rooms);
		IMPORTED.add(pack);
		version++;
		Files.createDirectories(DIR);
		Files.writeString(DIR.resolve(name + ".json"), GSON.toJson(toJson(rooms)));
		saveOrder();
		return pack;
	}

	public static void removeImported() throws Exception {
		for (Pack p : imported()) Files.deleteIfExists(DIR.resolve(p.name() + ".json"));
		IMPORTED.clear();
		version++;
		saveOrder();
	}

	/** A name for a pack from the clipboard: "Imported 1", "Imported 2", ... */
	public static String nextName() {
		for (int i = 1; ; i++) {
			String name = "Imported " + i;
			if (imported().stream().noneMatch(p -> p.name().equals(name))) return name;
		}
	}

	/** Odin's own packs (.minecraft/config/odin/dungeon-waypoints/*.json, and its older single file). */
	public static List<Path> odinPacks(Path gameDir) throws Exception {
		Path odin = gameDir.resolve("config").resolve("odin");
		List<Path> out = new ArrayList<>();
		Path dir = odin.resolve("dungeon-waypoints");
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(out::add);
			}
		}
		Path legacy = odin.resolve("dungeon-waypoint-config.json");
		if (Files.exists(legacy) && Files.size(legacy) > 2) out.add(legacy);
		return out;
	}

	// ---- Odin's format

	/** Clipboard text: Odin's export (Base64 of gzipped JSON) or its plain JSON. Throws if it's neither. */
	public static Map<String, List<Waypoint>> parse(String text) throws Exception {
		String t = text.trim();
		if (t.startsWith("{")) return parseJson(t);
		byte[] packed = Base64.getDecoder().decode(t.replaceAll("\\s", ""));
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
			return parseJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	/** Odin's export string for these rooms. */
	public static String encode(Map<String, List<Waypoint>> rooms) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
			out.write(GSON.toJson(toJson(rooms)).getBytes(StandardCharsets.UTF_8));
		}
		return Base64.getEncoder().encodeToString(bytes.toByteArray());
	}

	static Map<String, List<Waypoint>> parseJson(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, List<Waypoint>> rooms = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> room : root.entrySet()) {
			if (!room.getValue().isJsonArray()) continue;
			List<Waypoint> list = new ArrayList<>();
			for (JsonElement e : room.getValue().getAsJsonArray()) list.add(waypoint(e.getAsJsonObject()));
			rooms.put(room.getKey(), list);
		}
		return rooms;
	}

	private static Waypoint waypoint(JsonObject o) {
		BlockPos pos;
		if (o.has("blockPos")) {
			JsonObject p = o.getAsJsonObject("blockPos");
			pos = p.has("x") ? new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt())
					: p.has("field_11175") ? new BlockPos(p.get("field_11175").getAsInt(), p.get("field_11174").getAsInt(), p.get("field_11173").getAsInt())
					: BlockPos.ZERO;
		} else {
			pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
		}
		String title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : null;
		if (title != null && title.isBlank()) title = null;
		String type = o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : null;
		if (type == null && o.has("secret") && o.get("secret").getAsBoolean()) type = "SECRET";
		return new Waypoint(pos, color(o.get("color")), bool(o, "filled"), bool(o, "depth"), box(o.get("aabb")), title, type);
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).getAsBoolean();
	}

	/** "#RRGGBBAA" (Odin), or an older colour object; green when there's none. */
	private static int color(JsonElement e) {
		if (e == null || e.isJsonNull()) return 0xFF55FF55;
		if (e.isJsonPrimitive()) {
			String hex = e.getAsString().replace("#", "");
			long v = Long.parseLong(hex, 16);
			return hex.length() >= 8 ? (int) ((v & 0xFF) << 24 | v >>> 8) : (int) (0xFF000000L | v);
		}
		JsonObject o = e.getAsJsonObject();
		if (o.has("rgba")) return o.get("rgba").getAsInt();
		if (o.has("hue")) {
			int rgb = java.awt.Color.HSBtoRGB(o.get("hue").getAsFloat(), o.get("saturation").getAsFloat(), o.get("brightness").getAsFloat());
			float alpha = o.has("alpha") ? o.get("alpha").getAsFloat() : 1f;
			return (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
		}
		return 0xFF55FF55;
	}

	private static AABB box(JsonElement e) {
		if (e == null || !e.isJsonObject()) return new AABB(0, 0, 0, 1, 1, 1);
		JsonObject o = e.getAsJsonObject();
		String[] keys = o.has("minX") ? new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}
				: o.has("field_1323") ? new String[]{"field_1323", "field_1322", "field_1321", "field_1320", "field_1325", "field_1324"}
				: o.has("field_72340_a") ? new String[]{"field_72340_a", "field_72338_b", "field_72339_c", "field_72336_d", "field_72337_e", "field_72334_f"}
				: null;
		if (keys == null) return new AABB(0, 0, 0, 1, 1, 1);
		return new AABB(o.get(keys[0]).getAsDouble(), o.get(keys[1]).getAsDouble(), o.get(keys[2]).getAsDouble(),
				o.get(keys[3]).getAsDouble(), o.get(keys[4]).getAsDouble(), o.get(keys[5]).getAsDouble());
	}

	private static JsonObject toJson(Map<String, List<Waypoint>> rooms) {
		JsonObject root = new JsonObject();
		rooms.forEach((room, list) -> {
			JsonArray arr = new JsonArray();
			for (Waypoint w : list) {
				JsonObject o = new JsonObject();
				JsonObject p = new JsonObject();
				p.addProperty("x", w.pos().getX());
				p.addProperty("y", w.pos().getY());
				p.addProperty("z", w.pos().getZ());
				o.add("blockPos", p);
				o.addProperty("color", String.format("#%06X%02X", w.argb() & 0xFFFFFF, w.argb() >>> 24));
				o.addProperty("filled", w.filled());
				o.addProperty("depth", w.depth());
				JsonObject b = new JsonObject();
				b.addProperty("minX", w.box().minX);
				b.addProperty("minY", w.box().minY);
				b.addProperty("minZ", w.box().minZ);
				b.addProperty("maxX", w.box().maxX);
				b.addProperty("maxY", w.box().maxY);
				b.addProperty("maxZ", w.box().maxZ);
				o.add("aabb", b);
				if (w.title() != null) o.addProperty("title", w.title());
				if (w.type() != null) o.addProperty("type", w.type());
				arr.add(o);
			}
			root.add(room, arr);
		});
		return root;
	}
}
