package com.taz.foraging;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Foraging > Starlyn Contests in the Taz settings GUI (/taz). */
public final class StarlynConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Agatha's (Moonglade) and Miria's (Torrhus) contests: time left in the current one (a new one starts every "
			+ "SkyBlock day, at :15, :35 and :55), your last score and bracket, and one tidy line when you claim rewards.")
	@ConfigEditorBoolean
	@SearchTag("starlyn contest agatha miria points bracket coupon whispers rewards claim timer")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "One line per claim", desc = "When you claim rewards, replace the STARLYN CONTEST REWARDS CLAIMED blocks with one line adding them all up.")
	@ConfigEditorBoolean
	public boolean compactClaims = true;

	@Expose
	@ConfigOption(name = "Ending soon alert", desc = "A title a minute before the contest ends.")
	@ConfigEditorBoolean
	public boolean endAlert = false;

	@Expose
	@ConfigOption(name = "HUD", desc = "Time left, last score and bracket, and rewards claimed this session.")
	@ConfigEditorBoolean
	public boolean hudEnabled = true;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.72, 0.80);

	public static StarlynConfig get() {
		return Taz.config().foraging.starlyn;
	}
}
