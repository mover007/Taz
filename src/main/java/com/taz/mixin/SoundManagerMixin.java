package com.taz.mixin;

import com.taz.foraging.HuntingMobs;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drops sounds a feature mutes (Phantoms on Galatea). */
@Mixin(SoundManager.class)
public class SoundManagerMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void taz$mute(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (HuntingMobs.muted(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}
}
