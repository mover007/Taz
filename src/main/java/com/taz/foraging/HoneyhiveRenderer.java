package com.taz.foraging;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Honeyhives, from anywhere on the island: a box round every hive you can loot, refill times over the
 * ones you can't, a beam over clusters not mapped yet, and a line plus "Next hive: 23m" to the closest.
 */
public final class HoneyhiveRenderer {
	private static final int TIMER_COLOR = 0xFFAAAAAA;
	private static final double BEAM_HEIGHT = 40;

	private HoneyhiveRenderer() {
	}

	/** Puts a hive in the group of hives within 8 blocks of it (one cluster), or a new group. */
	private static void group(List<List<HoneyhiveTracker.Hive>> groups, HoneyhiveTracker.Hive h) {
		for (List<HoneyhiveTracker.Hive> g : groups) {
			for (HoneyhiveTracker.Hive other : g) {
				if (other.block.distSqr(h.block) <= 64) {
					g.add(h);
					return;
				}
			}
		}
		List<HoneyhiveTracker.Hive> g = new ArrayList<>();
		g.add(h);
		groups.add(g);
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		if (!HoneyhiveTracker.active()) return;
		HoneyhiveConfig cfg = HoneyhiveConfig.get();
		long now = System.currentTimeMillis();
		int color = cfg.argb();
		int faint = (color & 0x00FFFFFF) | 0x90000000;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			List<List<HoneyhiveTracker.Hive>> groups = new ArrayList<>();
			for (HoneyhiveTracker.Hive h : HoneyhiveTracker.hives()) {
				if (h.state(now) == HoneyhiveTracker.State.READY) {
					AABB box = new AABB(h.block).inflate(0.02);
					batch.filled(box, (color & 0x00FFFFFF) | 0x40000000, cfg.throughWalls);
					batch.outline(box, color, 3.0f, cfg.throughWalls);
				}
				// Hypixel's own hologram labels hives close by; far ones get one Taz label per cluster.
				if (!h.visible) group(groups, h);
			}
			for (List<HoneyhiveTracker.Hive> g : groups) {
				int ready = 0;
				long soonest = Long.MAX_VALUE;
				Vec3 center = Vec3.ZERO;
				for (HoneyhiveTracker.Hive h : g) {
					center = center.add(Vec3.atCenterOf(h.block).scale(1.0 / g.size()));
					if (h.state(now) == HoneyhiveTracker.State.READY) ready++;
					else soonest = Math.min(soonest, h.readyAtMs);
				}
				Vec3 above = center.add(0, 1.3, 0);
				if (ready > 0) {
					batch.label(above, ready == 1 ? "Honeyhive ready" : ready + " honeyhives ready", color, cfg.throughWalls);
				} else if (cfg.timers) {
					String more = g.size() > 1 ? " (" + g.size() + " hives)" : "";
					batch.label(above, "Refill in " + HoneyhiveTracker.formatLeft(soonest - now) + more, TIMER_COLOR, cfg.throughWalls);
				}
			}
			// Clusters with no known hive yet: a beam to walk to; the hives are mapped once you're there.
			for (HoneyhiveTracker.Cluster c : HoneyhiveTracker.unmappedClusters()) {
				Vec3 base = Vec3.atCenterOf(c.pos());
				batch.line(base, base.add(0, BEAM_HEIGHT, 0), faint, 3.0f, true);
				batch.label(base.add(0, 2.5, 0), "Honeyhives here (walk here once to map them)", color, true);
			}

			if (cfg.pointer) {
				Vec3 target = null;
				String text = null;
				HoneyhiveTracker.Hive next = HoneyhiveTracker.nearestReady();
				HoneyhiveTracker.Cluster cluster = HoneyhiveTracker.nearestUnmapped();
				if (next != null) {
					target = Vec3.atCenterOf(next.block);
					text = "Next hive: " + Math.round(next.distanceTo(batch.camera)) + "m";
				} else if (cluster != null) {
					target = Vec3.atCenterOf(cluster.pos());
					text = "Next hives: " + Math.round(cluster.distanceTo(batch.camera)) + "m (not mapped yet)";
				}
				if (target != null) {
					batch.line(WorldRenderer.crosshair(batch.camera), target, (color & 0x00FFFFFF) | 0xC0000000, 2.0f, true);
					batch.label(target.add(0, 1.3, 0), text, color, true, 1); // one line above the cluster label
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Honeyhive highlight failed", e); // never break rendering
		}
	}
}
