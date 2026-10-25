package com.taz.dungeons.hud;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.ModConfig;
import com.taz.hud.MovableHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** One Run Predictor panel (Timer, Score, Splits, Session) as a movable Taz HUD. */
public final class PanelHud implements MovableHud {
	private final HudPanel panel;

	public PanelHud(HudPanel panel) {
		this.panel = panel;
	}

	private List<HudLine> lines(boolean preview) {
		List<HudLine> lines = PanelContent.lines(panel, DungeonPredictorClient.TRACKER,
				DungeonPredictorClient.currentPrediction(), DungeonPredictorClient.currentScore());
		return lines.isEmpty() && preview ? PanelContent.preview(panel) : lines;
	}

	@Override
	public String name() {
		return "Dungeons: " + panel.displayName;
	}

	@Override
	public boolean enabled() {
		return ModConfig.get().enabled;
	}

	@Override
	public boolean hidden() {
		return !ModConfig.get().panelVisible(panel);
	}

	@Override
	public void setHidden(boolean hidden) {
		ModConfig.get().setPanelVisible(panel, !hidden);
	}

	@Override
	public float scale() {
		return PanelRenderer.clampScale(ModConfig.get().panel(panel).scale);
	}

	@Override
	public void setScale(float scale) {
		ModConfig.get().panel(panel).scale = PanelRenderer.clampScale(scale);
	}

	@Override
	public void reset() {
		ModConfig.PanelConfig pc = ModConfig.get().panel(panel);
		pc.x = panel.defaultX;
		pc.y = panel.defaultY;
		pc.scale = 1.0f;
	}

	@Override
	public int[] bounds(Font font, int screenW, int screenH, boolean preview) {
		List<HudLine> lines = lines(preview);
		if (lines.isEmpty()) return null;
		PanelRenderer.Bounds b = PanelRenderer.bounds(font, lines, ModConfig.get().panel(panel), screenW, screenH);
		return new int[]{b.x(), b.y(), b.w(), b.h()};
	}

	@Override
	public void moveTo(int x, int y, int screenW, int screenH) {
		ModConfig.PanelConfig pc = ModConfig.get().panel(panel);
		pc.x = x / (double) screenW;
		pc.y = y / (double) screenH;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Font font, boolean preview) {
		List<HudLine> lines = lines(preview);
		if (lines.isEmpty()) return;
		PanelRenderer.draw(g, font, lines, ModConfig.get().panel(panel), g.guiWidth(), g.guiHeight(), 1f);
	}
}
