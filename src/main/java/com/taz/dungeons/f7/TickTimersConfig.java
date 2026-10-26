package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Tick Timers in the Taz settings GUI (/taz). */
public final class TickTimersConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Server-tick timers for the Floor 7 boss: Maxor's start, Storm's pads and purple pad, Goldor's death ticks.")
	@ConfigEditorBoolean
	@SearchTag("tick timer maxor storm pad py goldor death ticks f7 m7")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Maxor start", desc = "Time until Maxor's fight starts.")
	@ConfigEditorBoolean
	public boolean maxor = true;

	@Expose
	@ConfigOption(name = "Storm pads", desc = "Storm's pad timer (every second).")
	@ConfigEditorBoolean
	public boolean pad = true;

	@Expose
	@ConfigOption(name = "Storm purple pad", desc = "Time until Storm's purple pad.")
	@ConfigEditorBoolean
	public boolean py = true;

	@Expose
	@ConfigOption(name = "Goldor death ticks", desc = "Goldor's death ticks (every 3 seconds) until the core opens.")
	@ConfigEditorBoolean
	public boolean goldor = true;

	@Expose
	@ConfigOption(name = "In ticks", desc = "Show ticks instead of seconds.")
	@ConfigEditorBoolean
	public boolean inTicks = false;

	@Expose
	@ConfigOption(name = "Name", desc = "Show which timer it is (\"Goldor 2.35s\").")
	@ConfigEditorBoolean
	public boolean prefix = true;

	@Expose
	@ConfigOption(name = "Unit", desc = "Show the s / t after the number.")
	@ConfigEditorBoolean
	public boolean suffix = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.47, 0.55);

	public static TickTimersConfig get() {
		return Taz.config().floor7.tickTimers;
	}
}
