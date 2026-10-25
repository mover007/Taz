package com.taz.dungeons.puzzles;

import com.google.gson.JsonElement;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Boulder: which button to press next to push the boulders out of the way, from Odin's solutions for each layout. */
final class Boulder {
	private record Click(BlockPos box, BlockPos button) {
	}

	private static final Map<String, List<int[]>> SOLUTIONS = new HashMap<>();
	private static final List<Click> CLICKS = new ArrayList<>();

	static {
		JsonElement json = Puzzles.json("boulder-solutions.json");
		if (json != null) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
				List<int[]> list = new ArrayList<>();
				for (JsonElement s : e.getValue().getAsJsonArray()) {
					var a = s.getAsJsonArray();
					list.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()});
				}
				SOLUTIONS.put(e.getKey(), list);
			}
		}
	}

	private Boulder() {
	}

	static void onRoomEnter(DungeonRooms.Room room) {
		if (!room.data.name().equals("Boulder")) return;
		Minecraft mc = Minecraft.getInstance();
		StringBuilder layout = new StringBuilder();
		for (int z = 24; z >= 9; z -= 3) {
			for (int x = 24; x >= 6; x -= 3) layout.append(mc.level.getBlockState(room.toReal(new BlockPos(x, 66, z))).isAir() ? '0' : '1');
		}
		List<int[]> sol = SOLUTIONS.get(layout.toString());
		CLICKS.clear();
		if (sol == null) return;
		for (int[] s : sol) CLICKS.add(new Click(room.toReal(new BlockPos(s[0], 65, s[1])), room.toReal(new BlockPos(s[2], 65, s[3]))));
	}

	static void onClick(BlockPos pos) {
		CLICKS.stream().filter(c -> c.button().equals(pos)).findFirst().ifPresent(CLICKS::remove);
	}

	/** Test hook. */
	static int clicksLeft() {
		return CLICKS.size();
	}

	static void reset() {
		CLICKS.clear();
	}

	static void render(WorldRenderer.Batch batch) {
		if (CLICKS.isEmpty() || Puzzles.room("Boulder") == null) return;
		List<Click> shown = Puzzles.cfg().boulderAll ? CLICKS : CLICKS.subList(0, 1);
		for (int i = 0; i < shown.size(); i++) batch.outline(new AABB(shown.get(i).box()), i == 0 ? Puzzles.FIRST : Puzzles.SECOND, 2.5f, false);
	}
}
