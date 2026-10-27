package com.taz.dungeons.run;

import com.taz.dungeons.dungeon.Milestone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One completed run as stored in history.json. Plain fields so Gson can (de)serialize it. */
public final class RunRecord {
	public String floor;
	/** "mod" when recorded live, "log" when rebuilt from a chat log (1s resolution, no sidebar data). */
	public String source = "mod";
	/** Wall-clock time the run finished (epoch millis). */
	public long recordedAt;
	/** Final run time in real ms. Uses the time from Hypixel's "Defeated ... in" line when available. */
	public long totalMs;
	/** Milestone name -> real ms since run start. Missing entries mean the trigger never fired. */
	public Map<String, Long> splits = new LinkedHashMap<>();
	/** Milestone name -> game ms since run start (empty if TPS data wasn't available). */
	public Map<String, Long> gameSplits = new LinkedHashMap<>();
	public long totalGameMs = -1;
	/** Average server TPS over the run. */
	public double avgTps = -1;

	/** Game ms of each Watcher line between the blood door opening and blood being cleared. */
	public List<Long> watcherLines = new ArrayList<>();
	/** {game ms, cleared %} each time the sidebar Cleared % changed before the boss. */
	public List<long[]> clearTimeline = new ArrayList<>();
	/** {game ms, secrets % x 10} each time the tab's Secrets Found % changed before the boss. */
	public List<long[]> secretsTimeline = new ArrayList<>();
	/** Game ms of each player death. */
	public List<Long> deathTimes = new ArrayList<>();
	/** F7/M7 terminals: {game ms, progress x 1000}, progress = gates destroyed + share of the current section (0-4). */
	public List<long[]> termProgress = new ArrayList<>();
	/** Game ms of "That will be enough for now." (Watcher done spawning), or -1. */
	public long watcherDoneAt = -1;
	/** Best possible finish (real ms) when the party entered the boss: portal time + best-ever boss phases. -1 if none. */
	public long bestEndAtBossMs = -1;
	/** {real ms elapsed, predicted end real ms}, sampled every few seconds, for accuracy checks. */
	public List<long[]> predictionTrace = new ArrayList<>();

	public int clearPctAtBoss = -1;
	public int partySize = -1;
	public List<String> partyMembers = new ArrayList<>();
	public int deaths = -1;
	public double secretsPct = -1;
	public int crypts = -1;
	public int puzzlesFailed = -1;
	public boolean mimic;
	public boolean prince;
	public int score = -1;
	/** Last live score read from the sidebar. */
	public int liveScore = -1;
	/** The mod's own score estimate from the final stats (compared with score to calibrate). */
	public int modelScore = -1;
	/** What the mod predicted when the party entered the boss, for checking accuracy. -1 if none. */
	public long predictedAtBossMs = -1;

	/** Real ms of a milestone, or null. */
	public Long split(Milestone m) {
		if (m == Milestone.START) return 0L;
		if (m == Milestone.END) return totalMs > 0 ? totalMs : splits.get(m.name());
		return splits.get(m.name());
	}

	/** Whether the saved server-tick times make sense: they can't be ahead of real time. */
	private transient Boolean ticksOk;

	private boolean ticksOk() {
		if (ticksOk == null) {
			boolean ok = true;
			if (gameSplits != null) {
				for (Map.Entry<String, Long> e : gameSplits.entrySet()) {
					Long real = splits.get(e.getKey());
					if (real != null && e.getValue() != null && e.getValue() > real + 3000) ok = false;
				}
			}
			if (totalGameMs > 0 && totalMs > 0 && totalGameMs > totalMs + 3000) ok = false;
			ticksOk = ok;
		}
		return ticksOk;
	}

	/** Game ms of a milestone; falls back to real ms for runs without (sensible) tick data. */
	public Long gameSplit(Milestone m) {
		if (m == Milestone.START) return 0L;
		if (gameSplits != null && !gameSplits.isEmpty() && ticksOk()) {
			if (m == Milestone.END) return totalGameMs > 0 ? totalGameMs : gameSplits.get(m.name());
			return gameSplits.get(m.name());
		}
		return split(m);
	}

	public long gameTotal() {
		Long t = gameSplit(Milestone.END);
		return t == null ? totalMs : t;
	}

	/** Deaths at or after {@code fromGame} and up to {@code toGame} (game ms). */
	public int deathsBetween(long fromGame, long toGame) {
		if (deathTimes == null) return 0;
		int n = 0;
		for (Long d : deathTimes) if (d != null && d >= fromGame && d <= toGame) n++;
		return n;
	}

	/** Game ms when terminals progress first reached {@code progress1000}/1000, or null if not recorded. */
	public Long terminalsReached(long progress1000) {
		if (termProgress == null || termProgress.isEmpty()) return null;
		for (long[] p : termProgress) {
			if (p.length >= 2 && p[1] >= progress1000) return p[0];
		}
		return gameSplit(Milestone.CORE); // never got that far in a section = it was done by Core
	}

	/** Game ms when Cleared % first reached {@code pct} before the boss, or null. */
	public Long clearReached(int pct) {
		if (clearTimeline == null) return null;
		for (long[] p : clearTimeline) {
			if (p.length >= 2 && p[1] >= pct) return p[0];
		}
		return null;
	}

	/**
	 * Game ms when this run first had at least {@code clearPct} cleared AND {@code secretsPct10}/10 %
	 * secrets, or null if it never got there before the boss (or has no timelines).
	 */
	public Long stateReached(int clearPct, long secretsPct10) {
		Long c = clearReached(clearPct);
		if (c == null) return null;
		if (secretsTimeline == null || secretsTimeline.isEmpty()) return null;
		for (long[] p : secretsTimeline) {
			if (p.length >= 2 && p[1] >= secretsPct10) return Math.max(c, p[0]);
		}
		return null;
	}

	public boolean hasStateTimelines() {
		return clearTimeline != null && !clearTimeline.isEmpty() && secretsTimeline != null && !secretsTimeline.isEmpty();
	}

	/**
	 * True when "boss entered" was logged before blood was cleared, from a bug in 0.2.x-0.3.0
	 * where a blood-mob boss (Scarf) counted as the real boss.
	 */
	public boolean bossEntryLooksWrong() {
		Long boss = splits.get(Milestone.BOSS_ENTER.name());
		if (boss == null) return false;
		Long bc = splits.get(Milestone.BLOOD_CLEAR.name());
		Long bo = splits.get(Milestone.BLOOD_OPEN.name());
		return bo != null && (bc == null || bc > boss);
	}

	/** Game ms when the clear was effectively done (reached the % it entered boss with), or null. */
	public Long clearDone() {
		int target = clearPctAtBoss;
		if (target < 0 && clearTimeline != null && !clearTimeline.isEmpty()) {
			target = (int) clearTimeline.get(clearTimeline.size() - 1)[1];
		}
		return target < 0 ? null : clearReached(target);
	}
}
