package com.taz.dungeons.puzzles;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Ice Path: the shortest set of hits that slides the silverfish to the exit, drawn as lines over the ice (the next slide in
 * red), worked out again whenever it lands somewhere new. From NoammAddons.
 *
 * <p>The ice is a 19 x 19 grid from room-relative (7, 7) to (25, 25) at y 67; the silverfish has to reach grid (7, 18).
 */
final class IcePath {
	private static final int SIZE = 19, OFFSET = 7;
	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	private static final List<Vec3[]> PATH = new ArrayList<>();
	private static boolean[][] wall;
	private static DungeonRooms.Room room;
	private static Silverfish fish;
	private static int lastX = -1, lastZ = -1;

	private IcePath() {
	}

	static void onRoomEnter(DungeonRooms.Room r) {
		if (!r.data.name().equals("Ice Path")) return;
		room = r;
		Minecraft mc = Minecraft.getInstance();
		wall = new boolean[SIZE][SIZE];
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) wall[z][x] = !mc.level.getBlockState(r.toReal(new BlockPos(x + OFFSET, 67, z + OFFSET))).isAir();
		}
	}

	static void tick(Minecraft mc) {
		if (!Puzzles.cfg().icePath || room == null || Puzzles.room("Ice Path") != room) return;
		if (fish == null || fish.isRemoved()) {
			BlockPos c = room.toReal(new BlockPos(15, 0, 15));
			fish = mc.level.getEntitiesOfClass(Silverfish.class, new AABB(c.getX() - 10, 67, c.getZ() - 10, c.getX() + 10, 68, c.getZ() + 10))
					.stream().findFirst().orElse(null);
			if (fish == null) return;
		}
		BlockPos rel = room.toRelative(fish.blockPosition());
		int gx = rel.getX() - OFFSET, gz = rel.getZ() - OFFSET;
		if (gx < 0 || gz < 0 || gx >= SIZE || gz >= SIZE || (gx == lastX && gz == lastZ)) return;
		lastX = gx;
		lastZ = gz;
		if (!PATH.isEmpty()) {
			Vec3[] seg = PATH.getFirst();
			if (Math.abs(fish.getX() - seg[1].x) + Math.abs(fish.getZ() - seg[1].z) <= 0.8) {
				PATH.removeFirst();
				return;
			}
			if (onSegment(fish.getX(), fish.getZ(), seg)) return;
		}
		solve(gx, gz);
	}

	private static boolean onSegment(double x, double z, Vec3[] s) {
		return x >= Math.min(s[0].x, s[1].x) - 0.8 && x <= Math.max(s[0].x, s[1].x) + 0.8 && z >= Math.min(s[0].z, s[1].z) - 0.8
				&& z <= Math.max(s[0].z, s[1].z) + 0.8;
	}

	/** Breadth-first over slides (the fish only stops at a wall). */
	private static void solve(int sx, int sz) {
		int[][][] parent = new int[SIZE][SIZE][];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {sx, sz});
		parent[sz][sx] = new int[] {sx, sz};
		while (!queue.isEmpty()) {
			int[] cur = queue.removeFirst();
			for (int[] d : DIRS) {
				int steps = 1;
				while (true) {
					int nx = cur[0] + steps * d[0], nz = cur[1] + steps * d[1];
					if (nx < 0 || nz < 0 || nx >= SIZE || nz >= SIZE || wall[nz][nx]) break;
					steps++;
				}
				steps--;
				if (steps == 0) continue;
				int fx = cur[0] + steps * d[0], fz = cur[1] + steps * d[1];
				if (parent[fz][fx] != null) continue;
				parent[fz][fx] = cur;
				queue.add(new int[] {fx, fz});
				if (fx == 7 && fz == 18) {
					List<int[]> path = new ArrayList<>();
					int[] t = {fx, fz};
					while (t[0] != sx || t[1] != sz) {
						path.addFirst(t);
						t = parent[t[1]][t[0]];
					}
					path.addFirst(new int[] {sx, sz});
					PATH.clear();
					for (int i = 1; i < path.size(); i++) PATH.add(new Vec3[] {world(path.get(i - 1)), world(path.get(i))});
					return;
				}
			}
		}
	}

	private static Vec3 world(int[] g) {
		BlockPos p = room.toReal(new BlockPos(g[0] + OFFSET, 66, g[1] + OFFSET));
		return new Vec3(p.getX() + 0.5, 67.5, p.getZ() + 0.5);
	}

	/** Test hook. */
	static int slides() {
		return PATH.size();
	}

	static void reset() {
		PATH.clear();
		room = null;
		fish = null;
		wall = null;
		lastX = lastZ = -1;
	}

	static void render(WorldRenderer.Batch batch) {
		if (PATH.isEmpty() || room == null || Puzzles.room("Ice Path") != room || (fish != null && fish.isRemoved())) return;
		for (int i = 0; i < PATH.size(); i++) batch.line(PATH.get(i)[0], PATH.get(i)[1], i == 0 ? Puzzles.FIRST : Puzzles.SECOND, i == 0 ? 5f : 3f, true);
	}
}
