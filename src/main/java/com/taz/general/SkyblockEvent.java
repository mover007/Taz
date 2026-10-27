package com.taz.general;

/** SkyBlock's calendar events, worked out from the clock alone. */
public enum SkyblockEvent {
	DARK_AUCTION("Dark Auction", "§5", 60, Kind.HOURLY, 0),
	JACOBS_CONTEST("Jacob's Contest", "§e", 1200, Kind.HOURLY, 1200),
	STAR_CULT("Cult of the Fallen Star", "§b", 300, Kind.MONTHLY, 7, 14, 21, 28),
	SPOOKY_FESTIVAL("Spooky Festival", "§6", 3600, Kind.YEARLY, 8, 29),
	TRAVELING_ZOO("Traveling Zoo", "§a", 3600, Kind.YEARLY, 4, 1, 10, 1),
	SEASON_OF_JERRY("Season of Jerry", "§f", 3600, Kind.YEARLY, 12, 24),
	NEW_YEAR("New Year Celebration", "§d", 3600, Kind.YEARLY, 12, 29),
	BANK_INTEREST("Bank Interest", "§6", 0, Kind.YEARLY, 1, 1, 4, 1, 7, 1, 10, 1);

	enum Kind {
		/** Every real hour; the numbers are seconds after the hour the calendar started on (xx:55). */
		HOURLY,
		/** Days of every month. */
		MONTHLY,
		/** (month, day) pairs every year. */
		YEARLY
	}

	public static final long EPOCH_S = 1_560_275_700L;
	public static final long DAY_S = 1200;
	public static final long MONTH_S = 31 * DAY_S;
	public static final long YEAR_S = 12 * MONTH_S;
	private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

	public final String label;
	public final String color;
	/** How long it runs, in real seconds. */
	public final long durationS;
	private final Kind kind;
	private final int[] when;

	SkyblockEvent(String label, String color, long durationS, Kind kind, int... when) {
		this.label = label;
		this.color = color;
		this.durationS = durationS;
		this.kind = kind;
		this.when = when;
	}

	@Override
	public String toString() {
		return label;
	}

	/** Start (Unix seconds) of the run going on now, or else the next one. */
	public long nextStart(long nowS) {
		long best = Long.MAX_VALUE;
		switch (kind) {
			case HOURLY -> {
				long since = Math.floorMod(nowS - EPOCH_S - when[0], 3600L);
				long start = nowS - since;
				best = start + Math.max(durationS, 1) > nowS ? start : start + 3600;
			}
			case MONTHLY -> {
				long monthIndex = Math.floorDiv(nowS - EPOCH_S, MONTH_S);
				for (long m = monthIndex - 1; m <= monthIndex + 1; m++) {
					for (int day : when) best = earliest(best, EPOCH_S + m * MONTH_S + (day - 1) * DAY_S, nowS);
				}
			}
			case YEARLY -> {
				long yearIndex = Math.floorDiv(nowS - EPOCH_S, YEAR_S);
				for (long y = yearIndex - 1; y <= yearIndex + 1; y++) {
					for (int i = 0; i + 1 < when.length; i += 2) {
						best = earliest(best, EPOCH_S + y * YEAR_S + (when[i] - 1) * MONTH_S + (when[i + 1] - 1) * DAY_S, nowS);
					}
				}
			}
		}
		return best;
	}

	private long earliest(long best, long start, long nowS) {
		return start + Math.max(durationS, 1) > nowS && start < best ? start : best;
	}

	/** "Late Spring 23rd, Year 412" for a Unix time in seconds. */
	public static String date(long unixS) {
		long t = unixS - EPOCH_S;
		long year = Math.floorDiv(t, YEAR_S) + 1;
		long inYear = Math.floorMod(t, YEAR_S);
		int month = (int) (inYear / MONTH_S);
		int day = (int) (inYear % MONTH_S / DAY_S) + 1;
		return MONTHS[month] + " " + day + suffix(day) + ", Year " + year;
	}

	private static String suffix(int day) {
		if (day >= 11 && day <= 13) return "th";
		return switch (day % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
	}
}
