package com.taz.fishing.crimson;

import com.taz.fishing.FishingHelper;
import com.taz.general.PetDisplay;
import com.taz.util.Alert;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Slugfish only bite once your bobber has been in for 20 seconds, less with a Slug pet out (0.5% less per level: 10
 * seconds at level 100). A ding when a fish bites after that, so you know it can be a Slugfish; the time counts from
 * when the bobber lands in the lava, so a bite that's too early never dings.
 */
public final class Slugfish {
	private static FishingHook hook;
	private static long inLavaAt;
	private static boolean biting;
	/** When the last ding played (for tests). */
	public static volatile long lastDing;

	private Slugfish() {
	}

	/** Seconds the bobber has to be in: 20, less with your Slug pet out (taken as level 100 until Taz sees its level). */
	public static double seconds() {
		if (!"Slug".equals(PetDisplay.activeName())) return 20;
		int level = PetDisplay.activeLevel();
		return 20 * (1 - 0.005 * Math.min(100, level > 0 ? level : 100));
	}

	/** Every client tick: a ding when a fish bites (Hypixel's "!!!") after the bobber has been in the lava long enough. */
	public static void tick(Minecraft mc) {
		FishingHook h = mc.player == null ? null : mc.player.fishing;
		if (h != hook) {
			hook = h;
			inLavaAt = 0;
			biting = false;
		}
		TrophyFishConfig cfg = TrophyFishConfig.get();
		if (h == null || !cfg.enabled || !cfg.slugDing || !TrophyFish.here()) return;
		long now = System.currentTimeMillis();
		if (inLavaAt == 0) {
			BlockPos at = h.blockPosition();
			if (mc.level.getFluidState(at).is(FluidTags.LAVA) || mc.level.getFluidState(at.below()).is(FluidTags.LAVA)) inLavaAt = now;
			return;
		}
		boolean bite = FishingHelper.biting(mc, h);
		// A bite that started before the time is up isn't one.
		if (bite && !biting && now - inLavaAt >= seconds() * 1000) {
			lastDing = now;
			Alert.play(cfg.slugSound);
		}
		biting = bite;
	}
}
