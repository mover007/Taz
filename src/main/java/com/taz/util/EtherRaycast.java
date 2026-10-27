package com.taz.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where an Etherwarp (or any look-at-a-block ability) lands: walks the blocks along your look, the way Hypixel does, and
 * says which block it hits and whether you'd fit on top. Odin's voxel walk (by Bloom), in Java.
 */
public final class EtherRaycast {
	public record Hit(boolean ok, BlockPos pos, BlockState state) {
	}

	private static final int AIR = 1, PASSABLE = 2, OBSTRUCTS = 4, STAND_SHIFT = 3;
	private static int[] flags;

	private EtherRaycast() {
	}

	/** The block your look hits within {@code distance} from {@code feet}. */
	public static Hit cast(Vec3 feet, double distance, boolean etherwarp, boolean sneaking) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null) return null;
		double eye = player.getPose() == Pose.SWIMMING ? 0.4 : sneaking ? 1.27 : 1.62;
		Vec3 start = feet.add(0, eye, 0);
		Vec3 end = start.add(player.getLookAngle().scale(distance));
		return walk(mc.level, start, end, etherwarp);
	}

	private static Hit walk(ClientLevel level, Vec3 start, Vec3 end, boolean etherwarp) {
		double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
		int x = (int) Math.floor(start.x), y = (int) Math.floor(start.y), z = (int) Math.floor(start.z);
		int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
		double tdx = Math.abs(1 / dx), tdy = Math.abs(1 / dy), tdz = Math.abs(1 / dz);
		double tmx = (sx < 0 ? start.x - x : x + 1 - start.x) * tdx;
		double tmy = (sy < 0 ? start.y - y : y + 1 - start.y) * tdy;
		double tmz = (sz < 0 ? start.z - z : z + 1 - start.z) * tdz;
		int remaining = Math.abs((int) Math.floor(end.x) - x) + Math.abs((int) Math.floor(end.y) - y) + Math.abs((int) Math.floor(end.z) - z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (true) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			if (chunk == null) return null;
			BlockState state = chunk.getBlockState(pos.set(x, y, z));
			int f = flags(state);
			if (etherwarp ? (f & PASSABLE) == 0 : (f & AIR) == 0) {
				if ((f & PASSABLE) != 0) return new Hit(false, pos.immutable(), state);
				int feetY = y + (f >>> STAND_SHIFT);
				boolean fits = !obstructs(chunk, new BlockPos(x, feetY, z)) && !obstructs(chunk, new BlockPos(x, feetY + 1, z));
				return new Hit(fits, pos.immutable(), state);
			}
			if (remaining-- == 0) return (f & AIR) != 0 ? null : new Hit(false, pos.immutable(), state);
			if (tmx <= tmy && tmx <= tmz) {
				tmx += tdx;
				x += sx;
			} else if (tmy <= tmz) {
				tmy += tdy;
				y += sy;
			} else {
				tmz += tdz;
				z += sz;
			}
		}
	}

	private static boolean obstructs(LevelChunk chunk, BlockPos pos) {
		return (flags(chunk.getBlockState(pos)) & OBSTRUCTS) != 0;
	}

	private static int flags(BlockState state) {
		if (flags == null) {
			int[] f = new int[Block.BLOCK_STATE_REGISTRY.size()];
			for (int i = 0; i < f.length; i++) {
				BlockState s = Block.BLOCK_STATE_REGISTRY.byId(i);
				if (s != null) f[i] = compute(s);
			}
			flags = f;
		}
		return flags[Block.getId(state)];
	}

	private static int compute(BlockState state) {
		Block b = state.getBlock();
		VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		boolean noHitbox = b instanceof SignBlock || b instanceof AbstractBannerBlock || b instanceof TripWireHookBlock || b instanceof BasePressurePlateBlock;
		boolean passable = !noHitbox && (b instanceof ButtonBlock || b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof LadderBlock
				|| b instanceof BubbleColumnBlock || b instanceof FlowerPotBlock || b instanceof PistonHeadBlock || b instanceof LeverBlock
				|| b instanceof NetherWartBlock || b instanceof ComparatorBlock || b instanceof RedstoneTorchBlock || b instanceof RepeaterBlock
				|| collision.isEmpty());
		boolean blocksFeet = b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof ComparatorBlock || b instanceof RepeaterBlock
				|| b instanceof FlowerPotBlock || b instanceof LadderBlock;
		int stand = Math.max(1, (int) Math.ceil(collision.isEmpty() ? 0 : collision.max(Direction.Axis.Y)));
		return (stand << STAND_SHIFT) | (state.isAir() ? AIR : 0) | (passable ? PASSABLE : 0) | ((!passable && !noHitbox) || blocksFeet ? OBSTRUCTS : 0);
	}
}
