package com.taz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
	protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
	}

	/** Arrow Fix: your shortbow never starts drawing back. */
	@Inject(method = "tick", at = @At("HEAD"), require = 0)
	private void taz$noPullback(CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player || !com.taz.general.Tweaks.noPullback(useItem)) return;
		useItem = ItemStack.EMPTY;
		useItemRemaining = 0;
	}
}
