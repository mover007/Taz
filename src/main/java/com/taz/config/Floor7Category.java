package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.f7.LeapCounterConfig;
import com.taz.dungeons.f7.TickTimersConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Floor 7" category: features for the Floor 7 / Master Mode 7 boss fight, one dropdown each. */
public final class Floor7Category {
	@Expose
	@ConfigOption(name = "Tick Timers", desc = "Server-tick timers for Maxor's start, Storm's pads and purple pad, and Goldor's death ticks.")
	@Accordion
	public TickTimersConfig tickTimers = new TickTimersConfig();

	@Expose
	@ConfigOption(name = "Leap Counter", desc = "How many teammates have leaped to you at each Phase 3 spot, and an alert when everyone has.")
	@Accordion
	public LeapCounterConfig leapCounter = new LeapCounterConfig();
}
