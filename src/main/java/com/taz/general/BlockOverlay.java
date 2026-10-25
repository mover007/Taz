package com.taz.general;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block Overlay: the block you're looking at gets a red box (fill and outline) in place of the thin black outline. From
 * NoammAddons' Block Overlay.
 */
public final class BlockOverlay {
	private BlockOverlay() {
	}

	/** Whether to replace the vanilla outline (LevelRenderEvents.BEFORE_BLOCK_OUTLINE). */
	public static boolean replaces() {
		BlockOverlayConfig cfg = BlockOverlayConfig.get();
		if (!cfg.enabled) return false;
		Minecraft mc = Minecraft.getInstance();
		return !(cfg.hideWithEtherwarp && mc.player != null && mc.player.isShiftKeyDown() && EtherwarpGuess.range(mc.player.getMainHandItem()) >= 0);
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		BlockOverlayConfig cfg = BlockOverlayConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || mc.options.hideGui || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		if (!replaces()) return;
		BlockPos pos = hit.getBlockPos();
		VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
		if (shape.isEmpty()) return;
		AABB box = shape.bounds().move(pos).inflate(0.002);
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.fill) batch.filled(box, 0x30FF2E2E, cfg.throughWalls);
			if (cfg.outline) batch.outline(box, 0xFFFF2E2E, cfg.lineWidth, cfg.throughWalls);
		} catch (Exception e) {
			Taz.LOGGER.error("Block overlay failed to draw", e);
		}
	}
}
