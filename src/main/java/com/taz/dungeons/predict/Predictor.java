package com.taz.dungeons.predict;

import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.run.RunHistory;
import com.taz.dungeons.run.RunRecord;
import com.taz.dungeons.run.RunTracker;
import com.taz.dungeons.run.RunView;
import com.taz.dungeons.util.LagMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Predicts the final run time from your own past runs on the same floor.
 *
 * Everything is measured in game time (server ticks x 50 ms) and converted back to real
 * time at the current TPS, so lag is handled: a laggy boss pushes the ETA out, and a laggy
 * past run doesn't skew future predictions.
 *
 * Before the boss: the boss opens once blood is cleared AND the party is done clearing
 * (rooms and secrets). Blood is predicted from how long it usually takes once the door
 * opens, the Watcher's spawn lines, and "That will be enough for now." when Hypixel says
 * it. The finish is learned from how long your past runs took from the same clear % and
 * secrets % to the portal (or, without that data, your usual blood-clear-to-portal gap).
 * Then the usual boss length is added.
 *
 * From the boss onward (or any milestone on floors without that split): the time left after
 * this milestone in past runs, counting only runs where the current phase lasted at least as
 * long as it has so far (so a slow phase doesn't predict a negative time left).
 *
 * Newer runs and runs with the same teammates count more (strongly before the boss, mildly
 * in it; tuned by replaying 49 real M6 runs). Built-in defaults fill in until there's
 * history, with a small weight so your own runs take over almost immediately. Past runs are
 * also rebuilt from your chat logs on startup (see LogImporter).
 */
public final class Predictor {
	public static final int MIN_RUNS_FOR_CONFIDENCE = 3;
	/** Set by the backtester: ignore the live server TPS. */
	static boolean replayMode;
	/** How many runs' worth of weight the built-in defaults get. Low: one real run already outweighs them. */
	private static final double PRIOR_WEIGHT = 0.5;
	private static final double DEFAULT_CLEAR_TARGET = 95;
	private static final double MAX_LAG_FACTOR = 2.5;
	/** Before the boss, pace and party change between sessions, so lean hard on the latest runs. */
	private static final int CLEAR_WINDOW = 20;
	private static final double CLEAR_DECAY = 0.85;
	/**
	 * Boss phases, tuned per floor by replaying 300 M6 + 123 M7 runs. F7/M7 bosses swing a lot
	 * with the party and its form that day, so recent runs dominate; steadier floors (M6's boss
	 * is +/-3s) do better with a longer, flatter history. Best possible uses a long window so
	 * one lucky streak doesn't dominate.
	 */
	private static int bossWindow(Floor f) {
		return f.number() == 7 ? 60 : 90;
	}

	private static double bossDecay(Floor f) {
		return f.number() == 7 ? 0.85 : 0.95;
	}

	/** Past runs with the same number of boss deaths so far count more: weight x e^(-k x difference). */
	private static double deathSimilarity(Floor f) {
		return f.number() == 7 ? 0.5 : 1.0;
	}

	private static final int BEST_WINDOW = 120;
	private static final double BEST_DECAY = 0.99;
	/** "Best possible" = the fastest 5% of real outcomes from the same point (beaten ~2-6% of the time). */
	private static final double BEST_QUANTILE = 0.05;
	/** F7/M7 before Core: past runs that reached the portal at a similar time count more (slow clears ~ slow bosses). */
	private static final double PORTAL_KERNEL_MS = 20_000;
	/** Walk from blood clear to the portal when nothing else is left (fastest observed ~4s). */
	private static final double MIN_WALK_MS = 4_000;
	/** "That will be enough for now." -> "You have proven yourself" usually takes ~2s. */
	private static final double WATCHER_DONE_TO_CLEAR_MS = 2_500;

	public record SegmentStats(String name, double medianMs, double p25Ms, double p75Ms, int samples) {
	}

	public record SplitLine(String name, long actualMs, long expectedMs, boolean live) {
	}

	/**
	 * @param bestEndMs fastest possible finish (real ms) from the boss onward: now + your
	 *                  best-ever time for each remaining boss phase. -1 before the boss.
	 */
	public record Prediction(
			long endMs, long lowMs, long highMs,
			double pace, int historyRuns, boolean learning,
			List<SplitLine> splits, long expectedTotalMs,
			double tps, long bestEndMs) {
	}

	/** Absolute game-time estimate of the end of the run, with a likely range. */
	private record Estimate(double end, double low, double high) {
	}

	private record History(List<RunRecord> runs, double[] weights) {
		int size() {
			return runs.size();
		}
	}

	private Predictor() {
	}

	// ---------------------------------------------------------------- segment stats (splits panel, stats screen)

	public static List<SegmentStats> segmentStats(Floor floor) {
		return segmentStats(floor, history(floor, Set.of(), -1, bossWindow(floor), bossDecay(floor)));
	}

	private static List<SegmentStats> segmentStats(Floor floor, History h) {
		int segs = floor.segmentCount();
		List<List<double[]>> samples = new ArrayList<>(segs);
		for (int i = 0; i < segs; i++) samples.add(new ArrayList<>());

		for (int ri = 0; ri < h.size(); ri++) {
			RunRecord r = h.runs().get(ri);
			// Walk the milestones this run has. If a trigger was missed, split the gap across the
			// skipped segments in proportion to their defaults so the data still counts.
			int prevIdx = 0;
			long prevT = 0;
			for (int k = 1; k < floor.splits.size(); k++) {
				Long t = r.gameSplit(floor.splits.get(k).milestone());
				if (t == null || t < prevT) continue;
				long span = t - prevT;
				double defSum = 0;
				for (int j = prevIdx; j < k; j++) defSum += floor.splits.get(j).defaultSeconds();
				for (int j = prevIdx; j < k; j++) {
					double share = defSum > 0 ? floor.splits.get(j).defaultSeconds() / defSum : 1.0 / (k - prevIdx);
					samples.get(j).add(new double[]{span * share, h.weights()[ri]});
				}
				prevIdx = k;
				prevT = t;
			}
		}

		List<SegmentStats> out = new ArrayList<>(segs);
		for (int i = 0; i < segs; i++) {
			Floor.Split split = floor.splits.get(i);
			double def = split.defaultSeconds() * 1000.0;
			List<double[]> v = samples.get(i);
			int n = v.size();
			out.add(new SegmentStats(split.segmentName(),
					blend(n == 0 ? 0 : wq(v, 0.5), def, n),
					blend(n == 0 ? 0 : wq(v, 0.25), def * 0.9, n),
					blend(n == 0 ? 0 : wq(v, 0.75), def * 1.12, n),
					n));
		}
		return out;
	}

	// ---------------------------------------------------------------- prediction

	public static Prediction predict(RunView t) {
		Floor floor = t.floor();
		if (floor == null) return null;
		ModConfig cfg = ModConfig.get();
		History h = history(floor, t.partyMembers(), t.partySize(), CLEAR_WINDOW, CLEAR_DECAY);
		History hb = history(floor, t.partyMembers(), t.partySize(), bossWindow(floor), bossDecay(floor));
		History hbest = history(floor, t.partyMembers(), t.partySize(), BEST_WINDOW, BEST_DECAY);
		List<SegmentStats> stats = segmentStats(floor, hb);
		int historyRuns = RunHistory.count(floor, cfg.historyCutoffMillis());
		boolean learning = historyRuns < MIN_RUNS_FOR_CONFIDENCE;
		double tps = ModConfig.on(Feature.LAG_ADJUST) && !replayMode ? LagMonitor.tps() : LagMonitor.NORMAL_TPS;

		double defTotal = sumDefaults(floor, 0);
		List<double[]> totals = new ArrayList<>();
		for (int i = 0; i < h.size(); i++) totals.add(new double[]{h.runs().get(i).gameTotal(), h.weights()[i]});
		double expTotal = blend(totals.isEmpty() ? 0 : wq(totals, 0.5), defTotal, totals.size());

		RunTracker.State state = t.state();
		if (state == RunTracker.State.WAITING || state == RunTracker.State.IDLE) {
			double lo = blend(totals.isEmpty() ? 0 : wq(totals, 0.2), defTotal * 0.92, totals.size());
			double hi = blend(totals.isEmpty() ? 0 : wq(totals, 0.8), defTotal * 1.12, totals.size());
			return new Prediction((long) expTotal, (long) lo, (long) hi, 1.0, historyRuns, learning, List.of(), (long) expTotal, tps, -1);
		}

		long nowReal = t.elapsedMs();
		int j = t.lastIndex();
		List<SplitLine> lines = completedSplits(t, floor, stats, j);
		if (state == RunTracker.State.FINISHED || j >= floor.segmentCount()) {
			return new Prediction(nowReal, nowReal, nowReal, 1.0, historyRuns, learning, lines, (long) expTotal, tps, -1);
		}

		long nowGame = t.gameElapsedMs();
		Long tjBoxed = t.gameSplitMs(floor.splits.get(j).milestone());
		long tjGame = tjBoxed == null ? nowGame : tjBoxed;

		int bossIdx = floor.indexOf(Milestone.BOSS_ENTER);
		Estimate e = bossIdx > j
				? clearPhase(floor, t, h, hb, nowGame)
				: fromMilestone(floor, t, hb, j, nowGame, tjGame);

		// Remaining game time -> real time at the current TPS.
		double lag = clamp(LagMonitor.NORMAL_TPS / tps, 1.0, MAX_LAG_FACTOR); // 1.0 when lag adjust is off
		long end = nowReal + (long) (Math.max(0, e.end() - nowGame) * lag);
		long low = nowReal + (long) (Math.max(0, e.low() - nowGame) * lag);
		long high = nowReal + (long) (Math.max(0, e.high() - nowGame) * lag);

		// Pace: game time to the last milestone vs. what's usual (display only).
		double expectedSoFar = 0;
		for (int i = 0; i < j; i++) expectedSoFar += stats.get(i).medianMs();
		double pace = j > 0 && expectedSoFar > 0 ? tjGame / expectedSoFar : 1.0;

		long bestEnd = -1;
		if (bossIdx >= 0 && j >= bossIdx) {
			double bestRemGame = bestRemaining(floor, t, hbest, j, nowGame, tjGame);
			bestEnd = nowReal + (long) (bestRemGame * lag);
		}

		List<SplitLine> withLive = new ArrayList<>(lines);
		withLive.add(new SplitLine(stats.get(j).name(), nowReal - t.splitMs(floor.splits.get(j).milestone()),
				(long) stats.get(j).medianMs(), true));
		return new Prediction(end, Math.min(low, end), Math.max(high, end), pace, historyRuns, learning, withLive, (long) expTotal, tps,
				bestEnd < 0 ? -1 : Math.min(bestEnd, end));
	}

	/**
	 * Weighted samples of the game time left from this point, one per comparable past run:
	 *   - time left after the current milestone, among runs whose current phase lasted at least
	 *     as long as it has so far (or, in Terminals, time left after reaching the same gate and
	 *     terminal count)
	 *   - weighted by recency and party, by how close their boss deaths so far are to this run's,
	 *     and on F7/M7 before Core by how close their portal time was
	 * Falls back to "time after the next milestone" when this phase is already longer than in
	 * every past run (the next milestone must be close).
	 */
	private static List<double[]> remainingSamples(Floor floor, RunView t, History h, int j, long nowGame, long tjGame) {
		Milestone m = floor.splits.get(j).milestone();
		Milestone next = floor.splits.get(j + 1).milestone();
		double s = nowGame - tjGame;
		int deathsNow = t.bossDeaths();
		Long bossNow = t.gameSplitMs(Milestone.BOSS_ENTER);
		int coreIdx = floor.indexOf(Milestone.CORE);
		boolean portalKernel = coreIdx >= 0 && j < coreIdx && bossNow != null;
		double prog = m == Milestone.GOLDOR ? t.terminalProgress() : -1;
		long prog1000 = prog > 0 ? Math.round(prog * 1000) : -1;

		List<double[]> cand = new ArrayList<>();
		List<double[]> afterNext = new ArrayList<>();
		for (int i = 0; i < h.size(); i++) {
			RunRecord r = h.runs().get(i);
			Long tm = r.gameSplit(m);
			if (tm == null) continue;
			double tot = r.gameTotal();
			double rem = tot - tm;
			if (rem <= 0) continue;
			double w = h.weights()[i];
			Long rb = r.gameSplit(Milestone.BOSS_ENTER);
			if (bossNow != null && rb != null && deathTimesKnown(r)) {
				int dr = r.deathsBetween(rb, (long) (tm + s));
				w *= Math.exp(-deathSimilarity(floor) * Math.abs(dr - deathsNow));
			}
			if (portalKernel && rb != null) w *= Math.exp(-Math.abs(rb - bossNow) / PORTAL_KERNEL_MS);

			if (prog1000 > 0 && r.termProgress != null && !r.termProgress.isEmpty()) {
				Long tp = r.terminalsReached(prog1000);
				if (tp != null && tot > tp) {
					cand.add(new double[]{tot - tp, w});
					continue;
				}
			}
			Long tn = r.gameSplit(next);
			boolean hasNext = tn != null && tn >= tm;
			double seg = hasNext ? tn - tm : rem;
			if (seg > s) cand.add(new double[]{rem - s, w});
			if (hasNext) afterNext.add(new double[]{tot - tn + 1000, w});
		}
		return cand.isEmpty() ? afterNext : cand;
	}

	/** Old records only stored a death count; their timing is unknown unless there were none. */
	private static boolean deathTimesKnown(RunRecord r) {
		return (r.deathTimes != null && !r.deathTimes.isEmpty()) || r.deaths == 0;
	}

	/** Boss phases (and floors without blood splits): median time left from this point in past runs. */
	private static Estimate fromMilestone(Floor floor, RunView t, History h, int j, long nowGame, long tjGame) {
		double s = nowGame - tjGame;
		List<double[]> samples = remainingSamples(floor, t, h, j, nowGame, tjGame);

		double defRem = sumDefaults(floor, j);
		double defSeg = floor.splits.get(j).defaultSeconds() * 1000.0;
		double defCur = defSeg > s ? defRem - s : (defRem - defSeg) + Math.max(1500, 0.1 * defSeg);

		int n = samples.size();
		double med = n == 0 ? 0 : wq(samples, 0.5);
		double lo = n == 0 ? 0 : wq(samples, 0.2);
		double hi = n == 0 ? 0 : wq(samples, 0.8);
		double rem = blend(med, defCur, n);
		double remLo = blend(lo, defCur * 0.92, n);
		double remHi = blend(hi, defCur * 1.12, n);
		double band = Math.max(1500, rem * 0.04);
		return new Estimate(nowGame + rem, nowGame + Math.min(remLo, rem - band), nowGame + Math.max(remHi, rem + band));
	}

	/**
	 * Before the boss: boss entry is when blood is cleared AND the party is done clearing (rooms
	 * and secrets), then the boss itself. The second part is the big unknown: parties often
	 * spend 20-35s after the clear hits 97% collecting secrets, so it's learned from your runs:
	 *   - with clear % and secrets % timelines: how long past runs took from this same
	 *     clear/secrets state to the portal
	 *   - otherwise: your usual gap between blood clear and boss entry
	 */
	private static Estimate clearPhase(Floor floor, RunView t, History h, History hb, long nowGame) {
		double dRush = defaultOf(floor, Milestone.START);
		double dBlood = defaultOf(floor, Milestone.BLOOD_OPEN);
		double dFinish = defaultOf(floor, Milestone.BLOOD_CLEAR);
		double dBoss = sumDefaults(floor, floor.indexOf(Milestone.BOSS_ENTER));
		Long bo = t.gameSplitMs(Milestone.BLOOD_OPEN);
		Long bc = t.gameSplitMs(Milestone.BLOOD_CLEAR);

		// 1) Blood door
		double tBO;
		if (bo != null) {
			tBO = bo;
		} else {
			List<double[]> later = new ArrayList<>();
			int n = 0;
			for (int i = 0; i < h.size(); i++) {
				Long v = h.runs().get(i).gameSplit(Milestone.BLOOD_OPEN);
				if (v == null) continue;
				n++;
				if (v > nowGame) later.add(new double[]{v, h.weights()[i]});
			}
			double hist = later.isEmpty() ? nowGame + 1500 : wq(later, 0.5);
			tBO = Math.max(nowGame + 500, blend(hist, Math.max(dRush, nowGame + 1500), n));
		}

		// 2) Blood cleared
		double tBC;
		if (bc != null) {
			tBC = bc;
		} else {
			double inBlood = bo != null ? nowGame - bo : 0;
			List<double[]> cond = new ArrayList<>();
			int n = 0;
			for (int i = 0; i < h.size(); i++) {
				RunRecord r = h.runs().get(i);
				Long rbo = r.gameSplit(Milestone.BLOOD_OPEN), rbc = r.gameSplit(Milestone.BLOOD_CLEAR);
				if (rbo == null || rbc == null || rbc < rbo) continue;
				n++;
				double w = rbc - rbo;
				if (w > inBlood) cond.add(new double[]{w - inBlood, h.weights()[i]});
			}
			double histRem = cond.isEmpty() ? 1500 : wq(cond, 0.5);
			double defRem = dBlood > inBlood ? dBlood - inBlood : 1500;
			double t1 = (bo != null ? nowGame : tBO) + blend(histRem, defRem, n);

			// Watcher spawn lines: when the count nears the usual total, blood is almost done.
			List<Long> lines = t.watcherLines();
			if (bo != null && lines.size() >= 3) {
				List<double[]> counts = new ArrayList<>(), tails = new ArrayList<>();
				for (int i = 0; i < h.size(); i++) {
					RunRecord r = h.runs().get(i);
					Long rbc = r.gameSplit(Milestone.BLOOD_CLEAR);
					if (rbc == null || r.watcherLines == null || r.watcherLines.size() < 3) continue;
					counts.add(new double[]{r.watcherLines.size(), h.weights()[i]});
					tails.add(new double[]{Math.max(0, rbc - r.watcherLines.get(r.watcherLines.size() - 1)), h.weights()[i]});
				}
				if (!counts.isEmpty()) {
					double usual = wq(counts, 0.5);
					double tail = wq(tails, 0.5);
					double last = lines.get(lines.size() - 1);
					double left = Math.max(0, usual - lines.size());
					double t2 = Math.max(last + left * recentInterval(lines) + tail, nowGame + 500);
					double wl = Math.min(0.8, 0.3 + 0.5 * lines.size() / Math.max(1.0, usual));
					t1 = (1 - wl) * t1 + wl * t2;
				}
			}
			// "That will be enough for now." = the Watcher is done spawning.
			if (t.watcherDoneAt() >= 0) t1 = Math.min(t1, Math.max(nowGame + 500, t.watcherDoneAt() + WATCHER_DONE_TO_CLEAR_MS));
			tBC = Math.max(nowGame + 500, t1);
		}

		// 3) Party done clearing -> portal
		double tBoss;
		Double fromState = stateAnalog(t, h, nowGame);
		if (fromState != null) {
			tBoss = Math.max(fromState, tBC + MIN_WALK_MS);
		} else {
			// Usual gap between blood clear and boss entry, given how long it's been since blood cleared.
			double sinceBc = bc != null ? nowGame - bc : 0;
			List<double[]> fin = new ArrayList<>();
			int n = 0;
			for (int i = 0; i < h.size(); i++) {
				RunRecord r = h.runs().get(i);
				Long rbc = r.gameSplit(Milestone.BLOOD_CLEAR), rb = r.gameSplit(Milestone.BOSS_ENTER);
				if (rbc == null || rb == null || rb < rbc) continue;
				n++;
				double f = rb - rbc;
				if (f > sinceBc) fin.add(new double[]{f - sinceBc, h.weights()[i]});
			}
			double histRem = fin.isEmpty() ? 1000 : wq(fin, 0.5);
			double defRem = Math.max(1000, dFinish - sinceBc);
			tBoss = (bc != null ? nowGame : tBC) + blend(histRem, defRem, n);
		}
		tBoss = Math.max(tBoss, nowGame + 500);

		// 4) Boss (steady, so the wide history)
		List<double[]> boss = new ArrayList<>();
		for (int i = 0; i < hb.size(); i++) {
			RunRecord r = hb.runs().get(i);
			Long b = r.gameSplit(Milestone.BOSS_ENTER);
			if (b != null && r.gameTotal() > b) boss.add(new double[]{r.gameTotal() - b, hb.weights()[i]});
		}
		int n = boss.size();
		double bMed = blend(n == 0 ? 0 : wq(boss, 0.5), dBoss, n);
		double bLo = blend(n == 0 ? 0 : wq(boss, 0.2), dBoss * 0.92, n);
		double bHi = blend(n == 0 ? 0 : wq(boss, 0.8), dBoss * 1.12, n);
		double horizon = tBoss - nowGame;
		double end = tBoss + bMed;
		double band = Math.max(2000, (end - nowGame) * 0.04);
		return new Estimate(end,
				Math.min(tBoss - 0.15 * horizon + bLo, end - band),
				Math.max(tBoss + 0.35 * horizon + bHi, end + band));
	}

	/**
	 * When past runs have clear/secrets timelines: for each, how long it took from first having
	 * this run's current clear % and secrets % to entering boss. A run that never got this far
	 * before its boss would already be in boss, so it counts as just the walk. Needs 2+ runs.
	 */
	private static Double stateAnalog(RunView t, History h, long nowGame) {
		int c = t.clearPct();
		double sec = t.secretsPct();
		if (c < 0 || sec < 0) return null;
		long sec10 = Math.round(sec * 10);
		List<double[]> rem = new ArrayList<>();
		for (int i = 0; i < h.size(); i++) {
			RunRecord r = h.runs().get(i);
			Long rb = r.gameSplit(Milestone.BOSS_ENTER);
			if (rb == null || !r.hasStateTimelines()) continue;
			Long at = r.stateReached(c, sec10);
			double left = at == null || at > rb ? MIN_WALK_MS : rb - at;
			rem.add(new double[]{left, h.weights()[i]});
		}
		if (rem.size() < 2) return null;
		return nowGame + wq(rem, 0.5);
	}

	/**
	 * Fastest realistic finish from here: the fastest 5% of what actually happened in comparable
	 * past runs from the same point (same weighting as the end time, over a longer window).
	 * Replaying a week of runs, this is beaten in ~2% of M6 and ~6% of M7 runs, while "sum of
	 * best-ever phases" was 5s (M6) / 41s (M7) faster than reality on a typical run.
	 */
	private static double bestRemaining(Floor floor, RunView t, History h, int j, long nowGame, long tjGame) {
		List<double[]> samples = remainingSamples(floor, t, h, j, nowGame, tjGame);
		if (!samples.isEmpty()) return wq(samples, BEST_QUANTILE);
		double s = nowGame - tjGame;
		double total = 0;
		for (int i = j; i < floor.segmentCount(); i++) {
			double d = floor.splits.get(i).defaultSeconds() * 900.0;
			total += i == j ? Math.max(0, d - s) : d;
		}
		return total;
	}

	// ---------------------------------------------------------------- helpers

	private record HistoryKey(Floor floor, Set<String> party, int partySize, int window, double decay, int version, String cutoff) {
	}

	private static final java.util.Map<HistoryKey, History> HISTORY_CACHE = new java.util.LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<HistoryKey, History> eldest) {
			return size() > 12;
		}
	};

	/** Cached per floor/party/window until the history changes. */
	private static History history(Floor floor, Set<String> party, int partySize, int window, double decay) {
		HistoryKey key = new HistoryKey(floor, Set.copyOf(party), partySize, window, decay, RunHistory.version(), ModConfig.get().advanced.historyCutoff);
		return HISTORY_CACHE.computeIfAbsent(key, k -> buildHistory(floor, party, partySize, window, decay));
	}

	private static History buildHistory(Floor floor, Set<String> party, int partySize, int window, double decay) {
		ModConfig cfg = ModConfig.get();
		List<RunRecord> runs = RunHistory.recent(floor, cfg.historyCutoffMillis(), Math.max(1, window));
		double[] w = new double[runs.size()];
		for (int i = 0; i < runs.size(); i++) w[i] = runWeight(runs.get(i), runs.size() - 1 - i, party, partySize, decay);
		return new History(runs, w);
	}

	/** Newer runs and runs with the same teammates count more; a different party size counts less. */
	private static double runWeight(RunRecord r, int age, Set<String> party, int partySize, double decay) {
		double w = Math.pow(decay, age);
		if (party != null && !party.isEmpty() && r.partyMembers != null && !r.partyMembers.isEmpty()) {
			int shared = 0;
			for (String name : r.partyMembers) if (party.contains(name)) shared++;
			w *= 1.0 + 0.5 * shared;
		}
		if (partySize > 0 && r.partySize > 0 && partySize != r.partySize) w *= 0.6;
		return w;
	}

	/** Clear % this party usually enters boss with. */
	private static double clearTarget(History h) {
		List<double[]> v = new ArrayList<>();
		for (int i = 0; i < h.size(); i++) {
			int c = h.runs().get(i).clearPctAtBoss;
			if (c >= 50) v.add(new double[]{c, h.weights()[i]});
		}
		return v.isEmpty() ? DEFAULT_CLEAR_TARGET : Math.min(100, wq(v, 0.5));
	}

	/** Clear % this party usually enters boss with (used by the score projection). */
	public static double typicalClearAtBoss(Floor floor, ModConfig cfg) {
		return clearTarget(history(floor, Set.of(), -1, CLEAR_WINDOW, CLEAR_DECAY));
	}

	/** Median gap between the last few Watcher lines, clamped to a sane range. */
	private static double recentInterval(List<Long> lines) {
		List<double[]> gaps = new ArrayList<>();
		for (int i = Math.max(1, lines.size() - 5); i < lines.size(); i++) {
			gaps.add(new double[]{lines.get(i) - lines.get(i - 1), 1});
		}
		return gaps.isEmpty() ? 3000 : clamp(wq(gaps, 0.5), 1500, 10_000);
	}

	private static List<SplitLine> completedSplits(RunView tracker, Floor floor, List<SegmentStats> stats, int lastIndex) {
		List<SplitLine> lines = new ArrayList<>();
		int upTo = Math.min(lastIndex, floor.segmentCount());
		for (int i = 0; i < upTo; i++) {
			Long a = tracker.splitMs(floor.splits.get(i).milestone());
			Long b = tracker.splitMs(floor.splits.get(i + 1).milestone());
			if (a == null || b == null) continue;
			lines.add(new SplitLine(stats.get(i).name(), b - a, (long) stats.get(i).medianMs(), false));
		}
		return lines;
	}

	private static double defaultOf(Floor floor, Milestone m) {
		int i = floor.indexOf(m);
		return i < 0 ? 0 : floor.splits.get(i).defaultSeconds() * 1000.0;
	}

	/** Sum of default segment lengths from segment {@code from} to the end, in ms. */
	private static double sumDefaults(Floor floor, int from) {
		double sum = 0;
		for (int i = Math.max(0, from); i < floor.segmentCount(); i++) sum += floor.splits.get(i).defaultSeconds() * 1000.0;
		return sum;
	}

	/** History value blended with the built-in default; n = how many runs back the history value. */
	private static double blend(double hist, double def, int n) {
		if (n <= 0) return def;
		return (n * hist + PRIOR_WEIGHT * def) / (n + PRIOR_WEIGHT);
	}

	/** Weighted quantile of {value, weight} pairs (sorts the list). */
	static double wq(List<double[]> v, double q) {
		v.sort((a, b) -> Double.compare(a[0], b[0]));
		if (v.size() == 1) return v.get(0)[0];
		double total = 0;
		for (double[] p : v) total += p[1];
		if (total <= 0) return v.get(v.size() / 2)[0];
		// Interpolate between neighbours so two runs give a midpoint rather than snapping to one.
		double target = q * total, cum = 0;
		for (int i = 0; i < v.size(); i++) {
			double prev = cum;
			cum += v.get(i)[1];
			double mid = (prev + cum) / 2;
			if (target <= mid) {
				if (i == 0) return v.get(0)[0];
				double prevMid = prev - v.get(i - 1)[1] / 2;
				double f = (target - prevMid) / (mid - prevMid);
				return v.get(i - 1)[0] + f * (v.get(i)[0] - v.get(i - 1)[0]);
			}
		}
		return v.get(v.size() - 1)[0];
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
