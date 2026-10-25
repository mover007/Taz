package com.taz.general;

import com.taz.dungeons.util.TextUtil;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/** Hides spammy chat lines (General > Chat Filter). Runs after Taz has read the line from the packet. */
public final class ChatFilter {
	private static final Pattern LOBBY_JOIN = Pattern.compile("^(?:>>> )?\\[(?:MVP\\+\\+|MVP\\+|MVP|VIP\\+|VIP)] \\w+ [a-z ]{0,30}the lobby!(?: <<<)?$");
	private static final Pattern WATCHDOG = Pattern.compile("^(?:\\[WATCHDOG ANNOUNCEMENT]|Watchdog has banned [\\d,]+ players in the last 7 days\\."
			+ "|Staff have banned an additional [\\d,]+ in the last 7 days\\.|Blacklisted modifications are a bannable offense!)$");
	private static final Pattern SERVER = Pattern.compile("^(?:Sending to server .+\\.\\.\\.|Warping\\.\\.\\.|Warping you to your SkyBlock island\\.\\.\\."
			+ "|Request join for .+\\.\\.\\.|You are playing on profile: .+|Profile ID: [0-9a-f-]+|Sending a visit request\\.\\.\\.)$");
	private static final Pattern FRIEND_GUILD = Pattern.compile("^(?:Friend|Guild) > \\w+ (?:joined|left)\\.$");
	private static final Pattern ABILITY_DAMAGE = Pattern.compile("^Your .+ hit [\\d,]+ enem(?:y|ies) for [\\d,.]+ damage\\.$");
	private static final Pattern ABILITY_ERROR = Pattern.compile("^(?:There are blocks in the way!|This ability is on cooldown for \\d+s\\."
			+ "|You do not have enough mana to do this!|Your .+ is on cooldown!)$");
	private static final Pattern KILL_COMBO = Pattern.compile("^(?:\\+\\d+ Kill Combo.*|Your Kill Combo has expired! You reached a \\d+ Kill Combo!)$");
	private static final Pattern AUTOPET = Pattern.compile("^Autopet equipped your .+! VIEW RULE$");

	/** Test hook: how many lines were hidden. */
	public static int hidden;

	private ChatFilter() {
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME: false hides the line. */
	public static boolean allow(Component message, boolean overlay) {
		if (overlay) return true;
		ChatFilterConfig cfg = ChatFilterConfig.get();
		if (!cfg.enabled) return true;
		String line = TextUtil.cleanChat(message.getString());
		boolean hide = cfg.lobbyJoins && LOBBY_JOIN.matcher(line).find()
				|| cfg.watchdog && WATCHDOG.matcher(line).find()
				|| cfg.server && SERVER.matcher(line).find()
				|| cfg.friendGuild && FRIEND_GUILD.matcher(line).find()
				|| cfg.abilityDamage && ABILITY_DAMAGE.matcher(line).find()
				|| cfg.abilityErrors && ABILITY_ERROR.matcher(line).find()
				|| cfg.killCombo && KILL_COMBO.matcher(line).find()
				|| cfg.autopet && AUTOPET.matcher(line).find();
		if (hide) hidden++;
		return !hide;
	}
}
