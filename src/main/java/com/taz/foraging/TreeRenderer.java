package com.taz.foraging;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.phys.Vec3;

/** The next-tree pointer: a line from the crosshair and "Next: Fig 23m (84 logs)" over the closest grown tree. */
public final class TreeRenderer {
	private TreeRenderer() {
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		TreeTracker.Tree next = TreeTracker.nearestGrown();
		if (!TreeTracker.active() || next == null || next.logs == 0) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			int color = TreesConfig.get().colour(next.kind);
			Vec3 eye = batch.camera;
			Vec3 target = new Vec3((next.minX + next.maxX + 1) / 2.0, next.minY + 1.0, (next.minZ + next.maxZ + 1) / 2.0);
			Vec3 top = new Vec3((next.minX + next.maxX + 1) / 2.0, next.maxY + 1.6, (next.minZ + next.maxZ + 1) / 2.0);
			batch.line(WorldRenderer.crosshair(eye), target, (color & 0x00FFFFFF) | 0xC0000000, 2.0f, true);
			batch.label(top, "Next: " + next.kind.label + " " + Math.round(next.distanceTo(eye)) + "m (" + next.logs + " logs)", color, true);
		} catch (Exception e) {
			Taz.LOGGER.error("Tree pointer failed", e); // never break rendering
		}
	}
}
