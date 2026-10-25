package com.taz.fishing;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Fishing > Fishing Helper in the Taz settings GUI (/taz). */
public final class FishingConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A bite alert when the \"!!!\" shows over your bobber, a timer over your bobber, and a Double Hook alert.")
	@ConfigEditorBoolean
	@SearchTag("fishing bite alert bobber timer double hook sea creature")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Bite alert", desc = "A title and sound the moment a fish bites (Hypixel's \"!!!\" over your bobber), so you can reel in straight away.")
	@ConfigEditorBoolean
	public boolean biteAlert = true;

	@Expose
	@ConfigOption(name = "Bite sound", desc = "The sound for a bite.")
	@ConfigEditorDropdown
	public Alert.Sound biteSound = Alert.Sound.HIGH_PLING;

	@Expose
	@ConfigOption(name = "Bobber timer", desc = "Seconds since you cast, over your bobber.")
	@ConfigEditorBoolean
	public boolean timer = true;

	@Expose
	@ConfigOption(name = "Double Hook alert", desc = "A title when you catch two sea creatures at once.")
	@ConfigEditorBoolean
	public boolean doubleHook = true;

	public static FishingConfig get() {
		return Taz.config().fishing.helper;
	}
}
