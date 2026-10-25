package com.taz.mixin;

import com.taz.foraging.HuntingMobs;
import com.taz.xpassist.npc.NpcTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the tracked NPC and Galatea's hunting mobs use the vanilla glowing outline, which is drawn through walls. */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void taz$glowTrackedNpc(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (NpcTracker.isHighlighted(entity) || HuntingMobs.glowColor(entity) >= 0) {
			cir.setReturnValue(true);
		}
	}
}
