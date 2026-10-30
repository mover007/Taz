package com.taz.general.pv;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;
import java.util.function.Supplier;

/** The player standing in the viewer, in their skin and armor. It's only drawn, never added to the world. */
final class ViewerPlayer extends RemotePlayer {
	private final Supplier<PlayerSkinRenderCache.RenderInfo> skin;

	ViewerPlayer(ClientLevel level, UUID id, String name) {
		super(level, new GameProfile(id, name));
		skin = Minecraft.getInstance().playerSkinRenderCache().createLookup(ResolvableProfile.createUnresolved(id));
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F); // hat, jacket, sleeves and the rest
	}

	@Override
	public PlayerSkin getSkin() {
		return skin.get().playerSkin();
	}

	/** No name tag over the head: the name's already at the top of the viewer. The body still draws. */
	@Override
	public boolean isInvisibleTo(Player player) {
		return true;
	}
}
