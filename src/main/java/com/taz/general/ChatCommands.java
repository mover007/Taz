package com.taz.general;

import com.taz.dungeons.util.LagMonitor;
import com.taz.util.Alert;
import com.taz.util.Party;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat Commands: party (and private, co-op, guild) members can type "!" commands that Taz answers or runs for you: !warp,
 * !allinvite, !pt, !kick, !f7 and friends (when you lead the party), !coords, !cf, !8ball, !dice, !ping, !tps, !fps,
 * !location, !holding, !taz (the Taz Discord), !dt (a reminder to stop after this run), !invite in a private message. Answers
 * start with [TAZ].
 * From Odin.
 */
public final class ChatCommands {
	private static final Pattern MESSAGE = Pattern.compile("^(?:Party > (?:\\[[^]]*?] )?(\\w{1,16})(?: [ቾ⚒])?: ?(.+)$|Guild > (?:\\[[^]]*?] )?(\\w{1,16})"
			+ "(?: \\[[^]]*?])?: ?(.+)$|From (?:\\[[^]]*?] )?(\\w{1,16}): ?(.+)$|Co-op > (?:\\[[^]]*?] )?(\\w{1,16})(?: [ቾ⚒])?: ?(.+)$)");
	private static final Pattern RUN_END = Pattern.compile("^ *> EXTRA STATS <$|^\\[NPC] Elle: Good job everyone\\. A hard fought battle come to an end\\. Let's "
			+ "get out of here before we run into any more trouble!$");
	private static final String[] EIGHT_BALL = {"It is certain", "It is decidedly so", "Without a doubt", "Yes definitely", "You may rely on it",
			"As I see it, yes", "Most likely", "Outlook good", "Yes", "Signs point to yes", "Reply hazy try again", "Ask again later",
			"Better not tell you now", "Cannot predict now", "Concentrate and ask again", "Don't count on it", "My reply is no", "My sources say no",
			"Outlook not so good", "Very doubtful"};
	private static final Map<String, String> INSTANCES = new LinkedHashMap<>();
	/** The official Taz Discord, for !taz. */
	public static final String DISCORD = "https://discord.gg/D4rUnmbpJr";
	/** Test hook: the last answer Taz gave. */
	public static String lastReply;

	private enum Channel {
		PARTY, GUILD, PRIVATE, COOP
	}

	private record Pending(long at, Runnable action) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** Who asked for downtime this run, and why. */
	private static final Map<String, String> DOWNTIME = new LinkedHashMap<>();

	static {
		String[] words = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};
		for (int i = 0; i < 7; i++) {
			INSTANCES.put("f" + (i + 1), "CATACOMBS_FLOOR_" + words[i]);
			INSTANCES.put("m" + (i + 1), "MASTER_CATACOMBS_FLOOR_" + words[i]);
		}
		String[] kuudra = {"NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL"};
		for (int i = 0; i < 5; i++) INSTANCES.put("t" + (i + 1), "KUUDRA_" + kuudra[i]);
	}

	private ChatCommands() {
	}

	/** Someone in the party asked to stop after this run (Auto Requeue waits). */
	public static boolean downtime() {
		return !DOWNTIME.isEmpty();
	}

	public static void onChat(String line) {
		ChatCommandsConfig cfg = ChatCommandsConfig.get();
		if (!cfg.enabled) return;
		if (RUN_END.matcher(line).matches()) {
			if (cfg.downtime && !DOWNTIME.isEmpty()) later(30, () -> {
				TazChat.info("Downtime: " + String.join(", ", DOWNTIME.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")").toList()));
				Alert.show("§c§lPlayers need DT", null, Alert.Sound.PLING);
				DOWNTIME.clear();
			});
			return;
		}
		Matcher m = MESSAGE.matcher(line);
		if (!m.matches()) return;
		Channel channel;
		String name, msg;
		if (m.group(1) != null) {
			channel = Channel.PARTY;
			name = m.group(1);
			msg = m.group(2);
		} else if (m.group(3) != null) {
			channel = Channel.GUILD;
			name = m.group(3);
			msg = m.group(4);
		} else if (m.group(5) != null) {
			channel = Channel.PRIVATE;
			name = m.group(5);
			msg = m.group(6);
		} else {
			channel = Channel.COOP;
			name = m.group(7);
			msg = m.group(8);
		}
		boolean on = switch (channel) {
			case PARTY -> cfg.party;
			case GUILD -> cfg.guild;
			case PRIVATE -> cfg.privateMessages;
			case COOP -> cfg.coop;
		};
		if (!on || !msg.startsWith("!")) return;
		later(cfg.delay, () -> handle(msg.substring(1), name, channel));
	}

	private static void later(int ticks, Runnable r) {
		PENDING.add(new Pending(System.currentTimeMillis() + ticks * 50L, r));
	}

	/** Every client tick: run what's due. */
	public static void tick() {
		long now = System.currentTimeMillis();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.at() > now) continue;
			it.remove();
			p.action().run();
			return;
		}
	}

	private static void handle(String text, String name, Channel channel) {
		ChatCommandsConfig cfg = ChatCommandsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		String[] words = text.toLowerCase().split(" ");
		String cmd = words[0];
		String arg = words.length > 1 && words[1].length() <= 16 ? words[1] : null;
		boolean party = channel == Channel.PARTY, lead = party && Party.maybeLeader();
		switch (cmd) {
			case "help", "h" -> reply(help(channel), name, channel);
			case "taz" -> {
				if (cfg.taz) reply("Join the official Taz Discord: " + DISCORD, name, channel);
			}
			case "coords", "co" -> {
				if (cfg.coords) reply("x: " + mc.player.getBlockX() + ", y: " + mc.player.getBlockY() + ", z: " + mc.player.getBlockZ(), name, channel);
			}
			case "boop" -> {
				if (cfg.boop && arg != null) command("boop " + arg);
			}
			case "cf" -> {
				if (cfg.coinflip) reply(ThreadLocalRandom.current().nextBoolean() ? "heads" : "tails", name, channel);
			}
			case "8ball" -> {
				if (cfg.eightBall) reply(EIGHT_BALL[ThreadLocalRandom.current().nextInt(EIGHT_BALL.length)], name, channel);
			}
			case "dice" -> {
				if (cfg.dice) reply(String.valueOf(ThreadLocalRandom.current().nextInt(1, 7)), name, channel);
			}
			case "ping" -> {
				if (cfg.ping) reply("Current Ping: " + PerformanceHud.ping() + "ms", name, channel);
			}
			case "tps" -> {
				if (cfg.tps) reply("TPS: " + (LagMonitor.hasData() ? String.format("%.1f", LagMonitor.tps()) : "20.0"), name, channel);
			}
			case "fps" -> {
				if (cfg.fps) reply("Current FPS: " + mc.getFps(), name, channel);
			}
			case "time" -> {
				if (cfg.time) reply("Current Time: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")), name, channel);
			}
			case "location" -> {
				if (cfg.location) reply("Current Location: " + SkyblockLocation.area(), name, channel);
			}
			case "holding" -> {
				if (cfg.holding) {
					String held = mc.player.getMainHandItem().isEmpty() ? "Nothing :(" : mc.player.getMainHandItem().getHoverName().getString();
					reply("Holding: " + net.minecraft.ChatFormatting.stripFormatting(held), name, channel);
				}
			}
			case "warp", "w" -> {
				if (cfg.warp && lead) command("party warp");
			}
			case "allinvite", "allinv" -> {
				if (cfg.allInvite && lead) command("party settings allinvite");
			}
			case "pt", "ptme", "transfer" -> {
				if (cfg.transfer && lead) command("party transfer " + (arg != null ? Party.find(arg) : name));
			}
			case "promote" -> {
				if (cfg.promote && lead) command("party promote " + (arg != null ? Party.find(arg) : name));
			}
			case "demote" -> {
				if (cfg.demote && lead) command("party demote " + (arg != null ? Party.find(arg) : name));
			}
			case "kick", "k" -> {
				if (cfg.kick && lead) command("p kick " + (arg != null ? Party.find(arg) : name));
			}
			case "kickoffline", "ko" -> {
				if (cfg.kick && cfg.kickOffline && lead) command("p kickoffline");
			}
			case "dt", "downtime" -> {
				if (!cfg.downtime || !party) return;
				if (DOWNTIME.containsKey(name)) {
					TazChat.info(name + " already has a reminder.");
					return;
				}
				String why = words.length > 1 ? text.substring(text.indexOf(' ') + 1) : "No reason given";
				DOWNTIME.put(name, why);
				TazChat.info("Downtime reminder set for the end of the run (" + name + ": " + why + "). Auto Requeue waits.");
			}
			case "undt", "undowntime" -> {
				if (cfg.downtime && party && DOWNTIME.remove(name) != null) TazChat.info("Downtime reminder removed (" + name + ").");
			}
			case "reinvite", "reinv" -> {
				if (cfg.reinvite && lead) {
					TazChat.info("Reinviting " + name + " in 5 seconds...");
					later(100, () -> command("p invite " + name));
				}
			}
			case "invite", "inv" -> {
				if (!cfg.invite || channel != Channel.PRIVATE) return;
				if (cfg.autoConfirm) {
					command("p invite " + name);
					return;
				}
				TazChat.send(Component.literal("Click here to invite " + name + " to your party").withStyle(s -> s.withColor(0xFFB0B0)
						.withClickEvent(new ClickEvent.RunCommand("/party invite " + name))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Invite " + name)))));
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f));
			}
			default -> {
				String instance = INSTANCES.get(cmd);
				if (instance != null && cfg.queue && lead) {
					TazChat.info("Entering " + cmd.toUpperCase());
					command("joininstance " + instance);
				}
			}
		}
	}

	private static String help(Channel channel) {
		ChatCommandsConfig c = ChatCommandsConfig.get();
		List<String> on = new ArrayList<>();
		if (c.taz) on.add("taz");
		if (c.coords) on.add("coords");
		if (c.boop) on.add("boop");
		if (c.coinflip) on.add("cf");
		if (c.eightBall) on.add("8ball");
		if (c.dice) on.add("dice");
		if (c.ping) on.add("ping");
		if (c.tps) on.add("tps");
		if (c.fps) on.add("fps");
		if (c.location) on.add("location");
		if (c.holding) on.add("holding");
		if (c.time) on.add("time");
		if (channel == Channel.PARTY) {
			if (c.warp) on.add("warp");
			if (c.allInvite) on.add("allinvite");
			if (c.transfer) on.add("pt");
			if (c.kick) on.add("kick");
			if (c.downtime) on.add("dt");
			if (c.queue) on.add("f1-7, m1-7, t1-5");
		}
		if (channel == Channel.PRIVATE && c.invite) on.add("invite");
		return "Commands: " + String.join(", ", on);
	}

	private static void reply(String text, String name, Channel channel) {
		lastReply = text;
		switch (channel) {
			case PARTY -> TazChat.party(text);
			case GUILD -> command("gc " + TazChat.PREFIX + text);
			case PRIVATE -> command("msg " + name + " " + TazChat.PREFIX + text);
			case COOP -> command("cc " + TazChat.PREFIX + text);
		}
	}

	private static void command(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
	}

	/** Test hooks. */
	public static void clear() {
		PENDING.clear();
		DOWNTIME.clear();
	}

	public static int pending() {
		return PENDING.size();
	}
}
