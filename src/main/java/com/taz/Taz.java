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
	private static Supplier<Screen> pendingScreen;
	/** True once every feature has started (the first save happens before that). */
	private static boolean started;
	private static boolean openSettingsNextTick;
	/** The settings GUI, kept for the session so /taz reopens on the same page, scrolled to the same spot. */
	private static MoulConfigEditor<TazConfig> editor;
	private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
	/** Developer tools (debug files, logs, backtests): only with -Dtaz.dev=true. */
	public static final boolean DEV = Boolean.getBoolean("taz.dev");
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
		TazHud.register(new com.taz.general.PerformanceHud());
		TazHud.register(new com.taz.dungeons.info.Blessings());
		TazHud.register(new com.taz.dungeons.info.MaskTimers());
		TazHud.register(new com.taz.dungeons.info.RoomAlerts());
		TazHud.register(new com.taz.dungeons.f7.TickTimers());
		TazHud.register(new com.taz.dungeons.f7.LeapCounter());
		TazHud.register(new com.taz.general.PetDisplay());
		TazHud.register(new com.taz.general.SpringBoots());
		TazHud.register(new com.taz.general.KickTimer());
		TazHud.register(new com.taz.dungeons.info.BloodCamp());
		TazHud.register(new com.taz.dungeons.puzzles.Quiz());
		TazHud.register(new com.taz.dungeons.f7.WitherDragons());
		TazHud.register(new com.taz.dungeons.map.DungeonMap());
		TazHud.register(new com.taz.dungeons.map.MapInfo.Score());
		TazHud.register(new com.taz.dungeons.map.MapInfo.RoomSecrets());
		TazHud.register(new com.taz.foraging.safari.SafariGuide());
		ClientChunkEvents.CHUNK_LOAD.register(TreeTracker::onChunkLoad);
		ClientChunkEvents.CHUNK_UNLOAD.register(TreeTracker::onChunkUnload);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			RubyVeilshrooms.FINDER.onChunkLoad(chunk);
			com.taz.foraging.safari.SafariSpots.BEDS.onChunkLoad(chunk);
			com.taz.foraging.safari.SafariSpots.LILY_PADS.onChunkLoad(chunk);
			com.taz.foraging.safari.SafariSpots.WHEAT.onChunkLoad(chunk);
			MoongladeResources.LUMIES.onChunkLoad(chunk);
			MoongladeResources.LUSHLILAC.onChunkLoad(chunk);
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			RubyVeilshrooms.FINDER.onChunkUnload(chunk);
			com.taz.foraging.safari.SafariSpots.BEDS.onChunkUnload(chunk);
			com.taz.foraging.safari.SafariSpots.LILY_PADS.onChunkUnload(chunk);
			com.taz.foraging.safari.SafariSpots.WHEAT.onChunkUnload(chunk);
			MoongladeResources.LUMIES.onChunkUnload(chunk);
			MoongladeResources.LUSHLILAC.onChunkUnload(chunk);
		});
		LevelRenderEvents.END_MAIN.register(TreeRenderer::render);
		LevelRenderEvents.END_MAIN.register(HoneyhiveRenderer::render);
		LevelRenderEvents.END_MAIN.register(RubyVeilshrooms::render);
		LevelRenderEvents.END_MAIN.register(MoongladeResources::render);
		LevelRenderEvents.END_MAIN.register(FairySouls::render);
		LevelRenderEvents.END_MAIN.register(IslandGuide::render);
		LevelRenderEvents.END_MAIN.register(com.taz.foraging.safari.SafariHelper::render);
		LevelRenderEvents.END_MAIN.register(RouteAssist::render);
		LevelRenderEvents.END_MAIN.register(Waypoints::render);
		LevelRenderEvents.END_MAIN.register(SlayerHelper::render);
		LevelRenderEvents.END_MAIN.register(FishingHelper::render);
		LevelRenderEvents.END_MAIN.register(SecretTrainer::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.info.TerracottaTimer::render);
		LevelRenderEvents.END_MAIN.register(com.taz.general.SpringBoots::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.highlight.DoorsAndKeys::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.highlight.SecretClicked::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.info.BloodCamp::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.info.MageBeam::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.f7.TerminalHighlight::render);
		LevelRenderEvents.END_MAIN.register(com.taz.general.EtherwarpGuess::render);
		LevelRenderEvents.END_MAIN.register(com.taz.general.GyroWand::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.puzzles.Puzzles::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.f7.SimonSays::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.f7.ArrowAlign::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.f7.WitherDragons::render);
		LevelRenderEvents.END_MAIN.register(com.taz.general.BlockOverlay::render);
		LevelRenderEvents.END_MAIN.register(com.taz.dungeons.map.MapInfo::render);
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !com.taz.general.BlockOverlay.replaces());
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_CHAT.register(com.taz.general.ChatTweaks::emoji);
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(command ->
				com.taz.general.ChatTweaks.emojiCommand(com.taz.general.CommandShortcuts.rewrite(command)));
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) safely("Terminal highlight click", () -> com.taz.dungeons.f7.TerminalHighlight.onClick(entity));
			if (level.isClientSide() && hand == net.minecraft.world.InteractionHand.MAIN_HAND) safely("Arrow align click", () -> com.taz.dungeons.f7.ArrowAlign.onClick(entity));
			if (level.isClientSide()) safely("Safari floor drop", () -> com.taz.foraging.safari.SafariFinds.onInteract(entity.position()));
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) safely("Terminal highlight click", () -> com.taz.dungeons.f7.TerminalHighlight.onClick(entity));
			if (level.isClientSide()) safely("Safari floor drop", () -> com.taz.foraging.safari.SafariFinds.onInteract(entity.position()));
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide()) safely("Safari floor drop", () -> com.taz.foraging.safari.SafariFinds.onInteract(net.minecraft.world.phys.Vec3.atCenterOf(pos)));
			return net.minecraft.world.InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide()) safely("Safari floor drop", () -> com.taz.foraging.safari.SafariFinds.onInteract(hit.getLocation()));
			if (level.isClientSide()) safely("Secret routes click", () -> SecretTrainer.onInteract(hit.getBlockPos(), level.getBlockState(hit.getBlockPos())));
			if (level.isClientSide()) safely("Secret clicked", () -> com.taz.dungeons.highlight.SecretClicked.onInteract(hit.getBlockPos(), level.getBlockState(hit.getBlockPos())));
			if (level.isClientSide()) safely("Puzzle click", () -> com.taz.dungeons.puzzles.Puzzles.onUseBlock(hit.getBlockPos()));
			if (level.isClientSide()) safely("Simon says click", () -> com.taz.dungeons.f7.SimonSays.onUseBlock(hit.getBlockPos()));
			return InteractionResult.PASS;
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> safely("Secret routes entity", () -> SecretTrainer.onEntityGone(entity)));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> safely("Secret routes screen", () -> SecretTrainer.onScreenOpened(screen)));
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> safely("Auction price input", () -> com.taz.general.AuctionInput.onScreen(screen)));
		ClientReceiveMessageEvents.ALLOW_GAME.register(ChatFilter::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(SweepDetails::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(TreeGifts::allow);
		ClientReceiveMessageEvents.ALLOW_GAME.register(StarlynContest::allow);
		ClientReceiveMessageEvents.MODIFY_GAME.register(Waypoints::addButton);
		ItemTooltipCallback.EVENT.register(PriceTooltips::onTooltip);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> safely("Party finder tooltip", () -> com.taz.dungeons.info.PartyFinder.tooltip(stack, lines)));
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
		editor = null;
		config().saveRunnables.add(Taz::onSaved);
		openSettings("About"); // reopen on the new settings, or edits would go to the old copy
	}

	/** Opens the settings GUI on the next tick (a command runs while the chat screen is still closing). */
	public static void openSettings(String category) {
		settingsCategory = category;
		openSettingsNextTick = true;
	}

	/** The settings page open (or last open), by name. */
	public static String settingsPage() {
		if (editor == null || editor.getSelectedCategory() == null) return null;
		ProcessedCategory c = editor.getAllCategories().get(editor.getSelectedCategory());
		return c == null ? null : c.getDisplayName().getText();
	}

	/** The settings GUI as a screen, opened at a category by its name (or where you left it when null). */
	public static Screen settingsScreen(Screen parent, String category) {
		if (editor == null) editor = config.getEditor();
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
		com.taz.foraging.safari.SafariSpots.BEDS.onBlockChanged(pos, newState);
		com.taz.foraging.safari.SafariSpots.LILY_PADS.onBlockChanged(pos, newState);
		com.taz.foraging.safari.SafariSpots.WHEAT.onBlockChanged(pos, newState);
		MoongladeResources.LUMIES.onBlockChanged(pos, newState);
		MoongladeResources.LUSHLILAC.onBlockChanged(pos, newState);
		safely("Secret routes block", () -> SecretTrainer.onBlockChanged(pos, oldState, newState));
		safely("Terracotta timer", () -> com.taz.dungeons.info.TerracottaTimer.onBlockChanged(pos, oldState, newState));
		safely("Puzzles block", () -> com.taz.dungeons.puzzles.Puzzles.onBlockChanged(pos, oldState, newState));
		safely("Simon says block", () -> com.taz.dungeons.f7.SimonSays.onBlockChanged(pos, oldState, newState));
		safely("Wither dragons block", () -> com.taz.dungeons.f7.WitherDragons.onBlockChanged(pos, oldState, newState));
	}

	/** Every server tick (Hypixel's ping packet, ClientCommonPacketListenerImplMixin): the clock for tick timers. */
	public static void onServerTick() {
		safely("Mask timers", com.taz.dungeons.info.MaskTimers::onServerTick);
		safely("Tick timers", com.taz.dungeons.f7.TickTimers::onServerTick);
		safely("Terracotta timer", com.taz.dungeons.info.TerracottaTimer::onServerTick);
		safely("Blood camp", com.taz.dungeons.info.BloodCamp::onServerTick);
		safely("Puzzles", com.taz.dungeons.puzzles.Puzzles::onServerTick);
		safely("Simon says", com.taz.dungeons.f7.SimonSays::onServerTick);
		safely("Wither dragons", com.taz.dungeons.f7.WitherDragons::onServerTick);
	}

	/** An entity event (ClientPacketListenerMixin): 3 is a mob's death. */
	public static void onEntityEvent(net.minecraft.world.entity.Entity entity, byte id) {
		safely("Score alerts", () -> com.taz.dungeons.info.ScoreAlerts.onEntityEvent(entity, id));
		safely("Secret clicked", () -> com.taz.dungeons.highlight.SecretClicked.onEntityEvent(entity, id));
		safely("Puzzles", () -> com.taz.dungeons.puzzles.Puzzles.onEntityEvent(entity, id));
	}

	/** An item entity picked up by someone (ClientPacketListenerMixin), before it's removed. */
	public static void onItemTaken(net.minecraft.world.entity.Entity item, int collector) {
		safely("Secret clicked", () -> com.taz.dungeons.highlight.SecretClicked.onItemTaken(item, collector));
	}

	/** The server moved you (ClientPacketListenerMixin): a teleport pad. */
	public static void onTeleport(net.minecraft.world.phys.Vec3 pos, float yaw, float pitch) {
		safely("Teleport maze", () -> com.taz.dungeons.puzzles.Puzzles.onTeleport(pos, yaw, pitch));
	}

	/** A particle the server sends (ClientPacketListenerMixin); true hides it. */
	public static boolean onParticle(net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z, float dx, float dy, float dz,
			float speed, int count) {
		boolean[] hide = {false};
		safely("Wither dragons", () -> com.taz.dungeons.f7.WitherDragons.onParticle(particle, x, y, z, dx, dy, dz, speed, count));
		safely("Gyro wand", () -> com.taz.general.GyroWand.onParticle(particle, x, y, z, dx, dy, dz));
		safely("Mage beam", () -> hide[0] |= com.taz.dungeons.info.MageBeam.onParticle(particle, x, y, z));
		safely("Render optimizer", () -> hide[0] |= com.taz.general.RenderOptimizer.hideParticle(particle));
		return hide[0];
	}

	/** The tab list's footer (ClientPacketListenerMixin): the dungeon's blessings. */
	public static void onTabFooter(String footer) {
		safely("Blessings", () -> com.taz.dungeons.info.Blessings.onTabFooter(footer));
	}

	/** Every sound the server plays (ClientPacketListenerMixin): its id ("entity.wolf.death") and pitch. */
	public static boolean onSound(String id, float pitch) {
		safely("Ragnarock", () -> com.taz.general.Ragnarock.onSound(id, pitch));
		safely("Spring boots", () -> com.taz.general.SpringBoots.onSound(id, pitch));
		return com.taz.dungeons.f7.TerminalSolver.muteSound(id, pitch) | com.taz.general.EtherwarpGuess.onSound(id, pitch);
	}

	/** Block events (ClientPacketListenerMixin): a chest lid opening. */
	public static void onBlockEvent(BlockPos pos, Block block, int type, int data) {
		safely("Secret routes block event", () -> SecretTrainer.onBlockEvent(pos, block, type, data));
		safely("Puzzle done", () -> com.taz.dungeons.puzzles.Puzzles.onBlockEvent(pos, block));
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
		safely("Party chat", () -> TazChat.onChat(line));
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
		safely("Safari helper chat", () -> com.taz.foraging.safari.SafariHelper.onChat(line));
		safely("Skill Issue chat", () -> SkillIssue.onChat(line));
		safely("Mask timers chat", () -> com.taz.dungeons.info.MaskTimers.onChat(line));
		safely("Score alerts chat", () -> com.taz.dungeons.info.ScoreAlerts.onChat(line));
		safely("Tick timers chat", () -> com.taz.dungeons.f7.TickTimers.onChat(line));
		safely("Ragnarock chat", () -> com.taz.general.Ragnarock.onChat(line));
		safely("Pet display chat", () -> com.taz.general.PetDisplay.onChat(content.getString(), line));
		safely("Kick timer chat", () -> com.taz.general.KickTimer.onChat(line));
		safely("Doors and keys chat", () -> com.taz.dungeons.highlight.DoorsAndKeys.onChat(line));
		safely("Secret clicked chat", () -> com.taz.dungeons.highlight.SecretClicked.onChat(line));
		safely("Blood camp chat", () -> com.taz.dungeons.info.BloodCamp.onChat(line));
		safely("Puzzles chat", () -> com.taz.dungeons.puzzles.Puzzles.onChat(line));
		safely("Party", () -> com.taz.util.Party.onChat(line));
		safely("Run extras", () -> com.taz.dungeons.run.RunExtras.onChat(line));
		safely("Chat commands", () -> com.taz.general.ChatCommands.onChat(line));
		safely("Chat tweaks", () -> com.taz.general.ChatTweaks.onChat(line));
		safely("Auto requeue", () -> com.taz.dungeons.info.AutoRequeue.onChat(line));
		safely("Leap menu", () -> com.taz.dungeons.info.LeapMenu.onChat(line));
		safely("Party finder", () -> com.taz.dungeons.info.PartyFinder.onChat(line));
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
		safely("Room alerts", () -> com.taz.dungeons.info.RoomAlerts.onActionBar(text));
		safely("Dungeon map", () -> com.taz.dungeons.map.DungeonMap.onActionBar(text));
		safely("Map info", () -> com.taz.dungeons.map.MapInfo.onActionBar(text));
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
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			com.taz.dungeons.util.LagMonitor.reset();
		}
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
		safely("Safari helper", () -> com.taz.foraging.safari.SafariHelper.tick(mc));
		safely("Tree gifts", TreeGifts::tick);
		safely("Tree route", () -> RouteAssist.tick(mc));
		safely("Starlyn contest", StarlynContest::tick);
		safely("Galatea recorder", () -> GalateaRecorder.tick(mc));
		safely("Party chat", TazChat::tick);
		safely("Dungeon location", () -> com.taz.dungeons.util.DungeonLocation.tick(mc));
		safely("Dungeon rooms", () -> DungeonRooms.tick(mc));
		safely("Performance HUD", () -> com.taz.general.PerformanceHud.tick(mc));
		safely("Mask timers", () -> com.taz.dungeons.info.MaskTimers.tick(mc));
		safely("Score alerts", () -> com.taz.dungeons.info.ScoreAlerts.tick(mc));
		safely("Tick timers", () -> com.taz.dungeons.f7.TickTimers.tick(mc));
		safely("Leap counter", () -> com.taz.dungeons.f7.LeapCounter.tick(mc));
		safely("Spring boots", () -> com.taz.general.SpringBoots.tick(mc));
		safely("Pet display", () -> com.taz.general.PetDisplay.tick(mc));
		safely("Dungeon party", () -> DungeonParty.tick(mc));
		safely("Starred mobs", () -> com.taz.dungeons.highlight.StarredMobs.tick(mc));
		safely("Doors and keys", () -> com.taz.dungeons.highlight.DoorsAndKeys.tick(mc));
		safely("Blood camp", () -> com.taz.dungeons.info.BloodCamp.tick(mc));
		safely("Terminal highlight", () -> com.taz.dungeons.f7.TerminalHighlight.tick(mc));
		safely("Door fix", () -> com.taz.dungeons.f7.DoorFix.tick(mc));
		safely("Puzzles", () -> com.taz.dungeons.puzzles.Puzzles.tick(mc));
		safely("Terminal solver", () -> com.taz.dungeons.f7.TerminalSolver.tick(mc));
		safely("Arrow align", () -> com.taz.dungeons.f7.ArrowAlign.tick(mc));
		safely("Wither dragons", () -> com.taz.dungeons.f7.WitherDragons.tick(mc));
		safely("Chat commands", com.taz.general.ChatCommands::tick);
		safely("Tweaks", () -> com.taz.general.Tweaks.tick(mc));
		safely("Run extras", () -> com.taz.dungeons.run.RunExtras.tick(mc));
		safely("Dungeon map", () -> com.taz.dungeons.map.DungeonMap.tick(mc));
		safely("Map info", () -> com.taz.dungeons.map.MapInfo.tick(mc));
		safely("Storage overlay", () -> com.taz.general.StorageOverlay.tick(mc));
		safely("Auto requeue", () -> com.taz.dungeons.info.AutoRequeue.tick(mc));
		safely("Secret routes", () -> SecretTrainer.tick(mc));
		if (++autosaveTicks % 20 == 0) saveIfChanged();
	}
}
