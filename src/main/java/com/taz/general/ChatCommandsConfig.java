package com.taz.general;

import com.google.gson.annotations.Expose;
import com.taz.Taz;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.annotations.SearchTag;

/** General > Chat Commands in the Taz settings GUI (/taz). */
public final class ChatCommandsConfig {
	@Expose
	@ConfigOption(name = "Enabled", desc = "Answers \"!\" commands from your party (and private messages, co-op, guild): !warp, !pt, !kick, !f7, !cf, !8ball, "
			+ "!dice, !ping, !tps, !coords, !dt... Party-leader ones only run when you lead.")
	@ConfigEditorBoolean
	@SearchTag("chat commands party commands warp pt kick coinflip 8ball dice ping tps downtime dt invite")
	public boolean enabled = false;

	@Expose
	@ConfigOption(name = "Party chat", desc = "Answer commands in party chat.")
	@ConfigEditorBoolean
	public boolean party = true;

	@Expose
	@ConfigOption(name = "Private messages", desc = "Answer commands in private messages.")
	@ConfigEditorBoolean
	public boolean privateMessages = true;

	@Expose
	@ConfigOption(name = "Co-op chat", desc = "Answer commands in co-op chat.")
	@ConfigEditorBoolean
	public boolean coop = true;

	@Expose
	@ConfigOption(name = "Guild chat", desc = "Answer commands in guild chat.")
	@ConfigEditorBoolean
	public boolean guild = false;

	@Expose
	@ConfigOption(name = "Delay", desc = "Ticks to wait before answering (raise it if Hypixel says to slow down).")
	@ConfigEditorSlider(minValue = 0, maxValue = 20, minStep = 1)
	public int delay = 6;

	@Expose
	@ConfigOption(name = "!warp", desc = "Party warp.")
	@ConfigEditorBoolean
	public boolean warp = true;

	@Expose
	@ConfigOption(name = "!allinvite", desc = "Turn on all-invite.")
	@ConfigEditorBoolean
	public boolean allInvite = true;

	@Expose
	@ConfigOption(name = "!pt", desc = "Transfer the party to whoever asked (or the name given).")
	@ConfigEditorBoolean
	public boolean transfer = true;

	@Expose
	@ConfigOption(name = "!kick", desc = "Kick whoever asked (or the name given).")
	@ConfigEditorBoolean
	public boolean kick = true;

	@Expose
	@ConfigOption(name = "!kickoffline", desc = "Kick offline players.")
	@ConfigEditorBoolean
	public boolean kickOffline = false;

	@Expose
	@ConfigOption(name = "!promote", desc = "Promote whoever asked.")
	@ConfigEditorBoolean
	public boolean promote = false;

	@Expose
	@ConfigOption(name = "!demote", desc = "Demote whoever asked.")
	@ConfigEditorBoolean
	public boolean demote = false;

	@Expose
	@ConfigOption(name = "!f1-7, !m1-7, !t1-5", desc = "Queue that dungeon floor or Kuudra tier.")
	@ConfigEditorBoolean
	public boolean queue = true;

	@Expose
	@ConfigOption(name = "!dt", desc = "Downtime: a reminder at the end of the run that someone needs a break (Auto Requeue waits).")
	@ConfigEditorBoolean
	public boolean downtime = true;

	@Expose
	@ConfigOption(name = "!reinvite", desc = "Invite whoever asked again 5 seconds later.")
	@ConfigEditorBoolean
	public boolean reinvite = false;

	@Expose
	@ConfigOption(name = "!invite (private)", desc = "A private !invite gives you a button to invite them.")
	@ConfigEditorBoolean
	public boolean invite = true;

	@Expose
	@ConfigOption(name = "!invite: no button", desc = "Invite them straight away instead of showing a button.")
	@ConfigEditorBoolean
	public boolean autoConfirm = false;

	@Expose
	@ConfigOption(name = "!taz", desc = "A link to the official Taz Discord.")
	@ConfigEditorBoolean
	public boolean taz = true;

	@Expose
	@ConfigOption(name = "!coords", desc = "Your coordinates.")
	@ConfigEditorBoolean
	public boolean coords = true;

	@Expose
	@ConfigOption(name = "!boop", desc = "Boop whoever is named.")
	@ConfigEditorBoolean
	public boolean boop = true;

	@Expose
	@ConfigOption(name = "!cf", desc = "Flip a coin.")
	@ConfigEditorBoolean
	public boolean coinflip = true;

	@Expose
	@ConfigOption(name = "!8ball", desc = "A magic 8-ball answer.")
	@ConfigEditorBoolean
	public boolean eightBall = true;

	@Expose
	@ConfigOption(name = "!dice", desc = "Roll a die.")
	@ConfigEditorBoolean
	public boolean dice = true;

	@Expose
	@ConfigOption(name = "!ping", desc = "Your ping.")
	@ConfigEditorBoolean
	public boolean ping = true;

	@Expose
	@ConfigOption(name = "!tps", desc = "The server's TPS.")
	@ConfigEditorBoolean
	public boolean tps = true;

	@Expose
	@ConfigOption(name = "!fps", desc = "Your FPS.")
	@ConfigEditorBoolean
	public boolean fps = true;

	@Expose
	@ConfigOption(name = "!location", desc = "Where you are.")
	@ConfigEditorBoolean
	public boolean location = true;

	@Expose
	@ConfigOption(name = "!holding", desc = "The item in your hand.")
	@ConfigEditorBoolean
	public boolean holding = true;

	@Expose
	@ConfigOption(name = "!time", desc = "Your local time.")
	@ConfigEditorBoolean
	public boolean time = false;

	public static ChatCommandsConfig get() {
		return Taz.config().general.chatCommands;
	}
}
