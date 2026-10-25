package com.taz.dungeons.f7;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Terminal Highlight: in Floor 7's Phase 3, a box on each terminal in your section that nobody has done yet (its stand says
 * "Inactive Terminal"), flashing white when you click it. From NoammAddons' Terminal ESP.
 */
public final class TerminalHighlight {
	private static final List<List<Vec3>> TERMINALS = List.of(
			List.of(new Vec3(110, 113, 73), new Vec3(110, 119, 79), new Vec3(90, 112, 92), new Vec3(90, 122, 101)),
			List.of(new Vec3(68, 109, 122), new Vec3(59, 119, 123), new Vec3(47, 109, 122), new Vec3(39, 108, 142), new Vec3(40, 124, 123)),
			List.of(new Vec3(-2, 109, 112), new Vec3(-2, 119, 93), new Vec3(18, 123, 93), new Vec3(-2, 109, 77)),
			List.of(new Vec3(41, 109, 30), new Vec3(44, 121, 30), new Vec3(67, 109, 30), new Vec3(72, 114, 47)));
	private static final long FLASH_MS = 250;

	/** Inactive terminal stand id -> its section (1-4). */
	private static final Map<Integer, Integer> INACTIVE = new HashMap<>();
	private static final Map<Integer, Long> CLICKED = new HashMap<>();
	private static ClientLevel level;
	private static int ticks;

	private TerminalHighlight() {
	}

	/** Every client tick in Phase 3: find the inactive terminals. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			INACTIVE.clear();
			CLICKED.clear();
		}
		if (!TerminalHighlightConfig.get().enabled || mc.level == null || DungeonLocation.floorNumber() != 7 || DungeonLocation.f7Phase() != 3
				|| ++ticks % 5 != 0) return;
		INACTIVE.clear();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand stand) || !stand.hasCustomName() || !stand.getCustomName().getString().equals("Inactive Terminal")) continue;
			for (int s = 0; s < TERMINALS.size(); s++) {
				if (TERMINALS.get(s).stream().anyMatch(p -> stand.distanceToSqr(p) <= 1.5)) INACTIVE.put(stand.getId(), s + 1);
			}
		}
	}

	/** You clicked (or hit) an entity. */
	public static void onClick(Entity e) {
		if (INACTIVE.containsKey(e.getId())) CLICKED.put(e.getId(), System.currentTimeMillis());
	}

	/** Test hook. */
	public static int inactive() {
		return INACTIVE.size();
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		TerminalHighlightConfig cfg = TerminalHighlightConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || INACTIVE.isEmpty() || mc.level == null || DungeonLocation.f7Phase() != 3) return;
		int section = DungeonLocation.p3Section();
		long now = System.currentTimeMillis();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			INACTIVE.forEach((id, s) -> {
				if (s != section) return;
				Entity e = mc.level.getEntity(id);
				if (e == null) return;
				boolean flash = cfg.flash && now - CLICKED.getOrDefault(id, 0L) < FLASH_MS;
				batch.filled(e.getBoundingBox(), flash ? 0x80FFFFFF : 0x50FF2E2E, cfg.throughWalls);
				batch.outline(e.getBoundingBox(), flash ? 0xFFFFFFFF : 0xFFFF2E2E, 2.5f, cfg.throughWalls);
			});
		} catch (Exception e) {
			Taz.LOGGER.error("Terminal highlight failed to draw", e);
		}
	}
}
