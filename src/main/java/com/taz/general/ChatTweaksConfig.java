package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Chat in the Taz settings GUI (/taz). */
public final class ChatTweaksConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Ctrl + click to copy a chat message, emoji codes like :skull: and <3, and Explosive Shot damage per enemy.")
	@ConfigEditorBoolean
	@SearchTag("chat copy ctrl click emoji emote explosive shot")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Ctrl + click to copy", desc = "Ctrl + left click a chat message to copy it.")
	@ConfigEditorBoolean
	public boolean copy = true;

	@Expose
	@ConfigOption(name = "Emoji", desc = "Type :skull:, :shrug:, <3, o/ and more; they're sent as the symbols (like [MVP++] emotes).")
	@ConfigEditorBoolean
	public boolean emoji = true;

	@Expose
	@ConfigOption(name = "Explosive Shot per enemy", desc = "After Explosive Shot's damage message, how much each enemy took.")
	@ConfigEditorBoolean
	public boolean explosiveShot = true;

	public static ChatTweaksConfig get() {
		return Taz.config().general.chat;
	}
}
