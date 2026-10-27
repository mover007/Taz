package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.foraging.FairySoulsConfig;
import com.taz.foraging.HoneyhiveConfig;
import com.taz.foraging.HuntingMobsConfig;
import com.taz.foraging.IslandGuideConfig;
import com.taz.foraging.MoongladeResourcesConfig;
import com.taz.foraging.PressureConfig;
import com.taz.foraging.VeilshroomConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Galatea" category: one dropdown per Moonglade Marsh / Torrhus Canyon feature. */
public final class GalateaCategory {
	@Expose
	@ConfigOption(name = "Honeyhives", desc = "Highlights the Torrhus Canyon honeyhives you can loot, with refill timers and a pointer to the next one.")
	@Accordion
	public HoneyhiveConfig honeyhives = new HoneyhiveConfig();

	@Expose
	@ConfigOption(name = "Ruby Veilshroom Finder", desc = "Every Ruby Veilshroom in the Torrhus Springs caves, through rock, with a pointer and respawn timers.")
	@Accordion
	public VeilshroomConfig veilshrooms = new VeilshroomConfig();

	@Expose
	@ConfigOption(name = "Pressure Warning", desc = "A big title when the Torrhus Springs pressure gets high, so you swim up before you faint.")
	@Accordion
	public PressureConfig pressure = new PressureConfig();

	@Expose
	@ConfigOption(name = "Sea Lumies & Lushlilac", desc = "Boxes round Sea Lumies and Lushlilac on Moonglade Marsh.")
	@Accordion
	public MoongladeResourcesConfig resources = new MoongladeResourcesConfig();

	@Expose
	@ConfigOption(name = "Hunting Mobs", desc = "Hideonleaf, Hideonsun, Shellwise, Coralot and Blue Jay glow through walls; mute Phantoms.")
	@Accordion
	public HuntingMobsConfig huntingMobs = new HuntingMobsConfig();

	@Expose
	@ConfigOption(name = "Fairy Souls", desc = "Waypoints and hints for the Moonglade Marsh and Torrhus Canyon Fairy Souls you haven't found.")
	@Accordion
	public FairySoulsConfig fairySouls = new FairySoulsConfig();

	@Expose
	@ConfigOption(name = "Island Guide", desc = "A beam and pointer to any NPC or spot on Galatea you pick.")
	@Accordion
	public IslandGuideConfig guide = new IslandGuideConfig();
}
