package com.taz.dungeons.run;

import com.taz.dungeons.dungeon.Floor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Completed runs since the game started, per floor (for the Session panel and the Stats screen). */
public final class SessionStats {
	public static final class FloorSession {
		public final List<Long> times = new ArrayList<>();
		public long bestMs = Long.MAX_VALUE;
		public long lastMs = -1;
		public int sPlus;
		public int scored;
		/** |actual - portal estimate| in ms, for runs that had an estimate. */
		public final List<Long> estimateErrors = new ArrayList<>();
		public long lastEstimateError;
		public boolean lastHadEstimate;

		public int runs() {
			return times.size();
		}

		public long averageMs() {
			if (times.isEmpty()) return 0;
			long sum = 0;
			for (long t : times) sum += t;
			return sum / times.size();
		}

		public long medianMs() {
			if (times.isEmpty()) return 0;
			List<Long> sorted = new ArrayList<>(times);
			sorted.sort(null);
			int n = sorted.size();
			return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
		}

		/** Mean absolute portal-estimate error in ms, or -1. */
		public long meanEstimateErrorMs() {
			if (estimateErrors.isEmpty()) return -1;
			long sum = 0;
			for (long e : estimateErrors) sum += Math.abs(e);
			return sum / estimateErrors.size();
		}
	}

	private static final Map<Floor, FloorSession> SESSIONS = new EnumMap<>(Floor.class);
	private static Floor lastFloor;

	private SessionStats() {
	}

	public static void record(RunRecord r) {
		Floor f = Floor.fromId(r.floor);
		if (f == null) return;
		FloorSession s = SESSIONS.computeIfAbsent(f, k -> new FloorSession());
		s.times.add(r.totalMs);
		s.bestMs = Math.min(s.bestMs, r.totalMs);
		s.lastMs = r.totalMs;
		if (r.score >= 0) {
			s.scored++;
			if (r.score >= 300) s.sPlus++;
		}
		s.lastHadEstimate = r.predictedAtBossMs > 0;
		if (s.lastHadEstimate) {
			s.lastEstimateError = r.totalMs - r.predictedAtBossMs;
			s.estimateErrors.add(s.lastEstimateError);
		}
		lastFloor = f;
	}

	public static FloorSession get(Floor f) {
		return f == null ? null : SESSIONS.get(f);
	}

	public static Floor lastFloor() {
		return lastFloor;
	}

	public static void clear() {
		SESSIONS.clear();
		lastFloor = null;
	}
}
