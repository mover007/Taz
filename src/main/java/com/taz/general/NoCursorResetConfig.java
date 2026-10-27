package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Controls > No Cursor Reset in the Taz settings GUI (/taz). */
public final class NoCursorResetConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "When a menu opens right after another closes (Hypixel menus do this), the mouse stays where it was instead of jumping to the middle. From Odin.")
	@ConfigEditorBoolean
	@SearchTag("no cursor reset mouse menu gui")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Within", desc = "How soon after a menu closes the next one counts as the same (ms).")
	@ConfigEditorSlider(minValue = 0, maxValue = 1000, minStep = 10)
	public int timeoutMs = 150;

	public static NoCursorResetConfig get() {
		return Taz.config().controls.noCursorReset;
	}
}
