package com.taz.foraging;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;

/**
 * Galatea's hunting mobs are vanilla mobs in disguise: Hideonleaf is a green shulker, Hideonsun a yellow / orange /
 * brown shulker, Shellwise a turtle, Coralot an axolotl and Blue Jay a blue parrot. They get the vanilla glowing
 * outline (MinecraftMixin, EntityMixin), which shows through walls.
 */
public final class HuntingMobs {
	public static final int HIDEONLEAF = 0x55FF55;
	public static final int HIDEONSUN = 0xFFDD33;
	public static final int SHELLWISE = 0x22CCAA;
	public static final int CORALOT = 0xFF77CC;
	public static final int BLUE_JAY = 0x5577FF;

	private HuntingMobs() {
	}

	/** The glow colour (RGB) for a hunting mob, or -1 for any other entity. */
	public static int glowColor(Entity e) {
		HuntingMobsConfig cfg = HuntingMobsConfig.get();
		if (!cfg.enabled || !Galatea.onIslands()) return -1;
		if (e instanceof Shulker s) {
			DyeColor c = s.getColor();
			if (c == DyeColor.GREEN && cfg.hideonleaf) return HIDEONLEAF;
			if ((c == DyeColor.YELLOW || c == DyeColor.ORANGE || c == DyeColor.BROWN) && cfg.hideonsun) return HIDEONSUN;
			return -1;
		}
		if (e instanceof Turtle && cfg.shellwise) return SHELLWISE;
		if (e instanceof Axolotl && cfg.coralot) return CORALOT;
		if (e instanceof Parrot p && p.getVariant() == Parrot.Variant.BLUE && cfg.blueJay) return BLUE_JAY;
		return -1;
	}

	/** SoundManagerMixin: true to drop the sound. */
	public static boolean muted(SoundInstance sound) {
		HuntingMobsConfig cfg = HuntingMobsConfig.get();
		return cfg.enabled && cfg.mutePhantoms && Galatea.onIslands() && sound.getIdentifier().getPath().startsWith("entity.phantom");
	}
}
