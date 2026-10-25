package com.taz.inventory;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Inventory > Item Prices in the Taz settings GUI (/taz). */
public final class PriceTooltipConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Lowest BIN and bazaar prices at the bottom of item tooltips (also enchanted books and pets).")
	@ConfigEditorBoolean
	@SearchTag("price tooltip lowest bin lbin bazaar coins value worth")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Lowest BIN", desc = "The cheapest Buy It Now on the auction house.")
	@ConfigEditorBoolean
	public boolean lowestBin = true;

	@Expose
	@ConfigOption(name = "Bazaar", desc = "Bazaar insta-buy and insta-sell prices.")
	@ConfigEditorBoolean
	public boolean bazaar = true;

	@Expose
	@ConfigOption(name = "Whole stack", desc = "Also the price of the whole stack, for stacks of more than one.")
	@ConfigEditorBoolean
	public boolean stack = true;

	@Expose
	@ConfigOption(name = "Only while holding Shift", desc = "Keep tooltips short until you hold Shift.")
	@ConfigEditorBoolean
	public boolean onlyShift = false;

	public static PriceTooltipConfig get() {
		return Taz.config().inventory.prices;
	}
}
