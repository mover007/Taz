package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Gyro Wand in the Taz settings GUI (/taz). */
public final class GyroWandConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "While you hold the Gyrokinetic Wand, a ring showing where its pull lands.")
	@ConfigEditorBoolean
	@SearchTag("gyro gyrokinetic wand range ring cooldown")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Show cooldown", desc = "The ring turns grey while the wand is on cooldown.")
	@ConfigEditorBoolean
	public boolean cooldown = true;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Draw the ring through walls.")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	public static GyroWandConfig get() {
		return Taz.config().general.gyroWand;
	}
}
