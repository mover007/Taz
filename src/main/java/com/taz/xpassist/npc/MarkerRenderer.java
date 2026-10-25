package com.taz.xpassist.npc;

import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws a box, a beam and a label with the depth test turned off, so they show through walls. */
public final class MarkerRenderer {
	private MarkerRenderer() {
	}

	public record Marker(AABB box, double beamHeight, String label, int rgb, boolean tracer) {
	}

	public static void render(LevelRenderContext context, Marker marker) {
		int solid = 0xFF000000 | marker.rgb();
		int fill = 0x45000000 | marker.rgb();
		AABB box = marker.box();
		Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY, (box.minZ + box.maxZ) / 2);
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			batch.filled(box, fill, true);
			batch.outline(box, solid, 2.5F, true);
			if (marker.beamHeight() > 0) {
				batch.line(top, top.add(0, marker.beamHeight(), 0), solid, 4.0F, true);
			}
			if (marker.tracer()) {
				batch.line(WorldRenderer.crosshair(batch.camera), top.add(0, -1, 0), solid, 2.0F, true);
			}
			batch.label(top.add(0, 0.6, 0), marker.label(), solid, true);
		}
	}
}
