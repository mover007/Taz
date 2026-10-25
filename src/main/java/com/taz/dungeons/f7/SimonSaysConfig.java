package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Simon Says in the Taz settings GUI (/taz). */
public final class SimonSaysConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Phase 3's Simon Says device: the buttons in order, the next one in red.")
	@ConfigEditorBoolean
	@SearchTag("simon says ss device p3 goldor buttons")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat", desc = "\"[TAZ] SS 3/4\" in party chat as you finish each round.")
	@ConfigEditorBoolean
	public boolean party = false;

	public static SimonSaysConfig get() {
		return Taz.config().floor7.simonSays;
	}
}
