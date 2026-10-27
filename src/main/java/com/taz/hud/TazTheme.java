package com.taz.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Taz's look: red and black. */
public final class TazTheme {
	public static final int RED = 0xFFFF2E2E;
	public static final int DARK_RED = 0xFFB3121B;
	public static final int LIGHT_RED = 0xFFFF6B6B;
	public static final int PALE_RED = 0xFFFFB0B0;
	public static final int TEXT = 0xFFF2E9E9;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GREY = 0xFFA89393;
	public static final int DARK_GREY = 0xFF6E5A5A;
	public static final int BLACK = 0xFF000000;

	private TazTheme() {
	}

	/** The theme colour for a § code. */
	public static int color(char code) {
		return switch (Character.toLowerCase(code)) {
			case '0' -> BLACK;
			case '4' -> DARK_RED;
			case 'c' -> RED;
			case '2', 'a' -> WHITE;
			case '1', '3', '9', 'b', '6' -> LIGHT_RED;
			case '5', 'd', 'e' -> PALE_RED;
			case '7' -> GREY;
			case '8' -> DARK_GREY;
			default -> TEXT;
		};
	}

	/** A HUD line with § codes, in the theme's colours. */
	public static MutableComponent styled(String line) {
		MutableComponent out = Component.empty();
		int color = TEXT;
		boolean bold = false;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '§' && i + 1 < line.length()) {
				append(out, run, color, bold);
				char code = Character.toLowerCase(line.charAt(++i));
				if (code == 'l') {
					bold = true;
				} else if (code == 'r') {
					color = TEXT;
					bold = false;
				} else if ("0123456789abcdef".indexOf(code) >= 0) {
					color = color(code);
					bold = false;
				}
				continue;
			}
			run.append(c);
		}
		append(out, run, color, bold);
		return out;
	}

	private static void append(MutableComponent out, StringBuilder run, int color, boolean bold) {
		if (run.isEmpty()) return;
		int c = bold ? RED : color;
		out.append(Component.literal(run.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(c & 0xFFFFFF)).withBold(bold)));
		run.setLength(0);
	}

	/** Text with a one-pixel black outline (no shadow, no box). */
	public static void outlined(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		outlined(g, font, text, x, y, color, BLACK);
	}

	/** Text with a one-pixel outline in this colour (black, or black faded out). */
	public static void outlined(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, int outline) {
		Component black = blackCopy(text);
		g.text(font, black, x - 1, y, outline, false);
		g.text(font, black, x + 1, y, outline, false);
		g.text(font, black, x, y - 1, outline, false);
		g.text(font, black, x, y + 1, outline, false);
		g.text(font, text, x, y, color, false);
	}

	/** A HUD line with § codes, themed and outlined. */
	public static void line(GuiGraphicsExtractor g, Font font, String line, int x, int y) {
		outlined(g, font, styled(line), x, y, TEXT);
	}

	/** The same text with every colour black (keeping bold, so the outline lines up). */
	private static Component blackCopy(Component text) {
		MutableComponent out = Component.empty();
		text.visit((style, s) -> {
			out.append(Component.literal(s).withStyle(style.withColor(TextColor.fromRgb(0))));
			return java.util.Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** "[TAZ] " for chat: red in dark red brackets. */
	public static MutableComponent chatPrefix() {
		return Component.empty()
				.append(Component.literal("[").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(DARK_RED & 0xFFFFFF))))
				.append(Component.literal("TAZ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(RED & 0xFFFFFF)).withBold(true)))
				.append(Component.literal("] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(DARK_RED & 0xFFFFFF))));
	}
}
