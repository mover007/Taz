package com.taz.fishing.crimson;

import com.taz.Taz;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

/**
 * Corrupted Bait is the only bait with a sound: a low Enderman scream when it's used. It can come with the cast or
 * later, when a fish bites, so Enderman sounds are muted while your bobber is out and just after any click with a rod
 * (SoundManagerMixin asks here).
 */
public final class CorruptedBait {
	/** How long after a click the scream can still arrive (Hypixel answers over your ping). */
	static final long WINDOW_MS = 3000;
	private static long castAt;
	/** Each sound heard while fishing, logged once, to see what plays if a scream gets through. */
	private static final Set<String> heard = new HashSet<>();

	private CorruptedBait() {
	}

	/**
	 * UseItemCallback: any right-click with a rod. A reel-in click counts too: when you reel in and cast again quickly,
	 * the old bobber is still there for a moment, so the cast looks like a reel-in.
	 */
	public static void onUse(Player player, ItemStack held) {
		if (held.is(Items.FISHING_ROD)) castAt = System.currentTimeMillis();
	}

	/** SoundManagerMixin: true to drop the sound. */
	public static boolean muted(SoundInstance sound) {
		TrophyFishConfig cfg = TrophyFishConfig.get();
		if (!cfg.enabled || !cfg.muteBait) return false;
		Player p = Minecraft.getInstance().player;
		boolean fishing = p != null && p.fishing != null || System.currentTimeMillis() - castAt < WINDOW_MS;
		if (!fishing) return false;
		String id = sound.getIdentifier().toString();
		if (heard.add(id)) Taz.LOGGER.info("Corrupted Bait: heard {} while fishing", id);
		return sound.getIdentifier().getPath().startsWith("entity.enderman.");
	}

	public static void resetForTest() {
		castAt = 0;
	}
}
