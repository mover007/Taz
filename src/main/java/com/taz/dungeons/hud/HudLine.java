package com.taz.dungeons.hud;

/** One HUD row: left-aligned label and optional right-aligned value. Colors are ARGB. */
public record HudLine(String left, int leftColor, String right, int rightColor) {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK_GRAY = 0xFF777777;
	public static final int GOLD = 0xFFFFAA00;
	public static final int AQUA = 0xFF55FFFF;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int YELLOW = 0xFFFFFF55;

	public static HudLine of(String text, int color) {
		return new HudLine(text, color, null, 0);
	}

	public static HudLine kv(String label, String value, int valueColor) {
		return new HudLine(label, GRAY, value, valueColor);
	}
}
