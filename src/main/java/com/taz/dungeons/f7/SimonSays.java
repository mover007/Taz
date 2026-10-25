package com.taz.dungeons.f7;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simon Says: the Phase 3 device's buttons in the order the lanterns lit: the next one red, then pale red, the rest dark
 * red. Fixes the first round's order the way Odin does (the server lights them oddly), and can say your progress in party
 * chat. From Odin.
 */
public final class SimonSays {
	private static final BlockPos START = new BlockPos(110, 121, 91);
	private static final List<BlockPos> ORDER = new ArrayList<>();
	private static int next, sinceLantern = -1;
	private static boolean firstRound = true;

	private SimonSays() {
	}

	private static boolean here() {
		return SimonSaysConfig.get().enabled && DungeonLocation.f7Phase() == 3;
	}

	private static void resetOrder() {
		ORDER.clear();
		next = 0;
		sinceLantern = -1;
	}

	public static void reset() {
		resetOrder();
		firstRound = true;
	}

	public static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		if (!here()) return;
		if (pos.equals(START) && now.is(Blocks.STONE_BUTTON) && now.getValue(ButtonBlock.POWERED)) {
			resetOrder();
			firstRound = true;
			return;
		}
		if (pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95) return;
		if (pos.getX() == 111 && now.is(Blocks.OBSIDIAN) && old.is(Blocks.SEA_LANTERN) && !ORDER.contains(pos)) {
			ORDER.add(pos.immutable());
			sinceLantern = 0;
			if (!firstRound) return;
			if (ORDER.size() == 2) Collections.reverse(ORDER);
			else if (ORDER.size() == 3) ORDER.remove(ORDER.size() - 2);
		} else if (pos.getX() == 110) {
			if (now.isAir()) {
				if (gridCount(true) > 8) resetOrder();
			} else if (old.is(Blocks.STONE_BUTTON) && now.is(Blocks.STONE_BUTTON) && now.getValue(ButtonBlock.POWERED)) {
				next = ORDER.indexOf(pos.east()) + 1;
				if (next >= ORDER.size()) {
					resetOrder();
					firstRound = false;
				}
			}
		}
	}

	private static int gridCount(boolean air) {
		Minecraft mc = Minecraft.getInstance();
		int n = 0;
		for (int y = 120; y <= 123; y++) {
			for (int z = 92; z <= 95; z++) {
				BlockState s = mc.level.getBlockState(new BlockPos(110, y, z));
				if (air ? s.isAir() : s.is(Blocks.STONE_BUTTON)) n++;
			}
		}
		return n;
	}

	/** Every server tick: buttons all back with no new lantern means the round was reset. */
	public static void onServerTick() {
		if (!here() || !firstRound || Minecraft.getInstance().level == null) return;
		if (sinceLantern++ > 10 && gridCount(false) > 8) firstRound = false;
	}

	/** You right-clicked a block: a button of the device. */
	public static void onUseBlock(BlockPos pos) {
		if (!here() || pos.getX() != 110 || pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95) return;
		if (SimonSaysConfig.get().party && !ORDER.isEmpty() && pos.equals(ORDER.getLast().west())) TazChat.party("SS " + ORDER.size() + "/4");
	}

	/** Test hooks. */
	public static List<BlockPos> order() {
		return ORDER;
	}

	public static int next() {
		return next;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		if (!here() || next >= ORDER.size()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (int i = next; i < ORDER.size(); i++) {
				BlockPos p = ORDER.get(i);
				AABB box = new AABB(p.getX() - 0.15, p.getY() + 0.37, p.getZ() + 0.3, p.getX() + 0.05, p.getY() + 0.63, p.getZ() + 0.7);
				int color = i == next ? 0xFFFF2E2E : i == next + 1 ? 0xFFFFB0B0 : 0xFFB3121B;
				batch.filled(box, (color & 0x00FFFFFF) | 0x90000000, true);
				batch.outline(box, color, 2f, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Simon Says failed to draw", e);
		}
	}
}
