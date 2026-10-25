package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Kick Timer in the Taz settings GUI (/taz). */
public final class KickTimerConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "After Hypixel kicks you from SkyBlock, how long ago it was (you can rejoin after about a minute).")
	@ConfigEditorBoolean
	@SearchTag("kick kicked skyblock rejoin timer")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat", desc = "Tell your party: \"[TAZ] I got kicked, back in a minute\".")
	@ConfigEditorBoolean
	public boolean party = false;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.40, 0.42);

	public static KickTimerConfig get() {
		return Taz.config().general.kickTimer;
	}
}
