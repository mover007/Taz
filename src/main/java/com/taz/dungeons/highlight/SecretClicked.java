package com.taz.dungeons.highlight;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Secret Clicked: a box on each secret you click (chest, lever, essence skull), each secret item you pick up and each
 * bat that dies near you, for a few seconds, and a chime (the blaze's hurt sound, like Odin). From Odin's Secret
 * Clicked.
 */
public final class SecretClicked {
	private static final double BAT_RANGE = 16;

	private static final class Clicked {
		final AABB box;
		final long at;
		boolean locked;

		Clicked(AABB box, long at) {
			this.box = box;
			this.at = at;
		}
	}

	private static final List<Clicked> CLICKED = new ArrayList<>();
	private static long lastChime;

	private SecretClicked() {
	}

	private static boolean active() {
		SecretClickedConfig cfg = SecretClickedConfig.get();
		return cfg.enabled && DungeonLocation.inDungeon() && (!DungeonLocation.inBoss() || cfg.inBoss);
	}

	/** You right-clicked a block. */
	public static void onInteract(BlockPos pos, BlockState state) {
		if (!active()) return;
		if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof AbstractSkullBlock) {
			Minecraft mc = Minecraft.getInstance();
			VoxelShape shape = mc.level == null ? null : state.getShape(mc.level, pos);
			box(shape == null || shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos));
			chime();
		}
	}

	/** An item entity was picked up (the take-item packet). */
	public static void onItemTaken(Entity item, int collector) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.player == null || collector != mc.player.getId()) return;
		if (SecretClickedConfig.get().items) box(new AABB(item.blockPosition()));
		chime();
	}

	/** An entity event: 3 is a death, and a bat dying near you is a bat secret. */
	public static void onEntityEvent(Entity entity, byte id) {
		Minecraft mc = Minecraft.getInstance();
		if (id != 3 || !(entity instanceof Bat) || !active() || mc.player == null || entity.distanceTo(mc.player) > BAT_RANGE) return;
		box(new AABB(entity.blockPosition()));
		chime();
	}

	/** Every chat line: the chest you just opened was locked. */
	public static void onChat(String line) {
		if (line.equals("That chest is locked!") && !CLICKED.isEmpty()) CLICKED.getLast().locked = true;
	}

	private static void box(AABB box) {
		SecretClickedConfig cfg = SecretClickedConfig.get();
		if (!cfg.boxes || CLICKED.stream().anyMatch(c -> c.box.intersects(box.deflate(0.01)))) return;
		CLICKED.add(new Clicked(box, System.currentTimeMillis()));
	}

	private static void chime() {
		SecretClickedConfig cfg = SecretClickedConfig.get();
		long now = System.currentTimeMillis();
		if (!cfg.chime || now - lastChime < 50) return;
		lastChime = now;
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BLAZE_HURT, 2.0f, cfg.volume / 100f));
	}

	/** Test hooks. */
	public static int boxes() {
		return CLICKED.size();
	}

	public static boolean locked() {
		return !CLICKED.isEmpty() && CLICKED.getLast().locked;
	}

	public static void reset() {
		CLICKED.clear();
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		long stay = (long) (SecretClickedConfig.get().seconds * 1000);
		long now = System.currentTimeMillis();
		CLICKED.removeIf(c -> now - c.at > stay);
		if (CLICKED.isEmpty() || !active()) return;
		boolean walls = SecretClickedConfig.get().throughWalls;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (Clicked c : CLICKED) {
				batch.filled(c.box, c.locked ? 0x90000000 : 0x50FF6B6B, walls);
				batch.outline(c.box, c.locked ? 0xFFB3121B : 0xFFFF2E2E, 2f, walls);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Secret clicked boxes failed to draw", e);
		}
	}
}
