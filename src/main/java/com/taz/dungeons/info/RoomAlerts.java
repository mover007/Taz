package com.taz.dungeons.info;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Room Alerts: a big "Secrets Done!" From NoammAddons. */
public final class RoomAlerts extends TextHud {
	private static final Pattern SECRET_COUNT = Pattern.compile("(\\d+)/(\\d+) Secrets");
	private static final Set<String> DONE = new HashSet<>();
	private static String lastRoom;
	private static int lastFound = -1;
	private static String text;
	private static long untilMs;
	private static ClientLevel level;

	public RoomAlerts() {
		super("Room Alerts", 0.42, 0.40);
	}

	public static void onActionBar(String line) {
		RoomAlertsConfig cfg = RoomAlertsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			level = mc.level;
			DONE.clear();
		}
		if (!cfg.enabled || !cfg.secrets) return;
		Matcher m = SECRET_COUNT.matcher(line);
		DungeonRooms.Room room = DungeonRooms.current();
		if (!m.find() || room == null) return;
		String name = room.data.name();
		int found = Integer.parseInt(m.group(1)), total = Integer.parseInt(m.group(2));
		if (!name.equals(lastRoom)) {
			lastRoom = name;
			lastFound = found;
			if (found >= total) DONE.add(name); // already done when you walked in
			return;
		}
		if (total > 0 && found >= total && lastFound < total && DONE.add(name)) show("§aSecrets Done!", cfg.seconds);
		lastFound = found;
	}

	private static void show(String what, float seconds) {
		text = what;
		untilMs = System.currentTimeMillis() + Math.round(seconds * 1000);
		Alert.play(Alert.Sound.PLING);
	}

	/** Test hook: what's shown, or null. */
	public static String shown() {
		return System.currentTimeMillis() < untilMs ? text : null;
	}

	@Override
	protected HudPos pos() {
		return RoomAlertsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return RoomAlertsConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) return List.of("§aSecrets Done!");
		return System.currentTimeMillis() < untilMs ? List.of(text) : List.of();
	}
}
