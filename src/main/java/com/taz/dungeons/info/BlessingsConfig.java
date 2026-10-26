package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Blessings in the Taz settings GUI (/taz). */
public final class BlessingsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Shows the dungeon's blessings and their levels (from the tab list).")
	@ConfigEditorBoolean
	@SearchTag("blessing power time stone life wisdom")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Power", desc = "Blessing of Power.")
	@ConfigEditorBoolean
	public boolean power = true;

	@Expose
	@ConfigOption(name = "Time", desc = "Blessing of Time.")
	@ConfigEditorBoolean
	public boolean time = true;

	@Expose
	@ConfigOption(name = "Stone", desc = "Blessing of Stone.")
	@ConfigEditorBoolean
	public boolean stone = false;

	@Expose
	@ConfigOption(name = "Life", desc = "Blessing of Life.")
	@ConfigEditorBoolean
	public boolean life = false;

	@Expose
	@ConfigOption(name = "Wisdom", desc = "Blessing of Wisdom.")
	@ConfigEditorBoolean
	public boolean wisdom = false;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.01, 0.40);

	public static BlessingsConfig get() {
		return Taz.config().dungeons.blessings;
	}
}
