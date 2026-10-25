package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Arrow Fix in the Taz settings GUI (/taz). */
public final class ArrowFixConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Shortbows (Terminator, Juju...) don't do the bow pull-back animation, since they shoot straight away. From NoammAddons.")
	@ConfigEditorBoolean
	@SearchTag("arrow fix shortbow terminator juju pullback")
	public boolean enabled = false;

	public static ArrowFixConfig get() {
		return Taz.config().general.arrowFix;
	}
}
