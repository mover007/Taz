package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Fairy Souls in the Taz settings GUI (/taz). */
public final class FairySoulsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Waypoints for the 24 Fairy Souls on Moonglade Marsh and Torrhus Canyon you haven't found yet, "
			+ "with a hint for each and a pointer to the closest. Picking one up ticks it off (per profile).")
	@ConfigEditorBoolean
	@SearchTag("fairy soul souls waypoint galatea moonglade torrhus")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Pointer", desc = "A line to the closest Fairy Soul you haven't found.")
	@ConfigEditorBoolean
	public boolean pointer = true;

	@Expose
	@ConfigOption(name = "Hints", desc = "Where each one is hidden (\"Behind a waterfall\"), under its name.")
	@ConfigEditorBoolean
	public boolean hints = true;

	@Expose
	@ConfigOption(name = "Colour", desc = "")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0xFF, 0x88, 0xFF, 255);

	@ConfigOption(name = "Mark all found", desc = "If you found them before using Taz.")
	@ConfigEditorButton(buttonText = "All found")
	public transient Runnable allFound = FairySouls::markAllFound;

	@ConfigOption(name = "Forget found", desc = "Show every Fairy Soul again (click twice).")
	@ConfigEditorButton(buttonText = "Forget")
	public transient Runnable forget = () -> {
		if (TazChat.confirm("fairysouls", "forget which Galatea Fairy Souls you found")) FairySouls.forgetFound();
	};

	public static FairySoulsConfig get() {
		return Taz.config().galatea.fairySouls;
	}

	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
