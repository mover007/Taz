package com.taz.dungeons.run;

import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.dungeon.Triggers;
import com.taz.dungeons.util.LagMonitor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** State machine for the current run: IDLE -> WAITING (in dungeon, not started) -> RUNNING -> FINISHED. */
public final class RunTracker implements RunView {
	public enum State {IDLE, WAITING, RUNNING, FINISHED}

	/** Latest values from the sidebar and tab list. Kept when a value briefly disappears. */
	public static final class Stats {
		public int clearPct = -1;
		/** Hypixel's live score from the sidebar ("Cleared: 100% (304)"), or -1. */
		public int liveScore = -1;
		public int completedRooms = -1;
		public double secretsPct = -1;
		public int deaths = -1;
		public int crypts = -1;
		public int puzzlesTotal = -1;
		public int puzzlesDone;
		public int puzzlesFailed;
		public int partySize = -1;
		public final Set<String> partyMembers = new LinkedHashSet<>();
		public boolean mimicKilled;
		public boolean princeKilled;
	}

	private static final Pattern DEFEATED_TIME = Pattern.compile("Defeated .+ in (?:(\\d+)h )?(?:(\\d+)m )?(\\d+)s$");
	/** "[MVP+] name entered MM The Catacombs, Floor VI!" (sent when each player joins the dungeon). */
	private static final Pattern ENTERED = Pattern.compile(" entered (MM )?The Catacombs, (?:Floor ([IVX]+)|Entrance)!");
	private static final Pattern WATCHER = Pattern.compile("^\\[BOSS] The Watcher: ");
	private static final String WATCHER_DONE_LINE = "[BOSS] The Watcher: You have proven yourself. You may pass.";
	/** Hypixel's run timer starts ~2s after this line (M7 has no Mort line to mark the start). */
	private static final String STARTING_IN_1 = "Starting in 1 second.";
	private static final long START_AFTER_COUNTDOWN_MS = 2_000;
	/** "name activated a terminal! (3/7)", "... activated a lever! (6/8)", "... completed a device! (5/7)" (F7/M7). */
	private static final Pattern TERMINAL = Pattern.compile("^\\S+ (?:activated a terminal|activated a lever|completed a device)! \\((\\d)/(\\d)\\)$");
	private static final String GATE_DESTROYED = "The gate has been destroyed!";
	/** "☠ name was killed by X and became a ghost." / "☠ You were killed by Storm ..." */
	private static final Pattern DEATH = Pattern.compile("^☠ (?:(?:You were|\\S+ was) killed by |.+ became a ghost\\.$)");
	private static final long TRACE_INTERVAL_MS = 2_000;
	private static final Pattern TEAM_SCORE = Pattern.compile("^Team Score: (\\d+)");
	private static final Pattern PRINCE = Pattern.compile("^A Prince falls\\. \\+1 Bonus Score$");
	/** Other players' mods announce the Mimic in party chat, e.g. "Party > [MVP+] name: Mimic Killed!". */
	private static final Pattern MIMIC = Pattern.compile(
			"(?i)^(?:Party > [^:]+: )?(?:\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$|mimic (?:dead|killed)!?)$");
	/** Before the run starts, leaving the dungeon sidebar for this long cancels it. */
	private static final long LEAVE_GRACE_MS = 8_000;
	/** Safety net: a run "running" this long was abandoned without a server change. */
	private static final long MAX_RUN_MS = 30 * 60_000L;
	/** The "entered" line arrives just before the transfer to the dungeon server; keep it through that switch. */
	private static final long CHAT_FLOOR_KEEP_MS = 90_000;
	/** Wait this long after the end trigger for the "Defeated in" and score lines before saving. */
	private static final long SAVE_DELAY_MS = 3_000;
	/** Resync with the sidebar's Time Elapsed if our clock drifts further than this. */
	private static final long RESYNC_THRESHOLD_MS = 3_000;

	public final Stats stats = new Stats();

	private State state = State.IDLE;
	private Floor floor;
	private long startNanos;
	private final Map<Milestone, Long> splits = new EnumMap<>(Milestone.class);
	/** Same milestones in game ms (server ticks x 50). */
	private final Map<Milestone, Long> gameSplits = new EnumMap<>(Milestone.class);
	/** Server tick at run start, or NaN if TPS data wasn't available then (game time falls back to real time). */
	private double startTick = Double.NaN;
	private long finalGameMs = -1;
	private final List<Long> watcherLines = new ArrayList<>();
	private final List<long[]> clearTimeline = new ArrayList<>();
	/** {game ms, secrets % x 10} each time the tab's Secrets Found % changed before the boss. */
	private final List<long[]> secretsTimeline = new ArrayList<>();
	/** Game ms of each player death. */
	private final List<Long> deathTimes = new ArrayList<>();
	/** F7/M7 terminals: {game ms, progress x 1000} where progress = gates destroyed + share of the current section. */
	private final List<long[]> termProgress = new ArrayList<>();
	private int gatesDestroyed;
	private double sectionFraction;
	/** Game ms of "That will be enough for now." (Watcher done spawning; not said every run), or -1. */
	private long watcherDoneAt = -1;
	/** Best possible finish (real ms) when the party entered the boss, or -1. */
	private long bestEndAtBossMs = -1;
	private final List<long[]> predictionTrace = new ArrayList<>();
	private int lastSidebarSec = -1;
	/** The score model's estimate with the final stats, saved to calibrate the model against Hypixel's real score. */
	private int modelScoreAtEnd = -1;
	private int lastIndex = -1;
	private int clearPctAtBoss = -1;
	private long lastSeenInDungeon;
	/** Pending start from the countdown (nanoTime), or 0. */
	private long countdownStartNanos;

	private long finalMs = -1;
	private boolean defeatedSeen;
	private int score = -1;
	private long predictedAtBossMs = -1;
	private long finishedAtWall;
	private boolean pendingSave;
	private boolean demo;
	/** Floor learned from the "entered ... The Catacombs" chat line. */
	private Floor chatFloor;
	private long chatFloorAt;
	/** A run already ended on this server; don't start another from a frozen sidebar timer. */
	private boolean runDoneOnThisServer;
	/** Bumped whenever something the predictor depends on changes (milestone, death, terminal). */
	private int version;
	private Consumer<RunRecord> onSaved = r -> {};

	public void setOnSaved(Consumer<RunRecord> listener) {
		onSaved = listener;
	}

	public State state() {
		return state;
	}

	public Floor floor() {
		return floor;
	}

	public boolean isDemo() {
		return demo;
	}

	public Floor chatFloor() {
		return chatFloor;
	}

	public int score() {
		return score;
	}

	public int version() {
		return version;
	}

	@Override
	public Set<String> partyMembers() {
		return stats.partyMembers;
	}

	@Override
	public int partySize() {
		return stats.partySize;
	}

	@Override
	public int clearPct() {
		return stats.clearPct;
	}

	@Override
	public double secretsPct() {
		return stats.secretsPct;
	}

	public long predictedAtBossMs() {
		return predictedAtBossMs;
	}

	public void setPredictedAtBossMs(long ms) {
		predictedAtBossMs = ms;
	}

	public void setModelScoreAtEnd(int score) {
		modelScoreAtEnd = score;
	}

	public int lastIndex() {
		return lastIndex;
	}

	public int clearPctAtBoss() {
		return clearPctAtBoss;
	}

	public boolean bossReached() {
		return splits.containsKey(Milestone.BOSS_ENTER);
	}

	/** Ms since run start, frozen once the run finishes. 0 if not started. */
	public long elapsedMs() {
		return switch (state) {
			case RUNNING -> (System.nanoTime() - startNanos) / 1_000_000L;
			case FINISHED -> finalMs;
			default -> 0L;
		};
	}

	public Long splitMs(Milestone m) {
		return splits.get(m);
	}

	/**
	 * Server-tick time since run start (ticks x 50 ms). Equal to real time at 20 TPS; runs
	 * slower when the server lags and stops while it freezes.
	 */
	public long gameElapsedMs() {
		if (!ModConfig.on(Feature.LAG_ADJUST) && state == State.RUNNING) return elapsedMs();
		return switch (state) {
			case RUNNING -> {
				double tick = LagMonitor.serverTickNow();
				if (Double.isNaN(tick)) yield elapsedMs();
				// No tick yet when the run started (the world had just loaded): count from now, as if no lag so far.
				if (Double.isNaN(startTick)) startTick = tick - elapsedMs() / 50.0;
				// Server ticks can't run ahead of real time; if they do, the start tick came from another server.
				long real = elapsedMs();
				long game = Math.max(0L, (long) ((tick - startTick) * 50.0));
				if (game > real + 1000) {
					startTick = tick - real / 50.0;
					game = real;
				}
				yield game;
			}
			case FINISHED -> finalGameMs >= 0 ? finalGameMs : finalMs;
			default -> 0L;
		};
	}

	public Long gameSplitMs(Milestone m) {
		return gameSplits.get(m);
	}

	public List<Long> watcherLines() {
		return watcherLines;
	}

	public long watcherDoneAt() {
		return watcherDoneAt;
	}

	/** Deaths since the boss started (0 before the boss). */
	public int bossDeaths() {
		Long boss = gameSplits.get(Milestone.BOSS_ENTER);
		if (boss == null) return 0;
		int n = 0;
		for (long d : deathTimes) if (d >= boss) n++;
		return n;
	}

	public int totalDeaths() {
		return deathTimes.size();
	}

	/** Terminals progress (0-4: gates destroyed + share of the current section), or -1 outside that phase. */
	public double terminalProgress() {
		if (floor == null || !floor.hasMilestone(Milestone.CORE) || lastIndex != floor.indexOf(Milestone.GOLDOR)) return -1;
		return gatesDestroyed + sectionFraction;
	}

	/** Current terminal count in this section as "x/n", or null. */
	public int[] terminalCount() {
		return lastTermCount;
	}

	private int[] lastTermCount;

	public long bestEndAtBossMs() {
		return bestEndAtBossMs;
	}

	public void setBestEndAtBossMs(long ms) {
		bestEndAtBossMs = ms;
	}

	/** Called with the current prediction; keeps a sample every few seconds. */
	public void tracePrediction(long predictedEndMs) {
		if (state != State.RUNNING || demo) return;
		long now = elapsedMs();
		if (!predictionTrace.isEmpty() && now - predictionTrace.get(predictionTrace.size() - 1)[0] < TRACE_INTERVAL_MS) return;
		predictionTrace.add(new long[]{now, predictedEndMs});
	}

	/** Milestones this floor has that were never detected in this run. */
	public List<Milestone> missedMilestones() {
		List<Milestone> out = new ArrayList<>();
		if (floor == null) return out;
		for (Floor.Split s : floor.splits) {
			if (!splits.containsKey(s.milestone())) out.add(s.milestone());
		}
		return out;
	}

	public void reset() {
		state = State.IDLE;
		floor = null;
		splits.clear();
		gameSplits.clear();
		startTick = Double.NaN;
		finalGameMs = -1;
		watcherLines.clear();
		clearTimeline.clear();
		secretsTimeline.clear();
		deathTimes.clear();
		termProgress.clear();
		gatesDestroyed = 0;
		sectionFraction = 0;
		lastTermCount = null;
		watcherDoneAt = -1;
		bestEndAtBossMs = -1;
		predictionTrace.clear();
		lastSidebarSec = -1;
		lastIndex = -1;
		clearPctAtBoss = score = -1;
		finalMs = predictedAtBossMs = -1;
		modelScoreAtEnd = -1;
		countdownStartNanos = 0;
		defeatedSeen = pendingSave = demo = false;
		Stats s = stats;
		s.clearPct = s.liveScore = s.completedRooms = s.deaths = s.crypts = s.puzzlesTotal = s.partySize = -1;
		s.secretsPct = -1;
		s.puzzlesDone = s.puzzlesFailed = 0;
		s.partyMembers.clear();
		s.mimicKilled = s.princeKilled = false;
		version++;
	}

	/** Joined a different server. Any unfinished run is abandoned; a finished one is saved first. */
	public void onServerChange() {
		if (pendingSave) {
			pendingSave = false;
			saveRecord(System.currentTimeMillis());
		}
		reset();
		// "entered ... The Catacombs" comes right before the transfer to the dungeon server, so keep it.
		if (System.currentTimeMillis() - chatFloorAt > CHAT_FLOOR_KEEP_MS) chatFloor = null;
		runDoneOnThisServer = false;
	}

	/** Starts a fake run (the Demo run button). Never saved. */
	public void startDemo(Floor f) {
		reset();
		floor = f;
		demo = true;
		start(0);
	}

	public void tick(DungeonState ds) {
		long nowWall = System.currentTimeMillis();
		if (demo) {
			if (state == State.FINISHED && nowWall - finishedAtWall > ModConfig.get().advanced.resultSeconds * 1000L) reset();
			return;
		}

		// In a dungeon if the sidebar says so, or if the "entered ... The Catacombs" line was seen recently.
		boolean inDungeon = ds.inDungeon || chatFloor != null;
		Floor seenFloor = ds.floor != null ? ds.floor : chatFloor;
		if (inDungeon) {
			lastSeenInDungeon = nowWall;
			absorbStats(ds);
		}

		switch (state) {
			case IDLE -> {
				if (inDungeon && seenFloor != null && !runDoneOnThisServer) {
					floor = seenFloor;
					state = State.WAITING;
				}
			}
			case WAITING -> {
				if (!inDungeon) {
					if (nowWall - lastSeenInDungeon > LEAVE_GRACE_MS) reset();
				} else {
					if (seenFloor != null) floor = seenFloor;
					if (countdownStartNanos != 0 && System.nanoTime() >= countdownStartNanos) {
						start((System.nanoTime() - countdownStartNanos) / 1_000_000L);
					} else if (ds.timeElapsedSec > 0) {
						// Joined mid-run or missed the countdown: start from the sidebar timer.
						start(ds.timeElapsedSec * 1000L);
					}
				}
			}
			case RUNNING -> {
				// Leaving a dungeon always changes server (handled in onServerChange), so a missing
				// sidebar line here (e.g. in the F7/M7 boss) doesn't end the run.
				if (elapsedMs() > MAX_RUN_MS) {
					reset();
				} else if (ds.timeElapsedSec >= 0) {
					// Only trust the sidebar at the moment its seconds tick over (freshest reading) and
					// while the server isn't lagging: during a lag spike the sidebar is stale and would
					// drag the timer backwards.
					boolean fresh = lastSidebarSec >= 0 && ds.timeElapsedSec != lastSidebarSec;
					lastSidebarSec = ds.timeElapsedSec;
					if (fresh && !LagMonitor.isLagging()) {
						long sidebarMs = ds.timeElapsedSec * 1000L + 100;
						if (Math.abs(sidebarMs - elapsedMs()) > RESYNC_THRESHOLD_MS) {
							startNanos = System.nanoTime() - sidebarMs * 1_000_000L;
						}
					}
				}
			}
			case FINISHED -> {
				if (pendingSave && nowWall - finishedAtWall > SAVE_DELAY_MS) {
					pendingSave = false;
					saveRecord(nowWall);
				}
				boolean resultExpired = nowWall - finishedAtWall > ModConfig.get().advanced.resultSeconds * 1000L;
				if (resultExpired && !pendingSave && (!ds.inDungeon || ds.timeElapsedSec < 0)) reset();
			}
		}
	}

	private void absorbStats(DungeonState ds) {
		if (state == State.FINISHED) return; // freeze end-of-run values
		Stats s = stats;
		if (ds.clearedPct >= 0) {
			if (state == State.RUNNING && ds.clearedPct != s.clearPct && !bossReached()) {
				clearTimeline.add(new long[]{gameElapsedMs(), ds.clearedPct});
			}
			s.clearPct = ds.clearedPct;
		}
		if (ds.liveScore >= 0) s.liveScore = ds.liveScore;
		if (ds.completedRooms >= 0) s.completedRooms = ds.completedRooms;
		if (ds.secretsPct >= 0) {
			if (state == State.RUNNING && ds.secretsPct != s.secretsPct && !bossReached()) {
				secretsTimeline.add(new long[]{gameElapsedMs(), Math.round(ds.secretsPct * 10)});
			}
			s.secretsPct = ds.secretsPct;
		}
		if (ds.deaths >= 0) s.deaths = ds.deaths;
		if (ds.crypts >= 0) s.crypts = ds.crypts;
		if (ds.puzzlesTotal >= 0) {
			s.puzzlesTotal = ds.puzzlesTotal;
			s.puzzlesDone = ds.puzzlesDone;
			s.puzzlesFailed = ds.puzzlesFailed;
		}
		if (ds.partySize > 0) s.partySize = ds.partySize;
		if (!ds.partyMembers.isEmpty()) {
			s.partyMembers.clear();
			s.partyMembers.addAll(ds.partyMembers);
		}
	}

	/** Feeds one formatting-stripped chat line. Returns the milestones it reached, in order. */
	public List<Milestone> onChat(String line) {
		List<Milestone> reached = new ArrayList<>(2);
		Matcher em = ENTERED.matcher(line);
		if (line.startsWith("-") && em.find()) {
			chatFloor = floorFromEntered(em.group(1) != null, em.group(2));
			chatFloorAt = System.currentTimeMillis();
			if (state == State.IDLE && chatFloor != null && !runDoneOnThisServer) {
				floor = chatFloor;
				state = State.WAITING;
			}
			return reached;
		}
		if (state == State.WAITING && line.equals(STARTING_IN_1)) {
			countdownStartNanos = System.nanoTime() + START_AFTER_COUNTDOWN_MS * 1_000_000L;
			return reached;
		}
		if (state == State.WAITING || state == State.RUNNING) {
			if (PRINCE.matcher(line).find()) stats.princeKilled = true;
			else if (MIMIC.matcher(line).find()) stats.mimicKilled = true;
			// Hypixel posts "Team Score: 304 (S+)" just before the "Defeated" line.
			Matcher sm = TEAM_SCORE.matcher(line);
			if (sm.find()) score = Integer.parseInt(sm.group(1));
		}
		if (state == State.FINISHED) {
			absorbEndLines(line);
			return reached;
		}
		if (floor == null || state == State.IDLE) return reached;

		if (state == State.RUNNING) {
			if (DEATH.matcher(line).find()) {
				deathTimes.add(gameElapsedMs());
				version++;
			} else if (terminalProgress() >= 0) {
				trackTerminals(line);
			}
		}

		for (Milestone m : Triggers.match(line, floor)) {
			if (m == Milestone.WATCHER_DONE) {
				if (state == State.RUNNING && watcherDoneAt < 0 && splits.containsKey(Milestone.BLOOD_OPEN)
						&& !splits.containsKey(Milestone.BLOOD_CLEAR)) {
					watcherDoneAt = gameElapsedMs();
					version++;
				}
				continue;
			}
			if (m == Milestone.START) {
				if (state == State.WAITING) {
					start(0);
					reached.add(m);
				} else if (state == State.RUNNING && lastIndex == 0 && elapsedMs() < 6_000) {
					// Mort speaks at the true start; correct a countdown-based start.
					startNanos = System.nanoTime();
					double tick = LagMonitor.serverTickNow();
					startTick = Double.isNaN(tick) ? Double.NaN : tick;
				}
				continue;
			}
			if (state != State.RUNNING) continue;
			int idx = floor.indexOf(m);
			if (idx <= lastIndex) continue;
			// On F1-F5 the boss's name can also be a blood mob; the real boss comes after blood is cleared.
			if (m == Milestone.BOSS_ENTER && floor.bossNeedsBloodClear() && floor.hasMilestone(Milestone.BLOOD_CLEAR)
					&& !splits.containsKey(Milestone.BLOOD_CLEAR)) continue;

			long at = elapsedMs();
			if (m == Milestone.END) {
				absorbEndLines(line);
				finish(at);
			} else {
				splits.put(m, at);
				gameSplits.put(m, gameElapsedMs());
				lastIndex = idx;
				if (m == Milestone.BOSS_ENTER) clearPctAtBoss = stats.clearPct;
				if (m == Milestone.GOLDOR) {
					gatesDestroyed = 0;
					sectionFraction = 0;
					termProgress.add(new long[]{gameElapsedMs(), 0});
				}
			}
			version++;
			reached.add(m);
			if (state == State.FINISHED) break;
		}
		// Each Watcher line while blood is open is one spawn; their count and cadence predict when blood clears.
		if (state == State.RUNNING && splits.containsKey(Milestone.BLOOD_OPEN) && !splits.containsKey(Milestone.BLOOD_CLEAR)
				&& WATCHER.matcher(line).find() && !line.equals(WATCHER_DONE_LINE)
				&& !line.startsWith("[BOSS] The Watcher: Welcome back")) { // revive lines aren't spawns
			watcherLines.add(gameElapsedMs());
		}
		return reached;
	}

	/** Terminals phase: each "(x/n)" line and each destroyed gate moves progress forward. */
	private void trackTerminals(String line) {
		if (line.equals(GATE_DESTROYED)) {
			if (gatesDestroyed < 3) {
				gatesDestroyed++;
				sectionFraction = 0;
				lastTermCount = null;
				recordProgress();
			}
			return;
		}
		Matcher tm = TERMINAL.matcher(line);
		if (tm.find()) {
			int x = Integer.parseInt(tm.group(1)), n = Integer.parseInt(tm.group(2));
			if (n > 0) {
				double f = Math.min(0.99, x / (double) n);
				if (f > sectionFraction) {
					sectionFraction = f;
					lastTermCount = new int[]{x, n};
					recordProgress();
				}
			}
		}
	}

	private void recordProgress() {
		termProgress.add(new long[]{gameElapsedMs(), Math.round((gatesDestroyed + sectionFraction) * 1000)});
		version++;
	}

	private void start(long alreadyElapsedMs) {
		state = State.RUNNING;
		countdownStartNanos = 0;
		startNanos = System.nanoTime() - alreadyElapsedMs * 1_000_000L;
		double tick = LagMonitor.serverTickNow();
		startTick = Double.isNaN(tick) ? Double.NaN : tick - alreadyElapsedMs / 50.0;
		splits.clear();
		gameSplits.clear();
		splits.put(Milestone.START, 0L);
		gameSplits.put(Milestone.START, 0L);
		lastIndex = 0;
		version++;
	}

	private void finish(long at) {
		long gameAt = gameElapsedMs();
		if (finalMs < 0) finalMs = at;
		// Official time is whole seconds; shift game time by the same rounding.
		finalGameMs = Math.max(0, gameAt + (finalMs - at));
		gameSplits.put(Milestone.END, finalGameMs);
		splits.put(Milestone.END, finalMs);
		lastIndex = floor.indexOf(Milestone.END);
		state = State.FINISHED;
		finishedAtWall = System.currentTimeMillis();
		pendingSave = !demo;
		if (!demo) runDoneOnThisServer = true;
	}

	/** Demo only: jump straight to the next milestone. */
	public void demoAdvance() {
		if (!demo || state != State.RUNNING) return;
		Milestone next = floor.splits.get(lastIndex + 1).milestone();
		long at = elapsedMs();
		if (next == Milestone.END) {
			finish(at);
		} else {
			splits.put(next, at);
			gameSplits.put(next, gameElapsedMs());
			lastIndex++;
		}
		version++;
	}

	static Floor floorFromEntered(boolean master, String roman) {
		if (roman == null) return Floor.E;
		int n = switch (roman) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			case "VI" -> 6;
			case "VII" -> 7;
			default -> 0;
		};
		if (n == 0) return null;
		return Floor.fromId((master ? "M" : "F") + n);
	}

	/** Picks up the official run time and score from the end-of-run summary. */
	private void absorbEndLines(String line) {
		Matcher m = DEFEATED_TIME.matcher(line);
		if (m.find()) {
			int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
			int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
			int sec = Integer.parseInt(m.group(3));
			finalMs = (h * 3600L + min * 60L + sec) * 1000L;
			defeatedSeen = true;
			if (state == State.FINISHED) splits.put(Milestone.END, finalMs);
			return;
		}
		m = TEAM_SCORE.matcher(line);
		if (m.find()) score = Integer.parseInt(m.group(1));
	}

	private void saveRecord(long nowWall) {
		// Only completed runs: Hypixel's "Defeated ... in" line. Failed or abandoned runs would skew the history.
		if (!defeatedSeen || finalMs <= 0) return;

		RunRecord r = new RunRecord();
		r.floor = floor.id;
		r.recordedAt = nowWall;
		r.totalMs = finalMs;
		for (Map.Entry<Milestone, Long> e : splits.entrySet()) {
			if (e.getKey() != Milestone.START) r.splits.put(e.getKey().name(), e.getValue());
		}
		if (!Double.isNaN(startTick)) {
			for (Map.Entry<Milestone, Long> e : gameSplits.entrySet()) {
				if (e.getKey() != Milestone.START && e.getKey() != Milestone.END) r.gameSplits.put(e.getKey().name(), e.getValue());
			}
			r.totalGameMs = finalGameMs;
			r.avgTps = finalMs > 0 ? Math.min(20.0, 20.0 * finalGameMs / finalMs) : -1;
		}
		r.watcherLines.addAll(watcherLines);
		r.clearTimeline.addAll(clearTimeline);
		r.secretsTimeline.addAll(secretsTimeline);
		r.deathTimes.addAll(deathTimes);
		r.termProgress.addAll(termProgress);
		r.watcherDoneAt = watcherDoneAt;
		r.bestEndAtBossMs = bestEndAtBossMs;
		r.predictionTrace.addAll(predictionTrace);
		r.liveScore = stats.liveScore;
		r.modelScore = modelScoreAtEnd;
		r.mimic = stats.mimicKilled;
		r.prince = stats.princeKilled;
		r.clearPctAtBoss = clearPctAtBoss;
		r.partySize = stats.partySize;
		r.partyMembers.addAll(stats.partyMembers);
		r.deaths = Math.max(stats.deaths, deathTimes.size());
		r.secretsPct = stats.secretsPct;
		r.crypts = stats.crypts;
		r.puzzlesFailed = stats.puzzlesFailed;
		r.score = score;
		r.predictedAtBossMs = predictedAtBossMs;
		RunHistory.add(r);
		onSaved.accept(r);
	}
}
