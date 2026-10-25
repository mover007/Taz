package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.ChromaColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;
import org.lwjgl.glfw.GLFW;

/** General > Chat Waypoints in the Taz settings GUI (/taz). */
public final class WaypointsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Coordinates people post in chat (\"x: 12, y: 70, z: -5\") become a beam you can walk to. "
			+ "Party chat ones show straight away; others get a [Waypoint] button.")
	@ConfigEditorBoolean
	@SearchTag("waypoint coords coordinates chat party beam share location")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat straight away", desc = "Coordinates in party chat become a waypoint without clicking.")
	@ConfigEditorBoolean
	public boolean autoParty = true;

	@Expose
	@ConfigOption(name = "Remove when this close", desc = "A waypoint goes away when you get within this many blocks of it.")
	@ConfigEditorSlider(minValue = 2, maxValue = 20, minStep = 1)
	public int removeDistance = 5;

	@Expose
	@ConfigOption(name = "Remove after (minutes)", desc = "Waypoints you never reach go away after this long.")
	@ConfigEditorSlider(minValue = 1, maxValue = 60, minStep = 1)
	public int expireMinutes = 10;

	@Expose
	@ConfigOption(name = "Colour", desc = "Beam and box colour.")
	@ConfigEditorColour
	public ChromaColour colour = ChromaColour.Companion.fromStaticRGB(0x55, 0xFF, 0xFF, 255);

	@Expose
	@ConfigOption(name = "Send my coords key", desc = "Sends where you're standing to party chat (\"x: 12, y: 70, z: -5\"), "
			+ "which party members with Taz, SkyHanni or Skyblocker see as a waypoint.")
	@ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
	public int shareKey = GLFW.GLFW_KEY_UNKNOWN;

	@ConfigOption(name = "Clear waypoints", desc = "Remove every waypoint now.")
	@ConfigEditorButton(buttonText = "Clear")
	public transient Runnable clear = Waypoints::clear;

	public static WaypointsConfig get() {
		return Taz.config().general.waypoints;
	}

	public int argb() {
		return 0xFF000000 | (colour.getEffectiveColourRGB() & 0xFFFFFF);
	}
}
