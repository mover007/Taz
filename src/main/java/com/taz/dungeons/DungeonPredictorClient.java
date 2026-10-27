package com.taz.dungeons;

import com.taz.Taz;
import com.taz.util.TazChat;
import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.dungeon.Triggers;
import com.taz.dungeons.hud.HudPanel;
import com.taz.dungeons.hud.PanelHud;
import com.taz.hud.TazHud;
import com.taz.dungeons.predict.Predictor;
import com.taz.dungeons.predict.ScoreCalculator;
import com.taz.dungeons.run.LogImporter;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunRecord;
import com.taz.dungeons.run.RunTracker;
import com.taz.dungeons.run.SessionStats;
import com.taz.dungeons.util.BossLineLog;
import com.taz.dungeons.util.Diagnostics;
import com.taz.dungeons.util.LagMonitor;
import com.taz.dungeons.util.TextUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** The Dungeons feature of Taz: run timer, end-time prediction, score and chat messages. */
public final class DungeonPredictorClient {
	public static final String MOD_ID = "taz";
	public static final Logger LOGGER = LoggerFactory.getLogger("Taz/Dungeons");

	public static final RunTracker TRACKER = new RunTracker();
	private static final DungeonState DUNGEON = new DungeonState();
	/** Read the scoreboard and tab list every 5 ticks (4x a second). */
	private static final int POLL_INTERVAL = 5;

	private static Predictor.Prediction prediction;
	private static ScoreCalculator.Score score;
	private static int tickCounter;
	/** Set once the packet-level chat hook has fired; Fabric's chat events are then ignored to avoid duplicates. */
	private static boolean mixinChatActive;
	/** Tracker version the current prediction was computed for (recompute on change or every poll). */
	private static int predictedVersion = -1;

	/** Called once from Taz at startup, after the config is loaded. */
	public static void init() {
		Triggers.load();
		RunHistory.load();
		BossLineLog.load();
		String backtest = System.getProperty("drp.backtest"); // dev: -Ddrp.backtest=M6,M7 writes backtest.txt after import
		if (backtest != null) {
			LogImporter.runAsync(() -> runStartupBacktest(backtest));
		} else if (ModConfig.on(Feature.LOG_IMPORT)) {
			LogImporter.runAsync(); // learn from past runs in the chat logs (background)
		}

		TRACKER.setOnSaved(r -> {
			SessionStats.record(r);
			postSummary(r);
		});

		ClientTickEvents.END_CLIENT_TICK.register(DungeonPredictorClient::onTick);
		// Fallback chat source in case the packet mixin can't apply. GAME_CANCELED covers lines other mods hid.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay && !mixinChatActive) onChat(message);
		});
		ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
			if (!overlay && !mixinChatActive) onChat(message);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onServerChange());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onServerChange());
		for (HudPanel panel : HudPanel.values()) TazHud.register(new PanelHud(panel));
	}

	public static Predictor.Prediction currentPrediction() {
		return prediction;
	}

	public static ScoreCalculator.Score currentScore() {
		return score;
	}

	private static boolean openStatsNextTick;

	/** Opens the stats screen on the next tick. */
	public static void openStats() {
		openStatsNextTick = true;
	}

	/** Opens the Taz settings at the Run Predictor. */
	public static void openSettings() {
		Taz.openSettings("Run Predictor");
	}

	/** Opens the HUD editor on the next tick (a command runs while the chat screen is still closing). */
	public static void openEditor() {
		TazHud.openEditor();
	}

	private static void runStartupBacktest(String floors) {
		try {
			List<Floor> list = new ArrayList<>();
			for (String id : floors.split(",")) {
				Floor f = Floor.fromId(id);
				if (f != null) list.add(f);
			}
			LOGGER.info("Backtest written to {}", com.taz.dungeons.predict.Backtester.writeReport(list.toArray(new Floor[0])));
		} catch (Exception e) {
			LOGGER.error("Backtest failed", e);
		}
		if (Boolean.getBoolean("drp.backtest.exit")) Minecraft.getInstance().stop();
	}

	private static void onServerChange() {
		TRACKER.onServerChange();
		Diagnostics.onServerChange();
		LagMonitor.reset();
	}

	/** Called from ClientPacketListenerMixin for every system chat packet, on the main thread. */
	public static void onSystemChatPacket(Component content, boolean overlay) {
		mixinChatActive = true;
		if (!overlay) onChat(content);
	}

	private static void onTick(Minecraft mc) {
		if (openStatsNextTick) {
			openStatsNextTick = false;
			mc.setScreen(new com.taz.dungeons.config.StatsScreen(null));
		}

		if (!ModConfig.get().enabled) return;
		boolean poll = ++tickCounter % POLL_INTERVAL == 0;
		if (poll) {
			try {
				DUNGEON.captureRaw = Diagnostics.wantsRaw();
				DUNGEON.update(mc);
				TRACKER.tick(DUNGEON);
				Diagnostics.tick(DUNGEON, TRACKER, mixinChatActive);
			} catch (Exception e) {
				LOGGER.error("Dungeon state update failed", e);
			}
		}
		if (TRACKER.state() == RunTracker.State.IDLE) {
			prediction = null;
			score = null;
			predictedVersion = -1;
		} else if (poll || prediction == null || predictedVersion != TRACKER.version()) {
			// The HUD's elapsed time is read live; the estimates only need refreshing a few times a second.
			predictedVersion = TRACKER.version();
			prediction = Predictor.predict(TRACKER);
			if (prediction != null) TRACKER.tracePrediction(prediction.endMs());
			score = prediction != null && TRACKER.state() != RunTracker.State.WAITING
					? ScoreCalculator.project(TRACKER, prediction.endMs()) : null;
		}
	}

	private static void onChat(Component message) {
		if (!ModConfig.get().enabled) return;
		try {
			handleChat(message);
		} catch (Exception e) {
			LOGGER.error("Failed to process chat line", e); // never break chat for other mods
		}
	}

	private static void handleChat(Component message) {
		String line = TextUtil.cleanChat(message.getString());
		if (line.isEmpty()) return;
		BossLineLog.maybeLog(line, TRACKER.floor());

		List<Milestone> reached = TRACKER.onChat(line);
		for (Milestone m : reached) {
			if (m != Milestone.START && ModConfig.on(Feature.CHAT_GOLD_SPLITS)) announceGoldSplit(m);
			if (m == Milestone.BOSS_ENTER) {
				Predictor.Prediction atBoss = Predictor.predict(TRACKER);
				if (atBoss != null) {
					TRACKER.setPredictedAtBossMs(atBoss.endMs());
					announcePortal(atBoss);
				}
			}
			if (m == Milestone.CORE) announceCore();
			if (m == Milestone.END) TRACKER.setModelScoreAtEnd(ScoreCalculator.modelAtEnd(TRACKER));
			if (ModConfig.on(Feature.CHAT_DEBUG)) {
				chat(Component.literal(m.displayName + " @ " + TextUtil.formatTimeTenths(TRACKER.elapsedMs()))
						.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** At portal entry: the end time, plus the best possible finish and PB check if those are on. */
	private static void announcePortal(Predictor.Prediction atBoss) {
		TRACKER.setBestEndAtBossMs(atBoss.bestEndMs());
		sendToParty(atBoss);
		if (!ModConfig.on(Feature.CHAT_PORTAL)) return;
		MutableComponent msg = Component.literal("Portal " + TextUtil.formatTimeTenths(TRACKER.elapsedMs()) + "  ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("End time: ").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(TextUtil.formatTimeTenths(atBoss.endMs())).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		if (ModConfig.on(Feature.CHAT_PORTAL_BEST) && atBoss.bestEndMs() >= 0) {
			msg.append(Component.literal("  Best possible: " + TextUtil.formatTimeTenths(atBoss.bestEndMs())).withStyle(ChatFormatting.DARK_AQUA));
		}
		OptionalLong pb = RunHistory.personalBest(TRACKER.floor(), ModConfig.get().historyCutoffMillis());
		if (ModConfig.on(Feature.CHAT_PORTAL_PB) && pb.isPresent() && atBoss.bestEndMs() >= 0) {
			msg.append(Component.literal(atBoss.bestEndMs() < pb.getAsLong()
					? "  PB still possible (" + TextUtil.formatTimeTenths(pb.getAsLong()) + ")"
					: "  PB out of reach (" + TextUtil.formatTimeTenths(pb.getAsLong()) + ")").withStyle(ChatFormatting.GRAY));
		}
		chat(msg);
	}

	/**
	 * Party chat at portal entry, one message per run, only for the parts that are switched on:
	 * "End time: 3:08.2 | Best possible: 2:58.0". Never in demo runs.
	 */
	private static void sendToParty(Predictor.Prediction atBoss) {
		if (TRACKER.isDemo()) return;
		List<String> parts = new ArrayList<>();
		if (ModConfig.on(Feature.CHAT_PARTY_END)) parts.add("End time: " + TextUtil.formatTimeTenths(atBoss.endMs()));
		if (ModConfig.on(Feature.CHAT_PARTY_BEST) && atBoss.bestEndMs() >= 0) {
			parts.add("Best possible: " + TextUtil.formatTimeTenths(atBoss.bestEndMs()));
		}
		if (!parts.isEmpty()) TazChat.party(String.join(" | ", parts));
	}

	/** F7/M7: an updated end time when the Core opens (about +/-8s on M7, vs +/-28s at the portal). */
	private static void announceCore() {
		if (TRACKER.isDemo()) return;
		Predictor.Prediction p = Predictor.predict(TRACKER);
		if (p == null) return;
		String end = TextUtil.formatTimeTenths(p.endMs());
		if (ModConfig.on(Feature.CHAT_CORE)) {
			chat(Component.literal("Core " + TextUtil.formatTimeTenths(TRACKER.elapsedMs()) + "  ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("End time: ").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(end).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
		}
		if (ModConfig.on(Feature.CHAT_PARTY_CORE)) TazChat.party("End time (Core): " + end);
	}

	/** "New best Terminals: 41.2s (-1.8s)" when a segment just beat every recorded run (and the portal time too). */
	private static void announceGoldSplit(Milestone m) {
		Floor floor = TRACKER.floor();
		if (floor == null || TRACKER.isDemo()) return;
		int idx = floor.indexOf(m);
		if (idx <= 0) return;
		Milestone prev = floor.splits.get(idx - 1).milestone();
		Long a = TRACKER.splitMs(prev), b = TRACKER.splitMs(m);
		if (a == null || b == null || b <= a) return;
		long dur = b - a;
		long best = Long.MAX_VALUE, bestPortal = Long.MAX_VALUE;
		int n = 0;
		for (RunRecord r : RunHistory.recent(floor, ModConfig.get().historyCutoffMillis(), Integer.MAX_VALUE)) {
			Long ra = r.split(prev), rb = r.split(m);
			if (ra != null && rb != null && rb > ra) {
				best = Math.min(best, rb - ra);
				n++;
			}
			Long rp = r.split(Milestone.BOSS_ENTER);
			if (rp != null) bestPortal = Math.min(bestPortal, rp);
		}
		if (n >= 5 && dur < best) {
			chat(Component.literal("New best " + floor.splits.get(idx - 1).segmentName() + ": ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(String.format("%.1fs", dur / 1000.0)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
					.append(Component.literal(" (" + TextUtil.formatDelta(dur - best) + ")").withStyle(ChatFormatting.GRAY)));
		}
		if (m == Milestone.BOSS_ENTER && n >= 5 && b < bestPortal) {
			chat(Component.literal("New best portal time: ").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(TextUtil.formatTimeTenths(b)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
					.append(Component.literal(" (" + TextUtil.formatDelta(b - bestPortal) + ")").withStyle(ChatFormatting.GRAY)));
		}
	}

	/** One-line recap after a run is saved (each part has its own toggle), plus an optional missed-phase warning. */
	private static void postSummary(RunRecord r) {
		Floor floor = Floor.fromId(r.floor);
		if (floor == null) return;
		OptionalLong prevPb = RunHistory.personalBestExcluding(floor, ModConfig.get().historyCutoffMillis(), r);
		boolean newPb = prevPb.isEmpty() || r.totalMs < prevPb.getAsLong();

		if (ModConfig.on(Feature.CHAT_SUMMARY)) {
			MutableComponent msg = Component.literal(floor.id + " " + TextUtil.formatTimeTenths(r.totalMs)).withStyle(ChatFormatting.WHITE);
			if (ModConfig.on(Feature.CHAT_SUMMARY_ESTIMATE) && r.predictedAtBossMs > 0) {
				msg.append(Component.literal("  portal estimate " + TextUtil.formatTimeTenths(r.predictedAtBossMs)
						+ " (" + TextUtil.formatDelta(r.totalMs - r.predictedAtBossMs) + ")").withStyle(ChatFormatting.GRAY));
			}
			if (ModConfig.on(Feature.CHAT_SUMMARY_BEST) && r.bestEndAtBossMs > 0) {
				msg.append(Component.literal("  best possible was " + TextUtil.formatTimeTenths(r.bestEndAtBossMs)
						+ " (" + TextUtil.formatDelta(r.totalMs - r.bestEndAtBossMs) + ")").withStyle(ChatFormatting.DARK_AQUA));
			}
			if (ModConfig.on(Feature.CHAT_SUMMARY_SCORE) && r.score >= 0) {
				msg.append(Component.literal("  " + ScoreCalculator.rank(r.score) + " " + r.score)
						.withStyle(r.score >= ScoreCalculator.S_PLUS ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			}
			if (ModConfig.on(Feature.CHAT_PB) && newPb) {
				msg.append(Component.literal("  New PB!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
			}
			chat(msg);
		} else if (ModConfig.on(Feature.CHAT_PB) && newPb) {
			chat(Component.literal(floor.id + " " + TextUtil.formatTimeTenths(r.totalMs) + "  New PB!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		}

		if (ModConfig.on(Feature.CHAT_MISSED)) {
			List<String> missed = new ArrayList<>();
			for (Milestone m : TRACKER.missedMilestones()) {
				if (m != Milestone.START && m != Milestone.END) missed.add(m.displayName);
			}
			if (!missed.isEmpty()) {
				chat(Component.literal("Not detected this run: " + String.join(", ", missed)
						+ ". Boss lines were saved to config/taz/dungeons/boss-lines.log.").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}

	public static void chat(Component msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.sendSystemMessage(TazChat.prefix().append(msg));
	}
}
