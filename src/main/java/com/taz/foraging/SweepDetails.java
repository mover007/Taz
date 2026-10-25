package com.taz.foraging;

import com.taz.dungeons.util.TextUtil;
import com.taz.general.AlertsConfig;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's sweep report after each chop:
 * <pre>
 * Sweep Details: 120∮ Sweep
 *   Fig Tree Toughness: 10 12.0 Logs
 *   Axe throw: -50% Sweep 6.0 Logs
 *   Wrong Style: -50% Sweep 3.0 Logs Cut the trunk first!!
 * </pre>
 * shown as a HUD for 30 seconds, with the chat lines hidden and a title for the wrong order.
 */
public final class SweepDetails extends TextHud {
	private static final Pattern SWEEP = Pattern.compile("^Sweep Details: ([\\d,.]+).{0,2} Sweep$");
	private static final Pattern TOUGHNESS = Pattern.compile("^(.+?) Tree Toughness: ([\\d.]+) ([\\d.]+) Logs$");
	private static final Pattern AXE_THROW = Pattern.compile("^Axe throw: (-\\d+)% Sweep ([\\d.]+) Logs$");
	private static final Pattern WRONG_STYLE = Pattern.compile("^Wrong Style: (-\\d+)% Sweep ([\\d.]+) Logs ?(.*?)!*$");
	/** Follow-up lines belong to the report for this long. */
	private static final long REPORT_MS = 5_000;
	private static final long SHOW_MS = 30_000;

	private static long reportMs;
	private static String sweep;
	private static String tree;
	private static String toughness;
	private static String logs;
	private static String axePenalty;
	private static String stylePenalty;
	private static String rightStyle;

	public SweepDetails() {
		super("Sweep Details", 0.52, 0.64);
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		if (!SweepConfig.get().enabled) return;
		long now = System.currentTimeMillis();
		Matcher m;
		if ((m = SWEEP.matcher(line)).find()) {
			reportMs = now;
			sweep = m.group(1);
			tree = toughness = logs = axePenalty = stylePenalty = rightStyle = null;
			return;
		}
		if (now - reportMs > REPORT_MS) return;
		if ((m = TOUGHNESS.matcher(line)).find()) {
			tree = m.group(1);
			toughness = m.group(2);
			logs = m.group(3);
		} else if ((m = AXE_THROW.matcher(line)).find()) {
			axePenalty = m.group(1);
			logs = m.group(2);
		} else if ((m = WRONG_STYLE.matcher(line)).find()) {
			stylePenalty = m.group(1);
			logs = m.group(2);
			rightStyle = m.group(3).trim();
			if (SweepConfig.get().wrongStyleAlert) {
				Alert.show("§c§lWRONG ORDER " + stylePenalty + "%", "§f" + (rightStyle.isEmpty() ? "Cut in the right order" : rightStyle),
						AlertsConfig.get().sound);
			}
		}
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: hides the report's lines. */
	public static boolean allow(Component message, boolean overlay) {
		SweepConfig cfg = SweepConfig.get();
		if (overlay || !cfg.enabled || !cfg.hideChat) return true;
		String line = TextUtil.cleanChat(message.getString());
		if (SWEEP.matcher(line).find()) return false;
		boolean followUp = TOUGHNESS.matcher(line).find() || AXE_THROW.matcher(line).find() || WRONG_STYLE.matcher(line).find();
		return !(followUp && System.currentTimeMillis() - reportMs <= REPORT_MS);
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return SweepConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return SweepConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		boolean recent = sweep != null && System.currentTimeMillis() - reportMs < SHOW_MS;
		if (!recent && !preview) return lines;
		if (!recent) {
			lines.add("§6§lSweep §f120");
			lines.add("§fFig Tree §7toughness §f10 §7→ §a12.0 logs");
			return lines;
		}
		lines.add("§6§lSweep §f" + sweep);
		if (tree != null) lines.add("§f" + tree + " Tree §7toughness §f" + toughness + " §7→ §a" + logs + " logs");
		else if (logs != null) lines.add("§a" + logs + " logs");
		if (axePenalty != null) lines.add("§eAxe throw " + axePenalty + "%");
		if (stylePenalty != null) lines.add("§cWrong order " + stylePenalty + "%" + (rightStyle.isEmpty() ? "" : ": " + rightStyle));
		return lines;
	}
}
