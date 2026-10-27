package com.taz.dungeons.secrets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which Catacombs room you're in and how it's turned, worked out the way Odin does it so Odin's room waypoints land on
 * the same blocks. A tile is recognised by its "core": a hash of the block column at its centre, looked up in Odin's
 * room table (rooms.json, from Odin, BSD 3-Clause).
 */
public final class DungeonRooms {
	/** Tests: the flat test world counts as a dungeon. */
	public static final boolean ANYWHERE = Boolean.getBoolean("taz.dungeons.anywhere");
	private static final int GRID = 6;
	/** A tile with no known room is looked at again this often (ticks): its chunk may not have been sent yet. */
	private static final int RETRY_TICKS = 40;

	public enum Rotation {
		NORTH(15, 15), SOUTH(-15, -15), WEST(15, -15), EAST(-15, 15);

		/** Where the blue terracotta is from a 1x1 room's centre. */
		final int dx, dz;

		Rotation(int dx, int dz) {
			this.dx = dx;
			this.dz = dz;
		}
	}

	public enum Shape {
		ONE("1x1", 1), TWO("1x2", 2), THREE("1x3", 3), FOUR("1x4", 4), L("L", 3), SQUARE("2x2", 4);

		final String id;
		final int tiles;

		Shape(String id, int tiles) {
			this.id = id;
			this.tiles = tiles;
		}

		static Shape of(String id) {
			for (Shape s : values()) if (s.id.equals(id)) return s;
			return ONE;
		}
	}

	public record RoomData(String name, String type, Shape shape, int secrets) {
	}

	public static final class Room {
		public final RoomData data;
		/** Grid tiles (x, z) found so far. */
		final List<int[]> tiles = new ArrayList<>();
		int highest = -1;
		public Rotation rotation;
		public BlockPos clay;

		Room(RoomData data) {
			this.data = data;
		}

		/** Its grid tiles {x, z} (0-5 each, x east, z south). */
		public List<int[]> tiles() {
			return tiles;
		}

		public boolean resolved() {
			return rotation != null && clay != null;
		}

		/** A waypoint's position (relative, facing north) in the world. */
		public BlockPos toReal(BlockPos rel) {
			return turn(rel.getX(), rel.getY(), rel.getZ()).offset(clay.getX(), 0, clay.getZ());
		}

		/** A world position as a waypoint position. */
		public BlockPos toRelative(BlockPos real) {
			int x = real.getX() - clay.getX(), y = real.getY(), z = real.getZ() - clay.getZ();
			return switch (rotation) {
				case NORTH -> new BlockPos(-x, y, -z);
				case WEST -> new BlockPos(z, y, -x);
				case SOUTH -> new BlockPos(x, y, z);
				case EAST -> new BlockPos(-z, y, x);
			};
		}

		/** A waypoint's box (block units around its block, facing north) in the world. */
		public AABB toReal(AABB rel, BlockPos realPos) {
			Vec3 a = turn(rel.maxX - 0.5, rel.maxY - 0.5, rel.maxZ - 0.5).add(0.5, 0.5, 0.5);
			Vec3 b = turn(rel.minX - 0.5, rel.minY - 0.5, rel.minZ - 0.5).add(0.5, 0.5, 0.5);
			return new AABB(a, b).move(realPos);
		}

		private BlockPos turn(int x, int y, int z) {
			return switch (rotation) {
				case NORTH -> new BlockPos(-x, y, -z);
				case WEST -> new BlockPos(-z, y, x);
				case SOUTH -> new BlockPos(x, y, z);
				case EAST -> new BlockPos(z, y, -x);
			};
		}

		private Vec3 turn(double x, double y, double z) {
			return switch (rotation) {
				case NORTH -> new Vec3(-x, y, -z);
				case WEST -> new Vec3(-z, y, x);
				case SOUTH -> new Vec3(x, y, z);
				case EAST -> new Vec3(z, y, -x);
			};
		}
	}

	private static final Map<Integer, RoomData> BY_CORE = new HashMap<>();
	private static final Room[] TILES = new Room[GRID * GRID];
	private static final int[] TRIED = new int[GRID * GRID];
	private static final Map<String, Room> ROOMS = new HashMap<>();
	private static ClientLevel level;
	private static Room current;
	private static boolean inDungeon;
	private static int ticks;

	static {
		loadTable();
	}

	private DungeonRooms() {
	}

	private static void loadTable() {
		try (InputStream in = DungeonRooms.class.getResourceAsStream("/assets/taz/dungeons/rooms.json")) {
			JsonArray rooms = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement e : rooms) {
				JsonObject o = e.getAsJsonObject();
				RoomData data = new RoomData(o.get("name").getAsString(), o.get("type").getAsString(), Shape.of(o.get("shape").getAsString()),
						o.has("maxSecrets") ? o.get("maxSecrets").getAsInt() : 0);
				for (JsonElement core : o.getAsJsonArray("cores")) BY_CORE.put(core.getAsInt(), data);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read the dungeon room table", e);
		}
	}

	/** The room you're in (with its rotation known), or null outside the dungeon's rooms. */
	public static Room current() {
		return current;
	}

	/** The room Taz has found on grid tile x, z (0-5), or null. */
	public static Room at(int x, int z) {
		return x >= 0 && x < GRID && z >= 0 && z < GRID ? TILES[x + z * GRID] : null;
	}

	/** Every room Taz has found this run. */
	public static java.util.Collection<Room> all() {
		return ROOMS.values();
	}

	public static Room named(String name) {
		return ROOMS.get(name);
	}

	public static int knownRooms() {
		return (int) BY_CORE.values().stream().map(RoomData::name).distinct().count();
	}

	public static boolean inDungeon() {
		return inDungeon;
	}

	/** Tests: a room for a column built in the test world. */
	public static void addRoom(int core, String name, String shape) {
		BY_CORE.put(core, new RoomData(name, "NORMAL", Shape.of(shape), 0));
	}

	/** Tests: a room turned this way with its clay corner here. */
	public static Room placedAt(Rotation rotation, BlockPos clay) {
		Room r = new Room(new RoomData("Test", "NORMAL", Shape.ONE, 0));
		r.rotation = rotation;
		r.clay = clay;
		return r;
	}

	public static void reset() {
		Arrays.fill(TILES, null);
		Arrays.fill(TRIED, Integer.MIN_VALUE / 2);
		ROOMS.clear();
		current = null;
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			reset();
			level = mc.level;
		}
		if (level == null || mc.player == null) {
			current = null;
			return;
		}
		ticks++;
		inDungeon = com.taz.dungeons.util.DungeonLocation.inDungeon();
		if (!inDungeon) {
			current = null;
			return;
		}
		if (ticks % 10 == 0) scan();
		int tx = (mc.player.getBlockX() + 201) >> 5, tz = (mc.player.getBlockZ() + 201) >> 5;
		Room room = tx >= 0 && tx < GRID && tz >= 0 && tz < GRID ? TILES[tx + tz * GRID] : null;
		if (room == null || !room.resolved()) {
			current = null;
			return;
		}
		// Standing on the roof (outside the room) keeps the room you were in.
		if (room != current && mc.player.getBlockY() > room.highest - 10) return;
		current = room;
	}

	// ---- scanning

	private static int centre(int tile) {
		return tile * 32 - 185;
	}

	private static void scan() {
		for (int i = 0; i < TILES.length; i++) {
			if (TILES[i] != null || ticks - TRIED[i] < RETRY_TICKS) continue;
			TRIED[i] = ticks;
			int tx = i % GRID, tz = i / GRID;
			int[] highest = new int[1];
			Integer core = coreAt(level, centre(tx), centre(tz), highest);
			RoomData data = core == null ? null : BY_CORE.get(core);
			if (data == null) continue;
			Room room = ROOMS.computeIfAbsent(data.name(), n -> new Room(data));
			room.tiles.add(new int[]{tx, tz});
			room.highest = highest[0];
			TILES[i] = room;
		}
		for (Room room : ROOMS.values()) {
			if (!room.resolved() && room.tiles.size() >= room.data.shape().tiles) resolve(room);
		}
	}

	/**
	 * The core of the column at x, z: every block from y 140 down to 12, starting at the first one that isn't air or gold,
	 * with chests and oak planks left out (they differ between runs), hashed.
	 */
	public static Integer coreAt(ClientLevel level, int x, int z, int[] highestOut) {
		LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
		if (chunk == null) return null;
		StringBuilder sb = new StringBuilder(1024);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean found = false;
		int highest = 0, bedrock = 0;
		for (int y = 140; y >= 12; y--) {
			BlockState s = chunk.getBlockState(pos.set(x, y, z));
			if (!found) {
				if (!s.isAir() && !s.is(Blocks.GOLD_BLOCK)) {
					found = true;
					highest = y;
				} else {
					sb.append('0');
				}
			}
			if (!found) continue;
			if (s.isAir() && bedrock >= 2 && y < 69) {
				sb.append("0".repeat(y - 11));
				break;
			}
			if (s.is(Blocks.BEDROCK)) {
				bedrock++;
			} else {
				bedrock = 0;
				if (s.is(Blocks.OAK_PLANKS) || s.is(Blocks.TRAPPED_CHEST) || s.is(Blocks.CHEST)) continue;
			}
			sb.append(s.getBlock());
		}
		highestOut[0] = highest;
		return sb.toString().hashCode();
	}

	/** Works out a room's rotation and clay corner once all its tiles are found. */
	private static void resolve(Room room) {
		int h = room.highest;
		if (room.data.name().equals("Fairy")) {
			int[] t = room.tiles.get(0);
			room.clay = new BlockPos(centre(t[0]) + Rotation.SOUTH.dx, h, centre(t[1]) + Rotation.SOUTH.dz);
			room.rotation = Rotation.SOUTH;
			return;
		}
		if (room.data.shape() == Shape.ONE) {
			int[] t = room.tiles.get(0);
			for (Rotation rot : Rotation.values()) {
				BlockPos p = new BlockPos(centre(t[0]) + rot.dx, h, centre(t[1]) + rot.dz);
				if (level.getBlockState(p).is(Blocks.BLUE_TERRACOTTA)) {
					room.clay = p;
					room.rotation = rot;
					return;
				}
			}
			return; // its roof corner isn't loaded yet
		}
		Layout layout = layout(room.data.shape(), room.tiles, h, p -> level.getBlockState(p).is(Blocks.BLUE_TERRACOTTA));
		room.rotation = layout.rotation();
		room.clay = layout.clay();
	}

	public record Layout(Rotation rotation, BlockPos clay) {
	}

	/** A bigger room's rotation and clay corner from its tiles on the grid. */
	public static Layout layout(Shape shape, List<int[]> tiles, int height, java.util.function.Predicate<BlockPos> isClay) {
		int tlX = tiles.stream().mapToInt(t -> t[0]).min().orElse(0);
		int tlZ = tiles.stream().mapToInt(t -> t[1]).min().orElse(0);
		int[] br = tiles.stream().max((a, b) -> Integer.compare(a[0] * 1000 + a[1], b[0] * 1000 + b[1])).orElseThrow();
		int tlx = centre(tlX), tlz = centre(tlZ), brx = centre(br[0]), brz = centre(br[1]);
		if (shape != Shape.L) {
			return tlX == br[0] ? new Layout(Rotation.WEST, new BlockPos(tlx + 15, height, tlz - 15))
					: new Layout(Rotation.SOUTH, new BlockPos(tlx - 15, height, tlz - 15));
		}
		List<Layout> options = new ArrayList<>();
		for (int[] other : tiles) {
			if ((other[0] == tlX && other[1] == tlZ) || other == br) continue;
			Rotation rot = tlX == br[0] ? Rotation.EAST : tlZ == br[1] ? Rotation.WEST : other[0] == tlX ? Rotation.SOUTH : Rotation.NORTH;
			BlockPos clay = switch (rot) {
				case EAST -> new BlockPos(centre(other[0]) - 15, height, tlz + 15);
				case WEST -> new BlockPos(brx + 15, height, brz - 15);
				case SOUTH -> new BlockPos(tlx - 15, height, tlz - 15);
				case NORTH -> new BlockPos(brx + 15, height, brz + 15);
			};
			options.add(new Layout(rot, clay));
		}
		for (Layout o : options) if (isClay.test(o.clay())) return o;
		return options.get(0);
	}
}
