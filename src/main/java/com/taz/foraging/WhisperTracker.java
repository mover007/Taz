package com.taz.foraging;

import com.taz.dungeons.util.TextUtil;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts Forest and Desert Whispers per hour of foraging.
 *
 * Hypixel's tab list shows your totals ("Forest Whispers: 12,345", "Desert Whispers: 1,234"); every increase
 * is whispers gained, from any source, except the refund when you reset your Heart of the Forest. The clock only runs while you forage: hitting a log, a log breaking
 * next to you, or whispers coming in keep it going, and it stops after a few seconds without any of those.
 */
public final class WhisperTracker {
	public enum Type {
		FOREST("Forest", "§3"),
		DESERT("Desert", "§6");

		public final String label;
		public final String color;

		Type(String label, String color) {
			this.label = label;
			this.color = color;
		}
	}

	/** "Forest Whispers: 12,345" (also "17.5k" just in case it's ever abbreviated). */
	private static final Pattern TOTAL = Pattern.compile("(Forest|Desert) Whispers: ?([\\d,]+(?:\\.\\d+)?)([kKmM])?");
	private static final int READ_INTERVAL = 10;
	/** A log breaking this close to you counts as you foraging (sweep breaks several at once). */
	private static final double NEAR_LOG = 7.0;
	/** "You have reset your Heart of the Forest! Your Perks and Abilities have been reset." refunds the whispers spent. */
	private static final String HOTF_RESET = "You have reset your Heart of the Forest!";
	/** The tab list can take a few seconds to show the refund; rises until then are the refund, not income. */
	private static final long RESET_WINDOW_MS = 15_000;

	private static final long[] last = new long[Type.values().length];
	private static final boolean[] seen = new boolean[Type.values().length];
	private static final long[] gained = new long[Type.values().length];
	private static long activeMs;
	private static long lastActivityMs;
	private static long lastTickMs;
	private static int tickCounter;
	private static long resetUntilMs;
	/** Test hook: tab list lines to read instead of the real tab list. */
	public static List<String> testLines;

	private WhisperTracker() {
	}

	// ---- queries

	/** Whispers gained of a type since the last reset. */
	public static long gained(Type t) {
		return gained[t.ordinal()];
	}

	/** Its total has been read from the tab list this session. */
	public static boolean seen(Type t) {
		return seen[t.ordinal()];
	}

	/** Foraging time counted so far. */
	public static long activeMs() {
		return activeMs;
	}

	public static boolean paused() {
		return System.currentTimeMillis() - lastActivityMs > WhisperConfig.get().pauseSeconds * 1000L;
	}

	/** Whispers per hour of foraging, or -1 until there's enough time to say (10 seconds). */
	public static long perHour(Type t) {
		if (activeMs < 10_000) return -1;
		return Math.round(gained[t.ordinal()] * 3_600_000.0 / activeMs);
	}

	public static void reset() {
		for (int i = 0; i < gained.length; i++) gained[i] = 0;
		activeMs = 0;
		lastActivityMs = 0;
		TazChat.info("Whisper counter reset.");
	}

	// ---- updates

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		long step = lastTickMs == 0 ? 0 : Math.min(now - lastTickMs, 1000);
		lastTickMs = now;
		if (!WhisperConfig.get().enabled || mc.player == null || mc.level == null) return;

		if (hittingLog(mc)) lastActivityMs = now;
		if (++tickCounter % READ_INTERVAL == 0) readTotals(tabLines(mc), now);
		if (!paused()) activeMs += step;
	}

	/** From the block-change hook: a tree log breaking right next to you means you're chopping it. */
	public static void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		if (!WhisperConfig.get().enabled || !isLog(oldState) || isLog(newState)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && pos.distToCenterSqr(mc.player.position()) <= NEAR_LOG * NEAR_LOG) {
			lastActivityMs = System.currentTimeMillis();
		}
	}

	private static boolean hittingLog(Minecraft mc) {
		if (!mc.options.keyAttack.isDown() || mc.screen != null) return false;
		HitResult hit = mc.hitResult;
		return hit != null && hit.getType() == HitResult.Type.BLOCK && isLog(mc.level.getBlockState(((BlockHitResult) hit).getBlockPos()));
	}

	private static boolean isLog(BlockState s) {
		return TreeTracker.kindOf(s) != null || s.is(BlockTags.LOGS);
	}

	private static List<String> tabLines(Minecraft mc) {
		if (testLines != null) return testLines;
		List<String> out = new ArrayList<>();
		if (mc.getConnection() == null) return out;
		for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name != null) out.add(TextUtil.cleanScoreboard(name.getString()));
		}
		return out;
	}

	/** Reads the totals; an increase is whispers gained (and counts as foraging), a drop is whispers spent. */
	static void readTotals(List<String> lines, long now) {
		for (String line : lines) {
			Matcher m = TOTAL.matcher(line);
			if (!m.find()) continue;
			Type t = m.group(1).equals("Forest") ? Type.FOREST : Type.DESERT;
			long value = parse(m.group(2), m.group(3));
			int i = t.ordinal();
			if (seen[i] && value > last[i] && now < resetUntilMs) {
				// HOTF reset refund: a new starting point, not whispers earned.
			} else if (seen[i] && value > last[i]) {
				gained[i] += value - last[i];
				lastActivityMs = now;
			}
			last[i] = value;
			seen[i] = true;
		}
	}

	private static long parse(String number, String suffix) {
		double v = Double.parseDouble(number.replace(",", ""));
		if (suffix != null) v *= suffix.equalsIgnoreCase("k") ? 1_000 : 1_000_000;
		return Math.round(v);
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		if (line.startsWith(HOTF_RESET)) resetUntilMs = System.currentTimeMillis() + RESET_WINDOW_MS;
	}

	/** On server change: totals are read fresh (switching profile or lobby must not count as a gain). */
	public static void onServerChange() {
		for (int i = 0; i < seen.length; i++) seen[i] = false;
	}
}
