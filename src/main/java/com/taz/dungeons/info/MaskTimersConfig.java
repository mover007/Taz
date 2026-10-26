package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Mask Timers in the Taz settings GUI (/taz). */
public final class MaskTimersConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Times your Spirit Mask, Bonzo Mask and Phoenix pet: how long you're invincible after one saves you, "
			+ "and its cooldown.")
	@ConfigEditorBoolean
	@SearchTag("invincibility spirit bonzo phoenix mask proc cooldown")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat", desc = "Say it in party chat when one saves you: \"[TAZ] Bonzo Procced! (2/3)\".")
	@ConfigEditorBoolean
	public boolean announce = true;

	@Expose
	@ConfigOption(name = "Alert", desc = "A title and a sound when one saves you.")
	@ConfigEditorBoolean
	public boolean alert = true;

	@Expose
	@ConfigOption(name = "Only in dungeons", desc = "Only time them (and show the HUD) in a dungeon.")
	@ConfigEditorBoolean
	public boolean onlyDungeons = true;

	@Expose
	@ConfigOption(name = "Only in boss", desc = "Only show the HUD in the boss fight.")
	@ConfigEditorBoolean
	public boolean onlyBoss = false;

	@Expose
	@ConfigOption(name = "Spirit Mask", desc = "Show the Spirit Mask.")
	@ConfigEditorBoolean
	public boolean spirit = true;

	@Expose
	@ConfigOption(name = "Bonzo Mask", desc = "Show the Bonzo Mask.")
	@ConfigEditorBoolean
	public boolean bonzo = true;

	@Expose
	@ConfigOption(name = "Phoenix", desc = "Show the Phoenix pet.")
	@ConfigEditorBoolean
	public boolean phoenix = true;

	@Expose
	@ConfigOption(name = "Cooldown on the mask", desc = "Draw the cooldown over the mask in your inventory.")
	@ConfigEditorBoolean
	public boolean onItem = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.40, 0.70);

	public static MaskTimersConfig get() {
		return Taz.config().dungeons.masks;
	}
}
