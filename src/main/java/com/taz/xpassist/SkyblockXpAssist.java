package com.taz.xpassist;

import com.taz.util.KeyWatcher;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.hud.XpHud;
import com.taz.hud.TazHud;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.npc.SkyblockLocation;
import com.taz.xpassist.progress.ProgressDetector;
import com.taz.xpassist.screen.NpcFinderScreen;
import com.taz.xpassist.screen.PlanScreen;
import com.taz.xpassist.screen.XpListScreen;
import com.taz.xpassist.util.Chat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/** The SkyBlock XP feature of Taz: cheapest XP list, level plan, NPC tracker and HUD. */
public final class SkyblockXpAssist {
	public static final String MOD_ID = "taz";
	public static final Logger LOGGER = LoggerFactory.getLogger("Taz/XP Assist");

	private static Supplier<Screen> pendingScreen;
	private static final KeyWatcher OPEN_LIST = new KeyWatcher();
	private static final KeyWatcher NPC_FINDER = new KeyWatcher();
	private static final KeyWatcher TOGGLE_HUD = new KeyWatcher();
	private static final KeyWatcher UNTRACK = new KeyWatcher();
	private static final KeyWatcher PLAN = new KeyWatcher();
	/** Settings that need a re-download or a re-price when they change in the settings GUI. */
	private static String lastDataSettings;
	private static String lastPriceSettings;

	/** Called once from Taz at startup, after the config is loaded. */
	public static void init() {
		ProgressDetector.init();

		ClientTickEvents.END_CLIENT_TICK.register(SkyblockXpAssist::tick);
		TazHud.register(new XpHud());
		LevelRenderEvents.END_MAIN.register(NpcTracker::drawMarker);

		lastDataSettings = dataSettings();
		lastPriceSettings = priceSettings();
		if (ModConfig.get().enabled) DataManager.refresh(false, null);
	}

	/** The SkyBlock XP feature's master switch (settings > SkyBlock XP > XP Assist > Enabled). */
	public static boolean enabled() {
		return ModConfig.get().enabled;
	}

	/**
	 * After the settings are saved: a new API key, profile or data source re-downloads your data;
	 * new pricing options or plan length recalculate the list.
	 */
	public static void onSettingsSaved() {
		if (!enabled()) {
			lastDataSettings = dataSettings();
			lastPriceSettings = priceSettings();
			return;
		}
		String data = dataSettings();
		String price = priceSettings();
		if (!data.equals(lastDataSettings)) {
			XpActions.refresh();
		} else if (!price.equals(lastPriceSettings)) {
			DataManager.recompute();
			DataManager.replan();
		}
		lastDataSettings = data;
		lastPriceSettings = price;
	}

	private static String dataSettings() {
		ModConfig c = ModConfig.get();
		return String.join("\n", String.valueOf(c.enabled), c.profile.hypixelApiKey, c.profile.profileName, String.valueOf(c.profile.useEliteBot), c.profile.eliteApiBase,
			c.dataSources.hypixelApiBase, c.dataSources.pricesUrl, c.dataSources.neuRepoOwner, c.dataSources.neuRepoName, c.dataSources.neuRepoBranch, c.dataSources.rulesUrl);
	}

	private static String priceSettings() {
		ModConfig c = ModConfig.get();
		return c.xpList.bazaarMode + " " + c.xpList.useNpcShops + " " + c.xpList.useCraftCosts + " " + c.xpList.showUnpriced + " " + c.hud.planLevels;
	}

	/** Opens a screen next tick, so it is not closed again by the chat screen a command was typed in. */
	public static void openScreen(Supplier<Screen> screen) {
		pendingScreen = screen;
	}

	private static void tick(Minecraft minecraft) {
		if (pendingScreen != null) {
			Supplier<Screen> screen = pendingScreen;
			pendingScreen = null;
			minecraft.setScreen(screen.get());
		}
		SkyblockLocation.tick(minecraft); // also used by the Galatea tree pointer, so it runs even when XP Assist is off
		if (!enabled()) {
			if (NpcTracker.target() != null) NpcTracker.untrack();
			return;
		}
		ModConfig.Keys keys = ModConfig.get().keys;
		if (OPEN_LIST.pressed(minecraft, keys.openList)) {
			minecraft.setScreen(new XpListScreen());
		}
		if (NPC_FINDER.pressed(minecraft, keys.npcFinder)) {
			minecraft.setScreen(new NpcFinderScreen(null));
		}
		if (TOGGLE_HUD.pressed(minecraft, keys.toggleHud)) {
			ModConfig config = ModConfig.get();
			config.hud.hudEnabled = !config.hud.hudEnabled;
			ModConfig.save();
			Chat.info("HUD " + (config.hud.hudEnabled ? "enabled" : "disabled") + ".");
		}
		if (UNTRACK.pressed(minecraft, keys.untrack)) {
			NpcTracker.untrack();
		}
		if (PLAN.pressed(minecraft, keys.plan)) {
			minecraft.setScreen(new PlanScreen(null));
		}
		ProgressDetector.tick(minecraft);
		NpcTracker.tick(minecraft);
		// Only refresh prices/profile in the background while you are actually playing SkyBlock.
		if (minecraft.level != null && SkyblockLocation.onSkyblock()) {
			DataManager.tick();
		}
	}
}
