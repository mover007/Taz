package com.taz.dungeons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.DungeonTools;
import com.taz.dungeons.dungeon.Floor;
import com.taz.dungeons.hud.HudPanel;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Dungeons" category of the Taz settings GUI (/taz). Saved inside config/taz/config.json;
 * run history, triggers and logs live in config/taz/dungeons/. Defaults are the "Simple" preset:
 * elapsed time, then the end time once you enter the boss portal.
 */
public final class ModConfig {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final Path DIR = Taz.CONFIG_DIR.resolve("dungeons");

	/**
	 * Patch 0.27.2 (6 Oct 2026) sped up boss/Watcher dialogue, cutscenes, secret pickups,
	 * terminals and the F7/M7 phase transitions. Runs recorded before this date don't
	 * describe current timings, so they're left out of predictions.
	 */
	public static final String DEFAULT_HISTORY_CUTOFF = "2026-10-06";

	/** Position and size of one HUD panel. x/y are fractions of the screen so it survives resizing. */
	public static final class PanelConfig {
		@Expose
		public double x;
		@Expose
		public double y;
		@Expose
		public float scale = 1.0f;

		public PanelConfig() {
		}

		public PanelConfig(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "A run timer, your estimated end time, live score and splits for every Catacombs run.")
	@ConfigEditorBoolean
	public boolean enabled = false;

	@ConfigOption(name = "Move HUD", desc = "Drag panels to move, scroll to resize, right-click to hide.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = DungeonPredictorClient::openEditor;

	@ConfigOption(name = "Stats", desc = "Your PB, usual times and estimate accuracy per floor.")
	@ConfigEditorButton(buttonText = "Open")
	public transient Runnable stats = DungeonPredictorClient::openStats;

	@ConfigOption(name = "Simple preset", desc = "Just the run time, and the end time at the portal.")
	@ConfigEditorButton(buttonText = "Apply")
	public transient Runnable simplePreset = () -> {
		applySimplePreset();
		Taz.save();
	};

	@ConfigOption(name = "Everything on", desc = "Every HUD line, panel and chat message.")
	@ConfigEditorButton(buttonText = "Apply")
	public transient Runnable everythingPreset = () -> {
		applyEverythingPreset();
		Taz.save();
	};

	@Expose
	@ConfigOption(name = "Timer HUD", desc = "The timer panel and what it shows.")
	@Accordion
	public TimerHud timer = new TimerHud();

	@Expose
	@ConfigOption(name = "Score", desc = "The score panel and its lines.")
	@Accordion
	public Score score = new Score();

	@Expose
	@ConfigOption(name = "Chat", desc = "Messages shown to you in chat.")
	@Accordion
	public Chat chat = new Chat();

	@Expose
	@ConfigOption(name = "Party Chat", desc = "Messages sent to your party. All off until you turn them on.")
	@Accordion
	public Party party = new Party();

	/** Lag adjustment, learning from chat logs and the developer files: not in the menu. */
	@Expose
	public Other other = new Other();

	@Expose
	@ConfigOption(name = "After a run", desc = "How long the HUD stays up when a run ends.")
	@Accordion
	public Advanced advanced = new Advanced();

	@Expose
	@ConfigOption(name = "History", desc = "Delete a floor's recorded runs.")
	@Accordion
	public Tools tools = new Tools();

	@Expose
	public Map<String, PanelConfig> panels = new LinkedHashMap<>();

	public static final class Tools {
		@Expose
		@ConfigOption(name = "Floor", desc = "The floor to delete runs from.")
		@ConfigEditorDropdown
		public Floor floor = Floor.M7;

		@ConfigOption(name = "Delete floor history", desc = "§cDeletes every run on that floor.§7 Click twice.")
		@ConfigEditorButton(buttonText = "Delete")
		public transient Runnable clearHistory = () -> DungeonTools.clearHistory(this.floor);
	}

	public static final class Advanced {
		@Expose
		@ConfigOption(name = "Keep HUD after run", desc = "Seconds the HUD stays up after a run ends.")
		@ConfigEditorSlider(minValue = 5, maxValue = 120, minStep = 5)
		public int resultSeconds = 30;

		/** Runs before this date (yyyy-MM-dd) are ignored for predictions. */
		@Expose
		public String historyCutoff = DEFAULT_HISTORY_CUTOFF;
	}

	public static final class TimerHud {
		@Expose
		@ConfigOption(name = "Timer panel", desc = "The timer HUD panel.")
		@ConfigEditorBoolean
		public boolean timerPanel = true;
		@Expose
		@ConfigOption(name = "Splits panel", desc = "Each phase's time vs. your usual.")
		@ConfigEditorBoolean
		public boolean splitsPanel = false;
		@Expose
		@ConfigOption(name = "Session panel", desc = "Runs, average, best and estimate accuracy since you started the game.")
		@ConfigEditorBoolean
		public boolean sessionPanel = false;
		@Expose
		@ConfigOption(name = "Title line", desc = "Floor name at the top of the timer.")
		@ConfigEditorBoolean
		public boolean title = true;
		@Expose
		@ConfigOption(name = "Elapsed time", desc = "Run timer, synced to Hypixel's.")
		@ConfigEditorBoolean
		public boolean elapsed = true;
		@Expose
		@ConfigOption(name = "End time at portal", desc = "Once you enter the boss: portal time + your usual boss length.")
		@ConfigEditorBoolean
		public boolean portalEnd = true;
		@Expose
		@ConfigOption(name = "Live ETA", desc = "Predicted finish, updated all run long (not just from the portal).")
		@ConfigEditorBoolean
		public boolean liveEta = false;
		@Expose
		@ConfigOption(name = "ETA range", desc = "Likely range next to the live ETA.")
		@ConfigEditorBoolean
		public boolean etaRange = false;
		@Expose
		@ConfigOption(name = "Best possible finish", desc = "In the boss: fastest finish using your best-ever boss phases.")
		@ConfigEditorBoolean
		public boolean bestPossible = false;
		@Expose
		@ConfigOption(name = "Current phase", desc = "Phase name, plus gate and terminal progress on F7/M7 (e.g. Terminals 2/4 - 5/7).")
		@ConfigEditorBoolean
		public boolean phase = false;
		@Expose
		@ConfigOption(name = "Deaths", desc = "Deaths in the boss / whole run (they slow the boss down; the estimate accounts for them).")
		@ConfigEditorBoolean
		public boolean deaths = false;
		@Expose
		@ConfigOption(name = "Pace", desc = "This run's pace vs. your usual (below 1.00x = faster).")
		@ConfigEditorBoolean
		public boolean pace = false;
		@Expose
		@ConfigOption(name = "Personal best", desc = "Your PB on the floor and the gap to it.")
		@ConfigEditorBoolean
		public boolean pb = false;
		@Expose
		@ConfigOption(name = "Result after run", desc = "Final time and how close the portal estimate was.")
		@ConfigEditorBoolean
		public boolean result = true;
		@Expose
		@ConfigOption(name = "Server lag warning", desc = "Shows TPS when the server lags (times are adjusted for it).")
		@ConfigEditorBoolean
		public boolean lag = true;
		@Expose
		@ConfigOption(name = "'Learning' note", desc = "Shown while fewer than 3 runs are recorded on a floor.")
		@ConfigEditorBoolean
		public boolean learning = false;
		@Expose
		@ConfigOption(name = "Splits: wither doors", desc = "Under the splits, how many wither doors were opened. From NoammAddons' Run Splits.")
		@ConfigEditorBoolean
		public boolean witherDoors = false;
		@Expose
		@ConfigOption(name = "Splits: 300 score", desc = "Under the splits, when the run reached 300 score.")
		@ConfigEditorBoolean
		public boolean score300 = false;
		@Expose
		@ConfigOption(name = "Splits: lag lost", desc = "Under the splits, how much time the server's lag cost.")
		@ConfigEditorBoolean
		public boolean lagLost = false;
	}

	public static final class Score {
		@Expose
		@ConfigOption(name = "Score panel", desc = "Live score, secrets, crypts, puzzles, deaths.")
		@ConfigEditorBoolean
		public boolean scorePanel = false;
		@Expose
		@ConfigOption(name = "Live score", desc = "Hypixel's own live score from the sidebar.")
		@ConfigEditorBoolean
		public boolean live = true;
		@Expose
		@ConfigOption(name = "Projected final score", desc = "Where the score is heading by the end of the run.")
		@ConfigEditorBoolean
		public boolean projected = true;
		@Expose
		@ConfigOption(name = "Secrets", desc = "Secrets % vs. the floor requirement.")
		@ConfigEditorBoolean
		public boolean secrets = true;
		@Expose
		@ConfigOption(name = "Crypts", desc = "Crypts killed (max 5 count).")
		@ConfigEditorBoolean
		public boolean crypts = true;
		@Expose
		@ConfigOption(name = "Puzzles", desc = "Puzzles done / failed.")
		@ConfigEditorBoolean
		public boolean puzzles = true;
		@Expose
		@ConfigOption(name = "Deaths", desc = "Team deaths.")
		@ConfigEditorBoolean
		public boolean deaths = true;
		@Expose
		@ConfigOption(name = "Bonus", desc = "Mimic / Prince / Paul.")
		@ConfigEditorBoolean
		public boolean bonus = true;
		@Expose
		@ConfigOption(name = "Time limit", desc = "Spare time before the speed score drops.")
		@ConfigEditorBoolean
		public boolean timeLimit = true;
		@Expose
		@ConfigOption(name = "Paul +10 bonus", desc = "Turn on while Paul (EZPZ) is mayor.")
		@ConfigEditorBoolean
		public boolean paul = false;
	}

	public static final class Chat {
		@Expose
		@ConfigOption(name = "Portal message", desc = "\"Portal 1:37.0  End time: 2:41.3\" when you enter the boss.")
		@ConfigEditorBoolean
		public boolean portal = true;
		@Expose
		@ConfigOption(name = "Portal: + best possible", desc = "Add the best possible finish to the portal message.")
		@ConfigEditorBoolean
		public boolean portalBest = false;
		@Expose
		@ConfigOption(name = "Portal: + PB check", desc = "Add whether a PB is still possible to the portal message.")
		@ConfigEditorBoolean
		public boolean portalPb = false;
		@Expose
		@ConfigOption(name = "Core update (F7/M7)", desc = "When the Core opens, show an updated end time (much tighter than at the portal on M7).")
		@ConfigEditorBoolean
		public boolean core = true;
		@Expose
		@ConfigOption(name = "End-of-run summary", desc = "One line with your time when the run ends.")
		@ConfigEditorBoolean
		public boolean summary = true;
		@Expose
		@ConfigOption(name = "Summary: + portal estimate", desc = "Add how close the portal estimate was.")
		@ConfigEditorBoolean
		public boolean summaryEstimate = true;
		@Expose
		@ConfigOption(name = "Summary: + best possible", desc = "Add what the best possible finish was.")
		@ConfigEditorBoolean
		public boolean summaryBest = false;
		@Expose
		@ConfigOption(name = "Summary: + score", desc = "Add the team score.")
		@ConfigEditorBoolean
		public boolean summaryScore = false;
		@Expose
		@ConfigOption(name = "New best phase", desc = "Say when a phase beats your best ever, e.g. \"New best Terminals: 41.2s (-1.8s)\".")
		@ConfigEditorBoolean
		public boolean goldSplits = false;
		@Expose
		@ConfigOption(name = "New PB!", desc = "Say \"New PB!\" when you beat your best.")
		@ConfigEditorBoolean
		public boolean pb = true;
		/** Developer messages (only with -Dtaz.dev=true). */
		@Expose
		public boolean missed = false;
		@Expose
		public boolean debug = false;
	}

	public static final class Party {
		@Expose
		@ConfigOption(name = "Send end time", desc = "At portal entry, send \"End time: 2:41.3\" in party chat (/pc). Once per run.")
		@ConfigEditorBoolean
		public boolean end = false;
		@Expose
		@ConfigOption(name = "Send best possible", desc = "At portal entry, send \"Best possible: 2:34.2\" in party chat (/pc). Once per run.")
		@ConfigEditorBoolean
		public boolean best = false;
		@Expose
		@ConfigOption(name = "Send Core update", desc = "When the Core opens (F7/M7), send the updated end time in party chat.")
		@ConfigEditorBoolean
		public boolean core = false;
	}

	public static final class Other {
		@Expose
		public boolean lagAdjust = true;
		@Expose
		public boolean logImport = true;
		/** Developer files (only with -Dtaz.dev=true). */
		@Expose
		public boolean bossLineLog = true;
		@Expose
		public boolean diagnostics = true;
	}

	public static ModConfig get() {
		return Taz.config().dungeons.runPredictor;
	}

	public static void save() {
		Taz.save();
	}

	public static boolean on(Feature f) {
		return get().isOn(f);
	}

	public static void set(Feature f, boolean v) {
		get().setOn(f, v);
	}

	public boolean isOn(Feature f) {
		ModConfig c = this;
		return switch (f) {
			case HUD_TITLE -> c.timer.title;
			case HUD_ELAPSED -> c.timer.elapsed;
			case HUD_PORTAL_END -> c.timer.portalEnd;
			case HUD_LIVE_ETA -> c.timer.liveEta;
			case HUD_ETA_RANGE -> c.timer.etaRange;
			case HUD_BEST_POSSIBLE -> c.timer.bestPossible;
			case HUD_PHASE -> c.timer.phase;
			case HUD_DEATHS -> c.timer.deaths;
			case HUD_PACE -> c.timer.pace;
			case HUD_PB -> c.timer.pb;
			case HUD_RESULT -> c.timer.result;
			case HUD_LAG -> c.timer.lag;
			case HUD_LEARNING -> c.timer.learning;
			case SCORE_LIVE -> c.score.live;
			case SCORE_PROJECTED -> c.score.projected;
			case SCORE_SECRETS -> c.score.secrets;
			case SCORE_CRYPTS -> c.score.crypts;
			case SCORE_PUZZLES -> c.score.puzzles;
			case SCORE_DEATHS -> c.score.deaths;
			case SCORE_BONUS -> c.score.bonus;
			case SCORE_TIME_LIMIT -> c.score.timeLimit;
			case PAUL -> c.score.paul;
			case CHAT_PORTAL -> c.chat.portal;
			case CHAT_PORTAL_BEST -> c.chat.portalBest;
			case CHAT_PORTAL_PB -> c.chat.portalPb;
			case CHAT_CORE -> c.chat.core;
			case CHAT_PARTY_CORE -> c.party.core;
			case CHAT_PARTY_END -> c.party.end;
			case CHAT_PARTY_BEST -> c.party.best;
			case CHAT_SUMMARY -> c.chat.summary;
			case CHAT_SUMMARY_ESTIMATE -> c.chat.summaryEstimate;
			case CHAT_SUMMARY_BEST -> c.chat.summaryBest;
			case CHAT_SUMMARY_SCORE -> c.chat.summaryScore;
			case CHAT_GOLD_SPLITS -> c.chat.goldSplits;
			case CHAT_PB -> c.chat.pb;
			case CHAT_MISSED -> Taz.DEV && c.chat.missed;
			case CHAT_DEBUG -> Taz.DEV && c.chat.debug;
			case LAG_ADJUST -> c.other.lagAdjust;
			case LOG_IMPORT -> c.other.logImport;
			case BOSS_LINE_LOG -> Taz.DEV && c.other.bossLineLog;
			case DIAGNOSTICS -> Taz.DEV && c.other.diagnostics;
		};
	}

	public void setOn(Feature f, boolean v) {
		ModConfig c = this;
		switch (f) {
			case HUD_TITLE -> c.timer.title = v;
			case HUD_ELAPSED -> c.timer.elapsed = v;
			case HUD_PORTAL_END -> c.timer.portalEnd = v;
			case HUD_LIVE_ETA -> c.timer.liveEta = v;
			case HUD_ETA_RANGE -> c.timer.etaRange = v;
			case HUD_BEST_POSSIBLE -> c.timer.bestPossible = v;
			case HUD_PHASE -> c.timer.phase = v;
			case HUD_DEATHS -> c.timer.deaths = v;
			case HUD_PACE -> c.timer.pace = v;
			case HUD_PB -> c.timer.pb = v;
			case HUD_RESULT -> c.timer.result = v;
			case HUD_LAG -> c.timer.lag = v;
			case HUD_LEARNING -> c.timer.learning = v;
			case SCORE_LIVE -> c.score.live = v;
			case SCORE_PROJECTED -> c.score.projected = v;
			case SCORE_SECRETS -> c.score.secrets = v;
			case SCORE_CRYPTS -> c.score.crypts = v;
			case SCORE_PUZZLES -> c.score.puzzles = v;
			case SCORE_DEATHS -> c.score.deaths = v;
			case SCORE_BONUS -> c.score.bonus = v;
			case SCORE_TIME_LIMIT -> c.score.timeLimit = v;
			case PAUL -> c.score.paul = v;
			case CHAT_PORTAL -> c.chat.portal = v;
			case CHAT_PORTAL_BEST -> c.chat.portalBest = v;
			case CHAT_PORTAL_PB -> c.chat.portalPb = v;
			case CHAT_CORE -> c.chat.core = v;
			case CHAT_PARTY_CORE -> c.party.core = v;
			case CHAT_PARTY_END -> c.party.end = v;
			case CHAT_PARTY_BEST -> c.party.best = v;
			case CHAT_SUMMARY -> c.chat.summary = v;
			case CHAT_SUMMARY_ESTIMATE -> c.chat.summaryEstimate = v;
			case CHAT_SUMMARY_BEST -> c.chat.summaryBest = v;
			case CHAT_SUMMARY_SCORE -> c.chat.summaryScore = v;
			case CHAT_GOLD_SPLITS -> c.chat.goldSplits = v;
			case CHAT_PB -> c.chat.pb = v;
			case CHAT_MISSED -> c.chat.missed = v;
			case CHAT_DEBUG -> c.chat.debug = v;
			case LAG_ADJUST -> c.other.lagAdjust = v;
			case LOG_IMPORT -> c.other.logImport = v;
			case BOSS_LINE_LOG -> c.other.bossLineLog = v;
			case DIAGNOSTICS -> c.other.diagnostics = v;
		}
	}

	public boolean panelVisible(HudPanel p) {
		return switch (p) {
			case TIMER -> timer.timerPanel;
			case SPLITS -> timer.splitsPanel;
			case SCORE -> score.scorePanel;
			case SESSION -> timer.sessionPanel;
		};
	}

	public void setPanelVisible(HudPanel p, boolean v) {
		switch (p) {
			case TIMER -> timer.timerPanel = v;
			case SPLITS -> timer.splitsPanel = v;
			case SCORE -> score.scorePanel = v;
			case SESSION -> timer.sessionPanel = v;
		}
	}

	/** Simple: elapsed time + end time at the portal, nothing else. */
	public void applySimplePreset() {
		for (Feature f : Feature.values()) setOn(f, f.simpleDefault);
		setPanelVisible(HudPanel.TIMER, true);
		setPanelVisible(HudPanel.SPLITS, false);
		setPanelVisible(HudPanel.SCORE, false);
		setPanelVisible(HudPanel.SESSION, false);
	}

	/** Everything on (except Paul, which depends on the mayor, debug spam and party messages). */
	public void applyEverythingPreset() {
		for (Feature f : Feature.values()) {
			boolean off = f == Feature.PAUL || f == Feature.CHAT_DEBUG || f == Feature.CHAT_PARTY_END || f == Feature.CHAT_PARTY_BEST
					|| f == Feature.CHAT_PARTY_CORE;
			setOn(f, !off);
		}
		for (HudPanel p : HudPanel.values()) setPanelVisible(p, true);
	}

	public PanelConfig panel(HudPanel p) {
		if (panels == null) panels = new LinkedHashMap<>();
		return panels.computeIfAbsent(p.id, k -> new PanelConfig(p.defaultX, p.defaultY));
	}

	public void resetPanels() {
		panels.clear();
		for (HudPanel p : HudPanel.values()) panel(p);
	}

	/** Cutoff as epoch millis (start of that day, local time). An invalid date counts as no cutoff. */
	public long historyCutoffMillis() {
		try {
			return LocalDate.parse(advanced.historyCutoff).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (DateTimeParseException | NullPointerException e) {
			return 0L;
		}
	}
}
