package com.taz.dungeons.info;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Mage Beam: in dungeons, the Mage's beam (a line of firework sparks the server sends all at once) drawn as one solid red
 * line for a couple of seconds, and the sparks hidden. From Odin's Mage Beam.
 */
public final class MageBeam {
	private static final class Beam {
		final List<Vec3> points = new ArrayList<>();
		long updated;
		final long born;

		Beam(Vec3 p, long now) {
			points.add(p);
			updated = born = now;
		}

		boolean inLine(Vec3 p) {
			if (points.size() <= 1) return true;
			Vec3 first = points.getFirst(), last = points.getLast();
			return last.subtract(first).normalize().dot(p.subtract(last).normalize()) > 0.99;
		}
	}

	private static final List<Beam> BEAMS = new ArrayList<>();

	private MageBeam() {
	}

	/** A particle from the server; true hides it. */
	public static boolean onParticle(ParticleOptions particle, double x, double y, double z) {
		MageBeamConfig cfg = MageBeamConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon() || particle.getType() != ParticleTypes.FIREWORK) return false;
		long now = System.currentTimeMillis();
		Vec3 p = new Vec3(x, y, z);
		Beam recent = BEAMS.isEmpty() ? null : BEAMS.getLast();
		if (recent != null && now - recent.updated < 50 && recent.inLine(p)) {
			recent.points.add(p);
			recent.updated = now;
		} else {
			BEAMS.add(new Beam(p, now));
		}
		return cfg.hideParticles;
	}

	/** Test hook: beams being drawn. */
	public static int beams() {
		return (int) BEAMS.stream().filter(b -> b.points.size() >= MageBeamConfig.get().minPoints).count();
	}

	public static void reset() {
		BEAMS.clear();
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		MageBeamConfig cfg = MageBeamConfig.get();
		long now = System.currentTimeMillis();
		BEAMS.removeIf(b -> now - b.born > cfg.ticks * 50L);
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || BEAMS.isEmpty() || mc.player == null) return;
		Vec3 me = mc.player.position();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (Beam b : BEAMS) {
				if (b.points.size() < cfg.minPoints) continue;
				Vec3 near = b.points.getFirst(), far = near;
				for (Vec3 p : b.points) {
					if (p.distanceToSqr(me) < near.distanceToSqr(me)) near = p;
					if (p.distanceToSqr(me) > far.distanceToSqr(me)) far = p;
				}
				if (near.equals(far)) continue;
				batch.line(near, far, 0xFFB3121B, 8f, cfg.throughWalls);
				batch.line(near, far, 0xFFFF2E2E, 3f, cfg.throughWalls);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Mage beam failed to draw", e);
		}
	}
}
