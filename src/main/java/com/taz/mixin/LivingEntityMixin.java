package com.taz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	public abstract MobEffectInstance getEffect(Holder<MobEffect> effect);

	/** Held Item: your swing's speed. */
	@Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$swingSpeed(CallbackInfoReturnable<Integer> cir) {
		Minecraft mc = Minecraft.getInstance();
		if ((Object) this != mc.player || mc.player.getMainHandItem().isEmpty()) return;
		MobEffectInstance haste = getEffect(MobEffects.HASTE), fatigue = getEffect(MobEffects.MINING_FATIGUE);
		int ticks = com.taz.general.HeldItem.swingTicks(haste == null ? 0 : haste.getAmplifier() + 1, fatigue == null ? 0 : fatigue.getAmplifier() + 1);
		if (ticks > 0) cir.setReturnValue(ticks);
	}
}
