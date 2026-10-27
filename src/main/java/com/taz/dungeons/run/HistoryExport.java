package com.taz.dungeons.run;

import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Writes the run history as a spreadsheet-friendly CSV (one row per run, split times in seconds). */
public final class HistoryExport {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	private HistoryExport() {
	}

	public static Path exportCsv() throws IOException {
		Path file = ModConfig.DIR.resolve("history.csv");
		StringBuilder sb = new StringBuilder("floor,finished,source,total_s");
		for (Milestone m : Milestone.values()) {
			if (m != Milestone.START && m != Milestone.END && m != Milestone.WATCHER_DONE) sb.append(',').append(m.name().toLowerCase(Locale.ROOT)).append("_s");
		}
		sb.append(",deaths,score,party_size,portal_estimate_s,estimate_error_s\n");
		for (RunRecord r : RunHistory.all()) {
			if (Floor.fromId(r.floor) == null) continue;
			sb.append(r.floor).append(',').append(TIME.format(Instant.ofEpochMilli(r.recordedAt))).append(',')
					.append(r.source).append(',').append(sec(r.totalMs));
			for (Milestone m : Milestone.values()) {
				if (m == Milestone.START || m == Milestone.END || m == Milestone.WATCHER_DONE) continue;
				Long v = r.split(m);
				sb.append(',').append(v == null ? "" : sec(v));
			}
			sb.append(',').append(r.deaths >= 0 ? r.deaths : "")
					.append(',').append(r.score >= 0 ? r.score : "")
					.append(',').append(r.partySize > 0 ? r.partySize : "")
					.append(',').append(r.predictedAtBossMs > 0 ? sec(r.predictedAtBossMs) : "")
					.append(',').append(r.predictedAtBossMs > 0 ? sec(r.totalMs - r.predictedAtBossMs) : "")
					.append('\n');
		}
		Files.createDirectories(ModConfig.DIR);
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		return file;
	}

	private static String sec(long ms) {
		return String.format(Locale.ROOT, "%.1f", ms / 1000.0);
	}
}
