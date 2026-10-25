package com.taz.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import com.taz.xpassist.progress.LocalProgress;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The Fairy Souls of Moonglade Marsh (MM) and Torrhus Canyon (TC), from the wiki. Found ones are kept per profile. */
public final class FairySouls {
	public record Soul(String id, BlockPos pos, String hint) {
		boolean torrhus() {
			return id.startsWith("TC");
		}
	}

	public static final List<Soul> SOULS = List.of(
			new Soul("MM-1", new BlockPos(-565, 95, 17), "Pool left of the entrance"),
			new Soul("MM-2", new BlockPos(-601, 128, -20), "Small room near the top of Fusion House"),
			new Soul("MM-3", new BlockPos(-598, 107, 16), "Basement of Tangleburg Library"),
			new Soul("MM-4", new BlockPos(-556, 94, 60), "Behind a waterfall"),
			new Soul("MM-5", new BlockPos(-663, 64, 62), "Lower Altar"),
			new Soul("MM-6", new BlockPos(-737, 101, -17), "Tranquility Sanctum, behind a water source"),
			new Soul("MM-7", new BlockPos(-707, 131, 1), "Bottom floor of Murkwater Outpost"),
			new Soul("MM-8", new BlockPos(-702, 169, -2), "Top floor of Murkwater Outpost"),
			new Soul("MM-9", new BlockPos(-586, 90, -26), "Wyrmgrove Tomb, behind some fire"),
			new Soul("MM-10", new BlockPos(-620, 66, 32), "Secret cave behind Tomb Floodway"),
			new Soul("MM-11", new BlockPos(-621, 108, -86), "Red House, inside the chimney"),
			new Soul("MM-12", new BlockPos(-696, 76, -44), "Stride-Ember Fissure, below the lava"),
			new Soul("TC-1", new BlockPos(-618, 125, 179), "Spring Depths, from a cave entrance"),
			new Soul("TC-2", new BlockPos(-603, 167, 234), "Torrhus Heights, cave entrance on the east side"),
			new Soul("TC-3", new BlockPos(-706, 98, 142), "Side entrance on top of yellow wool by the entrance"),
			new Soul("TC-4", new BlockPos(-565, 136, 243), "On top of a crane (jump from above)"),
			new Soul("TC-5", new BlockPos(-640, 90, 158), "Edge of the island by some sculk"),
			new Soul("TC-6", new BlockPos(-677, 102, 262), "Mushroom ledge"),
			new Soul("TC-7", new BlockPos(-627, 122, 285), "Towards the edge of the island"),
			new Soul("TC-8", new BlockPos(-567, 81, 272), "Towards the edge of the island"),
			new Soul("TC-9", new BlockPos(-554, 94, 256), "Cave on the island's edge (entrance at -526 86 247)"),
			new Soul("TC-10", new BlockPos(-678, 62, 258), "Cave, entrance at -689 82 285"),
			new Soul("TC-11", new BlockPos(-711, 59, 290), "Side of the island towards the bottom"),
			new Soul("TC-12", new BlockPos(-727, 125, 152), "Near the Critter Safari airships"));

	private static final Path FILE = Taz.CONFIG_DIR.resolve("foraging").resolve("fairysouls.json");
	/** Picking a soul up happens right next to it. */
	private static final double PICKUP = 8.0;
	private static final double LABEL_RANGE = 64;
	private static final Map<String, Set<String>> FOUND = new HashMap<>();
	private static boolean loaded;

	private FairySouls() {
	}

	private static Set<String> found() {
		if (!loaded) load();
		return FOUND.computeIfAbsent(LocalProgress.activeProfile(), k -> new HashSet<>());
	}

	public static boolean isFound(Soul s) {
		return found().contains(s.id());
	}

	/** Souls on the island you're on that you haven't found. */
	public static List<Soul> missing() {
		boolean torrhus = Galatea.inTorrhus(), moonglade = Galatea.inMoonglade();
		return SOULS.stream().filter(s -> !isFound(s) && (s.torrhus() ? torrhus : moonglade)).toList();
	}

	/** A plain-text chat line: picking up a soul (or one you had) ticks off the closest. */
	public static void onChat(String line) {
		if (!FairySoulsConfig.get().enabled) return;
		if (!line.startsWith("SOUL! You found a Fairy Soul!") && !line.startsWith("You have already found that Fairy Soul!")) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Soul best = null;
		double bestD = PICKUP * PICKUP;
		for (Soul s : SOULS) {
			double d = mc.player.position().distanceToSqr(Vec3.atCenterOf(s.pos()));
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		if (best != null && found().add(best.id())) {
			save();
			long left = SOULS.stream().filter(s -> !isFound(s)).count();
			TazChat.info("Fairy Soul " + best.id() + " ticked off. " + left + " left on Galatea.");
		}
	}

	public static void markAllFound() {
		for (Soul s : SOULS) found().add(s.id());
		save();
		TazChat.info("Every Galatea Fairy Soul marked as found.");
	}

	public static void forgetFound() {
		found().clear();
		save();
		TazChat.info("Showing every Galatea Fairy Soul again.");
	}

	private static void load() {
		loaded = true;
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String profile : root.keySet()) {
				Set<String> ids = new HashSet<>();
				for (JsonElement e : root.getAsJsonArray(profile)) ids.add(e.getAsString());
				FOUND.put(profile, ids);
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	private static void save() {
		try {
			JsonObject root = new JsonObject();
			for (Map.Entry<String, Set<String>> e : FOUND.entrySet()) {
				JsonArray ids = new JsonArray();
				e.getValue().stream().sorted().forEach(ids::add);
				root.add(e.getKey(), ids);
			}
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		FairySoulsConfig cfg = FairySoulsConfig.get();
		if (!cfg.enabled || !Galatea.onIslands()) return;
		List<Soul> missing = missing();
		if (missing.isEmpty()) return;
		int color = cfg.argb();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			Soul closest = null;
			double closestD = Double.MAX_VALUE;
			for (Soul s : missing) {
				double d = Vec3.atCenterOf(s.pos()).distanceToSqr(batch.camera);
				if (d < closestD) {
					closestD = d;
					closest = s;
				}
			}
			for (Soul s : missing) {
				Vec3 c = Vec3.atCenterOf(s.pos());
				AABB box = new AABB(s.pos()).deflate(0.15);
				batch.filled(box, (color & 0xFFFFFF) | 0x40000000, true);
				batch.outline(box, color, 2.0f, true);
				batch.line(c, c.add(0, 25, 0), (color & 0xFFFFFF) | 0x80000000, 2.0f, true);
				// Names and hints only near you (and for the closest), so far-off souls don't fill the sky with text.
				if (s != closest && c.distanceToSqr(batch.camera) > LABEL_RANGE * LABEL_RANGE) continue;
				batch.label(c.add(0, 0.8, 0), "Fairy Soul " + s.id() + "  " + Math.round(Math.sqrt(c.distanceToSqr(batch.camera))) + "m", color, true, 1);
				if (cfg.hints) batch.label(c.add(0, 0.8, 0), s.hint(), 0xFFDDDDDD, true);
			}
			if (cfg.pointer && closest != null) {
				batch.line(WorldRenderer.crosshair(batch.camera), Vec3.atCenterOf(closest.pos()), (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Fairy Soul waypoints failed", e);
		}
	}
}
