package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Mage Beam in the Taz settings GUI (/taz). */
public final class MageBeamConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Draws the Mage's beam as one solid red line instead of firework sparks.")
	@ConfigEditorBoolean
	@SearchTag("mage beam particles firework line")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Hide sparks", desc = "Hide the beam's firework sparks.")
	@ConfigEditorBoolean
	public boolean hideParticles = true;

	@Expose
	@ConfigOption(name = "Duration", desc = "How long the line stays, in ticks (20 a second).")
	@ConfigEditorSlider(minValue = 5, maxValue = 100, minStep = 1)
	public float ticks = 40;

	@Expose
	@ConfigOption(name = "Min points", desc = "Sparks needed in a line before it counts as a beam.")
	@ConfigEditorSlider(minValue = 2, maxValue = 10, minStep = 1)
	public int minPoints = 3;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Draw the line through walls.")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	public static MageBeamConfig get() {
		return Taz.config().dungeons.mageBeam;
	}
}
