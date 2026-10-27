package com.taz.foraging.safari;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.foraging.Galatea;
import com.taz.foraging.GalateaRecorder;
import com.taz.foraging.IslandGuide;
import com.taz.foraging.safari.Critters.Biome;
import com.taz.foraging.safari.Critters.Critter;
import com.taz.foraging.safari.Critters.QuestItem;
import com.taz.hud.TazTheme;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import com.taz.util.Items;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Critter Safari helper. Hypixel labels each critter with a name-tag entity whose name is exactly the critter's
 * ("Foxtrot", "SPARKLING Foxtrot") just above its body (a mob, an armour stand wearing it, or a display entity), so
 * Taz reads the labels (critterMod's way: colours and Hypixel's icon glyphs stripped, the body the nearest mob within 3
 * blocks), then boxes the ones you still need and makes them glow, with its own name tag. Catches come
 * from chat: "CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!" (yours) and "LOOT SHARE! You received a
 * Foxtrot Shard from Steve catching a Foxtrot!" (a party member's). A run is one Safari instance, so it starts over in
 * every new world. Chat patterns and how critters are built from critterMod (MrCloudy2, MIT).
 */
public final class SafariHelper {
	public enum Where { ELSEWHERE, ENTRANCE, INSIDE }

	public record Sighting(Critter critter, Entity label, Entity body, boolean sparkling) {
	}

	public static final int RED = 0xFF2E2E;
	public static final int GOLD = 0xFFC830;
	/** The Safari closes about an hour after it opens, whatever's left. */
	public static final long RUN_MS = 60 * 60_000L;
	private static final int LOW_CAPSULES = 5;
	/** How far from its name tag a critter's body can be (critterMod pairs them within 3 blocks). */
	private static final double BODY_RANGE = 3.0;

	private static final Pattern LOOT_SHARE = Pattern.compile("^LOOT SHARE! .* from (\\w{1,16}) (?:catching|finding) .+!$");
	private static final Pattern HOTSPOT = Pattern.compile("^HOTSPOT! Your Hunting Hotspot is the (\\w+) Biome!$");
	private static final Pattern BIRDFEEDER = Pattern.compile("^\\S+ ([A-Za-z ]+?)s? (?:were|was) attracted to the Birdfeeder!$");

	/** Test hooks: where you are, and which biome. */
	public static Where testWhere;
	public static Biome testBiome;

	private static Where where = Where.ELSEWHERE;
	private static Biome biome;
	private static List<Sighting> sightings = List.of();
	/** Entity id -> glow colour, rebuilt with each look round. */
	private static final Map<Integer, Integer> GLOW = new HashMap<>();
	private static final Set<Integer> SPARKLING_SEEN = new HashSet<>();
	/** Critter-like name tags Taz couldn't read, written to the log once each. */
	private static final Set<String> MISSED = new HashSet<>();

	private static ClientLevel runLevel;
	private static long runStart;
	/** Caught by anyone in the party this run. */
	private static final Set<Critter> CAUGHT_RUN = new HashSet<>();
	private static final Set<Biome> BIOMES_TOLD = EnumSet.noneOf(Biome.class);
	private static Biome hotspot;
	private static boolean welcomed, toldTen, toldTwo, toldLow, toldOut, toldEntrance, toldScrappy;
	private static int capsules = -1;
	private static boolean hadCapsules;
	/** Quest items in your inventory, by name, and how many. */
	private static Map<String, Integer> quest = new LinkedHashMap<>();
	private static String ticket;
	private static int ticks;

	private SafariHelper() {
	}

	// ---- where you are

	public static Where where() {
		return where;
	}

	public static boolean inside() {
		return where == Where.INSIDE;
	}

	/** The biome you're in, or null when Taz can't tell. */
	public static Biome biome() {
		return biome;
	}

	private static Where locate(Minecraft mc) {
		if (testWhere != null) return testWhere;
		Where w = classify(SkyblockLocation.area());
		if (w != Where.ELSEWHERE) return w;
		for (String line : GalateaRecorder.sidebarLines()) {
			String t = line.trim();
			if (t.equals("Critter Safari") || sidebarBiome(t) != null) return Where.INSIDE;
			if (t.contains("Safari Entrance") || t.contains("Entry To Critter Safari")) return Where.ENTRANCE;
		}
		if (SkyblockLocation.area() == null && biomeAt(mc) != null) return Where.INSIDE;
		if (Galatea.inTorrhus() && mc.player.position().distanceToSqr(IslandGuide.Place.SAFARI_MANAGER.pos) < 40 * 40) return Where.ENTRANCE;
		return Where.ELSEWHERE;
	}

	/** The tab list's "Area:" name: the entrance also has "Safari" in it. */
	private static Where classify(String area) {
		if (area == null || !area.contains("Safari")) return Where.ELSEWHERE;
		return area.contains("Entrance") || area.contains("Entry") ? Where.ENTRANCE : Where.INSIDE;
	}

	/** "Forest Biome" on the sidebar. */
	private static Biome sidebarBiome(String line) {
		if (!line.endsWith(" Biome")) return null;
		return Biome.named(line.substring(0, line.length() - " Biome".length()).trim());
	}

	/** Hypixel's own biomes in the Safari world: hypixel:forest, cavern, haunted, icy and icy_caves. */
	private static Biome biomeAt(Minecraft mc) {
		return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> {
			Identifier id = k.identifier();
			if (!id.getNamespace().equals("hypixel")) return null;
			return switch (id.getPath()) {
				case "forest" -> Biome.FOREST;
				case "cavern" -> Biome.CAVERN;
				case "haunted" -> Biome.HAUNTED;
				case "icy", "icy_caves" -> Biome.ICY;
				default -> null;
			};
		}).orElse(null);
	}

	private static Biome findBiome(Minecraft mc) {
		if (testBiome != null) return testBiome;
		Biome b = biomeAt(mc);
		if (b != null) return b;
		for (String line : GalateaRecorder.sidebarLines()) {
			b = sidebarBiome(line.trim());
			if (b != null) return b;
		}
		return null;
	}

	// ---- every tick

	public static void tick(Minecraft mc) {
		SafariConfig cfg = SafariConfig.get();
		if (!cfg.enabled || mc.level == null || mc.player == null) {
			where = Where.ELSEWHERE;
			sightings = List.of();
			GLOW.clear();
			return;
		}
		SafariSpots.BEDS.tick(mc, where == Where.INSIDE && cfg.showWay);
		if (++ticks % 5 != 0) return;
		where = locate(mc);
		if (where == Where.ENTRANCE) {
			biome = null;
			sightings = List.of();
			GLOW.clear();
			if (ticks % 20 == 0) readInventory(mc, false);
			if (cfg.tips && !toldEntrance) {
				toldEntrance = true;
				TazChat.info("This is the way into the Critter Safari. You need Hunting XV and a Safari Ticket (win them in Miria's "
						+ "Starlyn Contests). Give your ticket to the Safari Manager to go in: First-Class tickets give the most capsules. "
						+ "Go with 3-4 players and take one biome each.");
			}
			return;
		}
		if (where != Where.INSIDE) {
			biome = null;
			sightings = List.of();
			GLOW.clear();
			SafariWay.clear();
			return;
		}
		startRunIfNew(mc);
		biome = findBiome(mc);
		scan(mc, cfg);
		if (ticks % 20 == 0) readInventory(mc, true);
		if (cfg.tips) tips(cfg);
		SafariFinds.tick(mc, cfg);
		SafariWay.tick(mc, cfg);
	}

	private static void startRunIfNew(Minecraft mc) {
		if (mc.level != runLevel) reset();
	}

	/** Forgets the run (a new Safari instance; tests). */
	public static void reset() {
		runLevel = Minecraft.getInstance().level;
		runStart = System.currentTimeMillis();
		CAUGHT_RUN.clear();
		BIOMES_TOLD.clear();
		SPARKLING_SEEN.clear();
		MISSED.clear();
		SafariFinds.reset();
		SafariWay.reset();
		SafariSpots.BEDS.reset();
		hotspot = null;
		welcomed = toldTen = toldTwo = toldLow = toldOut = toldScrappy = false;
		capsules = -1;
		hadCapsules = false;
		quest = new LinkedHashMap<>();
	}

	private static void tips(SafariConfig cfg) {
		if (!welcomed) {
			welcomed = true;
			TazChat.info("Welcome to the Critter Safari! Right-click a Critter Capsule to throw it at a critter. Rarer ones can escape, "
					+ "so keep throwing: a Rare one takes up to 3 throws, a Legendary one up to 20.");
			TazChat.info("The critters you still need glow red with their name, and the Safari HUD shows what's left in your biome "
					+ "and how to find each one. String on the ground is a floor drop: pick it up for Safari Essence, Hunting XP, "
					+ "shards or quest items.");
			TazChat.info("Split the 4 biomes (Forest, Cavern, Haunted, Icy) with your party: everyone gets the shards from every catch. "
					+ "The Safari closes about an hour after it opens, and you can't come back in once you leave.");
		}
		if (biome != null && BIOMES_TOLD.add(biome)) TazChat.info(biome.label + " Biome: " + Critters.in(biome).size() + " critters. " + biome.tip);
		long left = timeLeftMs();
		if (!toldTen && left <= 10 * 60_000 && left > 2 * 60_000) {
			toldTen = true;
			TazChat.info("About 10 minutes until the Safari closes.");
		}
		if (!toldTwo && left <= 2 * 60_000) {
			toldTwo = toldTen = true;
			TazChat.info("About 2 minutes until the Safari closes!");
		}
	}

	/** Looks over the name tags now and then: which critters are around, and their bodies. */
	private static void scan(Minecraft mc, SafariConfig cfg) {
		List<Sighting> found = new ArrayList<>();
		List<Vec3> capturing = new ArrayList<>();
		Map<Entity, String> labels = new LinkedHashMap<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			String name = labelName(mc, e);
			if (name == null) continue;
			if (name.equals("CAPTURING")) capturing.add(e.position());
			else labels.put(e, name);
		}
		for (Map.Entry<Entity, String> l : labels.entrySet()) {
			Entity label = l.getKey();
			String name = l.getValue();
			boolean sparkling = false;
			Critter c = Critters.named(name);
			if (c == null && name.toUpperCase(Locale.ROOT).contains("SPARKLING")) {
				c = Critters.named(name.replaceAll("(?i)sparkling", "").replaceAll("[^A-Za-z ]", "").trim());
				sparkling = c != null;
			}
			if (c == null) {
				// Says in the game log how a critter-like name tag Taz couldn't read really looks, so it can be fixed.
				if (Critters.findIn(name) != null && MISSED.add(name)) {
					Taz.LOGGER.info("Safari: name tag not matched: \"{}\" ({})", escaped(label.getCustomName() == null ? name
							: label.getCustomName().getString()), label.getType().toShortString());
				}
				continue;
			}
			// A capture puts a stand with the critter's name next to one called CAPTURING: that one's already caught.
			if (capturing.stream().anyMatch(p -> p.distanceToSqr(label.position()) < 2.5 * 2.5)) continue;
			found.add(new Sighting(c, label, body(mc, label), sparkling));
		}
		sightings = found;
		GLOW.clear();
		for (Sighting s : found) {
			if (!needed(s, cfg)) continue;
			if (s.body() != null) GLOW.put(s.body().getId(), s.sparkling() ? GOLD : RED);
			if (s.sparkling() && cfg.alerts && SPARKLING_SEEN.add(s.label().getId())) {
				Alert.show("§6§lSPARKLING!", "§fA Sparkling " + s.critter().name() + " §7- 10x shards and a Rainbow Feather", cfg.sound);
			}
		}
	}

	/** A name tag's text, or null. Hypixel's NPC players (version 2 ids) count; real players don't. */
	private static String labelName(Minecraft mc, Entity e) {
		if (e.hasCustomName()) return cleanName(e.getCustomName().getString());
		if (e instanceof Player p && p != mc.player && p.getUUID().version() == 2) return cleanName(p.getName().getString());
		return null;
	}

	/**
	 * A name tag's text with nothing but the name: no colours, none of the icon glyphs Hypixel's resource pack draws
	 * (private-use characters, as critterMod strips them) and no symbols round it.
	 */
	public static String cleanName(String raw) {
		String text = TextUtil.cleanChat(raw).replaceAll("[\\p{Cf}\\p{Co}]", "");
		return text.replaceAll("^[^A-Za-z]+|[^A-Za-z]+$", "");
	}

	/** "Foxtrot" for the log. */
	private static String escaped(String text) {
		StringBuilder sb = new StringBuilder();
		for (char ch : text.toCharArray()) {
			if (ch >= 32 && ch < 127) sb.append(ch);
			else sb.append(String.format("\\u%04X", (int) ch));
		}
		return sb.toString();
	}

	/**
	 * The critter under a name tag. As in critterMod: the nearest mob within 3 blocks of the tag. Where there's none, a
	 * prop that is the critter (one of Hypixel's NPC players, an armour stand wearing it, or a display entity) right
	 * under the tag: props stand close together, so one a block to the side is another critter's.
	 */
	private static Entity body(Minecraft mc, Entity label) {
		if (label instanceof LivingEntity && !(label instanceof ArmorStand)) return label;
		Vec3 p = label.position();
		Entity mob = null, prop = null;
		double mobDist = BODY_RANGE * BODY_RANGE, propScore = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(label, new AABB(p, p).inflate(BODY_RANGE))) {
			Vec3 q = e.position();
			if (isMob(mc, e)) {
				double d = q.distanceToSqr(p);
				if (d < mobDist) {
					mob = e;
					mobDist = d;
				}
			} else if (isProp(mc, e)) {
				double side = (q.x - p.x) * (q.x - p.x) + (q.z - p.z) * (q.z - p.z), up = q.y - p.y;
				if (side > 1.0 || up > 0.5 || up < -BODY_RANGE) continue;
				double score = side + up * up * 0.1;
				if (score < propScore) {
					prop = e;
					propScore = score;
				}
			}
		}
		return mob != null ? mob : prop;
	}

	/** A real mob (critterMod's "mob-like": not one of the armour stands, displays and players Hypixel builds props from). */
	private static boolean isMob(Minecraft mc, Entity e) {
		return e instanceof LivingEntity && !(e instanceof ArmorStand) && !(e instanceof Player);
	}

	/** A prop that can be a critter's body. */
	private static boolean isProp(Minecraft mc, Entity e) {
		if (e == mc.player) return false;
		if (e instanceof Player p) return p.getUUID().version() == 2;
		if (e instanceof ArmorStand stand) {
			return !stand.hasCustomName() && !(stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && stand.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty());
		}
		if (e instanceof Display.ItemDisplay display) {
			ItemStack stack = display.getSlot(0).get();
			// A thrown capsule is an item display too.
			return !stack.isEmpty() && !stack.getHoverName().getString().contains("Capsule");
		}
		return e instanceof Display.BlockDisplay;
	}

	/** The box drawn round a critter: its body's, or a block-sized one for Hypixel's props, which have no size. */
	public static AABB box(Sighting s) {
		Entity body = s.body();
		if (body != null) {
			AABB b = body.getBoundingBox();
			if (b.getXsize() > 0.1 && b.getYsize() > 0.1) return b.inflate(0.05);
			Vec3 p = body.position();
			// A display entity draws its item round its position; anything else stands on it.
			double bottom = body instanceof Display ? p.y - 0.45 : p.y;
			return new AABB(p.x - 0.45, bottom, p.z - 0.45, p.x + 0.45, bottom + 0.9, p.z + 0.45);
		}
		Vec3 p = s.label().position();
		return new AABB(p.x - 0.4, p.y - 1.1, p.z - 0.4, p.x + 0.4, p.y - 0.1, p.z + 0.4);
	}

	/** Whether a critter is one to highlight. */
	public static boolean needed(Sighting s, SafariConfig cfg) {
		if (s.sparkling()) return true;
		return switch (cfg.highlight) {
			case RUN -> !CAUGHT_RUN.contains(s.critter());
			case MINE -> !cfg.caught.contains(s.critter().name());
			case ALL -> true;
		};
	}

	/** Whether a critter still counts as to do in the guide. */
	public static boolean toDo(Critter c) {
		SafariConfig cfg = SafariConfig.get();
		return cfg.highlight == SafariConfig.Highlight.MINE ? !cfg.caught.contains(c.name()) : !CAUGHT_RUN.contains(c);
	}

	public static int left(Biome b) {
		return (int) Critters.in(b).stream().filter(SafariHelper::toDo).count();
	}

	public static int done() {
		return (int) Critters.ALL.stream().filter(c -> !toDo(c)).count();
	}

	/** The glow colour (RGB) for an entity, or -1. Called for every entity every frame, so kept cheap. */
	public static int glow(Entity e) {
		if (where != Where.INSIDE || GLOW.isEmpty()) return -1;
		SafariConfig cfg = SafariConfig.get();
		if (!cfg.enabled || !cfg.glow) return -1;
		Integer c = GLOW.get(e.getId());
		return c == null ? -1 : c;
	}

	// ---- inventory: capsules, quest items, tickets

	private static void readInventory(Minecraft mc, boolean inside) {
		SafariConfig cfg = SafariConfig.get();
		Inventory inv = mc.player.getInventory();
		int caps = 0;
		Map<String, Integer> now = new LinkedHashMap<>();
		String bestTicket = null;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String id = Items.id(stack);
			if (id.endsWith("CRITTER_CAPSULE")) caps += stack.getCount();
			String name = TextUtil.cleanChat(stack.getHoverName().getString());
			QuestItem q = Critters.quest(name);
			if (q != null) now.merge(q.name(), stack.getCount(), Integer::sum);
			if (name.contains("Safari Ticket")) bestTicket = betterTicket(bestTicket, name);
		}
		ticket = bestTicket;
		if (!inside) return;
		if (cfg.tips) {
			for (Map.Entry<String, Integer> e : now.entrySet()) {
				if (e.getValue() > quest.getOrDefault(e.getKey(), 0)) {
					QuestItem q = Critters.quest(e.getKey());
					TazChat.info("You have a " + q.name() + ": " + q.use() + ".");
				}
			}
			if (caps > 0 && caps <= LOW_CAPSULES && !toldLow) {
				toldLow = true;
				TazChat.info("Only " + caps + " Critter Capsule" + (caps == 1 ? "" : "s") + " left: save them for critters you still need.");
			}
			if (caps == 0 && capsules > 0 && !toldOut) {
				toldOut = true;
				TazChat.info("You're out of Critter Capsules. You can leave, or stay and help your party find critters.");
			}
		}
		quest = now;
		capsules = caps;
		hadCapsules |= caps > 0;
	}

	private static final List<String> TICKETS = List.of("Basic", "Economy", "Premium", "First-Class");

	private static String betterTicket(String a, String b) {
		if (a == null) return b;
		return tier(b) > tier(a) ? b : a;
	}

	private static int tier(String ticket) {
		for (int i = TICKETS.size() - 1; i >= 0; i--) if (ticket.contains(TICKETS.get(i))) return i;
		return -1;
	}

	// ---- chat

	public static void onChat(String line) {
		SafariConfig cfg = SafariConfig.get();
		if (!cfg.enabled) return;
		Minecraft mc = Minecraft.getInstance();
		Matcher m;
		if (line.startsWith("CAPTURE! ")) {
			Critter c = Critters.findIn(line);
			if (c != null) caught(mc, c, true);
		} else if (line.startsWith("LOOT SHARE! ") && LOOT_SHARE.matcher(line).matches()) {
			Critter c = Critters.findIn(line.substring(line.lastIndexOf(" from ")));
			if (c != null) caught(mc, c, false);
		} else if ((m = HOTSPOT.matcher(line)).matches()) {
			hotspot = Biome.named(m.group(1));
			if (hotspot != null && cfg.tips) TazChat.info("Your Hunting Hotspot is the " + hotspot.label + " Biome. If your party splits up, "
					+ "take that one.");
		} else if ((m = BIRDFEEDER.matcher(line)).matches()) {
			Critter bird = Critters.named(m.group(1).trim());
			if (bird != null && bird.name().equals("Macaw") && toDo(bird) && cfg.alerts) {
				Alert.show("§6§lMACAW!", "§fAt the Birdfeeder in the Forest", cfg.sound);
			}
		} else if (line.startsWith("Your Critter Capsule can't get through Scrappy's armor")) {
			if (cfg.tips && !toldScrappy) {
				toldScrappy = true;
				TazChat.info("A Scrappy has to be fed before a capsule works: give it 3 Wholesale Wheat, Flavor-packed Fish or Lush Lily "
						+ "Pads (punch the fish, break the lily pads, around -111, 81, 36 in the Cavern), then throw.");
			}
		} else if (line.startsWith("A rumbling sound can be heard")) {
			if (cfg.alerts) Alert.show("§c§lGEMZIES!", "§f3 Gemzies are flying in the Cavern gem room", cfg.sound);
		} else if (line.startsWith("You hear the sound of massive footsteps")) {
			SafariWay.onWumpaWaking();
			if (cfg.alerts) Alert.show("§c§lWUMPA!", "§fIt wakes up in about 30 seconds", cfg.sound);
			if (cfg.tips) TazChat.info("The Wumpa is waking up in the Polaris cave. Lure it into the obsidian pillar to stun it, then throw "
					+ "capsules at it.");
		} else if (line.startsWith("Your ritual summoned a Doomspiral")) {
			if (cfg.alerts) Alert.show("§c§lDOOMSPIRAL!", "§fThrow capsules at it", cfg.sound);
			if (cfg.tips) TazChat.info("The Doomspiral is up! Throw capsules at it, and break any sculk sensors that appear.");
		}
	}

	private static void caught(Minecraft mc, Critter c, boolean mine) {
		SafariConfig cfg = SafariConfig.get();
		if (mc.level != null && mc.level != runLevel) reset();
		boolean newThisRun = CAUGHT_RUN.add(c);
		boolean first = mine && !cfg.caught.contains(c.name());
		if (first) {
			cfg.caught.add(c.name());
			Taz.saveIfChanged();
		}
		if (!cfg.tips) return;
		if (first) TazChat.info("Your first " + c.name() + "! (" + cfg.caught.size() + "/" + Critters.ALL.size() + " critters caught so far)");
		// What the guide counts: this run's catches, or in "never caught by me" mode only your own first ones.
		if (cfg.highlight == SafariConfig.Highlight.MINE ? !first : !newThisRun) return;
		if (done() == Critters.ALL.size()) {
			TazChat.info("Every critter is caught! Use your leftover capsules on Sparkling ones, or leave when you're done.");
			return;
		}
		int left = left(c.biome());
		if (left == 0) {
			TazChat.info(c.biome().label + " Biome is done! " + nextStep());
		} else if (c.biome() == Biome.FOREST && left == 1 && toDo(Critters.named("Macaw"))) {
			TazChat.info("The Forest is done except the Macaw: it only comes to the Birdfeeder (best with a Bag of Seeds), and not "
					+ "every run.");
		}
	}

	/** "Next: the Cavern Biome (4 left)." */
	public static String nextStep() {
		Biome best = nextBiome();
		if (best == null) return "Nothing left to catch!";
		return "Next: the " + best.label + " Biome (" + left(best) + " left).";
	}

	/** Where to go when your biome is done: your hotspot, else the biome with the most left. Null when all are done. */
	public static Biome nextBiome() {
		Biome best = hotspot != null && left(hotspot) > 0 ? hotspot : null;
		if (best == null) {
			for (Biome b : Biome.values()) if (left(b) > 0 && (best == null || left(b) > left(best))) best = b;
		}
		return best;
	}

	public static void forgetCaught() {
		SafariConfig.get().caught.clear();
		Taz.save();
		TazChat.info("Every critter counts as never caught again.");
	}

	// ---- what the guide HUD reads

	public static List<Sighting> sightings() {
		return sightings;
	}

	public static Biome hotspot() {
		return hotspot;
	}

	/** Critter Capsules in your inventory, or -1 before Taz has looked. */
	public static int capsules() {
		return capsules;
	}

	/** Whether Taz has seen any capsules this run. */
	public static boolean hadCapsules() {
		return hadCapsules;
	}

	public static Map<String, Integer> questItems() {
		return quest;
	}

	/** Your best Safari Ticket ("First-Class Safari Ticket"), or null. */
	public static String ticket() {
		return ticket;
	}

	public static long timeLeftMs() {
		return Math.max(0, RUN_MS - (System.currentTimeMillis() - runStart));
	}

	// ---- in the world

	/** LevelRenderEvents.END_MAIN: the critters' boxes and name tags, floor drops, mounds and the way to go. */
	public static void render(LevelRenderContext context) {
		SafariConfig cfg = SafariConfig.get();
		if (!cfg.enabled || where == Where.ELSEWHERE) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (where == Where.ENTRANCE) {
				if (cfg.showWay) beam(batch, IslandGuide.Place.SAFARI_MANAGER.pos, "Safari Manager: give it your ticket");
				return;
			}
			for (Sighting s : sightings) {
				if (!needed(s, cfg) || s.label().isRemoved()) continue;
				int color = s.sparkling() ? 0xFF000000 | GOLD : TazTheme.RED;
				if (cfg.boxes) {
					AABB box = box(s);
					batch.filled(box, (color & 0xFFFFFF) | 0x30000000, true);
					batch.outline(box, color, 2.0f, true);
				}
				if (cfg.nameTags) {
					Vec3 at = s.label().position().add(0, 0.6, 0);
					int dist = (int) Math.round(Math.sqrt(at.distanceToSqr(batch.camera)));
					boolean fresh = !cfg.caught.contains(s.critter().name());
					String text = (s.sparkling() ? "Sparkling " : "") + s.critter().name() + (fresh ? " ✦ new" : "") + "  " + dist + "m";
					batch.label(at, text, color, true);
				}
			}
			SafariFinds.render(batch, cfg);
			SafariWay.render(batch);
		} catch (Exception e) {
			Taz.LOGGER.error("Safari helper render failed", e);
		}
	}

	private static void beam(WorldRenderer.Batch batch, Vec3 pos, String label) {
		double dist = Math.sqrt(pos.distanceToSqr(batch.camera));
		if (dist < 4) return;
		int color = TazTheme.RED;
		batch.line(pos.add(0, -1, 0), pos.add(0, 60, 0), (color & 0xFFFFFF) | 0xA0000000, 4.0f, true);
		batch.line(WorldRenderer.crosshair(batch.camera), pos.add(0, 1, 0), (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
		batch.label(pos.add(0, 2.2, 0), label + "  " + Math.round(dist) + "m", color, true);
	}
}
