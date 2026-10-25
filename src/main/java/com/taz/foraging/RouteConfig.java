package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

/** Galatea > Tree Route Assist in the Taz settings GUI (/taz). */
public final class RouteConfig {
	public enum Mode {
		NORMAL("Normal: show the whole route"),
		TRAINING("Training: guide me point by point, timed");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	@Expose
	@ConfigOption(name = "Enabled", desc = "Your tree route (imported from Skyblocker: copy its export, then Import below). A route only shows on "
			+ "the island it's for. Training mode leads you to the next warp or tree in order and times your laps and splits.")
	@ConfigEditorBoolean
	@SearchTag("tree route waypoints skyblocker import export training etherwarp lap splits")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Mode", desc = "Normal shows every point of the route; Training shows the next one (and the one after), with a pointer, "
			+ "lap timer and splits against your best.")
	@ConfigEditorDropdown
	public Mode mode = Mode.NORMAL;

	@ConfigOption(name = "Import from clipboard", desc = "Copy a route in Skyblocker (Waypoints > Share > Export) and press this. "
			+ "A route made for no island gets the island you're standing on.")
	@ConfigEditorButton(buttonText = "Import")
	public transient Runnable importRoute = RouteAssist::importFromClipboard;

	@ConfigOption(name = "Export to clipboard", desc = "Copy this island's route in Skyblocker's format, to share or load into Skyblocker.")
	@ConfigEditorButton(buttonText = "Export")
	public transient Runnable exportRoute = RouteAssist::exportToClipboard;

	@ConfigOption(name = "Next route here", desc = "Switch to your next route for this island.")
	@ConfigEditorButton(buttonText = "Switch")
	public transient Runnable nextRoute = RouteAssist::switchRoute;

	@ConfigOption(name = "List routes", desc = "Show every route you have, its island and best lap.")
	@ConfigEditorButton(buttonText = "List")
	public transient Runnable list = RouteAssist::listRoutes;

	@ConfigOption(name = "Delete this route", desc = "Remove this island's route (click twice).")
	@ConfigEditorButton(buttonText = "Delete")
	public transient Runnable delete = () -> {
		if (TazChat.confirm("deleteroute", "delete this island's route")) RouteAssist.deleteRoute();
	};

	@Expose
	@ConfigOption(name = "Reach distance", desc = "How close (blocks) counts as reaching a point in Training mode.")
	@ConfigEditorSlider(minValue = 2, maxValue = 10, minStep = 1)
	public int reach = 4;

	@Expose
	@ConfigOption(name = "Show the rest (Training)", desc = "In Training mode, show the rest of the route faintly too.")
	@ConfigEditorBoolean
	public boolean showRest = false;

	@Expose
	@ConfigOption(name = "Path lines", desc = "Lines from each point to the next.")
	@ConfigEditorBoolean
	public boolean lines = true;

	@Expose
	@ConfigOption(name = "Restart key", desc = "Training: start the route over from the first point.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int restartKey = GLFW.GLFW_KEY_UNKNOWN;

	@Expose
	@ConfigOption(name = "Skip key", desc = "Training: skip to the point after the next.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int skipKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Reset best times", desc = "Forget this route's best lap and splits (click twice).")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable resetBest = () -> {
		if (TazChat.confirm("routebest", "forget this route's best times")) RouteAssist.resetBest();
	};

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.70, 0.08);

	public static RouteConfig get() {
		return Taz.config().galatea.route;
	}
}
