package com.taz.dungeons.puzzles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quiz: the right answer's pad in red with a beam over it, from Odin's answer list (and the SkyBlock year worked out), plus
 * a HUD counting down until you must answer.
 */
public final class Quiz extends TextHud {
	private static final String ORUO = "[STATUE] Oruo the Omniscient: ";
	private static final int START_TICKS = 220, QUESTION_TICKS = 100;
	private static final Map<String, List<String>> ANSWERS = new HashMap<>();
	private static final BlockPos[] OPTIONS = {new BlockPos(20, 70, 6), new BlockPos(15, 70, 9), new BlockPos(10, 70, 6)};
	private static final BlockPos[] PADS = new BlockPos[3];
	private static final boolean[] CORRECT = new boolean[3];
	private static List<String> answers;
	private static int ticks, maxTicks, stage;

	static {
		JsonElement json = Puzzles.json("quiz-answers.json");
		if (json != null) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
				List<String> list = new ArrayList<>();
				e.getValue().getAsJsonArray().forEach(a -> list.add(a.getAsString()));
				ANSWERS.put(e.getKey(), list);
			}
		}
	}

	public Quiz() {
		super("Quiz Timer", 0.45, 0.30);
	}

	static void onServerTick() {
		if (ticks > 0) ticks--;
	}

	static void onChat(String msg) {
		if (!Puzzles.cfg().quiz) return;
		switch (msg) {
			case ORUO + "I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know." -> start(START_TICKS, 1);
			case ORUO + "2 questions left... Then you will have proven your worth to me!" -> start(QUESTION_TICKS, 2);
			case ORUO + "One more question!" -> start(QUESTION_TICKS, 3);
			default -> {
			}
		}
		if (msg.startsWith(ORUO) && msg.endsWith("correctly!")) {
			if (msg.contains("answered the final question")) {
				Puzzles.solved("Quiz");
				clear();
				return;
			}
			if (msg.contains("answered Question #")) java.util.Arrays.fill(CORRECT, false);
		}
		String t = msg.trim();
		if (answers != null && !t.isEmpty() && "ⓐⓑⓒ".indexOf(t.charAt(0)) >= 0 && answers.stream().anyMatch(msg::endsWith)) {
			CORRECT["ⓐⓑⓒ".indexOf(t.charAt(0))] = true;
		}
		if (t.equals("What SkyBlock year is it?")) {
			answers = List.of("Year " + ((System.currentTimeMillis() / 1000 - 1560276000) / 446400 + 1));
			return;
		}
		for (Map.Entry<String, List<String>> e : ANSWERS.entrySet()) {
			if (msg.contains(e.getKey())) {
				answers = e.getValue();
				return;
			}
		}
	}

	private static void start(int t, int s) {
		ticks = maxTicks = t;
		stage = s;
	}

	static void onRoomEnter(DungeonRooms.Room room) {
		if (!room.data.name().equals("Quiz")) return;
		for (int i = 0; i < 3; i++) PADS[i] = room.toReal(OPTIONS[i]);
	}

	/** Test hooks. */
	static boolean correct(int i) {
		return CORRECT[i];
	}

	static int ticksLeft() {
		return ticks;
	}

	static void clear() {
		java.util.Arrays.fill(CORRECT, false);
		answers = null;
		ticks = maxTicks = stage = 0;
	}

	static void render(WorldRenderer.Batch batch) {
		if (answers == null) return;
		for (int i = 0; i < 3; i++) {
			if (!CORRECT[i] || PADS[i] == null) continue;
			BlockPos p = PADS[i].below();
			batch.filled(new AABB(p), 0x80FF2E2E, false);
			batch.outline(new AABB(p), Puzzles.FIRST, 2.5f, false);
			batch.filled(new AABB(p.getX() + 0.35, p.getY() + 1, p.getZ() + 0.35, p.getX() + 0.65, p.getY() + 40, p.getZ() + 0.65), 0x60FF2E2E, false);
		}
	}

	@Override
	protected HudPos pos() {
		return PuzzlesConfig.get().quizHud;
	}

	@Override
	public boolean enabled() {
		PuzzlesConfig c = PuzzlesConfig.get();
		return c.enabled && c.quiz && c.quizTimer;
	}

	@Override
	protected List<String> lines(boolean preview) {
		int t = preview ? 160 : ticks, max = preview ? 220 : maxTicks, s = preview ? 1 : stage;
		if (t <= 0) return List.of();
		String color = t >= max * 0.66 ? "§f" : t >= max * 0.33 ? "§c" : "§4";
		return List.of("§cQuiz §7(" + s + "/3) " + color + String.format("%.2fs", t / 20.0));
	}
}
