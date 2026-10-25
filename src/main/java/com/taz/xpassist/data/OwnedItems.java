package com.taz.xpassist.data;

import java.util.Map;
import java.util.Set;

/**
 * What you are carrying right now: SkyBlock item ids, and pets (type -> rarity) held as items.
 * Upgrades you already have the item for become free ("just donate it").
 */
public record OwnedItems(Set<String> ids, Map<String, String> pets) {
	public static final OwnedItems EMPTY = new OwnedItems(Set.of(), Map.of());
}
