package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Chat > Stack Duplicates in the Taz settings GUI (/taz). */
public final class ChatStackConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A message that repeats one of the last few takes its place with a count: "
			+ "\"TROPHY FISH! You caught Blobfish BRONZE! (x3)\". Blank lines and separator lines aren't stacked.")
	@ConfigEditorBoolean
	@SearchTag("chat stack duplicate repeat compact spam count")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Look back", desc = "How many of the newest messages a repeat can stack with. 1 stacks only back-to-back repeats.")
	@ConfigEditorSlider(minValue = 1, maxValue = 20, minStep = 1)
	public int range = 5;

	public static ChatStackConfig get() {
		return Taz.config().chat.stack;
	}
}
