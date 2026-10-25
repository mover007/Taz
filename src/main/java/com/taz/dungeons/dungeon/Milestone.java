package com.taz.dungeons.dungeon;

/**
 * Points in a run that we can timestamp. Each floor uses an ordered subset of these
 * (see {@link Floor}). The time between two consecutive milestones is a "segment".
 */
public enum Milestone {
	START("Start"),
	BLOOD_OPEN("Blood Door Opened"),
	WATCHER_DONE("Watcher Finished Spawning"),
	BLOOD_CLEAR("Blood Cleared"),
	BOSS_ENTER("Boss Entered"),

	// Floor 6 / Master Mode 6 (Sadan)
	GIANTS("Giants Phase"),
	SADAN("Sadan Phase"),

	// Floor 7 / Master Mode 7
	STORM("Storm Phase"),
	GOLDOR("Goldor / Terminals Phase"),
	CORE("Core Opened"),
	NECRON("Necron Phase"),
	WITHER_KING("Wither King / Dragons Phase"),

	END("Run Complete");

	public final String displayName;

	Milestone(String displayName) {
		this.displayName = displayName;
	}

	public static Milestone fromName(String name) {
		if (name == null) return null;
		try {
			return valueOf(name.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
