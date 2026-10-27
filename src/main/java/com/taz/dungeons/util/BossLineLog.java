package com.taz.dungeons.util;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.Feature;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.dungeon.Floor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** Writes each distinct "[BOSS] ..." / "[NPC] Mort: ..." line to boss-lines.log. */
public final class BossLineLog {
	private static final Path FILE = ModConfig.DIR.resolve("boss-lines.log");
	private static final Set<String> seen = new HashSet<>();

	private BossLineLog() {
	}

	public static void load() {
		seen.clear();
		try {
			if (Files.exists(FILE)) {
				for (String l : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
					int tab = l.indexOf('\t');
					seen.add(tab >= 0 ? l.substring(tab + 1) : l);
				}
			}
		} catch (IOException e) {
			DungeonPredictorClient.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	public static void maybeLog(String line, Floor floor) {
		if (!ModConfig.on(Feature.BOSS_LINE_LOG)) return;
		if (!line.startsWith("[BOSS] ") && !line.startsWith("[NPC] Mort:")) return;
		if (!seen.add(line)) return;
		try {
			Files.createDirectories(ModConfig.DIR);
			Files.writeString(FILE, (floor == null ? "?" : floor.id) + "\t" + line + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			DungeonPredictorClient.LOGGER.warn("Could not write {}", FILE, e);
		}
	}
}
