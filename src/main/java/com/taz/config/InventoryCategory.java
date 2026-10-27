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

	@Expose
	@ConfigOption(name = "Storage Overlay", desc = "/storage shows every ender chest page and backpack with its items at once.")
	@Accordion
	public com.taz.general.StorageOverlayConfig storageOverlay = new com.taz.general.StorageOverlayConfig();

	@Expose
	@ConfigOption(name = "Auction Price Input", desc = "A text box for auction prices with an undercut mode.")
	@Accordion
	public com.taz.general.AuctionInputConfig auctionInput = new com.taz.general.AuctionInputConfig();
}
