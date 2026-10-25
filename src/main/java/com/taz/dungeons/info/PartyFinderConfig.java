package com.taz.dungeons.info;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Dungeons > Party Finder in the Taz settings GUI (/taz). */
public final class PartyFinderConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Stats of players who join your group, Auto Kick, and missing classes and level requirements in the Party Finder menu.")
	@ConfigEditorBoolean
	@SearchTag("party finder pf join stats auto kick missing classes requirements")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Join stats", desc = "When someone joins, their Catacombs level, secrets, magical power and S+ time in your chat (from EliteBot).")
	@ConfigEditorBoolean
	public boolean joinStats = true;

	@Expose
	@ConfigOption(name = "Level required", desc = "In the menu: each party's level requirement on its head.")
	@ConfigEditorBoolean
	public boolean levelRequired = true;

	@Expose
	@ConfigOption(name = "Missing classes", desc = "In the menu: the first letters of the classes each party is missing.")
	@ConfigEditorBoolean
	public boolean missingClasses = true;

	@Expose
	@ConfigOption(name = "Missing list", desc = "In the menu: \"Missing: Archer, Mage\" at the bottom of each party's tooltip.")
	@ConfigEditorBoolean
	public boolean missingList = true;

	@Expose
	@ConfigOption(name = "Floor", desc = "The floor for the S+ time (and Auto Kick).")
	@ConfigEditorSlider(minValue = 1, maxValue = 7, minStep = 1)
	public int floor = 7;

	@Expose
	@ConfigOption(name = "Master Mode", desc = "That floor in Master Mode.")
	@ConfigEditorBoolean
	public boolean master = true;

	@Expose
	@ConfigOption(name = "Auto Kick", desc = "Kick players who join without the S+ time, secrets or magical power below (when you lead the party).")
	@ConfigEditorBoolean
	public boolean autoKick = false;

	@Expose
	@ConfigOption(name = "Kick: slowest S+", desc = "Their S+ time on the floor must be this many seconds or less.")
	@ConfigEditorSlider(minValue = 60, maxValue = 480, minStep = 10)
	public int maxSeconds = 400;

	@Expose
	@ConfigOption(name = "Kick: fewest secrets", desc = "In thousands (0 = any).")
	@ConfigEditorSlider(minValue = 0, maxValue = 200, minStep = 1)
	public int minSecretsK = 0;

	@Expose
	@ConfigOption(name = "Kick: least magical power", desc = "0 = any.")
	@ConfigEditorSlider(minValue = 0, maxValue = 3000, minStep = 10)
	public int minMp = 0;

	@Expose
	@ConfigOption(name = "Kick: say why", desc = "Tell the party why before kicking.")
	@ConfigEditorBoolean
	public boolean informKicked = false;

	public static PartyFinderConfig get() {
		return Taz.config().dungeons.partyFinder;
	}
}
