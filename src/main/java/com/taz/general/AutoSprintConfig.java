package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Controls > Auto Sprint in the Taz settings GUI (/taz). */
public final class AutoSprintConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "You always sprint, without holding the sprint key. From Odin.")
	@ConfigEditorBoolean
	@SearchTag("auto sprint toggle sprint")
	public boolean enabled = false;

	public static AutoSprintConfig get() {
		return Taz.config().controls.autoSprint;
	}
}
