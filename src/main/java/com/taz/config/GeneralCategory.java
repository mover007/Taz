package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.general.AlertsConfig;
import com.taz.general.ChatFilterConfig;
import com.taz.general.CommandKeysConfig;
import com.taz.general.DarkAuctionConfig;
import com.taz.general.EtherwarpConfig;
import com.taz.general.EventTimersConfig;
import com.taz.general.GyroWandConfig;
import com.taz.general.KickTimerConfig;
import com.taz.general.PerformanceHudConfig;
import com.taz.general.PetDisplayConfig;
import com.taz.general.RagnarockConfig;
import com.taz.general.SpringBootsConfig;
import com.taz.general.SkillTrackerConfig;
import com.taz.general.WaypointsConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "General" category: features for all of SkyBlock, one dropdown each. */
public final class GeneralCategory {
	@Expose
	@ConfigOption(name = "Alerts", desc = "Big titles and a sound for rare drops, filled bazaar orders, sold auctions, party invites and low health.")
	@Accordion
	public AlertsConfig alerts = new AlertsConfig();

	@Expose
	@ConfigOption(name = "Dark Auction Reminder", desc = "Tells you a few minutes before every Dark Auction (:55 every hour), with a button to warp there.")
	@Accordion
	public DarkAuctionConfig darkAuction = new DarkAuctionConfig();

	@Expose
	@ConfigOption(name = "Chat Filter", desc = "Hides spammy Hypixel messages (lobby joins, Watchdog, warping, ability damage ...).")
	@Accordion
	public ChatFilterConfig chatFilter = new ChatFilterConfig();

	@Expose
	@ConfigOption(name = "Command Keys", desc = "Keys for commands you use a lot: /pets, /storage, /warp garden ...")
	@Accordion
	public CommandKeysConfig commandKeys = new CommandKeysConfig();

	@Expose
	@ConfigOption(name = "Event Timers", desc = "A HUD counting down to the Dark Auction, Jacob's Contest, Cult of the Fallen Star and other events, with reminders.")
	@Accordion
	public EventTimersConfig eventTimers = new EventTimersConfig();

	@Expose
	@ConfigOption(name = "Skill Tracker", desc = "Skill XP per hour and time to your next level, counted only while you're training.")
	@Accordion
	public SkillTrackerConfig skillTracker = new SkillTrackerConfig();

	@Expose
	@ConfigOption(name = "Chat Waypoints", desc = "Coordinates posted in chat become a beam to walk to, and a key sends yours to your party.")
	@Accordion
	public WaypointsConfig waypoints = new WaypointsConfig();

	@Expose
	@ConfigOption(name = "Performance HUD", desc = "Your FPS, the server's TPS and your ping.")
	@Accordion
	public PerformanceHudConfig performance = new PerformanceHudConfig();

	@Expose
	@ConfigOption(name = "Pet Display", desc = "Your active pet, its level and held item, and a title when Autopet swaps it.")
	@Accordion
	public PetDisplayConfig pets = new PetDisplayConfig();

	@Expose
	@ConfigOption(name = "Ragnarock", desc = "Alerts when you cast the Ragnarock Axe or it's cancelled, and the Strength it gave you.")
	@Accordion
	public RagnarockConfig ragnarock = new RagnarockConfig();

	@Expose
	@ConfigOption(name = "Spring Boots", desc = "How high your Spring Boots will throw you as you charge them.")
	@Accordion
	public SpringBootsConfig springBoots = new SpringBootsConfig();

	@Expose
	@ConfigOption(name = "Kick Timer", desc = "How long ago Hypixel kicked you from SkyBlock, so you know when you can rejoin.")
	@Accordion
	public KickTimerConfig kickTimer = new KickTimerConfig();

	@Expose
	@ConfigOption(name = "Etherwarp", desc = "While you sneak with an Etherwarp item, a box where you'd land.")
	@Accordion
	public EtherwarpConfig etherwarp = new EtherwarpConfig();

	@Expose
	@ConfigOption(name = "Gyro Wand", desc = "While you hold the Gyrokinetic Wand, a ring where its pull lands, grey on cooldown.")
	@Accordion
	public GyroWandConfig gyroWand = new GyroWandConfig();

	@Expose
	@ConfigOption(name = "Chat Commands", desc = "Answers \"!\" commands from your party: !warp, !pt, !kick, !f7, !cf, !8ball, !dice, !ping, !dt...")
	@Accordion
	public com.taz.general.ChatCommandsConfig chatCommands = new com.taz.general.ChatCommandsConfig();

	@Expose
	@ConfigOption(name = "Chat", desc = "Ctrl + click to copy a message, emoji codes, and Explosive Shot damage per enemy.")
	@Accordion
	public com.taz.general.ChatTweaksConfig chat = new com.taz.general.ChatTweaksConfig();

	@Expose
	@ConfigOption(name = "Command Shortcuts", desc = "Your own short commands: /dn sends /warp dhub.")
	@Accordion
	public com.taz.general.CommandShortcutsConfig commandShortcuts = new com.taz.general.CommandShortcutsConfig();

	@Expose
	@ConfigOption(name = "Slot Binds", desc = "Bind inventory slots to hotbar slots and shift + click to swap them.")
	@Accordion
	public com.taz.general.SlotBindsConfig slotBinds = new com.taz.general.SlotBindsConfig();

	@Expose
	@ConfigOption(name = "Loadout Keybinds", desc = "Number keys pick a loadout in the Loadout menu.")
	@Accordion
	public com.taz.general.LoadoutKeybindsConfig loadoutKeybinds = new com.taz.general.LoadoutKeybindsConfig();

	@Expose
	@ConfigOption(name = "Auto Sprint", desc = "Always sprint.")
	@Accordion
	public com.taz.general.AutoSprintConfig autoSprint = new com.taz.general.AutoSprintConfig();

	@Expose
	@ConfigOption(name = "No Cursor Reset", desc = "The mouse stays put when one menu replaces another.")
	@Accordion
	public com.taz.general.NoCursorResetConfig noCursorReset = new com.taz.general.NoCursorResetConfig();

	@Expose
	@ConfigOption(name = "Camera", desc = "F5 skips the front-facing view.")
	@Accordion
	public com.taz.general.CameraConfig camera = new com.taz.general.CameraConfig();

	@Expose
	@ConfigOption(name = "Render Optimizer", desc = "Stops drawing falling blocks, lightning, XP orbs, dying mobs, explosions, screen fire and more.")
	@Accordion
	public com.taz.general.RenderOptimizerConfig renderOptimizer = new com.taz.general.RenderOptimizerConfig();

	@Expose
	@ConfigOption(name = "Held Item", desc = "The size, position, turn and swing of the item in your hand.")
	@Accordion
	public com.taz.general.HeldItemConfig heldItem = new com.taz.general.HeldItemConfig();

	@Expose
	@ConfigOption(name = "Arrow Fix", desc = "No bow pull-back on shortbows.")
	@Accordion
	public com.taz.general.ArrowFixConfig arrowFix = new com.taz.general.ArrowFixConfig();

	@Expose
	@ConfigOption(name = "Block Overlay", desc = "A red box on the block you look at.")
	@Accordion
	public com.taz.general.BlockOverlayConfig blockOverlay = new com.taz.general.BlockOverlayConfig();

	@Expose
	@ConfigOption(name = "Sound Volumes", desc = "Make any sound louder or quieter.")
	@Accordion
	public com.taz.general.SoundVolumesConfig soundVolumes = new com.taz.general.SoundVolumesConfig();
}
