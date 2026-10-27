package com.taz.foraging;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Every block of one kind in the loaded chunks (a Ruby Veilshroom, a Sea Lumie ...): chunks are scanned a few per tick
 * while the feature is on, and kept up to date from block changes.
 */
public final class BlockFinder {
	private static final int SCANS_PER_TICK = 4;

	private final Predicate<BlockState> match;
	private final Map<Long, Set<BlockPos>> byChunk = new HashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	/** Told when a found block stops matching (harvested, broken). */
	private Consumer<BlockPos> onGone = pos -> { };
	private Consumer<BlockPos> onFound = pos -> { };
	private boolean active;
	private ClientLevel level;

	public BlockFinder(Predicate<BlockState> match) {
		this.match = match;
	}

	public BlockFinder onGone(Consumer<BlockPos> listener) {
		this.onGone = listener;
		return this;
	}

	public BlockFinder onFound(Consumer<BlockPos> listener) {
		this.onFound = listener;
		return this;
	}

	public boolean active() {
		return active;
	}

	/** Every client tick: on while {@code want}, scanning a few chunks. */
	public void tick(Minecraft mc, boolean want) {
		want = want && mc.level != null && mc.player != null;
		if (!want) {
			if (active) reset();
			return;
		}
		if (!active || mc.level != level) {
			reset();
			active = true;
			level = mc.level;
			// Closest chunks first, so what's near you shows up straight away.
			int r = mc.options.getEffectiveRenderDistance() + 1;
			ChunkPos center = mc.player.chunkPosition();
			List<int[]> offsets = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) offsets.add(new int[]{dx, dz});
			}
			offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
			for (int[] o : offsets) enqueue(ChunkPos.pack(center.x() + o[0], center.z() + o[1]));
		}
		for (int i = 0; i < SCANS_PER_TICK && !queue.isEmpty(); i++) {
			long key = queue.poll();
			queued.remove(key);
			LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
			if (chunk != null) scan(chunk);
		}
	}

	private void enqueue(long key) {
		if (queued.add(key)) queue.add(key);
	}

	private void scan(LevelChunk chunk) {
		long key = chunk.getPos().pack();
		Set<BlockPos> found = new HashSet<>();
		chunk.findBlocks(match, (pos, state) -> found.add(pos.immutable()));
		if (found.isEmpty()) byChunk.remove(key);
		else byChunk.put(key, found);
		for (BlockPos p : found) onFound.accept(p);
	}

	public void onChunkLoad(LevelChunk chunk) {
		if (active) enqueue(chunk.getPos().pack());
	}

	public void onChunkUnload(LevelChunk chunk) {
		if (active) byChunk.remove(chunk.getPos().pack());
	}

	public void onBlockChanged(BlockPos pos, BlockState newState) {
		if (!active) return;
		long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		if (match.test(newState)) {
			if (byChunk.computeIfAbsent(key, k -> new HashSet<>()).add(pos.immutable())) onFound.accept(pos.immutable());
		} else {
			Set<BlockPos> set = byChunk.get(key);
			if (set != null && set.remove(pos)) {
				if (set.isEmpty()) byChunk.remove(key);
				onGone.accept(pos.immutable());
			}
		}
	}

	public void reset() {
		active = false;
		level = null;
		byChunk.clear();
		queue.clear();
		queued.clear();
	}

	public int count() {
		int n = 0;
		for (Set<BlockPos> s : byChunk.values()) n += s.size();
		return n;
	}

	public Collection<BlockPos> all() {
		List<BlockPos> out = new ArrayList<>();
		for (Set<BlockPos> s : byChunk.values()) out.addAll(s);
		return out;
	}

	/** The found blocks closest to {@code from}, at most {@code limit}. */
	public List<BlockPos> nearest(Vec3 from, int limit) {
		List<BlockPos> out = new ArrayList<>(all());
		out.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(from)));
		return out.size() > limit ? out.subList(0, limit) : out;
	}

	public boolean contains(BlockPos pos) {
		Set<BlockPos> s = byChunk.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		return s != null && s.contains(pos);
	}
}
