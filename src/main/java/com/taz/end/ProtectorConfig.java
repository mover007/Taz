package com.taz.end;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TazHud;
import com.taz.util.Alert;
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** The End > End Stone Protector in the Taz settings GUI (/taz). */
public final class ProtectorConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "In The End: the End Stone Protector's stage, where it will rise and when, a waypoint to it, "
			+ "alerts at stage 4 and 5, and a lobby tracker that tells you the moment you land in a lobby you've already been in. The stage comes from "
			+ "Hypixel's tab list: turn it on with /widget → General Info Widget → Show End Stone Protector Status.")
	@ConfigEditorBoolean
	@SearchTag("end stone protector endstone golem stage dragon's nest zealot lobby hop hopping tracker server same waypoint highlight beam")
	public boolean enabled = false;

	@ConfigOption(name = "Stage HUD", desc = "The protector's stage, where it rises and the countdown.")
	@ConfigEditorAccordion(id = 0)
	public boolean section0 = false;

	@Expose
	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Show it", desc = "The stage (0 Resting up to 5 Summoned), how long it's been at stage 4, which of its six spots "
			+ "it will rise at, a countdown once it's coming, and the lobby you're in.")
	@ConfigEditorBoolean
	public boolean hud = true;

	@ConfigAccordionId(id = 0)
	@ConfigOption(name = "Move HUD", desc = "Drag it to move, scroll to resize.")
	@ConfigEditorButton(buttonText = "Edit")
	public transient Runnable moveHud = TazHud::openEditor;

	@ConfigOption(name = "Waypoint", desc = "Where it will rise, and the protector itself once it's up.")
	@ConfigEditorAccordion(id = 3)
	public boolean section3 = false;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Spot waypoint", desc = "A purple box and beam at the spot it will rise at, seen through walls, with how far away "
			+ "it is and the countdown once it's coming. Taz finds the spot from the protector's head, which shows there from stage 1.")
	@ConfigEditorBoolean
	public boolean waypoint = true;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "From stage", desc = "Show the waypoint from this stage on: 1 shows it whenever Taz knows the spot, "
			+ "4 only once it's Awakening.")
	@ConfigEditorSlider(minValue = 1, maxValue = 5, minStep = 1)
	public int fromStage = 1;

	@Expose
	@ConfigAccordionId(id = 3)
	@ConfigOption(name = "Highlight it", desc = "A purple box around the End Stone Protector once it has risen, seen through walls, "
			+ "with how far away it is, so you can find it during the fight.")
	@ConfigEditorBoolean
	public boolean highlight = true;

	@ConfigOption(name = "Alerts", desc = "Titles when it's nearly time.")
	@ConfigEditorAccordion(id = 1)
	public boolean section1 = false;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Stage 4", desc = "A title when the protector reaches stage 4 (Awakening), or when you land in a lobby where it already has.")
	@ConfigEditorBoolean
	public boolean stage4 = true;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Stage 5", desc = "A title when it's summoned: it rises 20 seconds later.")
	@ConfigEditorBoolean
	public boolean stage5 = true;

	@Expose
	@ConfigAccordionId(id = 1)
	@ConfigOption(name = "Sound", desc = "The sound for these alerts and the same lobby alert.")
	@ConfigEditorDropdown
	public Alert.Sound sound = Alert.Sound.ANVIL;

	@ConfigOption(name = "Lobby Tracker", desc = "The End lobbies you've been in, for lobby hopping.")
	@ConfigEditorAccordion(id = 2)
	public boolean section2 = false;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Same lobby alert", desc = "A title and a chat line the moment you land in an End lobby you've already been in, "
			+ "with how long ago and the protector's stage then, so you can hop again straight away.")
	@ConfigEditorBoolean
	public boolean sameLobby = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Lobby list", desc = "A HUD with the End lobbies you've been in: the protector's stage in each and how long ago you left.")
	@ConfigEditorBoolean
	public boolean lobbyList = true;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Lobbies in the list", desc = "How many lobbies the list shows, newest first.")
	@ConfigEditorSlider(minValue = 1, maxValue = 15, minStep = 1)
	public int shown = 6;

	@Expose
	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Remember for", desc = "Minutes a lobby is remembered after you leave it.")
	@ConfigEditorSlider(minValue = 5, maxValue = 180, minStep = 5)
	public int minutes = 60;

	@ConfigAccordionId(id = 2)
	@ConfigOption(name = "Forget lobbies", desc = "Clears the lobbies you've been in.")
	@ConfigEditorButton(buttonText = "Forget")
	public transient Runnable forget = LobbyTracker::forget;

	@Expose
	public HudPos stagePos = new HudPos(0.01, 0.35);

	@Expose
	public HudPos lobbyPos = new HudPos(0.01, 0.56);

	public static ProtectorConfig get() {
		return Taz.config().end.protector;
	}
}
