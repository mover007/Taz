package com.taz.general.pv;

import com.google.gson.JsonObject;

import java.util.Locale;

/** SkyBlock's XP tables (skills, Catacombs, slayers, pets) and the level each XP total makes. */
final class Levels {
	/** XP for each skill level, up to 60. */
	static final long[] SKILL = {50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000, 50000, 75000,
			100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000, 1100000, 1200000, 1300000, 1400000, 1500000, 1600000,
			1700000, 1800000, 1900000, 2000000, 2100000, 2200000, 2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000,
			4000000, 4300000, 4600000, 4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000};
	static final long[] RUNECRAFTING = {50, 100, 125, 160, 200, 250, 315, 400, 500, 625, 785, 1000, 1250, 1600, 2000, 2465, 3125, 4000, 5000,
			6200, 7800, 9800, 12200, 15300, 19050};
	static final long[] SOCIAL = {50, 100, 150, 250, 500, 750, 1000, 1250, 1500, 2000, 2500, 3000, 3750, 4500, 6000, 8000, 10000, 12500, 15000,
			20000, 25000, 30000, 35000, 40000, 50000};
	/** Catacombs and class levels, up to 50; past that, 200M XP a level. */
	static final long[] CATACOMBS = {50, 75, 110, 160, 230, 330, 470, 670, 950, 1340, 1890, 2665, 3760, 5260, 7380, 10300, 14400, 20000, 27600,
			38000, 52500, 71500, 97000, 132000, 180000, 243000, 328000, 445000, 600000, 800000, 1065000, 1410000, 1900000, 2500000, 3300000,
			4300000, 5600000, 7200000, 9200000, 12000000, 15000000, 19000000, 24000000, 30000000, 38000000, 48000000, 60000000, 75000000, 93000000,
			116250000};
	private static final long CATACOMBS_OVERFLOW = 200_000_000L;
	/** Total slayer XP for each level. */
	private static final long[] ZOMBIE = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final long[] SPIDER = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
	private static final long[] WOLF = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
	private static final long[] VAMPIRE = {20, 75, 240, 840, 2400};
	/** Pet XP for each level; a pet starts at its rarity's offset into this list. */
	private static final long[] PET = {100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600, 660,
			730, 800, 880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920, 3160, 3420, 3700, 4000, 4350, 4750,
			5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300, 14700, 16200, 17800, 19500, 21300, 23200, 25200, 27400, 29800, 32400,
			35200, 38200, 41400, 44800, 48400, 52200, 56200, 60400, 64800, 69400, 74200, 79200, 84700, 90700, 97200, 104200, 111700, 119700,
			128200, 137200, 146700, 156700, 167700, 179700, 192700, 206700, 221700, 237700, 254700, 272700, 291700, 311700, 333700, 357700,
			383700, 411700, 441700, 476700, 516700, 561700, 611700, 666700, 726700, 791700, 861700, 936700, 1016700, 1101700, 1191700, 1286700,
			1386700, 1496700, 1616700, 1746700, 1886700};

	private Levels() {
	}

	/** A skill level up to {@code cap}: {xp, level, maxLevel, xpCurrent, xpForNext, progress, levelWithProgress, maxed}. */
	static JsonObject skill(double xp, long[] table, int cap) {
		int level = 0;
		double left = xp;
		while (level < cap && level < table.length && left >= table[level]) left -= table[level++];
		boolean maxed = level >= cap || level >= table.length;
		double next = maxed ? 0 : table[level];
		return make(xp, level, cap, left, next, maxed);
	}

	/** Catacombs and classes: 50 levels, then a level per 200M XP (levelWithProgress keeps counting past 50). */
	static JsonObject catacombs(double xp) {
		JsonObject o = skill(xp, CATACOMBS, 50);
		if (o.get("maxed").getAsBoolean()) {
			double over = xp - 569_809_640L;
			o.addProperty("levelWithProgress", 50 + over / CATACOMBS_OVERFLOW);
			o.addProperty("xpCurrent", over % CATACOMBS_OVERFLOW);
			o.addProperty("xpForNext", CATACOMBS_OVERFLOW);
		}
		return o;
	}

	/** A slayer's level from its total XP; xpForNext is the total needed for the next level. */
	static JsonObject slayer(String boss, double xp) {
		long[] table = switch (boss) {
			case "zombie" -> ZOMBIE;
			case "spider" -> SPIDER;
			case "vampire" -> VAMPIRE;
			default -> WOLF;
		};
		int level = 0;
		while (level < table.length && xp >= table[level]) level++;
		boolean maxed = level >= table.length;
		JsonObject o = make(xp, level, table.length, xp, maxed ? 0 : table[level], maxed);
		o.addProperty("xpForNext", maxed ? 0 : table[level]);
		return o;
	}

	/** A pet's level from its XP: 100 at most (200 for the Golden and Jade Dragons). */
	static int pet(double exp, String tier, String type) {
		int offset = switch (tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 6;
			case "RARE" -> 11;
			case "EPIC" -> 16;
			case "LEGENDARY", "MYTHIC" -> 20;
			default -> 0;
		};
		int max = type.equals("GOLDEN_DRAGON") || type.equals("JADE_DRAGON") ? 200 : 100;
		int level = 1;
		double left = exp;
		while (level < max) {
			int i = offset + level - 1;
			long need = i < PET.length ? PET[i] : PET[PET.length - 1];
			if (left < need) break;
			left -= need;
			level++;
		}
		return level;
	}

	private static JsonObject make(double xp, int level, int max, double current, double next, boolean maxed) {
		JsonObject o = new JsonObject();
		o.addProperty("xp", xp);
		o.addProperty("level", level);
		o.addProperty("maxLevel", max);
		o.addProperty("xpCurrent", current);
		o.addProperty("xpForNext", next);
		double progress = maxed || next <= 0 ? 1 : current / next;
		o.addProperty("progress", progress);
		o.addProperty("levelWithProgress", maxed ? level : level + progress);
		o.addProperty("maxed", maxed);
		return o;
	}
}
