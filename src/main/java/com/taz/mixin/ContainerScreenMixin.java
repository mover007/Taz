package com.taz.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
public class ContainerScreenMixin {
	/** Leap Menu: no chest behind Taz's boxes. */
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$noChest(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (com.taz.dungeons.info.LeapMenu.active((ContainerScreen) (Object) this)) ci.cancel();
	}
}
