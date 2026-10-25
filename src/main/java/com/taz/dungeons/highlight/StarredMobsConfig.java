package com.taz.dungeons.highlight;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Starred Mobs in the Taz settings GUI (/taz). */
public final class StarredMobsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Starred mobs, mini-bosses and bonus bats glow through walls in a dungeon's clear (red; Fels pink, "
			+ "bats white).")
	@ConfigEditorBoolean
	@SearchTag("starred mob esp glow highlight bat fel shadow assassin")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Starred mobs", desc = "Starred mobs and mini-bosses (Shadow Assassin, Lost Adventurer, Diamond Guy, King Midas).")
	@ConfigEditorBoolean
	public boolean starred = true;

	@Expose
	@ConfigOption(name = "Bats", desc = "Bonus bats.")
	@ConfigEditorBoolean
	public boolean bats = true;

	@Expose
	@ConfigOption(name = "Hide other names", desc = "Hide the nameplates of mobs that aren't starred, so the starred ones stand out.")
	@ConfigEditorBoolean
	public boolean hideNames = true;

	@Expose
	@ConfigOption(name = "Teammate class glow", desc = "Your teammates glow in their class colour (Archer dark red, Berserk gold, Healer "
			+ "purple, Mage aqua, Tank green).")
	@ConfigEditorBoolean
	public boolean teammates = true;

	public static StarredMobsConfig get() {
		return Taz.config().dungeons.starredMobs;
	}
}
