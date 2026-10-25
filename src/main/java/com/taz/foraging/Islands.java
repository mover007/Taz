package com.taz.foraging;

import com.taz.xpassist.npc.SkyblockLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Island ids, the same ones Skyblocker uses for waypoint groups ("foraging_3" = Torrhus Canyon), so routes keep their
 * island going between the two mods. Worked out from the tab list's "Area:" line.
 */
public final class Islands {
	public static final String UNKNOWN = "unknown";
	private static final Map<String, String> BY_AREA = new HashMap<>();
	private static final Map<String, String> NAMES = new HashMap<>();

	static {
		add("dynamic", "Private Island");
		add("garden", "Garden");
		add("hub", "Hub");
		add("farming_1", "The Farming Islands");
		add("foraging_1", "The Park");
		add("combat_1", "Spider's Den");
		add("combat_3", "The End");
		add("crimson_isle", "Crimson Isle");
		add("mining_1", "Gold Mine");
		add("mining_2", "Deep Caverns");
		add("mining_3", "Dwarven Mines");
		add("fishing_1", "Backwater Bayou");
		add("dungeon_hub", "Dungeon Hub");
		add("winter", "Jerry's Workshop");
		add("rift", "The Rift");
		add("dark_auction", "Dark Auction");
		add("crystal_hollows", "Crystal Hollows");
		add("kuudra", "Kuudra's Hollow");
		add("mineshaft", "Glacite Mineshafts");
		add("foraging_2", "Moonglade Marsh");
		add("foraging_3", "Torrhus Canyon");
		add("lotus_atoll", "Lotus Atoll");
		BY_AREA.put("Galatea", "foraging_2");
	}

	private Islands() {
	}

	private static void add(String id, String name) {
		BY_AREA.put(name, id);
		NAMES.put(id, name);
	}

	/** "Torrhus Canyon" for "foraging_3"; the id itself for one Taz doesn't know. */
	public static String name(String id) {
		return NAMES.getOrDefault(id, id);
	}

	/** The island you're on, or "unknown". */
	public static String current() {
		String area = SkyblockLocation.area();
		if (area != null && TreeTracker.isForagingIsland(area)) return Galatea.inTorrhus() && !Galatea.ANYWHERE ? "foraging_3" : "foraging_2";
		if (area == null) return UNKNOWN;
		if (area.startsWith("The Catacombs")) return "dungeon";
		return BY_AREA.getOrDefault(area, UNKNOWN);
	}

	/** Whether something for {@code island} belongs where you are now. */
	public static boolean here(String island) {
		if (Galatea.ANYWHERE || island == null || UNKNOWN.equals(island)) return true;
		return island.equals(current());
	}

	/** For a route with no island: Galatea's two islands can be told apart by where the points are. */
	public static String guess(int x, int z) {
		if (x >= -800 && x <= -480 && z >= -120 && z <= 320) return z > 100 ? "foraging_3" : "foraging_2";
		return UNKNOWN;
	}
}
