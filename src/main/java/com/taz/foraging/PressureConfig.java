package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Galatea > Pressure Warning in the Taz settings GUI (/taz). */
public final class PressureConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A big title when the pressure in Torrhus Springs (\"Pressure: 85%\" on the action bar) gets high, "
			+ "so you swim up before you faint.")
	@ConfigEditorBoolean
	@SearchTag("pressure torrhus springs depths dive faint warning respiration")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Warn at (%)", desc = "Warn when the pressure reaches this.")
	@ConfigEditorSlider(minValue = 30, maxValue = 95, minStep = 5)
	public int warnAt = 80;

	@Expose
	@ConfigOption(name = "Sound", desc = "")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.ANVIL;

	public static PressureConfig get() {
		return Taz.config().galatea.pressure;
	}
}
