package com.taz.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Visuals" category: how the game looks and sounds, one dropdown each. */
public final class VisualsCategory {
	@Expose
	@ConfigOption(name = "Performance HUD", desc = "Your FPS, the server's TPS and your ping.")
	@Accordion
	public com.taz.general.PerformanceHudConfig performance = new com.taz.general.PerformanceHudConfig();

	@Expose
	@ConfigOption(name = "Render Optimizer", desc = "Stops drawing falling blocks, lightning, XP orbs, dying mobs, explosions, screen fire and more.")
	@Accordion
	public com.taz.general.RenderOptimizerConfig renderOptimizer = new com.taz.general.RenderOptimizerConfig();

	@Expose
	@ConfigOption(name = "Held Item", desc = "The size, position, turn and swing of the item in your hand.")
	@Accordion
	public com.taz.general.HeldItemConfig heldItem = new com.taz.general.HeldItemConfig();

	@Expose
	@ConfigOption(name = "Block Overlay", desc = "A red box on the block you look at.")
	@Accordion
	public com.taz.general.BlockOverlayConfig blockOverlay = new com.taz.general.BlockOverlayConfig();

	@Expose
	@ConfigOption(name = "Dark Mode", desc = "Darkens the world with a black tint.")
	@Accordion
	public com.taz.general.DarkModeConfig darkMode = new com.taz.general.DarkModeConfig();

	@Expose
	@ConfigOption(name = "Sound Volumes", desc = "Make any sound louder or quieter.")
	@Accordion
	public com.taz.general.SoundVolumesConfig soundVolumes = new com.taz.general.SoundVolumesConfig();

	@Expose
	@ConfigOption(name = "Arrow Fix", desc = "No bow pull-back on shortbows.")
	@Accordion
	public com.taz.general.ArrowFixConfig arrowFix = new com.taz.general.ArrowFixConfig();

	@Expose
	@ConfigOption(name = "Etherwarp", desc = "While you sneak with an Etherwarp item, a box where you'd land.")
	@Accordion
	public com.taz.general.EtherwarpConfig etherwarp = new com.taz.general.EtherwarpConfig();

	@Expose
	@ConfigOption(name = "Gyro Wand", desc = "While you hold the Gyrokinetic Wand, a ring where its pull lands, grey on cooldown.")
	@Accordion
	public com.taz.general.GyroWandConfig gyroWand = new com.taz.general.GyroWandConfig();
}
