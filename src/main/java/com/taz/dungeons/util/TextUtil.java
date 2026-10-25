package com.taz.dungeons.util;

import net.minecraft.ChatFormatting;

public final class TextUtil {
	private static final java.util.regex.Pattern SECTION_PAIR = java.util.regex.Pattern.compile("§.?");

	private TextUtil() {
	}

	/** Strips formatting codes from a chat line. Unicode symbols (☠, ✦, ...) are kept. */
	public static String cleanChat(String raw) {
		if (raw == null) return "";
		String stripped = ChatFormatting.stripFormatting(raw);
		if (stripped == null) return "";
		// Hypixel also splits words with invalid codes like "The Catac§x§combs", which
		// stripFormatting leaves alone. Drop every section sign and the character after it.
		return SECTION_PAIR.matcher(stripped).replaceAll("").trim();
	}

	/**
	 * Cleans a sidebar or tab-list line. Hypixel puts emoji between a team's prefix and
	 * suffix so every line is unique, so this keeps printable ASCII only. Every value
	 * we parse from the scoreboard is plain ASCII.
	 */
	public static String cleanScoreboard(String raw) {
		String stripped = cleanChat(raw);
		StringBuilder sb = new StringBuilder(stripped.length());
		boolean lastSpace = false;
		for (int i = 0; i < stripped.length(); i++) {
			char c = stripped.charAt(i);
			if (c < 0x20 || c > 0x7E) continue;
			if (c == ' ') {
				if (lastSpace) continue;
				lastSpace = true;
			} else {
				lastSpace = false;
			}
			sb.append(c);
		}
		return sb.toString().trim();
	}

	/** 312500 -> "5:12" */
	public static String formatTime(long ms) {
		if (ms < 0) ms = 0;
		long totalSec = ms / 1000;
		return (totalSec / 60) + ":" + pad2(totalSec % 60);
	}

	/** 312500 -> "5:12.5" */
	public static String formatTimeTenths(long ms) {
		if (ms < 0) ms = 0;
		long tenths = ms / 100;
		long totalSec = tenths / 10;
		return (totalSec / 60) + ":" + pad2(totalSec % 60) + "." + (tenths % 10);
	}

	/** 3200 -> "+3.2s", -75000 -> "-1:15" */
	public static String formatDelta(long ms) {
		String sign = ms < 0 ? "-" : "+";
		long abs = Math.abs(ms);
		if (abs < 60_000) {
			return sign + (abs / 1000) + "." + ((abs / 100) % 10) + "s";
		}
		return sign + formatTime(abs);
	}

	private static String pad2(long v) {
		return v < 10 ? "0" + v : Long.toString(v);
	}
}
