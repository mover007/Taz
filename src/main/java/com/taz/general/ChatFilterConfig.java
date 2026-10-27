package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** Chat > Chat Filter in the Taz settings GUI (/taz). */
public final class ChatFilterConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Hides spammy Hypixel messages. Taz features still see them (the dungeon timer reads boss lines either way).")
	@ConfigEditorBoolean
	@SearchTag("chat filter hide spam messages clean")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Lobby joins", desc = "\"[MVP++] Steve joined the lobby!\"")
	@ConfigEditorBoolean
	public boolean lobbyJoins = true;

	@Expose
	@ConfigOption(name = "Watchdog announcements", desc = "The [WATCHDOG ANNOUNCEMENT] ban count lines.")
	@ConfigEditorBoolean
	public boolean watchdog = true;

	@Expose
	@ConfigOption(name = "Server and profile messages", desc = "\"Sending to server mini12A...\", \"Warping...\", \"You are playing on profile: ...\", \"Profile ID: ...\".")
	@ConfigEditorBoolean
	public boolean server = true;

	@Expose
	@ConfigOption(name = "Friend and guild joins", desc = "\"Friend > Steve joined.\", \"Guild > Steve left.\"")
	@ConfigEditorBoolean
	public boolean friendGuild = false;

	@Expose
	@ConfigOption(name = "Ability damage", desc = "\"Your Implosion hit 3 enemies for 1,234,567 damage.\"")
	@ConfigEditorBoolean
	public boolean abilityDamage = true;

	@Expose
	@ConfigOption(name = "Ability errors", desc = "\"There are blocks in the way!\", \"This ability is on cooldown for 3s.\", \"You do not have enough mana...\"")
	@ConfigEditorBoolean
	public boolean abilityErrors = true;

	@Expose
	@ConfigOption(name = "Kill combo", desc = "\"+5 Kill Combo +3% ✯ Magic Find\" and the combo expired message.")
	@ConfigEditorBoolean
	public boolean killCombo = true;

	@Expose
	@ConfigOption(name = "Autopet", desc = "\"Autopet equipped your [Lvl 100] Golden Dragon! VIEW RULE\"")
	@ConfigEditorBoolean
	public boolean autopet = false;

	public static ChatFilterConfig get() {
		return Taz.config().chat.chatFilter;
	}
}
