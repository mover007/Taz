package com.taz.dungeons.puzzles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Water Board: which levers to flip and when, from Odin's solutions for each board and set of gates. */
final class WaterBoard {
	private enum Lever {
		COAL(20, 61, 10), GOLD(20, 61, 15), QUARTZ(20, 61, 20), DIAMOND(10, 61, 20), EMERALD(10, 61, 15), CLAY(10, 61, 10), WATER(15, 60, 5);

		final BlockPos rel;
		int flips;

		Lever(int x, int y, int z) {
			rel = new BlockPos(x, y, z);
		}

		static Lever of(String key) {
			return switch (key) {
				case "diamond_block" -> DIAMOND;
				case "emerald_block" -> EMERALD;
				case "hardened_clay" -> CLAY;
				case "quartz_block" -> QUARTZ;
				case "gold_block" -> GOLD;
				case "coal_block" -> COAL;
				case "water" -> WATER;
				default -> null;
			};
		}
	}

	/** The coloured gates; three of them are out on each board. */
	private static final BlockPos[] WOOL = {new BlockPos(15, 56, 19), new BlockPos(15, 56, 18), new BlockPos(15, 56, 17), new BlockPos(15, 56, 16),
			new BlockPos(15, 56, 15)};
	private static final JsonObject SOLUTIONS;
	private static final Map<Lever, double[]> PLAN = new EnumMap<>(Lever.class);
	private static int board = -1;
	private static int openedAt = -1;

	static {
		JsonElement json = Puzzles.json("water-solutions.json");
		SOLUTIONS = json == null ? new JsonObject() : json.getAsJsonObject();
	}

	private WaterBoard() {
	}

	/** Every client tick in the room: read the board once the gates are out. */
	static void tick() {
		DungeonRooms.Room room = Puzzles.room("Water Board");
		if (!Puzzles.cfg().waterBoard || room == null || board != -1) return;
		Minecraft mc = Minecraft.getInstance();
		StringBuilder gates = new StringBuilder();
		for (int i = 0; i < WOOL.length; i++) if (!mc.level.getBlockState(room.toReal(WOOL[i])).isAir()) gates.append(i);
		if (gates.length() != 3) return;
		if (mc.level.getBlockState(room.toReal(new BlockPos(14, 77, 27))).is(Blocks.TERRACOTTA)) board = 0;
		else if (mc.level.getBlockState(room.toReal(new BlockPos(16, 78, 27))).is(Blocks.EMERALD_BLOCK)) board = 1;
		else if (mc.level.getBlockState(room.toReal(new BlockPos(14, 78, 27))).is(Blocks.DIAMOND_BLOCK)) board = 2;
		else if (mc.level.getBlockState(room.toReal(new BlockPos(14, 78, 27))).is(Blocks.QUARTZ_BLOCK)) board = 3;
		else {
			board = -2;
			TazChat.info("Water Board: couldn't read the board. Was it already started?");
			return;
		}
		PLAN.clear();
		JsonObject byGates = SOLUTIONS.getAsJsonObject(String.valueOf(Puzzles.cfg().waterOptimized)).getAsJsonObject(String.valueOf(board));
		if (byGates == null || !byGates.has(gates.toString())) return;
		for (Map.Entry<String, JsonElement> e : byGates.getAsJsonObject(gates.toString()).entrySet()) {
			Lever lever = Lever.of(e.getKey());
			if (lever == null) continue;
			var arr = e.getValue().getAsJsonArray();
			double[] times = new double[arr.size()];
			for (int i = 0; i < times.length; i++) times[i] = arr.get(i).getAsDouble();
			PLAN.put(lever, times);
		}
	}

	/** A lever flipped (by anyone): that click is done. */
	static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		DungeonRooms.Room room = Puzzles.room("Water Board");
		if (room == null || PLAN.isEmpty() || !(old.getBlock() instanceof LeverBlock) || !(now.getBlock() instanceof LeverBlock)) return;
		if (old.getValue(LeverBlock.POWERED) == now.getValue(LeverBlock.POWERED)) return;
		for (Lever l : Lever.values()) {
			if (!room.toReal(l.rel).equals(pos)) continue;
			if (l == Lever.WATER && openedAt == -1) openedAt = Puzzles.serverTicks;
			l.flips++;
		}
	}

	private record Step(Lever lever, double time) {
	}

	/** The clicks left, in order: the "now" ones first (by lever), then by time. */
	private static List<Step> steps() {
		List<Step> out = new ArrayList<>();
		PLAN.forEach((lever, times) -> {
			for (int i = lever.flips; i < times.length; i++) out.add(new Step(lever, times[i]));
		});
		out.sort(Comparator.comparing((Step s) -> s.time() != 0).thenComparingInt(s -> s.time() == 0 ? s.lever().ordinal() : Integer.MAX_VALUE)
				.thenComparingDouble(Step::time));
		return out;
	}

	/** Test hooks. */
	static int board() {
		return board;
	}

	static int clicksLeft() {
		return steps().size();
	}

	static void reset() {
		for (Lever l : Lever.values()) l.flips = 0;
		board = -1;
		PLAN.clear();
		openedAt = -1;
	}

	static void render(WorldRenderer.Batch batch) {
		DungeonRooms.Room room = Puzzles.room("Water Board");
		if (room == null || board < 0 || PLAN.isEmpty()) return;
		List<Step> steps = steps();
		if (!steps.isEmpty() && Puzzles.cfg().waterTracer) {
			Vec3 first = Vec3.atCenterOf(room.toReal(steps.get(0).lever().rel));
			batch.line(WorldRenderer.crosshair(batch.camera), first, Puzzles.FIRST, 2f, true);
			if (steps.size() > 1 && steps.get(1).lever() != steps.get(0).lever()) {
				batch.line(first, Vec3.atCenterOf(room.toReal(steps.get(1).lever().rel)), Puzzles.SECOND, 2f, true);
			}
		}
		PLAN.forEach((lever, times) -> {
			Vec3 at = Vec3.atLowerCornerOf(room.toReal(lever.rel)).add(0.5, 1.5, 0.5);
			for (int i = lever.flips, row = 0; i < times.length; i++, row++) {
				int ticks = (int) (times[i] * 20);
				int left = openedAt == -1 ? ticks : openedAt + ticks - Puzzles.serverTicks;
				boolean now = openedAt == -1 ? ticks == 0 : left <= 0;
				String text = now ? "CLICK ME!" : String.format("%.1fs", (openedAt == -1 ? times[i] : left / 20.0));
				batch.label(at, text, now ? Puzzles.FIRST : 0xFFFFFFFF, true, row);
			}
		});
	}
}
