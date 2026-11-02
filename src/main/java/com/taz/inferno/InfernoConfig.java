package com.taz.inferno;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** The "Inferno Minions" category of the Taz settings GUI (/taz): fuel costs, profit, fuel timers and rare drops. */
public final class InfernoConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Tracks your Inferno minions: what each fuel cost you (from what you bought on the bazaar to craft it), "
			+ "what it should make, and what you collect from it. Open the tracker with /taz inferno.")
	@ConfigEditorBoolean
	@SearchTag("inferno minion fuel gabagool hypergolic distillate chili pepper vertex apex profit tracker")
	public boolean enabled = false;

	@ConfigOption(name = "Profit Tracker", desc = "Every fuel you put in, what it cost and what it made.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Chat lines", desc = "A line in chat when you craft a fuel, put one in a minion and collect from one, with the coins.")
	@ConfigEditorBoolean
	public boolean chat = true;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Open the tracker", desc = "Your fuels, minions, drops and what you bought. Also /taz inferno.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable open = () -> Taz.openScreenNextTick(InfernoScreen::new);

	@ConfigOption(name = "Prices and speed", desc = "How the coins and what a fuel should make are worked out.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Bazaar prices", desc = "Orders: you buy with buy orders and sell with sell offers (cheaper in, more out). "
			+ "Instant: you insta-buy and insta-sell.")
	@ConfigEditorDropdown
	public Prices prices = Prices.ORDERS;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Mithril Infusion", desc = "Your Inferno minions have a Mithril Infusion (10% faster).")
	@ConfigEditorBoolean
	public boolean infusion = true;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Other speed", desc = "Any other speed boost on your island in %, like a Beacon (2-10%) or a Power Crystal. "
			+ "Taz reads each minion's Flycatchers and Minion Expanders itself.")
	@ConfigEditorSlider(minValue = 0, maxValue = 30, minStep = 1)
	public float otherSpeed = 0;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Inferno minions placed", desc = "For Rising Celsius (each one makes them all 18% faster, up to 10). "
			+ "0: from the tab list's Minions line, or the ones you've opened.")
	@ConfigEditorSlider(minValue = 0, maxValue = 10, minStep = 1)
	public float placed = 0;

	@ConfigOption(name = "Fuel timers", desc = "When each minion's fuel runs out.")
	@ConfigEditorAccordion(id = 2)
	public boolean section2 = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Fuel HUD", desc = "Your Inferno minions and how long their fuel lasts.")
	@ConfigEditorBoolean
	public boolean hud = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Only on your island", desc = "Show the fuel HUD only on your private island.")
	@ConfigEditorBoolean
	public boolean hudIsland = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Fuel ran out alert", desc = "A title and a chat line when a minion's fuel runs out.")
	@ConfigEditorBoolean
	public boolean emptyAlert = true;

	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Fuel HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@ConfigOption(name = "Rare drops", desc = "Alerts for what Hypergolic fuel makes once in a while.")
	@ConfigEditorAccordion(id = 3)
	public boolean section3 = false;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Rare drop alert", desc = "A title when you collect an Inferno Apex, Reaper Pepper or Gabagool the Fish.")
	@ConfigEditorBoolean
	public boolean rareAlert = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Inferno Vertex too", desc = "Also a title for an Inferno Vertex (about one a day per minion).")
	@ConfigEditorBoolean
	public boolean vertexAlert = false;

	@ConfigOption(name = "Prices", desc = "Prices provided by SkyCofl (sky.coflnet.com). Taz isn't affiliated with or endorsed by SkyCofl or Hypixel.")
	@ConfigEditorInfoText
	public transient String credit = "";

	/** Where the fuel HUD sits (moved in the HUD editor). */
	@Expose
	public HudPos pos = new HudPos(0.01, 0.30);

	public enum Prices {
		ORDERS("Orders"), INSTANT("Instant");

		private final String label;

		Prices(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static InfernoConfig get() {
		return Taz.config().inferno;
	}
}
