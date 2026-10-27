package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.highlight.DoorsAndKeysConfig;
import com.taz.dungeons.highlight.SecretClickedConfig;
import com.taz.dungeons.highlight.StarredMobsConfig;
import com.taz.dungeons.info.BlessingsConfig;
import com.taz.dungeons.info.BloodCampConfig;
import com.taz.dungeons.info.MageBeamConfig;
import com.taz.dungeons.info.MaskTimersConfig;
import com.taz.dungeons.info.RoomAlertsConfig;
import com.taz.dungeons.info.ScoreAlertsConfig;
import com.taz.dungeons.info.TerracottaTimerConfig;
import com.taz.dungeons.secrets.RoomTimesConfig;
import com.taz.dungeons.skillissue.SkillIssueConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Dungeons" category: one dropdown per dungeon feature. */
public final class DungeonsCategory {
	@Expose
	@ConfigOption(name = "Room Secret Times", desc = "How long each room's secrets took you, from the first secret to the last, in party chat "
			+ "and your chat. Works with any route, no waypoints needed.")
	@Accordion
	public RoomTimesConfig roomTimes = new RoomTimesConfig();

	@Expose
	@ConfigOption(name = "Skill Issue", desc = "A friendly joke in party chat when someone dies, about what killed them, where (terminals, "
			+ "Storm, blood...) and their class.")
	@Accordion
	public SkillIssueConfig skillIssue = new SkillIssueConfig();

	@Expose
	@ConfigOption(name = "Blessings", desc = "The dungeon's blessings and their levels.")
	@Accordion
	public BlessingsConfig blessings = new BlessingsConfig();

	@Expose
	@ConfigOption(name = "Mask Timers", desc = "Spirit Mask, Bonzo Mask and Phoenix: invincibility and cooldown timers, an alert and \"Procced!\" in party chat.")
	@Accordion
	public MaskTimersConfig masks = new MaskTimersConfig();

	@Expose
	@ConfigOption(name = "Score Alerts", desc = "270 and 300 score titles, and Mimic, Prince and Bat kills in party chat.")
	@Accordion
	public ScoreAlertsConfig scoreAlerts = new ScoreAlertsConfig();

	@Expose
	@ConfigOption(name = "Room Alerts", desc = "A big \"Secrets Done!\" when the room you're in has all its secrets.")
	@Accordion
	public RoomAlertsConfig roomAlerts = new RoomAlertsConfig();

	@Expose
	@ConfigOption(name = "Terracotta Timer", desc = "Floor 6 boss: a countdown over each dead Terracotta until it comes back.")
	@Accordion
	public TerracottaTimerConfig terracotta = new TerracottaTimerConfig();

	@Expose
	@ConfigOption(name = "Starred Mobs", desc = "Starred mobs, mini-bosses and bonus bats glow through walls; teammates glow in their class colour.")
	@Accordion
	public StarredMobsConfig starredMobs = new StarredMobsConfig();

	@Expose
	@ConfigOption(name = "Doors & Keys", desc = "Boxes the wither and blood doors (white once you have the key) and the keys themselves.")
	@Accordion
	public DoorsAndKeysConfig doorsAndKeys = new DoorsAndKeysConfig();

	@Expose
	@ConfigOption(name = "Secret Clicked", desc = "A box on each secret you click or pick up, and a chime.")
	@Accordion
	public SecretClickedConfig secretClicked = new SecretClickedConfig();

	@Expose
	@ConfigOption(name = "Blood Camp", desc = "When the Watcher moves, and where each of his mobs will land and when.")
	@Accordion
	public BloodCampConfig bloodCamp = new BloodCampConfig();

	@Expose
	@ConfigOption(name = "Mage Beam", desc = "The Mage's beam as one solid red line instead of firework sparks.")
	@Accordion
	public MageBeamConfig mageBeam = new MageBeamConfig();

	@Expose
	@ConfigOption(name = "Auto Requeue", desc = "When a run ends, queue the same floor again.")
	@Accordion
	public com.taz.dungeons.info.AutoRequeueConfig autoRequeue = new com.taz.dungeons.info.AutoRequeueConfig();

	@Expose
	@ConfigOption(name = "Leap Menu", desc = "The Spirit Leap menu as four big boxes, keys 1-4, and a party message when you leap.")
	@Accordion
	public com.taz.dungeons.info.LeapMenuConfig leapMenu = new com.taz.dungeons.info.LeapMenuConfig();

	@Expose
	@ConfigOption(name = "Party Finder", desc = "Stats of players who join, Auto Kick, and missing classes in the Party Finder menu.")
	@Accordion
	public com.taz.dungeons.info.PartyFinderConfig partyFinder = new com.taz.dungeons.info.PartyFinderConfig();
}
