package com.taz.dungeons.puzzles;

import com.google.gson.JsonElement;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creeper Beams: pairs of sea lanterns to line up with the creeper, each pair in its own colour (reds, whites and greys),
 * from Odin's list of pairs.
 */
final class Beams {
	private static final int[] COLORS = {0xFFFF2E2E, 0xFFFFFFFF, 0xFFB3121B, 0xFFFFB0B0, 0xFF6E5A5A, 0xFFFF6B6B, 0xFF1A1A1A, 0xFFA89393};
	private static final List<int[]> PAIRS = new ArrayList<>();
	private static final Map<BlockPos, BlockPos> CURRENT = new LinkedHashMap<>();
	private static final Map<BlockPos, Integer> COLOR = new LinkedHashMap<>();

	static {
		JsonElement json = Puzzles.json("creeper-beams-solutions.json");
		if (json != null) {
			for (JsonElement e : json.getAsJsonArray()) {
				var a = e.getAsJsonArray();
				int[] p = new int[6];
				for (int i = 0; i < 6; i++) p[i] = a.get(i).getAsInt();
				PAIRS.add(p);
			}
		}
	}

	private Beams() {
	}

	static void onRoomEnter(DungeonRooms.Room room) {
		if (!room.data.name().equals("Creeper Beams")) {
			reset();
			return;
		}
		recalc(room);
	}

	private static void recalc(DungeonRooms.Room room) {
		Minecraft mc = Minecraft.getInstance();
		CURRENT.clear();
		COLOR.clear();
		for (int i = 0; i < PAIRS.size(); i++) {
			int[] p = PAIRS.get(i);
			BlockPos a = room.toReal(new BlockPos(p[0], p[1], p[2])), b = room.toReal(new BlockPos(p[3], p[4], p[5]));
			if (!mc.level.getBlockState(a).is(Blocks.SEA_LANTERN) || !mc.level.getBlockState(b).is(Blocks.SEA_LANTERN)) continue;
			CURRENT.put(a, b);
			COLOR.put(a, COLORS[i % COLORS.length]);
		}
	}

	static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		DungeonRooms.Room room = Puzzles.room("Creeper Beams");
		if (room == null) return;
		if (pos.equals(room.toReal(new BlockPos(15, 69, 15))) && old.isAir() && now.is(Blocks.CHEST)) Puzzles.solved("Creeper Beams");
		if ((old.is(Blocks.PRISMARINE) || old.is(Blocks.SEA_LANTERN)) && (now.is(Blocks.PRISMARINE) || now.is(Blocks.SEA_LANTERN))) recalc(room);
	}

	/** Test hook. */
	static int pairs() {
		return CURRENT.size();
	}

	static void reset() {
		CURRENT.clear();
		COLOR.clear();
	}

	static void render(WorldRenderer.Batch batch) {
		if (CURRENT.isEmpty() || Puzzles.room("Creeper Beams") == null) return;
		CURRENT.forEach((a, b) -> {
			int c = COLOR.get(a);
			int fill = (c & 0x00FFFFFF) | 0x90000000;
			batch.filled(new AABB(a), fill, false);
			batch.filled(new AABB(b), fill, false);
			batch.outline(new AABB(a), c, 2f, false);
			batch.outline(new AABB(b), c, 2f, false);
			if (Puzzles.cfg().beamsTracer) batch.line(Puzzles.center(a), Puzzles.center(b), c, 2f, true);
		});
	}
}
