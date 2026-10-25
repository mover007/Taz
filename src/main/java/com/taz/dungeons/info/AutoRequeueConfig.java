package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Auto Requeue in the Taz settings GUI (/taz). */
public final class AutoRequeueConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "When a run ends, queue the same floor again a moment later.")
	@ConfigEditorBoolean
	@SearchTag("auto requeue joininstance instancerequeue")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Use /instancerequeue", desc = "Use /instancerequeue instead of /joininstance <floor>.")
	@ConfigEditorBoolean
	public boolean instanceRequeue = false;

	@Expose
	@ConfigOption(name = "Check party", desc = "Only when you lead the party and it has enough players.")
	@ConfigEditorBoolean
	public boolean checkParty = true;

	@Expose
	@ConfigOption(name = "Minimum party size", desc = "")
	@ConfigEditorSlider(minValue = 1, maxValue = 5, minStep = 1)
	public int minPartySize = 5;

	@Expose
	@ConfigOption(name = "Delay", desc = "Seconds to wait.")
	@ConfigEditorSlider(minValue = 1, maxValue = 10, minStep = 1)
	public int delaySeconds = 1;

	@Expose
	@ConfigOption(name = "Feedback", desc = "Say in your chat what it's doing.")
	@ConfigEditorBoolean
	public boolean feedback = true;

	public static AutoRequeueConfig get() {
		return Taz.config().dungeons.autoRequeue;
	}
}
