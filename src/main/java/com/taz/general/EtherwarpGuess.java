package com.taz.general;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import com.taz.util.EtherRaycast;
import com.taz.util.Items;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Etherwarp: while you sneak with an Etherwarp item (an Aspect of the Void with Etherwarp merged, or the Etherwarp
 * Conduit, which works without sneaking), a box on the block you'd land on: red when it works, black when it won't (no
 * room to stand, or out of range). From Odin's Etherwarp.
 */
public final class EtherwarpGuess {
	private static ItemStack cachedStack;
	private static double distance = -1;
	private static boolean conduit;
	private static EtherRaycast.Hit last;

	private EtherwarpGuess() {
	}

	/** The Etherwarp range of an item, or -1 when it can't Etherwarp. */
	public static double range(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return -1;
		CompoundTag tag = data.copyTag();
		boolean isConduit = Items.id(stack).equals("ETHERWARP_CONDUIT");
		if (tag.getIntOr("ethermerge", 0) != 1 && !isConduit) return -1;
		return 57 + tag.getIntOr("tuned_transmission", 0);
	}

	/** Where you'd land right now, or null (test hook too). */
	public static EtherRaycast.Hit guess() {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.screen != null) return null;
		ItemStack hand = player.getMainHandItem();
		if (hand != cachedStack) {
			cachedStack = hand;
			distance = range(hand);
			conduit = Items.id(hand).equals("ETHERWARP_CONDUIT");
		}
		if (distance < 0 || (!player.isShiftKeyDown() && !conduit)) return null;
		return last = EtherRaycast.cast(player.position(), distance, true, player.isShiftKeyDown());
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		EtherwarpConfig cfg = EtherwarpConfig.get();
		if (!cfg.enabled) return;
		EtherRaycast.Hit hit = guess();
		if (hit == null || (!hit.ok() && !cfg.showFail)) return;
		Minecraft mc = Minecraft.getInstance();
		VoxelShape shape = hit.state() == null || mc.level == null ? null : hit.state().getShape(mc.level, hit.pos());
		AABB box = cfg.fullBlock || shape == null || shape.isEmpty() ? new AABB(hit.pos()) : shape.bounds().move(hit.pos());
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			batch.filled(box, hit.ok() ? 0x55FF2E2E : 0x70000000, cfg.throughWalls);
			batch.outline(box, hit.ok() ? 0xFFFF2E2E : 0xFF6E5A5A, 2.5f, cfg.throughWalls);
		} catch (Exception e) {
			Taz.LOGGER.error("Etherwarp guess failed to draw", e);
		}
	}

	/** Hypixel's Etherwarp sound (the ender dragon's hurt sound at a low pitch); true when Taz plays its own instead. */
	public static boolean onSound(String id, float pitch) {
		EtherwarpConfig cfg = EtherwarpConfig.get();
		if (!cfg.enabled || !cfg.sound || !id.equals("entity.ender_dragon.hurt") || Math.abs(pitch - 0.53968257f) > 0.001f) return false;
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.5f));
		return true;
	}

	/** Test hook: the last guess drawn. */
	public static EtherRaycast.Hit last() {
		return last;
	}
}
