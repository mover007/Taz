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
	@Category(name = "About", desc = "What Taz does, and moving its HUDs.")
	public AboutConfig about = new AboutConfig();

	@Expose
	@Category(name = "General", desc = "Features for all of SkyBlock.")
	public GeneralCategory general = new GeneralCategory();

	@Expose
	@Category(name = "Inventory", desc = "Item prices and overlays.")
	public InventoryCategory inventory = new InventoryCategory();

	@Expose
	@Category(name = "Dungeons", desc = "Catacombs features.")
	public DungeonsCategory dungeons = new DungeonsCategory();

	@Expose
	@Category(name = "Floor 7", desc = "The Floor 7 and M7 boss.")
	public Floor7Category floor7 = new Floor7Category();

	@Expose
	@Category(name = "Slayer", desc = "Slayer boss features.")
	public SlayerCategory slayer = new SlayerCategory();

	@Expose
	@Category(name = "Fishing", desc = "Fishing features.")
	public FishingCategory fishing = new FishingCategory();

	@Expose
	@Category(name = "Galatea", desc = "Moonglade Marsh and Torrhus Canyon.")
	public GalateaCategory galatea = new GalateaCategory();

	@Expose
	@Category(name = "SkyBlock XP", desc = "Your SkyBlock level.")
	public SkyblockXpCategory skyblockXp = new SkyblockXpCategory();

	/** Layout version of config.json, for Migration.upgradeConfigFile. */
	@Expose
	public int configVersion = CURRENT_VERSION;

	/** Long descriptions make their option taller instead of shrinking the text. */
	@Override
	public io.github.notenoughupdates.moulconfig.DescriptionRendereringBehaviour getDescriptionBehaviour(
			io.github.notenoughupdates.moulconfig.processor.ProcessedOption option) {
		return io.github.notenoughupdates.moulconfig.DescriptionRendereringBehaviour.EXPAND_PANEL;
	}

	@Override
	public StructuredText getTitle() {
		return StructuredText.of("§4§l[§c§lTAZ§4§l] §7v" + Taz.VERSION + " §8by mTaz");
	}
}
