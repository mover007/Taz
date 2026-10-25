package com.taz.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {
	/** Sound Volumes: each sound's own volume setting. */
	@Inject(method = "getVolume", at = @At("RETURN"), cancellable = true, require = 0)
	private void taz$volume(CallbackInfoReturnable<Float> cir) {
		float m = com.taz.general.SoundVolumes.multiplier(((AbstractSoundInstance) (Object) this).getIdentifier().toString());
		if (m != 1) cir.setReturnValue(cir.getReturnValue() * m);
	}
}
