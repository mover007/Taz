package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Leap Counter in the Taz settings GUI (/taz). */
public final class LeapCounterConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "At a Phase 3 leap spot (simon says, early enters, core, relics), how many teammates have leaped to you.")
	@ConfigEditorBoolean
	@SearchTag("leap counter spirit leap p3 ee2 ee3 core")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Alert", desc = "\"Everyone Leaped!\" and a sound when all of them are there.")
	@ConfigEditorBoolean
	public boolean alert = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.45, 0.62);

	public static LeapCounterConfig get() {
		return Taz.config().floor7.leapCounter;
	}
}
