package com.taz.dungeons.predict;

import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.run.ReplayView;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays your recorded runs through the real predictor, each run seeing only the runs before
 * it, and reports how far off the end time was at each point of the boss, plus how often the
 * "best possible" time was beaten. Used by the Backtest button (/taz > Dungeons > Tools).
 */
public final class Backtester {
	private static final int WARMUP_RUNS = 10;

	private Backtester() {
	}

	public static List<String> run(Floor floor) {
		List<RunRecord> runs = RunHistory.recent(floor, ModConfig.get().historyCutoffMillis(), Integer.MAX_VALUE);
		List<String> out = new ArrayList<>();
		if (runs.size() <= WARMUP_RUNS) {
			out.add(floor.id + ": need more than " + WARMUP_RUNS + " recorded runs (have " + runs.size() + ")");
			return out;
		}
		Map<String, double[]> err = new LinkedHashMap<>(); // label -> {sum |error|, count, within 5s}
		int bestChecks = 0, bestBeaten = 0, tested = 0;
		Predictor.replayMode = true;
		try {
			for (int i = WARMUP_RUNS; i < runs.size(); i++) {
				RunRecord r = runs.get(i);
				Long portal = r.split(Milestone.BOSS_ENTER);
				if (portal == null) continue;
				RunHistory.setReplayLimit(r.recordedAt);
				tested++;
				for (Map.Entry<String, Long> cp : checkpoints(floor, r).entrySet()) {
					Predictor.Prediction p = Predictor.predict(new ReplayView(r, cp.getValue()));
					if (p == null) continue;
					double e = Math.abs(p.endMs() - r.totalMs) / 1000.0;
					double[] acc = err.computeIfAbsent(cp.getKey(), k -> new double[3]);
					acc[0] += e;
					acc[1]++;
					if (e <= 5) acc[2]++;
					if (cp.getKey().equals("portal") && p.bestEndMs() >= 0) {
						bestChecks++;
						if (r.totalMs < p.bestEndMs()) bestBeaten++;
					}
				}
			}
		} finally {
			RunHistory.setReplayLimit(Long.MAX_VALUE);
			Predictor.replayMode = false;
		}
		out.add(floor.id + ": replayed " + tested + " runs (each predicted from the runs before it)");
		for (Map.Entry<String, double[]> e : err.entrySet()) {
			double[] a = e.getValue();
			out.add(String.format("  %-14s +/-%5.1fs   within 5s: %3.0f%%   (%d)", e.getKey(), a[0] / a[1], 100 * a[2] / a[1], (int) a[1]));
		}
		if (bestChecks > 0) {
			out.add(String.format("  best possible beaten in %.1f%% of runs", 100.0 * bestBeaten / bestChecks));
		}
		return out;
	}

	/** Portal entry, every boss milestone, and points inside the longer phases. */
	private static Map<String, Long> checkpoints(Floor floor, RunRecord r) {
		Map<String, Long> cps = new LinkedHashMap<>();
		int bossIdx = floor.indexOf(Milestone.BOSS_ENTER);
		for (int i = bossIdx; i < floor.splits.size() - 1; i++) {
			Floor.Split s = floor.splits.get(i);
			Long at = r.split(s.milestone());
			if (at == null || at >= r.totalMs) continue;
			cps.put(i == bossIdx ? "portal" : s.segmentName().toLowerCase(), at);
			if (s.milestone() == Milestone.GOLDOR || s.milestone() == Milestone.STORM) {
				Long next = r.split(floor.splits.get(i + 1).milestone());
				for (long plus : new long[]{25_000, 45_000}) {
					if (next != null && at + plus < next) cps.put(s.segmentName().toLowerCase() + "+" + plus / 1000 + "s", at + plus);
				}
			}
		}
		return cps;
	}

	/** Runs the backtest for each floor with enough data and writes backtest.txt. */
	public static Path writeReport(Floor... floors) throws Exception {
		List<String> lines = new ArrayList<>();
		for (Floor f : floors) lines.addAll(run(f));
		Path file = ModConfig.DIR.resolve("backtest.txt");
		Files.createDirectories(ModConfig.DIR);
		Files.write(file, lines, StandardCharsets.UTF_8);
		return file;
	}
}
