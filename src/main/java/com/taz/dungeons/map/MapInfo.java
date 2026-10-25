package com.taz.dungeons.map;

import com.taz.Taz;
import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Map Info: a small score HUD (Hypixel's live score), the secrets found in the room you're in, and a red box on the Blood
 * room's portal once the run reaches 300. From Odin's Map Info.
 */
public final class MapInfo {
	private static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final DungeonState STATE = new DungeonState();
	private static ClientLevel level;
	private static int score = -1, found = -1, total = -1, ticks;

	private MapInfo() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			score = found = total = -1;
		}
		if (!MapInfoConfig.get().enabled || !DungeonLocation.inDungeon() || ++ticks % 10 != 0) return;
		STATE.update(mc);
		if (STATE.liveScore >= 0) score = STATE.liveScore;
	}

	public static void onActionBar(String text) {
		Matcher m = SECRETS.matcher(text);
		if (m.find()) {
			found = Integer.parseInt(m.group(1));
			total = Integer.parseInt(m.group(2));
		} else if (DungeonRooms.current() == null) {
			found = total = -1;
		}
	}

	/** Test hooks. */
	public static void testScore(int s) {
		score = s;
	}

	private static boolean shown() {
		MapInfoConfig cfg = MapInfoConfig.get();
		return cfg.enabled && DungeonLocation.inDungeon() && !(cfg.hideInBoss && DungeonLocation.inBoss());
	}

	/** LevelRenderEvents.END_MAIN: the Blood room's portal at 300 score. */
	public static void render(LevelRenderContext context) {
		MapInfoConfig cfg = MapInfoConfig.get();
		if (!cfg.enabled || !cfg.portal || score < 300 || !DungeonLocation.inDungeon() || DungeonLocation.inBoss()) return;
		for (DungeonRooms.Room room : DungeonRooms.all()) {
			if (!"BLOOD".equals(room.data.type()) || !room.resolved()) continue;
			AABB box = AABB.encapsulatingFullBlocks(room.toReal(new BlockPos(16, 69, 29)), room.toReal(new BlockPos(14, 69, 29))).inflate(0, 4, 0);
			try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
				batch.filled(box, 0x50FF2E2E, false);
				batch.outline(box, 0xFFFF2E2E, 2f, false);
			} catch (Exception e) {
				Taz.LOGGER.error("Map info portal failed to draw", e);
			}
		}
	}

	/** The score HUD. */
	public static final class Score extends TextHud {
		public Score() {
			super("Score", 0.005, 0.72);
		}

		@Override
		protected HudPos pos() {
			return MapInfoConfig.get().scoreHud;
		}

		@Override
		public boolean enabled() {
			return MapInfoConfig.get().enabled && MapInfoConfig.get().score;
		}

		@Override
		protected List<String> lines(boolean preview) {
			int s = preview ? 302 : score;
			if (!preview && (!shown() || s < 0)) return List.of();
			String color = s >= 300 ? "§c" : s >= 270 ? "§f" : "§7";
			return List.of("§cScore " + color + s);
		}
	}

	/** The room secrets HUD. */
	public static final class RoomSecrets extends TextHud {
		public RoomSecrets() {
			super("Room Secrets", 0.48, 0.55);
		}

		@Override
		protected HudPos pos() {
			return MapInfoConfig.get().roomHud;
		}

		@Override
		public boolean enabled() {
			return MapInfoConfig.get().enabled && MapInfoConfig.get().roomSecrets;
		}

		@Override
		protected List<String> lines(boolean preview) {
			int f = preview ? 2 : found, t = preview ? 5 : total;
			if (!preview && (!shown() || t <= 0 || DungeonLocation.inBoss())) return List.of();
			String color = f >= t ? "§c" : f * 2 >= t ? "§f" : "§7";
			return List.of(color + f + "§7/" + color + t);
		}
	}
}
