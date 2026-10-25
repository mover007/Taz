package com.taz.dungeons;

import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.dungeon.Triggers;
import com.taz.dungeons.predict.Backtester;
import com.taz.dungeons.run.HistoryExport;
import com.taz.dungeons.run.RunHistory;
import com.taz.util.TazChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The buttons under Dungeons > Tools in the settings GUI. Results are printed in chat. */
public final class DungeonTools {
	private DungeonTools() {
	}

	/** Replays your past runs on a floor and prints how accurate the estimates were. */
	public static void backtest(Floor floor) {
		TazChat.info("Backtest " + floor.id + ":");
		for (String line : Backtester.run(floor)) TazChat.send(Component.literal(line).withStyle(ChatFormatting.GRAY));
	}

	public static void exportCsv() {
		try {
			TazChat.info("Saved " + HistoryExport.exportCsv().getFileName() + " in config/taz/dungeons");
		} catch (Exception e) {
			TazChat.send(Component.literal("Export failed: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}

	public static void clearHistory(Floor floor) {
		if (!TazChat.confirm("clear-" + floor.id, "delete every recorded " + floor.id + " run")) return;
		TazChat.info("Removed " + RunHistory.clear(floor) + " " + floor.id + " runs");
	}

	public static void startDemo(Floor floor) {
		DungeonPredictorClient.TRACKER.startDemo(floor);
		TazChat.info("Demo " + floor.id + " run started. Use Next phase to advance, Stop demo to end it.");
	}

	public static void demoNext() {
		DungeonPredictorClient.TRACKER.demoAdvance();
	}

	public static void stopDemo() {
		DungeonPredictorClient.TRACKER.reset();
		TazChat.info("Run tracker reset");
	}

	/** Re-reads triggers.json and history.json after editing them by hand. */
	public static void reloadFiles() {
		Triggers.load();
		RunHistory.load();
		TazChat.info("Reloaded triggers.json and history.json");
	}
}
