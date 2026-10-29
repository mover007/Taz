package com.taz.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Chat" category: chat commands, filters, stacking, waypoints and shortcuts, one dropdown each. */
public final class ChatCategory {
	@Expose
	@ConfigOption(name = "Chat Filter", desc = "Hides spammy Hypixel messages (lobby joins, Watchdog, warping, ability damage ...).")
	@Accordion
	public com.taz.general.ChatFilterConfig chatFilter = new com.taz.general.ChatFilterConfig();

	@Expose
	@ConfigOption(name = "Chat Commands", desc = "Answers \"!\" commands from your party: !warp, !pt, !kick, !f7, !cf, !8ball, !dice, !ping, !dt...")
	@Accordion
	public com.taz.general.ChatCommandsConfig chatCommands = new com.taz.general.ChatCommandsConfig();

	@Expose
	@ConfigOption(name = "Chat", desc = "Ctrl + click to copy a message, emoji codes, and Explosive Shot damage per enemy.")
	@Accordion
	public com.taz.general.ChatTweaksConfig chat = new com.taz.general.ChatTweaksConfig();

	@Expose
	@ConfigOption(name = "Stack Duplicates", desc = "A repeated chat message shows once, with a count: (x2), (x3)...")
	@Accordion
	public com.taz.general.ChatStackConfig stack = new com.taz.general.ChatStackConfig();

	@Expose
	@ConfigOption(name = "Chat Waypoints", desc = "Coordinates posted in chat become a beam to walk to, and a key sends yours to your party.")
	@Accordion
	public com.taz.general.WaypointsConfig waypoints = new com.taz.general.WaypointsConfig();

	@Expose
	@ConfigOption(name = "Command Shortcuts", desc = "Your own short commands: /dn sends /warp dhub.")
	@Accordion
	public com.taz.general.CommandShortcutsConfig commandShortcuts = new com.taz.general.CommandShortcutsConfig();
}
