package com.taz.dungeons.hud;

import com.taz.dungeons.config.ModConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Draws one panel at its configured position. Shared by the in-game HUD and the editor. */
public final class PanelRenderer {
	public static final int PADDING = 3;
	private static final int MIN_WIDTH = 80;

	/** Screen-space bounds of a drawn panel, including padding and scale. */
	public record Bounds(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private PanelRenderer() {
	}

	/** Unscaled content size. */
	public static int[] measure(Font font, List<HudLine> lines) {
		int width = MIN_WIDTH;
		for (HudLine l : lines) {
			int w = font.width(l.left()) + (l.right() == null ? 0 : 8 + font.width(l.right()));
			width = Math.max(width, w);
		}
		int height = Math.max(font.lineHeight, lines.size() * (font.lineHeight + 1) - 1);
		return new int[]{width, height};
	}

	/** Where the panel ends up on screen: the configured spot, pushed back inside the screen if needed. */
	public static Bounds bounds(Font font, List<HudLine> lines, ModConfig.PanelConfig pc, int screenW, int screenH) {
		int[] size = measure(font, lines);
		float scale = clampScale(pc.scale);
		int w = Math.round((size[0] + PADDING * 2) * scale);
		int h = Math.round((size[1] + PADDING * 2) * scale);
		int x = (int) Math.round(pc.x * screenW);
		int y = (int) Math.round(pc.y * screenH);
		x = Math.max(0, Math.min(x, screenW - w));
		y = Math.max(0, Math.min(y, screenH - h));
		return new Bounds(x, y, w, h);
	}

	public static Bounds draw(GuiGraphicsExtractor g, Font font, List<HudLine> lines, ModConfig.PanelConfig pc,
							  int screenW, int screenH, float alpha) {
		Bounds b = bounds(font, lines, pc, screenW, screenH);
		int[] size = measure(font, lines);
		float scale = clampScale(pc.scale);

		g.pose().pushMatrix();
		g.pose().translate(b.x(), b.y());
		g.pose().scale(scale, scale);
		g.pose().translate(PADDING, PADDING);

		int y = 0;
		int lineH = font.lineHeight + 1;
		for (HudLine l : lines) {
			g.text(font, l.left(), 0, y, withAlpha(l.leftColor(), alpha), true);
			if (l.right() != null) {
				g.text(font, l.right(), size[0] - font.width(l.right()), y, withAlpha(l.rightColor(), alpha), true);
			}
			y += lineH;
		}
		g.pose().popMatrix();
		return b;
	}

	public static float clampScale(float s) {
		return Math.max(0.5f, Math.min(3f, s));
	}

	private static int withAlpha(int argb, float alpha) {
		if (alpha >= 1f) return argb;
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
