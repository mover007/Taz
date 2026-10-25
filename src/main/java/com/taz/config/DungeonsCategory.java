package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.secrets.SecretsConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Dungeons" category: one dropdown per dungeon feature. */
public final class DungeonsCategory {
	@Expose
	@ConfigOption(name = "Run Predictor", desc = "Run timer, end-time prediction, score, splits, PBs and chat messages for Catacombs runs.")
	@Accordion
	public ModConfig runPredictor = new ModConfig();

	@Expose
	@ConfigOption(name = "Secret Routes", desc = "Secret routes for every room from Odin waypoint packs, with a guide mode that shows the next step and times each room.")
	@Accordion
	public SecretsConfig secrets = new SecretsConfig();
}
