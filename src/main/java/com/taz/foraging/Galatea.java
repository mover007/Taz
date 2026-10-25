package com.taz.foraging;

import com.taz.xpassist.npc.SkyblockLocation;
import net.minecraft.client.Minecraft;

/**
 * Which Galatea island you're on. Both islands are in the same world: Moonglade Marsh is around z -90..70 and
 * Torrhus Canyon (reached by a launch pad) around z 130..300.
 */
public final class Galatea {
	/** Dev and test flag: -Dtaz.foraging.anywhere=true makes every foraging feature run off Galatea. */
	public static final boolean ANYWHERE = Boolean.getBoolean("taz.foraging.anywhere");
	private static final double TORRHUS_MIN_Z = 100;

	private Galatea() {
	}

	/** On Moonglade Marsh or Torrhus Canyon. */
	public static boolean onIslands() {
		return ANYWHERE || TreeTracker.isForagingIsland(SkyblockLocation.area());
	}

	public static boolean inTorrhus() {
		if (ANYWHERE) return true;
		String area = SkyblockLocation.area();
		if (area == null || !TreeTracker.isForagingIsland(area)) return false;
		if (area.contains("Torrhus")) return true;
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.player.getZ() > TORRHUS_MIN_Z;
	}

	public static boolean inMoonglade() {
		if (ANYWHERE) return true;
		String area = SkyblockLocation.area();
		if (area == null || !TreeTracker.isForagingIsland(area) || area.contains("Torrhus")) return false;
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || mc.player.getZ() <= TORRHUS_MIN_Z;
	}
}
