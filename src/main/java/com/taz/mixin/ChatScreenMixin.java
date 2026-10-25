package com.taz.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	/** Ctrl + left click copies the message under the mouse. */
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$copy(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (com.taz.general.ChatTweaks.onChatClick(event.x(), event.y(), event.button())) cir.setReturnValue(true);
	}
}
