package com.taz.dungeons.map;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeon Map > Map Info in the Taz settings GUI (/taz). */
public final class MapInfoConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A small score HUD, the secrets found in the room you're in, and a box on the Blood room's portal at 300 score.")
	@ConfigEditorBoolean
	@SearchTag("map info score secrets room secrets portal 300")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Score", desc = "Hypixel's live score.")
	@ConfigEditorBoolean
	public boolean score = true;

	@Expose
	@ConfigOption(name = "Room secrets", desc = "Secrets found in the room you're in (\"2/5\").")
	@ConfigEditorBoolean
	public boolean roomSecrets = true;

	@Expose
	@ConfigOption(name = "Portal at 300", desc = "A red box on the Blood room's portal once the run reaches 300 score.")
	@ConfigEditorBoolean
	public boolean portal = true;

	@Expose
	@ConfigOption(name = "Hide in boss", desc = "")
	@ConfigEditorBoolean
	public boolean hideInBoss = true;

	@ConfigOption(name = "Move HUDs", desc = "Drag them to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	public HudPos scoreHud = new HudPos(0.005, 0.72);

	@Expose
	public HudPos roomHud = new HudPos(0.48, 0.55);

	public static MapInfoConfig get() {
		return Taz.config().dungeonMap.mapInfo;
	}
}
