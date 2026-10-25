package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Terminal Solver in the Taz settings GUI (/taz). */
public final class TerminalSolverConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "In the terminals, only the slots to click show, in red (numbers and click counts on them). Your clicks stay your own.")
	@ConfigEditorBoolean
	@SearchTag("terminal solver red green colors starts with rubix numbers melody goldor p3")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Red-Green", desc = "Correct all the panes!")
	@ConfigEditorBoolean
	public boolean redGreen = true;

	@Expose
	@ConfigOption(name = "Colors", desc = "Select all the ... items!")
	@ConfigEditorBoolean
	public boolean colors = true;

	@Expose
	@ConfigOption(name = "Start-With", desc = "What starts with: '...'?")
	@ConfigEditorBoolean
	public boolean startsWith = true;

	@Expose
	@ConfigOption(name = "Rubix", desc = "Change all to same color! (red: left clicks, grey: right clicks)")
	@ConfigEditorBoolean
	public boolean rubix = true;

	@Expose
	@ConfigOption(name = "Rubix: left clicks only", desc = "Only left clicks, even when right clicks would be fewer.")
	@ConfigEditorBoolean
	public boolean rubixLeftOnly = false;

	@Expose
	@ConfigOption(name = "Numbers", desc = "Click in order! (the next three in red, pale and dark red)")
	@ConfigEditorBoolean
	public boolean numbers = true;

	@Expose
	@ConfigOption(name = "Numbers: show all", desc = "Show the number on every pane left, not just the next three.")
	@ConfigEditorBoolean
	public boolean showNumbers = false;

	@Expose
	@ConfigOption(name = "Melody", desc = "Click the button on time! (the button turns red when the line matches the column)")
	@ConfigEditorBoolean
	public boolean melody = true;

	@Expose
	@ConfigOption(name = "Terminal sounds", desc = "Play a pling on each click (instead of Hypixel's).")
	@ConfigEditorBoolean
	public boolean sounds = true;

	@Expose
	@ConfigOption(name = "Sound pitch", desc = "The click sound's pitch.")
	@ConfigEditorSlider(minValue = 0.5f, maxValue = 2f, minStep = 0.05f)
	public float pitch = 1.5f;

	public static TerminalSolverConfig get() {
		return Taz.config().floor7.terminalSolver;
	}
}
