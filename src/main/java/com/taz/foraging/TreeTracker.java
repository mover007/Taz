package com.taz.foraging;

import com.taz.dungeons.util.TextUtil;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Fig, Mangrove and Helix trees on Galatea near you and works out which ones are fully grown,
 * for the next-tree pointer.
 *
 * Every log of those trees is a real block, so the tracker keeps the log blocks in the chunks around
 * you, groups touching logs into trees, and watches each tree's logs change: chopping removes logs
 * (and shows a "FIG TREE 42%" hologram), regrowing puts them back over a few seconds. A tree is grown
 * once nothing has changed on it for a few seconds and either it was never touched, it grew back after
 * being chopped, or it is still (almost) as big as when it was last seen grown.
 *
 * Kept cheap for busy lobbies: only chunks within the search radius are read (a few per tick), and when
 * logs change only the trees touching them are regrouped, never every log around you.
 */
public final class TreeTracker {
	public enum Kind {
		FIG("Fig"),
		MANGROVE("Mangrove"),
		HELIX("Helix");

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	public enum State {
		/** Every log is there and nobody is chopping it. */
		GROWN,
		/** Logs are missing (being chopped, or chopped and left). */
		CHOPPED,
		/** Logs are coming back right now. */
		REGROWING
	}

	public static final class Tree {
		final int id;
		public Kind kind = Kind.FIG;
		public State state = State.CHOPPED;
		/** Logs currently in the tree. */
		public int logs;
		/** Logs the last time the tree was seen fully grown (0 = never seen grown). */
		int grownLogs;
		public int minX, minY, minZ, maxX, maxY, maxZ;
		/** False until the tree's box has been set (a tree created in the current regroup). */
		boolean hasBox;
		/** The tree's log positions at the last regroup. */
		long[] positions = new long[0];
		long lastChangeMs;
		boolean lastChangeAdded;
		/** A log was broken since the tree was last seen grown. */
		boolean dirty;
		/** Logs that grew back since the last one was broken. */
		int regrown;
		long hologramMs;
		long goneSinceMs;
		/** Hypixel said "You cannot damage a tree while it is regenerating!" for this tree until then. */
		long regenUntilMs;

		Tree(int id) {
			this.id = id;
		}

		/** The tree's log positions at the last regroup. */
		public long[] positions() {
			return positions;
		}

		/** Straight-line distance from a point to the nearest block of the tree's bounding box. */
		public double distanceTo(Vec3 p) {
			double dx = Math.max(Math.max(minX - p.x, 0), p.x - (maxX + 1));
			double dy = Math.max(Math.max(minY - p.y, 0), p.y - (maxY + 1));
			double dz = Math.max(Math.max(minZ - p.z, 0), p.z - (maxZ + 1));
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		boolean containsInflated(long pos, int by) {
			int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
			return x >= minX - by && x <= maxX + by && y >= minY - by && y <= maxY + by && z >= minZ - by && z <= maxZ + by;
		}
	}

	/** Smaller clusters are fences, beams or the first logs of a regrowing tree, not a tree. */
	private static final int MIN_LOGS = 6;
	/** A tree must go this long without a log breaking or growing before it counts as grown. */
	private static final long STABLE_MS = 3500;
	/** A "FIG TREE 42%" hologram this recent marks the tree as being chopped. */
	private static final long HOLOGRAM_MS = 2000;
	/** Chopped-down trees are remembered this long so their regrowth is recognised. */
	private static final long FORGET_GONE_MS = 15 * 60_000;
	/** Evaluate every 10 ticks (twice a second); block changes are recorded as they happen. */
	private static final int EVAL_INTERVAL = 10;
	/** Chunks read per tick while catching up (arriving on the island, walking into new areas). */
	private static final int SCANS_PER_TICK = 4;
	/** Dev: -Dtaz.foraging.anywhere=true tracks trees on every island (and in singleplayer) for testing. */
	private static final boolean ANYWHERE = Boolean.getBoolean("taz.foraging.anywhere");
	/** "FIG TREE 88%", "MANGROVE TREE 5%" (colour codes stripped). */
	private static final Pattern PROGRESS = Pattern.compile("(\\w+) TREE (\\d+)%");

	private record Change(long pos, boolean added, long timeMs) {
	}

	/** Log block position -> Kind ordinal, for every tree log in the tracked chunks. */
	private static final Long2ByteOpenHashMap LOGS = new Long2ByteOpenHashMap();
	/** Chunk -> the log positions in it, so a chunk can be dropped quickly. */
	private static final Long2ObjectOpenHashMap<LongOpenHashSet> BY_CHUNK = new Long2ObjectOpenHashMap<>();
	/** Chunks that have been read (with or without logs). Block changes elsewhere are ignored. */
	private static final LongOpenHashSet SCANNED = new LongOpenHashSet();
	/** Chunks waiting to be read, nearest first. */
	private static final LongArrayFIFOQueue SCAN_QUEUE = new LongArrayFIFOQueue();
	private static final LongOpenHashSet QUEUED = new LongOpenHashSet();
	/** Logs that appeared / disappeared since the last regroup. */
	private static final LongOpenHashSet ADDED = new LongOpenHashSet();
	private static final LongOpenHashSet REMOVED = new LongOpenHashSet();
	private static final List<Change> PENDING = new ArrayList<>();
	private static final Map<Integer, Tree> TREES = new LinkedHashMap<>();
	/** Log position -> tree id. */
	private static final Long2IntOpenHashMap OWNER = new Long2IntOpenHashMap();

	static {
		OWNER.defaultReturnValue(-1);
	}

	private static boolean active;
	private static ClientLevel trackedLevel;
	private static int nextId;
	private static int tickCounter;
	private static Tree nearestGrown;
	/** Player chunk and radius the scan window was last laid out for. */
	private static long windowCenter = Long.MIN_VALUE;
	private static int windowRadius = -1;

	private TreeTracker() {
	}

	// ---- queries (main thread)

	public static boolean active() {
		return active;
	}

	public static Collection<Tree> trees() {
		return Collections.unmodifiableCollection(TREES.values());
	}

	/** The closest fully grown tree (of the selected types) at the last evaluation, or null. */
	public static Tree nearestGrown() {
		return nearestGrown;
	}

	public static int trackedLogs() {
		return LOGS.size();
	}

	/** The "Tree status" button: what the tracker sees right now, in chat. */
	public static void printStatus() {
		if (!TreesConfig.get().enabled) {
			TazChat.info("The next tree pointer is off.");
			return;
		}
		if (!active) {
			String area = SkyblockLocation.area();
			TazChat.info("The tree pointer only runs on Galatea / Torrhus Canyon (tab list Area: " + (area == null ? "not found" : area) + ")");
			return;
		}
		int grown = 0, chopped = 0, regrowing = 0;
		for (Tree t : TREES.values()) {
			if (t.logs == 0) continue;
			switch (t.state) {
				case GROWN -> grown++;
				case CHOPPED -> chopped++;
				case REGROWING -> regrowing++;
			}
		}
		double[] ms = timingMs();
		TazChat.info(grown + " grown, " + chopped + " chopped, " + regrowing + " regrowing within " + TreesConfig.get().radius
				+ " blocks (" + LOGS.size() + " logs). Updates: " + String.format("%.2f ms avg, %.2f ms worst", ms[0], ms[1]));
		Minecraft mc = Minecraft.getInstance();
		if (nearestGrown != null && mc.player != null) {
			Tree n = nearestGrown;
			TazChat.info("Next: " + n.kind.label + " at " + ((n.minX + n.maxX) / 2) + " " + n.minY + " " + ((n.minZ + n.maxZ) / 2)
					+ ", " + Math.round(n.distanceTo(mc.player.position())) + "m, " + n.logs + " logs");
		}
	}

	/** Tab list "Area: ..." values of the islands with custom trees. */
	public static boolean isForagingIsland(String area) {
		if (area == null) return false;
		return area.contains("Galatea") || area.contains("Moonglade") || area.contains("Torrhus");
	}

	// ---- lifecycle

	/** Called every client tick with the tab list's "Area:" (or null). */
	public static void tick(Minecraft mc, String area) {
		boolean want = TreesConfig.get().enabled && mc.level != null && mc.player != null
				&& (ANYWHERE || isForagingIsland(area));
		if (!want) {
			if (active) reset();
			return;
		}
		if (!active || mc.level != trackedLevel) {
			reset();
			active = true;
			trackedLevel = mc.level;
		}
		long t0 = System.nanoTime();
		updateWindow(mc);
		for (int i = 0; i < SCANS_PER_TICK && !SCAN_QUEUE.isEmpty(); i++) {
			long key = SCAN_QUEUE.dequeueLong();
			QUEUED.remove(key);
			if (SCANNED.contains(key) || !inWindow(key)) continue;
			LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX(key), chunkZ(key), ChunkStatus.FULL, false);
			if (chunk != null) scanChunk(chunk);
		}
		if (++tickCounter % EVAL_INTERVAL == 0) evaluate(mc);
		record(System.nanoTime() - t0);
	}

	public static void reset() {
		active = false;
		trackedLevel = null;
		LOGS.clear();
		BY_CHUNK.clear();
		SCANNED.clear();
		SCAN_QUEUE.clear();
		QUEUED.clear();
		ADDED.clear();
		REMOVED.clear();
		PENDING.clear();
		TREES.clear();
		OWNER.clear();
		nearestGrown = null;
		windowCenter = Long.MIN_VALUE;
		windowRadius = -1;
	}

	public static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!active || level != trackedLevel) return;
		long key = chunkKey(chunk.getPos());
		if (!inWindow(key)) return;
		if (SCANNED.contains(key)) scanChunk(chunk); // re-sent chunk: replace what was there
		else enqueue(key);
	}

	public static void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		if (active && level == trackedLevel) dropChunk(chunkKey(chunk.getPos()));
	}

	/** From ClientLevelMixin: every block that changes on the client, on the main thread. */
	public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		if (!active) return;
		Kind was = kindOf(oldState), now = kindOf(newState);
		if (was == null && now == null) return;
		long p = pos.asLong();
		if (!SCANNED.contains(chunkKeyOf(p))) return; // outside the search radius, or read later anyway
		if (now != null) {
			addLog(p, now);
			if (was == null) PENDING.add(new Change(p, true, System.currentTimeMillis()));
		} else {
			removeLog(p);
			PENDING.add(new Change(p, false, System.currentTimeMillis()));
		}
	}

	// ---- search window

	/** Queues the chunks that came within the radius (nearest first) and drops the ones that left it. */
	private static void updateWindow(Minecraft mc) {
		ChunkPos center = mc.player.chunkPosition();
		long key = chunkKey(center);
		int r = Math.max(1, (TreesConfig.get().radius + 15) / 16);
		if (key == windowCenter && r == windowRadius) return;
		windowCenter = key;
		windowRadius = r;
		LongArrayList out = new LongArrayList();
		for (LongIterator it = SCANNED.iterator(); it.hasNext(); ) {
			long k = it.nextLong();
			if (!inWindow(k)) out.add(k);
		}
		for (int i = 0; i < out.size(); i++) dropChunk(out.getLong(i));
		for (int d = 0; d <= r; d++) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue; // ring by ring, nearest first
					long k = chunkKey(center.x() + dx, center.z() + dz);
					if (!SCANNED.contains(k)) enqueue(k);
				}
			}
		}
	}

	private static void enqueue(long chunkKey) {
		if (QUEUED.add(chunkKey)) SCAN_QUEUE.enqueue(chunkKey);
	}

	private static boolean inWindow(long chunkKey) {
		if (windowRadius < 0) return false;
		return Math.abs(chunkX(chunkKey) - chunkX(windowCenter)) <= windowRadius
				&& Math.abs(chunkZ(chunkKey) - chunkZ(windowCenter)) <= windowRadius;
	}

	// ---- block bookkeeping

	static Kind kindOf(BlockState state) {
		Block b = state.getBlock();
		if (b == Blocks.STRIPPED_SPRUCE_WOOD) return Kind.FIG;
		if (b == Blocks.MANGROVE_WOOD) return Kind.MANGROVE;
		if (b == Blocks.STRIPPED_BIRCH_WOOD || b == Blocks.STRIPPED_MANGROVE_WOOD) return Kind.HELIX; // beige + red logs
		return null;
	}

	private static void scanChunk(LevelChunk chunk) {
		ChunkPos cp = chunk.getPos();
		long key = chunkKey(cp);
		dropChunk(key); // a re-sent chunk replaces what was there
		SCANNED.add(key);
		int baseX = cp.getMinBlockX(), baseZ = cp.getMinBlockZ();
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> kindOf(st) != null)) continue;
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						Kind k = kindOf(s.getBlockState(x, y, z));
						if (k != null) addLog(BlockPos.asLong(baseX + x, baseY + y, baseZ + z), k);
					}
				}
			}
		}
	}

	private static void dropChunk(long key) {
		SCANNED.remove(key);
		LongOpenHashSet set = BY_CHUNK.remove(key);
		if (set == null) return;
		for (LongIterator it = set.iterator(); it.hasNext(); ) {
			long p = it.nextLong();
			LOGS.remove(p);
			ADDED.remove(p);
			REMOVED.add(p);
		}
	}

	private static void addLog(long p, Kind k) {
		LOGS.put(p, (byte) k.ordinal());
		BY_CHUNK.computeIfAbsent(chunkKeyOf(p), c -> new LongOpenHashSet()).add(p);
		REMOVED.remove(p);
		ADDED.add(p);
	}

	private static void removeLog(long p) {
		LOGS.remove(p);
		LongOpenHashSet set = BY_CHUNK.get(chunkKeyOf(p));
		if (set != null) set.remove(p);
		ADDED.remove(p);
		REMOVED.add(p);
	}

	private static long chunkKey(ChunkPos cp) {
		return chunkKey(cp.x(), cp.z());
	}

	private static long chunkKey(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int chunkX(long key) {
		return (int) (key >> 32);
	}

	private static int chunkZ(long key) {
		return (int) key;
	}

	private static long chunkKeyOf(long pos) {
		return chunkKey(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
	}

	// ---- evaluation

	/** Totals for one tree's new logs during a regroup. */
	private static final class Acc {
		int logs;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		final int[] kinds = new int[Kind.values().length];
		final LongArrayList positions = new LongArrayList();

		void add(long p, int kind) {
			int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
			logs++;
			positions.add(p);
			kinds[kind]++;
			if (x < minX) minX = x;
			if (y < minY) minY = y;
			if (z < minZ) minZ = z;
			if (x > maxX) maxX = x;
			if (y > maxY) maxY = y;
			if (z > maxZ) maxZ = z;
		}
	}

	private static void evaluate(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (!ADDED.isEmpty() || !REMOVED.isEmpty()) regroup(now);
		markHolograms(mc, now);
		decideStates(mc, now);
	}

	// ---- timing (shown by Tree status, used by the stress test)

	private static long statTicks, statNanos, statMaxNanos;

	private static void record(long nanos) {
		statTicks++;
		statNanos += nanos;
		statMaxNanos = Math.max(statMaxNanos, nanos);
	}

	public static void resetTiming() {
		statTicks = statNanos = statMaxNanos = 0;
	}

	/** Average and worst time the tracker took in one game tick, in milliseconds. */
	public static double[] timingMs() {
		return new double[]{statTicks == 0 ? 0 : statNanos / 1e6 / statTicks, statMaxNanos / 1e6};
	}

	/**
	 * Regroups only the trees touched by logs that appeared or disappeared since last time (plus the new
	 * logs themselves), then credits each broken or regrown log to its tree.
	 */
	private static void regroup(long now) {
		// Trees touched by the change: owners of removed logs, of re-added logs, and of logs next to new ones.
		IntOpenHashSet affected = new IntOpenHashSet();
		for (LongIterator it = REMOVED.iterator(); it.hasNext(); ) {
			int id = OWNER.get(it.nextLong());
			if (id >= 0) affected.add(id);
		}
		LongArrayList seeds = new LongArrayList();
		for (LongIterator it = ADDED.iterator(); it.hasNext(); ) {
			long p = it.nextLong();
			if (!LOGS.containsKey(p)) continue;
			seeds.add(p);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						int id = OWNER.get(BlockPos.offset(p, dx, dy, dz));
						if (id >= 0) affected.add(id);
					}
				}
			}
		}
		for (IntIterator it = affected.iterator(); it.hasNext(); ) {
			Tree t = TREES.get(it.nextInt());
			if (t == null) continue;
			for (long p : t.positions) if (LOGS.containsKey(p)) seeds.add(p);
		}

		// Group touching logs (26 neighbours) among them; each group goes to the tree most of its logs belonged to.
		LongOpenHashSet seen = new LongOpenHashSet(seeds.size());
		LongArrayList group = new LongArrayList();
		Int2IntOpenHashMap votes = new Int2IntOpenHashMap();
		Map<Integer, Acc> acc = new LinkedHashMap<>();
		for (int s = 0; s < seeds.size(); s++) {
			long start = seeds.getLong(s);
			if (!seen.add(start)) continue;
			group.clear();
			group.add(start);
			for (int i = 0; i < group.size(); i++) {
				long p = group.getLong(i);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							if ((dx | dy | dz) == 0) continue;
							long q = BlockPos.offset(p, dx, dy, dz);
							if (LOGS.containsKey(q) && seen.add(q)) group.add(q);
						}
					}
				}
			}
			Tree tree = treeFor(group, votes);
			Acc a = acc.computeIfAbsent(tree.id, k -> new Acc());
			for (int i = 0; i < group.size(); i++) {
				long p = group.getLong(i);
				a.add(p, LOGS.get(p));
			}
		}

		// Broken logs are credited to the tree that owned them before this regroup, so look them up now.
		Tree[] creditTo = new Tree[PENDING.size()];
		for (int i = 0; i < creditTo.length; i++) {
			Change c = PENDING.get(i);
			if (c.added()) continue;
			creditTo[i] = TREES.get(OWNER.get(c.pos()));
			if (creditTo[i] == null) creditTo[i] = treeAround(c.pos());
		}

		// Rewrite ownership for the trees that were regrouped.
		for (IntIterator it = affected.iterator(); it.hasNext(); ) {
			int id = it.nextInt();
			Tree t = TREES.get(id);
			if (t == null) continue;
			for (long p : t.positions) if (OWNER.get(p) == id) OWNER.remove(p);
		}
		for (LongIterator it = REMOVED.iterator(); it.hasNext(); ) OWNER.remove(it.nextLong());
		for (Map.Entry<Integer, Acc> e : acc.entrySet()) {
			LongArrayList ps = e.getValue().positions;
			for (int i = 0; i < ps.size(); i++) OWNER.put(ps.getLong(i), (int) e.getKey());
		}
		ADDED.clear();
		REMOVED.clear();

		// Credit each broken or regrown log to its tree (regrown ones by their new owner).
		for (int i = 0; i < creditTo.length; i++) {
			Change c = PENDING.get(i);
			Tree t = c.added() ? TREES.get(OWNER.get(c.pos())) : creditTo[i];
			if (t == null) continue;
			t.lastChangeMs = Math.max(t.lastChangeMs, c.timeMs());
			t.lastChangeAdded = c.added();
			if (c.added()) {
				t.regrown++;
			} else {
				t.dirty = true;
				t.regrown = 0;
			}
		}
		PENDING.clear();

		// Update sizes and boxes. A tree left with no logs keeps its last box, for its regrowth.
		IntOpenHashSet touched = new IntOpenHashSet(affected);
		for (Integer id : acc.keySet()) touched.add(id);
		for (IntIterator it = touched.iterator(); it.hasNext(); ) {
			Tree t = TREES.get(it.nextInt());
			if (t == null) continue;
			Acc a = acc.get(t.id);
			if (a == null) {
				if (t.logs > 0) t.goneSinceMs = now;
				t.logs = 0;
				t.positions = new long[0];
				continue;
			}
			t.logs = a.logs;
			t.positions = a.positions.toLongArray();
			if (t.dirty && t.hasBox) {
				// Chopped trees keep their full-size box, so logs growing back anywhere in it stay this tree.
				t.minX = Math.min(t.minX, a.minX);
				t.minY = Math.min(t.minY, a.minY);
				t.minZ = Math.min(t.minZ, a.minZ);
				t.maxX = Math.max(t.maxX, a.maxX);
				t.maxY = Math.max(t.maxY, a.maxY);
				t.maxZ = Math.max(t.maxZ, a.maxZ);
			} else {
				t.minX = a.minX;
				t.minY = a.minY;
				t.minZ = a.minZ;
				t.maxX = a.maxX;
				t.maxY = a.maxY;
				t.maxZ = a.maxZ;
			}
			t.hasBox = true;
			int best = 0;
			for (int k = 1; k < a.kinds.length; k++) if (a.kinds[k] > a.kinds[best]) best = k;
			t.kind = Kind.values()[best];
		}
	}

	/** "FIG TREE 42%" holograms mark the tree next to them as being chopped. */
	private static void markHolograms(Minecraft mc, long now) {
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName()) continue;
			Component name = e.getCustomName();
			if (name == null) continue;
			Matcher m = PROGRESS.matcher(TextUtil.cleanScoreboard(name.getString()));
			if (!m.find()) continue;
			int pct = Integer.parseInt(m.group(2));
			if (pct <= 0 || pct >= 100) continue;
			Tree closest = null;
			double bestD = 6.0;
			for (Tree t : TREES.values()) {
				if (t.logs == 0) continue;
				double d = t.distanceTo(e.position());
				if (d < bestD) {
					bestD = d;
					closest = t;
				}
			}
			if (closest != null) closest.hologramMs = now;
		}
	}

	/** Each tree's state and the closest grown one; forgets empty trees that were never grown or have been gone a long time. */
	private static void decideStates(Minecraft mc, long now) {
		Vec3 player = mc.player.position();
		nearestGrown = null;
		double nearestD = Double.MAX_VALUE;
		TreesConfig cfg = TreesConfig.get();
		TREES.values().removeIf(t -> t.logs == 0 && (t.grownLogs == 0 || now - t.goneSinceMs > FORGET_GONE_MS));
		for (Tree t : TREES.values()) {
			t.state = decide(t, now);
			if (t.state == State.GROWN && cfg.enabled(t.kind) && !(cfg.skipProtected && ProtectedTrees.isProtected(t))) {
				double d = t.distanceTo(player);
				if (d < nearestD) {
					nearestD = d;
					nearestGrown = t;
				}
			}
		}
	}

	private static State decide(Tree t, long now) {
		if (now < t.regenUntilMs) return State.REGROWING;
		boolean settled = now - t.lastChangeMs >= STABLE_MS;
		if (t.logs < MIN_LOGS) return t.lastChangeAdded && !settled ? State.REGROWING : State.CHOPPED;
		if (!settled) return t.lastChangeAdded ? State.REGROWING : State.CHOPPED;
		if (now - t.hologramMs < HOLOGRAM_MS) return State.CHOPPED;
		if (!t.dirty) {
			t.grownLogs = Math.max(t.grownLogs, t.logs);
			return State.GROWN;
		}
		if (t.regrown >= Math.max(4, t.logs / 4)) {
			// Grew back after being chopped (a new tree can be a different size).
			t.grownLogs = t.logs;
			t.dirty = false;
			t.regrown = 0;
			return State.GROWN;
		}
		// Barely scratched still counts, but keeps the old size so repeated scratches add up.
		if (t.grownLogs > 0 && t.logs >= t.grownLogs * 0.97) return State.GROWN;
		return State.CHOPPED;
	}

	/**
	 * The tree a group of logs belongs to: the one most of its logs were part of last time,
	 * otherwise a chopped or empty tree whose box holds most of it (it is growing back or its chunk
	 * reloaded), otherwise a new tree.
	 */
	private static Tree treeFor(LongArrayList group, Int2IntOpenHashMap votes) {
		votes.clear();
		for (int i = 0; i < group.size(); i++) {
			int id = OWNER.get(group.getLong(i));
			if (id >= 0) votes.addTo(id, 1);
		}
		Tree best = null;
		int bestVotes = 0;
		for (Int2IntOpenHashMap.Entry e : votes.int2IntEntrySet()) {
			Tree t = TREES.get(e.getIntKey());
			if (t != null && e.getIntValue() > bestVotes) {
				best = t;
				bestVotes = e.getIntValue();
			}
		}
		if (best != null) return best;

		double bestShare = 0.5;
		for (Tree t : TREES.values()) {
			if (!t.hasBox || (t.logs > 0 && !t.dirty)) continue; // a standing grown tree never absorbs a neighbour
			int inside = 0;
			for (int i = 0; i < group.size(); i++) if (t.containsInflated(group.getLong(i), 1)) inside++;
			double share = inside / (double) group.size();
			if (share >= bestShare) {
				bestShare = share;
				best = t;
			}
		}
		if (best != null) return best;

		Tree t = new Tree(nextId++);
		TREES.put(t.id, t);
		return t;
	}

	/** A tree whose box holds a position (for a log that was added and broken between two evaluations). */
	private static Tree treeAround(long pos) {
		for (Tree t : TREES.values()) if (t.hasBox && t.containsInflated(pos, 0)) return t;
		return null;
	}
}
