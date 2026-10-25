package com.taz.general;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.util.Skulls;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

/**
 * Render Optimizer: stops drawing what you don't need to see: falling blocks, lightning, XP orbs, dying mobs (and their
 * name tags), explosion particles, the fire on your screen, and in dungeons the Archer passive's bone meal and the healer
 * fairies, Soul Weaver and tentacle heads some mobs carry. From Odin.
 */
public final class RenderOptimizer {
	private static final String FAIRY = "2eedcffc6a11a3834a28849cc316af7a2752a376d536cf84039cf79108b167ae";
	private static final String WEAVER = "2f24ed6875304fa4a1f0c785b2cb6a6a72563e9f3e24ea55e18178452119aa66";
	private static final String TENTACLE = "37d83674926b89714e6b5a55470501c04b066dd87bf6c335cddc6e60a1a1a5f5";

	/** Armor stand id -> whether its head or hand item is one to hide (worked out once; textures are slow to read). */
	private static final java.util.Map<Integer, Boolean> STANDS = new java.util.HashMap<>();
	private static net.minecraft.client.multiplayer.ClientLevel level;

	private RenderOptimizer() {
	}

	/** Whether to skip drawing this entity. */
	public static boolean hide(Entity e) {
		RenderOptimizerConfig cfg = RenderOptimizerConfig.get();
		if (!cfg.enabled) return false;
		EntityType<?> type = e.getType();
		if (cfg.fallingBlocks && type == EntityType.FALLING_BLOCK) return true;
		if (cfg.lightning && type == EntityType.LIGHTNING_BOLT) return true;
		if (cfg.xpOrbs && type == EntityType.EXPERIENCE_ORB) return true;
		if (cfg.dying && e instanceof LivingEntity living && !(e instanceof ArmorStand) && living.isDeadOrDying()) return true;
		if (cfg.dying && cfg.dyingTags && e instanceof ArmorStand && e.level().getEntity(e.getId() - 1) instanceof LivingEntity living && living.isDeadOrDying()) return true;
		if (!DungeonLocation.inDungeon()) return false;
		if (cfg.archerBoneMeal && e instanceof ItemEntity item && item.getItem().is(Items.BONE_MEAL)) return true;
		if (!(e instanceof ArmorStand stand)) return false;
		if (stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && stand.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
		if (stand.level() != level) {
			level = (net.minecraft.client.multiplayer.ClientLevel) stand.level();
			STANDS.clear();
		}
		return STANDS.computeIfAbsent(stand.getId(), id -> {
			if (cfg.healerFairy && FAIRY.equals(Skulls.texture(stand.getItemBySlot(EquipmentSlot.MAINHAND)))) return true;
			String head = cfg.soulWeaver || cfg.tentacle ? Skulls.texture(stand.getItemBySlot(EquipmentSlot.HEAD)) : "";
			return cfg.soulWeaver && WEAVER.equals(head) || cfg.tentacle && TENTACLE.equals(head);
		});
	}

	/** Explosion particles to drop. */
	public static boolean hideParticle(ParticleOptions particle) {
		RenderOptimizerConfig cfg = RenderOptimizerConfig.get();
		return cfg.enabled && cfg.explosions && (particle.getType() == ParticleTypes.EXPLOSION || particle.getType() == ParticleTypes.EXPLOSION_EMITTER);
	}

	public static boolean hideFire() {
		RenderOptimizerConfig cfg = RenderOptimizerConfig.get();
		return cfg.enabled && cfg.fireOverlay;
	}
}
