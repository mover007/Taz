package com.taz.mixin;

import com.taz.Taz;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	/**
	 * Every block that actually changes on the client (single and multi-block update packets, and the client's own break
	 * prediction) passes through here with its old and new state.
	 */
	@Inject(method = "setBlocksDirty", at = @At("HEAD"), require = 0)
	private void taz$onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		Taz.onBlockChanged(pos, oldState, newState);
	}
}
