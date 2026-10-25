package com.taz.dungeons.util;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.run.RunTracker;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes config/taz/dungeons/debug-scoreboard.txt on each dungeon visit: once 15s after
 * entering and once 45s into the run. It holds the raw sidebar and tab-list text plus what
 * was parsed from it, so parsing problems can be fixed without asking for screenshots.
 */
public final class Diagnostics {
	private static final Path FILE = ModConfig.DIR.resolve("debug-scoreboard.txt");
	private static final long PRE_START_DELAY_MS = 15_000;
	private static final long IN_RUN_DELAY_MS = 45_000;

	private static int stage;
	private static long enteredAt;
	private static long startedAt;

	private Diagnostics() {
	}

	public static void onServerChange() {
		stage = 0;
		enteredAt = startedAt = 0;
	}

	/** Whether the next poll should keep raw text. */
	public static boolean wantsRaw() {
		return stage < 2;
	}

	public static void tick(DungeonState ds, RunTracker t, boolean mixinChat) {
		if (stage >= 2 || t.isDemo() || t.state() == RunTracker.State.IDLE || !ModConfig.on(Feature.DIAGNOSTICS)) return;
		long now = System.currentTimeMillis();
		if (enteredAt == 0) enteredAt = now;
		if (startedAt == 0 && t.state() == RunTracker.State.RUNNING) startedAt = now;

		if (stage == 0 && now - enteredAt > PRE_START_DELAY_MS) {
			write(ds, t, mixinChat, "pre-start", false);
			stage = 1;
		} else if (stage == 1 && startedAt > 0 && now - startedAt > IN_RUN_DELAY_MS) {
			write(ds, t, mixinChat, "in-run", true);
			stage = 2;
		}
	}

	private static void write(DungeonState ds, RunTracker t, boolean mixinChat, String label, boolean append) {
		StringBuilder sb = new StringBuilder();
		String version = FabricLoader.getInstance().getModContainer(DungeonPredictorClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		sb.append("===== Dungeon Run Predictor ").append(version).append(" - ").append(label)
				.append(" - ").append(LocalDateTime.now()).append(" =====\n");
		sb.append("tracker: state=").append(t.state()).append(" floor=").append(t.floor())
				.append(" chatFloor=").append(t.chatFloor()).append(" elapsed=").append(TextUtil.formatTimeTenths(t.elapsedMs()))
				.append(" lastMilestoneIndex=").append(t.lastIndex()).append('\n');
		sb.append("chat hook: ").append(mixinChat ? "packet mixin" : "fabric events only").append('\n');
		sb.append("sidebar: source=").append(ds.sidebarSource).append(" floor=").append(ds.floor)
				.append(" timeElapsedSec=").append(ds.timeElapsedSec).append(" cleared%=").append(ds.clearedPct).append('\n');
		sb.append("tab: party=").append(ds.partySize).append(' ').append(ds.partyMembers)
				.append(" completedRooms=").append(ds.completedRooms).append(" secrets%=").append(ds.secretsPct)
				.append(" deaths=").append(ds.deaths).append(" crypts=").append(ds.crypts)
				.append(" puzzles=").append(ds.puzzlesDone).append('/').append(ds.puzzlesTotal)
				.append(" failed=").append(ds.puzzlesFailed).append('\n');
		section(sb, "sidebar lines", ds.rawSidebar);
		section(sb, "team prefix+suffix scan", ds.rawTeams);
		section(sb, "tab list lines", ds.rawTab);
		sb.append('\n');
		try {
			Files.createDirectories(ModConfig.DIR);
			if (append) {
				Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} else {
				Files.writeString(FILE, sb.toString(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			DungeonPredictorClient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	private static void section(StringBuilder sb, String title, List<String> lines) {
		sb.append("--- ").append(title).append(" (").append(lines.size()).append(") ---\n");
		for (String l : lines) sb.append(escape(l)).append('\n');
	}

	/** Shows non-ASCII characters as \\uXXXX so invisible or odd glyphs are visible. */
	private static String escape(String s) {
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0x20 && c <= 0x7E) out.append(c);
			else if (c == '\n') out.append("\\n");
			else out.append(String.format("\\u%04X", (int) c));
		}
		return out.toString();
	}
}
