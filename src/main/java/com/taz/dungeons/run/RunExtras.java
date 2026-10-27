package com.taz.dungeons.run;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.regex.Pattern;

/**
 * Extra lines for the splits panel, from NoammAddons' Run Splits: how many wither doors were opened, when the run reached
 * 300 score, and how much time the server's lag cost (real time minus server-tick time).
 */
public final class RunExtras {
	private static final Pattern WITHER_DOOR = Pattern.compile("^(?:\\[[^]]*?] )?\\w{1,16} opened a WITHER door!$");
	private static ClientLevel level;
	private static int witherDoors;
	private static long score300Ms = -1;
	private static final com.taz.dungeons.dungeon.DungeonState STATE = new com.taz.dungeons.dungeon.DungeonState();
	private static int ticks;

	private RunExtras() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			witherDoors = 0;
			score300Ms = -1;
		}
		if (score300Ms >= 0 || !com.taz.dungeons.util.DungeonLocation.inDungeon() || ++ticks % 10 != 0) return;
		STATE.update(mc);
		if (STATE.liveScore >= 300 && STATE.timeElapsedSec >= 0) score300Ms = STATE.timeElapsedSec * 1000L;
	}

	public static void onChat(String line) {
		if (WITHER_DOOR.matcher(line).matches()) witherDoors++;
	}

	public static int witherDoors() {
		return witherDoors;
	}

	/** Ms into the run when it reached 300 score, or -1. */
	public static long score300Ms() {
		return score300Ms;
	}

	/** Ms the server's lag added to this run. */
	public static long lagLostMs(RunTracker t) {
		return Math.max(0, t.elapsedMs() - t.gameElapsedMs());
	}
}
