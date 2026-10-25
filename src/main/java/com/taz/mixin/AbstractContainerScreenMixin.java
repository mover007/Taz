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
import com.taz.dungeons.f7.TerminalSolver;

/**
 * Sees the "Confirm Donation" click so museum donations come off the list straight away, and draws the
 * item overlays (rarity background, item numbers) on every slot.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@org.spongepowered.asm.mixin.Shadow
	protected Slot hoveredSlot;
	@org.spongepowered.asm.mixin.Shadow
	protected int leftPos;
	@org.spongepowered.asm.mixin.Shadow
	protected int topPos;

	/** Slot Binds and Loadout Keybinds: keys and clicks in menus. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$key(net.minecraft.client.input.KeyEvent event, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (com.taz.dungeons.info.LeapMenu.onKey(self, event.key()) || com.taz.general.AuctionInput.onKey(self, event.key())
				|| com.taz.general.InventoryKeys.onKey(self, hoveredSlot, event.key())) cir.setReturnValue(true);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$click(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (com.taz.dungeons.info.LeapMenu.onClick(self, event.x(), event.y(), event.button())
				|| com.taz.general.StorageOverlay.onClick(self, event.x(), event.y())
				|| com.taz.general.InventoryKeys.onClick(self, hoveredSlot, event.button())) cir.setReturnValue(true);
	}

	/** Leap Menu: Taz's four boxes instead of the chest. */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$leapMenu(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (com.taz.dungeons.info.LeapMenu.render(self, g, mouseX, mouseY) || com.taz.general.StorageOverlay.render(self, g, mouseX, mouseY)) ci.cancel();
	}

	/** Storage Overlay: the mouse wheel scrolls it. */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$scroll(double mouseX, double mouseY, double scrollX, double scrollY,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		if (com.taz.general.StorageOverlay.onScroll((AbstractContainerScreen<?>) (Object) this, scrollY)) cir.setReturnValue(true);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void taz$bindFrames(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		com.taz.general.InventoryKeys.render((AbstractContainerScreen<?>) (Object) this, g, hoveredSlot, leftPos, topPos);
	}

	/** In a Floor 7 terminal, Taz draws the slots (only the ones to click show). */
	@Inject(method = "extractSlots", at = @At("HEAD"), require = 0)
	private void taz$terminalBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		TerminalSolver.background((AbstractContainerScreen<?>) (Object) this, g);
	}

	@Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$terminalSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (TerminalSolver.slot((AbstractContainerScreen<?>) (Object) this, g, slot)) ci.cancel();
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
	private void taz$terminalTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if (TerminalSolver.active((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void taz$slotBackground(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		ItemOverlays.background(g, slot);
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void taz$slotForeground(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		ItemOverlays.foreground(g, slot);
		com.taz.dungeons.info.MaskTimers.slot(g, slot);
		com.taz.dungeons.info.PartyFinder.slot(g, slot);
	}

	@Inject(method = "slotClicked", at = @At("HEAD"))
	private void taz$onSlotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput, CallbackInfo ci) {
		if (com.taz.xpassist.SkyblockXpAssist.enabled()) ProgressDetector.onSlotClick((AbstractContainerScreen<?>) (Object) this, slotId);
		TerminalSolver.onClick((AbstractContainerScreen<?>) (Object) this, slotId);
		com.taz.general.AuctionInput.onSlotClick((AbstractContainerScreen<?>) (Object) this, slotId);
	}
}
