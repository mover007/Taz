package com.taz.dungeons.puzzles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Ice Fill: the path over each of the three floors that covers every tile, from Odin's patterns. */
final class IceFill {
	private static final JsonObject FLOORS;
	private static final List<Vec3> PATH = new ArrayList<>();

	static {
		JsonElement json = Puzzles.json("ice-fill-floors.json");
		FLOORS = json == null ? new JsonObject() : json.getAsJsonObject();
	}

	private IceFill() {
	}

	private static BlockPos pos(JsonElement e) {
		JsonObject o = e.getAsJsonObject();
		return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
	}

	static void onRoomEnter(DungeonRooms.Room room) {
		if (!room.data.name().equals("Ice Fill") || !PATH.isEmpty() || !FLOORS.has("identifier")) return;
		Minecraft mc = Minecraft.getInstance();
		JsonArray patterns = FLOORS.getAsJsonArray(Puzzles.cfg().iceFillOptimized ? "hard" : "easy");
		for (int floor = 0; floor < 3; floor++) {
			JsonArray ids = FLOORS.getAsJsonArray("identifier").get(floor).getAsJsonArray();
			boolean found = false;
			for (int p = 0; p < ids.size(); p++) {
				JsonArray pair = ids.get(p).getAsJsonArray();
				if (mc.level.getBlockState(room.toReal(pos(pair.get(0)))).isAir() && !mc.level.getBlockState(room.toReal(pos(pair.get(1)))).isAir()) {
					for (JsonElement step : patterns.get(floor).getAsJsonArray().get(p).getAsJsonArray()) {
						PATH.add(Vec3.atLowerCornerOf(room.toReal(pos(step))).add(0.5, 0.1, 0.5));
					}
					found = true;
					break;
				}
			}
			if (!found) TazChat.info("Ice Fill: couldn't read floor " + (floor + 1));
		}
	}

	/** Test hook. */
	static int points() {
		return PATH.size();
	}

	static void reset() {
		PATH.clear();
	}

	static void render(WorldRenderer.Batch batch) {
		if (PATH.size() < 2 || Puzzles.room("Ice Fill") == null) return;
		for (int i = 1; i < PATH.size(); i++) batch.line(PATH.get(i - 1), PATH.get(i), Puzzles.FIRST, 3f, true);
	}
}
