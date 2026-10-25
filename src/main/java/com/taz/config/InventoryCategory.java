package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.inventory.ItemOverlayConfig;
import com.taz.inventory.PriceTooltipConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Inventory" category: item tooltips and inventory overlays. */
public final class InventoryCategory {
	@Expose
	@ConfigOption(name = "Item Prices", desc = "Lowest BIN and bazaar prices in item tooltips.")
	@Accordion
	public PriceTooltipConfig prices = new PriceTooltipConfig();

	@Expose
	@ConfigOption(name = "Item Overlays", desc = "Rarity colours behind items and numbers on them (pet level, book level, minion tier ...).")
	@Accordion
	public ItemOverlayConfig overlays = new ItemOverlayConfig();
}
