package com.taz.dungeons.run;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;
import com.taz.dungeons.dungeon.Triggers;
import com.taz.dungeons.util.TextUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Rebuilds past dungeon runs from Minecraft's own chat logs (logs/*.log.gz) on startup, so predictions use every run
 * you've played (also ones from before the mod, or while a floor wasn't tracked yet), and a floor starts with real data
 * instead of guesses.
 */
public final class LogImporter {
	private static final Pattern LOG_LINE = Pattern.compile("^\\[(\\d\\d):(\\d\\d):(\\d\\d)] \\[[^]]*]: (?:\\[System] )?\\[CHAT] (.*)$");
	private static final Pattern FILE_NAME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-(\\d+)\\.log\\.gz$");
	private static final Pattern ENTERED = Pattern.compile(" entered (MM )?The Catacombs, (?:Floor ([IVX]+)|Entrance)!");
	private static final Pattern DEFEATED = Pattern.compile("Defeated .+ in (?:(\\d+)h )?(?:(\\d+)m )?(\\d+)s$");
	private static final Pattern TEAM_SCORE = Pattern.compile("^Team Score: (\\d+)");
	/** Chat mods append "(x2)" to repeated lines in the log. */
	private static final Pattern DUPLICATE_SUFFIX = Pattern.compile("\\s*\\(x\\d+\\)$");
	private static final Pattern TERMINAL = Pattern.compile("^\\S+ (?:activated a terminal|activated a lever|completed a device)! \\((\\d)/(\\d)\\)$");
	private static final Pattern DEATH = Pattern.compile("^☠ (?:(?:You were|\\S+ was) killed by |.+ became a ghost\\.$)");
	private static final String WATCHER_PREFIX = "[BOSS] The Watcher: ";
	private static final String WATCHER_DONE_LINE = "[BOSS] The Watcher: You have proven yourself. You may pass.";
	private static final long MATCH_WINDOW_MS = 20_000;

	private LogImporter() {
	}

	/** Parses logs on a background thread, then merges into history on the main thread. */
	public static void runAsync() {
		runAsync(() -> {});
	}

	/** Same, then runs {@code afterMerge} on the main thread. */
	public static void runAsync(Runnable afterMerge) {
		Thread t = new Thread(() -> {
			try {
				List<RunRecord> found = parseAll();
				Minecraft.getInstance().execute(() -> {
					merge(found);
					afterMerge.run();
				});
			} catch (Exception e) {
				DungeonPredictorClient.LOGGER.warn("Chat log import failed", e);
			}
		}, "DungeonPredictor-LogImport");
		t.setDaemon(true);
		t.start();
	}

	private record LogFile(Path path, LocalDate date, int index) {
	}

	private record Line(long wall, String text) {
	}

	private static List<RunRecord> parseAll() throws Exception {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("logs");
		if (!Files.isDirectory(dir)) return List.of();
		LocalDate cutoff;
		try {
			cutoff = LocalDate.parse(ModConfig.get().advanced.historyCutoff);
		} catch (Exception e) {
			cutoff = LocalDate.MIN;
		}
		List<LogFile> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			for (Path p : (Iterable<Path>) s::iterator) {
				Matcher m = FILE_NAME.matcher(p.getFileName().toString());
				if (!m.matches()) continue;
				LocalDate d = LocalDate.parse(m.group(1));
				if (!d.isBefore(cutoff)) files.add(new LogFile(p, d, Integer.parseInt(m.group(2))));
			}
		}
		files.sort(Comparator.comparing(LogFile::date).thenComparingInt(LogFile::index));

		List<RunRecord> out = new ArrayList<>();
		for (LogFile f : files) parseFile(f, out);
		return out;
	}

	/** Collects each dungeon's chat from "entered" to "Defeated", then builds the run from it. */
	private static void parseFile(LogFile f, List<RunRecord> out) throws Exception {
		long dayStart = f.date().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long lastSecOfDay = -1, dayOffset = 0;
		Floor floor = null;
		List<Line> lines = null;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(Files.newInputStream(f.path())), StandardCharsets.UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				Matcher lm = LOG_LINE.matcher(raw);
				if (!lm.matches()) continue;
				long sec = Integer.parseInt(lm.group(1)) * 3600L + Integer.parseInt(lm.group(2)) * 60L + Integer.parseInt(lm.group(3));
				if (lastSecOfDay >= 0 && sec + 12 * 3600 < lastSecOfDay) dayOffset += 86_400_000L; // passed midnight
				lastSecOfDay = sec;
				long wall = dayStart + dayOffset + sec * 1000L;
				String text = DUPLICATE_SUFFIX.matcher(TextUtil.cleanChat(lm.group(4))).replaceAll("");

				Matcher em = ENTERED.matcher(text);
				if (em.find()) {
					floor = RunTracker.floorFromEntered(em.group(1) != null, em.group(2));
					lines = floor == null ? null : new ArrayList<>();
					continue;
				}
				if (lines == null) continue;
				lines.add(new Line(wall, text));
				Matcher dm = DEFEATED.matcher(text);
				if (dm.find()) {
					int h = dm.group(1) == null ? 0 : Integer.parseInt(dm.group(1));
					int min = dm.group(2) == null ? 0 : Integer.parseInt(dm.group(2));
					long total = (h * 3600L + min * 60L + Integer.parseInt(dm.group(3))) * 1000L;
					RunRecord run = build(floor, lines, wall - total, total);
					if (run != null) out.add(run);
					lines = null;
				}
			}
		}
	}

	/** Same milestone rules as RunTracker, applied with times relative to the official start. */
	private static RunRecord build(Floor floor, List<Line> lines, long startWall, long total) {
		Map<Milestone, Long> splits = new EnumMap<>(Milestone.class);
		List<Long> watcher = new ArrayList<>(), deaths = new ArrayList<>();
		List<long[]> terms = new ArrayList<>();
		int lastIndex = 0, gates = 0, score = -1;
		double frac = 0;
		long watcherDone = -1;
		int goldorIdx = floor.indexOf(Milestone.GOLDOR);

		for (Line l : lines) {
			long rel = l.wall() - startWall;
			if (rel < -1000 || rel > total + 2000) continue;
			String text = l.text();
			Matcher sm = TEAM_SCORE.matcher(text);
			if (sm.find()) score = Integer.parseInt(sm.group(1));
			if (rel < 0) continue;
			if (DEATH.matcher(text).find()) deaths.add(rel);

			if (goldorIdx >= 0 && lastIndex == goldorIdx) {
				if (text.equals("The gate has been destroyed!") && gates < 3) {
					gates++;
					frac = 0;
					terms.add(new long[]{rel, gates * 1000L});
				} else {
					Matcher tm = TERMINAL.matcher(text);
					if (tm.find()) {
						int x = Integer.parseInt(tm.group(1)), n = Integer.parseInt(tm.group(2));
						double f = n > 0 ? Math.min(0.99, x / (double) n) : 0;
						if (f > frac) {
							frac = f;
							terms.add(new long[]{rel, Math.round((gates + frac) * 1000)});
						}
					}
				}
			}

			boolean bloodOpen = splits.containsKey(Milestone.BLOOD_OPEN);
			for (Milestone m : Triggers.match(text, floor)) {
				if (m == Milestone.START || m == Milestone.END) continue;
				if (m == Milestone.WATCHER_DONE) {
					if (bloodOpen && !splits.containsKey(Milestone.BLOOD_CLEAR) && watcherDone < 0) watcherDone = rel;
					continue;
				}
				int idx = floor.indexOf(m);
				if (idx <= lastIndex) continue;
				if (m == Milestone.BOSS_ENTER && floor.bossNeedsBloodClear() && floor.hasMilestone(Milestone.BLOOD_CLEAR)
						&& !splits.containsKey(Milestone.BLOOD_CLEAR)) continue;
				splits.put(m, rel);
				lastIndex = idx;
				if (m == Milestone.GOLDOR) terms.add(new long[]{rel, 0});
			}
			if (bloodOpen && !splits.containsKey(Milestone.BLOOD_CLEAR) && text.startsWith(WATCHER_PREFIX)
					&& !text.equals(WATCHER_DONE_LINE) && !text.startsWith(WATCHER_PREFIX + "Welcome back")) {
				watcher.add(rel);
			}
		}
		if (!splits.containsKey(Milestone.BOSS_ENTER) && floor.bossName != null) return null; // incomplete log

		RunRecord r = new RunRecord();
		r.source = "log";
		r.floor = floor.id;
		r.totalMs = total;
		r.recordedAt = startWall + total + 3000;
		for (Map.Entry<Milestone, Long> e : splits.entrySet()) r.splits.put(e.getKey().name(), e.getValue());
		r.watcherLines.addAll(watcher);
		r.watcherDoneAt = watcherDone;
		r.deathTimes.addAll(deaths);
		r.deaths = deaths.size();
		r.termProgress.addAll(terms);
		r.score = score;
		return r;
	}

	/** Main thread: add new runs, fill gaps in / repair matching ones, drop failed runs. */
	private static void merge(List<RunRecord> found) {
		int added = 0, repaired = 0, enriched = 0;
		int removed = RunHistory.removeIf(r -> r.score >= 0 && r.score < 100); // failed/abandoned runs saved by old versions
		List<RunRecord> existing = RunHistory.all();
		List<RunRecord> toAdd = new ArrayList<>();
		for (RunRecord imp : found) {
			RunRecord match = null;
			for (RunRecord ex : existing) {
				if (ex.floor.equals(imp.floor) && Math.abs(ex.recordedAt - imp.recordedAt) <= MATCH_WINDOW_MS
						&& Math.abs(ex.totalMs - imp.totalMs) <= 1500) {
					match = ex;
					break;
				}
			}
			if (match == null) {
				toAdd.add(imp);
				added++;
			} else if (match.bossEntryLooksWrong()) {
				repair(match, imp);
				repaired++;
			} else if (enrich(match, imp)) {
				enriched++;
			}
		}
		if (added > 0) RunHistory.addAll(toAdd);
		else if (repaired > 0 || enriched > 0 || removed > 0) RunHistory.save();
		if (added + repaired + enriched + removed > 0) {
			DungeonPredictorClient.LOGGER.info("Chat logs: imported {} runs, repaired {}, filled gaps in {}, dropped {} failed",
					added, repaired, enriched, removed);
		}
	}

	private static double gameRatio(RunRecord r) {
		return r.totalGameMs > 0 && r.totalMs > 0 ? r.totalGameMs / (double) r.totalMs : 1.0;
	}

	/** Adds phases, deaths and terminal progress the live recording didn't have. */
	private static boolean enrich(RunRecord ex, RunRecord fromLog) {
		boolean changed = false;
		double ratio = gameRatio(ex);
		boolean hasGame = ex.gameSplits != null && !ex.gameSplits.isEmpty();
		for (Map.Entry<String, Long> e : fromLog.splits.entrySet()) {
			if (!ex.splits.containsKey(e.getKey())) {
				ex.splits.put(e.getKey(), e.getValue());
				if (hasGame) ex.gameSplits.put(e.getKey(), Math.round(e.getValue() * ratio));
				changed = true;
			}
		}
		if ((ex.deathTimes == null || ex.deathTimes.isEmpty()) && !fromLog.deathTimes.isEmpty()) {
			ex.deathTimes = new ArrayList<>();
			for (Long d : fromLog.deathTimes) ex.deathTimes.add(Math.round(d * ratio));
			changed = true;
		}
		if ((ex.termProgress == null || ex.termProgress.isEmpty()) && !fromLog.termProgress.isEmpty()) {
			ex.termProgress = new ArrayList<>();
			for (long[] p : fromLog.termProgress) ex.termProgress.add(new long[]{Math.round(p[0] * ratio), p[1]});
			changed = true;
		}
		return changed;
	}

	private static void repair(RunRecord broken, RunRecord fromLog) {
		double ratio = gameRatio(broken);
		broken.splits.clear();
		broken.splits.putAll(fromLog.splits);
		if (broken.gameSplits != null && !broken.gameSplits.isEmpty()) {
			broken.gameSplits.clear();
			for (Map.Entry<String, Long> e : fromLog.splits.entrySet()) {
				broken.gameSplits.put(e.getKey(), Math.round(e.getValue() * ratio));
			}
		}
		broken.watcherLines.clear();
		for (Long l : fromLog.watcherLines) broken.watcherLines.add(Math.round(l * ratio));
		broken.watcherDoneAt = fromLog.watcherDoneAt;
		// Recorded only up to the false boss entry, so incomplete.
		broken.clearPctAtBoss = -1;
		broken.clearTimeline.clear();
		if (broken.secretsTimeline != null) broken.secretsTimeline.clear();
		broken.predictedAtBossMs = -1;
	}
}
