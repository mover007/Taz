package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.slayer.SlayerConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Slayer" category. */
public final class SlayerCategory {
	@Expose
	@ConfigOption(name = "Slayer Helper", desc = "Boss spawned alert, a box around your boss, and kill times.")
	@Accordion
	public SlayerConfig helper = new SlayerConfig();
}
