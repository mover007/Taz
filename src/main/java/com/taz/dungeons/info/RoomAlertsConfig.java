package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Room Alerts in the Taz settings GUI (/taz). */
public final class RoomAlertsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A big alert the moment the room you're in has all its secrets.")
	@ConfigEditorBoolean
	@SearchTag("room alert secrets done cleared")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Secrets done", desc = "\"Secrets Done!\" when the room's last secret is found.")
	@ConfigEditorBoolean
	public boolean secrets = true;

	@Expose
	@ConfigOption(name = "Show for", desc = "Seconds the alert stays up.")
	@ConfigEditorSlider(minValue = 0.5f, maxValue = 3, minStep = 0.1f)
	public float seconds = 2;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = big(new HudPos(0.42, 0.40));

	private static HudPos big(HudPos pos) {
		pos.scale = 2.5f;
		return pos;
	}

	public static RoomAlertsConfig get() {
		return Taz.config().dungeons.roomAlerts;
	}
}
