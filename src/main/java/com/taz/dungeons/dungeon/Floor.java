package com.taz.dungeons.dungeon;

import java.util.List;

/**
 * Every Catacombs floor with its ordered milestones and a rough default length (seconds)
 * for each segment. The defaults are only used until enough of your own runs are recorded
 * (see Predictor), so they're intentionally rough. They assume a competent party after the
 * 0.27.2 "Dungeon Time Saves" patch (6 Oct 2026). M6 comes from real recorded runs.
 */
public enum Floor {
	// id, master, boss name (first [BOSS] speaker in the boss room), speed-score time offset (s), secrets needed for full secret score, splits
	E("E", false, null, 120, 0.30, standard(20, 35, 15, 30, boss(40))),
	F1("F1", false, "Bonzo", 120, 0.30, standard(25, 40, 15, 45, boss(45))),
	F2("F2", false, "Scarf", 120, 0.40, standard(25, 40, 15, 45, boss(50))),
	F3("F3", false, "The Professor", 120, 0.50, standard(25, 40, 15, 50, boss(70))),
	F4("F4", false, "Thorn", 240, 0.60, standard(30, 45, 18, 60, boss(60))),
	F5("F5", false, "Livid", 120, 0.70, standard(30, 45, 18, 60, boss(70))),
	F6("F6", false, "Sadan", 240, 0.85, standard(30, 45, 18, 70, sadan(60, 30, 20))),
	F7("F7", false, "Maxor", 360, 1.00, standard(30, 45, 20, 80, floor7(35, 45, 60, 15, 30))),
	M1("M1", true, "Bonzo", 0, 1.00, standard(25, 40, 18, 55, boss(50))),
	M2("M2", true, "Scarf", 0, 1.00, standard(25, 40, 18, 55, boss(55))),
	M3("M3", true, "The Professor", 0, 1.00, standard(25, 40, 18, 60, boss(70))),
	M4("M4", true, "Thorn", 0, 1.00, standard(28, 40, 18, 60, boss(65))),
	M5("M5", true, "Livid", 0, 1.00, standard(28, 40, 18, 65, boss(70))),
	M6("M6", true, "Sadan", 120, 1.00, standard(28, 45, 10, 17, sadan(39, 11, 13))),
	M7("M7", true, "Maxor", 360, 1.00, standard(28, 40, 20, 85, master7(30, 40, 55, 15, 30, 80)));

	/** A milestone plus the name and default length of the segment that starts at it. */
	public record Split(Milestone milestone, String segmentName, double defaultSeconds) {
	}

	public final String id;
	public final boolean master;
	/**
	 * The floor boss as named in "[BOSS] <name>:" chat. Boss entry is its first line. The Watcher
	 * also spawns old bosses as blood mobs (Scarf, Bonzo, ...) that talk with the same prefix, so
	 * on F1-F5 the boss only counts once blood is cleared.
	 */
	public final String bossName;
	/**
	 * Speed score is 100 while (run time - this offset) is under 480s, i.e. the floor's time
	 * limit is 480s + offset (F6 12 min, M6 10 min, F7/M7 14 min). From the SkyBlock wiki.
	 */
	public final int speedOffsetSec;
	/** Fraction of the floor's secrets needed for the full 40 secret points. */
	public final double secretsRequired;
	public final List<Split> splits;

	Floor(String id, boolean master, String bossName, int speedOffsetSec, double secretsRequired, Split[] splits) {
		this.id = id;
		this.master = master;
		this.bossName = bossName;
		this.speedOffsetSec = speedOffsetSec;
		this.secretsRequired = secretsRequired;
		this.splits = List.of(splits);
	}

	/** Whether the boss's name can also show up as a blood mob, so boss entry must come after blood clear. */
	public boolean bossNeedsBloodClear() {
		return number() <= 5;
	}

	public long speedLimitMs() {
		return (480L + speedOffsetSec) * 1000L;
	}

	/** Floor number 0-7 (Entrance = 0). */
	public int number() {
		return this == E ? 0 : id.charAt(1) - '0';
	}

	public String displayName() {
		return this == E ? "Entrance" : (master ? "Master " : "Floor ") + id.substring(1);
	}

	/** Index of the milestone in this floor's order, or -1 if this floor doesn't use it. */
	public int indexOf(Milestone m) {
		for (int i = 0; i < splits.size(); i++) {
			if (splits.get(i).milestone() == m) return i;
		}
		return -1;
	}

	public boolean hasMilestone(Milestone m) {
		return indexOf(m) >= 0;
	}

	public int segmentCount() {
		return splits.size() - 1;
	}

	public static Floor fromId(String id) {
		if (id == null) return null;
		String s = id.trim().toUpperCase();
		if (s.equals("ENTRANCE") || s.equals("F0")) return E;
		for (Floor f : values()) {
			if (f.id.equals(s)) return f;
		}
		return null;
	}

	// --- builders ---------------------------------------------------------------------

	/**
	 * Clear-phase splits. Since 0.27.2 the Watcher no longer says "That will be enough for now.",
	 * so blood is one segment: door opened -> "You have proven yourself".
	 */
	private static Split[] standard(double rush, double watcher, double bloodClear, double finishClear, Split[] boss) {
		Split[] out = new Split[3 + boss.length + 1];
		out[0] = new Split(Milestone.START, "Blood Rush", rush);
		out[1] = new Split(Milestone.BLOOD_OPEN, "Blood", watcher + bloodClear);
		out[2] = new Split(Milestone.BLOOD_CLEAR, "Finish Clear", finishClear);
		System.arraycopy(boss, 0, out, 3, boss.length);
		out[out.length - 1] = new Split(Milestone.END, null, 0);
		return out;
	}

	private static Split[] boss(double seconds) {
		return new Split[]{new Split(Milestone.BOSS_ENTER, "Boss", seconds)};
	}

	private static Split[] sadan(double terracottas, double giants, double sadan) {
		return new Split[]{
				new Split(Milestone.BOSS_ENTER, "Terracottas", terracottas),
				new Split(Milestone.GIANTS, "Giants", giants),
				new Split(Milestone.SADAN, "Sadan", sadan),
		};
	}

	private static Split[] floor7(double maxor, double storm, double terminals, double goldor, double necron) {
		return new Split[]{
				new Split(Milestone.BOSS_ENTER, "Maxor", maxor),
				new Split(Milestone.STORM, "Storm", storm),
				new Split(Milestone.GOLDOR, "Terminals", terminals),
				new Split(Milestone.CORE, "Goldor", goldor),
				new Split(Milestone.NECRON, "Necron", necron),
		};
	}

	private static Split[] master7(double maxor, double storm, double terminals, double goldor, double necron, double dragons) {
		return new Split[]{
				new Split(Milestone.BOSS_ENTER, "Maxor", maxor),
				new Split(Milestone.STORM, "Storm", storm),
				new Split(Milestone.GOLDOR, "Terminals", terminals),
				new Split(Milestone.CORE, "Goldor", goldor),
				new Split(Milestone.NECRON, "Necron", necron),
				new Split(Milestone.WITHER_KING, "Dragons", dragons),
		};
	}
}
