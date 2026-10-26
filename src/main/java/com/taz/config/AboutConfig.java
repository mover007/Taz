package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import org.lwjgl.glfw.GLFW;

/** The "About" category: what Taz is, the key to open this menu and the HUD editor. */
public final class AboutConfig {
	@ConfigOption(name = "Taz", desc = "All your SkyBlock features in one mod. Pick a category on the left: General, Inventory, Dungeons, "
			+ "Floor 7, Slayer, Fishing, Galatea and SkyBlock XP. Every feature starts off; open its dropdown and turn on Enabled.")
	@ConfigEditorInfoText
	public transient String info = "";

	@ConfigOption(name = "How it works", desc = "§e/taz§7 opens this menu, right where you left it. Use the search bar to find a setting. "
			+ "Settings save as soon as you change them.")
	@ConfigEditorInfoText
	public transient String controls = "";

	@Expose
	@ConfigOption(name = "Open this menu key", desc = "A key that opens this menu, as well as /taz.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int settingsKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Move HUDs", desc = "Every Taz HUD: drag to move, scroll to resize, right-click to hide.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHuds = TazHud::openEditor;
}
