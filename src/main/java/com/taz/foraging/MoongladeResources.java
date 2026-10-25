package com.taz.foraging;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Sea Lumies (sea pickles) and Lushlilac (flowering azalea) on Moonglade Marsh. */
public final class MoongladeResources {
	public static final BlockFinder LUMIES = new BlockFinder(s -> s.is(Blocks.SEA_PICKLE));
	public static final BlockFinder LUSHLILAC = new BlockFinder(s -> s.is(Blocks.FLOWERING_AZALEA));
	private static final int DRAW_LIMIT = 100;

	private MoongladeResources() {
	}

	public static void tick(Minecraft mc) {
		MoongladeResourcesConfig cfg = MoongladeResourcesConfig.get();
		boolean on = cfg.enabled && Galatea.inMoonglade();
		LUMIES.tick(mc, on && cfg.seaLumies);
		LUSHLILAC.tick(mc, on && cfg.lushlilac);
	}

	/** A Sea Lumies clump big enough to show. */
	public static boolean bigEnough(BlockState state) {
		return state.is(Blocks.SEA_PICKLE) && state.getValue(SeaPickleBlock.PICKLES) >= MoongladeResourcesConfig.get().minLumies;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		MoongladeResourcesConfig cfg = MoongladeResourcesConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || mc.level == null || !LUMIES.active() && !LUSHLILAC.active()) return;
		int lumies = 0xFF000000 | (cfg.lumiesColour.getEffectiveColourRGB() & 0xFFFFFF);
		int lilac = 0xFF000000 | (cfg.lushlilacColour.getEffectiveColourRGB() & 0xFFFFFF);
		double range = cfg.range * (double) cfg.range;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			BlockPos closest = null;
			int closestColor = 0;
			double closestD = Double.MAX_VALUE;
			int drawn = 0;
			for (BlockPos p : LUMIES.nearest(batch.camera, DRAW_LIMIT)) {
				double d = Vec3.atCenterOf(p).distanceToSqr(batch.camera);
				if (d > range || !bigEnough(mc.level.getBlockState(p))) continue;
				box(batch, p, lumies, cfg.throughWalls);
				drawn++;
				if (d < closestD) {
					closestD = d;
					closest = p;
					closestColor = lumies;
				}
			}
			for (BlockPos p : LUSHLILAC.nearest(batch.camera, DRAW_LIMIT)) {
				double d = Vec3.atCenterOf(p).distanceToSqr(batch.camera);
				if (d > range) continue;
				box(batch, p, lilac, cfg.throughWalls);
				drawn++;
				if (d < closestD) {
					closestD = d;
					closest = p;
					closestColor = lilac;
				}
			}
			if (cfg.pointer && closest != null && drawn > 0) {
				batch.line(WorldRenderer.crosshair(batch.camera), Vec3.atCenterOf(closest), (closestColor & 0xFFFFFF) | 0xC0000000, 2.0f, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Sea Lumies / Lushlilac highlight failed", e);
		}
	}

	private static void box(WorldRenderer.Batch batch, BlockPos p, int color, boolean throughWalls) {
		AABB box = new AABB(p).deflate(0.1);
		batch.filled(box, (color & 0xFFFFFF) | 0x40000000, throughWalls);
		batch.outline(box, color, 2.0f, throughWalls);
	}
}
