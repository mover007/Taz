package com.taz.dungeons.info;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Blessing Display: the dungeon's blessings and their levels, from the tab list footer ("Blessing of Power XIX"). From Odin. */
public final class Blessings extends TextHud {
	public enum Blessing {POWER, TIME, STONE, LIFE, WISDOM}

	private static final Pattern BLESSING = Pattern.compile("Blessing of (Power|Time|Stone|Life|Wisdom) ([IVXLC]+)");
	private static final Map<Blessing, Integer> LEVELS = new EnumMap<>(Blessing.class);

	public Blessings() {
		super("Blessings", 0.01, 0.40);
	}

	/** The tab list's footer changed (ClientPacketListenerMixin). */
	public static void onTabFooter(String footer) {
		LEVELS.clear();
		Matcher m = BLESSING.matcher(footer);
		while (m.find()) LEVELS.put(Blessing.valueOf(m.group(1).toUpperCase()), roman(m.group(2)));
	}

	/** "XIX" is 19. */
	static int roman(String s) {
		int total = 0, last = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				default -> 100;
			};
			total += v < last ? -v : v;
			last = Math.max(last, v);
		}
		return total;
	}

	public static int level(Blessing b) {
		return LEVELS.getOrDefault(b, 0);
	}

	@Override
	protected HudPos pos() {
		return BlessingsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return BlessingsConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		BlessingsConfig cfg = BlessingsConfig.get();
		List<String> lines = new ArrayList<>();
		if (!preview && !DungeonLocation.inDungeon()) return lines;
		for (Blessing b : Blessing.values()) {
			boolean show = switch (b) {
				case POWER -> cfg.power;
				case TIME -> cfg.time;
				case STONE -> cfg.stone;
				case LIFE -> cfg.life;
				case WISDOM -> cfg.wisdom;
			};
			int level = preview ? (b == Blessing.TIME ? 5 : 19) : level(b);
			if (show && level > 0) lines.add("§c" + b.name().charAt(0) + b.name().substring(1).toLowerCase() + " §f" + level);
		}
		return lines;
	}
}
