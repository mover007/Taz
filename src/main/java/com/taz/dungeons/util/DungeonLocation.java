package com.taz.dungeons.util;

import com.taz.foraging.GalateaRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where you are in a Catacombs run: in a dungeon or not and on which floor (the sidebar's "The Catacombs (M7)"), in the
 * boss room (each floor's boss room is a fixed box, from NoammAddons), and on Floor 7 which phase (by height) and which
 * Phase 3 section you're in.
 */
public final class DungeonLocation {
	/** Tests: the flat test world counts as a dungeon. */
	public static final boolean ANYWHERE = Boolean.getBoolean("taz.dungeons.anywhere");
	private static final Pattern FLOOR = Pattern.compile("The Catacombs \\((E|F[1-7]|M[1-7])\\)");

	private static final AABB[] BOSS_ROOMS = {
			box(-14, 55, 49, -72, 146, -40),
			box(-40, 99, -40, 24, 54, 59),
			box(-40, 118, -40, 42, 64, 37),
			box(-40, 112, -40, 50, 53, 47),
			box(-40, 112, -8, 50, 53, 118),
			box(-40, 51, -8, 22, 110, 134),
			box(-8, 0, -8, 134, 254, 147)};
	private static final AABB[] P3_SECTIONS = {
			box(90, 158, 123, 111, 105, 32),
			box(16, 158, 122, 111, 105, 143),
			box(19, 158, 48, -3, 106, 142),
			box(91, 158, 50, -3, 106, 30)};

	private static boolean inDungeon;
	/** "M7", "F3", "E"; null outside a dungeon. */
	private static String floor;
	private static boolean inBoss;
	private static int f7Phase, p3Section;
	private static ClientLevel level;
	private static int ticks;
	/** Tests: a floor to pretend to be on. */
	private static String testFloor;

	private DungeonLocation() {
	}

	private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return new AABB(x1, y1, z1, x2, y2, z2);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			inDungeon = false;
			floor = null;
		}
		if (mc.level == null || mc.player == null) {
			inBoss = false;
			f7Phase = p3Section = 0;
			return;
		}
		if (++ticks % 20 == 1) {
			String found = null;
			for (String line : GalateaRecorder.sidebarLines()) {
				Matcher m = FLOOR.matcher(line);
				if (m.find()) found = m.group(1);
			}
			if (testFloor != null) found = testFloor;
			inDungeon = ANYWHERE || found != null;
			floor = found;
		}
		int n = floorNumber();
		double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
		inBoss = inDungeon && n >= 1 && BOSS_ROOMS[n - 1].contains(x, y, z);
		f7Phase = n == 7 && inBoss ? y > 210 ? 1 : y > 155 ? 2 : y > 100 ? 3 : y > 45 ? 4 : 5 : 0;
		p3Section = 0;
		if (f7Phase == 3) {
			for (int i = 0; i < P3_SECTIONS.length; i++) {
				if (P3_SECTIONS[i].contains(x, y, z)) {
					p3Section = i + 1;
					break;
				}
			}
		}
	}

	public static boolean inDungeon() {
		return inDungeon;
	}

	/** "M7", "F3", "E", or null. */
	public static String floor() {
		return floor;
	}

	/** 1-7, 0 for the Entrance or outside. */
	public static int floorNumber() {
		return floor == null || floor.equals("E") ? 0 : floor.charAt(1) - '0';
	}

	public static boolean masterMode() {
		return floor != null && floor.startsWith("M");
	}

	public static boolean inBoss() {
		return inBoss;
	}

	/** Floor 7 boss phase 1-5 (Maxor, Storm, Goldor, Necron, the dragons), 0 elsewhere. */
	public static int f7Phase() {
		return f7Phase;
	}

	/** Floor 7 Phase 3 section 1-4, 0 elsewhere. */
	public static int p3Section() {
		return p3Section;
	}

	/** The Phase 3 section (1-4) a position is in on Floor 7, 0 when it isn't in one. */
	public static int p3SectionAt(double x, double y, double z) {
		if (floorNumber() != 7 || y <= 100 || y > 155) return 0;
		for (int i = 0; i < P3_SECTIONS.length; i++) if (P3_SECTIONS[i].contains(x, y, z)) return i + 1;
		return 0;
	}

	/** Tests: pretend to be on this floor ("M7"), or null to read the sidebar again. */
	public static void setTestFloor(String floor) {
		testFloor = floor;
		ticks = 0;
	}
}
