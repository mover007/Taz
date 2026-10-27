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

/** Foraging > Tree Route Assist in the Taz settings GUI (/taz). */
public final class RouteConfig {
	public enum Mode {
		NORMAL("Normal: show the whole route"),
		TRAINING("Guide: next tree and the warps to it, timed");

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
	@ConfigOption(name = "Enabled", desc = "A tree route to follow. Taz comes with the Helix route for Torrhus Canyon; import your own "
			+ "from Skyblocker (copy its export, then Import below) and pick which one to use with Choose route. A route only shows "
			+ "on the island it's for. Guide mode shows just the next tree and the warps that get you there, and moves on by itself "
			+ "when the tree is done.")
	@ConfigEditorBoolean
	@SearchTag("tree route waypoints skyblocker import export training etherwarp lap splits")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Mode", desc = "Normal shows every point of the route. Guide shows only the next tree (box, beam, distance) and the "
			+ "warps / teleports to it, numbered on a path from your crosshair; each ticks off when you reach it. The next tree comes up "
			+ "when this one's done (your tree gift or TIMBER! message, or its logs gone) or when you leave it. A tree that isn't "
			+ "grown (or is regenerating) is skipped by itself. Laps are timed with a split at every tree.")
	@ConfigEditorDropdown
	public Mode mode = Mode.NORMAL;

	@Expose
	@ConfigOption(name = "End stops after the last tree", desc = "The teleports and stops your route has after its last tree "
			+ "(like TP1... and Miria). Off: the lap ends at the last tree and the route starts straight over at Tree 1.")
	@ConfigEditorBoolean
	public boolean endStops = false;

	@ConfigOption(name = "Choose route", desc = "Every route you have, by island, with its best lap: click one to use it. The Helix route "
			+ "(comes with Taz) is used until you pick another.")
	@ConfigEditorButton(buttonText = "Choose")
	public transient Runnable chooseRoute = RouteAssist::chooseRoute;

	@ConfigOption(name = "Import from clipboard", desc = "Copy a route in Skyblocker (Waypoints > Share > Export) and press this. "
			+ "A route made for no island gets the island you're standing on.")
	@ConfigEditorButton(buttonText = "Import")
	public transient Runnable importRoute = RouteAssist::importFromClipboard;

	@ConfigOption(name = "Export to clipboard", desc = "Copies this island's route for Skyblocker.")
	@ConfigEditorButton(buttonText = "Export")
	public transient Runnable exportRoute = RouteAssist::exportToClipboard;

	@ConfigOption(name = "Delete this route", desc = "Remove the route in use on this island (click twice). The Helix route comes "
			+ "with Taz and stays.")
	@ConfigEditorButton(buttonText = "Delete")
	public transient Runnable delete = () -> {
		if (TazChat.confirm("deleteroute", "delete this island's route")) RouteAssist.deleteRoute();
	};

	@Expose
	@ConfigOption(name = "Reach distance", desc = "How close (blocks) counts as reaching a warp or tree in Guide mode.")
	@ConfigEditorSlider(minValue = 2, maxValue = 10, minStep = 1)
	public int reach = 4;

	@Expose
	@ConfigOption(name = "Show the rest (Guide)", desc = "In Guide mode, also outline the rest of the route faintly.")
	@ConfigEditorBoolean
	public boolean showRest = false;

	@Expose
	@ConfigOption(name = "Path lines", desc = "Lines from each point to the next.")
	@ConfigEditorBoolean
	public boolean lines = true;

	@Expose
	@ConfigOption(name = "Restart key", desc = "Guide: start the route over from the first tree.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int restartKey = GLFW.GLFW_KEY_UNKNOWN;

	@Expose
	@ConfigOption(name = "Skip key", desc = "Guide: skip this tree and go on to the next.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int skipKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Reset best times", desc = "Forget this route's best lap and splits (click twice).")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable resetBest = () -> {
		if (TazChat.confirm("routebest", "forget this route's best times")) RouteAssist.resetBest();
	};

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.70, 0.08);

	public static RouteConfig get() {
		return Taz.config().foraging.route;
	}
}
