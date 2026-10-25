package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Pet Display in the Taz settings GUI (/taz). */
public final class PetDisplayConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Your active pet and its level on screen. Open /pets once so Taz sees which one you have out.")
	@ConfigEditorBoolean
	@SearchTag("pet display autopet active pet level")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Held item", desc = "Show the pet's held item too (seen in /pets).")
	@ConfigEditorBoolean
	public boolean showItem = true;

	@Expose
	@ConfigOption(name = "Autopet title", desc = "A title with the pet's name when Autopet swaps it.")
	@ConfigEditorBoolean
	public boolean autopetTitle = true;

	@Expose
	@ConfigOption(name = "Title only in dungeons", desc = "Only show the Autopet title in a dungeon.")
	@ConfigEditorBoolean
	public boolean titleDungeonsOnly = false;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.01, 0.20);

	public static PetDisplayConfig get() {
		return Taz.config().general.pets;
	}
}
