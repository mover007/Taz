package com.taz.foraging.safari;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A walking path over the blocks themselves, for where the Safari map's walkways stop: A* over the places you can
 * stand, walking (diagonals too), stepping up a block, dropping up to three, swimming and climbing.
 */
public final class BlockPath {
	/** How near the target you have to get: you stand next to a bee nest, not in it. */
	public static final double REACH = 2.5;
	private static final int MAX_STEPS = 6000;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	public record Result(List<Vec3> points, int target, double length) {
	}

	private BlockPath() {
	}

	/** The shortest walk from {@code from} to within reach of the nearest of {@code targets}, or null. */
	public static Result find(Level level, Vec3 from, List<Vec3> targets, double range) {
		if (level == null || targets.isEmpty()) return null;
		BlockPos start = standingAt(level, BlockPos.containing(from));
		if (start == null) return null;
		Map<BlockPos, Double> cost = new HashMap<>();
		Map<BlockPos, BlockPos> prev = new HashMap<>();
		PriorityQueue<Object[]> open = new PriorityQueue<>((a, b) -> Double.compare((double) a[0], (double) b[0]));
		cost.put(start, 0.0);
		open.add(new Object[]{guess(start, targets), start});
		int steps = 0;
		while (!open.isEmpty() && steps++ < MAX_STEPS) {
			BlockPos p = (BlockPos) open.poll()[1];
			double here = cost.get(p);
			Vec3 feet = Vec3.atBottomCenterOf(p);
			for (int t = 0; t < targets.size(); t++) {
				if (feet.distanceTo(targets.get(t)) <= REACH) return result(p, prev, targets.get(t), t);
			}
			for (Object[] step : neighbours(level, p)) {
				BlockPos q = (BlockPos) step[0];
				if (q.distSqr(start) > range * range) continue;
				double c = here + (double) step[1];
				Double known = cost.get(q);
				if (known != null && known <= c) continue;
				cost.put(q, c);
				prev.put(q, p);
				open.add(new Object[]{c + guess(q, targets), q});
			}
		}
		return null;
	}

	private static double guess(BlockPos p, List<Vec3> targets) {
		Vec3 feet = Vec3.atBottomCenterOf(p);
		double best = Double.MAX_VALUE;
		for (Vec3 t : targets) best = Math.min(best, Math.max(0, feet.distanceTo(t) - REACH));
		return best;
	}

	private static Result result(BlockPos end, Map<BlockPos, BlockPos> prev, Vec3 target, int index) {
		List<Vec3> points = new ArrayList<>();
		for (BlockPos p = end; p != null; p = prev.get(p)) points.add(0, Vec3.atBottomCenterOf(p));
		points.add(target);
		// Straight runs as one line.
		List<Vec3> out = new ArrayList<>();
		for (Vec3 p : points) {
			if (out.size() >= 2) {
				Vec3 a = out.get(out.size() - 2), b = out.get(out.size() - 1);
				if (b.subtract(a).normalize().distanceTo(p.subtract(b).normalize()) < 1e-6) out.remove(out.size() - 1);
			}
			out.add(p);
		}
		double length = 0;
		for (int i = 1; i < out.size(); i++) length += out.get(i).distanceTo(out.get(i - 1));
		return new Result(out, index, length);
	}

	/** Where you can go from {@code p}, and what each step costs. */
	private static List<Object[]> neighbours(Level level, BlockPos p) {
		List<Object[]> out = new ArrayList<>();
		boolean headroom = open(level, p.above(2));
		for (int[] d : DIRS) {
			boolean diagonal = d[0] != 0 && d[1] != 0;
			if (diagonal && !(clear(level, p.offset(d[0], 0, 0)) && clear(level, p.offset(0, 0, d[1])))) continue;
			double walk = diagonal ? 1.414 : 1.0;
			BlockPos q = p.offset(d[0], 0, d[1]);
			if (canStand(level, q)) {
				out.add(new Object[]{q, walk});
			} else if (headroom && canStand(level, q.above()) && clear(level, q.above())) {
				out.add(new Object[]{q.above(), walk + 1.0});
			} else if (clear(level, q)) {
				for (int drop = 1; drop <= 3; drop++) {
					BlockPos down = q.below(drop);
					if (canStand(level, down)) {
						out.add(new Object[]{down, walk + drop * 0.5});
						break;
					}
					if (!open(level, down)) break;
				}
			}
		}
		boolean water = water(level, p), climb = level.getBlockState(p).is(BlockTags.CLIMBABLE);
		if ((water || climb) && clear(level, p.above()) && open(level, p.above(2))) out.add(new Object[]{p.above(), 1.2});
		if ((water || climb) && (water(level, p.below()) || level.getBlockState(p.below()).is(BlockTags.CLIMBABLE))) out.add(new Object[]{p.below(), 1.2});
		return out;
	}

	/** You can stand here: room for feet and head, and something under you (a block, water or a ladder). */
	static boolean canStand(Level level, BlockPos p) {
		if (!clear(level, p) || !open(level, p.above())) return false;
		return !open(level, p.below()) || low(level, p) || water(level, p) || level.getBlockState(p).is(BlockTags.CLIMBABLE);
	}

	/** If you're mid-jump, the block you'll land on. */
	private static BlockPos standingAt(Level level, BlockPos p) {
		for (int i = 0; i <= 3; i++) {
			if (canStand(level, p.below(i))) return p.below(i);
		}
		return clear(level, p) ? p : null;
	}

	/** Nothing to bump into (unloaded blocks are walls). */
	private static boolean open(Level level, BlockPos p) {
		if (!level.isLoaded(p)) return false;
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/** Open, or only a carpet, snow layer or plate you walk over. */
	private static boolean clear(Level level, BlockPos p) {
		return open(level, p) || low(level, p);
	}

	private static boolean low(Level level, BlockPos p) {
		if (!level.isLoaded(p)) return false;
		BlockState s = level.getBlockState(p);
		VoxelShape shape = s.getCollisionShape(level, p);
		return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.2;
	}

	private static boolean water(Level level, BlockPos p) {
		return level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER);
	}
}
