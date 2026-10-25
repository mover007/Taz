package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.TazHud;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import org.lwjgl.glfw.GLFW;

/** The "About" category: what Taz is, the keys to open this menu and the HUD editor. */
public final class AboutConfig {
	@ConfigOption(name = "Taz", desc = "One mod for your SkyBlock features. §bGeneral§7: alerts, Dark Auction reminder, chat filter, command keys, "
			+ "event timers, skill tracker, chat waypoints. §bInventory§7: item prices, rarity colours, item numbers. §bDungeons§7: run timer and "
			+ "end-time prediction. §bSlayer§7: boss alert, highlight, kill times. §bFishing§7: bite alert, bobber timer. "
			+ "§bGalatea§7: tree pointer and progress, sweep details, whisper rates, honeyhives, Ruby Veilshroom finder, Sea Lumies, "
			+ "hunting mob glows, Fairy Souls, island guide. §bSkyBlock XP§7: cheapest XP list, level plan, NPC tracker.")
	@ConfigEditorInfoText
	public transient String info = "";

	@ConfigOption(name = "Controls", desc = "Everything is in this menu: §e/taz§7 opens it. Every setting, button and keybind "
			+ "for every feature is in its category on the left. Use the search bar to find one. "
			+ "§aSettings save by themselves§7 the moment you change them, so they're all still there next time you start the game.")
	@ConfigEditorInfoText
	public transient String controls = "";

	@Expose
	@ConfigOption(name = "Open this menu key", desc = "A key that opens this menu, as well as /taz.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int settingsKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Move HUDs", desc = "Every Taz HUD on one screen: drag to move, scroll to resize, right-click to hide or show, "
			+ "R to reset one.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHuds = TazHud::openEditor;

	@Expose
	@ConfigOption(name = "Move HUDs key", desc = "Opens the HUD editor in game.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_RIGHT_SHIFT)
	public int hudEditorKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

	@Expose
	@ConfigOption(name = "Log Galatea HUD text", desc = "On Galatea, writes each new kind of sidebar, action bar and title line to the game log "
			+ "(logs/latest.log), so Taz can learn messages it doesn't know yet (contest points, the Torrhus Springs pressure meter). "
			+ "Nothing leaves your computer.")
	@ConfigEditorBoolean
	public boolean recordGalatea = true;

	@ConfigOption(name = "Reload settings file", desc = "Re-read config/taz/config.json, if you edited it by hand while the game was running.")
	@ConfigEditorButton(buttonText = "Reload")
	public transient Runnable reload = () -> {
		Taz.reloadConfig();
		TazChat.info("Reloaded config/taz/config.json");
	};
}
