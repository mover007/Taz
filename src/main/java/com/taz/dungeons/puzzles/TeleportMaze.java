package com.taz.dungeons.puzzles;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Teleport Maze: each pad you land on faces you toward the exit, so after a few jumps only the pads your view lined up with
 * every time are left: red when one is left, pale red when a few are, black for pads you've used, and a line to the best
 * pad in your group. From Odin.
 */
final class TeleportMaze {
	private static final int[][] PADS = {{4, 69, 12}, {4, 69, 6}, {10, 69, 12}, {10, 69, 6}, {4, 69, 20}, {4, 69, 14}, {10, 69, 20}, {10, 69, 14},
			{4, 69, 28}, {4, 69, 22}, {10, 69, 28}, {10, 69, 22}, {12, 69, 28}, {12, 69, 22}, {18, 69, 28}, {18, 69, 22}, {20, 69, 28}, {20, 69, 22},
			{26, 69, 28}, {26, 69, 22}, {26, 69, 20}, {26, 69, 14}, {20, 69, 20}, {20, 69, 14}, {26, 69, 12}, {26, 69, 6}, {20, 69, 12}, {20, 69, 6},
			{15, 69, 14}, {15, 69, 12}};
	private static final List<BlockPos> pads = new ArrayList<>();
	private static List<BlockPos> correct = new ArrayList<>();
	private static final Set<BlockPos> VISITED = new HashSet<>();
	private static BlockPos best;

	private TeleportMaze() {
	}

	static void onRoomEnter(DungeonRooms.Room room) {
		if (!room.data.name().equals("Teleport Maze")) return;
		pads.clear();
		for (int[] p : PADS) pads.add(room.toReal(new BlockPos(p[0], p[1], p[2])));
	}

	/** The server put you on a pad, facing somewhere. */
	static void onTeleport(Vec3 pos, float yaw, float pitch) {
		if (!Puzzles.cfg().teleportMaze || Puzzles.room("Teleport Maze") == null || pads.isEmpty()) return;
		if (pos.x % 0.5 != 0 || pos.y != 69.5 || pos.z % 0.5 != 0) return;
		Minecraft mc = Minecraft.getInstance();
		AABB here = new AABB(pos, pos.add(1, 1, 1)).inflate(1, 0, 1);
		AABB me = mc.player == null ? here : mc.player.getBoundingBox().inflate(1, 0, 1);
		for (BlockPos p : pads) if (here.intersects(new AABB(p)) || me.intersects(new AABB(p))) VISITED.add(p);
		if (correct.isEmpty()) correct = new ArrayList<>(pads);
		correct = correct.stream().filter(p -> !VISITED.contains(p)
				&& lookCrosses(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 4, p.getZ() + 1).inflate(0.75, 0, 0.75), pos, yaw, pitch)
				&& (mc.player == null || !new AABB(p).inflate(0.5, 0, 0.5).intersects(mc.player.getBoundingBox()))).toList();
		BlockPos current = pads.stream().filter(p -> here.intersects(new AABB(p))).findFirst().orElse(null);
		if (current == null) return;
		int index = pads.indexOf(current);
		if (index >= 28) {
			best = null;
			return;
		}
		int group = index / 4 * 4;
		List<BlockPos> candidates = pads.subList(group, group + 4).stream().filter(p -> !p.equals(current) && !VISITED.contains(p)).toList();
		best = candidates.stream().filter(correct::contains).findFirst().orElse(candidates.stream().min(Comparator.comparingDouble(p -> {
			float toward = (float) (Math.atan2(p.getZ() + 0.5 - pos.z, p.getX() + 0.5 - pos.x) * 180 / Math.PI) - 90;
			return Math.abs(Mth.wrapDegrees(toward) - Mth.wrapDegrees(yaw));
		})).orElse(null));
	}

	/** Whether a look from this spot passes through the box's sides (in x and z), within 32 blocks. Odin's isXZInterceptable. */
	private static boolean lookCrosses(AABB box, Vec3 feet, float yaw, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 start = feet.add(0, mc.player == null ? 1.62 : mc.player.getEyeHeight(), 0);
		float f = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
		Vec3 look = new Vec3(Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI) * f, Mth.sin(-pitch * Mth.DEG_TO_RAD), Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI) * f);
		Vec3 goal = start.add(look.scale(32));
		return inZ(atX(start, goal, box.minX), box) || inZ(atX(start, goal, box.maxX), box) || inX(atZ(start, goal, box.minZ), box)
				|| inX(atZ(start, goal, box.maxZ), box);
	}

	private static Vec3 atX(Vec3 a, Vec3 b, double x) {
		double dx = b.x - a.x;
		if (dx * dx < 1e-8) return null;
		double t = (x - a.x) / dx;
		return t < 0 || t > 1 ? null : new Vec3(a.x + dx * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static Vec3 atZ(Vec3 a, Vec3 b, double z) {
		double dz = b.z - a.z;
		if (dz * dz < 1e-8) return null;
		double t = (z - a.z) / dz;
		return t < 0 || t > 1 ? null : new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + dz * t);
	}

	private static boolean inX(Vec3 v, AABB b) {
		return v != null && v.x >= b.minX && v.x <= b.maxX;
	}

	private static boolean inZ(Vec3 v, AABB b) {
		return v != null && v.z >= b.minZ && v.z <= b.maxZ;
	}

	/** Test hooks. */
	static List<BlockPos> correct() {
		return correct;
	}

	static BlockPos best() {
		return best;
	}

	static List<BlockPos> pads() {
		return pads;
	}

	static void reset() {
		correct = new ArrayList<>();
		VISITED.clear();
		best = null;
	}

	static void render(WorldRenderer.Batch batch) {
		if (pads.isEmpty() || Puzzles.room("Teleport Maze") == null) return;
		for (BlockPos p : pads) {
			AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.8125, p.getZ() + 1);
			if (correct.contains(p)) batch.filled(box, correct.size() == 1 ? 0x90FF2E2E : 0x80FFB0B0, false);
			else if (VISITED.contains(p)) batch.filled(box, 0xA0000000, true);
			else batch.filled(box, 0x40FFFFFF, true);
		}
		if (best != null && Puzzles.cfg().mazeTracer) {
			batch.line(WorldRenderer.crosshair(batch.camera), new Vec3(best.getX() + 0.5, best.getY() + 0.8, best.getZ() + 0.5), Puzzles.FIRST, 2f, true);
		}
	}
}
