package com.taz.slayer;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Slayer > Slayer Helper in the Taz settings GUI (/taz). */
public final class SlayerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "An alert when your slayer boss spawns, a box around it (also through walls), and how fast you killed it.")
	@ConfigEditorBoolean
	@SearchTag("slayer boss spawn alert highlight kill time revenant tarantula sven voidgloom inferno vampire")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Boss spawned alert", desc = "A title and sound when your boss spawns.")
	@ConfigEditorBoolean
	public boolean spawnAlert = true;

	@Expose
	@ConfigOption(name = "Highlight your boss", desc = "A box around your own boss (not other players').")
	@ConfigEditorBoolean
	public boolean highlight = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Show the box through blocks.")
	@ConfigEditorBoolean
	public boolean throughWalls = true;

	@Expose
	@ConfigOption(name = "Colour", desc = "Colour of the box.")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0xFF, 0x40, 0x40, 255);

	@Expose
	@ConfigOption(name = "Kill times", desc = "After each boss: \"Boss killed in 0:42 (best 0:38, average 0:45, 12 this session)\".")
	@ConfigEditorBoolean
	public boolean killTimes = true;

	public static SlayerConfig get() {
		return Taz.config().slayer.helper;
	}

	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
