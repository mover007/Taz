package com.taz.render;

import com.taz.dungeons.puzzles.Puzzles;
import net.minecraft.world.entity.Entity;

/** Entities a feature doesn't want drawn: the blazes while the Blaze solver boxes them. EntityRenderDispatcherMixin asks here. */
public final class Hidden {
	private Hidden() {
	}

	public static boolean hide(Entity entity) {
		return Puzzles.hide(entity);
	}
}
