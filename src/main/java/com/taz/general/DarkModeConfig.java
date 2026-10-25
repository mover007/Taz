package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Dark Mode in the Taz settings GUI (/taz). */
public final class DarkModeConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Darkens the world with a black tint.")
	@ConfigEditorBoolean
	@SearchTag("dark mode tint darken screen brightness")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Strength", desc = "How dark, in %.")
	@ConfigEditorSlider(minValue = 1, maxValue = 80, minStep = 1)
	public int opacity = 25;

	@Expose
	@ConfigOption(name = "Tint the HUD too", desc = "Also darken the hotbar, chat and HUDs.")
	@ConfigEditorBoolean
	public boolean tintHud = false;

	public static DarkModeConfig get() {
		return Taz.config().general.darkMode;
	}
}
