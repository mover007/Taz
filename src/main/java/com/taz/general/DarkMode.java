package com.taz.general;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Dark Mode: a black tint over the world (and the HUD too, if you like). From NoammAddons' Dark Mode (GuiMixin draws it). */
public final class DarkMode {
	private DarkMode() {
	}

	/** Before the HUD draws (over the world only), or after it (over the HUD too). */
	public static void draw(GuiGraphicsExtractor g, boolean afterHud) {
		DarkModeConfig cfg = DarkModeConfig.get();
		if (!cfg.enabled || cfg.tintHud != afterHud) return;
		int alpha = Math.round(cfg.opacity / 100f * 255) << 24;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha);
	}
}
