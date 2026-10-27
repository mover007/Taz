package com.taz.foraging.safari;

import com.taz.foraging.BlockFinder;
import com.taz.foraging.safari.Critters.Biome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where each critter is found, and what you need first. Bee nests, Hideonwall perches and the Snoozle and Troodon walls
 * are critterMod's (MrCloudy2, MIT, checked against its runs); the critters' own spots, Hideyho's hiding places, the
 * Ritual, Gem Podiums, Birdfeeder, mansion and floor-drop spots are SkyHanni's Safari map; the Cavernfish pond,
 * Scrappy's food, the Polaris cave and the Shuddersquid pool are from the forum guide. Beds (for Gazers) are found in
 * the world. Critters with no fixed spot roam their biome, so the way searches it.
 */
public final class SafariSpots {
	/** What a critter takes: a spot to go to (or none: it roams), what to do there, and what you need first. */
	public record Task(String what, List<Vec3> spots, String needs) {
	}

	/** Quest items that let you do a task ("bait" is any of the three bird foods, "gem" any of the three gems). */
	public static final Map<String, List<String>> NEEDS = Map.of(
			"bait", List.of("Yogi Berry", "Wriggleworm", "Bag of Seeds"),
			"gem", List.of("Purple Gem", "Lime Gem", "Orange Gem"),
			"incense", List.of("Soothing Incense"),
			"coin", List.of("Shining Coin"),
			"icebreaker", List.of("Icebreaker"),
			"food", List.of("Wholesale Wheat", "Flavor-packed Fish", "Lush Lily Pad"));

	/** Critters that wander their biome: the way walks the biome to find them. */
	public static final Set<String> ROAMING = Set.of("Foxtrot", "Treefrog", "Woodchucker", "Fluffling", "Hideonfloor", "Cavernfish",
			"Driftling", "Scrappy", "Bloodbat", "Duplico", "Solsnatcher", "Strongarm", "Tepid", "Polaris", "Shuddersquid", "Billygoat",
			"Mantis Shrimp", "Nozzlenose");

	private static final int[][] BEE_NESTS = {{-17, 68, 41}, {-22, 68, 39}, {23, 70, 46}, {25, 70, 41}, {-9, 81, 58}, {-1, 81, 60},
			{15, 88, 31}, {20, 88, 33}, {-1, 84, 9}, {-13, 103, 21}, {-11, 103, 13}};
	private static final int[][] HIDEONWALL_PERCHES = {{18, 71, -57}, {-5, 71, -85}, {-24, 71, -76}, {-25, 71, -65}, {-25, 79, -57},
			{6, 79, -84}, {17, 80, -75}, {17, 79, -52}};
	public static final int[][] SNOOZLE_WALLS = {{-126, 39, 74}, {-114, 39, 87}, {-70, 39, 68}, {-96, 40, 17}, {-95, 40, 42}};
	public static final int[][] TROODON_WALLS = {{-104, 80, -95}, {-131, 78, -61}, {-109, 89, -27}};
	private static final Vec3 CAVERNFISH_POND = new Vec3(-75.5, 71, 68.5);
	/** The Cavern pond with the fish (the wiki), up the ladder by the Cavern entrance. */
	private static final Vec3 FISH_POND = new Vec3(-109.5, 81, 38.5);
	private static final Vec3 POLARIS_CAVE = new Vec3(-123.5, 81, -88.5);
	private static final Vec3 SHUDDERSQUID_POOL = new Vec3(-123.5, 92, -28.5);

	/** Lily pads and wheat in the loaded chunks: broken in the Cavern, they're Scrappy food. */
	public static final BlockFinder LILY_PADS = new BlockFinder(s -> s.is(Blocks.LILY_PAD));
	public static final BlockFinder WHEAT = new BlockFinder(s -> s.is(Blocks.WHEAT));
	/** The mansion's basement, under its ground floor: a Shining Coin thrown in its water brings a Gimmiegold. */
	private static final BlockPos BASEMENT_FROM = new BlockPos(-15, 55, -70), BASEMENT_TO = new BlockPos(26, 66, -44);
	/** The middle of the basement walkway on SkyHanni's map, until the water itself is found. */
	private static final Vec3 BASEMENT_WALKWAY = new Vec3(-1.5, 62, -50.5);
	private static Vec3 basementWater;
	private static ClientLevel basementLevel;
	private static long basementLooked;

	/** Test hook: lily pads and wheat count wherever they are. */
	public static boolean testFoodAnywhere;

	/** The heads of the beds in the loaded chunks (Gazers come when you try to sleep in one). */
	public static final BlockFinder BEDS = new BlockFinder(s -> s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.HEAD);

	/** Space between the search points the way walks a biome by: about what you can see critters from. */
	private static final double SEARCH_SPACING = 24;
	private static Map<Biome, List<Vec3>> searchPoints;

	private SafariSpots() {
	}

	/** What a critter takes, or null for one that only roams. The Wumpa's spot only counts once it's awake. */
	public static Task task(String critter) {
		return switch (critter) {
			case "Honeybug" -> new Task("punch the bee nest", blocks(BEE_NESTS), null);
			case "Hideonwall" -> new Task("look behind the painting", blocks(HIDEONWALL_PERCHES), null);
			case "Snoozle" -> new Task("break the cracked wall", blocks(SNOOZLE_WALLS), null);
			case "Troodon" -> new Task("break the ice wall with your Icebreaker", blocks(TROODON_WALLS), "icebreaker");
			case "Hideyho" -> new Task("look for Hideyho here", tagged("hideyho"), null);
			case "Areita", "Litterbug", "Flitter", "Shyworm", "Chuckwalla" -> new Task("where it lives", named(critter), null);
			case "Gazer" -> new Task("try to sleep in the bed", beds(), null);
			case "Rockmite" -> new Task("punch the mound", SafariFinds.mounds(), null);
			case "Cavernfish" -> new Task("the underwater pond", List.of(CAVERNFISH_POND), null);
			case "Polaris" -> new Task("the Polaris cave", List.of(POLARIS_CAVE), null);
			case "Shuddersquid" -> new Task("the hidden pool behind the waterfall", List.of(SHUDDERSQUID_POOL), null);
			case "Wumpa" -> new Task("it wakes in the Polaris cave", List.of(POLARIS_CAVE), null);
			case "Scrappy" -> new Task("feed it its food 4 times", List.of(), "food");
			case "Bluebird", "Parakeet", "Macaw" -> new Task("feed the Birdfeeder your bait", named("Birdfeeder"), "bait");
			case "Doomspiral" -> new Task("light a candle with your Soothing Incense", named("Ritual"), "incense");
			case "Gemzie" -> new Task("put your gem on its pedestal", named("Gem Podiums"), "gem");
			case "Gimmiegold" -> new Task("throw your Shining Coin in the basement water", basementWater(), "coin");
			default -> null;
		};
	}

	/** What to do when you've found one: most just take capsules; some want something first. */
	public static String howToCatch(String critter) {
		return switch (critter) {
			case "Scrappy" -> "feed it its food 4 times, then throw";
			case "Bloodbat" -> "1 throw wakes it, the next catches it";
			case "Duplico" -> "right-click it to make it run, then throw";
			case "Chuckwalla" -> "hit it, then crawl after it into its hole";
			case "Treefrog" -> "throw from far away or from above";
			case "Wumpa" -> "lure it into the obsidian pillar, then throw";
			case "Hideyho" -> "walk up to him: no capsule needed";
			default -> "catch it";
		};
	}

	/** Where a Scrappy food comes from: the fish pond, the Cavern's lily pads and wheat (the pond area if none are found). */
	public static List<Vec3> foodSources(String food) {
		BlockFinder blocks = switch (food) {
			case "Lush Lily Pad" -> LILY_PADS;
			case "Wholesale Wheat" -> WHEAT;
			default -> null;
		};
		if (blocks == null) return List.of(FISH_POND);
		List<Vec3> out = new ArrayList<>();
		for (BlockPos p : blocks.all()) {
			Vec3 v = Vec3.atBottomCenterOf(p);
			if (testFoodAnywhere || SafariMap.zoneAt(v) == Biome.CAVERN) out.add(v);
		}
		return out.isEmpty() ? List.of(FISH_POND) : out;
	}

	public static String howToHarvest(String food) {
		return switch (food) {
			case "Lush Lily Pad" -> "break the lily pads";
			case "Wholesale Wheat" -> "break the wheat";
			default -> "punch the fish in the pond";
		};
	}

	/** The points the way walks to search a biome for roaming critters, spread about {@value #SEARCH_SPACING} apart. */
	public static synchronized List<Vec3> searchPoints(Biome biome) {
		if (searchPoints == null) {
			searchPoints = new EnumMap<>(Biome.class);
			for (Biome b : Biome.values()) {
				List<Vec3> points = new ArrayList<>();
				for (SafariMap.Node n : SafariMap.nodes()) {
					if (SafariMap.zoneOf(n) != b) continue;
					if (points.stream().noneMatch(p -> p.distanceToSqr(n.pos()) < SEARCH_SPACING * SEARCH_SPACING)) points.add(n.pos());
				}
				searchPoints.put(b, List.copyOf(points));
			}
		}
		return searchPoints.get(biome);
	}

	/**
	 * Where to throw a Shining Coin: the middle of the water in the mansion's basement, looked for (every 2 seconds
	 * until found) once the basement is loaded; the basement walkway before that.
	 */
	public static List<Vec3> basementWater() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return List.of(BASEMENT_WALKWAY);
		if (mc.level != basementLevel) {
			basementLevel = mc.level;
			basementWater = null;
			basementLooked = 0;
		}
		long now = System.currentTimeMillis();
		if (basementWater == null && now - basementLooked > 2000) {
			basementLooked = now;
			basementWater = findWater(mc.level);
		}
		return List.of(basementWater != null ? basementWater : BASEMENT_WALKWAY);
	}

	private static Vec3 findWater(ClientLevel level) {
		if (!level.isLoaded(BASEMENT_FROM) || !level.isLoaded(BASEMENT_TO)
				|| !level.isLoaded(new BlockPos(BASEMENT_FROM.getX(), 60, BASEMENT_TO.getZ())) || !level.isLoaded(new BlockPos(BASEMENT_TO.getX(), 60, BASEMENT_FROM.getZ()))) {
			return null;
		}
		List<BlockPos> water = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(BASEMENT_FROM, BASEMENT_TO)) {
			if (level.getFluidState(p).is(FluidTags.WATER)) water.add(p.immutable());
		}
		if (water.isEmpty()) return null;
		double x = 0, y = 0, z = 0;
		for (BlockPos p : water) {
			x += p.getX();
			y += p.getY();
			z += p.getZ();
		}
		Vec3 middle = new Vec3(x / water.size() + 0.5, y / water.size() + 0.5, z / water.size() + 0.5);
		BlockPos best = water.get(0);
		for (BlockPos p : water) if (Vec3.atCenterOf(p).distanceToSqr(middle) < Vec3.atCenterOf(best).distanceToSqr(middle)) best = p;
		return Vec3.atCenterOf(best);
	}

	/** The map's floor-drop spots in a biome (where drops can be; each run has some of them). */
	public static List<Vec3> dropSpots(Biome biome) {
		return SafariMap.matching(n -> n.tagged("floor_drops") && SafariMap.zoneOf(n) == biome).stream().map(SafariMap.Node::pos).toList();
	}

	private static List<Vec3> beds() {
		List<Vec3> out = new ArrayList<>();
		for (BlockPos p : BEDS.all()) out.add(Vec3.atBottomCenterOf(p));
		return out.isEmpty() ? named("Luigis Mansion 64") : out;
	}

	private static List<Vec3> blocks(int[][] spots) {
		List<Vec3> out = new ArrayList<>();
		for (int[] s : spots) out.add(new Vec3(s[0] + 0.5, s[1], s[2] + 0.5));
		return out;
	}

	private static List<Vec3> named(String name) {
		return SafariMap.matching(n -> n.is(name)).stream().map(SafariMap.Node::pos).toList();
	}

	private static List<Vec3> tagged(String tag) {
		return SafariMap.matching(n -> n.tagged(tag)).stream().map(SafariMap.Node::pos).toList();
	}
}
