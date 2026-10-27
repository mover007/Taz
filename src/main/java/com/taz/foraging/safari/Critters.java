package com.taz.foraging.safari;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 37 Critter Safari critters by biome, with where to find each one, and the quest items from floor drops with what
 * they're for. Names and rarities as Hypixel writes them (checked against critterMod by MrCloudy2, MIT, built from real
 * catch logs); where they live from the Hypixel SkyBlock wiki's critter table and the forum guide.
 */
public final class Critters {
	public enum Biome {
		FOREST("Forest", "Feed the Birdfeeder (-1, 90, 44) a Yogi Berry, Wriggleworm or Bag of Seeds from floor drops for Bluebirds, "
				+ "Parakeets and Macaws, and punch beehives for Honeybugs."),
		CAVERN("Cavern", "It's the slowest biome. Punch the small rock mounds for Rockmites, break the cracked walls near the TNT for "
				+ "Snoozles, feed Scrappies Wheat, Fish or Lily Pads, and put the Purple, Lime and Orange Gems on their pedestals for Gemzies."),
		HAUNTED("Haunted", "Try to sleep in the bed for a Gazer, throw a Shining Coin into the basement water for a Gimmiegold, look "
				+ "behind paintings for Hideonwalls, and light the 4 ritual candles with Soothing Incense for the Doomspiral."),
		ICY("Icy", "It's freezing: warm up by the campfires and stay out of the water (at -100 Cold you faint and lose items). Break ice "
				+ "walls with an Icebreaker for Troodons; catch every other Icy critter and the Wumpa wakes up.");

		public final String label;
		/** Said once a run, the first time you walk in. */
		public final String tip;

		Biome(String label, String tip) {
			this.label = label;
			this.tip = tip;
		}

		/** "Icy" -> ICY, or null. */
		public static Biome named(String name) {
			for (Biome b : values()) if (b.label.equalsIgnoreCase(name)) return b;
			return null;
		}
	}

	/** How likely a capsule catches it, and Hypixel's most throws it can take (its hard pity). */
	public enum Rarity {
		COMMON("Common", 1), UNCOMMON("Uncommon", 2), RARE("Rare", 3), EPIC("Epic", 5), LEGENDARY("Legendary", 20);

		public final String label;
		public final int maxThrows;

		Rarity(String label, int maxThrows) {
			this.label = label;
			this.maxThrows = maxThrows;
		}
	}

	public record Critter(String name, Biome biome, Rarity rarity, String hint) {
	}

	/** A floor-drop item, what to do with it, and where (null when the spot isn't a fixed one). */
	public record QuestItem(String name, String use, Vec3 place, String placeName) {
	}

	private static final Vec3 BIRDFEEDER = new Vec3(-0.5, 90, 44.5);
	private static final Vec3 GEM_PEDESTALS = new Vec3(-139.5, 61, 50.5);
	private static final Vec3 RITUAL_ROOM = new Vec3(47.5, 68, -18.5);
	private static final String SCRAPPY = "feed it to a Scrappy in the Cavern (3 to catch one)";

	public static final List<Critter> ALL = List.of(
			new Critter("Foxtrot", Biome.FOREST, Rarity.COMMON, "runs around the forest floor"),
			new Critter("Bluebird", Biome.FOREST, Rarity.UNCOMMON, "feed the Birdfeeder a Yogi Berry"),
			new Critter("Honeybug", Biome.FOREST, Rarity.UNCOMMON, "punch a beehive"),
			new Critter("Treefrog", Biome.FOREST, Rarity.UNCOMMON, "hops away; throw from far or above"),
			new Critter("Woodchucker", Biome.FOREST, Rarity.UNCOMMON, "on treetops, throws wood at you"),
			new Critter("Fluffling", Biome.FOREST, Rarity.RARE, "underground, sleeping upside down"),
			new Critter("Hideonfloor", Biome.FOREST, Rarity.RARE, "blends into the floor; runs when hit"),
			new Critter("Parakeet", Biome.FOREST, Rarity.RARE, "feed the Birdfeeder a Wriggleworm"),
			new Critter("Macaw", Biome.FOREST, Rarity.LEGENDARY, "feed the Birdfeeder a Bag of Seeds"),

			new Critter("Cavernfish", Biome.CAVERN, Rarity.COMMON, "swims in the underwater pond"),
			new Critter("Flitter", Biome.CAVERN, Rarity.COMMON, "flies around the caves"),
			new Critter("Shyworm", Biome.CAVERN, Rarity.COMMON, "pops out of the ground in the big cave"),
			new Critter("Driftling", Biome.CAVERN, Rarity.UNCOMMON, "drifts along a path on the wind"),
			new Critter("Chuckwalla", Biome.CAVERN, Rarity.RARE, "hit it, then crawl after it into its hole"),
			new Critter("Rockmite", Biome.CAVERN, Rarity.RARE, "punch the small rock mounds"),
			new Critter("Scrappy", Biome.CAVERN, Rarity.RARE, "feed it 3 Wheat, Fish or Lily Pads"),
			new Critter("Snoozle", Biome.CAVERN, Rarity.RARE, "break the cracked walls near the TNT"),
			new Critter("Gemzie", Biome.CAVERN, Rarity.EPIC, "put the 3 gems on their pedestals"),

			new Critter("Areita", Biome.HAUNTED, Rarity.UNCOMMON, "in the mansion attic"),
			new Critter("Bloodbat", Biome.HAUNTED, Rarity.UNCOMMON, "sleeps on ceilings; 1 throw wakes it"),
			new Critter("Duplico", Biome.HAUNTED, Rarity.UNCOMMON, "looks like an out-of-place block"),
			new Critter("Gazer", Biome.HAUNTED, Rarity.UNCOMMON, "try to sleep in the bed"),
			new Critter("Litterbug", Biome.HAUNTED, Rarity.UNCOMMON, "scurries in the kitchen and attic"),
			new Critter("Solsnatcher", Biome.HAUNTED, Rarity.UNCOMMON, "flies high up in the sky"),
			new Critter("Gimmiegold", Biome.HAUNTED, Rarity.RARE, "throw a Shining Coin in the basement water"),
			new Critter("Hideonwall", Biome.HAUNTED, Rarity.RARE, "on walls behind paintings"),
			new Critter("Hideyho", Biome.HAUNTED, Rarity.RARE, "hide and seek: find him, no capsule"),
			new Critter("Doomspiral", Biome.HAUNTED, Rarity.LEGENDARY, "light 4 ritual candles with Soothing Incense"),

			new Critter("Strongarm", Biome.ICY, Rarity.COMMON, "on the hills"),
			new Critter("Tepid", Biome.ICY, Rarity.COMMON, "in the icy lakes"),
			new Critter("Polaris", Biome.ICY, Rarity.UNCOMMON, "big cave by the entrance; it charges"),
			new Critter("Shuddersquid", Biome.ICY, Rarity.UNCOMMON, "hidden pool behind the high waterfall"),
			new Critter("Billygoat", Biome.ICY, Rarity.RARE, "on top of the high mountains"),
			new Critter("Mantis Shrimp", Biome.ICY, Rarity.RARE, "at the bottom of the icy lakes"),
			new Critter("Nozzlenose", Biome.ICY, Rarity.RARE, "deep in the lakes; hides in tunnels"),
			new Critter("Troodon", Biome.ICY, Rarity.RARE, "break ice walls with an Icebreaker"),
			new Critter("Wumpa", Biome.ICY, Rarity.LEGENDARY, "catch every other Icy critter first"));

	public static final List<QuestItem> QUEST_ITEMS = List.of(
			new QuestItem("Yogi Berry", "feed it to the Birdfeeder in the Forest (most often a Bluebird)", BIRDFEEDER, "Birdfeeder"),
			new QuestItem("Wriggleworm", "feed it to the Birdfeeder in the Forest (often a Parakeet)", BIRDFEEDER, "Birdfeeder"),
			new QuestItem("Bag of Seeds", "feed it to the Birdfeeder in the Forest (the best bait for Macaws)", BIRDFEEDER, "Birdfeeder"),
			new QuestItem("Soothing Incense", "light one of the 4 ritual candles under the Haunted biome (all 4 bring the Doomspiral)",
					RITUAL_ROOM, "Ritual room"),
			new QuestItem("Shining Coin", "throw it into the water in the Haunted mansion's basement for a Gimmiegold", null, null),
			new QuestItem("Icebreaker", "break an ice wall in the Icy biome to get to a Troodon", null, null),
			new QuestItem("Purple Gem", "put it on its pedestal in the Cavern (all 3 gems bring 3 Gemzies)", GEM_PEDESTALS, "Gem pedestals"),
			new QuestItem("Lime Gem", "put it on its pedestal in the Cavern (all 3 gems bring 3 Gemzies)", GEM_PEDESTALS, "Gem pedestals"),
			new QuestItem("Orange Gem", "put it on its pedestal in the Cavern (all 3 gems bring 3 Gemzies)", GEM_PEDESTALS, "Gem pedestals"),
			new QuestItem("Wholesale Wheat", SCRAPPY, null, null),
			new QuestItem("Flavor-packed Fish", SCRAPPY, null, null),
			new QuestItem("Lush Lily Pad", SCRAPPY, null, null));

	private static final Map<String, Critter> BY_NAME = new HashMap<>();
	/** Longest name first, so "Mantis Shrimp" is found before anything shorter inside it. */
	private static final List<Critter> LONGEST_FIRST = new ArrayList<>(ALL);

	static {
		for (Critter c : ALL) BY_NAME.put(c.name(), c);
		LONGEST_FIRST.sort(Comparator.comparingInt((Critter c) -> c.name().length()).reversed());
	}

	private Critters() {
	}

	/** The critter with exactly this name, or null. */
	public static Critter named(String name) {
		return name == null ? null : BY_NAME.get(name);
	}

	/** The critter a chat line names ("CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!"), or null. */
	public static Critter findIn(String line) {
		for (Critter c : LONGEST_FIRST) if (line.contains(c.name())) return c;
		return null;
	}

	public static List<Critter> in(Biome biome) {
		return ALL.stream().filter(c -> c.biome() == biome).toList();
	}

	/** The quest item an inventory item's name is ("Lush Lily Pads" counts as a Lush Lily Pad), or null. */
	public static QuestItem quest(String itemName) {
		for (QuestItem q : QUEST_ITEMS) if (itemName.startsWith(q.name())) return q;
		return null;
	}
}
