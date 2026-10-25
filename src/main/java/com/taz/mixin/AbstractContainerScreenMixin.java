package com.taz.mixin;

import com.taz.inventory.ItemOverlays;
import com.taz.xpassist.progress.ProgressDetector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sees the "Confirm Donation" click so museum donations come off the list straight away, and draws the
 * item overlays (rarity background, item numbers) on every slot.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void taz$slotBackground(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		ItemOverlays.background(g, slot);
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void taz$slotForeground(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		ItemOverlays.foreground(g, slot);
		com.taz.dungeons.info.MaskTimers.slot(g, slot);
	}

	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void taz$onSlotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
		if (com.taz.xpassist.SkyblockXpAssist.enabled()) ProgressDetector.onSlotClick((AbstractContainerScreen<?>) (Object) this, slotId);
	}
}
