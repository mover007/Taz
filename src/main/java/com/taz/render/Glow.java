package com.taz.render;

import com.taz.dungeons.highlight.StarredMobs;
import net.minecraft.world.entity.Entity;

/**
 * Entities a feature wants outlined with the vanilla glow (drawn through walls), and in which colour: the starred mobs,
 * bonus bats and teammates in dungeons. The glow mixins ask here.
 */
public final class Glow {
	private Glow() {
	}

	/** The glow colour (RGB) for an entity, or -1 for none. */
	public static int color(Entity entity) {
		return StarredMobs.glow(entity);
	}
}
