package com.taz.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Cursor Reset: a menu opened right after another keeps the mouse where it was. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow
	private double xpos;
	@Shadow
	private double ypos;
	@Unique
	private double taz$x;
	@Unique
	private double taz$y;

	@Inject(method = "grabMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;xpos:D", ordinal = 0, opcode = Opcodes.PUTFIELD), require = 0)
	private void taz$remember(CallbackInfo ci) {
		taz$x = xpos;
		taz$y = ypos;
	}

	@Inject(method = "releaseMouse", at = @At("TAIL"), require = 0)
	private void taz$restore(CallbackInfo ci) {
		if (!com.taz.general.Tweaks.keepCursor() || taz$x <= 0 || taz$y <= 0) return;
		xpos = taz$x;
		ypos = taz$y;
		InputConstants.grabOrReleaseMouse(Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL, xpos, ypos);
	}
}
