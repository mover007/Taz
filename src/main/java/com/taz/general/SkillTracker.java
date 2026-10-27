package com.taz.general;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.TazChat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Skill XP per hour from the action bar. */
public final class SkillTracker extends TextHud {
	private static final Pattern GAIN = Pattern.compile("\\+([\\d,]+(?:\\.\\d+)?) (Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy"
			+ "|Carpentry|Runecrafting|Social|Taming|Hunting) \\(([\\d,.]+)(%|/([\\d,.]+)([kKmMbB]?))\\)");
	/** A message that hasn't changed for this long is a new gain when it shows again. */
	private static final long SAME_MESSAGE_MS = 4000;

	static final class Skill {
		final String name;
		double gained;
		long activeMs;
		long lastGainMs;
		/** XP into the level and XP the level needs, or -1 when unknown. */
		double current = -1;
		double needed = -1;
		double percent = -1;

		Skill(String name) {
			this.name = name;
		}
	}

	private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();
	private static Skill latest;
	private static String lastMessage = "";
	private static long lastMessageMs;
	private static long lastTickMs;

	public SkillTracker() {
		super("Skill Tracker", 0.50, 0.04);
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return SkillTrackerConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return SkillTrackerConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		if (latest == null) {
			if (!preview) return lines;
			lines.add("§b§lSkill Tracker");
			lines.add("§7Get some skill XP to start");
			return lines;
		}
		long now = System.currentTimeMillis();
		List<Skill> shown = SkillTrackerConfig.get().allSkills ? new ArrayList<>(SKILLS.values()) : List.of(latest);
		for (Skill s : shown) {
			boolean paused = paused(s, now);
			lines.add("§b§l" + s.name + (s == latest ? (paused ? " §c(paused)" : " §a(counting)") : ""));
			long rate = perHour(s);
			lines.add(rate < 0 ? "§7- XP/h" : "§e" + String.format("%,d", rate) + " §fXP/h");
			lines.add("§f+" + String.format("%,d", Math.round(s.gained)) + " §7in " + clock(s.activeMs));
			if (s.current >= 0 && s.needed > 0 && rate > 0) {
				long secs = Math.round((s.needed - s.current) / rate * 3600);
				lines.add("§fLevel up in §e" + EventTimers.span(secs) + " §7(" + String.format("%.1f", s.current * 100 / s.needed) + "%)");
			} else if (s.percent >= 0) {
				lines.add("§7" + String.format("%.1f", s.percent) + "% to next level");
			}
		}
		return lines;
	}

	static String clock(long ms) {
		long s = ms / 1000;
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}

	// ---- tracking

	private static boolean paused(Skill s, long now) {
		return now - s.lastGainMs > SkillTrackerConfig.get().pauseSeconds * 1000L;
	}

	/** XP per hour of training, or -1 until there's 10 seconds of it. */
	static long perHour(Skill s) {
		return s.activeMs < 10_000 ? -1 : Math.round(s.gained * 3_600_000.0 / s.activeMs);
	}

	/** A plain-text action bar message. */
	public static void onActionBar(String text, long now) {
		if (!SkillTrackerConfig.get().enabled) return;
		Matcher m = GAIN.matcher(text);
		if (!m.find()) return;
		String message = m.group();
		Skill s = SKILLS.computeIfAbsent(m.group(2), Skill::new);
		double amount = number(m.group(1), "");
		boolean numbers = m.group(5) != null;
		double gained;
		if (numbers) {
			double current = number(m.group(3), "");
			double needed = number(m.group(5), m.group(6));
			if (s.current < 0 || needed != s.needed) gained = message.equals(lastMessage) && now - lastMessageMs < SAME_MESSAGE_MS ? 0 : amount;
			else gained = current > s.current ? current - s.current : 0;
			s.current = current;
			s.needed = needed;
		} else {
			s.percent = number(m.group(3), "");
			gained = message.equals(lastMessage) && now - lastMessageMs < SAME_MESSAGE_MS ? 0 : amount;
		}
		lastMessage = message;
		lastMessageMs = now;
		if (gained <= 0) return;
		s.gained += gained;
		s.lastGainMs = now;
		latest = s;
	}

	private static double number(String digits, String suffix) {
		double v = Double.parseDouble(digits.replace(",", ""));
		return switch (suffix.toLowerCase()) {
			case "k" -> v * 1_000;
			case "m" -> v * 1_000_000;
			case "b" -> v * 1_000_000_000;
			default -> v;
		};
	}

	/** Every client tick: the clock of every skill still being trained. */
	public static void tick() {
		long now = System.currentTimeMillis();
		long step = lastTickMs == 0 ? 0 : Math.min(now - lastTickMs, 1000);
		lastTickMs = now;
		for (Skill s : SKILLS.values()) if (!paused(s, now)) s.activeMs += step;
	}

	public static void resetCounts() {
		SKILLS.clear();
		latest = null;
		lastMessage = "";
		TazChat.info("Skill tracker reset.");
	}

	// ---- test hooks

	public static double gained(String skill) {
		Skill s = SKILLS.get(skill);
		return s == null ? 0 : s.gained;
	}

	public static long activeMs(String skill) {
		Skill s = SKILLS.get(skill);
		return s == null ? 0 : s.activeMs;
	}

	public static long perHour(String skill) {
		Skill s = SKILLS.get(skill);
		return s == null ? -1 : perHour(s);
	}
}
