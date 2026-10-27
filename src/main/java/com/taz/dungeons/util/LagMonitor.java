package com.taz.dungeons.util;

import java.util.ArrayDeque;

/**
 * Measures server TPS from the time packet Hypixel sends every 20 ticks. Arrival times are taken on the network thread,
 * so client FPS doesn't skew them.
 */
public final class LagMonitor {
	private static final long WINDOW_NANOS = 10_000_000_000L; // TPS averaged over the last ~10s
	private static final long EXTRAPOLATE_LIMIT_NANOS = 1_100_000_000L;
	public static final double NORMAL_TPS = 20.0;

	private static final ArrayDeque<long[]> samples = new ArrayDeque<>(); // {gameTime, nanoTime}
	private static double tps = NORMAL_TPS;
	private static long lastNanos;
	private static long lastGameTime = Long.MIN_VALUE;

	private LagMonitor() {
	}

	public static synchronized void onTimePacket(long gameTime, long nanos) {
		if (gameTime == lastGameTime) return; // same packet seen again on the main thread
		if (gameTime < lastGameTime) samples.clear(); // new world
		lastGameTime = gameTime;
		lastNanos = nanos;
		samples.addLast(new long[]{gameTime, nanos});
		while (samples.size() > 2 && nanos - samples.peekFirst()[1] > WINDOW_NANOS) samples.removeFirst();
		if (samples.size() >= 2) {
			long[] first = samples.peekFirst();
			double seconds = (nanos - first[1]) / 1e9;
			if (seconds >= 0.5) tps = Math.max(0.5, Math.min(NORMAL_TPS, (gameTime - first[0]) / seconds));
		}
	}

	public static synchronized void reset() {
		samples.clear();
		tps = NORMAL_TPS;
		lastGameTime = Long.MIN_VALUE;
		lastNanos = 0;
	}

	public static synchronized boolean hasData() {
		return lastGameTime != Long.MIN_VALUE;
	}

	/** Average TPS over the last ~10s, counting a stall since the last packet. */
	public static synchronized double tps() {
		if (!hasData()) return NORMAL_TPS;
		long since = System.nanoTime() - lastNanos;
		if (since > 2_000_000_000L) {
			// No packet for over 2s: the server is stalling, so effective TPS is dropping.
			double expectedTicks = 20.0;
			return Math.max(0.5, Math.min(tps, expectedTicks / (since / 1e9)));
		}
		return tps;
	}

	public static synchronized long millisSinceLastPacket() {
		return hasData() ? (System.nanoTime() - lastNanos) / 1_000_000L : 0;
	}

	public static boolean isLagging() {
		return tps() < 18.0 || millisSinceLastPacket() > 1_500;
	}

	/** Current server tick, extrapolated a little past the last packet but never through a stall. NaN if unknown. */
	public static synchronized double serverTickNow() {
		if (!hasData()) return Double.NaN;
		long since = Math.min(System.nanoTime() - lastNanos, EXTRAPOLATE_LIMIT_NANOS);
		return lastGameTime + since / 1e9 * tps;
	}
}
