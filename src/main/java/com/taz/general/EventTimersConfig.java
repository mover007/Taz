package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

import java.util.ArrayList;
import java.util.List;

/** General > Event Timers in the Taz settings GUI (/taz). */
public final class EventTimersConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "A HUD counting down to SkyBlock events: Dark Auction, Jacob's Contest, Cult of the Fallen Star, "
			+ "Spooky Festival, Traveling Zoo, Season of Jerry, New Year and bank interest.")
	@ConfigEditorBoolean
	@SearchTag("event timer calendar dark auction jacob contest cult star spooky zoo jerry new year bank interest")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Events", desc = "Which events the HUD shows (soonest first). Drag one out to hide it.")
	@ConfigEditorDraggableList
	public List<SkyblockEvent> events = new ArrayList<>(List.of(SkyblockEvent.DARK_AUCTION, SkyblockEvent.JACOBS_CONTEST,
			SkyblockEvent.STAR_CULT, SkyblockEvent.SPOOKY_FESTIVAL, SkyblockEvent.SEASON_OF_JERRY, SkyblockEvent.BANK_INTEREST));

	@Expose
	@ConfigOption(name = "Show SkyBlock date", desc = "The date at the top of the HUD (\"Late Spring 23rd, Year 412\").")
	@ConfigEditorBoolean
	public boolean date = false;

	@Expose
	@ConfigOption(name = "Only on SkyBlock", desc = "Hide the HUD outside SkyBlock.")
	@ConfigEditorBoolean
	public boolean onlySkyblock = true;

	@Expose
	@ConfigOption(name = "Remind me", desc = "Which events get a title and sound shortly before they start. "
			+ "The Dark Auction has its own reminder (General > Dark Auction Reminder).")
	@ConfigEditorDraggableList
	public List<SkyblockEvent> reminders = new ArrayList<>(List.of(SkyblockEvent.JACOBS_CONTEST, SkyblockEvent.STAR_CULT));

	@Expose
	@ConfigOption(name = "Remind before (minutes)", desc = "How long before an event starts the reminder comes. 0 = when it starts.")
	@ConfigEditorSlider(minValue = 0, maxValue = 10, minStep = 1)
	public int remindMinutes = 1;

	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@Expose
	public HudPos hud = new HudPos(0.005, 0.08);

	public static EventTimersConfig get() {
		return Taz.config().general.eventTimers;
	}
}
