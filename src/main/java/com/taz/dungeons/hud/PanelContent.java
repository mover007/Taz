package com.taz.dungeons.hud;

import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.predict.Predictor;
import com.taz.dungeons.predict.ScoreCalculator;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunTracker;
import com.taz.dungeons.run.SessionStats;
import com.taz.dungeons.util.LagMonitor;
import com.taz.dungeons.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static com.taz.dungeons.config.ModConfig.on;
import static com.taz.dungeons.hud.HudLine.AQUA;
import static com.taz.dungeons.hud.HudLine.DARK_GRAY;
import static com.taz.dungeons.hud.HudLine.GOLD;
import static com.taz.dungeons.hud.HudLine.GRAY;
import static com.taz.dungeons.hud.HudLine.GREEN;
import static com.taz.dungeons.hud.HudLine.RED;
import static com.taz.dungeons.hud.HudLine.WHITE;
import static com.taz.dungeons.hud.HudLine.YELLOW;

/** Builds the rows for each panel from the live run, or sample rows for the editor preview. Each row has its own toggle. */
public final class PanelContent {
	private PanelContent() {
	}

	public static List<HudLine> lines(HudPanel panel, RunTracker t, Predictor.Prediction p, ScoreCalculator.Score score) {
		if (panel == HudPanel.SESSION) return session(t); // also shown between runs
		if (t.floor() == null || p == null || t.state() == RunTracker.State.IDLE) return List.of();
		return switch (panel) {
			case TIMER -> timer(t, p);
			case SPLITS -> splits(p, t);
			case SCORE -> score(t, score);
			case SESSION -> session(t);
		};
	}

	/** Runs since the game started on the current (or last played) floor. */
	private static List<HudLine> session(RunTracker t) {
		Floor f = t.floor() != null ? t.floor() : SessionStats.lastFloor();
		SessionStats.FloorSession s = SessionStats.get(f);
		if (s == null || s.runs() == 0) return List.of();
		List<HudLine> out = new ArrayList<>();
		out.add(new HudLine("Session", GOLD, f.displayName() + " - " + s.runs() + " run" + (s.runs() == 1 ? "" : "s"), GRAY));
		out.add(HudLine.kv("Average", TextUtil.formatTimeTenths(s.averageMs()), WHITE));
		out.add(HudLine.kv("Best", TextUtil.formatTimeTenths(s.bestMs), AQUA));
		out.add(HudLine.kv("Last", TextUtil.formatTimeTenths(s.lastMs), WHITE));
		if (s.scored > 0) out.add(HudLine.kv("S+", s.sPlus + "/" + s.scored, s.sPlus == s.scored ? GREEN : YELLOW));
		long err = s.meanEstimateErrorMs();
		if (err >= 0) out.add(HudLine.kv("Estimate error", String.format("+/-%.1fs", err / 1000.0), GREEN));
		return out;
	}

	private static List<HudLine> timer(RunTracker t, Predictor.Prediction p) {
		List<HudLine> out = new ArrayList<>();
		Floor floor = t.floor();
		if (on(Feature.HUD_TITLE)) {
			out.add(new HudLine("Run Timer", GOLD, floor.displayName() + (t.isDemo() ? " (demo)" : ""), GRAY));
		}
		OptionalLong pb = RunHistory.personalBest(floor, ModConfig.get().historyCutoffMillis());

		switch (t.state()) {
			case WAITING -> {
				if (on(Feature.HUD_ELAPSED)) out.add(HudLine.of("Waiting for run start...", GRAY));
				if (on(Feature.HUD_LIVE_ETA)) {
					out.add(HudLine.kv("Expected", "~" + TextUtil.formatTime(p.endMs()) + rangeIfOn(p), WHITE));
				}
				if (on(Feature.HUD_PB)) pb.ifPresent(v -> out.add(HudLine.kv("PB", TextUtil.formatTimeTenths(v), AQUA)));
			}
			case RUNNING -> {
				if (on(Feature.HUD_ELAPSED)) out.add(HudLine.kv("Elapsed", TextUtil.formatTimeTenths(t.elapsedMs()), WHITE));
				if (on(Feature.HUD_PORTAL_END) && t.predictedAtBossMs() > 0) {
					out.add(HudLine.kv("End time", TextUtil.formatTimeTenths(t.predictedAtBossMs()), AQUA));
				}
				if (on(Feature.HUD_LIVE_ETA)) {
					int etaColor = pb.isPresent() && p.endMs() < pb.getAsLong() ? GREEN : YELLOW;
					out.add(HudLine.kv("Live ETA", TextUtil.formatTime(p.endMs()) + rangeIfOn(p), etaColor));
				}
				if (on(Feature.HUD_BEST_POSSIBLE) && p.bestEndMs() >= 0) {
					out.add(HudLine.kv("Best possible", TextUtil.formatTimeTenths(p.bestEndMs()), AQUA));
				}
				if (on(Feature.HUD_PHASE)) {
					String phase = p.splits().isEmpty() ? "" : p.splits().get(p.splits().size() - 1).name();
					double prog = t.terminalProgress();
					if (prog >= 0) {
						int[] c = t.terminalCount();
						phase += " " + (int) prog + "/4" + (c != null ? " - " + c[0] + "/" + c[1] : "");
					}
					if (!phase.isEmpty()) out.add(HudLine.kv("Phase", phase, WHITE));
				}
				if (on(Feature.HUD_DEATHS) && t.totalDeaths() > 0) {
					out.add(HudLine.kv("Deaths", (t.bossReached() ? t.bossDeaths() + " in boss / " : "") + t.totalDeaths(), RED));
				}
				if (on(Feature.HUD_PACE)) {
					out.add(HudLine.kv("Pace", String.format("%.2fx", p.pace()), p.pace() <= 1.0 ? GREEN : RED));
				}
				if (on(Feature.HUD_PB)) pb.ifPresent(v -> out.add(HudLine.kv("PB", TextUtil.formatTimeTenths(v)
						+ "  " + TextUtil.formatDelta(p.endMs() - v), AQUA)));
				if (on(Feature.HUD_LAG)) addLagLine(out);
			}
			case FINISHED -> {
				long fin = t.elapsedMs();
				if (on(Feature.HUD_RESULT)) {
					out.add(HudLine.kv("Final", TextUtil.formatTimeTenths(fin), GREEN));
					if (t.predictedAtBossMs() > 0) {
						long err = fin - t.predictedAtBossMs();
						out.add(HudLine.kv("Portal estimate", TextUtil.formatTimeTenths(t.predictedAtBossMs())
								+ "  " + TextUtil.formatDelta(err), Math.abs(err) <= 5_000 ? GREEN : YELLOW));
					}
				}
				if (on(Feature.HUD_BEST_POSSIBLE) && t.bestEndAtBossMs() > 0) {
					out.add(HudLine.kv("Best possible at portal", TextUtil.formatTimeTenths(t.bestEndAtBossMs())
							+ "  " + TextUtil.formatDelta(fin - t.bestEndAtBossMs()), AQUA));
				}
				if (on(Feature.HUD_PB)) pb.ifPresent(v -> out.add(v >= fin
						? HudLine.kv("PB", "New PB!", AQUA)
						: HudLine.kv("PB", TextUtil.formatTimeTenths(v), AQUA)));
			}
			default -> {
			}
		}
		if (on(Feature.HUD_LEARNING) && p.learning()) {
			out.add(HudLine.of("Learning: " + p.historyRuns() + "/" + Predictor.MIN_RUNS_FOR_CONFIDENCE + " runs on this floor", DARK_GRAY));
		}
		return out;
	}

	private static String rangeIfOn(Predictor.Prediction p) {
		return on(Feature.HUD_ETA_RANGE)
				? " (" + TextUtil.formatTime(p.lowMs()) + "-" + TextUtil.formatTime(p.highMs()) + ")"
				: "";
	}

	private static List<HudLine> splits(Predictor.Prediction p, RunTracker t) {
		if (p.splits().isEmpty()) return List.of();
		List<HudLine> out = new ArrayList<>();
		out.add(HudLine.of("Splits", GOLD));
		for (Predictor.SplitLine s : p.splits()) {
			if (s.live()) {
				out.add(new HudLine("> " + s.name(), WHITE,
						TextUtil.formatTimeTenths(s.actualMs()) + " / ~" + TextUtil.formatTime(s.expectedMs()), WHITE));
			} else {
				long d = s.actualMs() - s.expectedMs();
				out.add(new HudLine(s.name(), GRAY,
						TextUtil.formatTimeTenths(s.actualMs()) + " " + TextUtil.formatDelta(d), d <= 0 ? GREEN : RED));
			}
		}
		ModConfig.TimerHud cfg = ModConfig.get().timer;
		if (cfg.witherDoors) out.add(HudLine.kv("Wither doors", String.valueOf(com.taz.dungeons.run.RunExtras.witherDoors()), GRAY));
		if (cfg.score300 && com.taz.dungeons.run.RunExtras.score300Ms() >= 0) {
			out.add(HudLine.kv("300 score", TextUtil.formatTime(com.taz.dungeons.run.RunExtras.score300Ms()), WHITE));
		}
		long lag = com.taz.dungeons.run.RunExtras.lagLostMs(t);
		if (cfg.lagLost && lag >= 1000) out.add(HudLine.kv("Lag lost", "+" + lag / 1000 + "s", RED));
		return out;
	}

	private static List<HudLine> score(RunTracker t, ScoreCalculator.Score sc) {
		if (sc == null || t.state() == RunTracker.State.WAITING) return List.of();
		List<HudLine> out = new ArrayList<>();
		Floor floor = t.floor();
		RunTracker.Stats s = t.stats;
		boolean finished = t.state() == RunTracker.State.FINISHED;

		if (finished && t.score() >= 0) {
			out.add(new HudLine("Score", GOLD, t.score() + " " + ScoreCalculator.rank(t.score()), rankColor(t.score())));
			return out;
		}
		String gap = sc.total() >= ScoreCalculator.S_PLUS ? "" : "  (S+ -" + (ScoreCalculator.S_PLUS - sc.total()) + ")";
		if (on(Feature.SCORE_LIVE) && sc.live() >= 0) {
			out.add(new HudLine("Score (live)", GOLD, sc.live() + " " + ScoreCalculator.rank(sc.live()), rankColor(sc.live())));
		}
		if (on(Feature.SCORE_PROJECTED) && !finished) {
			out.add(HudLine.kv("Projected final", sc.total() + " " + sc.rank() + gap, rankColor(sc.total())));
		}
		if (!sc.hasData()) {
			out.add(HudLine.of("Waiting for tab list data...", DARK_GRAY));
			return out;
		}

		if (on(Feature.SCORE_SECRETS)) {
			double needPct = floor.secretsRequired * 100;
			String secrets = String.format("%.1f%% / %.0f%%", Math.max(0, s.secretsPct), needPct);
			out.add(HudLine.kv("Secrets", secrets, sc.secretsPctMissing() <= 0 ? GREEN : YELLOW));
		}
		if (on(Feature.SCORE_CRYPTS)) {
			out.add(HudLine.kv("Crypts", Math.max(0, s.crypts) + "/5", sc.cryptsMissing() == 0 ? GREEN : YELLOW));
		}
		if (on(Feature.SCORE_PUZZLES) && s.puzzlesTotal > 0) {
			String puzzles = s.puzzlesDone + "/" + s.puzzlesTotal + (s.puzzlesFailed > 0 ? "  " + s.puzzlesFailed + " failed" : "");
			out.add(HudLine.kv("Puzzles", puzzles, s.puzzlesFailed > 0 ? RED : (s.puzzlesDone == s.puzzlesTotal ? GREEN : WHITE)));
		}
		if (on(Feature.SCORE_DEATHS)) {
			out.add(HudLine.kv("Deaths", Integer.toString(Math.max(0, s.deaths)), s.deaths > 0 ? RED : GREEN));
		}
		if (on(Feature.SCORE_BONUS)) {
			String bonus = (s.mimicKilled ? "Mimic " : "") + (s.princeKilled ? "Prince " : "") + (on(Feature.PAUL) ? "Paul" : "");
			if (!bonus.isBlank()) out.add(HudLine.kv("Bonus", bonus.trim(), GREEN));
		}
		if (on(Feature.SCORE_TIME_LIMIT)) {
			if (sc.overTimeMs() > 0) {
				out.add(HudLine.kv("Over time limit", "+" + TextUtil.formatTime(sc.overTimeMs()) + " (speed " + sc.speed() + ")", RED));
			} else {
				out.add(HudLine.kv("Time limit", TextUtil.formatTime(floor.speedLimitMs())
						+ " (" + TextUtil.formatTime(-sc.overTimeMs()) + " spare)", GREEN));
			}
		}
		return out;
	}

	/** Shown only while the server lags: estimates are already stretched by the TPS drop. */
	private static void addLagLine(List<HudLine> out) {
		long since = LagMonitor.millisSinceLastPacket();
		if (since > 1_500) {
			out.add(HudLine.kv("Server", "not responding " + String.format("%.1fs", since / 1000.0), RED));
		} else if (LagMonitor.isLagging()) {
			out.add(HudLine.kv("Server lag", String.format("%.1f TPS", LagMonitor.tps()), RED));
		}
	}

	private static int rankColor(int total) {
		if (total >= ScoreCalculator.S_PLUS) return GREEN;
		if (total >= ScoreCalculator.S) return YELLOW;
		return RED;
	}

	/** Sample rows for the editor when no run is active: one per enabled line, so the panel can be sized and placed. */
	public static List<HudLine> preview(HudPanel panel) {
		List<HudLine> out = new ArrayList<>();
		switch (panel) {
			case TIMER -> {
				if (on(Feature.HUD_TITLE)) out.add(new HudLine("Run Timer", GOLD, "Master 6", GRAY));
				if (on(Feature.HUD_ELAPSED)) out.add(HudLine.kv("Elapsed", "1:52.4", WHITE));
				if (on(Feature.HUD_PORTAL_END)) out.add(HudLine.kv("End time", "2:41.3", AQUA));
				if (on(Feature.HUD_LIVE_ETA)) out.add(HudLine.kv("Live ETA", "2:41" + (on(Feature.HUD_ETA_RANGE) ? " (2:37-2:46)" : ""), GREEN));
				if (on(Feature.HUD_BEST_POSSIBLE)) out.add(HudLine.kv("Best possible", "2:34.2", AQUA));
				if (on(Feature.HUD_PHASE)) out.add(HudLine.kv("Phase", "Terminals 2/4 - 5/7", WHITE));
				if (on(Feature.HUD_DEATHS)) out.add(HudLine.kv("Deaths", "1 in boss / 2", RED));
				if (on(Feature.HUD_PACE)) out.add(HudLine.kv("Pace", "0.96x", GREEN));
				if (on(Feature.HUD_PB)) out.add(HudLine.kv("PB", "2:26.0  +15.0s", AQUA));
				if (on(Feature.HUD_LAG)) out.add(HudLine.kv("Server lag", "14.2 TPS", RED));
				if (out.isEmpty()) out.add(HudLine.of("(all timer lines off)", DARK_GRAY));
			}
			case SCORE -> {
				if (on(Feature.SCORE_LIVE)) out.add(new HudLine("Score (live)", GOLD, "302 S+", GREEN));
				if (on(Feature.SCORE_PROJECTED)) out.add(HudLine.kv("Projected final", "305 S+", GREEN));
				if (on(Feature.SCORE_SECRETS)) out.add(HudLine.kv("Secrets", "100.0% / 100%", GREEN));
				if (on(Feature.SCORE_CRYPTS)) out.add(HudLine.kv("Crypts", "5/5", GREEN));
				if (on(Feature.SCORE_PUZZLES)) out.add(HudLine.kv("Puzzles", "3/3", GREEN));
				if (on(Feature.SCORE_DEATHS)) out.add(HudLine.kv("Deaths", "0", GREEN));
				if (on(Feature.SCORE_BONUS)) out.add(HudLine.kv("Bonus", "Mimic Prince", GREEN));
				if (on(Feature.SCORE_TIME_LIMIT)) out.add(HudLine.kv("Time limit", "10:00 (7:19 spare)", GREEN));
				if (out.isEmpty()) out.add(HudLine.of("(all score lines off)", DARK_GRAY));
			}
			case SESSION -> {
				out.add(new HudLine("Session", GOLD, "Master 7 - 12 runs", GRAY));
				out.add(HudLine.kv("Average", "5:21.4", WHITE));
				out.add(HudLine.kv("Best", "4:58.0", AQUA));
				out.add(HudLine.kv("Last", "5:13.0", WHITE));
				out.add(HudLine.kv("Estimate error", "+/-6.2s", GREEN));
			}
			case SPLITS -> {
				out.add(HudLine.of("Splits", GOLD));
				out.add(new HudLine("Blood Rush", GRAY, "0:24.1 -3.9s", GREEN));
				out.add(new HudLine("Blood", GRAY, "0:52.0 -3.0s", GREEN));
				out.add(new HudLine("Finish Clear", GRAY, "0:11.2 -5.8s", GREEN));
				out.add(new HudLine("> Terracottas", WHITE, "0:19.6 / ~0:39", WHITE));
			}
		}
		return out;
	}
}
