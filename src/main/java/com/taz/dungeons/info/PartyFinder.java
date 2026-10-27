package com.taz.dungeons.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.util.Items;
import com.taz.util.Party;
import com.taz.util.TazChat;
import com.taz.xpassist.util.Http;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party Finder: when someone joins your dungeon group, their stats in chat (Catacombs and class levels, secrets,
 * magical power and their S+ time on the floor you pick), and Auto Kick for players who don't meet your needs. From
 * NoammAddons' Party Finder.
 */
public final class PartyFinder {
	private static final Pattern JOINED = Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$");
	private static final Pattern MEMBER = Pattern.compile("^\\s*(\\w{1,16}):?\\s+(Archer|Tank|Berserk|Healer|Mage)\\s*\\((\\d+)\\)\\s*$");
	private static final Pattern LEVEL_REQUIRED = Pattern.compile("Dungeon Level Required:\\s*(\\d+)");
	private static final List<String> CLASSES = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");
	private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Taz party finder");
		t.setDaemon(true);
		return t;
	});

	/** What Taz learned about a player. */
	public record Stats(String name, int cata, double classAverage, long secrets, double secretsPerRun, int mp, long pbMs) {
	}

	private PartyFinder() {
	}

	public static void onChat(String line) {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		if (!cfg.enabled || (!cfg.joinStats && !cfg.autoKick)) return;
		Matcher m = JOINED.matcher(line);
		if (!m.matches()) return;
		String name = m.group(1);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) return;
		POOL.submit(() -> {
			try {
				Stats s = fetch(name);
				mc.execute(() -> {
					if (cfg.joinStats) TazChat.info(describe(s));
					if (cfg.autoKick && Party.maybeLeader()) kickIfShort(s);
				});
			} catch (Exception e) {
				Taz.LOGGER.warn("Party finder: couldn't get {}'s stats", name, e);
				mc.execute(() -> TazChat.info("Couldn't get " + name + "'s stats."));
			}
		});
	}

	/** The player's stats, from Mojang (their id) and EliteBot (their selected profile). Blocking. */
	public static Stats fetch(String name) throws Exception {
		JsonObject mojang = JsonParser.parseString(Http.getString("https://api.mojang.com/users/profiles/minecraft/" + name)).getAsJsonObject();
		String uuid = mojang.get("id").getAsString();
		String realName = mojang.has("name") ? mojang.get("name").getAsString() : name;
		JsonArray profiles = JsonParser.parseString(Http.getString("https://api.elitebot.dev/Profiles/" + uuid)).getAsJsonArray();
		String profileId = null;
		for (JsonElement p : profiles) {
			JsonObject o = p.getAsJsonObject();
			if (profileId == null || (o.has("selected") && o.get("selected").getAsBoolean())) profileId = o.get("profileId").getAsString();
		}
		if (profileId == null) throw new IllegalStateException("no profiles");
		JsonObject member = JsonParser.parseString(Http.getString("https://api.elitebot.dev/Profile/" + uuid + "/" + profileId)).getAsJsonObject();
		return parse(realName, member);
	}

	/** Reads an EliteBot profile member. */
	public static Stats parse(String name, JsonObject member) {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		JsonObject stats = member.getAsJsonObject("stats");
		JsonObject d = stats.getAsJsonObject("dungeons");
		int cata = d.has("catacombsLevel") ? d.get("catacombsLevel").getAsInt() : 0;
		double avg = d.has("classAverage") ? d.get("classAverage").getAsDouble() : 0;
		long secrets = d.has("secretsFound") ? d.get("secretsFound").getAsLong() : 0;
		long runs = 0;
		for (String key : List.of("floors", "masterFloors")) {
			if (d.has(key) && d.getAsJsonObject(key).has("total")) runs += d.getAsJsonObject(key).getAsJsonObject("total").get("completions").getAsLong();
		}
		JsonObject floors = d.has(cfg.master ? "masterFloors" : "floors") ? d.getAsJsonObject(cfg.master ? "masterFloors" : "floors") : new JsonObject();
		String floor = String.valueOf(cfg.floor);
		long pb = floors.has(floor) ? floors.getAsJsonObject(floor).get("fastestTimeSPlus").getAsLong() : 0;
		int mp = 0;
		JsonObject acc = stats.has("accessories") ? stats.getAsJsonObject("accessories") : null;
		if (acc != null && acc.has("highestMagicalPower")) mp = acc.get("highestMagicalPower").getAsInt();
		return new Stats(name, cata, avg, secrets, runs > 0 ? (double) secrets / runs : 0, mp, pb);
	}

	private static String floorName() {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		return (cfg.master ? "M" : "F") + cfg.floor;
	}

	private static String time(long ms) {
		long s = ms / 1000;
		return s / 60 + ":" + String.format("%02d", s % 60);
	}

	public static String describe(Stats s) {
		return s.name() + ": Cata " + s.cata() + " (class avg " + String.format("%.1f", s.classAverage()) + "), "
				+ String.format(Locale.ROOT, "%.1fk", s.secrets() / 1000.0) + " secrets (" + String.format(Locale.ROOT, "%.1f", s.secretsPerRun()) + "/run), "
				+ s.mp() + " MP, " + floorName() + " S+ " + (s.pbMs() > 0 ? time(s.pbMs()) : "none");
	}

	/** Why a player doesn't meet the needs, or null when they do. */
	public static String shortfall(Stats s) {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		if (s.pbMs() <= 0) return "no " + floorName() + " S+";
		if (s.pbMs() / 1000 > cfg.maxSeconds) return floorName() + " S+ " + time(s.pbMs()) + " (needs " + time(cfg.maxSeconds * 1000L) + ")";
		if (s.secrets() < cfg.minSecretsK * 1000L) return String.format(Locale.ROOT, "%.1fk", s.secrets() / 1000.0) + " secrets (needs " + cfg.minSecretsK + "k)";
		if (cfg.minMp > 0 && s.mp() < cfg.minMp) return s.mp() + " MP (needs " + cfg.minMp + ")";
		return null;
	}

	private static void kickIfShort(Stats s) {
		String why = shortfall(s);
		if (why == null) return;
		PartyFinderConfig cfg = PartyFinderConfig.get();
		if (cfg.informKicked) TazChat.party("Kicking " + s.name() + ": " + why);
		TazChat.info("Kicking " + s.name() + ": " + why);
		Minecraft mc = Minecraft.getInstance();
		new Thread(() -> {
			try {
				Thread.sleep(800);
			} catch (InterruptedException ignored) {
				return;
			}
			mc.execute(() -> {
				if (mc.getConnection() != null) mc.getConnection().sendCommand("p kick " + s.name());
			});
		}, "Taz auto kick").start();
	}

	// ---- the Party Finder menu

	private static boolean inMenu() {
		Minecraft mc = Minecraft.getInstance();
		PartyFinderConfig cfg = PartyFinderConfig.get();
		return cfg.enabled && mc.screen != null && mc.screen.getTitle().getString().equals("Party Finder");
	}

	/** Which classes a listed party is missing. */
	public static List<String> missing(ItemStack stack) {
		List<String> left = new ArrayList<>(CLASSES);
		for (String line : Items.lore(stack)) {
			Matcher m = MEMBER.matcher(line);
			if (m.matches()) left.remove(m.group(2));
		}
		return left;
	}

	/** On each party's head: its level requirement (red, top right) and the classes it's missing (bottom). */
	public static void slot(GuiGraphicsExtractor g, Slot slot) {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		if (!inMenu() || !slot.hasItem() || !slot.getItem().is(net.minecraft.world.item.Items.PLAYER_HEAD) || slot.index >= 45) return;
		ItemStack stack = slot.getItem();
		Font font = Minecraft.getInstance().font;
		g.pose().pushMatrix();
		g.pose().translate(slot.x, slot.y);
		g.pose().scale(0.6f, 0.6f);
		if (cfg.levelRequired) {
			for (String line : Items.lore(stack)) {
				Matcher m = LEVEL_REQUIRED.matcher(line);
				if (m.find()) g.text(font, m.group(1), Math.round(16 / 0.6f) - font.width(m.group(1)), 0, com.taz.hud.TazTheme.RED, true);
			}
		}
		if (cfg.missingClasses) {
			StringBuilder letters = new StringBuilder();
			for (String c : missing(stack)) letters.append(c.charAt(0));
			if (!letters.isEmpty()) g.text(font, letters.toString(), 0, Math.round(16 / 0.6f) - 8, com.taz.hud.TazTheme.PALE_RED, true);
		}
		g.pose().popMatrix();
	}

	/** The tooltip of a listed party: "Missing: Archer, Mage" at the bottom. */
	public static void tooltip(ItemStack stack, List<Component> lines) {
		PartyFinderConfig cfg = PartyFinderConfig.get();
		if (!cfg.missingList || !inMenu() || !stack.is(net.minecraft.world.item.Items.PLAYER_HEAD)) return;
		boolean party = Items.lore(stack).stream().anyMatch(l -> MEMBER.matcher(l).matches());
		if (!party) return;
		List<String> left = missing(stack);
		lines.add(Component.literal("Missing: ").withStyle(s -> s.withColor(0xFF2E2E))
				.append(Component.literal(left.isEmpty() ? "nothing" : String.join(", ", left)).withStyle(s -> s.withColor(0xA89393))));
	}
}
