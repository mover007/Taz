package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Inventory > Storage Overlay in the Taz settings GUI (/taz). */
public final class StorageOverlayConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "/storage shows every ender chest page and backpack with its items at once; click one to open it.")
	@ConfigEditorBoolean
	@SearchTag("storage overlay ender chest backpack find items")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Size", desc = "")
	@ConfigEditorSlider(minValue = 0.5f, maxValue = 2f, minStep = 0.05f)
	public float scale = 1f;

	@Expose
	@ConfigOption(name = "Columns", desc = "Pages side by side.")
	@ConfigEditorSlider(minValue = 1, maxValue = 6, minStep = 1)
	public int columns = 3;

	@Expose
	@ConfigOption(name = "Height", desc = "How tall the overlay can get before it scrolls.")
	@ConfigEditorSlider(minValue = 80, maxValue = 600, minStep = 1)
	public int maxHeight = 324;

	@Expose
	@ConfigOption(name = "Scroll speed", desc = "")
	@ConfigEditorSlider(minValue = 1, maxValue = 50, minStep = 1)
	public int scrollSpeed = 10;

	@Expose
	@ConfigOption(name = "Keep scroll", desc = "Open where you left off.")
	@ConfigEditorBoolean
	public boolean keepScroll = true;

	public static StorageOverlayConfig get() {
		return Taz.config().inventory.storageOverlay;
	}
}
