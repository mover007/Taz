package com.taz.mixin;

import com.taz.Taz;
import com.taz.dungeons.util.LagMonitor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * Reads every system chat line straight from the packet, on the main thread, before any other mod can hide, rewrite or
	 * re-send it.
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

	/** Anything sent to chat, by any mod or you: Taz's party messages wait a moment after it. */
	@Inject(method = {"sendChat", "sendCommand", "sendUnattendedCommand"}, at = @At("HEAD"), require = 0)
	private void taz$onOutgoing(CallbackInfo ci) {
		com.taz.util.TazChat.onOutgoing();
	}

	/** Block events (a chest's lid opening), on the main thread: a dungeon secret chest someone opened. */
	@Inject(
			method = "handleBlockEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
		Taz.onBlockEvent(packet.getPos(), packet.getBlock(), packet.getB0(), packet.getB1());
	}

	/** Entity events on the main thread (a mob's death animation starting: the Mimic dying). */
	@Inject(
			method = "handleEntityEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Entity entity = packet.getEntity(((ClientPacketListener) (Object) this).getLevel());
		if (entity != null) Taz.onEntityEvent(entity, packet.getEventId());
	}

	/** An item picked up (a dungeon secret item), before the item entity is removed. */
	@Inject(
			method = "handleTakeItemEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onTakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Entity item = ((ClientPacketListener) (Object) this).getLevel().getEntity(packet.getItemId());
		if (item != null) Taz.onItemTaken(item, packet.getPlayerId());
	}

	/** Particles from the server (the Gyro Wand going off, the Mage beam); Taz can hide them. */
	@Inject(
			method = "handleParticleEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			cancellable = true,
			require = 0)
	private void taz$onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (Taz.onParticle(packet.getParticle(), packet.getX(), packet.getY(), packet.getZ(), packet.getXDist(), packet.getYDist(), packet.getZDist(), packet.getMaxSpeed(),
				packet.getCount())) ci.cancel();
	}

	/** The server moving you (a teleport pad in the Teleport Maze). */
	@Inject(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		Taz.onTeleport(packet.change().position(), packet.change().yRot(), packet.change().xRot());
	}

	/** The tab list's header and footer (the footer lists the dungeon's blessings). */
	@Inject(
			method = "handleTabListCustomisation",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			require = 0)
	private void taz$onTabList(ClientboundTabListPacket packet, CallbackInfo ci) {
		Taz.onTabFooter(packet.footer().getString());
	}

	/** A sound the server plays (its id and the exact pitch it sent: Ragnarock's cast, Spring Boots' notes). */
	@Inject(
			method = "handleSoundEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
					shift = At.Shift.AFTER),
			cancellable = true,
			require = 0)
	private void taz$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
		if (Taz.onSound(packet.getSound().value().location().getPath(), packet.getPitch())) ci.cancel();
	}

	/** The answer to Taz's ping request: your ping. */
	@Inject(method = "handlePongResponse", at = @At("HEAD"), require = 0)
	private void taz$onPong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		com.taz.general.PerformanceHud.onPong(packet.time());
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
