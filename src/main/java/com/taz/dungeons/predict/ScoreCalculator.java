package com.taz.dungeons.predict;

import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunRecord;
import com.taz.dungeons.run.RunTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dungeon score. Hypixel shows its own live score on the sidebar ("Cleared: 100% (304)"), and that number is used
 * as-is.
 */
public final class ScoreCalculator {
	public static final int S_PLUS = 300;
	public static final int S = 270;
	/** Clear % at boss that ends up counted as all rooms. */
	private static final double FULL_CLEAR_AT_BOSS = 0.93;

	public record Score(
			/** Projected final score (or the official one once the run is over). */
			int total,
			/** Hypixel's live score from the sidebar, or -1. */
			int live,
			int skill, int explore, int speed, int bonus,
			String rank,
			/** Secret % (of the floor total) still needed for full secret points, 0 if done. */
			double secretsPctMissing,
			int cryptsMissing,
			/** How far the predicted time is over the speed limit, or <= 0 if under. */
			long overTimeMs,
			/** False when no score data has been read yet. */
			boolean hasData) {
	}

	private ScoreCalculator() {
	}

	public static Score project(RunTracker t, long predictedEndMs) {
		Floor floor = t.floor();
		RunTracker.Stats s = t.stats;
		ModConfig cfg = ModConfig.get();
		boolean finished = t.state() == RunTracker.State.FINISHED;
		boolean late = finished || t.bossReached() || s.clearPct >= 95;

		double roomsNow = s.clearPct >= 0 ? s.clearPct / 100.0 : 0;
		double roomsAtBoss = t.bossReached() || finished
				? Math.max(0, t.clearPctAtBoss()) / 100.0
				: Math.max(roomsNow, Predictor.typicalClearAtBoss(floor, cfg) / 100.0);
		double roomsFinal = Math.min(1.0, Math.max(roomsNow, roomsAtBoss / FULL_CLEAR_AT_BOSS));

		double secretsNow = Math.max(0, s.secretsPct) / 100.0;
		double secretsFinal = secretsNow;
		if (!late && roomsNow >= 0.2 && roomsFinal > roomsNow) {
			secretsFinal = Math.min(1.0, secretsNow * roomsFinal / roomsNow); // secrets keep pace with rooms
		}

		int[] fin = model(floor, s, cfg, roomsFinal, secretsFinal, predictedEndMs, true);
		int projected = fin[4];

		// Calibrate against Hypixel's own number once it's meaningful (late in the run).
		int offset;
		if (late && s.liveScore >= 0) {
			int[] cur = model(floor, s, cfg, roomsNow, secretsNow, t.elapsedMs(), false);
			offset = s.liveScore - cur[4];
		} else {
			offset = historyOffset(floor, cfg);
		}
		projected += offset;

		if (finished && t.score() >= 0) projected = t.score();

		double secretsMissing = Math.max(0, floor.secretsRequired * 100.0 - Math.max(0, s.secretsPct));
		boolean hasData = s.liveScore >= 0 || (s.clearPct >= 0 && s.secretsPct >= 0);
		return new Score(projected, s.liveScore, fin[0], fin[1], fin[2], fin[3], rank(projected),
				secretsMissing, Math.max(0, 5 - Math.max(0, s.crypts)), predictedEndMs - floor.speedLimitMs(), hasData);
	}

	/** The model's score from the end-of-run stats, saved with the run to calibrate later runs. */
	public static int modelAtEnd(RunTracker t) {
		RunTracker.Stats s = t.stats;
		ModConfig cfg = ModConfig.get();
		double roomsAtBoss = Math.max(0, t.clearPctAtBoss()) / 100.0;
		double rooms = Math.min(1.0, Math.max(Math.max(0, s.clearPct) / 100.0, roomsAtBoss / FULL_CLEAR_AT_BOSS));
		return model(t.floor(), s, cfg, rooms, Math.max(0, s.secretsPct) / 100.0, t.elapsedMs(), true)[4];
	}

	/** {skill, explore, speed, bonus, total} */
	private static int[] model(Floor floor, RunTracker.Stats s, ModConfig cfg, double rooms, double secrets,
							   long runMs, boolean atEnd) {
		int deaths = Math.max(0, s.deaths);
		int failed = Math.max(0, s.puzzlesFailed);
		int unfinished = atEnd && s.puzzlesTotal > 0 ? Math.max(0, s.puzzlesTotal - s.puzzlesDone - s.puzzlesFailed) : 0;
		int skill = clamp((int) Math.round(20 + 80 * rooms) - 2 * deaths - 14 * failed - 10 * unfinished, 0, 100);
		double secretRatio = Math.min(1.0, secrets / floor.secretsRequired);
		int explore = clamp((int) Math.round(60 * rooms) + (int) Math.round(40 * secretRatio), 0, 100);
		int speed = speedScore(floor, runMs);
		int bonus = Math.min(5, Math.max(0, s.crypts))
				+ (s.mimicKilled && floor.number() >= 6 ? 2 : 0)
				+ (s.princeKilled ? 1 : 0)
				+ (ModConfig.on(Feature.PAUL) ? 10 : 0);
		return new int[]{skill, explore, speed, bonus, skill + explore + speed + bonus};
	}

	/** Median of (real score - model score) over recent runs on this floor. */
	private static int historyOffset(Floor floor, ModConfig cfg) {
		List<Integer> diffs = new ArrayList<>();
		for (RunRecord r : RunHistory.recent(floor, cfg.historyCutoffMillis(), 10)) {
			if (r.score >= 0 && r.modelScore >= 0) diffs.add(r.score - r.modelScore);
		}
		if (diffs.isEmpty()) return 0;
		Collections.sort(diffs);
		return clamp(diffs.get(diffs.size() / 2), -15, 15);
	}

	/** SkyBlock wiki speed-score brackets on (run time - floor offset). */
	public static int speedScore(Floor floor, long runMs) {
		double t = runMs / 1000.0 - floor.speedOffsetSec;
		if (t < 480) return 100;
		if (t < 600) return (int) Math.ceil(140 - t / 12);
		if (t < 840) return (int) Math.ceil(115 - t / 24);
		if (t < 1140) return (int) Math.ceil(108 - t / 30);
		if (t < 3940) return (int) Math.ceil(98.5 - t / 40);
		return 0;
	}

	public static String rank(int total) {
		if (total >= S_PLUS) return "S+";
		if (total >= S) return "S";
		if (total >= 230) return "A";
		if (total >= 160) return "B";
		if (total >= 100) return "C";
		return "D";
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
