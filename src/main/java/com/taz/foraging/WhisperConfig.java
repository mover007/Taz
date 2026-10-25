package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Whisper Counter in the Taz settings GUI (/taz). */
public final class WhisperConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A small HUD with how many Forest and Desert Whispers you get per hour, counted only while you forage.")
	@ConfigEditorBoolean
	@SearchTag("whispers forest desert rate per hour foraging")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Forest Whispers", desc = "Show the Forest Whispers line.")
	@ConfigEditorBoolean
	public boolean forest = true;

	@Expose
	@ConfigOption(name = "Desert Whispers", desc = "Show the Desert Whispers line.")
	@ConfigEditorBoolean
	public boolean desert = true;

	@Expose
	@ConfigOption(name = "Pause after (seconds)", desc = "The clock stops when you haven't foraged for this long, so the rate only counts time spent foraging.")
	@ConfigEditorSlider(minValue = 1, maxValue = 30, minStep = 1)
	public int pauseSeconds = 3;

	@ConfigOption(name = "Reset counter", desc = "Start counting again from zero.")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable reset = WhisperTracker::reset;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(WhisperHud.DEFAULT_X, WhisperHud.DEFAULT_Y);

	public static WhisperConfig get() {
		return Taz.config().galatea.whispers;
	}
}
