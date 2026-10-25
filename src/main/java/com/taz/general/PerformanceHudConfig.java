package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Performance HUD in the Taz settings GUI (/taz). */
public final class PerformanceHudConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Shows your FPS, the server's TPS and your ping.")
	@ConfigEditorBoolean
	@SearchTag("fps tps ping lag performance")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "FPS", desc = "Frames per second.")
	@ConfigEditorBoolean
	public boolean fps = true;

	@Expose
	@ConfigOption(name = "TPS", desc = "The server's ticks per second (20 is no lag).")
	@ConfigEditorBoolean
	public boolean tps = true;

	@Expose
	@ConfigOption(name = "Ping", desc = "Your ping to the server, measured every two seconds.")
	@ConfigEditorBoolean
	public boolean ping = true;

	@Expose
	@ConfigOption(name = "One per line", desc = "Stack them instead of one line.")
	@ConfigEditorBoolean
	public boolean vertical = false;

	@ConfigOption(name = "Move HUD", desc = "Opens the HUD editor (every Taz HUD): drag to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.01, 0.01);

	public static PerformanceHudConfig get() {
		return Taz.config().general.performance;
	}
}
