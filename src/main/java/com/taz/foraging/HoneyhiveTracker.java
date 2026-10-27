package com.taz.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.util.TextUtil;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import com.taz.xpassist.progress.LocalProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the honeyhives you can loot in Torrhus Canyon, near or far. Hives sit in 8 clusters of 2-3 at fixed spots. */
public final class HoneyhiveTracker {
	public enum State {
		READY,
		REFILLING
	}

	public static final class Hive {
		/** The hive block. */
		public final BlockPos block;
		/** When it can be looted again (0 or past = now). */
		public long readyAtMs;
		/** Its hologram was in view at the last scan. */
		public boolean visible;
		/** Until then the hologram doesn't override a loot / Queen Bee message (it updates a moment later). */
		long chatHoldUntilMs;

		Hive(BlockPos block) {
			this.block = block;
		}

		public State state(long now) {
			return readyAtMs <= now ? State.READY : State.REFILLING;
		}

		public double distanceTo(Vec3 p) {
			return Math.sqrt(block.distToCenterSqr(p));
		}
	}

	/** A spot where 2-3 hives stand, from SkyHanni's map of the island. */
	public record Cluster(BlockPos pos) {
		public double distanceTo(Vec3 p) {
			return Math.sqrt(pos.distToCenterSqr(p));
		}
	}

	/** Hypixel's refill time, counted from looting (the hologram corrects it, e.g. with the Beekeeper perk). */
	private static final long REFILL_MS = 60 * 60_000L;
	/** A cluster counts as mapped once a hive is known this close to it. */
	private static final double CLUSTER_RADIUS = 10.0;
	private static final int SCAN_INTERVAL = 10;
	/** Dev: -Dtaz.foraging.anywhere=true runs on every island (and in singleplayer) for testing. */
	private static final boolean ANYWHERE = Boolean.getBoolean("taz.foraging.anywhere");
	private static final Pattern REFILL = Pattern.compile("^Refill in:? ?(?:(\\d+)h)? ?(?:(\\d+)m)? ?(?:(\\d+)s)?");
	private static final String LOOTED = "You stick your hand into the honeyhive and feel around...";
	private static final String QUEEN_BEE = "QUEEN BEE! The Honeyhive instantly refilled with loot!";
	private static final String BUNDLED = "/assets/taz/foraging/honeyhives.json";
	private static final Path FILE = Taz.CONFIG_DIR.resolve("foraging").resolve("honeyhives.json");

	/** Every known hive, by hive block. Refill times are the current profile's. */
	private static final Map<Long, Hive> HIVES = new LinkedHashMap<>();
	private static final List<Cluster> CLUSTERS = new ArrayList<>();
	private static boolean loaded;
	private static boolean active;
	private static String profile;
	private static int tickCounter;
	/** The closest ready hive, or else the closest cluster that isn't mapped yet (its hives are likely ready). */
	private static Hive nearestReady;
	private static Cluster nearestUnmapped;
	private static boolean dirty;

	private HoneyhiveTracker() {
	}

	public static boolean active() {
		return active;
	}

	public static Collection<Hive> hives() {
		return Collections.unmodifiableCollection(HIVES.values());
	}

	/** The closest hive you can loot, or null. */
	public static Hive nearestReady() {
		return nearestReady;
	}

	/** The closest cluster with no known hive yet, when it's closer than any ready hive; or null. */
	public static Cluster nearestUnmapped() {
		return nearestUnmapped;
	}

	/** Clusters with no known hive yet: shown as a marker until you walk there once. */
	public static List<Cluster> unmappedClusters() {
		List<Cluster> out = new ArrayList<>();
		for (Cluster c : CLUSTERS) if (!mapped(c)) out.add(c);
		return out;
	}

	private static boolean mapped(Cluster c) {
		for (Hive h : HIVES.values()) if (c.pos().distSqr(h.block) <= CLUSTER_RADIUS * CLUSTER_RADIUS) return true;
		return false;
	}

	public static boolean isTorrhus(String area) {
		return area != null && area.contains("Torrhus");
	}

	/** Called every client tick with the tab list's "Area:" (or null). */
	public static void tick(Minecraft mc, String area) {
		boolean want = HoneyhiveConfig.get().enabled && mc.level != null && mc.player != null && (ANYWHERE || isTorrhus(area));
		if (want != active) {
			active = want;
			nearestReady = null;
			nearestUnmapped = null;
			for (Hive h : HIVES.values()) h.visible = false;
		}
		if (!active) return;
		if (!loaded) loadPositions();
		String p = LocalProgress.activeProfile();
		if (!p.equals(profile)) loadRefills(p);
		if (++tickCounter % SCAN_INTERVAL != 0) return;
		scan(mc);
		updateNearest(mc.player.position());
		if (dirty) save();
	}

	/** From Taz.onSystemChat: your own loot and Queen Bee messages update the hive you're at. */
	public static void onChat(String line) {
		if (!active) return;
		boolean looted = line.equals(LOOTED), queen = line.equals(QUEEN_BEE);
		if (!looted && !queen) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Hive closest = null;
		double best = 8.0;
		for (Hive h : HIVES.values()) {
			double d = h.distanceTo(mc.player.position());
			if (d < best) {
				best = d;
				closest = h;
			}
		}
		if (closest == null) return;
		long now = System.currentTimeMillis();
		closest.readyAtMs = queen ? 0 : now + REFILL_MS;
		closest.chatHoldUntilMs = now + 3000;
		dirty = true;
		updateNearest(mc.player.position());
	}

	// ---- holograms

	private record Line(Vec3 pos, String text) {
	}

	private static void scan(Minecraft mc) {
		long now = System.currentTimeMillis();
		List<Line> titles = new ArrayList<>();
		List<Line> refills = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName()) continue;
			Component name = e.getCustomName();
			if (name == null) continue;
			String text = TextUtil.cleanScoreboard(name.getString());
			if (text.equals("Honeyhive")) titles.add(new Line(e.position(), text));
			else if (text.startsWith("Refill in")) refills.add(new Line(e.position(), text));
		}

		for (Hive h : HIVES.values()) h.visible = false;
		for (Line title : titles) {
			BlockPos block = findHiveBlock(mc.level, title.pos());
			Hive hive = HIVES.computeIfAbsent(block.asLong(), k -> {
				dirty = true; // a hive Taz didn't know yet: remembered for every profile
				return new Hive(block);
			});
			hive.visible = true;
			if (now < hive.chatHoldUntilMs) continue;
			Line refill = null;
			double best = 1.6;
			for (Line r : refills) {
				double dx = r.pos().x - title.pos().x, dz = r.pos().z - title.pos().z, dy = title.pos().y - r.pos().y;
				if (dx * dx + dz * dz > 1.0 || dy < -0.1 || dy > best) continue;
				best = dy;
				refill = r;
			}
			long readyAt = refill == null ? 0 : now + parseRefill(refill.text());
			// Keep a time Taz already knows unless the hologram moved it by more than a few seconds.
			if (Math.abs(readyAt - Math.max(hive.readyAtMs, now)) > 5000 || (readyAt == 0) != (hive.readyAtMs <= now)) {
				hive.readyAtMs = readyAt;
				dirty = true;
			}
		}
	}

	private static void updateNearest(Vec3 player) {
		long now = System.currentTimeMillis();
		nearestReady = null;
		nearestUnmapped = null;
		double nearest = Double.MAX_VALUE;
		for (Hive h : HIVES.values()) {
			if (h.state(now) != State.READY) continue;
			double d = h.distanceTo(player);
			if (d < nearest) {
				nearest = d;
				nearestReady = h;
			}
		}
		for (Cluster c : unmappedClusters()) {
			double d = c.distanceTo(player);
			if (d < nearest) {
				nearest = d;
				nearestUnmapped = c;
				nearestReady = null;
			}
		}
	}

	/** For the in-game test: forget everything so the saved file is read again. */
	public static void resetForTest() {
		HIVES.clear();
		CLUSTERS.clear();
		loaded = false;
		profile = null;
		nearestReady = null;
		nearestUnmapped = null;
	}

	/** For the in-game test. */
	public static long parseRefillForTest(String text) {
		return parseRefill(text);
	}

	/** Milliseconds from "Refill in: 1h 2m 3s" / "59m 46s" / "12s". */
	static long parseRefill(String text) {
		Matcher m = REFILL.matcher(text);
		if (!m.find()) return 0;
		long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
		long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
		long s = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
		return ((h * 60 + min) * 60 + s) * 1000;
	}

	/**
	 * The hive block under a hologram: a bee nest / beehive close by if there is one, otherwise the first
	 * solid block straight down (the hive's block can change when it refills, so its type isn't relied on).
	 */
	private static BlockPos findHiveBlock(ClientLevel level, Vec3 hologram) {
		BlockPos base = BlockPos.containing(hologram);
		BlockPos found = null;
		double best = Double.MAX_VALUE;
		for (int dy = 1; dy >= -4; dy--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos p = base.offset(dx, dy, dz);
					BlockState s = level.getBlockState(p);
					if (!s.is(Blocks.BEE_NEST) && !s.is(Blocks.BEEHIVE)) continue;
					double d = p.distToCenterSqr(hologram);
					if (d < best) {
						best = d;
						found = p;
					}
				}
			}
		}
		if (found != null) return found;
		for (int dy = 0; dy >= -4; dy--) {
			BlockPos p = base.offset(0, dy, 0);
			if (!level.getBlockState(p).isAir()) return p;
		}
		return BlockPos.containing(hologram.x, hologram.y - 1.5, hologram.z);
	}

	// ---- positions (shared) and refill times (per profile)

	/** Saved file: {"hives": [[x, y, z], ...], "refills": {"<profile>": {"x,y,z": readyAtMs}}}. */
	private static void loadPositions() {
		loaded = true;
		JsonObject bundled = readBundled();
		for (JsonElement e : array(bundled, "clusters")) CLUSTERS.add(new Cluster(pos(e)));
		for (JsonElement e : array(bundled, "hives")) addHive(pos(e));
		JsonObject saved = readFile();
		for (JsonElement e : array(saved, "hives")) addHive(pos(e));
		// First version: positions came with each profile's list.
		for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
			if (!entry.getValue().isJsonArray() || entry.getKey().equals("hives") || entry.getKey().equals("clusters")) continue;
			for (JsonElement e : entry.getValue().getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				addHive(new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
				dirty = true;
			}
		}
	}

	private static void addHive(BlockPos block) {
		HIVES.computeIfAbsent(block.asLong(), k -> new Hive(block));
	}

	private static void loadRefills(String newProfile) {
		profile = newProfile;
		for (Hive h : HIVES.values()) h.readyAtMs = 0;
		JsonObject saved = readFile();
		JsonObject refills = saved.has("refills") && saved.get("refills").isJsonObject() ? saved.getAsJsonObject("refills") : new JsonObject();
		if (refills.has(profile) && refills.get(profile).isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : refills.getAsJsonObject(profile).entrySet()) {
				String[] xyz = e.getKey().split(",");
				Hive h = HIVES.get(BlockPos.asLong(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
				if (h != null) h.readyAtMs = e.getValue().getAsLong();
			}
		} else if (saved.has(profile) && saved.get(profile).isJsonArray()) {
			for (JsonElement e : saved.getAsJsonArray(profile)) { // first version
				JsonObject o = e.getAsJsonObject();
				Hive h = HIVES.get(BlockPos.asLong(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
				if (h != null) h.readyAtMs = o.get("readyAt").getAsLong();
			}
		}
	}

	private static void save() {
		dirty = false;
		if (profile == null) return;
		JsonObject old = readFile();
		JsonObject refills = old.has("refills") && old.get("refills").isJsonObject() ? old.getAsJsonObject("refills") : new JsonObject();
		long now = System.currentTimeMillis();
		// Other profiles' times from the first version of the file carry over.
		for (Map.Entry<String, JsonElement> entry : old.entrySet()) {
			if (!entry.getValue().isJsonArray() || entry.getKey().equals("hives") || refills.has(entry.getKey())) continue;
			JsonObject times = new JsonObject();
			for (JsonElement e : entry.getValue().getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				long readyAt = o.get("readyAt").getAsLong();
				if (readyAt > now) times.addProperty(o.get("x").getAsInt() + "," + o.get("y").getAsInt() + "," + o.get("z").getAsInt(), readyAt);
			}
			refills.add(entry.getKey(), times);
		}
		JsonObject mine = new JsonObject();
		JsonArray hives = new JsonArray();
		for (Hive h : HIVES.values()) {
			JsonArray xyz = new JsonArray();
			xyz.add(h.block.getX());
			xyz.add(h.block.getY());
			xyz.add(h.block.getZ());
			hives.add(xyz);
			if (h.readyAtMs > now) mine.addProperty(h.block.getX() + "," + h.block.getY() + "," + h.block.getZ(), h.readyAtMs);
		}
		refills.add(profile, mine);
		JsonObject root = new JsonObject();
		root.add("hives", hives);
		root.add("refills", refills);
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, ModConfig.GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.error("Could not save {}", FILE, e);
		}
	}

	private static JsonObject readFile() {
		try {
			if (Files.exists(FILE)) {
				JsonElement e = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
				if (e.isJsonObject()) return e.getAsJsonObject();
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Could not read {}", FILE, e);
		}
		return new JsonObject();
	}

	private static JsonObject readBundled() {
		try (InputStream in = HoneyhiveTracker.class.getResourceAsStream(BUNDLED)) {
			if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			Taz.LOGGER.error("Could not read bundled {}", BUNDLED, e);
		}
		return new JsonObject();
	}

	private static JsonArray array(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	private static BlockPos pos(JsonElement e) {
		JsonArray a = e.getAsJsonArray();
		return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
	}

	// ---- status

	/** The "Hive status" button. */
	public static void printStatus() {
		if (!HoneyhiveConfig.get().enabled) {
			TazChat.info("Honeyhives are off.");
			return;
		}
		if (!active) {
			String area = SkyblockLocation.area();
			TazChat.info("Honeyhives only run in Torrhus Canyon (tab list Area: " + (area == null ? "not found" : area) + ")");
			return;
		}
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		int ready = 0;
		for (Hive h : HIVES.values()) if (h.state(now) == State.READY) ready++;
		List<Cluster> unmapped = unmappedClusters();
		TazChat.info(ready + " of " + HIVES.size() + " known hives ready on profile " + profile
				+ (unmapped.isEmpty() ? "; every cluster is mapped." : "; " + unmapped.size() + " clusters not mapped yet (walk there once)."));
		for (Hive h : HIVES.values()) {
			String when = h.state(now) == State.READY ? "ready" : "refills in " + formatLeft(h.readyAtMs - now);
			String dist = mc.player != null ? Math.round(h.distanceTo(mc.player.position())) + "m, " : "";
			TazChat.info("  " + h.block.getX() + " " + h.block.getY() + " " + h.block.getZ() + " (" + dist + when + ")");
		}
		for (Cluster c : unmapped) {
			String dist = mc.player != null ? Math.round(c.distanceTo(mc.player.position())) + "m, " : "";
			TazChat.info("  cluster " + c.pos().getX() + " " + c.pos().getY() + " " + c.pos().getZ() + " (" + dist + "not mapped)");
		}
	}

	/** 3540000 -> "59m", 75000 -> "1m 15s". */
	public static String formatLeft(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
		if (s >= 600) return (s / 60) + "m";
		if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
		return s + "s";
	}

	/** On server change: hologram state is per lobby, saved positions and refill times stay. */
	public static void onServerChange() {
		for (Hive h : HIVES.values()) h.visible = false;
		nearestReady = null;
		nearestUnmapped = null;
	}
}
