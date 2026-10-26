package com.taz.gametest;

import com.taz.Taz;
import com.taz.config.Migration;
import com.taz.config.TazConfig;
import com.taz.hud.TazHudEditor;
import com.taz.dungeons.hud.HudPanel;
import com.taz.xpassist.config.ModConfig;
import io.github.notenoughupdates.moulconfig.managed.GsonMapper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens every category of the Taz settings GUI and takes a screenshot, then checks that settings
 * from the old Dungeon Run Predictor and Skyblock XP Assist config files are imported.
 */
public class SettingsGuiGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip("settings")) return;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			for (String category : new String[]{"About", "Dungeons", "Galatea", "SkyBlock XP"}) {
				ctx.runOnClient(mc -> mc.setScreen(Taz.settingsScreen(null, category)));
				ctx.waitTicks(5);
				ctx.takeScreenshot("taz-settings-" + category.toLowerCase(Locale.ROOT).replace(' ', '-'));
				if (!category.equals("About")) {
					// Click the feature's dropdown header open (first row of the options panel).
					ctx.getInput().setCursorPos(560, 200);
					ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
					ctx.waitTicks(5);
					ctx.takeScreenshot("taz-settings-" + category.toLowerCase(Locale.ROOT).replace(' ', '-') + "-open");
				}
			}
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);

			// /taz is the only command; the old /drp and /sbxp are gone.
			ctx.runOnClient(mc -> {
				var root = ClientCommands.getActiveDispatcher().getRoot();
				check("/taz registered", root.getChild("taz") != null);
				check("/drp removed", root.getChild("drp") == null);
				check("/sbxp removed", root.getChild("sbxp") == null);
			});

			// The HUD editor opens from /taz (About > Move HUDs), not from a key.
			ctx.getInput().holdKeyFor(GLFW.GLFW_KEY_RIGHT_SHIFT, 2);
			ctx.waitTicks(3);
			check("Right Shift does nothing", ctx.computeOnClient(mc -> mc.screen == null));
			ctx.runOnClient(mc -> com.taz.hud.TazHud.openEditor());
			ctx.waitTicks(3);
			check("Move HUDs opens the editor", ctx.computeOnClient(mc -> mc.screen instanceof TazHudEditor));
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);

			// /taz reopens where you left it.
			ctx.runOnClient(mc -> mc.setScreen(Taz.settingsScreen(null, "Floor 7")));
			ctx.waitTicks(3);
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.setScreen(Taz.settingsScreen(null, null)));
			ctx.waitTicks(3);
			check("/taz reopens on the same page (" + ctx.computeOnClient(mc -> Taz.settingsPage()) + ")",
					"Floor 7".equals(ctx.computeOnClient(mc -> Taz.settingsPage())));
			ctx.takeScreenshot("taz-settings-reopened");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(2);
		}
		checkImport();
		checkUpgrade();
	}

	private static void checkImport() {
		Path config = FabricLoader.getInstance().getConfigDir();
		try {
			Files.createDirectories(config.resolve("dungeonpredictor"));
			Files.writeString(config.resolve("dungeonpredictor/config.json"), """
					{"configVersion":3,"enabled":true,"features":{"HUD_PB":true,"CHAT_PORTAL":false,"CHAT_PARTY_END":true},
					 "panels":{"score":{"x":0.3,"y":0.4,"scale":1.5,"visible":true}},
					 "resultSeconds":45,"historyCutoff":"2026-09-01"}""", StandardCharsets.UTF_8);
			Files.createDirectories(config.resolve("skyblockxpassist"));
			Files.writeString(config.resolve("skyblockxpassist/config.json"), """
					{"hypixelApiKey":"test-key","hudX":120,"hudScale":1.5,"bazaarMode":"BUY_ORDER","trackerColor":16711680,
					 "hiddenTasks":["MINION_WHEAT_1"],"planLevels":8}""", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new AssertionError("could not write old configs", e);
		}

		TazConfig cfg = new TazConfig();
		Migration.importOldSettings(cfg);
		check("HUD_PB imported", cfg.dungeons.runPredictor.timer.pb);
		check("CHAT_PORTAL off imported", !cfg.dungeons.runPredictor.chat.portal);
		check("party end time imported", cfg.dungeons.runPredictor.party.end);
		check("untouched feature keeps its default", cfg.dungeons.runPredictor.timer.elapsed);
		check("score panel shown", cfg.dungeons.runPredictor.panelVisible(HudPanel.SCORE));
		check("score panel moved", Math.abs(cfg.dungeons.runPredictor.panel(HudPanel.SCORE).x - 0.3) < 1e-9 && cfg.dungeons.runPredictor.panel(HudPanel.SCORE).scale == 1.5f);
		check("result seconds", cfg.dungeons.runPredictor.advanced.resultSeconds == 45);
		check("history cutoff", "2026-09-01".equals(cfg.dungeons.runPredictor.advanced.historyCutoff));
		check("API key", "test-key".equals(cfg.skyblockXp.xpAssist.profile.hypixelApiKey));
		check("XP HUD position", cfg.skyblockXp.xpAssist.hud.hudX == 120 && cfg.skyblockXp.xpAssist.hud.hudScale == 1.5f);
		check("bazaar mode", cfg.skyblockXp.xpAssist.xpList.bazaarMode == ModConfig.BazaarMode.BUY_ORDER);
		check("tracker colour", cfg.skyblockXp.xpAssist.tracker.rgb() == 0xFF0000);
		check("hidden tasks", cfg.skyblockXp.xpAssist.hiddenTasks.contains("MINION_WHEAT_1"));
		check("plan levels", cfg.skyblockXp.xpAssist.hud.planLevels == 8);
		System.out.println("[SettingsGuiGameTest] PASSED");
	}

	/** A config.json from before the dropdown layout (v1) keeps every setting after the upgrade. */
	private static void checkUpgrade() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("taz-upgrade-test");
		Path file = dir.resolve("config.json");
		try {
			Files.createDirectories(dir);
			Files.writeString(file, """
					{"about":{"settingsKey":-1},
					 "dungeons":{"enabled":true,"hudEditorKey":345,"timer":{"timerPanel":true,"pb":true},"party":{"end":true,"best":true,"core":false},
					  "toolsFloor":"F7","resultSeconds":45,"historyCutoff":"2026-09-01",
					  "panels":{"timer":{"x":0.8457,"y":0.891,"scale":1.0}}},
					 "galatea":{"pointer":false,"fig":true,"mangrove":false,"helix":true,"radius":64,"figColour":"0:255:255:194:61"},
					 "xpAssist":{"keys":{"openList":75},"hud":{"hudEnabled":false,"planLevels":21,"hudX":6,"hudY":29},
					  "priceRefreshMinutes":15,"neuRepoBranch":"main","hiddenTasks":["museum:CHICKEN_AXE"]}}""", StandardCharsets.UTF_8);
			Migration.upgradeConfigFile(file);
			String json = Files.readString(file, StandardCharsets.UTF_8);
			TazConfig cfg = new GsonMapper<>(TazConfig.class).deserialize(json);
			check("upgrade: party messages kept", cfg.dungeons.runPredictor.party.end && cfg.dungeons.runPredictor.party.best);
			check("upgrade: PB line kept", cfg.dungeons.runPredictor.timer.pb);
			check("upgrade: timer panel position kept", Math.abs(cfg.dungeons.runPredictor.panel(HudPanel.TIMER).x - 0.8457) < 1e-9);
			check("upgrade: tools floor moved", cfg.dungeons.runPredictor.tools.floor == com.taz.dungeons.dungeon.Floor.F7);
			check("upgrade: advanced moved", cfg.dungeons.runPredictor.advanced.resultSeconds == 45
					&& "2026-09-01".equals(cfg.dungeons.runPredictor.advanced.historyCutoff));
			check("upgrade: tree pointer off kept", !cfg.galatea.treePointer.enabled);
			check("upgrade: tree types and radius kept", !cfg.galatea.treePointer.mangrove && cfg.galatea.treePointer.radius == 64);
			check("upgrade: XP HUD off and plan levels kept", !cfg.skyblockXp.xpAssist.hud.hudEnabled && cfg.skyblockXp.xpAssist.hud.planLevels == 21);
			check("upgrade: XP data sources moved", cfg.skyblockXp.xpAssist.dataSources.priceRefreshMinutes == 15
					&& "main".equals(cfg.skyblockXp.xpAssist.dataSources.neuRepoBranch));
			check("upgrade: hidden upgrades kept", cfg.skyblockXp.xpAssist.hiddenTasks.contains("museum:CHICKEN_AXE"));
			check("upgrade: XP Assist on by default", cfg.skyblockXp.xpAssist.enabled);
			check("upgrade: version bumped", cfg.configVersion == TazConfig.CURRENT_VERSION);
			check("upgrade: old file backed up", Files.exists(dir.resolve("config.json.bak-v1")));
		} catch (java.io.IOException e) {
			throw new AssertionError("could not write the v1 config", e);
		}
		System.out.println("[SettingsGuiGameTest] upgrade PASSED");
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("settings import: " + what);
		System.out.println("[SettingsGuiGameTest] ok: " + what);
	}
}
