package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Honeyhives in the Taz settings GUI (/taz). */
public final class HoneyhiveConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Highlight the Torrhus Canyon honeyhives you can loot right now. Only real hives count: "
			+ "cosmetic ones have no \"Honeyhive\" hologram and are ignored.")
	@ConfigEditorBoolean
	@SearchTag("honeyhive beehive bee hive torrhus honey")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Next hive pointer", desc = "A line from your crosshair and a \"Next hive: 23m\" label to the closest hive you can loot.")
	@ConfigEditorBoolean
	public boolean pointer = true;

	@Expose
	@ConfigOption(name = "Refill timers", desc = "Show when each hive you've looted refills, even when you're too far away to see its hologram. "
			+ "Remembered per profile, also after you leave.")
	@ConfigEditorBoolean
	public boolean timers = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Show the highlight over terrain, so hives behind rocks show too.")
	@ConfigEditorBoolean
	public boolean throughWalls = true;

	@Expose
	@ConfigOption(name = "Colour", desc = "Highlight colour of hives you can loot.")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0xFF, 0xC4, 0x1E, 255);

	@ConfigOption(name = "Hive status", desc = "Print every hive Taz has seen and when it refills.")
	@ConfigEditorButton(buttonText = "Show")
	public transient Runnable status = HoneyhiveTracker::printStatus;

	public static HoneyhiveConfig get() {
		return Taz.config().galatea.honeyhives;
	}

	/** ARGB highlight colour. */
	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
