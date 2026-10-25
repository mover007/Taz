package com.taz.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	/** Entities a feature hides (the blazes while the Blaze solver boxes them). */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
	private <E extends Entity> void taz$hide(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (com.taz.render.Hidden.hide(entity)) cir.setReturnValue(false);
	}
}
