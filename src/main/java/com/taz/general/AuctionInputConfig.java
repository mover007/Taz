package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Inventory > Auction Price Input in the Taz settings GUI (/taz). */
public final class AuctionInputConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A text box for auction prices (10m, 2.5k) with an undercut mode, and Enter presses Create and Confirm.")
	@ConfigEditorBoolean
	@SearchTag("auction price input ah sign undercut bin")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Start in Undercut", desc = "Open in Undercut mode (the price you type is taken off the lowest BIN).")
	@ConfigEditorBoolean
	public boolean undercutDefault = false;

	@Expose
	@ConfigOption(name = "Remember text", desc = "Keep what you typed last time.")
	@ConfigEditorBoolean
	public boolean rememberText = false;

	@Expose
	@ConfigOption(name = "Remember mode", desc = "Keep the mode you used last time.")
	@ConfigEditorBoolean
	public boolean rememberMode = false;

	public static AuctionInputConfig get() {
		return Taz.config().inventory.auctionInput;
	}
}
