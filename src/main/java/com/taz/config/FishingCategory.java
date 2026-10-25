package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.fishing.FishingConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Fishing" category. */
public final class FishingCategory {
	@Expose
	@ConfigOption(name = "Fishing Helper", desc = "Bite alert, bobber timer and Double Hook alert.")
	@Accordion
	public FishingConfig helper = new FishingConfig();
}
