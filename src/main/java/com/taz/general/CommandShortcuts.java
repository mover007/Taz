package com.taz.general;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Command Shortcuts: your own short commands, like /dn for /warp dhub. From NoammAddons' Command Shortcuts. */
public final class CommandShortcuts {
	private CommandShortcuts() {
	}

	/** "dn=warp dhub, pp=pets" as a map. */
	public static Map<String, String> shortcuts() {
		Map<String, String> out = new LinkedHashMap<>();
		for (String pair : CommandShortcutsConfig.get().shortcuts.split(",")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) continue;
			String from = pair.substring(0, eq).trim().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
			String to = pair.substring(eq + 1).trim().replaceFirst("^/", "");
			if (!from.isEmpty() && !to.isEmpty()) out.put(from, to);
		}
		return out;
	}

	/** A command you're sending (without the slash), with a shortcut swapped for its command. */
	public static String rewrite(String command) {
		if (!CommandShortcutsConfig.get().enabled) return command;
		int end = command.indexOf(' ');
		String first = (end < 0 ? command : command.substring(0, end)).toLowerCase(Locale.ROOT);
		String to = shortcuts().get(first);
		return to == null ? command : to + (end < 0 ? "" : command.substring(end));
	}
}
