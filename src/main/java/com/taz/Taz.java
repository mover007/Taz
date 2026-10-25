package com.taz;

import com.taz.config.ConfigSafety;
import com.taz.config.Migration;
import com.taz.config.TazConfig;
import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.util.TextUtil;
import com.taz.fishing.FishingHelper;
import com.taz.foraging.FairySouls;
import com.taz.foraging.GalateaRecorder;
import com.taz.foraging.HoneyhiveRenderer;
import com.taz.foraging.HoneyhiveTracker;
import com.taz.foraging.IslandGuide;
import com.taz.foraging.MoongladeResources;
import com.taz.foraging.PressureWarning;
import com.taz.foraging.ProtectedTrees;
import com.taz.foraging.RouteAssist;
import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.secrets.RoomSecretTimes;
import com.taz.dungeons.secrets.SecretTrainer;
import com.taz.dungeons.skillissue.SkillIssue;
import com.taz.dungeons.util.DungeonParty;
import com.taz.foraging.RubyVeilshrooms;
import com.taz.foraging.StarlynContest;
import com.taz.foraging.SweepDetails;
import com.taz.foraging.TreeGifts;
import com.taz.foraging.TreeProgress;
import com.taz.foraging.TreeRenderer;
import com.taz.foraging.TreeTracker;
import com.taz.foraging.WhisperHud;
import com.taz.foraging.WhisperTracker;
import com.taz.general.Alerts;
import com.taz.general.ChatFilter;
import com.taz.general.CommandKeys;
import com.taz.general.DarkAuctionReminder;
import com.taz.general.EventTimers;
import com.taz.general.SkillTracker;
import com.taz.general.Waypoints;
import com.taz.hud.TazHud;
import com.taz.hud.TazHudEditor;
import com.taz.inventory.PriceTooltips;
import com.taz.slayer.SlayerHelper;
import com.taz.util.KeyWatcher;
import com.taz.util.TazChat;
import com.taz.xpassist.SkyblockXpAssist;
import com.taz.xpassist.npc.SkyblockLocation;
import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Taz: one mod for all of mTaz's SkyBlock features. Each feature lives in its own package
 * (dungeons, foraging, xpassist) and has a category in the settings GUI (/taz), which is
 * MoulConfig, the same GUI SkyHanni uses. Settings are in config/taz/config.json.
 */
public final class Taz implements ClientModInitializer {
	public static final String MOD_ID = "taz";
	public static final Logger LOGGER = LoggerFactory.getLogger("Taz");
	public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

	private static ManagedConfig<TazConfig> config;
	private static final KeyWatcher SETTINGS_KEY = new KeyWatcher();
	private static final KeyWatcher HUD_KEY = new KeyWatcher();
	private static Supplier<Screen> pendingScreen;
	/** True once every feature has started (the first save happens before that). */
	private static boolean started;
	private static boolean openSettingsNextTick;
	private static String settingsCategory;
	/** The settings as last written to config.json, to see whether anything changed since. */
	private static String lastSavedJson;
	private static int autosaveTicks;
	/** Said in chat on joining a world: the settings file had to be restored. */
	private static String startupMessage;

	@Override
	public void onInitializeClient() {
		Path file = CONFIG_DIR.resolve("config.json");
		startupMessage = ConfigSafety.restoreIfBroken(file); // never fall back to defaults over a damaged file
		boolean firstRun = !Files.exists(file);
		if (firstRun) Migration.copyOldData(); // before the features read their data files
		else Migration.upgradeConfigFile(file); // settings moved since this file was written
		config = ManagedConfig.create(file.toFile(), TazConfig.class);
		if (firstRun) Migration.importOldSettings(config());
		config().saveRunnables.add(Taz::onSaved);
		save();
		ConfigSafety.keepGoodCopy(file);

		TazHud.init(); // before the features, which register their HUDs with it
		DungeonPredictorClient.init();
		SkyblockXpAssist.init();
		TazHud.register(new WhisperHud());
		TazHud.register(new EventTimers());
		TazHud.register(new SkillTracker());
		TazHud.register(new TreeProgress());
		TazHud.register(new SweepDetails());
		TazHud.register(new RubyVeilshrooms());
		TazHud.register(new TreeGifts());
		TazHud.register(new RouteAssist());
		TazHud.register(new StarlynContest());
		TazHud.register(new SecretTrainer());
		ClientChunkEvents.CHUNK_LOAD.register(TreeTracker::onChunkLoad);
		ClientChunkEvents.CHUNK_UNLOAD.register(TreeTracker::onChunkUnload);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			RubyVeilshrooms.FINDER.onChunkLoad(chunk);
			MoongladeResources.LUMIES.onChunkLoad(chunk);
			MoongladeResources.LUSHLILAC.onChunkLoad(chunk);
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			RubyVeilshrooms.FINDER.onChunkUnload(chunk);
			MoongladeResources.LUMIES.onChunkUnload(chunk);
			MoongladeResources.LUSHLILAC.onChunkUnload(chunk);
		});
		LevelRenderEvents.END_MAIN.register(TreeRenderer::render);
		LevelRenderEvents.END_MAIN.register(HoneyhiveRenderer::render);
		LevelRenderEvents.END_MAIN.register(RubyVeilshrooms::render);
		LevelRenderEvents.END_MAIN.register(MoongladeResources::render);
		LevelRenderEvents.END_MAIN.register(FairySouls::render);
		LevelRenderEvents.END_MAIN.register(IslandGuide::render);
		LevelRenderEvents.END_MAIN.register(RouteAssist::render);
		LevelRenderEvents.END_MAIN.register(Waypoints::render);
		LevelRenderEvents.END_MAIN.register(SlayerHelper::render);
		LevelRenderEvents.END_MAIN.register(FishingHelper::render);
		LevelRenderEvents.END_MAIN.register(SecretTrainer::render);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) safely("Secret routes click", () -> SecretTrainer.onInteract(hit.getBlockPos(), level.getBlockState(hit.getBlockPos())));
			return InteractionResult.PASS;
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> safely("Secret routes entity", () -> SecretTrainer.onEntityGone(entity)));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> safely("Secret routes screen", () -> SecretTrainer.onScreenOpened(screen)));
		ClientReceiveMessageEvents.ALLOW_GAME.register(ChatFilter::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(SweepDetails::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(TreeGifts::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(StarlynContest::allow);
		ClientReceiveMessageEvents.MODIFY_GAME.register(Waypoints::addButton);
		ItemTooltipCallback.EVENT.register(PriceTooltips::onTooltip);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			onServerChange();
			if (startupMessage != null) TazChat.info(startupMessage);
			startupMessage = null;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			onServerChange();
			saveIfChanged();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveIfChanged());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> TazCommand.register(dispatcher));
		ClientTickEvents.END_CLIENT_TICK.register(Taz::tick);
		started = true;
	}

	public static TazConfig config() {
		return config.getInstance();
	}

	/** Saves now, and lets features react to changed settings. */
	public static void save() {
		config.saveToFile();
		onSaved();
	}

	/** After any save, including closing the settings GUI: let features react to changed settings. */
	private static void onSaved() {
		lastSavedJson = serialize();
		if (started) SkyblockXpAssist.onSettingsSaved();
	}

	/**
	 * Auto-save: writes config.json when any setting changed since the last save. Runs every second (also while
	 * the settings GUI is open), on leaving a server and on quitting, so nothing is ever lost. Features only
	 * react to the new settings when the GUI closes, so typing an API key doesn't start a download per letter.
	 */
	public static void saveIfChanged() {
		if (config == null) return;
		String json = serialize();
		if (json == null || json.equals(lastSavedJson)) return;
		try {
			config.saveToFile();
			lastSavedJson = json;
		} catch (Exception e) {
			LOGGER.error("Could not auto-save the Taz settings", e);
		}
	}

	private static String serialize() {
		try {
			return config.getMapper().serialize(config.getInstance());
		} catch (Exception e) {
			LOGGER.warn("Could not read the Taz settings to save them", e);
			return null;
		}
	}

	/** Re-reads config/taz/config.json (after editing it by hand). */
	public static void reloadConfig() {
		config.reloadFromFile();
		config.rebuildConfigProcessor();
		config().saveRunnables.add(Taz::onSaved);
		openSettings("About"); // reopen on the new settings, or edits would go to the old copy
	}

	/** Opens the settings GUI on the next tick (a command runs while the chat screen is still closing). */
	public static void openSettings(String category) {
		settingsCategory = category;
		openSettingsNextTick = true;
	}

	/** The settings GUI as a screen, opened at a category by its name (or the first one when null). */
	public static Screen settingsScreen(Screen parent, String category) {
		MoulConfigEditor<TazConfig> editor = config.getEditor();
		if (category != null) {
			for (ProcessedCategory c : editor.getAllCategories().values()) {
				if (c.getDisplayName().getText().equalsIgnoreCase(category)) {
					editor.setSelectedCategory(c);
					break;
				}
			}
		}
		return new MoulConfigScreenComponent(Component.empty(), new GuiContext(new GuiElementComponent(editor)), parent);
	}

	private static void onServerChange() {
		TreeTracker.reset();
		HoneyhiveTracker.onServerChange();
		WhisperTracker.onServerChange();
		Waypoints.onServerChange();
		RubyVeilshrooms.onServerChange();
		MoongladeResources.LUMIES.reset();
		MoongladeResources.LUSHLILAC.reset();
	}

	/** Every block that changes on the client (ClientLevelMixin), for every feature. */
	public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		TreeTracker.onBlockChanged(pos, oldState, newState);
		WhisperTracker.onBlockChanged(pos, oldState, newState);
		RubyVeilshrooms.FINDER.onBlockChanged(pos, newState);
		MoongladeResources.LUMIES.onBlockChanged(pos, newState);
		MoongladeResources.LUSHLILAC.onBlockChanged(pos, newState);
		safely("Secret routes block", () -> SecretTrainer.onBlockChanged(pos, oldState, newState));
	}

	/** Block events (ClientPacketListenerMixin): a chest lid opening. */
	public static void onBlockEvent(BlockPos pos, Block block, int type, int data) {
		safely("Secret routes block event", () -> SecretTrainer.onBlockEvent(pos, block, type, data));
	}

	/** Opens a screen on the next tick (a settings button or command runs while another screen is closing). */
	public static void openScreenNextTick(Supplier<Screen> screen) {
		pendingScreen = screen;
	}

	/**
	 * Every system chat line, straight from the packet (ClientPacketListenerMixin), for every feature.
	 * Other mods hiding a line doesn't stop Taz from seeing it.
	 */
	public static void onSystemChat(Component content, boolean overlay) {
		DungeonPredictorClient.onSystemChatPacket(content, overlay);
		if (overlay) return;
		String line = TextUtil.cleanChat(content.getString());
		safely("Honeyhive chat", () -> HoneyhiveTracker.onChat(line));
		safely("Whisper counter chat", () -> WhisperTracker.onChat(line));
		safely("Alerts", () -> Alerts.onChat(line));
		safely("Chat waypoints", () -> Waypoints.onChat(line));
		safely("Slayer chat", () -> SlayerHelper.onChat(line));
		safely("Fishing chat", () -> FishingHelper.onChat(line));
		safely("Tree progress chat", () -> TreeProgress.onChat(line));
		safely("Sweep details chat", () -> SweepDetails.onChat(line));
		safely("Fairy souls chat", () -> FairySouls.onChat(line));
		safely("Tree route chat", () -> RouteAssist.onChat(line));
		safely("Tree gifts chat", () -> TreeGifts.onChat(content, line));
		safely("Starlyn contest chat", () -> StarlynContest.onChat(line));
		safely("Protected trees chat", () -> ProtectedTrees.onChat(line));
		safely("Skill Issue chat", () -> SkillIssue.onChat(line));
	}

	/** Every action bar message (GuiMixin), for every feature. */
	public static void onActionBar(Component message) {
		String text = TextUtil.cleanChat(message.getString());
		long now = System.currentTimeMillis();
		safely("Low health alert", () -> Alerts.onActionBar(text));
		safely("Skill tracker", () -> SkillTracker.onActionBar(text, now));
		safely("Galatea recorder", () -> GalateaRecorder.onActionBar(text));
		safely("Pressure warning", () -> PressureWarning.onActionBar(text));
		safely("Secret routes", () -> SecretTrainer.onActionBar(text));
		safely("Room secret times", () -> RoomSecretTimes.onActionBar(text));
	}

	/** Runs one feature's handler; a broken feature logs its error instead of breaking the others. */
	private static void safely(String what, Runnable handler) {
		try {
			handler.run();
		} catch (Exception e) {
			LOGGER.error("{} failed", what, e);
		}
	}

	private static void tick(Minecraft mc) {
		if (SETTINGS_KEY.pressed(mc, config().about.settingsKey)) openSettings(null);
		if (HUD_KEY.pressed(mc, config().about.hudEditorKey)) openScreenNextTick(TazHudEditor::new);
		if (pendingScreen != null) {
			Supplier<Screen> screen = pendingScreen;
			pendingScreen = null;
			mc.setScreen(screen.get());
		}
		if (openSettingsNextTick) {
			openSettingsNextTick = false;
			mc.setScreen(settingsScreen(mc.screen instanceof MoulConfigScreenComponent ? null : mc.screen, settingsCategory));
		}
		try {
			TreeTracker.tick(mc, SkyblockLocation.area());
		} catch (Exception e) {
			LOGGER.error("Tree tracker update failed", e);
			TreeTracker.reset();
		}
		try {
			HoneyhiveTracker.tick(mc, SkyblockLocation.area());
		} catch (Exception e) {
			LOGGER.error("Honeyhive tracker update failed", e);
		}
		try {
			WhisperTracker.tick(mc);
		} catch (Exception e) {
			LOGGER.error("Whisper counter update failed", e);
		}
		safely("Command keys", () -> CommandKeys.tick(mc));
		safely("Event timers", EventTimers::tick);
		safely("Dark Auction reminder", DarkAuctionReminder::tick);
		safely("Skill tracker", SkillTracker::tick);
		safely("Chat waypoints", () -> Waypoints.tick(mc));
		safely("Slayer helper", () -> SlayerHelper.tick(mc));
		safely("Fishing helper", () -> FishingHelper.tick(mc));
		safely("Ruby Veilshrooms", () -> RubyVeilshrooms.tick(mc));
		safely("Tree progress", () -> TreeProgress.tick(mc));
		safely("Sea Lumies / Lushlilac", () -> MoongladeResources.tick(mc));
		safely("Island guide", () -> IslandGuide.tick(mc));
		safely("Tree gifts", TreeGifts::tick);
		safely("Tree route", () -> RouteAssist.tick(mc));
		safely("Starlyn contest", StarlynContest::tick);
		safely("Galatea recorder", () -> GalateaRecorder.tick(mc));
		safely("Dungeon rooms", () -> DungeonRooms.tick(mc));
		safely("Dungeon party", () -> DungeonParty.tick(mc));
		safely("Secret routes", () -> SecretTrainer.tick(mc));
		if (++autosaveTicks % 20 == 0) saveIfChanged();
	}
}
