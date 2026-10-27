package com.taz.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Display entities have their own outline colour: Taz's glows (Safari critters built from item displays) colour them too. */
@Mixin(Display.class)
public class DisplayMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void taz$glowColor(CallbackInfoReturnable<Integer> cir) {
		int glow = com.taz.render.Glow.color((Entity) (Object) this);
		if (glow >= 0) cir.setReturnValue(glow);
	}
}
