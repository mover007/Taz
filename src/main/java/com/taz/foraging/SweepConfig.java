package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Foraging > Sweep Details in the Taz settings GUI (/taz). */
public final class SweepConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Turns Hypixel's \"Sweep Details\" chat block into a small HUD (sweep, tree toughness, logs, penalties) "
			+ "and warns you when you cut a tree in the wrong order.")
	@ConfigEditorBoolean
	@SearchTag("sweep details toughness logs wrong style order penalty axe throw")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Hide the chat lines", desc = "Don't show the Sweep Details lines in chat (the HUD has them).")
	@ConfigEditorBoolean
	public boolean hideChat = true;

	@Expose
	@ConfigOption(name = "Wrong order alert", desc = "A title with the right order when you lose logs for cutting in the wrong order "
			+ "(Fig: trunk first; Mangrove: branches, trunk, roots; Helix: beige before red).")
	@ConfigEditorBoolean
	public boolean wrongStyleAlert = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.52, 0.64);

	public static SweepConfig get() {
		return Taz.config().foraging.sweep;
	}
}
