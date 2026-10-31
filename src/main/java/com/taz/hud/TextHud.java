package com.taz.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Movable text lines (with § colour codes, shadowed, no background box): the simplest Taz HUD.
 * Subclasses give the lines and where the position is saved.
 */
public abstract class TextHud implements MovableHud {
	private static final int PADDING = 3;

	private final String name;
	private final double defaultX;
	private final double defaultY;

	protected TextHud(String name, double defaultX, double defaultY) {
		this.name = name;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	/** Where this HUD is saved. */
	protected abstract HudPos pos();

	/** The lines to show now; with {@code preview}, sample lines when there is nothing live. Empty = nothing. */
	protected abstract List<String> lines(boolean preview);

	/** The lines as they'd show now (for tests). */
	public final List<String> shownLines(boolean preview) {
		return lines(preview);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean hidden() {
		return pos().hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		pos().hidden = hidden;
	}

	@Override
	public float scale() {
		return pos().scale;
	}

	@Override
	public void setScale(float scale) {
		pos().scale = clampScale(scale);
	}

	@Override
	public void reset() {
		pos().x = defaultX;
		pos().y = defaultY;
		pos().scale = 1.0f;
	}

	@Override
	public int[] bounds(Font font, int screenW, int screenH, boolean preview) {
		List<String> lines = lines(preview);
		if (lines.isEmpty()) return null;
		return box(font, lines, screenW, screenH);
	}

	/** The index of the line under the mouse (screen coordinates), or -1. */
	public final int lineAt(Font font, int screenW, int screenH, double mouseX, double mouseY) {
		List<String> lines = lines(false);
		if (lines.isEmpty()) return -1;
		int[] b = box(font, lines, screenW, screenH);
		if (mouseX < b[0] || mouseX >= b[0] + b[2] || mouseY < b[1] || mouseY >= b[1] + b[3]) return -1;
		double y = (mouseY - b[1]) / scaleNow() - PADDING;
		int i = y < 0 ? -1 : (int) (y / (font.lineHeight + 1));
		return i < lines.size() ? i : -1;
	}

	@Override
	public void moveTo(int x, int y, int screenW, int screenH) {
		pos().x = screenW > 0 ? x / (double) screenW : 0;
		pos().y = screenH > 0 ? y / (double) screenH : 0;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Font font, boolean preview) {
		List<String> lines = lines(preview);
		if (lines.isEmpty()) return;
		int[] b = box(font, lines, g.guiWidth(), g.guiHeight());
		float scale = scaleNow();
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		g.pose().scale(scale, scale);
		int y = PADDING;
		for (String line : lines) {
			TazTheme.line(g, font, line, PADDING, y);
			y += font.lineHeight + 1;
		}
		g.pose().popMatrix();
	}

	/** Unscaled size including padding. */
	private static int[] size(Font font, List<String> lines) {
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		return new int[]{w + PADDING * 2, lines.size() * (font.lineHeight + 1) - 1 + PADDING * 2};
	}

	/** Screen box at the saved spot, pushed back inside the screen if needed. */
	private int[] box(Font font, List<String> lines, int screenW, int screenH) {
		int[] size = size(font, lines);
		float scale = scaleNow();
		int w = Math.round(size[0] * scale), h = Math.round(size[1] * scale);
		int x = (int) Math.round(pos().x * screenW), y = (int) Math.round(pos().y * screenH);
		x = Math.max(0, Math.min(x, screenW - w));
		y = Math.max(0, Math.min(y, screenH - h));
		return new int[]{x, y, w, h};
	}

	/** The scale it's drawn at now: the saved one, unless a HUD shrinks itself to fit somewhere. */
	protected float scaleNow() {
		return clampScale(pos().scale);
	}

	public static float clampScale(float s) {
		return Math.max(0.5f, Math.min(3f, s));
	}
}
