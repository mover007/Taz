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
	private static final Pattern MEMBER = Pattern.compile("([A-Za-z0-9_]{3,16}) ?\\((Archer|Berserk|Healer|Mage|Tank)\\b(?: ([IVXLCDM]+)\\b)?");
	private static final Pattern PARTY = Pattern.compile("^Party \\((\\d+)\\)");
	private static final Map<String, String> CLASSES = new HashMap<>();
	private static final Map<String, Integer> LEVELS = new HashMap<>();
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
			LEVELS.clear();
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
				if (m.group(3) != null) LEVELS.put(m.group(1).toLowerCase(), roman(m.group(3)));
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

	/** Their class level (from "Mage L"), or 0 when Taz hasn't seen it. */
	public static int levelOf(String name) {
		return LEVELS.getOrDefault(name.toLowerCase(), 0);
	}

	/** How many in the party play this class. */
	public static int count(String dungeonClass) {
		return (int) CLASSES.values().stream().filter(dungeonClass::equals).count();
	}

	/** Your ability cooldown after the Mage's reduction (25%, plus half your Mage level %, doubled as the only Mage). */
	public static double cooldown(double baseSeconds) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return baseSeconds;
		String me = mc.player.getName().getString();
		if (!"Mage".equals(classOf(me))) return baseSeconds;
		double cut = 0.25 + Math.floor(levelOf(me) / 2.0) / 100 * (count("Mage") == 1 ? 2 : 1);
		return baseSeconds * (1 - cut);
	}

	private static int roman(String r) {
		int total = 0, prev = 0;
		for (int i = r.length() - 1; i >= 0; i--) {
			int v = switch (r.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				default -> 1000;
			};
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
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
