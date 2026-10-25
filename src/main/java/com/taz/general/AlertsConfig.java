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

/** General > Alerts in the Taz settings GUI (/taz). */
public final class AlertsConfig {
	public enum DropTier {
		ALL("Every rare drop"),
		VERY_RARE("Very rare and better"),
		CRAZY_RARE("Crazy rare and better");

		private final String label;

		DropTier(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "A big title and a sound for things you shouldn't miss: rare drops, filled bazaar orders, "
			+ "sold auctions, party invites and low health.")
	@ConfigEditorBoolean
	@SearchTag("alert notification title sound rare drop bazaar auction party invite health")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Rare drops", desc = "RARE DROP!, VERY RARE DROP!, CRAZY RARE DROP!, PET DROP! and the like, with the item.")
	@ConfigEditorBoolean
	public boolean rareDrops = true;

	@Expose
	@ConfigOption(name = "Which drops", desc = "Alert for every rare drop, or only the rarer ones.")
	@ConfigEditorDropdown
	public DropTier dropTier = DropTier.ALL;

	@Expose
	@ConfigOption(name = "Bazaar orders filled", desc = "When one of your bazaar buy orders or sell offers is filled.")
	@ConfigEditorBoolean
	public boolean bazaar = true;

	@Expose
	@ConfigOption(name = "Auction sold", desc = "When someone buys one of your auctions.")
	@ConfigEditorBoolean
	public boolean auctions = true;

	@Expose
	@ConfigOption(name = "Party invites", desc = "When someone invites you to a party (handy when you're tabbed out).")
	@ConfigEditorBoolean
	public boolean partyInvites = true;

	@Expose
	@ConfigOption(name = "Low health", desc = "A red LOW HEALTH title when your health (from the action bar) drops below the limit below.")
	@ConfigEditorBoolean
	public boolean lowHealth = true;

	@Expose
	@ConfigOption(name = "Low health limit (%)", desc = "Warn below this much of your max health.")
	@ConfigEditorSlider(minValue = 5, maxValue = 75, minStep = 5)
	public int lowHealthPercent = 25;

	@Expose
	@ConfigOption(name = "Sound", desc = "The sound every alert plays.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.PLING;

	@ConfigOption(name = "Test", desc = "Show a test alert.")
	@ConfigEditorButton(buttonText = "Test")
	public transient Runnable test = () -> Alert.show("§6§lRARE DROP!", "§5Test Item", get().sound);

	public static AlertsConfig get() {
		return Taz.config().general.alerts;
	}
}
