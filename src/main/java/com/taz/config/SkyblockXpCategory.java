package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.xpassist.config.ModConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "SkyBlock XP" category: one dropdown per SkyBlock XP feature. */
public final class SkyblockXpCategory {
	@Expose
	@ConfigOption(name = "XP Assist", desc = "Cheapest SkyBlock XP list, level plan, NPC tracker and HUD.")
	@Accordion
	public ModConfig xpAssist = new ModConfig();
}
