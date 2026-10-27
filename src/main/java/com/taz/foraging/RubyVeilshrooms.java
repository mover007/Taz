package com.taz.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ruby Veilshrooms are crimson fungus blocks in the caves at the bottom of Torrhus Springs. */
public final class RubyVeilshrooms extends TextHud {
	/** Top of Torrhus Springs: dive down from here. */
	public static final BlockPos SPRINGS = new BlockPos(-646, 161, 200);
	private static final long RESPAWN_MIN_MS = 50_000;
	private static final long RESPAWN_MAX_MS = 55_000;
	/** A veilshroom going this close to you was picked by you. */
	private static final double MINE = 6.0;
	private static final long PAUSE_MS = 60_000;
	private static final int DRAW_LIMIT = 80;
	private static final Path FILE = Taz.CONFIG_DIR.resolve("foraging").resolve("veilshrooms.json");

	public static final BlockFinder FINDER = new BlockFinder(s -> s.is(Blocks.CRIMSON_FUNGUS))
			.onFound(RubyVeilshrooms::found)
			.onGone(RubyVeilshrooms::gone);
	/** Spot -> when it was picked. */
	private static final Map<BlockPos, Long> PICKED = new HashMap<>();
	private static final Set<BlockPos> KNOWN = new LinkedHashSet<>();
	private static boolean loaded;
	private static boolean dirty;
	private static int harvested;
	private static long activeMs;
	private static long lastHarvestMs;
	private static long lastTickMs;

	public RubyVeilshrooms() {
		super("Ruby Veilshrooms", 0.30, 0.20);
	}

	// ---- tracking

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		VeilshroomConfig cfg = VeilshroomConfig.get();
		boolean want = cfg.enabled && Galatea.inTorrhus();
		if (want && !loaded) load();
		FINDER.tick(mc, want);
		long now = System.currentTimeMillis();
		long step = lastTickMs == 0 ? 0 : Math.min(now - lastTickMs, 1000);
		lastTickMs = now;
		if (want && now - lastHarvestMs < PAUSE_MS && lastHarvestMs > 0) activeMs += step;
		PICKED.values().removeIf(t -> now - t > RESPAWN_MAX_MS * 3); // never came back while loaded
		if (dirty) save();
	}

	private static void found(BlockPos pos) {
		PICKED.remove(pos);
		if (KNOWN.add(pos)) dirty = true;
	}

	private static void gone(BlockPos pos) {
		long now = System.currentTimeMillis();
		PICKED.put(pos, now);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= MINE * MINE) {
			harvested++;
			lastHarvestMs = now;
		}
	}

	/** Seconds until a picked spot is back (the earliest it can be), or -1 if it isn't picked. */
	public static long respawnSeconds(BlockPos pos) {
		Long t = PICKED.get(pos);
		if (t == null) return -1;
		return Math.max(0, (RESPAWN_MIN_MS - (System.currentTimeMillis() - t) + 999) / 1000);
	}

	/** The closest one ready to pick, or a known spot not loaded right now, or null. */
	public static BlockPos next(Vec3 from) {
		List<BlockPos> ready = FINDER.nearest(from, 1);
		if (!ready.isEmpty()) return ready.get(0);
		Minecraft mc = Minecraft.getInstance();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : KNOWN) {
			if (mc.level != null && mc.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue; // loaded: the finder knows
			double d = Vec3.atCenterOf(p).distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public static int harvested() {
		return harvested;
	}

	public static int knownCount() {
		return KNOWN.size();
	}

	public static void resetCounts() {
		harvested = 0;
		activeMs = 0;
		lastHarvestMs = 0;
		TazChat.info("Ruby Veilshroom counter reset.");
	}

	/** On server change: the blocks are found again on the new server. */
	public static void onServerChange() {
		FINDER.reset();
		PICKED.clear();
	}

	// ---- saved spots

	private static void load() {
		loaded = true;
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("spots")) {
				JsonArray a = e.getAsJsonArray();
				KNOWN.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	private static void save() {
		dirty = false;
		try {
			JsonArray spots = new JsonArray();
			for (BlockPos p : KNOWN) {
				JsonArray a = new JsonArray();
				a.add(p.getX());
				a.add(p.getY());
				a.add(p.getZ());
				spots.add(a);
			}
			JsonObject root = new JsonObject();
			root.add("spots", spots);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	// ---- drawing

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		VeilshroomConfig cfg = VeilshroomConfig.get();
		if (!cfg.enabled || !FINDER.active()) return;
		int color = cfg.argb();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.highlight) {
				for (BlockPos p : FINDER.nearest(batch.camera, DRAW_LIMIT)) {
					AABB box = new AABB(p).deflate(0.2, 0, 0.2);
					batch.filled(box, (color & 0xFFFFFF) | 0x50000000, cfg.throughWalls);
					batch.outline(box, color, 2.5f, cfg.throughWalls);
				}
			}
			if (cfg.timers) {
				for (BlockPos p : new ArrayList<>(PICKED.keySet())) {
					if (Vec3.atCenterOf(p).distanceToSqr(batch.camera) > 64 * 64) continue;
					long s = respawnSeconds(p);
					batch.label(Vec3.atBottomCenterOf(p), s > 0 ? "Respawns in ~" + s + "s" : "Respawning any moment", 0xFFAAAAAA, cfg.throughWalls);
				}
			}
			BlockPos next = next(batch.camera);
			BlockPos soonest = null;
			for (BlockPos p : PICKED.keySet()) {
				if (soonest == null || PICKED.get(p) < PICKED.get(soonest)) soonest = p;
			}
			if (cfg.pointer && (next != null || soonest != null)) {
				// Nothing ready: point at the one that comes back first.
				Vec3 target = Vec3.atCenterOf(next != null ? next : soonest);
				String what = next != null ? "Next veilshroom: " : "Back in ~" + respawnSeconds(soonest) + "s: ";
				batch.line(WorldRenderer.crosshair(batch.camera), target, (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
				batch.label(target.add(0, 0.6, 0), what + Math.round(Math.sqrt(target.distanceToSqr(batch.camera))) + "m", color, true, 1);
			} else if (cfg.guide && next == null && KNOWN.isEmpty()) {
				Vec3 base = Vec3.atBottomCenterOf(SPRINGS);
				batch.line(base.add(0, -40, 0), base.add(0, 60, 0), (color & 0xFFFFFF) | 0xA0000000, 4.0f, true);
				batch.label(base.add(0, 2, 0), "Ruby Veilshrooms: dive down Torrhus Springs here", color, true);
				batch.label(base.add(0, 2, 0), "the caves are near the bottom, past Ant's Cave", 0xFFDDDDDD, true, 1);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Ruby Veilshroom highlight failed", e);
		}
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return VeilshroomConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return VeilshroomConfig.get().enabled && VeilshroomConfig.get().hudEnabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (!preview && !FINDER.active()) return List.of();
		List<String> lines = new ArrayList<>();
		lines.add("§c§lRuby Veilshrooms");
		lines.add("§fReady §a" + FINDER.count() + " §7· respawning §e" + PICKED.size());
		long rate = activeMs < 10_000 ? -1 : Math.round(harvested * 4 * 3_600_000.0 / activeMs);
		lines.add("§fPicked §c" + harvested + " §7(" + harvested * 4 + " items)" + (rate < 0 ? "" : " §7· §f" + String.format("%,d", rate) + "/h"));
		if (FINDER.count() == 0 && KNOWN.isEmpty()) lines.add("§7Dive down Torrhus Springs to the caves");
		return lines;
	}
}
