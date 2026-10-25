package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.config.ModConfig;
import com.taz.dungeons.info.BlessingsConfig;
import com.taz.dungeons.info.MaskTimersConfig;
import com.taz.dungeons.info.RoomAlertsConfig;
import com.taz.dungeons.info.ScoreAlertsConfig;
import com.taz.dungeons.info.TerracottaTimerConfig;
import com.taz.dungeons.secrets.RoomTimesConfig;
import com.taz.dungeons.secrets.SecretsConfig;
import com.taz.dungeons.skillissue.SkillIssueConfig;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Dungeons" category: one dropdown per dungeon feature. */
public final class DungeonsCategory {
	@Expose
	@ConfigOption(name = "Run Predictor", desc = "Run timer, end-time prediction, score, splits, PBs and chat messages for Catacombs runs.")
	@Accordion
	public ModConfig runPredictor = new ModConfig();

	@Expose
	@ConfigOption(name = "Secret Routes", desc = "Secret routes for every room from Odin waypoint packs, with a guide mode that shows the next step and times each room.")
	@Accordion
	public SecretsConfig secrets = new SecretsConfig();

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
}
