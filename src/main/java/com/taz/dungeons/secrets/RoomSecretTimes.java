package com.taz.dungeons.secrets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import com.taz.dungeons.util.DungeonParty;
import com.taz.dungeons.util.TextUtil;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Room Secret Times: how long each room's secrets took, from the first one found to the last, read from Hypixel's own
 * count on the action bar ("3/7 Secrets", the room you're in).
 */
public final class RoomSecretTimes {
	private static final Pattern SECRET_COUNT = Pattern.compile("(\\d+)/(\\d+) Secrets");
	/** A room whose last secret came this soon after its first had nothing to time. */
	private static final long MIN_MS = 500;
	private static final Path TIMES = Taz.CONFIG_DIR.resolve("dungeons").resolve("room-secret-times.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** One room this run: the count when first seen, when its first secret was found, and whether it's done. */
	private static final class Room {
		int seen = -1;
		long startMs;
		boolean done;
	}

	private static final Map<String, Room> RUN = new HashMap<>();
	private static ClientLevel runLevel;
	private static Map<String, Long> best;
	private static String lastParty, lastOwn;

	private RoomSecretTimes() {
	}

	/** The action bar (Taz.onActionBar). */
	public static void onActionBar(String text) {
		if (!RoomTimesConfig.get().enabled) return;
		Matcher m = SECRET_COUNT.matcher(text);
		if (!m.find()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != runLevel) {
			RUN.clear();
			runLevel = mc.level;
		}
		DungeonRooms.Room here = DungeonRooms.current();
		if (here == null) return;
		int found = Integer.parseInt(m.group(1)), total = Integer.parseInt(m.group(2));
		String name = here.data.name();
		Room room = RUN.computeIfAbsent(name, n -> new Room());
		if (room.done) return;
		long now = System.currentTimeMillis();
		if (room.seen < 0) {
			// First look at this room: what was already found doesn't start the clock (maybe a teammate's).
			room.seen = found;
			room.done = found >= total;
			return;
		}
		if (found > room.seen) {
			if (room.startMs == 0) room.startMs = now;
			room.seen = found;
		}
		if (found >= total && room.startMs > 0) {
			room.done = true;
			long time = now - room.startMs;
			if (time >= MIN_MS) finished(name, time, found, total);
		}
	}

	private static void finished(String name, long time, int found, int total) {
		RoomTimesConfig cfg = RoomTimesConfig.get();
		String took = TextUtil.formatTimeTenths(time);
		if (cfg.partyChat && !DungeonParty.solo()) lastParty = TazChat.party(name + " secrets done in " + took);
		Long old = best().get(name);
		if (old == null || time < old) {
			best().put(name, time);
			saveBest();
		}
		if (cfg.ownChat) {
			String vs = old == null ? "first time!" : time < old ? "new best, -" + TextUtil.formatTimeTenths(old - time) : "best " + TextUtil.formatTimeTenths(old);
			lastOwn = name + " secrets done in " + took + " (" + found + "/" + total + ", " + vs + ")";
			TazChat.info(lastOwn);
		}
	}

	public static void resetBest() {
		best().clear();
		saveBest();
		TazChat.info("Room secret best times forgotten.");
	}

	private static Map<String, Long> best() {
		if (best == null) {
			best = new HashMap<>();
			try {
				if (Files.exists(TIMES)) best.putAll(GSON.fromJson(Files.readString(TIMES), new TypeToken<Map<String, Long>>() {}.getType()));
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't read the room secret times", e);
			}
		}
		return best;
	}

	private static void saveBest() {
		try {
			Files.createDirectories(TIMES.getParent());
			Files.writeString(TIMES, GSON.toJson(best()));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save the room secret times", e);
		}
	}

	/** Test hooks: the last party chat line and the last line shown to you. */
	public static String lastParty() {
		return lastParty;
	}

	public static String lastOwn() {
		return lastOwn;
	}
}
