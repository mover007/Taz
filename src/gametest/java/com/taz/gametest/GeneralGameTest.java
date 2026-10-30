package com.taz.gametest;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.taz.Taz;
import com.taz.config.ConfigSafety;
import com.taz.config.Migration;
import com.taz.config.TazConfig;
import com.taz.fishing.FishingHelper;
import com.taz.general.AlertsConfig;
import com.taz.general.ChatFilter;
import com.taz.general.CommandKeysConfig;
import com.taz.general.DarkAuctionConfig;
import com.taz.general.DarkAuctionReminder;
import com.taz.general.EventTimers;
import com.taz.general.EventTimersConfig;
import com.taz.general.SkillTracker;
import com.taz.general.SkillTrackerConfig;
import com.taz.general.SkyblockEvent;
import com.taz.general.Waypoints;
import com.taz.general.pv.Networth;
import com.taz.general.pv.PlayerViewerScreen;
import com.taz.general.pv.SkyCrypt;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TazHudEditor;
import com.taz.hud.TextHud;
import com.taz.inventory.ItemOverlays;
import com.taz.inventory.ItemPrices;
import com.taz.slayer.SlayerHelper;
import com.taz.util.Alert;
import com.taz.xpassist.data.Prices;
import io.github.notenoughupdates.moulconfig.managed.GsonMapper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/** The General, Inventory, Slayer and Fishing features and settings auto-save. */
public class GeneralGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"defaults", "autosave", "alerts", "chatfilter", "commandkeys", "events", "darkauction", "skills", "waypoints", "huds",
			"prices", "overlays", "slayer", "fishing", "networth", "pv"};
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;
	/** Every chat line that reached the chat (after the chat filter and the [Waypoint] button). */
	private final List<String> shown = new CopyOnWriteArrayList<>();
	/** Set when another mod's /pv (a stand-in for Skyblocker's) runs instead of Taz's. */
	private static volatile boolean otherPv;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "general");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Stream.of(PARTS).noneMatch(GeneralGameTest::runs)) {
			System.out.println("[TestFilter] skipped general");
			return;
		}
		ctx = context;
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) shown.add(message.getString());
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> shown.add(message.getString()));
		// Another mod's /pv, registered like Skyblocker's: Taz's has to win.
		if (runs("pv")) ClientCommandRegistrationCallback.EVENT.register((d, b) -> d.register(ClientCommands.literal("pv")
				.then(ClientCommands.argument("username", StringArgumentType.string()).executes(c -> {
					otherPv = true;
					return 1;
				}))));
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			if (runs("defaults")) defaults();
			// Every feature starts off; switch on the ones tested here.
			ctx.runOnClient(mc -> {
				var g = Taz.config().general;
				g.alerts.enabled = g.darkAuction.enabled = g.eventTimers.enabled = g.skillTracker.enabled = true;
				Taz.config().chat.chatFilter.enabled = Taz.config().chat.waypoints.enabled = Taz.config().controls.commandKeys.enabled = true;
				Taz.config().inventory.prices.enabled = Taz.config().inventory.overlays.enabled = true;
				Taz.config().slayer.helper.enabled = Taz.config().fishing.helper.enabled = true;
			});
			if (runs("autosave")) autosave();
			if (runs("alerts")) alerts();
			if (runs("chatfilter")) chatFilter();
			if (runs("commandkeys")) commandKeys();
			if (runs("events")) events();
			if (runs("darkauction")) darkAuction();
			if (runs("skills")) skills();
			if (runs("waypoints")) waypoints();
			if (runs("huds")) huds();
			if (runs("prices")) prices();
			if (runs("overlays")) overlays();
			if (runs("slayer")) slayer();
			if (runs("fishing")) fishing();
			if (runs("networth")) networth();
			if (runs("pv")) playerViewer();
		}
		System.out.println("[GeneralGameTest] PASSED");
	}

	// ---- every feature starts off

	private void defaults() {
		TazConfig fresh = new TazConfig();
		List<String> on = new java.util.ArrayList<>();
		int features = 0;
		try {
			for (java.lang.reflect.Field category : TazConfig.class.getFields()) {
				Object c = category.get(fresh);
				if (c == null || category.getType().isPrimitive()) continue;
				try { // a big feature is a category of its own
					boolean enabled = c.getClass().getField("enabled").getBoolean(c);
					features++;
					if (enabled) on.add(category.getName());
				} catch (NoSuchFieldException notAFeature) {
					// a category of features
				}
				for (java.lang.reflect.Field feature : c.getClass().getFields()) {
					Object f = feature.get(c);
					if (f == null) continue;
					try {
						java.lang.reflect.Field enabled = f.getClass().getField("enabled");
						features++;
						if (enabled.getBoolean(f)) on.add(category.getName() + "." + feature.getName());
					} catch (NoSuchFieldException notAFeature) {
						// not a feature dropdown
					}
				}
			}
		} catch (IllegalAccessException e) {
			throw new AssertionError(e);
		}
		check("defaults: all " + features + " features start off (on: " + on + ")", features >= 16 && on.isEmpty());

		try {
			Path dir = Taz.CONFIG_DIR.resolveSibling("taz-defaults-test");
			Files.createDirectories(dir);
			// A version 3 file: today's new features were saved on, older ones as you set them.
			Path v3 = dir.resolve("v3.json");
			Files.writeString(v3, """
					{"configVersion":3,"general":{"alerts":{"enabled":true,"rareDrops":true},"commandKeys":{"enabled":false}},
					 "inventory":{"prices":{"enabled":true}},"slayer":{"helper":{"enabled":true}},"fishing":{"helper":{"enabled":true}},
					 "galatea":{"whispers":{"enabled":true},"honeyhives":{"enabled":true}},"dungeons":{"runPredictor":{"enabled":true}}}""",
					StandardCharsets.UTF_8);
			Migration.upgradeConfigFile(v3);
			TazConfig up = new GsonMapper<>(TazConfig.class).deserialize(Files.readString(v3, StandardCharsets.UTF_8));
			check("defaults: new features switched off once", !up.general.alerts.enabled && !up.inventory.prices.enabled
					&& !up.slayer.helper.enabled && !up.fishing.helper.enabled && up.general.alerts.rareDrops);
			check("defaults: features you had on stay on", up.foraging.whispers.enabled && up.galatea.honeyhives.enabled
					&& up.runPredictor.enabled);
			// A version 4 file: every feature that got a new category keeps its settings there.
			Path v4 = dir.resolve("v4.json");
			Files.writeString(v4, """
					{"configVersion":4,"general":{"alerts":{"enabled":true},"chatCommands":{"enabled":true,"taz":false},"slotBinds":{"enabled":true},
					 "heldItem":{"enabled":true,"scale":0.5},"storageOverlay":{"enabled":true}},
					 "dungeons":{"starredMobs":{"enabled":true},"secrets":{"enabled":true},"puzzles":{"enabled":true},"map":{"enabled":true}},
					 "galatea":{"honeyhives":{"enabled":true},"route":{"enabled":true},"safari":{"enabled":true,"floorDropRange":55}}}""",
					StandardCharsets.UTF_8);
			Migration.upgradeConfigFile(v4);
			TazConfig v5 = new GsonMapper<>(TazConfig.class).deserialize(Files.readString(v4, StandardCharsets.UTF_8));
			check("defaults: a v4 file's settings follow their features to the new categories", v5.general.alerts.enabled
					&& v5.chat.chatCommands.enabled && !v5.chat.chatCommands.taz && v5.controls.slotBinds.enabled && v5.visuals.heldItem.enabled
					&& v5.visuals.heldItem.scale == 0.5f && v5.inventory.storageOverlay.enabled && v5.dungeons.starredMobs.enabled
					&& v5.secretRoutes.enabled && v5.puzzles.enabled && v5.dungeonMap.map.enabled && v5.galatea.honeyhives.enabled
					&& v5.foraging.route.enabled && v5.safari.enabled && v5.safari.floorDropRange == 55);
			// A version 1 file (no on/off flags): what you used then stays on.
			Path v1 = dir.resolve("v1.json");
			Files.writeString(v1, """
					{"dungeons":{"timer":{"pb":true}},"galatea":{"fig":true},"xpAssist":{"hud":{"planLevels":8}}}""", StandardCharsets.UTF_8);
			Migration.upgradeConfigFile(v1);
			TazConfig old = new GsonMapper<>(TazConfig.class).deserialize(Files.readString(v1, StandardCharsets.UTF_8));
			check("defaults: a v1 file keeps Run Predictor, tree pointer and XP Assist on", old.runPredictor.enabled
					&& old.foraging.treePointer.enabled && old.skyblockXp.xpAssist.enabled && !old.general.alerts.enabled);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	// ---- settings auto-save

	private void autosave() {
		ctx.runOnClient(mc -> mc.setScreen(Taz.settingsScreen(null, "General")));
		ctx.waitTicks(5);
		ctx.takeScreenshot("general-settings");
		// What the GUI does when you move a slider: set the field. It must reach the file without closing the GUI.
		ctx.runOnClient(mc -> AlertsConfig.get().lowHealthPercent = 40);
		ctx.waitTicks(30);
		check("autosave: saved while the settings GUI is still open", savedLowHealth() == 40);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> AlertsConfig.get().lowHealthPercent = 25);
		ctx.waitTicks(30);
		check("autosave: saved after a change outside the GUI", savedLowHealth() == 25);
		check("autosave: last good copy kept", Files.exists(Taz.CONFIG_DIR.resolve("config.json.last-good")));

		try {
			Path dir = Taz.CONFIG_DIR.resolveSibling("taz-safety-test");
			if (Files.exists(dir)) try (Stream<Path> old = Files.list(dir)) {
				for (Path f : old.toList()) Files.delete(f);
			}
			Files.createDirectories(dir);
			Path file = dir.resolve("config.json");
			String good = Files.readString(Taz.CONFIG_DIR.resolve("config.json"), StandardCharsets.UTF_8);
			Files.writeString(file, good, StandardCharsets.UTF_8);
			ConfigSafety.keepGoodCopy(file);
			Files.writeString(file, good.substring(0, good.length() / 2), StandardCharsets.UTF_8); // the game died mid-write
			String message = ConfigSafety.restoreIfBroken(file);
			check("autosave: damaged file put back from the good copy (" + message + ")", message != null && message.contains("last good copy")
					&& Files.readString(file, StandardCharsets.UTF_8).equals(good));
			try (Stream<Path> files = Files.list(dir)) {
				check("autosave: damaged file kept", files.anyMatch(f -> f.getFileName().toString().startsWith("config.json.broken-")));
			}
			check("autosave: a good file is left alone", ConfigSafety.restoreIfBroken(file) == null);
			Files.delete(dir.resolve("config.json.last-good"));
			Files.writeString(file, "{\"general\":", StandardCharsets.UTF_8);
			message = ConfigSafety.restoreIfBroken(file);
			check("autosave: no good copy says so", message != null && message.contains("defaults") && !Files.exists(file));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static int savedLowHealth() {
		try {
			return JsonParser.parseString(Files.readString(Taz.CONFIG_DIR.resolve("config.json"), StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("general").getAsJsonObject("alerts").get("lowHealthPercent").getAsInt();
		} catch (Exception e) {
			return -1;
		}
	}

	// ---- alerts

	private void alerts() {
		clearAlert();
		tell("CRAZY RARE DROP! Judgement Core (+312% ✯ Magic Find)");
		check("alerts: crazy rare drop (" + Alert.lastTitle + " / " + Alert.lastSubtitle + ")", Alert.lastTitle != null
				&& Alert.lastTitle.contains("CRAZY RARE DROP!") && Alert.lastSubtitle.contains("Judgement Core") && !Alert.lastSubtitle.contains("Magic Find"));
		ctx.takeScreenshot("general-alert-drop");

		ctx.runOnClient(mc -> AlertsConfig.get().dropTier = AlertsConfig.DropTier.CRAZY_RARE);
		clearAlert();
		tell("RARE DROP! Enchanted Bone");
		check("alerts: plain rare drop skipped when only crazy rare is picked", Alert.lastTitle == null);
		ctx.runOnClient(mc -> AlertsConfig.get().dropTier = AlertsConfig.DropTier.ALL);
		tell("RARE DROP! Enchanted Bone");
		check("alerts: plain rare drop", Alert.lastTitle != null && Alert.lastTitle.contains("RARE DROP!"));

		tell("[Bazaar] Your Buy Order for 64x Enchanted Iron was filled!");
		check("alerts: bazaar order", Alert.lastTitle.contains("BUY ORDER FILLED") && Alert.lastSubtitle.contains("64x Enchanted Iron"));
		tell("[Auction] Steve bought Hyperion for 900,000,000 coins CLICK");
		check("alerts: auction sold", Alert.lastTitle.contains("AUCTION SOLD") && Alert.lastSubtitle.contains("Hyperion"));
		tell("[MVP+] Steve has invited you to join their party!");
		check("alerts: party invite", Alert.lastTitle.contains("PARTY INVITE") && Alert.lastSubtitle.contains("Steve"));
		clearAlert();
		tell("Party > [MVP+] Steve: RARE DROP! just kidding");
		check("alerts: a player typing RARE DROP! isn't a drop", Alert.lastTitle == null);
		actionBar("1,000/5,000❤     100/100✎ Mana");
		check("alerts: low health", Alert.lastTitle != null && Alert.lastTitle.contains("LOW HEALTH"));
		clearAlert();
		actionBar("900/5,000❤     100/100✎ Mana");
		check("alerts: low health only once until you heal", Alert.lastTitle == null);
		// Hypixel's resource pack draws ❤ as an icon now.
		actionBar("5,000/5,000     100/100");
		actionBar("1,000/5,000     100/100");
		check("alerts: low health with Hypixel's heart icon", Alert.lastTitle != null && Alert.lastTitle.contains("LOW HEALTH"));
	}

	// ---- chat filter

	private void chatFilter() {
		int before = ChatFilter.hidden;
		List<String> spam = List.of(">>> [MVP++] Steve joined the lobby! <<<", "[WATCHDOG ANNOUNCEMENT]",
				"Watchdog has banned 5,123 players in the last 7 days.", "Sending to server mini12A...",
				"Your Implosion hit 3 enemies for 1,234,567.8 damage.", "There are blocks in the way!", "+5 Kill Combo +3% ✯ Magic Find");
		for (String line : spam) tell(line);
		tell("Hello, this one stays");
		tell("Friend > Steve joined.");
		int hidden = ChatFilter.hidden - before;
		check("chat filter: spam hidden (" + hidden + " of " + spam.size() + ")", hidden == spam.size());
		check("chat filter: normal lines and friend joins (off by default) stay", shown.contains("Hello, this one stays")
				&& shown.contains("Friend > Steve joined.") && shown.stream().noneMatch(s -> s.contains("WATCHDOG")));
		ctx.takeScreenshot("general-chat-filter");
	}

	// ---- command keys

	private void commandKeys() {
		ctx.runOnClient(mc -> {
			CommandKeysConfig cfg = CommandKeysConfig.get();
			cfg.slot1.key = GLFW.GLFW_KEY_KP_1;
			cfg.slot1.command = "/me pressed a Taz key";
			cfg.slot2.key = GLFW.GLFW_KEY_KP_2;
			cfg.slot2.command = "hello from a Taz key";
		});
		ctx.getInput().holdKeyFor(GLFW.GLFW_KEY_KP_1, 2);
		ctx.waitTicks(10);
		ctx.getInput().holdKeyFor(GLFW.GLFW_KEY_KP_2, 2);
		ctx.waitTicks(10);
		check("command keys: command run", shown.stream().anyMatch(s -> s.contains("pressed a Taz key")));
		check("command keys: chat message sent", shown.stream().anyMatch(s -> s.contains("hello from a Taz key")));
		ctx.runOnClient(mc -> {
			CommandKeysConfig cfg = CommandKeysConfig.get();
			cfg.slot1.key = GLFW.GLFW_KEY_UNKNOWN;
			cfg.slot1.command = "/pets";
			cfg.slot2.key = GLFW.GLFW_KEY_UNKNOWN;
			cfg.slot2.command = "/storage";
		});
	}

	// ---- event timers

	private void events() {
		long t = 1_800_000_000L;
		long da = SkyblockEvent.DARK_AUCTION.nextStart(t);
		check("events: Dark Auction at :55 (" + da + ")", da % 3600 == 3300 && da >= t - 60 && da < t + 3600);
		long jacob = SkyblockEvent.JACOBS_CONTEST.nextStart(t);
		check("events: Jacob's Contest at :15", jacob % 3600 == 900 && jacob > t - 1200 && jacob < t + 3600);
		check("events: calendar starts at Early Spring 1st, Year 1", SkyblockEvent.date(SkyblockEvent.EPOCH_S).equals("Early Spring 1st, Year 1"));
		long spooky = SkyblockEvent.EPOCH_S + SkyblockEvent.YEAR_S + 7 * SkyblockEvent.MONTH_S + 28 * SkyblockEvent.DAY_S;
		check("events: Autumn 29th, Year 2 (" + SkyblockEvent.date(spooky) + ")", SkyblockEvent.date(spooky).equals("Autumn 29th, Year 2"));
		check("events: Spooky Festival starts on it", SkyblockEvent.SPOOKY_FESTIVAL.nextStart(spooky - 100) == spooky);
		check("events: and is still on half an hour later", SkyblockEvent.SPOOKY_FESTIVAL.nextStart(spooky + 1800) == spooky);
		check("events: Cult of the Fallen Star on day 7", SkyblockEvent.STAR_CULT.nextStart(SkyblockEvent.EPOCH_S) == SkyblockEvent.EPOCH_S + 6 * SkyblockEvent.DAY_S);

		long jacobMs = jacob * 1000;
		ctx.runOnClient(mc -> {
			EventTimersConfig cfg = EventTimersConfig.get();
			cfg.enabled = true;
			cfg.date = true;
			cfg.remindMinutes = 1;
			if (!cfg.reminders.contains(SkyblockEvent.JACOBS_CONTEST)) cfg.reminders.add(SkyblockEvent.JACOBS_CONTEST);
			EventTimers.clearReminders();
			EventTimers.testNowMs = jacobMs - 59_000;
		});
		clearAlert();
		ctx.waitTicks(3);
		check("events: reminder a minute before (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("Jacob's Contest"));
		List<String> lines = ctx.computeOnClient(mc -> ((TextHud) hud(EventTimers.class)).shownLines(false));
		check("events: HUD shows Jacob's Contest in 59s first (" + lines + ")", lines.size() > 3 && lines.get(2).contains("Jacob's Contest")
				&& lines.get(2).contains("59s"));
		ctx.takeScreenshot("general-event-timers");
		clearAlert();
		ctx.waitTicks(3);
		check("events: reminded once", Alert.lastTitle == null);
		ctx.runOnClient(mc -> {
			EventTimers.testNowMs = 0;
			EventTimersConfig.get().date = false;
		});
	}

	// ---- Dark Auction reminder

	private void darkAuction() {
		long da = SkyblockEvent.DARK_AUCTION.nextStart(1_800_000_000L);
		ctx.runOnClient(mc -> {
			DarkAuctionConfig cfg = DarkAuctionConfig.get();
			cfg.enabled = true;
			cfg.minutesBefore = 3;
			cfg.atStart = true;
			// Also picked in Event Timers' reminders: it must still come only once.
			if (!EventTimersConfig.get().reminders.contains(SkyblockEvent.DARK_AUCTION)) EventTimersConfig.get().reminders.add(SkyblockEvent.DARK_AUCTION);
			EventTimers.clearReminders();
			DarkAuctionReminder.clear();
			EventTimers.testNowMs = (da - 300) * 1000;
		});
		clearAlert();
		ctx.waitTicks(3);
		check("dark auction: nothing 5 minutes before", Alert.lastTitle == null);
		ctx.runOnClient(mc -> EventTimers.testNowMs = (da - 179) * 1000);
		ctx.waitTicks(3);
		check("dark auction: reminder 3 minutes before (" + Alert.lastTitle + " / " + Alert.lastSubtitle + ")", Alert.lastTitle != null
				&& Alert.lastTitle.contains("DARK AUCTION") && Alert.lastSubtitle.contains("Dark Auction in 2m 59s!"));
		ctx.takeScreenshot("general-dark-auction");
		clearAlert();
		ctx.runOnClient(mc -> EventTimers.testNowMs = (da - 60) * 1000);
		ctx.waitTicks(3);
		check("dark auction: reminded once before it (not twice with Event Timers)", Alert.lastTitle == null);
		ctx.runOnClient(mc -> EventTimers.testNowMs = (da + 2) * 1000);
		ctx.waitTicks(3);
		check("dark auction: reminder when it starts", Alert.lastSubtitle != null && Alert.lastSubtitle.contains("starting now"));
		clearAlert();
		ctx.runOnClient(mc -> EventTimers.testNowMs = (da + 10) * 1000);
		ctx.waitTicks(3);
		check("dark auction: and only once", Alert.lastTitle == null);
		ctx.runOnClient(mc -> {
			EventTimers.testNowMs = 0;
			EventTimersConfig.get().reminders.remove(SkyblockEvent.DARK_AUCTION);
		});
	}

	// ---- HUDs without backgrounds

	private void huds() {
		ctx.setScreen(TazHudEditor::new);
		ctx.waitTicks(5);
		ctx.takeScreenshot("general-huds-editor");
		ctx.setScreen(() -> null);
		ctx.waitTicks(2);
	}

	// ---- skill tracker

	private void skills() {
		ctx.runOnClient(mc -> {
			SkillTrackerConfig.get().pauseSeconds = 3;
			SkillTracker.resetCounts();
		});
		actionBar("+10 Foraging (1,000/10,000)");
		check("skills: first gain", gained("Foraging") == 10);
		actionBar("+10 Foraging (1,000/10,000)");
		ctx.waitTicks(5);
		actionBar("+10 Foraging (1,000/10,000)");
		check("skills: the same message again isn't a new gain", gained("Foraging") == 10);
		for (int i = 1; i <= 11; i++) {
			long tick = System.currentTimeMillis();
			actionBar("+10 Foraging (" + String.format("%,d", 1000 + 10 * i) + "/10,000)");
			realWait(1000 - (System.currentTimeMillis() - tick));
		}
		check("skills: 12 gains of 10 (" + gained("Foraging") + ")", gained("Foraging") == 120);
		long active = ctx.computeOnClient(mc -> SkillTracker.activeMs("Foraging"));
		long rate = ctx.computeOnClient(mc -> SkillTracker.perHour("Foraging"));
		check("skills: XP per hour = gained / time training (" + rate + ")", rate > 0 && rate == Math.round(120 * 3_600_000.0 / active));
		List<String> lines = ctx.computeOnClient(mc -> ((TextHud) hud(SkillTracker.class)).shownLines(false));
		check("skills: HUD shows time to level up (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Level up in")));
		ctx.takeScreenshot("general-skill-tracker");
		actionBar("+10 Foraging (5/12,000)");
		check("skills: gain at a level up counts", gained("Foraging") == 130);
		actionBar("+5 Mining (12.5%)");
		actionBar("+5 Mining (12.5%)");
		check("skills: percent display", gained("Mining") == 5);
		actionBar("+5 Mining (12.6%)");
		check("skills: next percent gain", gained("Mining") == 10);
		realWait(3500);
		long stopped = ctx.computeOnClient(mc -> SkillTracker.activeMs("Foraging"));
		realWait(1500);
		check("skills: clock stops when you stop training", ctx.computeOnClient(mc -> SkillTracker.activeMs("Foraging")) == stopped);
		ctx.runOnClient(mc -> SkillTrackerConfig.get().pauseSeconds = 10);
	}

	private double gained(String skill) {
		return ctx.computeOnClient(mc -> SkillTracker.gained(skill));
	}

	// ---- chat waypoints

	private void waypoints() {
		ctx.runOnClient(mc -> Waypoints.all().clear());
		tell("Party > [MVP+] Steve: x: 10, y: 70, z: 20");
		check("waypoints: party coords become a waypoint", hasWaypoint(new BlockPos(10, 70, 20), "Steve"));
		tell("[312] [VIP] Bob: meet me at 100 65 -30");
		check("waypoints: other chat isn't added straight away", !hasWaypoint(new BlockPos(100, 65, -30), "Bob"));
		check("waypoints: it gets a [Waypoint] button", shown.stream().anyMatch(s -> s.contains("meet me at") && s.contains("[Waypoint]")));
		ctx.runOnClient(mc -> {
			try {
				ClientCommands.getActiveDispatcher().execute("taz waypoint 100 65 -30 Bob", (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		});
		check("waypoints: the button's command adds it", hasWaypoint(new BlockPos(100, 65, -30), "Bob"));
		BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + p.getZ() + " facing 10 70 20");
		ctx.waitTicks(10);
		ctx.takeScreenshot("general-waypoint");
		cmd("tp @p 10 70 23");
		ctx.waitTicks(3);
		check("waypoints: reached one goes away", !hasWaypoint(new BlockPos(10, 70, 20), "Steve") && hasWaypoint(new BlockPos(100, 65, -30), "Bob"));
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + p.getZ());
		ctx.runOnClient(mc -> Waypoints.all().clear());
	}

	private boolean hasWaypoint(BlockPos pos, String name) {
		return ctx.computeOnClient(mc -> Waypoints.all().stream().anyMatch(w -> w.pos().equals(pos) && w.name().equals(name)));
	}

	// ---- item prices and overlays

	private static ItemStack item(net.minecraft.world.level.ItemLike base, int count, String name, String id, List<String> lore, CompoundTag extra) {
		ItemStack stack = new ItemStack(base, count);
		if (name != null) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (lore != null) stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(l -> (Component) Component.literal(l)).toList()));
		CompoundTag tag = extra == null ? new CompoundTag() : extra;
		if (id != null) tag.putString("id", id);
		if (!tag.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static List<ItemStack> sampleItems() {
		CompoundTag book = new CompoundTag();
		CompoundTag enchants = new CompoundTag();
		enchants.putInt("ultimate_wise", 5);
		book.put("enchantments", enchants);
		CompoundTag pet = new CompoundTag();
		pet.putString("petInfo", "{\"type\":\"GOLDEN_DRAGON\",\"tier\":\"LEGENDARY\"}");
		return List.of(
				item(Items.IRON_SWORD, 1, "Hyperion", "HYPERION", List.of("Gear Score: 1000", "", "LEGENDARY DUNGEON SWORD"), null),
				item(Items.DIAMOND, 64, "Enchanted Diamond", "ENCHANTED_DIAMOND", List.of("", "UNCOMMON"), null),
				item(Items.ENCHANTED_BOOK, 1, "Enchanted Book", "ENCHANTED_BOOK", List.of("Ultimate Wise V", "", "COMMON"), book),
				item(Items.PLAYER_HEAD, 1, "[Lvl 100] Golden Dragon", "PET", List.of("", "LEGENDARY"), pet),
				item(Items.PLAYER_HEAD, 1, "Wheat Minion XI", "WHEAT_GENERATOR_11", List.of("", "RARE"), null),
				item(Items.GOLDEN_HOE, 1, "Farming XXV", null, List.of("Click to view!"), null),
				item(Items.CAKE, 1, "New Year Cake (Year 412)", "NEW_YEAR_CAKE", List.of("", "SPECIAL"), null),
				item(Items.GOLD_NUGGET, 1, "Pandora's Box", "PANDORAS_BOX", List.of("", "a MYTHIC ACCESSORY a"), null),
				item(Items.DIRT, 1, null, null, null, null));
	}

	private void prices() {
		ctx.runOnClient(mc -> ItemPrices.testPrices = Prices.parse(
				"{\"HYPERION\":900000000,\"ENCHANTMENT_ULTIMATE_WISE_5\":3000000,\"LVL_100_LEGENDARY_GOLDEN_DRAGON\":700000000}",
				"{\"products\":{\"ENCHANTED_DIAMOND\":{\"quick_status\":{\"buyPrice\":1700.5,\"sellPrice\":1500.2}}}}"));
		List<ItemStack> items = sampleItems();
		String sword = tooltip(items.get(0));
		check("prices: lowest BIN (" + sword.replace("\n", " | ") + ")", sword.contains("Lowest BIN: 900M"));
		String diamond = tooltip(items.get(1));
		check("prices: bazaar with the stack (" + diamond + ")", diamond.contains("Bazaar buy: 1.7K") && diamond.contains("x64"));
		check("prices: enchanted book by its enchant", tooltip(items.get(2)).contains("Lowest BIN: 3M"));
		check("prices: pet by level and rarity", tooltip(items.get(3)).contains("Lowest BIN: 700M"));
		check("prices: nothing on vanilla items", !tooltip(items.get(8)).contains("Lowest BIN") && !tooltip(items.get(8)).contains("Bazaar"));
	}

	private String tooltip(ItemStack stack) {
		return ctx.computeOnClient(mc -> String.join("\n", stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
				.stream().map(c -> net.minecraft.ChatFormatting.stripFormatting(c.getString())).toList()));
	}

	private void overlays() {
		List<ItemStack> items = sampleItems();
		check("overlays: legendary", ItemOverlays.of(items.get(0)).rarity() == ItemOverlays.Rarity.LEGENDARY);
		check("overlays: recombobulated mythic", ItemOverlays.of(items.get(7)).rarity() == ItemOverlays.Rarity.MYTHIC);
		check("overlays: no rarity on vanilla items", ItemOverlays.of(items.get(8)).rarity() == null);
		check("overlays: book level", "5".equals(ItemOverlays.of(items.get(2)).number()));
		check("overlays: pet level", "100".equals(ItemOverlays.of(items.get(3)).number()));
		check("overlays: minion tier", "11".equals(ItemOverlays.of(items.get(4)).number()));
		check("overlays: skill level in a menu", "25".equals(ItemOverlays.of(items.get(5)).number()));
		check("overlays: cake year", "412".equals(ItemOverlays.of(items.get(6)).number()));
		check("overlays: no number on a sword", ItemOverlays.of(items.get(0)).number() == null);

		cmd("gamemode survival @p");
		sp.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().get(0);
			List<ItemStack> give = sampleItems();
			for (int i = 0; i < give.size(); i++) player.getInventory().setItem(9 + i, give.get(i));
		});
		ctx.waitTicks(5);
		ctx.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		ctx.waitTicks(5);
		double[] at = ctx.computeOnClient(mc -> {
			double gs = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2, top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[]{(left + 8 + 8) * gs, (top + 84 + 8) * gs};
		});
		ctx.takeScreenshot("general-inventory-overlays");
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot("general-inventory");
		ctx.setScreen(() -> null);
		cmd("gamemode creative @p");
		cmd("clear @p");
		ctx.runOnClient(mc -> ItemPrices.testPrices = null);
	}

	// ---- slayer

	private void slayer() {
		String me = ctx.computeOnClient(mc -> mc.player.getName().getString());
		BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
		cmd("summon husk " + (p.getX() + 6) + " " + p.getY() + " " + p.getZ() + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"taztest\"]}");
		cmd("summon armor_stand " + (p.getX() + 6.0) + " " + (p.getY() + 2.3) + " " + p.getZ()
				+ " {CustomName:\"Spawned by: " + me + "\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"taztest\"]}");
		cmd("summon husk " + (p.getX() - 6) + " " + p.getY() + " " + p.getZ() + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"taztest\"]}");
		cmd("summon armor_stand " + (p.getX() - 6.0) + " " + (p.getY() + 2.3) + " " + p.getZ()
				+ " {CustomName:\"Spawned by: SomeoneElse\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"taztest\"]}");
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + (p.getZ() - 8) + " facing " + p.getX() + " " + (p.getY() + 1) + " " + p.getZ());
		clearAlert();
		ctx.waitTicks(15);
		check("slayer: boss spawned alert (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("BOSS SPAWNED"));
		Double bossX = ctx.computeOnClient(mc -> {
			Entity boss = SlayerHelper.boss();
			return boss == null ? null : boss.getX();
		});
		check("slayer: your boss (not the other player's) found", bossX != null && bossX > p.getX());
		ctx.takeScreenshot("general-slayer-boss");
		tell("  SLAYER QUEST COMPLETE!");
		// (Taz's own "[Taz] Boss killed in 0:01" line goes straight into the chat, so it isn't in shown.)
		check("slayer: kill timed", ctx.computeOnClient(mc -> SlayerHelper.kills().size() == 1 && SlayerHelper.kills().get(0) > 0));
		ctx.takeScreenshot("general-slayer-killed");
		cmd("kill @e[tag=taztest]");
		ctx.waitTicks(10);
		check("slayer: gone with its hologram", ctx.computeOnClient(mc -> SlayerHelper.boss() == null));
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + p.getZ());
	}

	// ---- fishing

	private void fishing() {
		BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
		cmd("item replace entity @p weapon.mainhand with fishing_rod");
		cmd("tp @p " + p.getX() + " " + p.getY() + " " + p.getZ() + " facing " + (p.getX() + 6) + " " + (p.getY() - 1) + " " + p.getZ());
		ctx.waitTicks(5);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		// Wait for the bobber to land (stop moving) before the "!!!" goes over it.
		Vec3 hook = null;
		for (int i = 0; i < 100; i++) {
			ctx.waitTicks(5);
			Vec3 now = ctx.computeOnClient(mc -> mc.player.fishing == null ? null : mc.player.fishing.position());
			if (now != null && hook != null && now.distanceToSqr(hook) < 1e-4) break;
			hook = now;
		}
		check("fishing: cast", hook != null);
		check("fishing: no bite yet", !ctx.computeOnClient(mc -> FishingHelper.bitten()));
		clearAlert();
		cmd(String.format(java.util.Locale.ROOT, "summon armor_stand %.2f %.2f %.2f {CustomName:\"!!!\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"taztest\"]}",
				hook.x, hook.y + 0.5, hook.z));
		ctx.waitTicks(5);
		check("fishing: bite alert", ctx.computeOnClient(mc -> FishingHelper.bitten()) && Alert.lastTitle != null && Alert.lastTitle.contains("!!!"));
		ctx.takeScreenshot("general-fishing");
		tell("It's a Double Hook! Woot woot!");
		check("fishing: double hook", Alert.lastTitle.contains("DOUBLE HOOK"));
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		cmd("kill @e[tag=taztest]");
		cmd("clear @p");
		ctx.waitTicks(5);
	}

	// ---- helpers

	/** /nw: a networth from canned SkyCrypt answers, and what it says without a key or for a player SkyCrypt doesn't have. */
	private void networth() {
		ctx.runOnClient(mc -> {
			fakeSkyCrypt();
			Taz.config().general.viewer.apiKey = "";
			mc.gui.getChat().clearMessages(false);
		});
		clientCommand("nw TazTester");
		check("networth: asks for a key", chatHas("need your own SkyCrypt API key"));
		ctx.runOnClient(mc -> Taz.config().general.viewer.apiKey = "test-key");
		clientCommand("nw TazTester");
		String nw = ctx.computeOnClient(mc -> Networth.lastShown);
		check("networth: with and without cosmetics, and unsoulbound (" + nw + ")", nw.contains("TazTester's networth · Pomegranate")
				&& nw.contains("With cosmetics: 272.94B") && nw.contains("Non-cosmetic: 118.42B") && nw.contains("Unsoulbound: 98.5B")
				&& nw.contains("Purse 2.22B · Bank 1B") && nw.contains("Data from SkyCrypt"));
		int asked = SkyCrypt.REQUESTS.get();
		clientCommand("nw TazTester");
		check("networth: kept, not asked again", SkyCrypt.REQUESTS.get() == asked);
		ctx.runOnClient(mc -> SkyCrypt.FAKE.put("embed/Nobody", new SkyCrypt.Fake(404, "{\"code\":\"NOT_CACHED\",\"detail\":\"Not cached\"}")));
		clientCommand("nw Nobody");
		check("networth: a player SkyCrypt doesn't have", chatHas("Open Nobody's page once"));
		clientCommand("nw not-a-name");
		check("networth: not a Minecraft name", chatHas("isn't a Minecraft name"));
		ctx.takeScreenshot("networth-chat");
		ctx.runOnClient(mc -> {
			SkyCrypt.FAKE.clear();
			SkyCrypt.clear();
			Taz.config().general.viewer.apiKey = "";
		});
	}

	/** /pv: the viewer opens on canned SkyCrypt answers, every tab draws, and profiles switch. */
	private void playerViewer() {
		ctx.runOnClient(mc -> {
			fakeSkyCrypt();
			Taz.config().general.viewer.apiKey = "test-key";
		});
		clientCommand("pv TazTester");
		ctx.waitTicks(5);
		check("pv: opens", ctx.computeOnClient(mc -> mc.screen instanceof PlayerViewerScreen));
		check("pv: Taz's /pv runs, not the other mod's", !otherPv);
		for (int i = 0; i < PlayerViewerScreen.TABS.length; i++) {
			int tab = i;
			ctx.runOnClient(mc -> ((PlayerViewerScreen) mc.screen).showTab(tab));
			ctx.waitTicks(tab == 3 ? 10 : 4);
			ctx.takeScreenshot("pv-" + PlayerViewerScreen.TABS[i].toLowerCase(Locale.ROOT));
		}
		check("pv: inventories loaded", ctx.computeOnClient(mc -> ((PlayerViewerScreen) mc.screen).inventoriesDone()));
		// The helmet's tooltip on the Gear tab.
		ctx.runOnClient(mc -> ((PlayerViewerScreen) mc.screen).showTab(2));
		int[] at = ctx.computeOnClient(mc -> ((PlayerViewerScreen) mc.screen).screenPos(133, 29));
		double gui = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(at[0] * gui, at[1] * gui);
		ctx.waitTicks(4);
		ctx.takeScreenshot("pv-gear-tooltip");
		ctx.runOnClient(mc -> ((PlayerViewerScreen) mc.screen).nextProfile());
		ctx.waitTicks(5);
		check("pv: switches profile", "Kiwi".equals(ctx.computeOnClient(mc -> mc.screen instanceof PlayerViewerScreen v ? v.profileName() : "")));
		check("pv: stays on the tab", ctx.computeOnClient(mc -> ((PlayerViewerScreen) mc.screen).tab() == 2));
		ctx.runOnClient(mc -> {
			mc.setScreen(null);
			SkyCrypt.clear();
			Taz.config().general.viewer.apiKey = "";
			mc.gui.getChat().clearMessages(false);
		});
		clientCommand("pv TazTester");
		check("pv: asks for a key", chatHas("need your own SkyCrypt API key"));
		ctx.runOnClient(mc -> SkyCrypt.FAKE.clear());
	}

	/** Canned SkyCrypt answers for a made-up player with two profiles (src/gametest/resources/pv). */
	private static void fakeSkyCrypt() {
		SkyCrypt.FAKE.clear();
		SkyCrypt.clear();
		String uuid = "5d1a2b3c4d5e4f60a1b2c3d4e5f60718/";
		String pomegranate = uuid + "11111111-2222-3333-4444-555555555555", kiwi = uuid + "66666666-7777-8888-9999-000000000000";
		SkyCrypt.FAKE.put("embed/TazTester", SkyCrypt.Fake.ok(fixture("embed")));
		SkyCrypt.FAKE.put("stats/TazTester", SkyCrypt.Fake.ok(fixture("stats")));
		SkyCrypt.FAKE.put("stats/TazTester/66666666-7777-8888-9999-000000000000", SkyCrypt.Fake.ok(fixture("stats-kiwi")));
		for (String at : List.of(pomegranate, kiwi)) {
			SkyCrypt.FAKE.put("combined/" + at, SkyCrypt.Fake.ok(fixture("combined")));
			SkyCrypt.FAKE.put("networth/" + at, SkyCrypt.Fake.ok(fixture("networth")));
		}
		SkyCrypt.FAKE.put("inventory/" + pomegranate, SkyCrypt.Fake.ok(fixture("inventory")));
	}

	private static String fixture(String name) {
		try (var in = GeneralGameTest.class.getResourceAsStream("/pv/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new AssertionError("fixture " + name, e);
		}
	}

	private void clientCommand(String command) {
		ctx.runOnClient(mc -> {
			try {
				ClientCommands.getActiveDispatcher().execute(command, (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
			} catch (Exception e) {
				throw new AssertionError(command, e);
			}
		});
		ctx.waitTicks(3);
	}

	/** Whether a line in the chat contains this text. */
	private boolean chatHas(String text) {
		return ctx.computeOnClient(mc -> ((com.taz.mixin.ChatComponentAccessor) mc.gui.getChat()).taz$messages().stream()
				.anyMatch(m -> m.content().getString().contains(text)));
	}

	private static MovableHud hud(Class<?> type) {
		for (MovableHud h : TazHud.all()) if (type.isInstance(h)) return h;
		throw new AssertionError(type.getSimpleName() + " not registered");
	}

	private static void clearAlert() {
		Alert.lastTitle = null;
		Alert.lastSubtitle = null;
	}

	/** A system chat line, like Hypixel sends. */
	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void actionBar(String text) {
		cmd("title @p actionbar " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void realWait(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) ctx.waitTick();
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("general: " + what);
		System.out.println("[GeneralGameTest] ok: " + what);
	}
}
