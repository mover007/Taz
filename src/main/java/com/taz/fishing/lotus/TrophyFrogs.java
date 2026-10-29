package com.taz.fishing.lotus;

import com.taz.fishing.trophy.Tier;
import com.taz.fishing.trophy.TrophyBook;
import com.taz.util.Alert;
import com.taz.util.TazChat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trophy Frogs on the Lotus Atoll: the 12 species and their 4 tiers (from the wiki), and every one you've caught (see
 * {@link TrophyBook}). Also this session's frogs and Lily-splosions, and titles for rare catches.
 */
public final class TrophyFrogs {
	/** Each species: its name, the pack's model, its rarity, what catching it takes, Ribery's Lotus per tier, and the Gold pity (Diamond is 6x). */
	public enum Species {
		COMMON("Common Frog", "common_frog", "§f", "anywhere", 8, 16, 32, 64, 100),
		EXPLODING("Exploding Frog", "exploding_frog", "§a", "push Lily Pads together till one explodes", 12, 24, 48, 96, 50),
		LEAP("Leap Frog", "leap_frog", "§a", "jump as you reel in", 24, 48, 96, 192, 100),
		WETLANDS("Wetlands Frog", "wetlands_frog", "§a", "fish in the rain", 20, 40, 80, 160, 100),
		REALITY("Reality Hopper", "reality_hopper", "§a", "fish in a Wormhole (Froggles show them)", 20, 40, 80, 160, 100),
		BLESSED("Blessed Frog", "blessed_frog", "§9", "throw a Frogcoin in the Cave pond", 32, 64, 128, 256, 100),
		BULL("Bullfrog", "bullfrog", "§9", "wear a Red Sweater", 40, 80, 160, 320, 100),
		SEA("Sea Frog", "sea_frog", "§9", "fish underwater", 40, 80, 160, 320, 100),
		CAVE("Cave Frog", "cave_frog", "§5", "fish in the Lotus Eater's Cave", 80, 160, 320, 640, 100),
		HIGHLANDS("Highlands Frog", "highlands_frog", "§5", "fish in the Highlands (Kay launches you)", 80, 160, 320, 640, 100),
		TREE("Tree Frog", "tree_frog", "§5", "stand on leaves", 80, 160, 320, 640, 100),
		PUDDLE("Puddle Jumper", "puddle_jumper", "§6", "cast into the sea creature, hold on", 128, 192, 256, 512, 50);

		public final String label, how;
		final String model, rarity;
		private final int[] lotus;
		public final int goldPity;

		Species(String label, String model, String rarity, String how, int bronze, int silver, int gold, int diamond, int goldPity) {
			this.label = label;
			this.model = "frog/" + model;
			this.rarity = rarity;
			this.how = how;
			this.lotus = new int[]{bronze, silver, gold, diamond};
			this.goldPity = goldPity;
		}

		public int lotus(Tier t) {
			return lotus[t.ordinal()];
		}
	}

	public static final TrophyBook BOOK = new TrophyBook("Trophy Frogs", "FROG", "frogs", entries());
	private static final Pattern LILY_LOTUS = Pattern.compile("^Lotus x(\\d+)$");

	/** This session: frogs, the Lotus Ribery would give for them, Lily-splosions and their Lotus. */
	private static int sessionFrogs, sessionLotus, splosions, splosionLotus;
	private static long splosionAt;

	static {
		BOOK.onCatch(TrophyFrogs::caught);
	}

	private TrophyFrogs() {
	}

	private static List<TrophyBook.Entry> entries() {
		List<TrophyBook.Entry> out = new ArrayList<>();
		for (Species s : Species.values()) out.add(new TrophyBook.Entry(s.label, s.model, s.rarity, s.goldPity));
		return out;
	}

	/** How many of a species at a tier you've caught. */
	public static int count(Species s, Tier t) {
		return BOOK.count(s.ordinal(), t);
	}

	/** Whether you have a species at a tier: the tab list says so, or you've caught one. */
	public static boolean has(Species s, Tier t) {
		return BOOK.has(s.ordinal(), t);
	}

	/** The lowest tier you haven't caught every species of (Ribery rewards each full tier), or null when all 48 are done. */
	public static Tier goalTier() {
		return BOOK.goalTier();
	}

	public static int caughtAt(Tier t) {
		return BOOK.caughtAt(t);
	}

	public static int sessionFrogs() {
		return sessionFrogs;
	}

	public static int sessionLotus() {
		return sessionLotus;
	}

	public static int splosions() {
		return splosions;
	}

	public static int splosionLotus() {
		return splosionLotus;
	}

	/** A plain-text chat line: Lily-splosions (catches come through {@link TrophyBook#onChat}). */
	public static void onChat(String line) {
		if (line.equals("LILY-SPLOSION!")) {
			splosions++;
			splosionAt = System.currentTimeMillis();
			return;
		}
		Matcher m = LILY_LOTUS.matcher(line);
		if (m.matches() && System.currentTimeMillis() - splosionAt < 3000) {
			splosionLotus += Integer.parseInt(m.group(1));
			splosionAt = 0;
		}
	}

	private static void caught(TrophyBook.Catch c) {
		Species s = Species.values()[c.entry()];
		sessionFrogs++;
		sessionLotus += s.lotus(c.tier());
		LotusConfig cfg = LotusConfig.get();
		if (!cfg.enabled || !cfg.frogTitles) return;
		boolean rare = c.tier() == Tier.GOLD || c.tier() == Tier.DIAMOND;
		if (!rare && !c.isNew()) return;
		String title = (c.isNew() ? "§a§lNEW " : "") + c.tier().code + "§l" + c.tier().name() + " §f" + s.label;
		Alert.show(title, "§7Ribery gives §f" + s.lotus(c.tier()) + " Lotus", cfg.sound);
		if (rare && cfg.party) TazChat.party(c.tier().name() + " " + s.label + "!");
	}

	/** Catches of each species and tier in one chat log (tests). */
	public static int[] readLog(Path p) {
		return BOOK.countLog(p);
	}

	public static void reimport() {
		BOOK.reimport();
	}

	/** Tests: no logs, nothing caught. */
	public static void resetForTest() {
		BOOK.resetForTest();
		sessionFrogs = sessionLotus = splosions = splosionLotus = 0;
	}
}
