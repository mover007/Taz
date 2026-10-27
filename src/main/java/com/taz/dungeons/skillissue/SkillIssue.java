package com.taz.dungeons.skillissue;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.dungeons.util.DungeonParty;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill Issue: a friendly joke when someone in your dungeon dies (and an excuse when it's you), from what Hypixel says
 * about it (" ☠ mTaz was killed by Storm and became a ghost.", " ☠ You died and became a ghost.", "... disconnected /
 * died to a mob / died to a trap / was crushed ..."), where the run is, and their class from the tab list.
 */
public final class SkillIssue {
	private static final Pattern DEATH = Pattern.compile("^☠ (You|\\w{1,16}) (.+?) and became a ghost\\.");
	private static final Pattern KILLED = Pattern.compile("^(?:was|were) killed by (.+)$");
	private static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] ([^:]+): (.*)$");
	private static final Pattern RELIC = Pattern.compile("^\\S+ picked the Corrupted \\w+ Relic!$");
	private static final Random RANDOM = new Random();
	/** At most this many jokes in this long (ms): a wipe doesn't flood the chat. */
	private static final int WIPE_JOKES = 3;
	private static final long WIPE_MS = 5000;

	/** Where the run is. */
	enum Phase {CLEAR, BLOOD, TO_BOSS, MAXOR, STORM, TERMINALS, CORE, NECRON, DRAGONS, SADAN, LIVID, THORN, PROFESSOR, SCARF, BONZO, BOSS}

	/** How Hypixel says they died. */
	enum Cause {KILLED, DIED, MOB, TRAP, CRUSHED, DISCONNECTED}

	/**
	 * One set of lines: by phase, by cause, by class, by killer (a part of its name), any killer, and anything; and what
	 * a third death on gets ("death #3, it's a hobby now").
	 */
	record Pool(Map<Phase, List<String>> phase, Map<Cause, List<String>> cause, Map<String, List<String>> classes,
			Map<String, List<String>> killer, List<String> anyKiller, List<String> generic, String again) {
	}

	private static final Map<Phase, List<String>> PHASE = new EnumMap<>(Phase.class);
	private static final Map<Cause, List<String>> CAUSE = new EnumMap<>(Cause.class);
	private static final Map<String, List<String>> CLASS = new HashMap<>();
	/** Jokes for mobs whose name contains the key. */
	private static final Map<String, List<String>> KILLER = new HashMap<>();
	private static final List<String> ANY_KILLER = List.of(
			"{name} got clapped by a {killer}.",
			"A {killer} sends {name} its regards.",
			"{name} lost a fair fight to a {killer}. Fair for the {killer}, anyway.");
	private static final List<String> GENERIC = List.of(
			"{name} died. Skill issue.",
			"Press F for {name}.",
			"{name} has joined the ghost squad.",
			"{name} is now a professional ghost.");
	/** Teammates' deaths get jokes (the friendly tone; the toxic one is in ToxicLines). */
	private static final Pool ROASTS = new Pool(PHASE, CAUSE, CLASS, KILLER, ANY_KILLER, GENERIC, "it's a hobby now");

	// Your own deaths get excuses instead.
	private static final Map<Phase, List<String>> EXCUSE_PHASE = new EnumMap<>(Phase.class);
	private static final Map<Cause, List<String>> EXCUSE_CAUSE = new EnumMap<>(Cause.class);
	private static final Map<String, List<String>> EXCUSE_CLASS = new HashMap<>();
	private static final Map<String, List<String>> EXCUSE_KILLER = new HashMap<>();
	private static final Pool EXCUSES = new Pool(EXCUSE_PHASE, EXCUSE_CAUSE, EXCUSE_CLASS, EXCUSE_KILLER, List.of(
			"That {killer} was lag-buffed.",
			"A {killer} got me, but my ping was 500."), List.of(
			"Lag.",
			"My cat walked on my keyboard.",
			"I blinked.",
			"That one was the server's fault.",
			"Died on purpose to see if being a ghost is fun."), "it's a hobby now");

	static {
		EXCUSE_PHASE.put(Phase.CLEAR, List.of(
				"Died in clear. I was testing how hard the mobs hit. For science.",
				"Died in clear. All that loot slowed me down."));
		EXCUSE_PHASE.put(Phase.BLOOD, List.of(
				"Died at blood. The Watcher was clearly hacking.",
				"Died at blood. I was keeping the mobs busy for you, you're welcome."));
		EXCUSE_PHASE.put(Phase.TO_BOSS, List.of(
				"Died on the way to the boss. The floor lagged under me.",
				"Died before the boss. Just saving my strength."));
		EXCUSE_PHASE.put(Phase.MAXOR, List.of(
				"Died to Maxor. Those crystal hitboxes are broken, everyone knows that.",
				"Maxor got me. I was looking at the crystal, not the boss."));
		EXCUSE_PHASE.put(Phase.STORM, List.of(
				"Storm got me. My game froze right when the lightning hit.",
				"Died to Storm. I was standing exactly where the pad told me to."));
		EXCUSE_PHASE.put(Phase.TERMINALS, List.of(
				"Died in terminals. My mouse double-clicked, I swear.",
				"Died in terms. The GUI lagged and ate my click.",
				"Goldor got me in terms. I was doing YOUR terminal."));
		EXCUSE_PHASE.put(Phase.CORE, List.of(
				"Died at core. I was holding the door for you guys.",
				"Died at core. Goldor walked faster than he's allowed to."));
		EXCUSE_PHASE.put(Phase.NECRON, List.of(
				"Necron got me. The lasers didn't even render on my screen.",
				"Died to Necron. I was dodging the beam you were standing in."));
		EXCUSE_PHASE.put(Phase.DRAGONS, List.of(
				"Died to the dragons. The sun was in my eyes.",
				"Died in P5. I was lining up the perfect shot."));
		EXCUSE_PHASE.put(Phase.SADAN, List.of(
				"A giant stepped on me. My render distance hid it.",
				"Died to Sadan. That Terracotta wasn't where it should be."));
		EXCUSE_PHASE.put(Phase.LIVID, List.of("Died to Livid. They all looked the same, okay?"));
		EXCUSE_PHASE.put(Phase.THORN, List.of("Died to Thorn. I was aiming for the deer, it moved."));
		EXCUSE_PHASE.put(Phase.PROFESSOR, List.of("Died to The Professor. He never gave us the study guide."));
		EXCUSE_PHASE.put(Phase.SCARF, List.of("Died to Scarf. His minions ganged up on me."));
		EXCUSE_PHASE.put(Phase.BONZO, List.of("Died to Bonzo. The balloons distracted me."));
		EXCUSE_PHASE.put(Phase.BOSS, List.of("Died to {boss}. Hypixel moment."));

		EXCUSE_CAUSE.put(Cause.DISCONNECTED, List.of(
				"My wifi died, not me. That one doesn't count.",
				"I didn't die, my internet did."));
		EXCUSE_CAUSE.put(Cause.TRAP, List.of(
				"That trap wasn't there a second ago.",
				"Stepped on a trap. Someone should've marked it."));
		EXCUSE_CAUSE.put(Cause.CRUSHED, List.of(
				"Got crushed. That was a ghost block, I swear.",
				"Got crushed. The ceiling moved, I didn't."));
		EXCUSE_CAUSE.put(Cause.MOB, List.of("Died to a mob so laggy Hypixel couldn't even name it."));
		EXCUSE_CAUSE.put(Cause.DIED, List.of(
				"Died to nothing. Server lag spike, 100%.",
				"Just dropped dead. That's a Hypixel bug."));

		EXCUSE_CLASS.put("Archer", List.of("My arrows were mid-flight. They'd have saved me."));
		EXCUSE_CLASS.put("Berserk", List.of("I was this close to killing it first."));
		EXCUSE_CLASS.put("Healer", List.of("I was busy healing all of you, so I forgot me."));
		EXCUSE_CLASS.put("Mage", List.of("I was charging the big beam. It would've been worth it."));
		EXCUSE_CLASS.put("Tank", List.of("Tank down. Taking hits for the team, as designed."));

		EXCUSE_KILLER.put("Shadow Assassin", List.of("A Shadow Assassin teleported behind me. That's not fair."));
		EXCUSE_KILLER.put("Mimic", List.of("That Mimic looked exactly like a chest."));
		EXCUSE_KILLER.put("Fels", List.of("The Fels was invisible. Literally."));
		EXCUSE_KILLER.put("Sniper", List.of("Got sniped from across the map. Aimbot."));
		EXCUSE_KILLER.put("Super Archer", List.of("Got sniped from across the map. Aimbot."));
		EXCUSE_KILLER.put("Crypt", List.of("Crypt mobs hit way harder than the wiki says."));

		PHASE.put(Phase.CLEAR, List.of(
				"{name} died in clear. The mobs aren't even the boss.",
				"{name} died before we even found blood. Speedrunning the ghost life.",
				"{name} died in clear. Those were the warm-up mobs."));
		PHASE.put(Phase.BLOOD, List.of(
				"{name} died at blood. The Watcher didn't even have to try.",
				"{name} got blood camped. Wrong way round, buddy.",
				"The Watcher thanks {name} for the free kill."));
		PHASE.put(Phase.TO_BOSS, List.of(
				"{name} died on the way to the boss. Walking is hard.",
				"{name} died after blood. The boss hasn't even said hi yet."));
		PHASE.put(Phase.MAXOR, List.of(
				"{name} died to Maxor. The warm-up boss.",
				"{name} lost to Maxor. He's basically the tutorial.",
				"Maxor got {name}. The crystals go in the boss, not your face."));
		PHASE.put(Phase.STORM, List.of(
				"{name} got struck by Storm. Should've stayed out of the rain.",
				"{name} forgot Storm hits back. Umbrella next time?",
				"Today's forecast for {name}: Storm, with a 100% chance of ghost."));
		PHASE.put(Phase.TERMINALS, List.of(
				"{name} died in terminals. Clicking colours is hard, I get it.",
				"{name} died doing terms. Outplayed by a GUI.",
				"Goldor walked straight through {name}. Terminals aren't a hiding spot."));
		PHASE.put(Phase.CORE, List.of(
				"{name} died at core. So close to Necron, yet so far.",
				"{name} died at core. Goldor's last laugh."));
		PHASE.put(Phase.NECRON, List.of(
				"{name} got Necron'd. The lasers were not a suggestion.",
				"Necron 1, {name} 0.",
				"{name} died to Necron. Dodge the big red beams next time."));
		PHASE.put(Phase.DRAGONS, List.of(
				"{name} died to the dragons. All that work for this.",
				"{name} died in P5. Should've been watching the sky.",
				"The Wither King thanks {name} for the free kill."));
		PHASE.put(Phase.SADAN, List.of(
				"{name} got stomped by a giant. Look up!",
				"Sadan added {name} to his collection.",
				"{name} died to Sadan. Even the Terracottas are laughing."));
		PHASE.put(Phase.LIVID, List.of(
				"{name} hit the wrong Livid. Or the right one hit back.",
				"{name} lost to Livid. Colours are hard."));
		PHASE.put(Phase.THORN, List.of(
				"{name} lost to Thorn. A deer. A spirit deer.",
				"{name} died to Thorn. Bambi's evil cousin wins again."));
		PHASE.put(Phase.PROFESSOR, List.of(
				"{name} got schooled by The Professor.",
				"{name} failed The Professor's class. See you in summer school."));
		PHASE.put(Phase.SCARF, List.of(
				"{name} got wrapped up by Scarf.",
				"{name} died to Scarf. Cosy way to go, at least."));
		PHASE.put(Phase.BONZO, List.of(
				"{name} died to Bonzo. A clown. A literal clown.",
				"Bonzo honks in {name}'s honour."));
		PHASE.put(Phase.BOSS, List.of(
				"{name} died to {boss}. Boss fights are hard, huh?"));

		CAUSE.put(Cause.DISCONNECTED, List.of(
				"{name} disconnected. Their wifi rage quit for them.",
				"{name} lagged out. Blame the router, not the skill.",
				"{name}'s internet chose violence."));
		CAUSE.put(Cause.TRAP, List.of(
				"{name} found the trap. Thanks for checking it for us.",
				"{name} died to a trap. It was labelled. Probably."));
		CAUSE.put(Cause.CRUSHED, List.of(
				"{name} got crushed. Flat {name} is a new look.",
				"{name} was turned into a pancake."));
		CAUSE.put(Cause.MOB, List.of(
				"{name} died to \"a mob\". Hypixel won't even say which one.",
				"{name} died to a mob so forgettable it didn't get a name."));
		CAUSE.put(Cause.DIED, List.of(
				"{name} just... died. No killer. Pure talent.",
				"{name} died to absolutely nothing. Impressive."));

		CLASS.put("Archer", List.of(
				"{name} the Archer died. Keeping your distance is the whole point.",
				"Archer down. {name} forgot bows work from far away."));
		CLASS.put("Berserk", List.of(
				"{name} the Berserk died. Bro thought melee meant hugging.",
				"Berserk {name} went in head first. The head stayed there."));
		CLASS.put("Healer", List.of(
				"Our healer {name} died. Physician, heal thyself.",
				"{name} the Healer died. Who heals the healer?",
				"Healer down. {name} forgot to heal themselves."));
		CLASS.put("Mage", List.of(
				"{name} the Mage died. Too busy beaming to dodge.",
				"Mage down. {name}'s big brain couldn't save them."));
		CLASS.put("Tank", List.of(
				"{name} the Tank died. The tank. One job.",
				"Tank down. {name} was meant to take hits, not die from them."));

		KILLER.put("Shadow Assassin", List.of("{name} got backstabbed by a Shadow Assassin. Check behind you."));
		KILLER.put("Adventurer", List.of("{name} died to a {killer}. They were lost, {name} was losing."));
		KILLER.put("Fels", List.of("{name} got hugged too hard by a Fels."));
		KILLER.put("Withermancer", List.of("{name} got bullied by a Withermancer."));
		KILLER.put("Mimic", List.of("{name} opened the wrong chest. RIP."));
		KILLER.put("Crypt", List.of("{name} got buried by a {killer}. Crypts are for the dead, not you."));
		KILLER.put("Sniper", List.of("{name} got sniped. Should've zig-zagged."));
		KILLER.put("Super Archer", List.of("{name} got outshot by a Super Archer. Even the mobs have better aim."));
		KILLER.put("Zombie", List.of("{name} got clapped by a {killer}. A zombie. In this economy."));
		KILLER.put("Skelet", List.of("{name} got rattled by a {killer}."));
		KILLER.put("Wither Miner", List.of("{name} got mined by a Wither Miner. Wrong kind of ore."));
		KILLER.put("Terracotta", List.of("{name} died to a Terracotta. Killed by pottery."));
		KILLER.put("Lurker", List.of("{name} got jumped by a Crypt Lurker. It was lurking. It's in the name."));
	}

	private static final Map<String, Integer> DEATHS = new HashMap<>();
	private static Phase phase = Phase.CLEAR;
	private static String boss;
	private static boolean bloodCleared, coreOpen;
	private static ClientLevel level;
	/** When the last few jokes were made, to spot a wipe. */
	private static final ArrayDeque<Long> RECENT = new ArrayDeque<>();
	private static String lastTemplate;
	private static String lastJoke;

	private SkillIssue() {
	}

	/** Every system chat line (Taz.onSystemChat). */
	public static void onChat(String line) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			level = mc.level;
			reset();
		}
		follow(line);
		Matcher m = DEATH.matcher(line);
		if (m.find()) onDeath(mc, m.group(1), m.group(2));
	}

	private static void reset() {
		DEATHS.clear();
		phase = Phase.CLEAR;
		boss = null;
		bloodCleared = coreOpen = false;
	}

	/** Keeps track of where the run is from its chat. */
	private static void follow(String line) {
		if (line.equals("The BLOOD DOOR has been opened!")) {
			if (!bloodCleared) phase = Phase.BLOOD;
			return;
		}
		if (line.equals("The Core entrance is opening!")) {
			coreOpen = true;
			phase = Phase.CORE;
			return;
		}
		if (RELIC.matcher(line).matches()) {
			phase = Phase.DRAGONS;
			return;
		}
		Matcher m = BOSS_LINE.matcher(line);
		if (!m.matches()) return;
		String speaker = m.group(1).trim();
		if (speaker.equals("The Watcher")) {
			if (m.group(2).startsWith("You have proven yourself")) {
				bloodCleared = true;
				phase = Phase.TO_BOSS;
			} else if (!bloodCleared) {
				phase = Phase.BLOOD;
			}
			return;
		}
		Phase p = phaseOf(speaker);
		if (speaker.equals("Necron") && m.group(2).startsWith("All this, for nothing")) p = Phase.DRAGONS;
		// The Watcher brings old bosses back as blood mobs, and they talk too: only F6 / F7 bosses count before blood is done.
		boolean floorSixOrSeven = p == Phase.SADAN || p == Phase.MAXOR || p == Phase.STORM || p == Phase.TERMINALS || p == Phase.CORE
				|| p == Phase.NECRON || p == Phase.DRAGONS;
		if (!bloodCleared && !floorSixOrSeven) return;
		phase = p;
		if (p == Phase.BOSS) boss = speaker;
	}

	/** A boss's name to the phase it means (Goldor is terminals until the core opens). */
	private static Phase phaseOf(String name) {
		return switch (name) {
			case "Maxor" -> Phase.MAXOR;
			case "Storm" -> Phase.STORM;
			case "Goldor" -> coreOpen ? Phase.CORE : Phase.TERMINALS;
			case "Necron" -> Phase.NECRON;
			case "Wither King" -> Phase.DRAGONS;
			case "Sadan" -> Phase.SADAN;
			case "Livid" -> Phase.LIVID;
			case "Thorn" -> Phase.THORN;
			case "The Professor" -> Phase.PROFESSOR;
			case "Scarf" -> Phase.SCARF;
			case "Bonzo" -> Phase.BONZO;
			default -> Phase.BOSS;
		};
	}

	private static void onDeath(Minecraft mc, String who, String how) {
		SkillIssueConfig cfg = SkillIssueConfig.get();
		if (!cfg.enabled || mc.player == null || !DungeonRooms.inDungeon()) return;
		boolean self = who.equals("You");
		String name = self ? mc.player.getName().getString() : who;
		int count = DEATHS.merge(name.toLowerCase(), 1, Integer::sum);
		if (self && !cfg.yourself) return;
		Pool pool = pool(self);
		// Every death gets its joke straight away; only a wipe (more than 3 deaths in 5 seconds) is cut short.
		long now = System.currentTimeMillis();
		while (!RECENT.isEmpty() && now - RECENT.peekFirst() > WIPE_MS) RECENT.pollFirst();
		if (RECENT.size() >= WIPE_JOKES) return;
		RECENT.addLast(now);
		Matcher k = KILLED.matcher(how);
		String killer = k.matches() ? k.group(1).trim() : null;
		Cause cause = killer != null ? Cause.KILLED : switch (how) {
			case "disconnected" -> Cause.DISCONNECTED;
			case "died to a mob" -> Cause.MOB;
			case "died to a trap" -> Cause.TRAP;
			case "was crushed", "were crushed" -> Cause.CRUSHED;
			default -> Cause.DIED;
		};
		String joke = joke(pool, name, cause, killer, phase, DungeonParty.classOf(name), count);
		lastJoke = TazChat.PREFIX + joke;
		if (cfg.where == SkillIssueConfig.Where.PARTY && !DungeonParty.solo()) TazChat.party(joke);
		else TazChat.info(joke);
	}

	/** Every line that fits, the more specific ones several times so they come up more. */
	private static List<String> candidates(Pool pool, Cause cause, String killer, Phase phase, String dungeonClass) {
		List<String> out = new ArrayList<>();
		if (cause == Cause.DISCONNECTED) return pool.cause().get(Cause.DISCONNECTED);
		// "killed by Storm" says the phase better than the chat did.
		Phase where = killer != null && phaseOf(killer) != Phase.BOSS ? phaseOf(killer) : phase;
		add(out, pool.phase().get(where), 3);
		if (killer != null && phaseOf(killer) == Phase.BOSS) {
			boolean special = false;
			for (Map.Entry<String, List<String>> e : pool.killer().entrySet()) {
				if (killer.contains(e.getKey())) {
					add(out, e.getValue(), 3);
					special = true;
				}
			}
			if (!special) add(out, pool.anyKiller(), 1);
		}
		if (cause != Cause.KILLED) add(out, pool.cause().get(cause), 2);
		if (dungeonClass != null) add(out, pool.classes().get(dungeonClass), 2);
		add(out, pool.generic(), 1);
		return out;
	}

	private static void add(List<String> out, List<String> jokes, int weight) {
		if (jokes == null) return;
		for (int i = 0; i < weight; i++) out.addAll(jokes);
	}

	/** The lines for this death: a roast for a teammate, an excuse for you, in the tone picked in the settings. */
	private static Pool pool(boolean self) {
		boolean toxic = SkillIssueConfig.get().tone == SkillIssueConfig.Tone.TOXIC;
		return self ? (toxic ? ToxicLines.EXCUSES : EXCUSES) : (toxic ? ToxicLines.ROASTS : ROASTS);
	}

	/** One line, never the same one twice in a row, with a death count from their second death on. */
	private static String joke(Pool lines, String name, Cause cause, String killer, Phase where, String dungeonClass, int count) {
		List<String> pool = candidates(lines, cause, killer, where, dungeonClass);
		String template = pool.get(RANDOM.nextInt(pool.size()));
		for (int i = 0; i < 5 && template.equals(lastTemplate) && pool.size() > 1; i++) template = pool.get(RANDOM.nextInt(pool.size()));
		lastTemplate = template;
		String joke = template.replace("{name}", name).replace("{killer}", killer == null ? "mob" : killer)
				.replace("{boss}", boss == null ? "the boss" : boss);
		if (count == 2) joke += " (death #2)";
		else if (count > 2) joke += " (death #" + count + ", " + lines.again() + ")";
		return joke;
	}

	/** The "Try it" button: a few jokes about teammates and an excuse for you, shown only to you. */
	public static void sample() {
		TazChat.info(joke(pool(false), "Steve", Cause.KILLED, "Goldor", Phase.TERMINALS, null, 1));
		TazChat.info(joke(pool(false), "Alex", Cause.KILLED, "Shadow Assassin", Phase.CLEAR, "Healer", 1));
		TazChat.info(joke(pool(false), "Steve", Cause.CRUSHED, null, Phase.STORM, "Archer", 2));
		TazChat.info("When you die: " + joke(pool(true), "", Cause.KILLED, "Storm", Phase.STORM, null, 1));
	}

	/** Test hooks. */
	public static String lastJoke() {
		return lastJoke;
	}

	public static String phase() {
		return phase.name();
	}

	/** Test hook: every line that fits a death (an excuse when it's yours). */
	public static List<String> jokesFor(boolean self, String cause, String killer, String phase, String dungeonClass) {
		return candidates(pool(self), Cause.valueOf(cause), killer, Phase.valueOf(phase), dungeonClass);
	}
}
