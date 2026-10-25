package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Sea Lumies & Lushlilac in the Taz settings GUI (/taz). */
public final class MoongladeResourcesConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Boxes round Sea Lumies (in Murkwater Shallows and Depths, they give Foraging XP) "
			+ "and Lushlilac (on the Reaches and Verdant Summit) on Moonglade Marsh.")
	@ConfigEditorBoolean
	@SearchTag("sea lumies lumie pickle lushlilac flower moonglade highlight")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Sea Lumies", desc = "Highlight Sea Lumies.")
	@ConfigEditorBoolean
	public boolean seaLumies = true;

	@Expose
	@ConfigOption(name = "Sea Lumies: at least", desc = "Only clumps of at least this many (a full clump of 4 gives the most).")
	@ConfigEditorSlider(minValue = 1, maxValue = 4, minStep = 1)
	public int minLumies = 3;

	@Expose
	@ConfigOption(name = "Lushlilac", desc = "Highlight Lushlilac.")
	@ConfigEditorBoolean
	public boolean lushlilac = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Show the boxes through blocks and water.")
	@ConfigEditorBoolean
	public boolean throughWalls = true;

	@Expose
	@ConfigOption(name = "Range", desc = "Only highlight ones this close (blocks).")
	@ConfigEditorSlider(minValue = 16, maxValue = 160, minStep = 8)
	public int range = 64;

	@Expose
	@ConfigOption(name = "Pointer", desc = "A line to the closest one.")
	@ConfigEditorBoolean
	public boolean pointer = false;

	@Expose
	@ConfigOption(name = "Sea Lumies colour", desc = "")
	@ConfigEditorColour
	public ChromaColour lumiesColour = ChromaColour.Companion.fromStaticRGB(0x40, 0xF0, 0xE0, 255);

	@Expose
	@ConfigOption(name = "Lushlilac colour", desc = "")
	@ConfigEditorColour
	public ChromaColour lushlilacColour = ChromaColour.Companion.fromStaticRGB(0xE0, 0x60, 0xFF, 255);

	public static MoongladeResourcesConfig get() {
		return Taz.config().galatea.resources;
	}
}
