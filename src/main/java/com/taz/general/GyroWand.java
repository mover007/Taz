package com.taz.general;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonParty;
import com.taz.render.WorldRenderer;
import com.taz.util.EtherRaycast;
import com.taz.util.Items;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Gyro Wand: while you hold the Gyrokinetic Wand, a ring where its pull would land (10 blocks round the block you look at,
 * up to 25 away), red when it's ready and grey on cooldown. The cooldown starts from the wand's burst of witch particles
 * and counts the Mage's reduction. From Odin's Gyro Wand.
 */
public final class GyroWand {
	private static final double RADIUS = 10, RANGE = 25;
	private static final int SEGMENTS = 64;
	private static long burstStart, lastUse;
	private static double burstY;
	private static int burstCount;

	private GyroWand() {
	}

	public static boolean onCooldown() {
		return System.currentTimeMillis() - lastUse < DungeonParty.cooldown(30) * 1000;
	}

	/** A particle from the server: five still witch particles at one height in a moment is the wand going off. */
	public static void onParticle(ParticleOptions particle, double x, double y, double z, float dx, float dy, float dz) {
		if (particle.getType() != ParticleTypes.WITCH || dx != 0 || dy != 0 || dz != 0 || onCooldown()) return;
		long now = System.currentTimeMillis();
		if (now - burstStart > 120 || Math.abs(y - burstY) > 0.02) {
			burstStart = now;
			burstY = y;
			burstCount = 1;
		} else if (++burstCount >= 5) {
			lastUse = now;
			burstCount = 0;
		}
	}

	public static void reset() {
		lastUse = burstStart = 0;
		burstCount = 0;
	}

	/** Where the pull would land, or null. */
	public static Vec3 target() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !Items.id(mc.player.getMainHandItem()).equals("GYROKINETIC_WAND")) return null;
		EtherRaycast.Hit hit = EtherRaycast.cast(mc.player.position(), RANGE, false, mc.player.isCrouching());
		if (hit == null || hit.state() == null || hit.state().isAir()) return null;
		return Vec3.atLowerCornerOf(hit.pos()).add(0.5, 1, 0.5);
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		GyroWandConfig cfg = GyroWandConfig.get();
		if (!cfg.enabled) return;
		Vec3 c = target();
		if (c == null) return;
		int color = cfg.cooldown && onCooldown() ? 0xFF6E5A5A : 0xFFFF2E2E;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (double h : new double[] {0, 0.3}) {
				for (int i = 0; i < SEGMENTS; i++) {
					double a = Math.PI * 2 * i / SEGMENTS, b = Math.PI * 2 * (i + 1) / SEGMENTS;
					batch.line((float) (c.x + Math.cos(a) * RADIUS), (float) (c.y + h), (float) (c.z + Math.sin(a) * RADIUS),
							(float) (c.x + Math.cos(b) * RADIUS), (float) (c.y + h), (float) (c.z + Math.sin(b) * RADIUS), color, 2.5f, cfg.throughWalls);
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Gyro wand ring failed to draw", e);
		}
	}
}
