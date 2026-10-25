package com.taz.xpassist.xp;

import java.util.List;

/** The sorted list shown in game, with "SkyBlock Level N: cost" markers between tasks. */
public record XpResult(
	List<Entry> entries,
	List<XpTask> unpriced,
	List<XpTask> locked,
	double totalCost,
	double totalXp,
	double startXp,
	boolean progressKnown,
	String profileName,
	List<String> warnings,
	long computedAt,
	String source,
	double coins
) {
	public sealed interface Entry permits TaskEntry, LevelEntry {
	}

	public record TaskEntry(XpTask task, double cumulativeCost) implements Entry {
	}

	public record LevelEntry(int level, double cumulativeCost) implements Entry {
	}

	/** The highest level your coins (purse + bank) can reach by following the list, or -1 if unknown. */
	public int affordableLevel() {
		if (coins < 0 || !progressKnown) {
			return -1;
		}
		int level = currentLevel(100);
		for (Entry entry : entries) {
			if (entry instanceof LevelEntry levelEntry) {
				if (levelEntry.cumulativeCost() > coins) {
					break;
				}
				level = levelEntry.level();
			}
		}
		return level;
	}

	public int currentLevel(int xpPerLevel) {
		return (int) Math.floor(startXp / xpPerLevel);
	}
}
