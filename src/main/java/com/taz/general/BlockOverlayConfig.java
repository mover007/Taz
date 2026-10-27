package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Visuals > Block Overlay in the Taz settings GUI (/taz). */
public final class BlockOverlayConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "The block you look at gets a red box instead of the thin black outline.")
	@ConfigEditorBoolean
	@SearchTag("block overlay outline selection highlight")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Fill", desc = "Fill the box.")
	@ConfigEditorBoolean
	public boolean fill = true;

	@Expose
	@ConfigOption(name = "Outline", desc = "Outline the box.")
	@ConfigEditorBoolean
	public boolean outline = true;

	@Expose
	@ConfigOption(name = "Line width", desc = "")
	@ConfigEditorSlider(minValue = 1, maxValue = 10, minStep = 0.5f)
	public float lineWidth = 2;

	@Expose
	@ConfigOption(name = "Through walls", desc = "")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	@Expose
	@ConfigOption(name = "Hide while Etherwarping", desc = "No box while you sneak with an Etherwarp item.")
	@ConfigEditorBoolean
	public boolean hideWithEtherwarp = false;

	public static BlockOverlayConfig get() {
		return Taz.config().visuals.blockOverlay;
	}
}
