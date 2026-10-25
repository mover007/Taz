package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Arrow Align in the Taz settings GUI (/taz). */
public final class ArrowAlignConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Phase 3's arrow device: how many times to click each frame.")
	@ConfigEditorBoolean
	@SearchTag("arrow align device p3 goldor item frames")
	public boolean enabled = false;

	public static ArrowAlignConfig get() {
		return Taz.config().floor7.arrowAlign;
	}
}
