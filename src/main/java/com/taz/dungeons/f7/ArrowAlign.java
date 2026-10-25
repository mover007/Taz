package com.taz.dungeons.f7;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arrow Align: on Phase 3's arrow device, how many times to click each frame, from the nine boards Odin knows (red when it
 * needs many, white when it needs few). Your clicks count straight away, so the numbers keep up as you click. From Odin.
 */
public final class ArrowAlign {
	private static final BlockPos CORNER = new BlockPos(-2, 120, 75);
	private static final BlockPos CENTER = new BlockPos(0, 120, 77);
	private static final int[][] SOLUTIONS = {
			{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1},
			{-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1},
			{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3},
			{5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1},
			{5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
			{7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
			{-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
			{-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1},
			{-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1}};
	private static final Map<Integer, Long> CLICKED = new HashMap<>();
	private static final Map<Integer, Integer> LEFT = new HashMap<>();
	private static int[] rotations;
	private static int[] target;

	private ArrowAlign() {
	}

	private static BlockPos frame(int i) {
		return CORNER.offset(0, i % 5, i / 5);
	}

	private static boolean here() {
		return ArrowAlignConfig.get().enabled && DungeonLocation.f7Phase() == 3;
	}

	/** Every client tick: read the frames and match a board. */
	public static void tick(Minecraft mc) {
		LEFT.clear();
		if (!here() || mc.player == null || mc.level == null) return;
		if (mc.player.blockPosition().distSqr(CENTER) > 200) {
			rotations = null;
			target = null;
			return;
		}
		List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, new net.minecraft.world.phys.AABB(CENTER).inflate(6),
				f -> f.getItem().is(Items.ARROW));
		int[] now = new int[25];
		Arrays.fill(now, -1);
		long t = System.currentTimeMillis();
		for (int i = 0; i < 25; i++) {
			if (rotations != null && t - CLICKED.getOrDefault(i, 0L) < 1000) {
				now[i] = rotations[i];
				continue;
			}
			BlockPos p = frame(i);
			for (ItemFrame f : frames) if (f.blockPosition().equals(p)) now[i] = f.getRotation();
		}
		rotations = now;
		for (int[] sol : SOLUTIONS) {
			boolean fits = true;
			for (int i = 0; i < 25 && fits; i++) if ((sol[i] == -1 || now[i] == -1) && sol[i] != now[i]) fits = false;
			if (!fits) continue;
			target = sol;
			for (int i = 0; i < 25; i++) {
				int n = clicks(now[i], sol[i]);
				if (n != 0) LEFT.put(i, n);
			}
		}
	}

	private static int clicks(int current, int goal) {
		return (8 - current + goal) % 8;
	}

	/** You clicked an entity: an arrow frame turns one step. */
	public static void onClick(Entity e) {
		if (!here() || !(e instanceof ItemFrame f) || !f.getItem().is(Items.ARROW) || rotations == null) return;
		BlockPos p = f.blockPosition();
		int i = (p.getY() - CORNER.getY()) + (p.getZ() - CORNER.getZ()) * 5;
		if (p.getX() != CORNER.getX() || i < 0 || i > 24 || rotations[i] == -1) return;
		CLICKED.put(i, System.currentTimeMillis());
		rotations[i] = (rotations[i] + 1) % 8;
		if (target != null && clicks(rotations[i], target[i]) == 0) LEFT.remove(i);
		else if (target != null) LEFT.put(i, clicks(rotations[i], target[i]));
	}

	/** Test hook: clicks left per frame index. */
	public static Map<Integer, Integer> left() {
		return LEFT;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		if (!here() || LEFT.isEmpty()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			LEFT.forEach((i, n) -> {
				Vec3 at = Vec3.atCenterOf(frame(i)).add(0.3, 0.1, 0);
				int color = n < 3 ? 0xFFFFFFFF : n < 5 ? 0xFFFFB0B0 : 0xFFFF2E2E;
				batch.label(at, String.valueOf(n), color, false);
			});
		} catch (Exception e) {
			Taz.LOGGER.error("Arrow align failed to draw", e);
		}
	}
}
