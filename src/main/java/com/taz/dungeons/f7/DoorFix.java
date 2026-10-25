package com.taz.dungeons.f7;

import com.taz.dungeons.util.DungeonLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

/**
 * Door Fix: in Phase 3's third section the iron doors along the wall show turned the wrong way on new versions; this turns
 * them back (on your screen only). From NoammAddons' Door Fix.
 */
public final class DoorFix {
	private static final List<BlockPos> DOORS = new ArrayList<>();
	private static final BlockState FIXED = Blocks.IRON_DOOR.defaultBlockState()
			.setValue(DoorBlock.FACING, Direction.EAST)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
			.setValue(DoorBlock.OPEN, false);

	static {
		for (int i = 0; i <= 6; i++) {
			if (i != 1) DOORS.add(new BlockPos(1, 112 + i * 4, 104));
			if (i < 6) DOORS.add(new BlockPos(1, 113 + i * 4, 86));
			DOORS.add(new BlockPos(1, 112 + i * 4, 68));
		}
	}

	private DoorFix() {
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (!DoorFixConfig.get().enabled || mc.level == null || DungeonLocation.floorNumber() != 7 || DungeonLocation.p3Section() != 3) return;
		for (BlockPos pos : DOORS) {
			BlockState now = mc.level.getBlockState(pos);
			if (!now.isAir() && now != FIXED) mc.level.setBlock(pos, FIXED, 2);
		}
	}

	/** Test hook. */
	public static List<BlockPos> doors() {
		return DOORS;
	}

	public static BlockState fixed() {
		return FIXED;
	}
}
