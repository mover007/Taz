package com.taz.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Foraging" category: trees, whispers and Starlyn Contests on Galatea, one dropdown each. */
public final class ForagingCategory {
	@Expose
	@ConfigOption(name = "Next Tree Pointer", desc = "Points at the closest fully grown Fig, Mangrove or Helix tree.")
	@Accordion
	public com.taz.foraging.TreesConfig treePointer = new com.taz.foraging.TreesConfig();

	@Expose
	@ConfigOption(name = "Tree Route Assist", desc = "Your tree route from Skyblocker (import / export its format), only on its island. "
			+ "Training mode guides you warp to tree in order and times your laps.")
	@Accordion
	public com.taz.foraging.RouteConfig route = new com.taz.foraging.RouteConfig();

	@Expose
	@ConfigOption(name = "Tree Progress", desc = "The tree you're chopping in a HUD (\"Fig Tree 37%\"), a TIMBER! alert, and trees felled per hour.")
	@Accordion
	public com.taz.foraging.TreeProgressConfig treeProgress = new com.taz.foraging.TreeProgressConfig();

	@Expose
	@ConfigOption(name = "Sweep Details", desc = "Hypixel's Sweep Details chat block as a small HUD, and a warning when you cut a tree in the wrong order.")
	@Accordion
	public com.taz.foraging.SweepConfig sweep = new com.taz.foraging.SweepConfig();

	@Expose
	@ConfigOption(name = "Tree Gifts & Loot", desc = "Tree Gifts, bonus gifts, KARMA, mobs, Honeycomb and the Lottery buff in a HUD; one chat line per gift; rare bonus alert.")
	@Accordion
	public com.taz.foraging.TreeGiftsConfig treeGifts = new com.taz.foraging.TreeGiftsConfig();

	@Expose
	@ConfigOption(name = "Whisper Counter", desc = "Forest and Desert Whispers per hour, counted only while you forage, in a movable HUD.")
	@Accordion
	public com.taz.foraging.WhisperConfig whispers = new com.taz.foraging.WhisperConfig();

	@Expose
	@ConfigOption(name = "Starlyn Contests", desc = "Time left in Agatha's / Miria's contest, your last score and bracket, and one line per reward claim.")
	@Accordion
	public com.taz.foraging.StarlynConfig starlyn = new com.taz.foraging.StarlynConfig();
}
