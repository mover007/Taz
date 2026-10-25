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

	/** Dark Mode: a tint before the HUD draws (over the world) or after (over the HUD too). */
	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void taz$darkBefore(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
		com.taz.general.DarkMode.draw(g, false);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void taz$darkAfter(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
		com.taz.general.DarkMode.draw(g, true);
	}

	/** Hotbar slots: the rarity colour behind each item. */
	@Inject(method = "extractSlot", at = @At("HEAD"), require = 0)
	private void taz$hotbarSlot(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, net.minecraft.client.DeltaTracker delta,
			net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack, int seed, CallbackInfo ci) {
		com.taz.inventory.ItemOverlays.hotbar(g, stack, x, y);
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
