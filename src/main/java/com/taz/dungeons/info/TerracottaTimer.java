package com.taz.dungeons.info;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Terracotta Timer: in the Floor 6 boss, when a Terracotta dies it leaves a flower pot, and it comes back 15 seconds
 * later (12 in Master Mode). From Odin and NoammAddons.
 */
public final class TerracottaTimer {
	/** Pot -> ticks until its Terracotta is back. */
	private static final Map<BlockPos, Integer> POTS = new LinkedHashMap<>();

	private TerracottaTimer() {
	}

	/** A block changed: a new flower pot in the Floor 6 boss. */
	public static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		if (!TerracottaTimerConfig.get().enabled || DungeonLocation.floorNumber() != 6 || !DungeonLocation.inBoss()) return;
		if (now.getBlock() instanceof FlowerPotBlock && !(old.getBlock() instanceof FlowerPotBlock)) {
			POTS.putIfAbsent(pos.immutable(), DungeonLocation.masterMode() ? 240 : 300);
		}
	}

	/** Every server tick. */
	public static void onServerTick() {
		POTS.replaceAll((pos, t) -> t - 1);
		POTS.values().removeIf(t -> t <= 0);
	}

	/** Test hook: ticks left at a pot, or -1. */
	public static int left(BlockPos pos) {
		return POTS.getOrDefault(pos, -1);
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		if (!TerracottaTimerConfig.get().enabled || POTS.isEmpty() || !DungeonLocation.inBoss()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			POTS.forEach((pos, ticks) -> {
				double s = ticks / 20.0;
				int color = s > 5 ? 0xFFFFFFFF : s > 2 ? 0xFFFFB0B0 : 0xFFFF2E2E;
				batch.label(Vec3.atCenterOf(pos).add(0, 0.6, 0), String.format("%.1fs", s), color, true);
			});
		} catch (Exception e) {
			Taz.LOGGER.error("Terracotta timers failed to draw", e);
		}
	}
}
