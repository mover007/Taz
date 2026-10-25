package com.taz.dungeons.info;

import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Score Alerts: a title (and party chat, if you like) when the run reaches 270 and 300 score, from Hypixel's own live
 * score on the sidebar ("Cleared: 77% (164)"); and party chat when the Mimic (a baby zombie on Floors 6 and 7), the
 * Prince or a bonus Bat dies. From Odin's Mimic and Map Info and NoammAddons' Score Calculator.
 */
public final class ScoreAlerts {
	private static final DungeonState STATE = new DungeonState();
	private static int lastScore = -1;
	private static boolean mimic, prince;
	private static ClientLevel level;
	private static int ticks;
	private static String lastMessage;

	private ScoreAlerts() {
	}

	/** Every client tick: read the live score now and then. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			lastScore = -1;
			mimic = prince = false;
		}
		ScoreAlertsConfig cfg = ScoreAlertsConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon() || ++ticks % 10 != 0) return;
		STATE.update(mc);
		onScore(STATE.liveScore, STATE.timeElapsedSec);
	}

	/** The run's score now (Hypixel's): crossing 270 or 300 says so once. */
	static void onScore(int score, int elapsedSec) {
		if (score < 0) return;
		ScoreAlertsConfig cfg = ScoreAlertsConfig.get();
		if (lastScore >= 0) {
			if (lastScore < 270 && score >= 270) reached(270, cfg.title270, cfg.party270, elapsedSec);
			if (lastScore < 300 && score >= 300) reached(300, cfg.title300, cfg.party300, elapsedSec);
		}
		lastScore = score;
	}

	private static void reached(int score, boolean title, boolean party, int elapsedSec) {
		String time = elapsedSec >= 0 ? (elapsedSec / 60) + "m " + (elapsedSec % 60) + "s" : "?";
		if (title) Alert.show((score == 300 ? "§c" : "§e") + score + " Score!", null, Alert.Sound.ORB);
		if (party) TazChat.party(score + " Score!");
		lastMessage = score + " score reached in " + time + (DungeonLocation.floor() != null ? " (" + DungeonLocation.floor() + ")" : "");
		TazChat.info(lastMessage);
	}

	/** An entity event: 3 is a death; a baby zombie dying in clear on Floor 6 or 7 is the Mimic. */
	public static void onEntityEvent(Entity entity, byte id) {
		ScoreAlertsConfig cfg = ScoreAlertsConfig.get();
		if (!cfg.enabled || mimic || id != 3 || !(entity instanceof Zombie zombie) || !zombie.isBaby()) return;
		int floor = DungeonLocation.floorNumber();
		if (!DungeonLocation.inDungeon() || DungeonLocation.inBoss() || (floor != 6 && floor != 7 && !DungeonLocation.ANYWHERE)) return;
		mimic = true;
		if (cfg.mimic) TazChat.party("Mimic Killed!");
	}

	public static void onChat(String line) {
		ScoreAlertsConfig cfg = ScoreAlertsConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon()) return;
		if (!prince && line.equals("A Prince falls. +1 Bonus Score")) {
			prince = true;
			if (cfg.prince) TazChat.party("Prince Killed!");
		} else if (line.equals("A Bat has been slain. +1 Bonus Score") && cfg.bat) {
			TazChat.party("Bat Killed!");
		}
	}

	/** Test hooks. */
	public static boolean mimicKilled() {
		return mimic;
	}

	public static String lastMessage() {
		return lastMessage;
	}

	public static void testScore(int score) {
		onScore(score, 312);
	}
}
