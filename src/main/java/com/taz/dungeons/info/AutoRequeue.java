package com.taz.dungeons.info;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.dungeons.util.DungeonParty;
import com.taz.general.ChatCommands;
import com.taz.util.Party;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;

/**
 * Auto Requeue: when a dungeon run ends, queue the same floor again a moment later (if you lead the party, it's full
 * enough and nobody asked for downtime with !dt). From NoammAddons.
 */
public final class AutoRequeue {
	private static final String[] FLOORS = {"ENTRANCE", "FLOOR_ONE", "FLOOR_TWO", "FLOOR_THREE", "FLOOR_FOUR", "FLOOR_FIVE", "FLOOR_SIX", "FLOOR_SEVEN"};
	private static long at;
	private static String command;

	private AutoRequeue() {
	}

	/** The floor's /joininstance name ("MASTER_CATACOMBS_FLOOR_SEVEN"), or null outside a dungeon. */
	public static String instance() {
		if (DungeonLocation.floor() == null) return null;
		return (DungeonLocation.masterMode() ? "MASTER_" : "") + "CATACOMBS_" + FLOORS[DungeonLocation.floorNumber()];
	}

	public static void onChat(String line) {
		AutoRequeueConfig cfg = AutoRequeueConfig.get();
		if (!cfg.enabled || !line.trim().equals("> EXTRA STATS <") || !DungeonLocation.inDungeon()) return;
		if (cfg.checkParty) {
			if (!Party.maybeLeader()) {
				say("You aren't the party leader.");
				return;
			}
			int size = DungeonParty.size();
			if (size > 0 && size < cfg.minPartySize) {
				say("Not enough players (" + size + "/" + cfg.minPartySize + ").");
				return;
			}
		}
		if (ChatCommands.downtime()) {
			say("Someone needs downtime.");
			return;
		}
		String name = instance();
		if (name == null) return;
		command = cfg.instanceRequeue ? "instancerequeue" : "joininstance " + name;
		at = System.currentTimeMillis() + cfg.delaySeconds * 1000L;
		say("Requeueing in " + cfg.delaySeconds + "s.");
	}

	private static void say(String s) {
		if (AutoRequeueConfig.get().feedback) TazChat.info("Auto Requeue: " + s);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (command == null || System.currentTimeMillis() < at) return;
		String c = command;
		command = null;
		if (!AutoRequeueConfig.get().enabled || mc.getConnection() == null || ChatCommands.downtime()) return;
		mc.getConnection().sendCommand(c);
	}

	/** Test hook: the command waiting to be sent. */
	public static String pending() {
		return command;
	}
}
