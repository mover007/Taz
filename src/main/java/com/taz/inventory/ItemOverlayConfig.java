package com.taz.inventory;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Inventory > Item Overlays in the Taz settings GUI (/taz). */
public final class ItemOverlayConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Rarity colours behind items, and numbers on them: pet level, enchanted book level, minion tier, "
			+ "skill and collection levels in menus, cake year.")
	@ConfigEditorBoolean
	@SearchTag("rarity background colour item number pet level enchant book minion tier collection skill level cake")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Rarity background", desc = "A square in the item's rarity colour behind it, in every inventory and menu.")
	@ConfigEditorBoolean
	public boolean rarity = true;

	@Expose
	@ConfigOption(name = "Skip Common items", desc = "No background for Common items, so the colours stand out.")
	@ConfigEditorBoolean
	public boolean skipCommon = true;

	@Expose
	@ConfigOption(name = "Background strength (%)", desc = "How strong the rarity colour is.")
	@ConfigEditorSlider(minValue = 10, maxValue = 100, minStep = 5)
	public int opacity = 45;

	@Expose
	@ConfigOption(name = "Item numbers", desc = "A small number in the top-left of the slot: pet level, enchanted book level, minion tier, "
			+ "skill and collection levels in menus, New Year Cake year.")
	@ConfigEditorBoolean
	public boolean numbers = true;

	@Expose
	@ConfigOption(name = "Rarity on hotbar", desc = "Also colour your hotbar slots by rarity. From NoammAddons' Item Rarity.")
	@ConfigEditorBoolean
	public boolean hotbar = false;

	public static ItemOverlayConfig get() {
		return Taz.config().inventory.overlays;
	}
}
