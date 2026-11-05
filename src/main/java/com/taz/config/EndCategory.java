package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.end.ProtectorConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "The End" category. */
public final class EndCategory {
	@Expose
	@ConfigOption(name = "End Stone Protector", desc = "Its stage, where it will rise and when, stage 4 and 5 alerts, and a lobby tracker for lobby hopping.")
	@Accordion
	public ProtectorConfig protector = new ProtectorConfig();
}
