package com.taz.dungeons.f7;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Floor 7 > Door Fix in the Taz settings GUI (/taz). */
public final class DoorFixConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Phase 3, section 3: turns the iron doors along the wall the right way round (they show turned on new "
			+ "versions). Only on your screen.")
	@ConfigEditorBoolean
	@SearchTag("door fix rotation s3 section 3 goldor")
	public boolean enabled = false;

	public static DoorFixConfig get() {
		return Taz.config().floor7.doorFix;
	}
}
