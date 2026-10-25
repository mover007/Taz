package com.taz.xpassist.npc;

import java.util.List;

/**
 * A trackable NPC. Locations come from the NEU community repo, which records the island id
 * (Hypixel's server "mode", e.g. "hub", "combat_3") and the block the NPC stands on.
 */
public record Npc(String id, String name, String island, int x, int y, int z, List<String> lore, String source) {
	public boolean hasLocation() {
		return island != null && !island.isEmpty();
	}
}
