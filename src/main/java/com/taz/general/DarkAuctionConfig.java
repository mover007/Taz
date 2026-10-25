package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Dark Auction Reminder in the Taz settings GUI (/taz). */
public final class DarkAuctionConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Tells you when a Dark Auction is about to start (they start at :55 every hour), "
			+ "with a title, a sound and a chat button that warps you there.")
	@ConfigEditorBoolean
	@SearchTag("dark auction da reminder sirius warp")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Remind before (minutes)", desc = "How many minutes before it starts you get the reminder.")
	@ConfigEditorSlider(minValue = 1, maxValue = 10, minStep = 1)
	public int minutesBefore = 3;

	@Expose
	@ConfigOption(name = "Also when it starts", desc = "A second reminder the moment it starts.")
	@ConfigEditorBoolean
	public boolean atStart = true;

	@Expose
	@ConfigOption(name = "Title", desc = "A big title on screen as well as the chat message.")
	@ConfigEditorBoolean
	public boolean title = true;

	@Expose
	@ConfigOption(name = "Sound", desc = "The reminder's sound.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.BELL;

	@Expose
	@ConfigOption(name = "Warp button", desc = "A [Warp] button in the chat message that runs /warp da.")
	@ConfigEditorBoolean
	public boolean warpButton = true;

	@Expose
	@ConfigOption(name = "Only on SkyBlock", desc = "No reminders in lobbies or other games.")
	@ConfigEditorBoolean
	public boolean onlySkyblock = true;

	@ConfigOption(name = "Test", desc = "Show the reminder now.")
	@ConfigEditorButton(buttonText = "Test")
	public transient Runnable test = () -> DarkAuctionReminder.remind("Dark Auction in 3m 0s!");

	public static DarkAuctionConfig get() {
		return Taz.config().general.darkAuction;
	}
}
