package com.taz.dungeons.puzzles;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Puzzle Solvers in the Taz settings GUI (/taz). */
public final class PuzzlesConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Shows the answer to each dungeon puzzle (the ones turned on below) and times them.")
	@ConfigEditorBoolean
	@SearchTag("puzzle solver water board teleport maze ice fill ice path blaze creeper beams weirdos quiz trivia boulder tic tac toe")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Water Board", desc = "Which levers to flip and when, with a line to the next one.")
	@ConfigEditorBoolean
	public boolean waterBoard = true;

	@Expose
	@ConfigOption(name = "Water: line to lever", desc = "A line from your crosshair to the next lever.")
	@ConfigEditorBoolean
	public boolean waterTracer = true;

	@Expose
	@ConfigOption(name = "Water: fastest solutions", desc = "Use Odin's quicker (harder) solutions.")
	@ConfigEditorBoolean
	public boolean waterOptimized = false;

	@Expose
	@ConfigOption(name = "Teleport Maze", desc = "The pads that can still be the way out (red when one is left), and the ones you've used.")
	@ConfigEditorBoolean
	public boolean teleportMaze = true;

	@Expose
	@ConfigOption(name = "Maze: line to pad", desc = "A line to the best pad to try next.")
	@ConfigEditorBoolean
	public boolean mazeTracer = true;

	@Expose
	@ConfigOption(name = "Ice Fill", desc = "The path over each floor that covers every tile.")
	@ConfigEditorBoolean
	public boolean iceFill = true;

	@Expose
	@ConfigOption(name = "Ice Fill: fastest paths", desc = "Use Odin's quicker (harder) paths.")
	@ConfigEditorBoolean
	public boolean iceFillOptimized = false;

	@Expose
	@ConfigOption(name = "Ice Path", desc = "Where to hit the silverfish to slide it to the exit.")
	@ConfigEditorBoolean
	public boolean icePath = true;

	@Expose
	@ConfigOption(name = "Blaze", desc = "The blazes in the order to kill them.")
	@ConfigEditorBoolean
	public boolean blaze = true;

	@Expose
	@ConfigOption(name = "Blaze: lines", desc = "How many lines to draw from one blaze to the next.")
	@ConfigEditorSlider(minValue = 0, maxValue = 10, minStep = 1)
	public int blazeLines = 2;

	@Expose
	@ConfigOption(name = "Blaze: hide blazes", desc = "Hide the blazes themselves so only the boxes show.")
	@ConfigEditorBoolean
	public boolean hideBlazes = false;

	@Expose
	@ConfigOption(name = "Blaze: party chat", desc = "\"[TAZ] Blaze puzzle solved!\" in party chat.")
	@ConfigEditorBoolean
	public boolean blazeParty = false;

	@Expose
	@ConfigOption(name = "Creeper Beams", desc = "Each pair of lanterns to line up, in its own colour.")
	@ConfigEditorBoolean
	public boolean beams = true;

	@Expose
	@ConfigOption(name = "Beams: lines", desc = "A line between the two lanterns of each pair.")
	@ConfigEditorBoolean
	public boolean beamsTracer = false;

	@Expose
	@ConfigOption(name = "Three Weirdos", desc = "The chest to open in red, the wrong ones in black.")
	@ConfigEditorBoolean
	public boolean weirdos = true;

	@Expose
	@ConfigOption(name = "Quiz", desc = "The right answer's pad.")
	@ConfigEditorBoolean
	public boolean quiz = true;

	@Expose
	@ConfigOption(name = "Quiz timer", desc = "A HUD counting down until you must answer.")
	@ConfigEditorBoolean
	public boolean quizTimer = true;

	@Expose
	@ConfigOption(name = "Boulder", desc = "Which button to press next.")
	@ConfigEditorBoolean
	public boolean boulder = true;

	@Expose
	@ConfigOption(name = "Boulder: all clicks", desc = "Show every click, not just the next.")
	@ConfigEditorBoolean
	public boolean boulderAll = false;

	@Expose
	@ConfigOption(name = "Tic Tac Toe", desc = "The button for your best move.")
	@ConfigEditorBoolean
	public boolean ticTacToe = true;

	@Expose
	@ConfigOption(name = "Puzzle times", desc = "How long each puzzle took you, and your best.")
	@ConfigEditorBoolean
	public boolean timers = true;

	@Expose
	@ConfigOption(name = "Draft on fail", desc = "When someone fails a puzzle, a chat button to get an Architect's First Draft from your sacks.")
	@ConfigEditorBoolean
	public boolean draftPrompt = true;

	@ConfigOption(name = "Quiz timer position", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable editHud = TazHud::openEditor;

	@Expose
	public HudPos quizHud = new HudPos(0.45, 0.30);

	public static PuzzlesConfig get() {
		return Taz.config().dungeons.puzzles;
	}
}
