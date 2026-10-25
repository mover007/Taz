package com.taz.dungeons.secrets;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.util.TazChat;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Room Secret Times in the Taz settings GUI (/taz). */
public final class RoomTimesConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Times how long each room's secrets take you, from the first secret found to the last (Hypixel's "
			+ "\"3/7 Secrets\"), whatever route you take. Needs no waypoints and works without Secret Routes.")
	@ConfigEditorBoolean
	@SearchTag("room secrets time party chat done")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat", desc = "Say it in party chat when a room's secrets are done: \"[TAZ] Mossy secrets done in 0:11.2\". "
			+ "Not when you're solo, and not for a room whose last secret was its first (nothing to time).")
	@ConfigEditorBoolean
	public boolean partyChat = true;

	@Expose
	@ConfigOption(name = "Your chat", desc = "Also show it to you with your best time for that room: \"[TAZ] Mossy secrets done in 0:11.2 "
			+ "(7/7, new best!)\".")
	@ConfigEditorBoolean
	public boolean ownChat = true;

	@ConfigOption(name = "Reset best times", desc = "Forget every room's best secrets time (click twice).")
	@ConfigEditorButton(buttonText = "Reset")
	public transient Runnable resetBest = () -> {
		if (TazChat.confirm("roomtimesbest", "forget every room's best secrets time")) RoomSecretTimes.resetBest();
	};

	public static RoomTimesConfig get() {
		return Taz.config().dungeons.roomTimes;
	}
}
