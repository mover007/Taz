package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

/** Everything in the settings GUI (/taz), saved as config/taz/config.json. */
public final class TazConfig extends Config {
	public static final int CURRENT_VERSION = 5;

	@Expose
	@Category(name = "About", desc = "What Taz does, and moving its HUDs.")
	public AboutConfig about = new AboutConfig();

	@Expose
	@Category(name = "General", desc = "Features for all of SkyBlock.")
	public GeneralCategory general = new GeneralCategory();

	@Expose
	@Category(name = "Chat", desc = "Chat commands, filters, waypoints and shortcuts.")
	public ChatCategory chat = new ChatCategory();

	@Expose
	@Category(name = "Controls", desc = "Keybinds and how the game handles.")
	public ControlsCategory controls = new ControlsCategory();

	@Expose
	@Category(name = "Visuals", desc = "How the game looks and sounds.")
	public VisualsCategory visuals = new VisualsCategory();

	@Expose
	@Category(name = "Inventory", desc = "Item prices, overlays, storage and the auction house.")
	public InventoryCategory inventory = new InventoryCategory();

	@Expose
	@Category(name = "Dungeons", desc = "Catacombs features.")
	public DungeonsCategory dungeons = new DungeonsCategory();

	@Expose
	@Category(name = "Run Predictor", desc = "Run timer, end-time prediction, score, splits and PBs.")
	public com.taz.dungeons.config.ModConfig runPredictor = new com.taz.dungeons.config.ModConfig();

	@Expose
	@Category(name = "Secret Routes", desc = "Secret routes for every Catacombs room.")
	public com.taz.dungeons.secrets.SecretsConfig secretRoutes = new com.taz.dungeons.secrets.SecretsConfig();

	@Expose
	@Category(name = "Puzzle Solvers", desc = "The answer to each dungeon puzzle.")
	public com.taz.dungeons.puzzles.PuzzlesConfig puzzles = new com.taz.dungeons.puzzles.PuzzlesConfig();

	@Expose
	@Category(name = "Dungeon Map", desc = "The dungeon map and map info.")
	public DungeonMapCategory dungeonMap = new DungeonMapCategory();

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
	@Category(name = "Foraging", desc = "Trees, whispers and Starlyn Contests.")
	public ForagingCategory foraging = new ForagingCategory();

	@Expose
	@Category(name = "Galatea", desc = "Moonglade Marsh and Torrhus Canyon.")
	public GalateaCategory galatea = new GalateaCategory();

	@Expose
	@Category(name = "Critter Safari", desc = "Your guide to the Critter Safari.")
	public com.taz.foraging.safari.SafariConfig safari = new com.taz.foraging.safari.SafariConfig();

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
