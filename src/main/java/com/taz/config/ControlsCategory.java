package com.taz.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Controls" category: keybinds and how the game handles, one dropdown each. */
public final class ControlsCategory {
	@Expose
	@ConfigOption(name = "Command Keys", desc = "Keys for commands you use a lot: /pets, /storage, /warp garden ...")
	@Accordion
	public com.taz.general.CommandKeysConfig commandKeys = new com.taz.general.CommandKeysConfig();

	@Expose
	@ConfigOption(name = "Slot Binds", desc = "Bind inventory slots to hotbar slots and shift + click to swap them.")
	@Accordion
	public com.taz.general.SlotBindsConfig slotBinds = new com.taz.general.SlotBindsConfig();

	@Expose
	@ConfigOption(name = "Loadout Keybinds", desc = "Number keys pick a loadout in the Loadout menu.")
	@Accordion
	public com.taz.general.LoadoutKeybindsConfig loadoutKeybinds = new com.taz.general.LoadoutKeybindsConfig();

	@Expose
	@ConfigOption(name = "Auto Sprint", desc = "Always sprint.")
	@Accordion
	public com.taz.general.AutoSprintConfig autoSprint = new com.taz.general.AutoSprintConfig();

	@Expose
	@ConfigOption(name = "No Cursor Reset", desc = "The mouse stays put when one menu replaces another.")
	@Accordion
	public com.taz.general.NoCursorResetConfig noCursorReset = new com.taz.general.NoCursorResetConfig();

	@Expose
	@ConfigOption(name = "Camera", desc = "F5 skips the front-facing view.")
	@Accordion
	public com.taz.general.CameraConfig camera = new com.taz.general.CameraConfig();
}
