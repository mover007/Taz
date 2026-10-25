package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Ragnarock in the Taz settings GUI (/taz). */
public final class RagnarockConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Alerts for the Ragnarock Axe: when you cast it and when the cast is cancelled, and the Strength it gave you.")
	@ConfigEditorBoolean
	@SearchTag("ragnarock axe rag strength cast")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Cast alert", desc = "A title when you cast it.")
	@ConfigEditorBoolean
	public boolean castAlert = true;

	@Expose
	@ConfigOption(name = "Cancel alert", desc = "A title when the cast is cancelled (you got hit).")
	@ConfigEditorBoolean
	public boolean cancelAlert = true;

	@Expose
	@ConfigOption(name = "Strength gained", desc = "Tell you how much Strength the cast gave you.")
	@ConfigEditorBoolean
	public boolean strength = true;

	@Expose
	@ConfigOption(name = "Party chat", desc = "Also say the Strength in party chat.")
	@ConfigEditorBoolean
	public boolean announce = false;

	public static RagnarockConfig get() {
		return Taz.config().general.ragnarock;
	}
}
