package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Island Guide in the Taz settings GUI (/taz). */
public final class IslandGuideConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A beam and a pointer to any NPC or spot on Moonglade Marsh and Torrhus Canyon you pick below.")
	@ConfigEditorBoolean
	@SearchTag("guide npc location waypoint agatha swoop rosemary kysha miria helia honey ant dory safari torrhus springs")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Show the way to", desc = "Where to go.")
	@ConfigEditorDropdown
	public IslandGuide.Place destination = IslandGuide.Place.NONE;

	@Expose
	@ConfigOption(name = "Stop when there", desc = "Clear the destination when you reach it.")
	@ConfigEditorBoolean
	public boolean clearWhenReached = true;

	@Expose
	@ConfigOption(name = "Colour", desc = "")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0xFF, 0xD7, 0x40, 255);

	public static IslandGuideConfig get() {
		return Taz.config().galatea.guide;
	}

	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
