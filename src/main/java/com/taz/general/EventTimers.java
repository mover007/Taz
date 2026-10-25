package com.taz.general;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Event Timers HUD and reminders:
 * <pre>
 * Events
 * Jacob's Contest  now (14m left)
 * Dark Auction     39m 12s
 * Spooky Festival  2d 3h
 * </pre>
 */
public final class EventTimers extends TextHud {
	/** Test hook: the clock to use instead of the real one (ms), or 0. */
	public static long testNowMs;
	private static final Map<SkyblockEvent, Long> REMINDED = new EnumMap<>(SkyblockEvent.class);

	public EventTimers() {
		super("Event Timers", 0.005, 0.08);
	}

	static long now() {
		return testNowMs != 0 ? testNowMs : System.currentTimeMillis();
	}

	@Override
	protected HudPos pos() {
		return EventTimersConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return EventTimersConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		EventTimersConfig cfg = EventTimersConfig.get();
		if (!preview && cfg.onlySkyblock && !SkyblockLocation.onSkyblock() && testNowMs == 0) return List.of();
		long nowS = now() / 1000;
		List<String> lines = new ArrayList<>();
		lines.add("§b§lEvents");
		if (cfg.date) lines.add("§7" + SkyblockEvent.date(nowS));
		List<SkyblockEvent> shown = new ArrayList<>(cfg.events);
		shown.sort(Comparator.comparingLong(e -> e.nextStart(nowS)));
		for (SkyblockEvent e : shown) {
			long start = e.nextStart(nowS);
			String when = start <= nowS
					? "§anow" + (e.durationS > 60 ? " §7(" + span(start + e.durationS - nowS) + " left)" : "")
					: "§f" + span(start - nowS);
			lines.add(e.color + e.label + " " + when);
		}
		if (shown.isEmpty()) lines.add("§7No events picked");
		return lines;
	}

	/** 3725 -> "1h 2m", 312 -> "5m 12s", 2 days and more -> "2d 3h". */
	static String span(long s) {
		if (s >= 86400) return s / 86400 + "d " + s % 86400 / 3600 + "h";
		if (s >= 3600) return s / 3600 + "h " + s % 3600 / 60 + "m";
		if (s >= 60) return s / 60 + "m " + s % 60 + "s";
		return s + "s";
	}

	/** Every client tick: reminders. */
	public static void tick() {
		EventTimersConfig cfg = EventTimersConfig.get();
		if (!cfg.enabled) return;
		long nowS = now() / 1000;
		for (SkyblockEvent e : cfg.reminders) {
			if (e == SkyblockEvent.DARK_AUCTION) continue; // it has its own reminder (DarkAuctionReminder)
			long start = e.nextStart(nowS);
			long lead = cfg.remindMinutes * 60L;
			if (start - nowS > lead || start + 5 < nowS) continue; // not yet, or long started
			Long done = REMINDED.get(e);
			if (done != null && done == start) continue;
			REMINDED.put(e, start);
			String in = start <= nowS ? "starting now" : "in " + span(start - nowS);
			Alert.show(e.color + "§l" + e.label, "§f" + in, AlertsConfig.get().sound);
			TazChat.info(e.label + " " + in + ".");
		}
	}

	/** Test hook. */
	public static void clearReminders() {
		REMINDED.clear();
	}
}
