package com.taz.dungeons.dungeon;

import com.taz.dungeons.DungeonPredictorClient;
import com.taz.dungeons.config.ModConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat lines that mark milestones, loaded from config/taz/dungeons/triggers.json so
 * they can be fixed without a rebuild when Hypixel rewrites dialogue (0.27.2 just
 * shortened all of it).
 *
 * Two kinds of trigger:
 *  - "regex":   matched against the chat line with formatting stripped. Anchor with ^
 *               so party or guild chat can't fake it.
 *  - "speaker": fires on the first "[BOSS] <speaker>: ..." line. It doesn't depend on the
 *               exact dialogue, so it survives dialogue rewrites. "@floorboss" means the
 *               current floor's boss (Sadan on F6/M6, Maxor on F7/M7, ...). Don't use "any
 *               [BOSS]": the Watcher spawns old bosses like Scarf as blood mobs and they talk too.
 * A milestone only counts if the current floor uses it and it comes after the last
 * milestone reached, so extra matches are harmless.
 */
public final class Triggers {
	/** Raise when the built-in defaults change. Older files are backed up and replaced. */
	public static final int DEFAULTS_VERSION = 4;
	private static final Path FILE = ModConfig.DIR.resolve("triggers.json");
	public static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] ([^:]+): ");

	public static final class Def {
		public String milestone;
		public String regex;
		public String speaker;

		Def() {
		}

		static Def regex(Milestone m, String regex) {
			Def d = new Def();
			d.milestone = m.name();
			d.regex = regex;
			return d;
		}

		static Def speaker(Milestone m, String speaker) {
			Def d = new Def();
			d.milestone = m.name();
			d.speaker = speaker;
			return d;
		}
	}

	private static final class FileModel {
		int version;
		String note;
		List<Def> triggers;
	}

	private record Compiled(Milestone milestone, Pattern pattern, String speaker) {
	}

	private static List<Compiled> compiled = List.of();

	private Triggers() {
	}

	public static List<Def> defaults() {
		List<Def> d = new ArrayList<>();
		d.add(Def.regex(Milestone.START, "^\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.$"));
		d.add(Def.regex(Milestone.BLOOD_OPEN, "^The BLOOD DOOR has been opened!$"));
		// Fallback if the door message is missed: the Watcher always talks once blood opens.
		d.add(Def.speaker(Milestone.BLOOD_OPEN, "The Watcher"));
		d.add(Def.regex(Milestone.WATCHER_DONE, "^\\[BOSS] The Watcher: That will be enough for now\\.$"));
		d.add(Def.regex(Milestone.BLOOD_CLEAR, "^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$"));
		d.add(Def.speaker(Milestone.BOSS_ENTER, "@floorboss"));

		// F6 / M6 - Sadan. Several lines per phase change (from the SkyBlock wiki) so this keeps
		// working if 0.27.2's shorter dialogue removed some. The first one seen wins.
		// Giants phase: the "Phase Two Starts" lines, then lines that only appear once the Giants are out.
		for (String l : new String[]{
				"ENOUGH!",
				"My giants! Unleashed!",
				"Rumor has it those are dead Precursors, legendary civilization.",
				"My first time using them, extremely interesting!",
				"Which one is your favorite? Those laser eyes...beautiful!",
				"This is what peak Necromancy looks like!",
				"Boulders, lasers, stomping, and giant swords! Oh my!",
				"You've managed to kill a Giant! Good luck with the rest of them!",
				"Wow, one Giant down already? You guys are good!",
				"How many players does it take to kill a Giant?",
				"You killed my weakest Giant!"}) {
			d.add(Def.regex(Milestone.GIANTS, sadan(l)));
		}
		// Sadan phase: the "Phase Three Starts" lines, plus the M6 Giants-revive line.
		for (String l : new String[]{
				"You did it. I understand now, you have earned my respect.",
				"If only you had become my disciples instead of this incompetent bunch.",
				"Maybe in another life. Until then, meet my ultimate corpse.",
				"I'm sorry but I need to concentrate. I wish it didn't have to come to this.",
				"You thought I'd fall for the same tricks as those before me? Not today!"}) {
			d.add(Def.regex(Milestone.SADAN, sadan(l)));
		}

		// F7 / M7 - each phase starts when its boss first speaks.
		d.add(Def.speaker(Milestone.STORM, "Storm"));
		d.add(Def.speaker(Milestone.GOLDOR, "Goldor"));
		d.add(Def.regex(Milestone.CORE, "^The Core entrance is opening!$"));
		d.add(Def.speaker(Milestone.NECRON, "Necron"));
		d.add(Def.speaker(Milestone.WITHER_KING, "Wither King"));
		// M7 phase 5: the Wither King doesn't speak any more, so use Necron's death line or the relic pickups.
		d.add(Def.regex(Milestone.WITHER_KING, "^\\[BOSS] Necron: All this, for nothing\\.\\.\\.$"));
		d.add(Def.regex(Milestone.WITHER_KING, "^\\S+ picked the Corrupted \\w+ Relic!$"));

		d.add(Def.regex(Milestone.END, "^(?:☠ )?Defeated .+ in (?:\\d+h )?(?:\\d+m )?\\d+s$"));
		d.add(Def.regex(Milestone.END, "^> EXTRA STATS <$"));
		return d;
	}

	private static String sadan(String line) {
		return "^\\[BOSS] Sadan: " + Pattern.quote(line) + "$";
	}

	public static void load() {
		List<Def> defs = defaults();
		try {
			FileModel model = null;
			if (Files.exists(FILE)) {
				model = ModConfig.GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), FileModel.class);
			}
			if (model == null || model.version < DEFAULTS_VERSION || model.triggers == null) {
				if (model != null) {
					Files.copy(FILE, FILE.resolveSibling("triggers.json.bak"), StandardCopyOption.REPLACE_EXISTING);
				}
				model = new FileModel();
				model.version = DEFAULTS_VERSION;
				model.note = "Edit freely, then press Reload files in /taz > Dungeons > Tools. Regex is matched against the chat line with formatting removed. "
						+ "'speaker' fires on the first [BOSS] line from that speaker ('*' = any boss except The Watcher). "
						+ "boss-lines.log in this folder lists every boss line seen, useful after Hypixel changes dialogue.";
				model.triggers = defs;
				Files.createDirectories(ModConfig.DIR);
				Files.writeString(FILE, ModConfig.GSON.toJson(model), StandardCharsets.UTF_8);
			} else {
				defs = model.triggers;
			}
		} catch (Exception e) {
			DungeonPredictorClient.LOGGER.error("Failed to load {}, using built-in triggers", FILE, e);
		}
		compiled = compile(defs);
	}

	private static List<Compiled> compile(List<Def> defs) {
		List<Compiled> out = new ArrayList<>();
		for (Def d : defs) {
			Milestone m = Milestone.fromName(d.milestone);
			if (m == null) {
				DungeonPredictorClient.LOGGER.warn("Unknown milestone '{}' in triggers.json", d.milestone);
				continue;
			}
			try {
				Pattern p = d.regex != null && !d.regex.isBlank() ? Pattern.compile(d.regex) : null;
				String speaker = d.speaker != null && !d.speaker.isBlank() ? d.speaker.trim() : null;
				if (p == null && speaker == null) continue;
				out.add(new Compiled(m, p, speaker));
			} catch (PatternSyntaxException e) {
				DungeonPredictorClient.LOGGER.warn("Bad regex for {} in triggers.json: {}", d.milestone, e.getMessage());
			}
		}
		return List.copyOf(out);
	}

	/** Every milestone whose trigger matches this (cleaned) chat line. */
	public static List<Milestone> match(String line, Floor floor) {
		List<Milestone> hits = new ArrayList<>(2);
		String speaker = null;
		Matcher bm = BOSS_LINE.matcher(line);
		if (bm.find()) speaker = bm.group(1).trim();

		for (Compiled c : compiled) {
			boolean hit;
			if (c.pattern() != null) {
				hit = c.pattern().matcher(line).find();
			} else if (speaker == null) {
				hit = false;
			} else if (c.speaker().equals("@floorboss")) {
				hit = floor != null && floor.bossName != null && speaker.equalsIgnoreCase(floor.bossName);
			} else if (c.speaker().equals("*")) {
				hit = !speaker.equals("The Watcher");
			} else {
				hit = speaker.equalsIgnoreCase(c.speaker());
			}
			if (hit && !hits.contains(c.milestone())) hits.add(c.milestone());
		}
		return hits;
	}
}
