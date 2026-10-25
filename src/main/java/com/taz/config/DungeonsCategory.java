package com.taz.config;

import com.google.gson.annotations.Expose;
import com.taz.dungeons.config.ModConfig;
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
}
