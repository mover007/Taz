package com.taz.foraging.safari;

import com.taz.foraging.safari.Critters.Biome;
import com.taz.foraging.safari.Critters.Critter;
import com.taz.foraging.safari.Critters.QuestItem;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Critter Safari HUD. */
public final class SafariGuide extends TextHud {
	private static final int MAX_CRITTERS = 9;

	public SafariGuide() {
		super("Critter Safari", 0.01, 0.30);
	}

	@Override
	protected HudPos pos() {
		return SafariConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		SafariConfig cfg = SafariConfig.get();
		return cfg.enabled && cfg.guide;
	}

	@Override
	protected List<String> lines(boolean preview) {
		return switch (SafariHelper.where()) {
			case INSIDE -> inside();
			case ENTRANCE -> entrance();
			case ELSEWHERE -> preview ? sample() : List.of();
		};
	}

	private static List<String> inside() {
		if (SafariHelper.fullClearBiome() != null) return fullClear(SafariHelper.fullClearBiome());
		List<String> lines = new ArrayList<>();
		long minutes = (SafariHelper.timeLeftMs() + 59_999) / 60_000;
		lines.add("§c§lCritter Safari §f" + SafariHelper.done() + "§7/" + Critters.ALL.size() + " §8| §7~§f" + minutes + "m §7left");
		// Only once Taz has seen capsules: 0 from the start would mean it can't find them, not that you're out.
		int caps = SafariHelper.hadCapsules() ? SafariHelper.capsules() : -1;
		Biome hotspot = SafariHelper.hotspot();
		String info = (caps >= 0 ? (caps <= 5 ? "§cCapsules " : "§7Capsules §f") + caps : "")
				+ (hotspot != null ? (caps >= 0 ? "  " : "") + "§7Hotspot §f" + hotspot.label : "");
		if (!info.isEmpty()) lines.add(info);
		String way = SafariWay.hudLine();
		if (way != null) lines.add(way);
		if (SafariConfig.get().countdowns) lines.addAll(SafariHelper.countdowns());

		Biome here = SafariHelper.biome();
		if (here != null) {
			int left = SafariHelper.left(here);
			lines.add("§c§l" + here.label + " §7(you're here) " + (left == 0 ? "§fdone ✔" : "§f" + left + " left"));
			int shown = 0;
			for (Critter c : Critters.in(here)) {
				if (!SafariHelper.toDo(c)) continue;
				if (++shown > MAX_CRITTERS) break;
				lines.add(" §f" + c.name() + " §7- " + (c.name().equals("Scrappy") ? SafariHelper.scrappyHint() : SafariHelper.progressHint(c)));
			}
			if (left == 0) lines.add("§7" + SafariHelper.nextStep());
		} else {
			lines.add("§7Walk into a biome to see its critters.");
		}
		// What's caught takes no room: done biomes and quest items for caught critters are left out.
		StringBuilder others = new StringBuilder();
		for (Biome b : Biome.values()) {
			int left = SafariHelper.left(b);
			if (b == here || left == 0) continue;
			if (!others.isEmpty()) others.append(" §8| ");
			others.append("§7").append(b.label).append(" §f").append(left);
		}
		if (!others.isEmpty()) lines.add(others.toString());
		for (Map.Entry<String, Integer> e : SafariHelper.questItems().entrySet()) {
			QuestItem q = Critters.quest(e.getKey());
			if (q.brings().stream().map(Critters::named).noneMatch(c -> c != null && SafariHelper.toDo(c))) continue;
			lines.add("§f" + q.name() + (e.getValue() > 1 ? " x" + e.getValue() : "") + "§7: " + q.use());
		}
		return lines;
	}

	/** Full clear: your biome's critters with how many of each you caught, and the way to the next one. */
	private static List<String> fullClear(Biome clear) {
		List<String> lines = new ArrayList<>();
		long minutes = (SafariHelper.timeLeftMs() + 59_999) / 60_000;
		lines.add("§c§lFull clear §f" + clear.label + " §8| §7~§f" + minutes + "m §7left");
		int caps = SafariHelper.hadCapsules() ? SafariHelper.capsules() : -1;
		int inView = (int) SafariHelper.sightings().stream().filter(s -> s.critter().biome() == clear).count();
		lines.add("§7Caught here §f" + SafariHelper.caughtIn(clear) + "  §7In view §f" + inView
				+ (caps >= 0 ? (caps <= 5 ? "  §cCapsules " : "  §7Capsules §f") + caps : ""));
		if (SafariHelper.biome() != null && SafariHelper.biome() != clear) lines.add("§7Head to the §f" + clear.label + " §7Biome");
		String way = SafariWay.hudLine();
		if (way != null) lines.add(way);
		if (SafariConfig.get().countdowns) lines.addAll(SafariHelper.countdowns());
		int shown = 0;
		for (Critter c : Critters.in(clear)) {
			if (++shown > MAX_CRITTERS) break;
			int n = SafariHelper.caughtCount(c);
			lines.add(" §f" + c.name() + (n > 0 ? " §7x" + n : "") + " §7- "
					+ (c.name().equals("Scrappy") ? SafariHelper.scrappyHint() : SafariHelper.progressHint(c)));
		}
		for (Map.Entry<String, Integer> e : SafariHelper.questItems().entrySet()) {
			QuestItem q = Critters.quest(e.getKey());
			if (q.brings().stream().map(Critters::named).noneMatch(c -> c != null && SafariHelper.toDo(c))) continue;
			lines.add("§f" + q.name() + (e.getValue() > 1 ? " x" + e.getValue() : "") + "§7: " + q.use());
		}
		return lines;
	}

	private static List<String> entrance() {
		List<String> lines = new ArrayList<>();
		lines.add("§c§lCritter Safari");
		lines.add("§7You need §fHunting XV §7and a §fSafari Ticket");
		String ticket = SafariHelper.ticket();
		lines.add(ticket != null ? "§7Ticket: §f" + ticket + " ✔" : "§7Ticket: §cnone with you §7(win them in Miria's contests)");
		int party = Party.members().size();
		lines.add(party >= 2 ? "§7Party: §f" + party + "§7/4 §7(each takes one biome)" : "§7Go with §f3-4 players§7: each takes one biome");
		lines.add("§7Give your ticket to the §fSafari Manager §7to go in");
		return lines;
	}

	private static List<String> sample() {
		return List.of(
				"§c§lCritter Safari §f12§7/37 §8| §7~§f41m §7left",
				"§7Capsules §f38  §7Hotspot §fIcy",
				"§7Next: §fHoneybug §7- punch the bee nest §f23m",
				"§eShyworm up §f0:14 §7- don't let it see you",
				"§c§lForest §7(you're here) §f2 left",
				" §fMacaw §7- feed the Birdfeeder a Bag of Seeds",
				" §fHideonfloor §7- blends into the floor; runs when hit",
				"§7Cavern §f4 §8| §7Icy §f9",
				"§fBag of Seeds§7: feed it to the Birdfeeder in the Forest (the best bait for Macaws)");
	}
}
