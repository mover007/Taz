package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

/**
 * Everything in the settings GUI (/taz), saved as config/taz/config.json. Categories are areas of
 * the game; each feature is one collapsed dropdown in its category, with "Enabled" first.
 * To add a feature: give it a config class, then add it as an @Accordion field in the right
 * category class (or a new category here). Every feature starts off (enabled = false). If a saved field moves, bump CURRENT_VERSION and add
 * the move to Migration.upgradeConfigFile.
 */
public final class TazConfig extends Config {
	public static final int CURRENT_VERSION = 4;

	@Expose
	@Category(name = "About", desc = "What Taz does, the key to open this menu, and the HUD editors.")
	public AboutConfig about = new AboutConfig();

	@Expose
	@Category(name = "General", desc = "Features for all of SkyBlock: alerts, Dark Auction reminder, chat filter, command keys, event timers, skill tracker, chat waypoints.")
	public GeneralCategory general = new GeneralCategory();

	@Expose
	@Category(name = "Inventory", desc = "Item prices in tooltips, rarity colours and numbers on items.")
	public InventoryCategory inventory = new InventoryCategory();

	@Expose
	@Category(name = "Dungeons", desc = "Catacombs features.")
	public DungeonsCategory dungeons = new DungeonsCategory();

	@Expose
	@Category(name = "Floor 7", desc = "The Floor 7 / Master Mode 7 boss fight: tick timers, leap counter.")
	public Floor7Category floor7 = new Floor7Category();

	@Expose
	@Category(name = "Slayer", desc = "Slayer boss features.")
	public SlayerCategory slayer = new SlayerCategory();

	@Expose
	@Category(name = "Fishing", desc = "Fishing features.")
	public FishingCategory fishing = new FishingCategory();

	@Expose
	@Category(name = "Galatea", desc = "Moonglade Marsh and Torrhus Canyon: trees, sweep, whispers, honeyhives, Ruby Veilshrooms, Sea Lumies, hunting mobs, Fairy Souls, island guide.")
	public GalateaCategory galatea = new GalateaCategory();

	@Expose
	@Category(name = "SkyBlock XP", desc = "SkyBlock level and XP features.")
	public SkyblockXpCategory skyblockXp = new SkyblockXpCategory();

	/** Layout version of config.json, for Migration.upgradeConfigFile. */
	@Expose
	public int configVersion = CURRENT_VERSION;

	@Override
	public StructuredText getTitle() {
		return StructuredText.of("§4§l[§c§lTAZ§4§l] §7v" + Taz.VERSION + " §8by mTaz");
	}
}
