package com.taz.foraging;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writes each new kind of sidebar, action bar and title line seen on Galatea to the game log
 * ("[Taz] Galatea sidebar: Contest: 1,234 points"), so features can be built from what Hypixel really shows (contest
 * points, the Torrhus Springs pressure meter). Chat is in the log already. Lines that only differ in their numbers
 * count once. About > "Log Galatea HUD text".
 */
public final class GalateaRecorder {
	/** Tests: record without the developer flag. */
	public static boolean testOn;

	private static final Pattern NUMBERS = Pattern.compile("\\d[\\d,.]*");
	private static final Pattern GAPS = Pattern.compile(" {3,}");
	private static final int MAX = 600;
	private static final Set<String> SEEN = new HashSet<>();
	private static int ticks;

	private GalateaRecorder() {
	}

	private static boolean on() {
		return (Taz.DEV || testOn) && Galatea.onIslands();
	}

	/** Every client tick: the sidebar, every 2 seconds. */
	public static void tick(Minecraft mc) {
		if (++ticks % 40 != 0 || !on()) return;
		for (String line : sidebar(false)) record("sidebar", line);
	}

	/** The sidebar's lines as plain ASCII text (Hypixel puts emoji in them to keep lines apart). */
	public static List<String> sidebarLines() {
		return sidebar(true);
	}

	private static List<String> sidebar(boolean ascii) {
		List<String> out = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		Scoreboard board = mc.level.getScoreboard();
		Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return out;
		for (PlayerScoreEntry entry : board.listPlayerScores(sidebar)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = board.getPlayersTeam(entry.owner());
			String raw = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			out.add(ascii ? TextUtil.cleanScoreboard(raw) : TextUtil.cleanChat(raw));
		}
		return out;
	}

	/** An action bar message (its parts are spaced apart). */
	public static void onActionBar(String text) {
		if (!on()) return;
		for (String part : GAPS.split(text)) record("action bar", part.trim());
	}

	public static void onTitle(String kind, String text) {
		if (!on() || isOwn(text)) return;
		record(kind, text.trim());
	}

	/** Taz's own alerts aren't Hypixel's. */
	private static boolean isOwn(String text) {
		String t = TextUtil.cleanChat(text);
		return com.taz.util.Alert.lastTitle != null && t.equals(TextUtil.cleanChat(com.taz.util.Alert.lastTitle))
				|| com.taz.util.Alert.lastSubtitle != null && t.equals(TextUtil.cleanChat(com.taz.util.Alert.lastSubtitle));
	}

	/** Test hook: a line of this shape was written to the log. */
	public static boolean recorded(String kind, String text) {
		return SEEN.contains(kind + "|" + NUMBERS.matcher(text).replaceAll("#"));
	}

	private static void record(String kind, String text) {
		if (text.isEmpty() || SEEN.size() >= MAX) return;
		if (SEEN.add(kind + "|" + NUMBERS.matcher(text).replaceAll("#"))) Taz.LOGGER.info("[Taz] Galatea {}: {}", kind, text);
	}
}
