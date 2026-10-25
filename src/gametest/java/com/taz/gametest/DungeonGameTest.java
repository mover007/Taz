package com.taz.gametest;

import com.google.gson.JsonPrimitive;
import com.taz.Taz;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.secrets.RoomSecretTimes;
import com.taz.dungeons.secrets.RoomTimesConfig;
import com.taz.dungeons.skillissue.SkillIssue;
import com.taz.dungeons.skillissue.SkillIssueConfig;
import com.taz.dungeons.secrets.SecretRoutes;
import com.taz.dungeons.secrets.SecretTrainer;
import com.taz.dungeons.secrets.SecretsConfig;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TextHud;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Dungeon features. secrets: Odin's room table and rotation maths, then a fake 1x1 room built on the dungeon grid with a
 * test route imported from an Odin export, walked through in Guide mode. roomtimes: Room Secret Times in that room,
 * without Secret Routes. skillissue: death jokes from the run's chat. "dungeons" runs them all. Runs with
 * -Dtaz.dungeons.anywhere=true, so the flat test world counts as a dungeon.
 */
public class DungeonGameTest implements FabricClientGameTest {
	/** Tile (2, 3) of the dungeon grid: centre -121, -89. Its roof is at y 80, floor at 60. */
	private static final int CX = -121, CZ = -89, ROOF = 80, FLOOR = 60;
	/** The blue terracotta on the roof's south-west corner: the room faces east. */
	private static final BlockPos CLAY = new BlockPos(CX - 15, ROOF, CZ + 15);
	/** The route (3ppopka's legend): red start, purple etherwarp, green chest, purple bat spot, purple slab spot; plus a yellow aim spot, a prince and a note. */
	private static final BlockPos START = new BlockPos(-128, FLOOR, -92);
	private static final BlockPos STEP1 = new BlockPos(-125, FLOOR, -89);
	private static final BlockPos CHEST = new BlockPos(-118, FLOOR + 1, -85);
	private static final BlockPos AIM = new BlockPos(-118, FLOOR + 1, -95);
	private static final BlockPos STEP3 = new BlockPos(-110, FLOOR, -80);
	private static final BlockPos STEP4 = new BlockPos(-112, FLOOR, -95);
	private static final BlockPos PRINCE = new BlockPos(-115, FLOOR + 1, -100);
	private static final BlockPos TEXT = new BlockPos(-121, FLOOR + 2, -89);
	/** Step 5 is a block to mine, step 6 a lever; "line+tp" is a mining hint with no number. */
	private static final BlockPos MINE = new BlockPos(-108, FLOOR + 1, -90);
	private static final BlockPos LEVER = new BlockPos(-107, FLOOR + 1, -98);
	private static final BlockPos LINE = new BlockPos(-130, FLOOR + 1, -100);
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "dungeons");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!runs("secrets") && !runs("roomtimes") && !runs("skillissue")) {
			System.out.println("[TestFilter] skipped dungeons");
			return;
		}
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			cmd("gamerule doMobSpawning false");
			if (runs("secrets")) {
				tables();
				rotations();
				layouts();
			}
			if (runs("secrets") || runs("roomtimes")) buildRoom();
			if (runs("secrets")) route();
			if (runs("roomtimes")) roomTimes();
			if (runs("skillissue")) skillIssue();
		}
		System.out.println("[DungeonGameTest] PASSED");
	}

	// ---- Odin's data

	private void tables() {
		check("room table: Odin's 140 rooms", DungeonRooms.knownRooms() == 140);
		SecretRoutes.Pack taz = SecretRoutes.defaults();
		check("Taz's routes: 139+ rooms, 1175 waypoints (" + taz.rooms().size() + ", " + taz.waypoints() + ")", taz.rooms().size() >= 139 && taz.waypoints() == 1175);
		List<SecretRoutes.Waypoint> mossy = taz.rooms().get("Mossy");
		check("Mossy: 9 waypoints, steps 1 to 7", mossy.size() == 9 && mossy.stream().mapToInt(SecretRoutes.Waypoint::step).max().orElse(0) == 7);
		SecretRoutes.Waypoint pipes = taz.rooms().get("Pipes").stream().filter(w -> "17 | done".equals(w.title())).findFirst().orElseThrow();
		check("titles: \"17 | done\" is step 17, a chest", pipes.step() == 17 && pipes.note().equals("done") && pipes.small());
		check("colours: #AA00AAFF is purple", taz.rooms().get("Temple").get(1).argb() == 0xFFAA00AA);
		List<SecretRoutes.Waypoint> pipes2 = taz.rooms().get("Pipes");
		check("legend: red is the start, purple etherwarp (a small box too), green a secret, yellow aim",
				pipes2.get(0).kind() == SecretRoutes.Kind.START && pipes2.get(2).kind() == SecretRoutes.Kind.ETHERWARP
						&& pipes2.get(3).kind() == SecretRoutes.Kind.ETHERWARP && pipes2.get(3).box().getXsize() < 0.2
						&& pipes2.get(1).kind() == SecretRoutes.Kind.SECRET && pipes2.get(19).kind() == SecretRoutes.Kind.AIM);
		check("legend: aqua is a prince, see-through is a note", taz.rooms().get("Leaves").stream().anyMatch(w -> w.kind() == SecretRoutes.Kind.PRINCE)
				&& taz.rooms().get("Entrance").stream().filter(w -> w.title() != null && w.title().startsWith("Routes by 3ppopka"))
						.allMatch(w -> w.kind() == SecretRoutes.Kind.TEXT));
		check("legend: what the notes mean", SecretRoutes.explain(SecretRoutes.Kind.ETHERWARP, "5 breakwarp").startsWith("teleport normally, break the block")
				&& SecretRoutes.explain(SecretRoutes.Kind.AIM, "").startsWith("aim here and right-click")
				&& SecretRoutes.explain(SecretRoutes.Kind.ETHERWARP, "bat").contains("Hyperion")
				&& SecretRoutes.explain(SecretRoutes.Kind.ETHERWARP, "line").equals("mine only the line")
				&& SecretRoutes.explain(SecretRoutes.Kind.ETHERWARP, "down").isEmpty());
	}

	private void rotations() {
		BlockPos clay = new BlockPos(-200, 80, -100);
		BlockPos rel = new BlockPos(3, 70, 5);
		Map<DungeonRooms.Rotation, BlockPos> expected = Map.of(
				DungeonRooms.Rotation.SOUTH, new BlockPos(-197, 70, -95),
				DungeonRooms.Rotation.NORTH, new BlockPos(-203, 70, -105),
				DungeonRooms.Rotation.WEST, new BlockPos(-205, 70, -97),
				DungeonRooms.Rotation.EAST, new BlockPos(-195, 70, -103));
		AABB lever = new AABB(0.3, 0, 0.2, 0.7, 0.6, 0.5);
		for (DungeonRooms.Rotation rot : DungeonRooms.Rotation.values()) {
			DungeonRooms.Room r = DungeonRooms.placedAt(rot, clay);
			BlockPos real = r.toReal(rel);
			check("rotation " + rot + ": " + rel + " -> " + real, real.equals(expected.get(rot)) && r.toRelative(real).equals(rel));
			AABB box = r.toReal(lever, real);
			check("rotation " + rot + ": box keeps its size", Math.abs(box.getXsize() * box.getZsize() - 0.12) < 1e-9 && near(box.getYsize(), 0.6));
		}
		AABB north = DungeonRooms.placedAt(DungeonRooms.Rotation.NORTH, clay).toReal(lever, BlockPos.ZERO);
		check("rotation NORTH: box turned half way", near(north.minX, 0.3) && near(north.maxX, 0.7) && near(north.minZ, 0.5) && near(north.maxZ, 0.8));
	}

	private void layouts() {
		layout("1x2 along z faces west", DungeonRooms.Shape.TWO, List.of(t(2, 1), t(2, 2)), DungeonRooms.Rotation.WEST, -106, -168, null);
		layout("1x3 along x faces south", DungeonRooms.Shape.THREE, List.of(t(1, 4), t(2, 4), t(3, 4)), DungeonRooms.Rotation.SOUTH, -168, -72, null);
		layout("2x2 faces south", DungeonRooms.Shape.SQUARE, List.of(t(0, 0), t(1, 0), t(0, 1), t(1, 1)), DungeonRooms.Rotation.SOUTH, -200, -200, null);
		layout("L without top-right faces south", DungeonRooms.Shape.L, List.of(t(0, 0), t(0, 1), t(1, 1)), DungeonRooms.Rotation.SOUTH, -200, -200, null);
		layout("L without bottom-left faces north", DungeonRooms.Shape.L, List.of(t(0, 0), t(1, 0), t(1, 1)), DungeonRooms.Rotation.NORTH, -138, -138, null);
		layout("L without bottom-right faces west", DungeonRooms.Shape.L, List.of(t(0, 0), t(1, 0), t(0, 1)), DungeonRooms.Rotation.WEST, -138, -200, null);
		layout("L without top-left: the corner with clay (north)", DungeonRooms.Shape.L, List.of(t(1, 0), t(0, 1), t(1, 1)), DungeonRooms.Rotation.NORTH, -138, -138, new BlockPos(-138, 80, -138));
		layout("L without top-left: the corner with clay (south)", DungeonRooms.Shape.L, List.of(t(1, 0), t(0, 1), t(1, 1)), DungeonRooms.Rotation.SOUTH, -200, -200, new BlockPos(-200, 80, -200));
	}

	private void layout(String what, DungeonRooms.Shape shape, List<int[]> tiles, DungeonRooms.Rotation rot, int x, int z, BlockPos clayAt) {
		DungeonRooms.Layout l = DungeonRooms.layout(shape, tiles, 80, p -> p.equals(clayAt));
		check("layout: " + what + " (" + l + ")", l.rotation() == rot && l.clay().getX() == x && l.clay().getZ() == z);
	}

	// ---- a room in the world

	private void buildRoom() {
		cmd("gamemode creative @p"); // the room is built in mid-air
		cmd("tp @p " + CX + " " + (FLOOR + 1) + " " + CZ);
		ctx.waitTicks(20);
		sp.getClientLevel().waitForChunksRender();
		cmd("fill " + (CX - 15) + " " + FLOOR + " " + (CZ - 15) + " " + (CX + 15) + " " + FLOOR + " " + (CZ + 15) + " stone");
		cmd("fill " + CX + " " + (FLOOR + 1) + " " + CZ + " " + CX + " 140 " + CZ + " air");
		cmd("setblock " + CX + " " + ROOF + " " + CZ + " stone");
		cmd("setblock " + CX + " " + (ROOF - 1) + " " + CZ + " glowstone");
		cmd("setblock " + CLAY.getX() + " " + CLAY.getY() + " " + CLAY.getZ() + " blue_terracotta");
		cmd("setblock " + CHEST.getX() + " " + CHEST.getY() + " " + CHEST.getZ() + " chest");
		cmd("setblock " + MINE.getX() + " " + MINE.getY() + " " + MINE.getZ() + " stone");
		cmd("setblock " + LINE.getX() + " " + LINE.getY() + " " + LINE.getZ() + " stone");
		cmd("setblock " + LEVER.getX() + " " + LEVER.getY() + " " + LEVER.getZ() + " lever[face=floor]");
		cmd("tp @p " + (CX + 2) + " " + (FLOOR + 1) + " " + (CZ + 2));
		ctx.waitTicks(10);
		int core = ctx.computeOnClient(mc -> DungeonRooms.coreAt(mc.level, CX, CZ, new int[1]));
		ctx.runOnClient(mc -> {
			DungeonRooms.addRoom(core, "Test Room", "1x1");
			DungeonRooms.reset();
		});
		ctx.waitTicks(30);
		DungeonRooms.Room room = ctx.computeOnClient(mc -> DungeonRooms.current());
		check("room: found by its core, facing east from its clay (" + (room == null ? null : room.rotation + " " + room.clay) + ")",
				room != null && room.data.name().equals("Test Room") && room.rotation == DungeonRooms.Rotation.EAST && room.clay.equals(CLAY));
	}

	private void route() {
		ctx.runOnClient(mc -> {
			SecretsConfig cfg = Taz.config().dungeons.secrets;
			cfg.enabled = true;
			cfg.mode = SecretsConfig.Mode.GUIDE;
			cfg.defaultRoutes = false;
			cfg.reach = 2;
			mc.keyboardHandler.setClipboard(odinExport());
			SecretTrainer.importFromClipboard();
		});
		ctx.waitTicks(10);
		List<BlockPos> placed = ctx.computeOnClient(mc -> SecretTrainer.positions());
		check("room: the Odin waypoints land on their blocks (" + placed + ")", placed.equals(List.of(START, STEP1, CHEST, AIM, STEP3, STEP4, PRINCE, TEXT, MINE, LEVER, LINE)));
		List<String> lines = hudLines();
		check("guide: starts at the red start, no timer yet (" + lines + ")", step() == SecretTrainer.START && !started()
				&& lines.stream().anyMatch(l -> l.startsWith("Start · stand on the red block")) && lines.stream().anyMatch(l -> l.startsWith("then 1")));
		ctx.takeScreenshot("dungeon-secrets-guide");

		goTo(START.above());
		check("guide: on the red start, step 1 next, still no timer (" + hudLines() + ")", !started() && step() == 1
				&& hudLines().stream().anyMatch(l -> l.startsWith("Step 1/6 Etherwarp")));
		goTo(STEP1.above());
		check("guide: standing on step 1 ticks it off, still no timer", step() == 2 && !started());
		cmd("tp @p " + (CHEST.getX() - 2) + " " + CHEST.getY() + " " + CHEST.getZ() + " facing " + CHEST.getX() + " " + CHEST.getY() + " " + CHEST.getZ());
		ctx.waitTicks(4);
		ctx.takeScreenshot("dungeon-secrets-chest");
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(CHEST), Direction.WEST, CHEST, false)));
		ctx.waitTicks(4);
		ctx.runOnClient(mc -> mc.player.closeContainer());
		check("guide: opening step 2's chest ticks it off and starts the timer (the first secret)", step() == 3 && started());
		goTo(STEP3.above());
		lines = hudLines();
		check("legend: a bat spot waits for the bat (" + lines + ")", step() == 3 && lines.stream().anyMatch(l -> l.contains("kill the bat with Hyperion")));
		cmd("summon bat " + STEP3.getX() + " " + (FLOOR + 4) + " " + STEP3.getZ() + " {NoAI:1b}");
		ctx.waitTicks(5);
		cmd("kill @e[type=bat]");
		ctx.waitTicks(30);
		check("legend: killing the bat ticks it off", step() == 4);
		goTo(AIM.above());
		check("legend: a yellow aim spot isn't somewhere to stand", step() == 4);
		goTo(STEP4.above());
		check("guide: a slab-sized etherwarp spot", step() == 5);
		check("mining: the hint is there before it's mined", !cleared(LINE));
		cmd("setblock " + LINE.getX() + " " + LINE.getY() + " " + LINE.getZ() + " air");
		ctx.waitTicks(3);
		check("mining: breaking a mining hint's block clears it", cleared(LINE) && step() == 5);
		cmd("setblock " + MINE.getX() + " " + MINE.getY() + " " + MINE.getZ() + " air");
		ctx.waitTicks(3);
		check("mining: breaking step 5's block ticks it off", step() == 6 && cleared(MINE));
		cmd("setblock " + LEVER.getX() + " " + LEVER.getY() + " " + LEVER.getZ() + " lever[face=floor,powered=true]");
		ctx.waitTicks(3);
		lines = hudLines();
		Long best = ctx.computeOnClient(mc -> SecretTrainer.bestMs("Test Room"));
		check("lever: flipping step 6's lever ticks it off, the route is done and timed (" + lines + ")", step() == SecretTrainer.DONE
				&& best != null && best > 0 && lines.contains("Route done") && lines.stream().anyMatch(l -> l.startsWith("Test Room done in")));

		// Restart: back to the red start. Arriving on step 1 straight away ticks the start off too.
		ctx.runOnClient(mc -> Taz.config().dungeons.secrets.restartKey = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5);
		goTo(new BlockPos(CX, FLOOR + 1, CZ));
		restart();
		check("restart key: back to the red start, timer waiting", step() == SecretTrainer.START && !started() && !cleared(LINE));
		goTo(STEP1.above());
		check("guide: arriving on a later step ticks off the ones before", step() == 2 && !started());
		restart();
		check("restart key on a spot: doesn't count it again", step() == SecretTrainer.START);

		// The room's secret count going up next to the chest (not straight after the lever, which already counted).
		ctx.waitTicks(40);
		goTo(START.above());
		goTo(STEP1.above());
		actionBar("1/3 Secrets");
		cmd("tp @p " + (CHEST.getX() - 2) + " " + CHEST.getY() + " " + CHEST.getZ());
		ctx.waitTicks(3);
		actionBar("2/3 Secrets");
		check("secret count: going up next to the chest ticks it off", step() == 3 && started());

		// A chest a teammate opens (its lid opens with nobody clicking here).
		goTo(new BlockPos(CX, FLOOR + 1, CZ));
		restart();
		goTo(START.above());
		goTo(STEP1.above());
		sp.getServer().runOnServer(server -> server.overworld().blockEvent(CHEST, net.minecraft.world.level.block.Blocks.CHEST, 1, 1));
		ctx.waitTicks(5);
		check("chest lid: a chest opened by anyone ticks it off", step() == 3);

		// A secret item (any item: Hypixel's are plain Paper, Ender Pearl...) picked up next to the chest.
		goTo(new BlockPos(CX, FLOOR + 1, CZ));
		restart();
		goTo(START.above());
		goTo(STEP1.above());
		sp.getServer().runOnServer(server -> {
			net.minecraft.server.level.ServerLevel level = server.overworld();
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
					CHEST.getX() + 0.5, CHEST.getY() + 1, CHEST.getZ() - 0.5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
			item.setNoPickUpDelay();
			level.addFreshEntity(item);
		});
		ctx.waitTicks(5);
		goTo(new BlockPos(CHEST.getX(), CHEST.getY() + 1, CHEST.getZ() - 1));
		ctx.waitTicks(10);
		check("item: picking up Paper by the chest ticks it off", step() == 3);

		// Export, re-import, Odin's own folder, Normal mode.
		String export = ctx.computeOnClient(mc -> {
			SecretTrainer.exportToClipboard();
			return mc.keyboardHandler.getClipboard();
		});
		Map<String, List<SecretRoutes.Waypoint>> back = ctx.computeOnClient(mc -> {
			try {
				return SecretRoutes.parse(export);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		});
		check("export: reads back the same", back.get("Test Room").equals(ctx.computeOnClient(mc -> SecretRoutes.imported().get(0).rooms().get("Test Room"))));
		Path odinDir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("config").resolve("odin").resolve("dungeon-waypoints"));
		try {
			Files.createDirectories(odinDir);
			Files.writeString(odinDir.resolve("mine.json"), odinJson());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		ctx.runOnClient(mc -> SecretTrainer.importFromOdin());
		check("import from Odin: its packs copied", ctx.computeOnClient(mc -> SecretRoutes.imported().stream().anyMatch(p -> p.name().equals("Odin mine") && p.waypoints() == 11)));
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.secrets.mode = SecretsConfig.Mode.NORMAL;
			Taz.config().dungeons.secrets.defaultRoutes = true;
		});
		cmd("tp @p " + (CX + 8) + " " + (FLOOR + 4) + " " + (CZ - 10) + " facing " + CX + " " + FLOOR + " " + CZ);
		ctx.waitTicks(5);
		ctx.takeScreenshot("dungeon-secrets-normal");
		check("normal: every waypoint (" + hudLines() + ")", hudLines().stream().anyMatch(l -> l.startsWith("11 waypoints")));
		check("Taz's routes: Mossy has its route", ctx.computeOnClient(mc -> SecretRoutes.forRoom("Mossy").size()) == 9);

		ctx.runOnClient(mc -> {
			SecretTrainer.removeImported();
			SecretsConfig cfg = Taz.config().dungeons.secrets;
			cfg.enabled = false;
			cfg.mode = SecretsConfig.Mode.GUIDE;
			cfg.restartKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
			SecretTrainer.resetBest();
		});
		check("remove imported: none left", ctx.computeOnClient(mc -> SecretRoutes.imported().isEmpty()));

		ctx.runOnClient(mc -> Taz.openSettings("Dungeons"));
		ctx.waitTicks(10);
		check("settings: the Dungeons category opens with Secret Routes", ctx.computeOnClient(mc -> mc.screen != null));
		ctx.takeScreenshot("dungeon-secrets-settings");
		ctx.runOnClient(mc -> mc.setScreen(null));
	}

	// ---- Room Secret Times: the room's own secret count, no route

	private void roomTimes() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.secrets.enabled = false;
			RoomTimesConfig cfg = Taz.config().dungeons.roomTimes;
			cfg.enabled = true;
			cfg.partyChat = true;
			cfg.ownChat = true;
		});
		goTo(new BlockPos(CX, FLOOR + 1, CZ));
		check("room times: in the test room, with Secret Routes off", ctx.computeOnClient(mc -> DungeonRooms.current() != null
				&& DungeonRooms.current().data.name().equals("Test Room") && SecretTrainer.roomName() == null));
		actionBar("1/3 Secrets"); // already found when you walk in (a teammate's): the clock waits
		ctx.waitTicks(10);
		actionBar("2/3 Secrets"); // the first secret found here starts it
		ctx.waitTicks(20);
		check("room times: nothing said before the last secret", ctx.computeOnClient(mc -> RoomSecretTimes.lastParty()) == null);
		actionBar("3/3 Secrets");
		String party = ctx.computeOnClient(mc -> RoomSecretTimes.lastParty());
		String own = ctx.computeOnClient(mc -> RoomSecretTimes.lastOwn());
		check("room times: party chat says the room and its time (" + party + ")", party != null && party.matches("\\[TAZ] Test Room secrets done in 0:01\\.\\d"));
		check("room times: your chat has the count and your best (" + own + ")", own != null && own.matches("Test Room secrets done in 0:01\\.\\d \\(3/3, first time!\\)"));
		check("[TAZ] starts every Taz line", TazChat.prefix().getString().equals("[TAZ] "));
		actionBar("3/3 Secrets");
		check("room times: said once per room", party.equals(ctx.computeOnClient(mc -> RoomSecretTimes.lastParty())));
		ctx.runOnClient(mc -> {
			RoomSecretTimes.resetBest();
			Taz.config().dungeons.roomTimes.enabled = false;
		});
	}

	// ---- Skill Issue: death jokes with context

	private void skillIssue() {
		ctx.runOnClient(mc -> {
			SkillIssueConfig cfg = Taz.config().dungeons.skillIssue;
			cfg.enabled = true;
			cfg.where = SkillIssueConfig.Where.PARTY;
			cfg.yourself = true;
		});
		ctx.waitTicks(25);
		String me = ctx.computeOnClient(mc -> mc.player.getName().getString());
		tell("The BLOOD DOOR has been opened!");
		check("skill issue: blood", phase().equals("BLOOD"));
		tell("[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.");
		check("skill issue: a boss the Watcher brings back is still blood", phase().equals("BLOOD"));
		tell("[BOSS] The Watcher: You have proven yourself. You may pass.");
		check("skill issue: on the way to the boss", phase().equals("TO_BOSS"));
		tell("[BOSS] Goldor: Who dares trespass into my domain?");
		check("skill issue: Goldor means terminals", phase().equals("TERMINALS"));
		tell(" ☠ Steve was killed by Goldor and became a ghost.");
		String joke = lastJoke();
		check("skill issue: a terminals joke about Steve (" + joke + ")", fits(joke, false, "Steve", "KILLED", "Goldor", "TERMINALS", null, 1));
		ctx.waitTicks(40);
		tell("The Core entrance is opening!");
		tell(" ☠ Steve was killed by Goldor and became a ghost.");
		joke = lastJoke();
		check("skill issue: core, and Steve's second death (" + joke + ")", phase().equals("CORE") && fits(joke, false, "Steve", "KILLED", "Goldor", "CORE", null, 2));
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> com.taz.dungeons.util.DungeonParty.setClass("Alex", "Healer"));
		tell(" ☠ Alex died to a trap and became a ghost.");
		joke = lastJoke();
		check("skill issue: a healer who found a trap (" + joke + ")", fits(joke, false, "Alex", "TRAP", null, "CORE", "Healer", 1)
				&& ctx.computeOnClient(mc -> SkillIssue.jokesFor(false, "TRAP", null, "CORE", "Healer")).contains("Our healer {name} died. Physician, heal thyself."));
		ctx.waitTicks(40);
		tell(" ☠ Bob disconnected and became a ghost.");
		joke = lastJoke();
		check("skill issue: disconnects get their own jokes (" + joke + ")", fits(joke, false, "Bob", "DISCONNECTED", null, "CORE", null, 1));
		ctx.waitTicks(40);
		tell(" ☠ You were killed by Storm and became a ghost.");
		joke = lastJoke();
		check("skill issue: you, killed by Storm, get an excuse (" + joke + ")", fits(joke, true, me, "KILLED", "Storm", "CORE", null, 1)
				&& ctx.computeOnClient(mc -> SkillIssue.jokesFor(true, "KILLED", "Storm", "CORE", null)).contains("Storm got me. My game froze right when the lightning hit.")
				&& ctx.computeOnClient(mc -> SkillIssue.jokesFor(true, "KILLED", "Storm", "CORE", null)).stream().noneMatch(t -> t.contains("{name}")));
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> Taz.config().dungeons.skillIssue.yourself = false);
		String before = lastJoke();
		tell(" ☠ You died and became a ghost.");
		check("skill issue: your own excuses can be turned off", before.equals(lastJoke()));
		// Two deaths straight after each other both get their joke; a wipe (a fourth in 5 seconds) is cut short.
		ctx.waitTicks(120);
		tell(" ☠ Steve was killed by Goldor and became a ghost.");
		String first = lastJoke();
		tell(" ☠ Alex was killed by Goldor and became a ghost.");
		String second = lastJoke();
		check("skill issue: back-to-back deaths both get a joke (" + first + " / " + second + ")", first.contains("Steve") && second.contains("Alex"));
		tell(" ☠ Bob was killed by Goldor and became a ghost.");
		String third = lastJoke();
		tell(" ☠ Carl was killed by Goldor and became a ghost.");
		check("skill issue: a wipe stops at 3 jokes in 5 seconds (" + third + ")", third.contains("Bob") && third.equals(lastJoke()));
		ctx.runOnClient(mc -> {
			SkillIssue.sample();
			SkillIssueConfig cfg = Taz.config().dungeons.skillIssue;
			cfg.enabled = false;
			cfg.yourself = true;
		});
		partyOutbox(me);
	}

	/** Party messages: one at a time, after anything another mod just sent, confirmed by Hypixel showing them back. */
	private void partyOutbox(String me) {
		ctx.runOnClient(mc -> TazChat.clearParty());
		ctx.waitTicks(20);
		int sent = ctx.computeOnClient(mc -> TazChat.sent());
		ctx.runOnClient(mc -> {
			mc.getConnection().sendCommand("help"); // another mod saying something just now
			TazChat.party("one");
			TazChat.party("two");
		});
		check("party: waits a moment after something else was sent", ctx.computeOnClient(mc -> TazChat.sent()) == sent && ctx.computeOnClient(mc -> TazChat.waiting()) == 2);
		ctx.waitTicks(20);
		check("party: then the first goes out, the second waits for it to show", ctx.computeOnClient(mc -> TazChat.sent()) == sent + 1 && ctx.computeOnClient(mc -> TazChat.waiting()) == 2);
		tell("Party > [MVP++] " + me + ": [TAZ] one");
		ctx.waitTicks(20);
		check("party: Hypixel showed the first back, so the second goes", ctx.computeOnClient(mc -> TazChat.sent()) == sent + 2 && ctx.computeOnClient(mc -> TazChat.waiting()) == 1);
		ctx.waitTicks(60);
		check("party: the second never showed back, so it went once more", ctx.computeOnClient(mc -> TazChat.sent()) == sent + 3);
		ctx.waitTicks(60);
		check("party: then Taz gives up on it", ctx.computeOnClient(mc -> TazChat.sent()) == sent + 3 && ctx.computeOnClient(mc -> TazChat.waiting()) == 0);
	}

	/** The joke is one of the ones that fit this death, with Taz's prefix and the death count. */
	private boolean fits(String joke, boolean self, String name, String cause, String killer, String phase, String dungeonClass, int count) {
		if (joke == null || !joke.startsWith("[TAZ] ")) return false;
		String text = joke.substring(6);
		String suffix = count == 2 ? " (death #2)" : count > 2 ? " (death #" + count + ", it's a hobby now)" : "";
		if (!text.endsWith(suffix)) return false;
		String body = text.substring(0, text.length() - suffix.length());
		List<String> pool = ctx.computeOnClient(mc -> SkillIssue.jokesFor(self, cause, killer, phase, dungeonClass));
		return pool.stream().anyMatch(t -> t.replace("{name}", name).replace("{killer}", killer == null ? "mob" : killer)
				.replace("{boss}", "the boss").equals(body));
	}

	private String phase() {
		return ctx.computeOnClient(mc -> SkillIssue.phase());
	}

	private String lastJoke() {
		return ctx.computeOnClient(mc -> SkillIssue.lastJoke());
	}

	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	/** The test route as Odin writes it: positions relative to the clay corner, turned to face north. */
	private static String odinJson() {
		return "{\"Test Room\":["
				+ wp(18, FLOOR, 8, "#FF5555FF", null, 1) + ","
				+ wp(15, FLOOR, 11, "#AA00AAFF", "1", 1) + ","
				+ wp(11, FLOOR + 1, 18, "#55FF55FF", "2", 0.875) + ","
				+ wp(21, FLOOR + 1, 18, "#FFF255FF", "just tp", 1) + ","
				+ wp(6, FLOOR, 26, "#AA00AAFF", "3 bat", 1) + ","
				+ wp(21, FLOOR, 24, "#AA00AAFF", "4 | done", 0.5) + ","
				+ wp(26, FLOOR + 1, 21, "#55A7CEFF", "prince", 1) + ","
				+ wp(15, FLOOR + 2, 15, "#AA00AA00", "Routes by test", 1) + ","
				+ wp(16, FLOOR + 1, 28, "#AA00AAFF", "5 mine", 1) + ","
				+ wp(24, FLOOR + 1, 29, "#55FF55FF", "6", 0.375) + ","
				+ wp(26, FLOOR + 1, 6, "#AA00AAFF", "line+tp", 1) + "]}";
	}

	/** A waypoint; its box is a full block, a chest (0.875), a lever (0.375) or a slab (height 0.5). */
	private static String wp(int x, int y, int z, String color, String title, double size) {
		double min = size < 1 && size != 0.5 ? (1 - size) / 2 : 0, max = size < 1 && size != 0.5 ? 1 - min : 1, maxY = size;
		return "{\"blockPos\":{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "},\"color\":\"" + color + "\",\"filled\":false,\"depth\":false,"
				+ "\"aabb\":{\"minX\":" + min + ",\"minY\":0.0,\"minZ\":" + min + ",\"maxX\":" + max + ",\"maxY\":" + maxY + ",\"maxZ\":" + max + "}"
				+ (title == null ? "" : ",\"title\":\"" + title + "\"") + "}";
	}

	/** Odin's export string: Base64 of the gzipped JSON. */
	private static String odinExport() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
				out.write(odinJson().getBytes(StandardCharsets.UTF_8));
			}
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	// ---- helpers

	private static int[] t(int x, int z) {
		return new int[]{x, z};
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-9;
	}

	private int step() {
		return ctx.computeOnClient(mc -> SecretTrainer.step());
	}

	private boolean cleared(BlockPos pos) {
		return ctx.computeOnClient(mc -> SecretTrainer.cleared(pos));
	}

	private boolean started() {
		return ctx.computeOnClient(mc -> SecretTrainer.started());
	}

	private void restart() {
		ctx.getInput().holdKeyFor(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5, 2);
		ctx.waitTicks(3);
	}

	private void goTo(BlockPos p) {
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + p.getZ());
		ctx.waitTicks(4);
	}

	private void actionBar(String text) {
		cmd("title @p actionbar " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private List<String> hudLines() {
		return ctx.computeOnClient(mc -> {
			for (MovableHud h : TazHud.all()) {
				if (h instanceof SecretTrainer t) return ((TextHud) t).shownLines(false).stream().map(net.minecraft.ChatFormatting::stripFormatting).toList();
			}
			throw new AssertionError("SecretTrainer not registered");
		});
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("dungeons: " + what);
		System.out.println("[DungeonGameTest] ok: " + what);
	}
}
