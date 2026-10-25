package com.taz.foraging;

import com.taz.util.Alert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Pressure: 85%" on the action bar in Torrhus Springs: a title when it gets high (Galatea > Pressure Warning). */
public final class PressureWarning {
	private static final Pattern PRESSURE = Pattern.compile("Pressure: (\\d+)%");
	/** Warn again only after the pressure dropped this far below the limit. */
	private static final int REARM = 10;

	private static boolean warned;
	private static int last = -1;

	private PressureWarning() {
	}

	/** A plain-text action bar message. */
	public static void onActionBar(String text) {
		PressureConfig cfg = PressureConfig.get();
		if (!cfg.enabled) return;
		Matcher m = PRESSURE.matcher(text);
		if (!m.find()) return;
		last = Integer.parseInt(m.group(1));
		if (last >= cfg.warnAt && !warned) {
			warned = true;
			Alert.show("§c§lPRESSURE " + last + "%", "§fSwim up!", cfg.sound);
		} else if (last < cfg.warnAt - REARM) {
			warned = false;
		}
	}

	/** Test hook: the last pressure read, or -1. */
	public static int last() {
		return last;
	}
}
