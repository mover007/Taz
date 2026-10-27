package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Chat > Command Shortcuts in the Taz settings GUI (/taz). */
public final class CommandShortcutsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Your own short commands: /dn sends /warp dhub.")
	@ConfigEditorBoolean
	@SearchTag("command shortcuts alias dn warp dhub")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Shortcuts", desc = "shortcut=command, separated by commas. Example: dn=warp dhub, d7=joininstance MASTER_CATACOMBS_FLOOR_SEVEN")
	@ConfigEditorText
	public String shortcuts = "dn=warp dhub";

	public static CommandShortcutsConfig get() {
		return Taz.config().chat.commandShortcuts;
	}
}
