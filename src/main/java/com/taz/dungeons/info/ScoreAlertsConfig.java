package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Score Alerts in the Taz settings GUI (/taz). */
public final class ScoreAlertsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Tells you when the run reaches 270 and 300 score (and how long it took), and says in party chat when "
			+ "the Mimic, the Prince or a bonus Bat dies.")
	@ConfigEditorBoolean
	@SearchTag("score 300 270 mimic prince bat s+")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "300 title", desc = "A title at 300 score.")
	@ConfigEditorBoolean
	public boolean title300 = true;

	@Expose
	@ConfigOption(name = "300 party chat", desc = "\"[TAZ] 300 Score!\" in party chat.")
	@ConfigEditorBoolean
	public boolean party300 = false;

	@Expose
	@ConfigOption(name = "270 title", desc = "A title at 270 score.")
	@ConfigEditorBoolean
	public boolean title270 = false;

	@Expose
	@ConfigOption(name = "270 party chat", desc = "\"[TAZ] 270 Score!\" in party chat.")
	@ConfigEditorBoolean
	public boolean party270 = false;

	@Expose
	@ConfigOption(name = "Mimic killed", desc = "\"[TAZ] Mimic Killed!\" in party chat (Floors 6 and 7).")
	@ConfigEditorBoolean
	public boolean mimic = true;

	@Expose
	@ConfigOption(name = "Prince killed", desc = "\"[TAZ] Prince Killed!\" in party chat.")
	@ConfigEditorBoolean
	public boolean prince = true;

	@Expose
	@ConfigOption(name = "Bat killed", desc = "\"[TAZ] Bat Killed!\" in party chat.")
	@ConfigEditorBoolean
	public boolean bat = true;

	public static ScoreAlertsConfig get() {
		return Taz.config().dungeons.scoreAlerts;
	}
}
