package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Skill Tracker in the Taz settings GUI (/taz). */
public final class SkillTrackerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Skill XP per hour and time to your next level, from the \"+12.5 Foraging\" action bar messages. "
			+ "The clock only runs while you're getting XP.")
	@ConfigEditorBoolean
	@SearchTag("skill xp per hour tracker level up farming mining combat foraging fishing")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Show every skill", desc = "Every skill trained since the last reset, not just the latest one.")
	@ConfigEditorBoolean
	public boolean allSkills = false;

	@Expose
	@ConfigOption(name = "Pause after (seconds)", desc = "The clock stops when you haven't got skill XP for this long.")
	@ConfigEditorSlider(minValue = 3, maxValue = 60, minStep = 1)
	public int pauseSeconds = 10;

	@ConfigOption(name = "Reset", desc = "Start counting again from zero.")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable reset = SkillTracker::resetCounts;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.50, 0.04);

	public static SkillTrackerConfig get() {
		return Taz.config().general.skillTracker;
	}
}
