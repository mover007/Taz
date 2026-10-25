package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Wither Dragons in the Taz settings GUI (/taz). */
public final class WitherDragonsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Master Mode 7's dragons: spawn titles and timers, boxes, a line to the next, health, and how long each lived.")
	@ConfigEditorBoolean
	@SearchTag("wither dragons m7 p5 dragon timer spawn priority relics")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Spawn timer", desc = "A countdown over each spawning dragon and on a HUD for the first one due.")
	@ConfigEditorBoolean
	public boolean timer = true;

	@Expose
	@ConfigOption(name = "Spawn title", desc = "A title when a dragon starts spawning.")
	@ConfigEditorBoolean
	public boolean title = true;

	@Expose
	@ConfigOption(name = "Boxes", desc = "A box round each living or spawning dragon's area.")
	@ConfigEditorBoolean
	public boolean boxes = true;

	@Expose
	@ConfigOption(name = "Line to next", desc = "A line to the first dragon due.")
	@ConfigEditorBoolean
	public boolean tracer = true;

	@Expose
	@ConfigOption(name = "Health", desc = "Each dragon's health over it.")
	@ConfigEditorBoolean
	public boolean health = true;

	@Expose
	@ConfigOption(name = "Spawned message", desc = "Say in your chat when each dragon spawns.")
	@ConfigEditorBoolean
	public boolean spawned = true;

	@Expose
	@ConfigOption(name = "Time alive", desc = "Say in your chat how long each dragon lived.")
	@ConfigEditorBoolean
	public boolean aliveTime = true;

	@ConfigOption(name = "Timer HUD position", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.45, 0.25);

	public static WitherDragonsConfig get() {
		return Taz.config().floor7.witherDragons;
	}
}
