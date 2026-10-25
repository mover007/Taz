package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Etherwarp in the Taz settings GUI (/taz). */
public final class EtherwarpConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "While you sneak with an Etherwarp item, a box where you'd land: red when it works, black when it won't.")
	@ConfigEditorBoolean
	@SearchTag("etherwarp aotv aspect of the void conduit guess teleport")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Show when it fails", desc = "Still draw the box (black) when the Etherwarp would fail.")
	@ConfigEditorBoolean
	public boolean showFail = true;

	@Expose
	@ConfigOption(name = "Full block", desc = "Box the whole block instead of its shape (a slab's half).")
	@ConfigEditorBoolean
	public boolean fullBlock = false;

	@Expose
	@ConfigOption(name = "Through walls", desc = "Draw the box through walls.")
	@ConfigEditorBoolean
	public boolean throughWalls = false;

	@Expose
	@ConfigOption(name = "Etherwarp sound", desc = "Play the XP orb sound when you Etherwarp, instead of the dragon's growl. From NoammAddons.")
	@ConfigEditorBoolean
	public boolean sound = false;

	public static EtherwarpConfig get() {
		return Taz.config().general.etherwarp;
	}
}
