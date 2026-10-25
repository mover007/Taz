package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Ruby Veilshroom Finder in the Taz settings GUI (/taz). */
public final class VeilshroomConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Finds every Ruby Veilshroom on Torrhus Canyon (they're in the caves at the bottom of Torrhus Springs), "
			+ "even through rock, points you to the closest, and times their respawn (50-55 seconds).")
	@ConfigEditorBoolean
	@SearchTag("ruby veilshroom velishroom mushroom torrhus springs cave finder respawn")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Highlight", desc = "A box round every Ruby Veilshroom.")
	@ConfigEditorBoolean
	public boolean highlight = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Show the boxes through rock, so you can see the caves from above.")
	@ConfigEditorBoolean
	public boolean throughWalls = true;

	@Expose
	@ConfigOption(name = "Pointer", desc = "A line from your crosshair and \"Next veilshroom: 23m\" to the closest one.")
	@ConfigEditorBoolean
	public boolean pointer = true;

	@Expose
	@ConfigOption(name = "Respawn timers", desc = "\"Respawns in ~37s\" where you picked one.")
	@ConfigEditorBoolean
	public boolean timers = true;

	@Expose
	@ConfigOption(name = "Show the way", desc = "Before you've found any: a beam at the top of Torrhus Springs, where you dive down to the caves.")
	@ConfigEditorBoolean
	public boolean guide = true;

	@Expose
	@ConfigOption(name = "HUD", desc = "How many are ready and respawning, and how many you've picked (per hour).")
	@ConfigEditorBoolean
	public boolean hudEnabled = true;

	@Expose
	@ConfigOption(name = "Colour", desc = "Box and pointer colour.")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0xFF, 0x2D, 0x6F, 255);

	@ConfigOption(name = "Reset counter", desc = "Start the picked count and rate from zero.")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable reset = RubyVeilshrooms::resetCounts;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.30, 0.20);

	public static VeilshroomConfig get() {
		return Taz.config().galatea.veilshrooms;
	}

	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
