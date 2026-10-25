package com.taz.dungeons.util;

import com.taz.foraging.GalateaRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your dungeon party from the tab list: each player's class ("mTaz (Mage L)"), remembered after they die (the tab list
 * then says DEAD), and whether you're on your own.
 */
public final class DungeonParty {
	private static final Pattern MEMBER = Pattern.compile("([A-Za-z0-9_]{3,16}) ?\\((Archer|Berserk|Healer|Mage|Tank)\\b");
	private static final Pattern PARTY = Pattern.compile("^Party \\((\\d+)\\)");
	private static final Map<String, String> CLASSES = new HashMap<>();
	private static int size = -1;
	private static ClientLevel level;
	private static int ticks;

	private DungeonParty() {
	}

	/** Every client tick: read the tab list now and then (a new dungeon starts afresh). */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			CLASSES.clear();
			size = -1;
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || ++ticks % 40 != 0) return;
		for (PlayerInfo info : connection.getListedOnlinePlayers()) {
			Component display = info.getTabListDisplayName();
			if (display == null) continue;
			String line = TextUtil.cleanScoreboard(TextUtil.cleanChat(display.getString()));
			Matcher m = MEMBER.matcher(line);
			if (m.find()) {
				CLASSES.put(m.group(1).toLowerCase(), m.group(2));
				continue;
			}
			m = PARTY.matcher(line);
			if (m.find()) size = Integer.parseInt(m.group(1));
		}
	}

	/** "Healer", "Archer"... or null when Taz hasn't seen it. */
	public static String classOf(String name) {
		return CLASSES.get(name.toLowerCase());
	}

	/** Tests: a class as the tab list would show it. */
	public static void setClass(String name, String dungeonClass) {
		CLASSES.put(name.toLowerCase(), dungeonClass);
	}

	/** On your own (the sidebar says "Solo", or the tab list "Party (1)"): nobody to tell in party chat. */
	public static boolean solo() {
		return size == 1 || GalateaRecorder.sidebarLines().stream().anyMatch(l -> l.trim().equals("Solo"));
	}
}
