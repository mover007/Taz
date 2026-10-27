package com.taz.dungeons.run;

import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Milestone;

import java.util.List;
import java.util.Set;

/**
 * What the predictor needs to know about a run in progress. Implemented by the live
 * {@link RunTracker} and by {@link ReplayView} (a past run frozen at some moment, for backtests).
 */
public interface RunView {
	Floor floor();

	RunTracker.State state();

	long elapsedMs();

	long gameElapsedMs();

	int lastIndex();

	Long splitMs(Milestone m);

	Long gameSplitMs(Milestone m);

	List<Long> watcherLines();

	long watcherDoneAt();

	int bossDeaths();

	/** F7/M7 terminals progress (0-4), or -1 outside that phase. */
	double terminalProgress();

	Set<String> partyMembers();

	int partySize();

	int clearPct();

	double secretsPct();
}
