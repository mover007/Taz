package com.taz.foraging;

import com.taz.general.AlertsConfig;
import com.taz.general.EventTimers;
import com.taz.general.SkyblockEvent;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starlyn Contests (Agatha on Moonglade Marsh, Miria on Torrhus Canyon) run back to back, one per SkyBlock day
 * (20 minutes). When you claim, Hypixel sends a block per contest:
 * <pre>
 * STARLYN CONTEST REWARDS CLAIMED
 * » Agatha Contest on Spring 22nd, Year 521
 * REWARDS
 * Agatha's Coupon x15
 * +20x Forest Essence
 * +20,000 Foraging Experience
 * +200 HOTF Exp
 * +1,200 HOTF Exp (Daily Bonus)
 * +5,000 Forest Whispers
 * +71 Bits
 * </pre>
 * and at the end of a contest "[NPC] Miria: You earned a total of 4,999 points!". While one runs, the sidebar shows
 * "Agatha's Contest 14m55s", "RARE with 1.2k" and "Epic requires +1.3k" (or "+1k over Mythic"). A contest ends 30
 * seconds before its SkyBlock day does (at :14:30, :34:30, :54:30); the next starts with the new day.
 */
public final class StarlynContest extends TextHud {
	private static final Pattern CONTEST = Pattern.compile("^» (Agatha|Miria) Contest on (.+)$");
	private static final Pattern AMOUNT = Pattern.compile("^\\+([\\d,]+)x? (.+?)(?: \\(Daily Bonus\\))?$");
	private static final Pattern COUPON = Pattern.compile("^((?:Agatha|Miria)'s Coupon) x([\\d,]+)$");
	private static final Pattern POINTS = Pattern.compile("^\\[NPC] (Agatha|Miria): You earned a total of ([\\d,]+) points!");
	private static final Pattern SIDEBAR_TIMER = Pattern.compile("^(Agatha|Miria)'s Contest (?:(\\d+)m ?)?(\\d+)s$");
	private static final Pattern SIDEBAR_BRACKET = Pattern.compile("^(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL) with [\\d.,]+k?$");
	private static final Pattern SIDEBAR_NEXT = Pattern.compile("^(?:\\w+ requires \\+[\\d.,]+k?|\\+[\\d.,]+k? over \\w+|Collected [\\d.,]+k?)$");
	/** A contest ends this long before its SkyBlock day (seen on the sidebar timer). */
	public static final long END_EARLY_S = 30;
	/** Sidebar values older than this are stale (you left the island, or the contest ended). */
	private static final long LIVE_MS = 5_000;
	/** The claim blocks all come at once; summed up once nothing more came for this long. */
	private static final long CLAIM_GAP_MS = 1000;

	/** Points needed for each bracket (wiki), for Agatha and Miria. */
	private static final String[] BRACKETS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL"};
	private static final int[] AGATHA = {25, 250, 1_000, 2_000, 3_000, 5_000, 8_000, 12_000};
	private static final int[] MIRIA = {50, 250, 1_000, 2_500, 5_000, 10_000, 15_000, 20_000};

	/** Rewards claimed this session, by name ("Forest Whispers" -> 17,500). */
	private static final Map<String, Long> CLAIMED = new LinkedHashMap<>();
	private static final Map<String, Long> BATCH = new LinkedHashMap<>();
	private static int batchContests;
	private static String batchHost;
	private static long lastClaimLineMs;
	private static boolean inClaim;
	/** The current line is part of a claim block (hide it when summing up). */
	private static boolean partOfClaim;
	private static int claimedContests;
	private static String lastHost;
	private static int lastPoints = -1;
	private static long remindedEnd;
	/** From the sidebar: when the running contest ends (ms), the bracket line and the next-bracket line. */
	private static long liveEndMs;
	private static String liveBracket;
	private static String liveNext;
	private static long liveSeenMs;
	private static int ticks;

	public StarlynContest() {
		super("Starlyn Contest", 0.72, 0.80);
	}

	/** Unix seconds when the SkyBlock day ends (the next contest starts then). */
	public static long dayEnd(long nowS) {
		long t = nowS - SkyblockEvent.EPOCH_S;
		return SkyblockEvent.EPOCH_S + (Math.floorDiv(t, SkyblockEvent.DAY_S) + 1) * SkyblockEvent.DAY_S;
	}

	/**
	 * Unix seconds when the running contest ends: 30 seconds before its SkyBlock day ends. In those last 30 seconds
	 * no contest runs, and this is the end of the next one.
	 */
	public static long endsAt(long nowS) {
		long end = dayEnd(nowS) - END_EARLY_S;
		return end > nowS ? end : end + SkyblockEvent.DAY_S;
	}

	/** Between two contests (the 30 seconds before a new SkyBlock day). */
	public static boolean between(long nowS) {
		return dayEnd(nowS) - nowS <= END_EARLY_S;
	}

	/** When the running contest ends, in ms: the sidebar's timer while it's showing, else the calendar. */
	static long endMs(long nowMs) {
		if (liveEndMs > 0 && nowMs - liveSeenMs < LIVE_MS) return liveEndMs;
		return endsAt(nowMs / 1000) * 1000;
	}

	/** Every second: the contest lines on the sidebar. */
	public static void readSidebar(List<String> lines, long nowMs) {
		boolean timer = false;
		for (String line : lines) {
			Matcher m = SIDEBAR_TIMER.matcher(line);
			if (m.find()) {
				long left = (m.group(2) == null ? 0 : Long.parseLong(m.group(2)) * 60) + Long.parseLong(m.group(3));
				liveEndMs = nowMs + left * 1000;
				liveSeenMs = nowMs;
				timer = true;
			} else if (SIDEBAR_BRACKET.matcher(line).find()) {
				liveBracket = line;
			} else if (SIDEBAR_NEXT.matcher(line).find()) {
				liveNext = line;
			}
		}
		if (!timer && nowMs - liveSeenMs >= LIVE_MS) {
			liveBracket = null;
			liveNext = null;
		}
	}

	/** "EPIC", or "" below COMMON. */
	public static String bracket(String host, int points) {
		int[] t = "Miria".equals(host) ? MIRIA : AGATHA;
		String b = "";
		for (int i = 0; i < t.length; i++) if (points >= t[i]) b = BRACKETS[i];
		return b;
	}

	/** Points to the next bracket, or -1 at the top. */
	static int toNext(String host, int points) {
		for (int need : "Miria".equals(host) ? MIRIA : AGATHA) if (points < need) return need - points;
		return -1;
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		partOfClaim = false;
		StarlynConfig cfg = StarlynConfig.get();
		if (!cfg.enabled) return;
		long now = System.currentTimeMillis();
		Matcher m;
		if ((m = POINTS.matcher(line)).find()) {
			lastHost = m.group(1);
			lastPoints = Integer.parseInt(m.group(2).replace(",", ""));
			return;
		}
		if (line.equals("STARLYN CONTEST REWARDS CLAIMED")) {
			inClaim = true;
			partOfClaim = true;
			lastClaimLineMs = now;
			return;
		}
		if (!inClaim) return;
		if ((m = CONTEST.matcher(line)).find()) {
			batchContests++;
			batchHost = m.group(1);
		} else if ((m = COUPON.matcher(line)).find()) {
			add(m.group(1), Long.parseLong(m.group(2).replace(",", "")));
		} else if ((m = AMOUNT.matcher(line)).find()) {
			add(m.group(2), Long.parseLong(m.group(1).replace(",", "")));
		} else if (line.equals("REWARDS")) {
			// header
		} else if (!line.isEmpty() && !line.contains(":") && line.length() < 40 && now - lastClaimLineMs < 200) {
			add(line, 1); // an item such as "Agatha's Prize" or a Safari Ticket
		} else {
			inClaim = false;
			sumUp();
			return;
		}
		partOfClaim = true;
		lastClaimLineMs = now;
	}

	private static void add(String what, long amount) {
		BATCH.merge(what, amount, Long::sum);
		CLAIMED.merge(what, amount, Long::sum);
	}

	/** Every client tick: closes a claim batch, and the ending-soon alert. */
	public static void tick() {
		StarlynConfig cfg = StarlynConfig.get();
		if (!cfg.enabled) return;
		long now = System.currentTimeMillis();
		if (inClaim && now - lastClaimLineMs > CLAIM_GAP_MS) {
			inClaim = false;
			sumUp();
		}
		if (!Galatea.onIslands()) return;
		if (++ticks % 20 == 0) readSidebar(GalateaRecorder.sidebarLines(), now);
		if (cfg.endAlert) {
			long nowMs = EventTimers.testNowMs != 0 ? EventTimers.testNowMs : now;
			long end = endMs(nowMs);
			long left = (end - nowMs + 999) / 1000;
			long key = Math.round(end / 10_000.0); // the same contest, give or take the sidebar's rounding
			if (left <= 60 && left > 0 && remindedEnd != key) {
				remindedEnd = key;
				Alert.show("§d§l" + host() + "'s Contest", "§fends in " + left + "s", AlertsConfig.get().sound);
			}
		}
	}

	private static void sumUp() {
		if (BATCH.isEmpty() && batchContests == 0) return;
		claimedContests += batchContests;
		if (StarlynConfig.get().compactClaims) {
			List<String> parts = new ArrayList<>();
			for (Map.Entry<String, Long> e : BATCH.entrySet()) parts.add(String.format("%,d", e.getValue()) + " " + e.getKey());
			TazChat.info("Claimed " + batchContests + " " + (batchHost == null ? "" : batchHost + " ") + "contest"
					+ (batchContests == 1 ? "" : "s") + ": " + String.join(", ", parts));
		}
		BATCH.clear();
		batchContests = 0;
		batchHost = null;
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME (after onChat saw the same line). */
	public static boolean allow(net.minecraft.network.chat.Component message, boolean overlay) {
		StarlynConfig cfg = StarlynConfig.get();
		return overlay || !cfg.enabled || !cfg.compactClaims || !partOfClaim;
	}

	/** Agatha on Moonglade Marsh, Miria on Torrhus Canyon. */
	static String host() {
		return Galatea.inTorrhus() && !Galatea.inMoonglade() ? "Miria" : "Agatha";
	}

	public static long claimed(String what) {
		return CLAIMED.getOrDefault(what, 0L);
	}

	public static int claimedContests() {
		return claimedContests;
	}

	public static int lastPoints() {
		return lastPoints;
	}

	public static String liveBracket() {
		return liveBracket;
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return StarlynConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return StarlynConfig.get().enabled && StarlynConfig.get().hudEnabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		if (!preview && !Galatea.onIslands()) return lines;
		long nowMs = EventTimers.testNowMs != 0 ? EventTimers.testNowMs : System.currentTimeMillis();
		long nowS = nowMs / 1000;
		if (between(nowS) && !(liveEndMs > nowMs && nowMs - liveSeenMs < LIVE_MS)) {
			long in = dayEnd(nowS) - nowS;
			lines.add("§d§l" + host() + "'s Contest §7next in §f" + in + "s");
		} else {
			long left = Math.max(0, (endMs(nowMs) - nowMs) / 1000);
			lines.add("§d§l" + host() + "'s Contest §f" + String.format("%d:%02d", left / 60, left % 60) + " §7left");
		}
		if (liveBracket != null) lines.add("§6" + liveBracket + (liveNext == null ? "" : " §7· §f" + liveNext));
		if (lastPoints >= 0) {
			String b = bracket(lastHost, lastPoints);
			int next = toNext(lastHost, lastPoints);
			lines.add("§7Last: §f" + String.format("%,d", lastPoints) + " points" + (b.isEmpty() ? "" : " §6" + b)
					+ (next > 0 ? " §8(" + String.format("%,d", next) + " to next)" : ""));
		}
		if (claimedContests > 0) {
			StringBuilder sb = new StringBuilder("§7Claimed §f" + claimedContests);
			long coupons = CLAIMED.getOrDefault("Agatha's Coupon", 0L) + CLAIMED.getOrDefault("Miria's Coupon", 0L);
			long whispers = CLAIMED.getOrDefault("Forest Whispers", 0L) + CLAIMED.getOrDefault("Desert Whispers", 0L);
			if (coupons > 0) sb.append(" §7· §f").append(coupons).append(" coupons");
			if (whispers > 0) sb.append(" §7· §f").append(com.taz.xpassist.util.TextUtil.coins(whispers)).append(" whispers");
			lines.add(sb.toString());
		}
		return lines;
	}
}
