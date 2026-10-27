package com.taz.foraging;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.xpassist.npc.SkyblockLocation;

import java.util.ArrayList;
import java.util.List;

/** Shown on Galatea and Torrhus Canyon, or anywhere once whispers have been counted. */
public final class WhisperHud extends TextHud {
	public static final double DEFAULT_X = 0.80;
	public static final double DEFAULT_Y = 0.30;

	public WhisperHud() {
		super("Whisper Counter", DEFAULT_X, DEFAULT_Y);
	}

	@Override
	protected HudPos pos() {
		return WhisperConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return WhisperConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		WhisperConfig cfg = WhisperConfig.get();
		boolean counted = WhisperTracker.activeMs() > 0 || WhisperTracker.seen(WhisperTracker.Type.FOREST)
				|| WhisperTracker.seen(WhisperTracker.Type.DESERT);
		boolean here = TreeTracker.isForagingIsland(SkyblockLocation.area()) || WhisperTracker.testLines != null;
		if (!preview && !(here && counted)) return List.of();

		List<String> lines = new ArrayList<>();
		boolean paused = WhisperTracker.paused();
		lines.add("§b§lWhispers" + (WhisperTracker.activeMs() == 0 ? "" : paused ? " §c(paused)" : " §a(counting)"));
		for (WhisperTracker.Type t : WhisperTracker.Type.values()) {
			if (t == WhisperTracker.Type.FOREST && !cfg.forest || t == WhisperTracker.Type.DESERT && !cfg.desert) continue;
			long rate = WhisperTracker.perHour(t);
			String perHour = rate < 0 ? "§7-" : "§f" + String.format("%,d", rate) + "/h";
			lines.add(t.color + t.label + " " + perHour + " §7+" + String.format("%,d", WhisperTracker.gained(t)));
		}
		lines.add("§7Foraging §f" + time(WhisperTracker.activeMs()));
		return lines;
	}

	/** 2533000 -> "42:13", 4000000 -> "1:06:40". */
	static String time(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}
}
