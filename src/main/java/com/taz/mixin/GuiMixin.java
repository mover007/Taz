package com.taz.mixin;

import com.taz.xpassist.progress.ProgressDetector;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every action bar message ends up here, whichever packet Hypixel used to send it. */
@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void taz$onActionBar(Component message, boolean animate, CallbackInfo ci) {
		if (com.taz.xpassist.SkyblockXpAssist.enabled()) ProgressDetector.onActionBar(message);
		com.taz.Taz.onActionBar(message);
	}

	@Inject(method = "setTitle", at = @At("HEAD"))
	private void taz$onTitle(Component title, CallbackInfo ci) {
		com.taz.foraging.GalateaRecorder.onTitle("title", com.taz.dungeons.util.TextUtil.cleanChat(title.getString()));
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void taz$onSubtitle(Component subtitle, CallbackInfo ci) {
		com.taz.foraging.GalateaRecorder.onTitle("subtitle", com.taz.dungeons.util.TextUtil.cleanChat(subtitle.getString()));
	}
}
