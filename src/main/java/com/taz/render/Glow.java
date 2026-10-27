package com.taz.render;

import com.taz.dungeons.highlight.StarredMobs;
import com.taz.foraging.safari.SafariHelper;
import net.minecraft.world.entity.Entity;

/**
 * Entities a feature wants outlined with the vanilla glow (drawn through walls), and in which colour: the starred mobs,
 * bonus bats and teammates in dungeons, and the critters you need in the Critter Safari. The glow mixins ask here.
 */
public final class Glow {
	private Glow() {
	}

	/** The glow colour (RGB) for an entity, or -1 for none. */
	public static int color(Entity entity) {
		int starred = StarredMobs.glow(entity);
		return starred >= 0 ? starred : SafariHelper.glow(entity);
	}
}
