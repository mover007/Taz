package com.taz.mixin;

import com.taz.foraging.HuntingMobs;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.npc.NpcTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colours the tracked NPC's outline with the tracker colour, and each hunting mob's with its own, instead of the team colour. */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void taz$trackerColor(CallbackInfoReturnable<Integer> cir) {
		if (NpcTracker.isHighlighted((Entity) (Object) this)) {
			cir.setReturnValue(ModConfig.get().tracker.rgb());
			return;
		}
		int hunting = HuntingMobs.glowColor((Entity) (Object) this);
		if (hunting >= 0) cir.setReturnValue(hunting);
	}
}
