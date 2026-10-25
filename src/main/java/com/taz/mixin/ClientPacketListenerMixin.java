package com.taz.mixin;

import com.taz.Taz;
import com.taz.dungeons.util.LagMonitor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * Reads every system chat line straight from the packet, on the main thread, before any
	 * other mod can hide, rewrite or re-send it. SkyBlock mods commonly hide boss and Watcher
	 * dialogue, which kept those lines from reaching Fabric's chat events.
	 */
	@Inject(
			method = "handleSystemChat",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		Taz.onSystemChat(packet.content(), packet.overlay());
	}

	/**
	 * Server time packet (every 20 ticks), used to measure TPS. HEAD runs first on the network
	 * thread, which gives the true arrival time; the repeat call on the main thread is ignored.
	 */
	@Inject(method = "handleSetTime", at = @At("HEAD"), require = 0)
	private void taz$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		LagMonitor.onTimePacket(packet.gameTime(), System.nanoTime());
	}
}
