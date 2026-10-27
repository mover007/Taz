package com.taz.foraging.safari;

import com.taz.foraging.BlockFinder;
import com.taz.foraging.safari.Critters.Biome;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
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
	private static final Vec3 SCRAPPY_FOOD = new Vec3(-110.5, 81, 36.5);
	private static final Vec3 POLARIS_CAVE = new Vec3(-123.5, 81, -88.5);
	private static final Vec3 SHUDDERSQUID_POOL = new Vec3(-123.5, 92, -28.5);

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
			case "Scrappy" -> new Task("feed it Wheat, Fish or Lily Pads", List.of(), "food");
			case "Bluebird", "Parakeet", "Macaw" -> new Task("feed the Birdfeeder your bait", named("Birdfeeder"), "bait");
			case "Doomspiral" -> new Task("light a candle with your Soothing Incense", named("Ritual"), "incense");
			case "Gemzie" -> new Task("put your gem on its pedestal", named("Gem Podiums"), "gem");
			case "Gimmiegold" -> new Task("throw your Shining Coin in the basement water", named("Luigis Mansion 64"), "coin");
			default -> null;
		};
	}

	/** What to do when you've found one: most just take capsules; some want something first. */
	public static String howToCatch(String critter) {
		return switch (critter) {
			case "Scrappy" -> "feed it 3 Wheat, Fish or Lily Pads, then throw";
			case "Bloodbat" -> "1 throw wakes it, the next catches it";
			case "Duplico" -> "right-click it to make it run, then throw";
			case "Chuckwalla" -> "hit it, then crawl after it into its hole";
			case "Treefrog" -> "throw from far away or from above";
			case "Wumpa" -> "lure it into the obsidian pillar, then throw";
			case "Hideyho" -> "walk up to him: no capsule needed";
			default -> "catch it";
		};
	}

	/** Where Scrappy food grows (punch the fish, break the lily pads). */
	public static Vec3 scrappyFood() {
		return SCRAPPY_FOOD;
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
