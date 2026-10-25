package com.taz.foraging;

import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel puts "FIG TREE 37%" over a tree being chopped; this shows the closest one in a HUD, says TIMBER! when
 * "TIMBER! You felled the entire Fig Tree!" comes in, and counts trees per hour of chopping.
 * <pre>
 * Tree Progress
 * Fig Tree 37%
 * Felled: Fig 12, Mangrove 3
 * 15 in 22:31 (40/h)
 * </pre>
 */
public final class TreeProgress extends TextHud {
	private static final Pattern HOLOGRAM = Pattern.compile("(FIG|MANGROVE|HELIX) TREE\\D*?(\\d{1,3})%");
	private static final Pattern TIMBER = Pattern.compile("^TIMBER! You felled the entire (\\w+) Tree!");
	private static final double RANGE = 24;
	/** The clock stops when no tree changed for this long. */
	private static final long PAUSE_MS = 120_000;

	private static final Map<String, Integer> FELLED = new LinkedHashMap<>();
	private static String nearest;
	private static int nearestPercent = -1;
	private static long activeMs;
	private static long lastActivityMs;
	private static long lastTickMs;
	private static int ticks;

	public TreeProgress() {
		super("Tree Progress", 0.20, 0.64);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		long step = lastTickMs == 0 ? 0 : Math.min(now - lastTickMs, 1000);
		lastTickMs = now;
		if (!TreeProgressConfig.get().enabled || !Galatea.onIslands() || mc.player == null || mc.level == null) {
			nearest = null;
			return;
		}
		if (lastActivityMs > 0 && now - lastActivityMs < PAUSE_MS) activeMs += step;
		if (++ticks % 5 != 0) return;
		String best = null;
		int bestPercent = -1;
		double bestD = RANGE * RANGE;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || e.getCustomName() == null) continue;
			double d = e.distanceToSqr(mc.player);
			if (d >= bestD) continue;
			Matcher m = HOLOGRAM.matcher(TextUtil.cleanChat(e.getCustomName().getString()));
			if (!m.find()) continue;
			bestD = d;
			best = m.group(1).charAt(0) + m.group(1).substring(1).toLowerCase() + " Tree";
			bestPercent = Integer.parseInt(m.group(2));
		}
		if (best != null && (!best.equals(nearest) || bestPercent != nearestPercent)) lastActivityMs = now; // chopping
		nearest = best;
		nearestPercent = bestPercent;
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		TreeProgressConfig cfg = TreeProgressConfig.get();
		if (!cfg.enabled) return;
		Matcher m = TIMBER.matcher(line);
		if (!m.find()) return;
		String type = m.group(1);
		FELLED.merge(type, 1, Integer::sum);
		lastActivityMs = System.currentTimeMillis();
		if (cfg.timberAlert) Alert.show("§a§lTIMBER!", "§f" + type + " Tree felled", cfg.sound);
	}

	public static int felled(String type) {
		return FELLED.getOrDefault(type, 0);
	}

	public static String nearestTree() {
		return nearest == null ? null : nearest + " " + nearestPercent + "%";
	}

	public static void resetCounts() {
		FELLED.clear();
		activeMs = 0;
		lastActivityMs = 0;
		TazChat.info("Tree counts reset.");
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return TreeProgressConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return TreeProgressConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		TreeProgressConfig cfg = TreeProgressConfig.get();
		List<String> lines = new ArrayList<>();
		if (!preview && !Galatea.onIslands()) return lines;
		boolean counts = cfg.counts && !FELLED.isEmpty();
		if (nearest == null && !counts && !preview) return lines;
		lines.add("§a§lTree Progress");
		if (nearest != null) lines.add("§f" + nearest + " §e" + nearestPercent + "%");
		else if (preview) lines.add("§fFig Tree §e37%");
		if (counts) {
			List<String> parts = new ArrayList<>();
			int total = 0;
			for (Map.Entry<String, Integer> e : FELLED.entrySet()) {
				parts.add(e.getKey() + " " + e.getValue());
				total += e.getValue();
			}
			lines.add("§7Felled: §f" + String.join("§7, §f", parts));
			String rate = activeMs < 60_000 ? "" : " §7(" + String.format("%.1f", total * 3_600_000.0 / activeMs) + "/h)";
			lines.add("§f" + total + " §7in " + clock(activeMs) + rate);
		}
		return lines;
	}

	/** m:ss / h:mm:ss. */
	static String clock(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}
}
