package com.taz.foraging.safari;

import com.taz.render.WorldRenderer;
import com.taz.util.Skulls;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Things to find on the Safari floor. A floor drop is three string item displays in one block (with a happy-villager
 * sparkle over it), as critterMod and Skyblocker read it; a Rockmite mound is an item display wearing the mound's head
 * texture, as Skyblocker reads it.
 */
public final class SafariFinds {
	/** The Rockmite mound head (Skyblocker's HeadTextures.ROCKMITE_MOUND). */
	public static final String MOUND_TEXTURE = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800";
	public static final int DROP_COLOR = 0xFF55FF55;
	public static final int MOUND_COLOR = 0xFFFFAA33;
	/** How near a drop a hit or use counts as taking it. */
	private static final double TAKE_RANGE = 1.5;
	/** A drop you hit stays hidden this long; if its strings are still there after, it wasn't taken. */
	private static final long TAKE_MS = 3_000;

	/** A floor drop: its three strings. */
	private record Drop(List<Display> strings) {
		boolean gone() {
			Long until = TAKEN.get(strings.get(0).getId());
			return until != null && until > System.currentTimeMillis() || strings.stream().anyMatch(Entity::isRemoved);
		}

		Vec3 centre() {
			Vec3 sum = Vec3.ZERO;
			for (Display d : strings) sum = sum.add(drawnAt(d));
			return sum.scale(1.0 / strings.size());
		}

		AABB box() {
			AABB box = null;
			for (Display d : strings) {
				Vec3 p = drawnAt(d);
				AABB one = new AABB(p, p);
				box = box == null ? one : box.minmax(one);
			}
			return new AABB(box.minX - 0.2, box.minY - 0.1, box.minZ - 0.2, box.maxX + 0.2, box.maxY + 0.2, box.maxZ + 0.2);
		}
	}

	private static List<Drop> drops = List.of();
	/** Drops you just hit, by their first string's id, and until when they stay hidden. */
	private static final Map<Integer, Long> TAKEN = new HashMap<>();
	private static List<Display> mounds = List.of();
	private static int ticks;

	private SafariFinds() {
	}

	/** Every 5 ticks while you're in the Safari (SafariHelper); looks round every 10. */
	public static void tick(Minecraft mc, SafariConfig cfg) {
		if (++ticks % 2 != 0) return;
		Map<BlockPos, List<Display>> strings = new HashMap<>();
		List<Display> foundMounds = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Display.ItemDisplay display)) continue;
			ItemStack stack = display.getSlot(0).get();
			if (stack.isEmpty()) continue;
			if (stack.is(Items.STRING)) strings.computeIfAbsent(e.blockPosition(), p -> new ArrayList<>()).add(display);
			else if (stack.is(Items.PLAYER_HEAD) && MOUND_TEXTURE.equals(Skulls.texture(stack))) foundMounds.add(display);
		}
		List<Drop> found = new ArrayList<>();
		for (List<Display> group : strings.values()) {
			if (group.size() == 3) found.add(new Drop(List.copyOf(group)));
		}
		drops = found;
		mounds = foundMounds;
		long now = System.currentTimeMillis();
		TAKEN.values().removeIf(until -> until <= now);
	}

	/**
	 * Where a display entity really draws its item: its position plus its transformation's translation, turned with
	 * the entity the way the game draws a fixed display.
	 */
	public static Vec3 drawnAt(Display d) {
		Display.RenderState state = d.renderState();
		if (state == null) return d.position();
		Vector3f t = new Vector3f(state.transformation().get(1f).translation());
		if (state.billboardConstraints() == Display.BillboardConstraints.FIXED) {
			new Quaternionf().rotationYXZ(-d.getYRot() * Mth.DEG_TO_RAD, d.getXRot() * Mth.DEG_TO_RAD, 0).transform(t);
		}
		return d.position().add(t.x, t.y, t.z);
	}

	/** You hit or used something here (an entity or a block): a floor drop right there is taken. */
	public static void onInteract(Vec3 at) {
		long until = System.currentTimeMillis() + TAKE_MS;
		for (Drop d : drops) {
			if (d.centre().distanceToSqr(at) <= TAKE_RANGE * TAKE_RANGE) TAKEN.put(d.strings().get(0).getId(), until);
		}
	}

	public static void reset() {
		TAKEN.clear();
		drops = List.of();
		mounds = List.of();
	}

	/** The floor drops still there, where their strings are drawn. */
	public static List<Vec3> drops() {
		return drops.stream().filter(d -> !d.gone()).map(Drop::centre).toList();
	}

	/** The Rockmite mounds still there, where they're drawn. */
	public static List<Vec3> mounds() {
		return mounds.stream().filter(d -> !d.isRemoved()).map(SafariFinds::drawnAt).toList();
	}

	/** Whether the mounds are worth showing: only while a Rockmite is still to catch. */
	public static boolean showMounds(SafariConfig cfg) {
		return cfg.mounds && SafariHelper.toDo(Critters.named("Rockmite"));
	}

	public static void render(WorldRenderer.Batch batch, SafariConfig cfg) {
		if (cfg.floorDrops) {
			double range = cfg.floorDropRange;
			for (Drop d : drops) {
				if (d.gone()) continue;
				AABB box = d.box();
				if (box.getCenter().distanceToSqr(batch.camera) > range * range) continue;
				mark(batch, box, "Floor drop", DROP_COLOR);
			}
		}
		if (showMounds(cfg)) {
			for (Vec3 p : mounds()) mark(batch, new AABB(p.x - 0.45, p.y - 0.45, p.z - 0.45, p.x + 0.45, p.y + 0.45, p.z + 0.45), "Rockmite mound", MOUND_COLOR);
		}
	}

	private static void mark(WorldRenderer.Batch batch, AABB box, String name, int color) {
		batch.filled(box, (color & 0xFFFFFF) | 0x40000000, true);
		batch.outline(box, color, 2.0f, true);
		Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.6, (box.minZ + box.maxZ) / 2);
		batch.label(top, name + "  " + Math.round(Math.sqrt(top.distanceToSqr(batch.camera))) + "m", color, true);
	}
}
