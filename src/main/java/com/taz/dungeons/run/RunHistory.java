package com.taz.dungeons.run;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Completed runs, saved to config/taz/dungeons/history.json. Oldest first. */
public final class RunHistory {
	private static final Path FILE = ModConfig.DIR.resolve("history.json");
	private static final int MAX_PER_FLOOR = 300;
	/** Only the newest runs per floor keep their prediction traces (they're the bulk of the file). */
	private static final int TRACES_KEPT_PER_FLOOR = 30;
	private static final Type LIST_TYPE = new TypeToken<List<RunRecord>>() {}.getType();

	/** One writer thread so saves never overlap. */
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "DungeonPredictor-IO");
		t.setDaemon(true);
		return t;
	});

	private static final List<RunRecord> runs = new ArrayList<>();
	/** Backtests: only runs recorded before this time are visible. */
	private static long replayLimit = Long.MAX_VALUE;

	public static void setReplayLimit(long beforeMillis) {
		replayLimit = beforeMillis;
		version++;
	}

	/** Bumped on every change so predictions can cache history-derived statistics. */
	private static int version;

	public static int version() {
		return version;
	}

	/** Removes matching runs; returns how many. */
	public static int removeIf(java.util.function.Predicate<RunRecord> filter) {
		int before = runs.size();
		runs.removeIf(filter);
		if (runs.size() != before) version++;
		return before - runs.size();
	}

	private RunHistory() {
	}

	public static void load() {
		runs.clear();
		try {
			if (Files.exists(FILE)) {
				List<RunRecord> loaded = ModConfig.GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), LIST_TYPE);
				if (loaded != null) {
					for (RunRecord r : loaded) {
						if (r != null && Floor.fromId(r.floor) != null && r.totalMs > 0) runs.add(r);
					}
				}
			}
		} catch (Exception e) {
			DungeonPredictorClient.LOGGER.error("Failed to read {}; it will be backed up and replaced on the next save", FILE, e);
			try {
				Files.copy(FILE, FILE.resolveSibling("history.json.corrupt"), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {
			}
		}
		for (RunRecord r : runs) {
			// Fields added in later versions are null in older files.
			if (r.deathTimes == null) r.deathTimes = new ArrayList<>();
			if (r.termProgress == null) r.termProgress = new ArrayList<>();
			if (r.secretsTimeline == null) r.secretsTimeline = new ArrayList<>();
			if (r.predictionTrace == null) r.predictionTrace = new ArrayList<>();
		}
		runs.sort(java.util.Comparator.comparingLong(r -> r.recordedAt));
		version++;
	}

	public static void add(RunRecord record) {
		runs.add(record);
		long sameFloor = runs.stream().filter(r -> r.floor.equals(record.floor)).count();
		if (sameFloor > MAX_PER_FLOOR) {
			for (int i = 0; i < runs.size(); i++) {
				if (runs.get(i).floor.equals(record.floor)) {
					runs.remove(i);
					break;
				}
			}
		}
		save();
	}

	private static void trimTraces() {
		java.util.Map<String, Integer> seen = new java.util.HashMap<>();
		for (int i = runs.size() - 1; i >= 0; i--) {
			RunRecord r = runs.get(i);
			int n = seen.merge(r.floor, 1, Integer::sum);
			if (n > TRACES_KEPT_PER_FLOOR && r.predictionTrace != null && !r.predictionTrace.isEmpty()) r.predictionTrace.clear();
		}
	}

	/** Every stored run (all floors), oldest first. Read-only view. */
	public static List<RunRecord> all() {
		return java.util.Collections.unmodifiableList(runs);
	}

	/** Adds several runs (e.g. imported from logs), keeps history in time order, then saves. */
	public static void addAll(List<RunRecord> records) {
		runs.addAll(records);
		runs.sort(java.util.Comparator.comparingLong(r -> r.recordedAt));
		for (Floor f : Floor.values()) {
			long n = runs.stream().filter(r -> r.floor.equals(f.id)).count();
			for (int i = 0; i < runs.size() && n > MAX_PER_FLOOR; i++) {
				if (runs.get(i).floor.equals(f.id)) {
					runs.remove(i--);
					n--;
				}
			}
		}
		save();
	}

	public static void save() {
		version++;
		trimTraces();
		String json = ModConfig.GSON.toJson(new ArrayList<>(runs), LIST_TYPE);
		IO.execute(() -> {
			try {
				Files.createDirectories(ModConfig.DIR);
				Path tmp = FILE.resolveSibling("history.json.tmp");
				Files.writeString(tmp, json, StandardCharsets.UTF_8);
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception e) {
				DungeonPredictorClient.LOGGER.error("Failed to write {}", FILE, e);
			}
		});
	}

	/** The most recent {@code window} runs on this floor recorded on or after the cutoff, oldest first. */
	public static List<RunRecord> recent(Floor floor, long cutoffMillis, int window) {
		List<RunRecord> out = new ArrayList<>();
		for (int i = runs.size() - 1; i >= 0 && out.size() < window; i--) {
			RunRecord r = runs.get(i);
			if (r.floor.equals(floor.id) && r.recordedAt >= cutoffMillis && r.recordedAt < replayLimit) out.add(0, r);
		}
		return out;
	}

	public static int count(Floor floor, long cutoffMillis) {
		int n = 0;
		for (RunRecord r : runs) {
			if (r.floor.equals(floor.id) && r.recordedAt >= cutoffMillis && r.recordedAt < replayLimit) n++;
		}
		return n;
	}

	public static OptionalLong personalBest(Floor floor, long cutoffMillis) {
		return runs.stream()
				.filter(r -> r.floor.equals(floor.id) && r.recordedAt >= cutoffMillis && r.recordedAt < replayLimit)
				.mapToLong(r -> r.totalMs)
				.min();
	}

	/** Best time on this floor not counting {@code exclude} (used to spot a new PB). */
	public static OptionalLong personalBestExcluding(Floor floor, long cutoffMillis, RunRecord exclude) {
		return runs.stream()
				.filter(r -> r != exclude && r.floor.equals(floor.id) && r.recordedAt >= cutoffMillis)
				.mapToLong(r -> r.totalMs)
				.min();
	}

	public static int clear(Floor floor) {
		int before = runs.size();
		runs.removeIf(r -> r.floor.equals(floor.id));
		save();
		return before - runs.size();
	}
}
