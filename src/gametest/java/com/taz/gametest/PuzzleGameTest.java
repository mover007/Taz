package com.taz.gametest;

import com.google.gson.JsonPrimitive;
import com.taz.Taz;
import com.taz.dungeons.puzzles.Puzzles;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TextHud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.stream.Stream;

/**
 * The puzzle solvers, in a test room built in mid-air (the same column each time, renamed to the puzzle being tested, facing
 * east from its clay). Parts: quiz, weirdos, blaze, boulder, icefill, beams, water, icepath, maze, tictactoe ("puzzles" runs
 * them all). Runs with -Dtaz.dungeons.anywhere=true.
 */
public class PuzzleGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"quiz", "weirdos", "blaze", "boulder", "icefill", "beams", "water", "icepath", "maze", "tictactoe"};
	private static final int CX = -121, CZ = -89, ROOF = 80, FLOOR = 60;
	private static final BlockPos CLAY = new BlockPos(CX - 15, ROOF, CZ + 15);
	/** The room as Taz will see it, to work out where puzzle blocks go. */
	private static final DungeonRooms.Room ROOM = DungeonRooms.placedAt(DungeonRooms.Rotation.EAST, CLAY);
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;
	private int core;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "puzzles");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Stream.of(PARTS).noneMatch(PuzzleGameTest::runs)) {
			System.out.println("[TestFilter] skipped puzzles");
			return;
		}
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			ctx.runOnClient(mc -> Taz.config().puzzles.enabled = true);
			build();
			if (runs("quiz")) quiz();
			if (runs("weirdos")) weirdos();
			if (runs("blaze")) blaze();
			if (runs("boulder")) boulder();
			if (runs("icefill")) iceFill();
			if (runs("beams")) beams();
			if (runs("water")) water();
			if (runs("icepath")) icePath();
			if (runs("maze")) maze();
			if (runs("tictactoe")) ticTacToe();
		}
		System.out.println("[PuzzleGameTest] PASSED");
	}

	private void build() {
		cmd("gamemode creative @p");
		cmd("tp @p " + CX + " " + (FLOOR + 1) + " " + CZ);
		ctx.waitTicks(20);
		sp.getClientLevel().waitForChunksRender();
		cmd("fill " + (CX - 15) + " " + FLOOR + " " + (CZ - 15) + " " + (CX + 15) + " " + FLOOR + " " + (CZ + 15) + " stone");
		cmd("fill " + CX + " " + (FLOOR + 1) + " " + CZ + " " + CX + " 140 " + CZ + " air");
		cmd("setblock " + CX + " " + ROOF + " " + CZ + " stone");
		cmd("setblock " + CX + " " + (ROOF - 1) + " " + CZ + " glowstone");
		cmd("setblock " + CLAY.getX() + " " + CLAY.getY() + " " + CLAY.getZ() + " blue_terracotta");
		cmd("tp @p " + (CX + 2) + " " + (FLOOR + 1) + " " + (CZ + 2));
		ctx.waitTicks(10);
	}

	/** Makes the test room the named puzzle and waits until Taz is in it. */
	private void enter(String name) {
		core = ctx.computeOnClient(mc -> DungeonRooms.coreAt(mc.level, CX, CZ, new int[1]));
		ctx.runOnClient(mc -> {
			DungeonRooms.addRoom(core, name, "1x1");
			DungeonRooms.reset();
		});
		ctx.waitTicks(30);
		check("in the " + name + " room", ctx.computeOnClient(mc -> DungeonRooms.current() != null && DungeonRooms.current().data.name().equals(name)
				&& DungeonRooms.current().rotation == DungeonRooms.Rotation.EAST));
	}

	private BlockPos real(int x, int y, int z) {
		return ROOM.toReal(new BlockPos(x, y, z));
	}

	private void set(BlockPos p, String block) {
		cmd("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + block);
	}

	private void quiz() {
		enter("Quiz");
		tell("[STATUE] Oruo the Omniscient: I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know.");
		check("quiz: 11s to answer", ctx.computeOnClient(mc -> Puzzles.quizTicks()) == 220 && hud().equals(List.of("Quiz (1/3) 11.00s")));
		long year = (System.currentTimeMillis() / 1000 - 1560276000) / 446400 + 1;
		tell("What SkyBlock year is it?");
		tell("     ⓐ Year " + (year + 3));
		tell("     ⓑ Year " + year);
		tell("     ⓒ Year " + (year - 7));
		check("quiz: the year worked out, answer b", !ctx.computeOnClient(mc -> Puzzles.quizCorrect(0)) && ctx.computeOnClient(mc -> Puzzles.quizCorrect(1))
				&& !ctx.computeOnClient(mc -> Puzzles.quizCorrect(2)));
		tell("Which of these enemies does not spawn in the Spiders Den?");
		tell("     ⓐ Blaze");
		tell("     ⓒ Broodfather");
		check("quiz: from Odin's answers", ctx.computeOnClient(mc -> Puzzles.quizCorrect(2)));
		ctx.takeScreenshot("puzzle-quiz");
	}

	private void weirdos() {
		enter("Three Weirdos");
		BlockPos npc = real(13, 69, 24);
		cmd("summon armor_stand " + (npc.getX() + 0.5) + " 61 " + (npc.getZ() + 0.5) + " {CustomName:\"Ardis\",NoGravity:1b}");
		ctx.waitTicks(5);
		tell("[NPC] Ardis: The reward is not in my chest!");
		BlockPos expected = ctx.computeOnClient(mc -> {
			DungeonRooms.Room r = DungeonRooms.current();
			return r.toReal(r.toRelative(new BlockPos((int) (npc.getX() + 0.5) - 1, 69, (int) (npc.getZ() + 0.5) - 1)).offset(1, 0, 0));
		});
		check("weirdos: Ardis tells the truth, so it's their chest (" + ctx.computeOnClient(mc -> Puzzles.weirdosRight()) + ")",
				expected.equals(ctx.computeOnClient(mc -> Puzzles.weirdosRight())));
		cmd("kill @e[type=armor_stand]");
	}

	private void blaze() {
		enter("Higher Blaze");
		ctx.runOnClient(mc -> Taz.config().puzzles.hideBlazes = true);
		cmd("summon armor_stand " + (CX + 5) + " 62 " + CZ + " {CustomName:\"[Lv10] Blaze 300/300❤\",NoGravity:1b,Tags:[\"blazetag\"]}");
		cmd("summon armor_stand " + (CX - 5) + " 62 " + CZ + " {CustomName:\"[Lv10] Blaze 90/100❤\",NoGravity:1b,Tags:[\"blazetag\"]}");
		cmd("summon blaze " + (CX + 5) + " 61 " + (CZ + 4) + " {NoAI:1b}");
		ctx.waitTicks(15);
		check("blaze: two blazes read", ctx.computeOnClient(mc -> Puzzles.blazes()) == 2);
		check("blaze: the blaze is hidden", ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Blaze.class,
				mc.player.getBoundingBox().inflate(20)).stream().allMatch(com.taz.render.Hidden::hide)));
		ctx.takeScreenshot("puzzle-blaze");
		cmd("kill @e[tag=blazetag]");
		ctx.waitTicks(15);
		check("blaze: solved once they're gone (" + Puzzles.lastSolved + ")", "Higher Blaze".equals(Puzzles.lastSolved));
		cmd("kill @e[type=blaze]");
	}

	private void boulder() {
		String layout = "010000010111101001010011100000101110000111";
		int i = 0;
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) {
				set(real(x, 66, z), layout.charAt(i++) == '1' ? "stone" : "air");
			}
		}
		ctx.waitTicks(3);
		enter("Boulder");
		check("boulder: the layout's two clicks", ctx.computeOnClient(mc -> Puzzles.boulderClicks()) == 2);
		BlockPos button = real(23, 65, 15);
		ctx.runOnClient(mc -> Puzzles.onUseBlock(button));
		check("boulder: one left after pressing its button", ctx.computeOnClient(mc -> Puzzles.boulderClicks()) == 1);
		ctx.takeScreenshot("puzzle-boulder");
		for (int z = 24; z >= 9; z -= 3) for (int x = 24; x >= 6; x -= 3) set(real(x, 66, z), "air");
	}

	private void iceFill() {
		set(real(16, 70, 9), "stone");
		set(real(16, 71, 13), "stone");
		set(real(14, 72, 23), "stone");
		ctx.waitTicks(3);
		enter("Ice Fill");
		check("ice fill: a path over all three floors (" + ctx.computeOnClient(mc -> Puzzles.iceFillPoints()) + ")",
				ctx.computeOnClient(mc -> Puzzles.iceFillPoints()) > 10);
		ctx.takeScreenshot("puzzle-ice-fill");
		set(real(16, 70, 9), "air");
		set(real(16, 71, 13), "air");
		set(real(14, 72, 23), "air");
	}

	private void beams() {
		set(real(15, 78, 3), "sea_lantern");
		set(real(15, 76, 27), "sea_lantern");
		ctx.waitTicks(3);
		enter("Creeper Beams");
		check("beams: one pair of lanterns", ctx.computeOnClient(mc -> Puzzles.beamPairs()) == 1);
		set(real(15, 78, 3), "prismarine");
		ctx.waitTicks(5);
		check("beams: gone once it's done", ctx.computeOnClient(mc -> Puzzles.beamPairs()) == 0);
		set(real(15, 76, 27), "air");
		set(real(15, 78, 3), "air");
	}

	private void water() {
		set(real(14, 77, 27), "terracotta");
		set(real(15, 56, 19), "purple_wool");
		set(real(15, 56, 18), "orange_wool");
		set(real(15, 56, 17), "blue_wool");
		BlockPos gold = real(20, 61, 15);
		set(gold, "lever[face=floor]");
		ctx.waitTicks(3);
		enter("Water Board");
		ctx.waitTicks(3);
		check("water: board 0, gates 012, seven clicks", ctx.computeOnClient(mc -> Puzzles.waterBoard()) == 0
				&& ctx.computeOnClient(mc -> Puzzles.waterClicks()) == 7);
		set(gold, "lever[face=floor,powered=true]");
		ctx.waitTicks(3);
		check("water: flipping the gold lever ticks it off", ctx.computeOnClient(mc -> Puzzles.waterClicks()) == 6);
		ctx.takeScreenshot("puzzle-water");
	}

	private void icePath() {
		BlockPos fish = real(14, 67, 7);
		cmd("summon silverfish " + (fish.getX() + 0.5) + " 67 " + (fish.getZ() + 0.5) + " {NoAI:1b,NoGravity:1b}");
		ctx.waitTicks(3);
		enter("Ice Path");
		ctx.waitTicks(5);
		check("ice path: one slide to the exit (" + ctx.computeOnClient(mc -> Puzzles.icePathSlides()) + ")", ctx.computeOnClient(mc -> Puzzles.icePathSlides()) == 1);
		cmd("kill @e[type=silverfish]");
	}

	private void maze() {
		enter("Teleport Maze");
		List<BlockPos> pads = ctx.computeOnClient(mc -> Puzzles.mazePads());
		check("maze: 30 pads", pads.size() == 30);
		BlockPos start = pads.get(0);
		Vec3 on = new Vec3(start.getX() + 0.5, 69.5, start.getZ() + 0.5);
		ctx.runOnClient(mc -> Puzzles.onTeleport(on, 180, 0));
		check("maze: the pads you're facing are left, not the one you're on (" + ctx.computeOnClient(mc -> Puzzles.mazeCorrect().size()) + ")",
				ctx.computeOnClient(mc -> !Puzzles.mazeCorrect().isEmpty() && Puzzles.mazeCorrect().size() < 30 && !Puzzles.mazeCorrect().contains(start)
						&& Puzzles.mazeBest() != null));
	}

	private void ticTacToe() {
		check("tic tac toe: block the row", ctx.computeOnClient(mc -> Puzzles.ticTacToeBest("XX..O....")).equals(List.of(2)));
		check("tic tac toe: take the win", ctx.computeOnClient(mc -> Puzzles.ticTacToeBest("XX.OO.X..")).equals(List.of(5)));
	}

	// ---- helpers

	private List<String> hud() {
		return ctx.computeOnClient(mc -> {
			for (MovableHud h : TazHud.all()) {
				if (h instanceof com.taz.dungeons.puzzles.Quiz q) return ((TextHud) q).shownLines(false).stream().map(net.minecraft.ChatFormatting::stripFormatting).toList();
			}
			throw new AssertionError("Quiz HUD not registered");
		});
	}

	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("puzzles: " + what);
		System.out.println("[PuzzleGameTest] ok: " + what);
	}
}
