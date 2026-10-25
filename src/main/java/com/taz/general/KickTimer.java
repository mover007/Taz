package com.taz.general;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.TazChat;

import java.util.List;

/**
 * Kick Timer: after Hypixel kicks you from SkyBlock ("There was a problem joining SkyBlock", "You were kicked while
 * joining that server!"), how long ago it was, so you know when you can rejoin. From NoammAddons' SB Kick.
 */
public final class KickTimer extends TextHud {
	private static final long SHOW_MS = 60_000;
	private static long kickedMs;

	public KickTimer() {
		super("Kick Timer", 0.40, 0.42);
	}

	public static void onChat(String line) {
		KickTimerConfig cfg = KickTimerConfig.get();
		if (!cfg.enabled) return;
		long now = System.currentTimeMillis();
		boolean showing = now - kickedMs < SHOW_MS;
		switch (line) {
			case "There was a problem joining SkyBlock, try again in a moment!" -> {
				if (!showing) kickedMs = now;
			}
			case "A kick occurred in your connection, so you were put in the SkyBlock lobby!", "You were kicked while joining that server!" -> {
				if (showing) return;
				kickedMs = now;
				if (cfg.party) TazChat.party("I got kicked, back in a minute");
			}
			default -> {
			}
		}
	}

	/** Test hook. */
	public static boolean showing() {
		return System.currentTimeMillis() - kickedMs < SHOW_MS;
	}

	@Override
	protected HudPos pos() {
		return KickTimerConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return KickTimerConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		long ago = preview ? 12_340 : System.currentTimeMillis() - kickedMs;
		if (!preview && ago >= SHOW_MS) return List.of();
		return List.of("§cKicked from SkyBlock §f" + String.format("%.1fs", ago / 1000.0) + " ago");
	}
}
