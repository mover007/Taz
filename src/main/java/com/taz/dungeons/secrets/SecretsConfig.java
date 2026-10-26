package com.taz.dungeons.secrets;

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

/** Dungeons > Secret Routes in the Taz settings GUI (/taz). */
public final class SecretsConfig {
	public enum Mode {
		NORMAL("Normal: every waypoint in the room"),
		GUIDE("Guide: the next step only, timed");

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
	@ConfigOption(name = "Enabled", desc = "Secret routes for every Catacombs room, in Odin's waypoint format. Taz finds the room you're in "
			+ "and how it's turned (like Odin does), so any Odin waypoint pack works: import one below, or use the routes Taz comes with.")
	@ConfigEditorBoolean
	@SearchTag("dungeon secrets waypoints odin route trainer etherwarp catacombs")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Mode", desc = "Normal shows every waypoint in the room, like Odin. Guide starts each room at the red start, then "
			+ "shows only the next step of the route (waypoints titled 1, 2, 3...) and the one after, with a path from your crosshair "
			+ "and what to do there (etherwarp, open the chest, aim and teleport, kill the bat with Hyperion...). A step ticks off when "
			+ "you arrive on it, click its chest / lever / skull, pick up its item or kill its bat. Each room is timed from the red "
			+ "start against your best.")
	@ConfigEditorDropdown
	public Mode mode = Mode.GUIDE;

	@Expose
	@ConfigOption(name = "Taz's routes", desc = "Use the routes Taz comes with (3ppopka's Odin routes, 139 rooms). Off: only the "
			+ "routes you import. A room in a pack you import always uses yours.")
	@ConfigEditorBoolean
	public boolean defaultRoutes = true;

	@ConfigOption(name = "Import from clipboard", desc = "Copy an Odin waypoint export, then press this.")
	@ConfigEditorButton(buttonText = "Import")
	public transient Runnable importClipboard = SecretTrainer::importFromClipboard;

	@ConfigOption(name = "Import from Odin", desc = "Copy the waypoint packs from your Odin install (config/odin/dungeon-waypoints). "
			+ "Press again after changing them in Odin.")
	@ConfigEditorButton(buttonText = "Import")
	public transient Runnable importOdin = SecretTrainer::importFromOdin;

	@ConfigOption(name = "Export to clipboard", desc = "Copies your routes as an Odin waypoint export.")
	@ConfigEditorButton(buttonText = "Export")
	public transient Runnable export = SecretTrainer::exportToClipboard;

	@ConfigOption(name = "List routes", desc = "Show the route packs in use and how many rooms each has.")
	@ConfigEditorButton(buttonText = "List")
	public transient Runnable list = SecretTrainer::listPacks;

	@ConfigOption(name = "Remove imported routes", desc = "Forgets the packs you imported (click twice).")
	@ConfigEditorButton(buttonText = "Remove")
	public transient Runnable remove = () -> {
		if (TazChat.confirm("removesecrets", "forget every secret route pack you imported")) SecretTrainer.removeImported();
	};

	@Expose
	@ConfigOption(name = "Reach distance", desc = "How close (blocks) counts as standing on a spot in Guide mode.")
	@ConfigEditorSlider(minValue = 1, maxValue = 5, minStep = 0.5f)
	public float reach = 2;

	@Expose
	@ConfigOption(name = "Show the next step", desc = "Guide: also outline the step after this one, faintly.")
	@ConfigEditorBoolean
	public boolean showNext = true;

	@Expose
	@ConfigOption(name = "Show hints", desc = "Guide: also show the route's waypoints that aren't steps (\"if 4 locked\", \"just tp\", superboom walls).")
	@ConfigEditorBoolean
	public boolean hints = true;

	@Expose
	@ConfigOption(name = "Path line", desc = "Guide: a line from your crosshair to the next step.")
	@ConfigEditorBoolean
	public boolean lines = true;

	@Expose
	@ConfigOption(name = "Skip key", desc = "Guide: tick off this step (a secret someone else took).")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int skipKey = GLFW.GLFW_KEY_UNKNOWN;

	@Expose
	@ConfigOption(name = "Restart room key", desc = "Guide: start this room's route and timer over.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int restartKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Reset best times", desc = "Forget every room's best time (click twice).")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable resetBest = () -> {
		if (TazChat.confirm("secretbest", "forget every room's best time")) SecretTrainer.resetBest();
	};

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.70, 0.30);

	public static SecretsConfig get() {
		return Taz.config().dungeons.secrets;
	}
}
