package com.taz.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

/** The "Dungeon Map" category: the map and what it tells you, one dropdown each. */
public final class DungeonMapCategory {
	@Expose
	@ConfigOption(name = "Dungeon Map", desc = "Hypixel's dungeon map redrawn bigger, with room names, secrets and teammates' faces.")
	@Accordion
	public com.taz.dungeons.map.DungeonMapConfig map = new com.taz.dungeons.map.DungeonMapConfig();

	@Expose
	@ConfigOption(name = "Map Info", desc = "A score HUD, the room's secrets, and the Blood portal boxed at 300 score.")
	@Accordion
	public com.taz.dungeons.map.MapInfoConfig mapInfo = new com.taz.dungeons.map.MapInfoConfig();
}
