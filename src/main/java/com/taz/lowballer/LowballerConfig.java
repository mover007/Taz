package com.taz.lowballer;

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
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** The "Lowballer" category of the Taz settings GUI (/taz): lowball offers in the trade menu. */
public final class LowballerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "In the trade menu, what the other player's items are worth (SkyCofl prices) and lowball offers for "
			+ "them: 10%, 12%, 14% and so on under, plus a smart offer that goes lower for items that sell slowly. Click an offer to "
			+ "have it typed into the coins sign.")
	@ConfigEditorBoolean
	@SearchTag("lowball lowballer trade offer coins worth value cofl skycofl price volume")
	public boolean enabled = false;

	@ConfigOption(name = "Trade menu HUD", desc = "What the HUD in the trade menu shows.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Lowball steps", desc = "The offers to list, in % under the total worth, separated by commas.")
	@ConfigEditorText
	public String steps = "10, 12, 14, 16, 18, 20";

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Smart offer", desc = "An offer worked out item by item: lower for items that sell slowly or whose price "
			+ "jumps around, and enough to cover the auction house tax.")
	@ConfigEditorBoolean
	public boolean smartOffer = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Item list", desc = "Each of their items with its worth, sales a day and how far under the smart offer goes.")
	@ConfigEditorBoolean
	public boolean itemList = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Your offer", desc = "The coins you've put in, and how far under their worth that is.")
	@ConfigEditorBoolean
	public boolean yourOffer = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Click to offer", desc = "Click an offer on the HUD to copy it. Then click Coins in the trade menu, and Taz "
			+ "types it into the sign for you.")
	@ConfigEditorBoolean
	public boolean clickToOffer = true;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Item tooltips", desc = "Their items' tooltips in the trade menu show the worth, sales a day and smart offer.")
	@ConfigEditorBoolean
	public boolean tooltips = true;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Trade HUD position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@ConfigOption(name = "Smart offers", desc = "How the smart offer is worked out.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Profit margin", desc = "What you want to make on top of the tax, in % of the worth. Slow sellers and "
			+ "unsteady prices add more.")
	@ConfigEditorSlider(minValue = 0, maxValue = 25, minStep = 1)
	public float margin = 5;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Worth", desc = "What an item is worth: the lower of its lowest BIN and what it really sells for "
			+ "(safest), or just one of them.")
	@ConfigEditorDropdown
	public Worth worth = Worth.LOWER;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Most under", desc = "The smart offer never goes further under than this, in %.")
	@ConfigEditorSlider(minValue = 10, maxValue = 60, minStep = 1)
	public float maxUnder = 40;

	@ConfigOption(name = "Prices", desc = "Prices provided by SkyCofl (sky.coflnet.com): the lowest BIN and median sale of items "
			+ "like theirs (same stars, scrolls, pet level and so on), and how many sell a day. Taz isn't affiliated with or "
			+ "endorsed by SkyCofl or Hypixel.")
	@ConfigEditorInfoText
	public transient String credit = "";

	/** Where the trade HUD sits (moved in the HUD editor). */
	@Expose
	public HudPos hud = new HudPos(0.01, 0.12);

	public enum Worth {
		LOWER("Lower of BIN and median"), BIN("Lowest BIN"), MEDIAN("Median sale");

		private final String label;

		Worth(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public static LowballerConfig get() {
		return Taz.config().lowballer;
	}
}
