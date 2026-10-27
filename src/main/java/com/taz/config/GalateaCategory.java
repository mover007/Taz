package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.foraging.FairySoulsConfig;
import com.taz.foraging.HoneyhiveConfig;
import com.taz.foraging.HuntingMobsConfig;
import com.taz.foraging.IslandGuideConfig;
import com.taz.foraging.MoongladeResourcesConfig;
import com.taz.foraging.PressureConfig;
import com.taz.foraging.RouteConfig;
import com.taz.foraging.StarlynConfig;
import com.taz.foraging.SweepConfig;
import com.taz.foraging.TreeGiftsConfig;
import com.taz.foraging.TreeProgressConfig;
import com.taz.foraging.TreesConfig;
import com.taz.foraging.VeilshroomConfig;
import com.taz.foraging.WhisperConfig;
import com.taz.foraging.safari.SafariConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Galatea" category: one dropdown per Moonglade Marsh / Torrhus Canyon feature. */
public final class GalateaCategory {
	@Expose
	@ConfigOption(name = "Next Tree Pointer", desc = "Points at the closest fully grown Fig, Mangrove or Helix tree.")
	@Accordion
	public TreesConfig treePointer = new TreesConfig();

	@Expose
	@ConfigOption(name = "Tree Route Assist", desc = "Your tree route from Skyblocker (import / export its format), only on its island. "
			+ "Training mode guides you warp to tree in order and times your laps.")
	@Accordion
	public RouteConfig route = new RouteConfig();

	@Expose
	@ConfigOption(name = "Tree Progress", desc = "The tree you're chopping in a HUD (\"Fig Tree 37%\"), a TIMBER! alert, and trees felled per hour.")
	@Accordion
	public TreeProgressConfig treeProgress = new TreeProgressConfig();

	@Expose
	@ConfigOption(name = "Sweep Details", desc = "Hypixel's Sweep Details chat block as a small HUD, and a warning when you cut a tree in the wrong order.")
	@Accordion
	public SweepConfig sweep = new SweepConfig();

	@Expose
	@ConfigOption(name = "Tree Gifts & Loot", desc = "Tree Gifts, bonus gifts, KARMA, mobs, Honeycomb and the Lottery buff in a HUD; one chat line per gift; rare bonus alert.")
	@Accordion
	public TreeGiftsConfig treeGifts = new TreeGiftsConfig();

	@Expose
	@ConfigOption(name = "Starlyn Contests", desc = "Time left in Agatha's / Miria's contest, your last score and bracket, and one line per reward claim.")
	@Accordion
	public StarlynConfig starlyn = new StarlynConfig();

	@Expose
	@ConfigOption(name = "Whisper Counter", desc = "Forest and Desert Whispers per hour, counted only while you forage, in a movable HUD.")
	@Accordion
	public WhisperConfig whispers = new WhisperConfig();

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
	@ConfigOption(name = "Critter Safari", desc = "Your guide to the Critter Safari: the critters you still need glow with a name tag, "
			+ "a HUD says what's left and how to find each one, and Taz tells you what to do.")
	@Accordion
	public SafariConfig safari = new SafariConfig();

	@Expose
	@ConfigOption(name = "Fairy Souls", desc = "Waypoints and hints for the Moonglade Marsh and Torrhus Canyon Fairy Souls you haven't found.")
	@Accordion
	public FairySoulsConfig fairySouls = new FairySoulsConfig();

	@Expose
	@ConfigOption(name = "Island Guide", desc = "A beam and pointer to any NPC or spot on Galatea you pick.")
	@Accordion
	public IslandGuideConfig guide = new IslandGuideConfig();
}
