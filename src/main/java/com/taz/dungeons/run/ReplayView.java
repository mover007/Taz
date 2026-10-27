package com.taz.dungeons.run;

import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A recorded run seen as if it were still in progress at {@code atMs} (real time), for backtests. */
public final class ReplayView implements RunView {
	private final RunRecord r;
	private final Floor floor;
	private final long atMs;
	private final long atGame;
	private final int lastIndex;

	public ReplayView(RunRecord r, long atMs) {
		this.r = r;
		this.floor = Floor.fromId(r.floor);
		this.atMs = atMs;
		double ratio = r.totalGameMs > 0 && r.totalMs > 0 ? r.totalGameMs / (double) r.totalMs : 1.0;
		this.atGame = Math.round(atMs * ratio);
		int j = 0;
		for (int i = 0; i < floor.splits.size() - 1; i++) {
			Long v = r.split(floor.splits.get(i).milestone());
			if (v != null && v <= atMs) j = i;
		}
		this.lastIndex = j;
	}

	@Override
	public Floor floor() {
		return floor;
	}

	@Override
	public RunTracker.State state() {
		return RunTracker.State.RUNNING;
	}

	@Override
	public long elapsedMs() {
		return atMs;
	}

	@Override
	public long gameElapsedMs() {
		return atGame;
	}

	@Override
	public int lastIndex() {
		return lastIndex;
	}

	@Override
	public Long splitMs(Milestone m) {
		Long v = r.split(m);
		return v != null && v <= atMs ? v : null;
	}

	@Override
	public Long gameSplitMs(Milestone m) {
		Long real = r.split(m);
		if (real == null || real > atMs) return null;
		Long g = r.gameSplit(m);
		return g != null ? g : real;
	}

	@Override
	public List<Long> watcherLines() {
		List<Long> out = new ArrayList<>();
		if (r.watcherLines != null) for (Long l : r.watcherLines) if (l <= atGame) out.add(l);
		return out;
	}

	@Override
	public long watcherDoneAt() {
		return r.watcherDoneAt >= 0 && r.watcherDoneAt <= atGame ? r.watcherDoneAt : -1;
	}

	@Override
	public int bossDeaths() {
		Long boss = gameSplitMs(Milestone.BOSS_ENTER);
		return boss == null ? 0 : r.deathsBetween(boss, atGame);
	}

	@Override
	public double terminalProgress() {
		if (!floor.hasMilestone(Milestone.CORE) || lastIndex != floor.indexOf(Milestone.GOLDOR)) return -1;
		long best = 0;
		if (r.termProgress != null) for (long[] p : r.termProgress) if (p[0] <= atGame) best = Math.max(best, p[1]);
		return best / 1000.0;
	}

	@Override
	public Set<String> partyMembers() {
		return r.partyMembers == null ? Set.of() : new LinkedHashSet<>(r.partyMembers);
	}

	@Override
	public int partySize() {
		return r.partySize;
	}

	@Override
	public int clearPct() {
		int v = -1;
		if (r.clearTimeline != null) for (long[] p : r.clearTimeline) if (p[0] <= atGame) v = (int) p[1];
		return v;
	}

	@Override
	public double secretsPct() {
		double v = -1;
		if (r.secretsTimeline != null) for (long[] p : r.secretsTimeline) if (p[0] <= atGame) v = p[1] / 10.0;
		return v;
	}
}
