package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Terracotta Timer in the Taz settings GUI (/taz). */
public final class TerracottaTimerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "In the Floor 6 boss, a countdown over each dead Terracotta's flower pot until it comes back.")
	@ConfigEditorBoolean
	@SearchTag("terracotta f6 m6 sadan respawn timer")
	public boolean enabled = false;

	public static TerracottaTimerConfig get() {
		return Taz.config().dungeons.terracotta;
	}
}
