package com.taz.dungeons.hud;

import com.taz.hud.TazTheme;
/** One HUD row: left-aligned label and optional right-aligned value. */
public record HudLine(String left, int leftColor, String right, int rightColor) {
	public static final int WHITE = TazTheme.TEXT;
	public static final int GRAY = TazTheme.GREY;
	public static final int DARK_GRAY = TazTheme.DARK_GREY;
	public static final int GOLD = TazTheme.RED;
	public static final int AQUA = TazTheme.LIGHT_RED;
	public static final int GREEN = TazTheme.WHITE;
	public static final int RED = TazTheme.DARK_RED;
	public static final int YELLOW = TazTheme.PALE_RED;

	public static HudLine of(String text, int color) {
		return new HudLine(text, color, null, 0);
	}

	public static HudLine kv(String label, String value, int valueColor) {
		return new HudLine(label, GRAY, value, valueColor);
	}
}
