package com.taz.foraging.safari;

import com.taz.render.WorldRenderer;
import com.taz.util.Skulls;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Things to find on the Safari floor. A floor drop is three string item displays in one block (with a happy-villager
 * sparkle over it), as critterMod and Skyblocker read it; a Rockmite mound is an item display wearing the mound's head
 * texture, as Skyblocker reads it. Both get a box and a name through walls.
 */
public final class SafariFinds {
	/** The Rockmite mound head (Skyblocker's HeadTextures.ROCKMITE_MOUND). */
	public static final String MOUND_TEXTURE = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800";
	public static final int DROP_COLOR = 0xFF55FF55;
	public static final int MOUND_COLOR = 0xFFFFAA33;
	/** A drop stays marked this long after it was last seen, so one that flickers out of view doesn't blink. */
	private static final long HOLD_MS = 5_000;

	private static final Map<BlockPos, Long> DROPS = new LinkedHashMap<>();
	private static List<Vec3> mounds = List.of();
	private static int ticks;

	private SafariFinds() {
	}

	/** Every 5 ticks while you're in the Safari (SafariHelper); looks round every 10. */
	public static void tick(Minecraft mc, SafariConfig cfg) {
		if (++ticks % 2 != 0) return;
		Map<BlockPos, Integer> strings = new HashMap<>();
		List<Vec3> foundMounds = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Display.ItemDisplay display)) continue;
			ItemStack stack = display.getSlot(0).get();
			if (stack.isEmpty()) continue;
			if (stack.is(Items.STRING)) strings.merge(e.blockPosition(), 1, Integer::sum);
			else if (stack.is(Items.PLAYER_HEAD) && MOUND_TEXTURE.equals(Skulls.texture(stack))) foundMounds.add(e.position());
		}
		long now = System.currentTimeMillis();
		strings.forEach((pos, count) -> {
			if (count == 3) DROPS.put(pos, now);
		});
		DROPS.values().removeIf(seen -> now - seen > HOLD_MS);
		mounds = foundMounds;
	}

	public static void reset() {
		DROPS.clear();
		mounds = List.of();
	}

	/** The floor drops in sight range, as block centres. */
	public static List<Vec3> drops() {
		return DROPS.keySet().stream().map(Vec3::atBottomCenterOf).toList();
	}

	public static List<Vec3> mounds() {
		return mounds;
	}

	/** Whether the mounds are worth showing: only while a Rockmite is still to catch. */
	public static boolean showMounds(SafariConfig cfg) {
		return cfg.mounds && SafariHelper.toDo(Critters.named("Rockmite"));
	}

	public static void render(WorldRenderer.Batch batch, SafariConfig cfg) {
		if (cfg.floorDrops) {
			for (Vec3 p : drops()) mark(batch, new AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 0.4, p.z + 0.5), "Floor drop", DROP_COLOR);
		}
		if (showMounds(cfg)) {
			for (Vec3 p : mounds) mark(batch, new AABB(p.x - 0.45, p.y - 0.45, p.z - 0.45, p.x + 0.45, p.y + 0.45, p.z + 0.45), "Rockmite mound", MOUND_COLOR);
		}
	}

	private static void mark(WorldRenderer.Batch batch, AABB box, String name, int color) {
		batch.filled(box, (color & 0xFFFFFF) | 0x40000000, true);
		batch.outline(box, color, 2.0f, true);
		Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.6, (box.minZ + box.maxZ) / 2);
		batch.label(top, name + "  " + Math.round(Math.sqrt(top.distanceToSqr(batch.camera))) + "m", color, true);
	}
}
