package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.f7.ArrowAlignConfig;
import com.taz.dungeons.f7.DoorFixConfig;
import com.taz.dungeons.f7.LeapCounterConfig;
import com.taz.dungeons.f7.SimonSaysConfig;
import com.taz.dungeons.f7.TerminalSolverConfig;
import com.taz.dungeons.f7.TerminalHighlightConfig;
import com.taz.dungeons.f7.TickTimersConfig;
import com.taz.dungeons.f7.WitherDragonsConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Floor 7" category: features for the Floor 7 / Master Mode 7 boss fight, one dropdown each. */
public final class Floor7Category {
	@Expose
	@ConfigOption(name = "Tick Timers", desc = "Server-tick timers for Maxor's start, Storm's pads and purple pad, and Goldor's death ticks.")
	@Accordion
	public TickTimersConfig tickTimers = new TickTimersConfig();

	@Expose
	@ConfigOption(name = "Leap Counter", desc = "How many teammates have leaped to you at each Phase 3 spot, and an alert when everyone has.")
	@Accordion
	public LeapCounterConfig leapCounter = new LeapCounterConfig();

	@Expose
	@ConfigOption(name = "Terminal Highlight", desc = "Phase 3: a box on each terminal in your section that isn't done yet.")
	@Accordion
	public TerminalHighlightConfig terminals = new TerminalHighlightConfig();

	@Expose
	@ConfigOption(name = "Door Fix", desc = "Phase 3, section 3: turns the iron doors the right way round.")
	@Accordion
	public DoorFixConfig doorFix = new DoorFixConfig();

	@Expose
	@ConfigOption(name = "Terminal Solver", desc = "In the terminals, only the slots to click show, in red. Your clicks stay your own.")
	@Accordion
	public TerminalSolverConfig terminalSolver = new TerminalSolverConfig();

	@Expose
	@ConfigOption(name = "Simon Says", desc = "Phase 3's Simon Says device: the buttons in order.")
	@Accordion
	public SimonSaysConfig simonSays = new SimonSaysConfig();

	@Expose
	@ConfigOption(name = "Arrow Align", desc = "Phase 3's arrow device: how many times to click each frame.")
	@Accordion
	public ArrowAlignConfig arrowAlign = new ArrowAlignConfig();

	@Expose
	@ConfigOption(name = "Wither Dragons", desc = "Master Mode 7's dragons: spawn timers, boxes, health and how long each lived.")
	@Accordion
	public WitherDragonsConfig witherDragons = new WitherDragonsConfig();
}
