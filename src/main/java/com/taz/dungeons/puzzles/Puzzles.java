package com.taz.dungeons.puzzles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Puzzle Solvers: shows the answer to each dungeon puzzle (Water Board, Teleport Maze, Ice Fill, Ice Path, Blaze, Creeper
 * Beams, Three Weirdos, Quiz, Boulder, Tic Tac Toe), times how long each took you, and offers an Architect's First Draft
 * when someone fails one. Mostly Odin's solvers (and their solution tables), Ice Path and Tic Tac Toe from NoammAddons.
 *
 * <p>Positions are room-relative the way Odin's are (from the room's blue clay corner, turned to face north); Taz's
 * {@link DungeonRooms.Room#toReal} turns them into the world.
 */
public final class Puzzles {
	/** The Taz colours for answers: the one to do now, the next, the one after, the rest, and wrong. */
	static final int FIRST = 0xFFFF2E2E, SECOND = 0xFFFFB0B0, THIRD = 0xFFB3121B, OTHER = 0x60FFFFFF, WRONG = 0xFF000000;
	private static final Set<String> PUZZLES = Set.of("Water Board", "Teleport Maze", "Ice Fill", "Ice Path", "Lower Blaze", "Higher Blaze",
			"Creeper Beams", "Three Weirdos", "Quiz", "Boulder", "Tic Tac Toe");
	private static final Pattern FAIL = Pattern.compile("^PUZZLE FAIL! (\\w{1,16}) .+$|^\\[STATUE] Oruo the Omniscient: (\\w{1,16}) chose the wrong "
			+ "answer! I shall never forget this moment of misrememberance\\.$");
	private static final Pattern NPC = Pattern.compile("^\\[NPC] (.+?): (.+)$");
	private static final Path TIMES = Taz.CONFIG_DIR.resolve("dungeons").resolve("puzzle-times.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Map<String, Long> ENTERED = new HashMap<>();
	private static final Map<String, Boolean> TIMED = new HashMap<>();
	private static Map<String, Long> best;
	private static ClientLevel level;
	private static DungeonRooms.Room lastRoom;
	static int serverTicks;

	private Puzzles() {
	}

	static PuzzlesConfig cfg() {
		return PuzzlesConfig.get();
	}

	/** The puzzle room you're in, or null. */
	static DungeonRooms.Room room(String... names) {
		DungeonRooms.Room r = DungeonRooms.current();
		if (r == null || !r.resolved()) return null;
		for (String n : names) if (r.data.name().equals(n)) return r;
		return null;
	}

	static boolean active() {
		return cfg().enabled && DungeonLocation.inDungeon() && !DungeonLocation.inBoss();
	}

	static JsonElement json(String file) {
		try (InputStream in = Puzzles.class.getResourceAsStream("/assets/taz/dungeons/puzzles/" + file)) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read puzzle data {}", file, e);
			return null;
		}
	}

	static Vec3 center(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			reset();
		}
		if (!active() || mc.player == null) return;
		DungeonRooms.Room now = DungeonRooms.current();
		if (now != lastRoom && now != null && now.resolved()) {
			lastRoom = now;
			onRoomEnter(now);
		}
		WaterBoard.tick();
		Blaze.tick(mc);
		IcePath.tick(mc);
		TicTacToe.tick(mc);
	}

	private static void onRoomEnter(DungeonRooms.Room room) {
		String name = room.data.name();
		if (PUZZLES.contains(name) && cfg().timers) ENTERED.putIfAbsent(name, System.currentTimeMillis());
		Boulder.onRoomEnter(room);
		IceFill.onRoomEnter(room);
		TeleportMaze.onRoomEnter(room);
		Beams.onRoomEnter(room);
		Quiz.onRoomEnter(room);
		Blaze.sort(name);
		IcePath.onRoomEnter(room);
		TicTacToe.onRoomEnter(room);
	}

	/** Every server tick. */
	public static void onServerTick() {
		serverTicks++;
		Quiz.onServerTick();
	}

	public static void onChat(String line) {
		if (!active()) return;
		if (cfg().draftPrompt && FAIL.matcher(line).matches() && DungeonRooms.current() != null && PUZZLES.contains(DungeonRooms.current().data.name())) {
			TazChat.send(Component.literal("Click here to get an Architect's First Draft").withStyle(s -> s
					.withColor(0xFFB0B0)
					.withClickEvent(new ClickEvent.RunCommand("gfs architect's first draft 1"))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Takes one from your sacks")))));
		}
		var m = NPC.matcher(line);
		if (m.matches()) Weirdos.onNpc(m.group(1), m.group(2));
		Quiz.onChat(line);
	}

	/** A block event: the puzzle's reward chest opening means it's done. */
	public static void onBlockEvent(BlockPos pos, Block block) {
		if (!active() || block != Blocks.CHERRY_LOG) return;
		DungeonRooms.Room r = DungeonRooms.current();
		if (r == null || !r.resolved()) return;
		boolean done = switch (r.data.name()) {
			case "Three Weirdos" -> pos.equals(r.toReal(new BlockPos(18, 69, 24))) || pos.equals(r.toReal(new BlockPos(16, 69, 25)))
					|| pos.equals(r.toReal(new BlockPos(14, 69, 24)));
			case "Ice Fill" -> pos.equals(r.toReal(new BlockPos(14, 75, 29))) || pos.equals(r.toReal(new BlockPos(16, 75, 29)));
			case "Teleport Maze" -> pos.equals(r.toReal(new BlockPos(15, 70, 20)));
			case "Water Board" -> pos.equals(r.toReal(new BlockPos(15, 56, 22)));
			case "Boulder" -> pos.equals(r.toReal(new BlockPos(15, 66, 29)));
			default -> false;
		};
		if (done) solved(r.data.name());
	}

	public static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		if (!active()) return;
		Beams.onBlockChanged(pos, old, now);
		WaterBoard.onBlockChanged(pos, old, now);
	}

	/** You right-clicked a block. */
	public static void onUseBlock(BlockPos pos) {
		if (active()) Boulder.onClick(pos);
	}

	public static void onEntityEvent(Entity entity, byte id) {
		if (active()) Blaze.onEntityEvent(entity, id);
	}

	/** The server moved you (a teleport pad). */
	public static void onTeleport(Vec3 pos, float yaw, float pitch) {
		if (active()) TeleportMaze.onTeleport(pos, yaw, pitch);
	}

	/** Whether to hide this entity (the blazes, while the Blaze solver shows them). */
	public static boolean hide(Entity e) {
		return active() && cfg().blaze && cfg().hideBlazes && Blaze.hides(e);
	}

	/** A puzzle was solved: say how long it took. */
	static void solved(String name) {
		Long start = ENTERED.get(name);
		if (!cfg().timers || start == null || TIMED.containsKey(name)) return;
		TIMED.put(name, true);
		lastSolved = name;
		long ms = System.currentTimeMillis() - start;
		Long pb = best().get(name);
		String extra = pb == null ? "" : ms < pb ? " (new best, -" + String.format("%.1f", (pb - ms) / 1000.0) + "s)" : " (best " + String.format("%.1f", pb / 1000.0) + "s)";
		TazChat.info(name + " solved in " + String.format("%.1f", ms / 1000.0) + "s" + extra);
		if (pb == null || ms < pb) {
			best().put(name, ms);
			try {
				Files.createDirectories(TIMES.getParent());
				Files.writeString(TIMES, GSON.toJson(best()));
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't save the puzzle times", e);
			}
		}
	}

	private static Map<String, Long> best() {
		if (best == null) {
			best = new HashMap<>();
			try {
				if (Files.exists(TIMES)) best.putAll(GSON.fromJson(Files.readString(TIMES), new TypeToken<Map<String, Long>>() {}.getType()));
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't read the puzzle times", e);
			}
		}
		return best;
	}

	// ---- test hooks

	/** The last puzzle Taz saw solved. */
	public static String lastSolved;

	public static int quizTicks() {
		return Quiz.ticksLeft();
	}

	public static boolean quizCorrect(int option) {
		return Quiz.correct(option);
	}

	public static BlockPos weirdosRight() {
		return Weirdos.right();
	}

	public static int blazes() {
		return Blaze.count();
	}

	public static int boulderClicks() {
		return Boulder.clicksLeft();
	}

	public static int iceFillPoints() {
		return IceFill.points();
	}

	public static int beamPairs() {
		return Beams.pairs();
	}

	public static int waterBoard() {
		return WaterBoard.board();
	}

	public static int waterClicks() {
		return WaterBoard.clicksLeft();
	}

	public static int icePathSlides() {
		return IcePath.slides();
	}

	public static java.util.List<BlockPos> mazePads() {
		return TeleportMaze.pads();
	}

	public static java.util.List<BlockPos> mazeCorrect() {
		return TeleportMaze.correct();
	}

	public static BlockPos mazeBest() {
		return TeleportMaze.best();
	}

	/** The best Tic Tac Toe moves for O on a board written as 9 characters ("XX..O...."). */
	public static java.util.List<Integer> ticTacToeBest(String board) {
		char[] b = new char[9];
		for (int i = 0; i < 9; i++) b[i] = board.charAt(i) == '.' ? 0 : board.charAt(i);
		return TicTacToe.best(b, 'O', 'X');
	}

	public static void reset() {
		ENTERED.clear();
		TIMED.clear();
		lastRoom = null;
		serverTicks = 0;
		WaterBoard.reset();
		TeleportMaze.reset();
		IceFill.reset();
		Blaze.reset();
		Beams.reset();
		Weirdos.reset();
		Quiz.clear();
		Boulder.reset();
		IcePath.reset();
		TicTacToe.reset();
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		if (!active()) return;
		try (var batch = com.taz.render.WorldRenderer.begin(context)) {
			PuzzlesConfig c = cfg();
			if (c.waterBoard) WaterBoard.render(batch);
			if (c.teleportMaze) TeleportMaze.render(batch);
			if (c.iceFill) IceFill.render(batch);
			if (c.icePath) IcePath.render(batch);
			if (c.blaze) Blaze.render(batch);
			if (c.beams) Beams.render(batch);
			if (c.weirdos) Weirdos.render(batch);
			if (c.quiz) Quiz.render(batch);
			if (c.boulder) Boulder.render(batch);
			if (c.ticTacToe) TicTacToe.render(batch);
		} catch (Exception e) {
			Taz.LOGGER.error("Puzzle solvers failed to draw", e);
		}
	}
}
