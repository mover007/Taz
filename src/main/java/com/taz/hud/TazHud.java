package com.taz.hud;

import com.taz.Taz;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draws every Taz HUD in game. Features register their HUD once at startup; the HUD editor moves them all. */
public final class TazHud {
	private static final List<MovableHud> HUDS = new ArrayList<>();

	private TazHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(Taz.MOD_ID, "hud"), TazHud::render);
	}

	public static void register(MovableHud hud) {
		HUDS.add(hud);
	}

	/** In drawing order: later ones are on top. */
	public static List<MovableHud> all() {
		return Collections.unmodifiableList(HUDS);
	}

	/** Opens the editor on the next tick (a settings button or command runs while another screen is closing). */
	public static void openEditor() {
		Taz.openScreenNextTick(TazHudEditor::new);
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null || mc.screen instanceof TazHudEditor) return;
		for (MovableHud hud : HUDS) {
			if (!hud.enabled() || hud.hidden()) continue;
			try {
				hud.draw(g, mc.font, false);
			} catch (Exception e) {
				Taz.LOGGER.error("HUD {} failed to draw", hud.name(), e); // never break the game's HUD
			}
		}
	}
}
