package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Terminal Highlight in the Taz settings GUI (/taz). */
public final class TerminalHighlightConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Phase 3: a box on each terminal in your section that isn't done yet.")
	@ConfigEditorBoolean
	@SearchTag("terminal esp highlight inactive goldor p3")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Flash on click", desc = "The box flashes white when you click the terminal.")
	@ConfigEditorBoolean
	public boolean flash = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Draw the boxes through walls.")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	public static TerminalHighlightConfig get() {
		return Taz.config().floor7.terminals;
	}
}
