package com.taz.slayer;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.general.AlertsConfig;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Your slayer boss: Hypixel puts a "Spawned by: &lt;name&gt;" hologram over every slayer boss, so the boss under
 * the one with your name is yours. Alerts when it spawns, boxes it, and times the kill until
 * "SLAYER QUEST COMPLETE!".
 */
public final class SlayerHelper {
	private static final double SEARCH_RANGE = 48;

	private static int standId = -1;
	private static int bossId = -1;
	private static long spawnMs;
	private static int ticks;
	private static final List<Long> KILLS = new ArrayList<>();

	private SlayerHelper() {
	}

	/** Your boss right now, or null. */
	public static Entity boss() {
		Minecraft mc = Minecraft.getInstance();
		if (bossId < 0 || mc.level == null) return null;
		Entity e = mc.level.getEntity(bossId);
		return e != null && e.isAlive() ? e : null;
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		SlayerConfig cfg = SlayerConfig.get();
		if (!cfg.enabled || mc.player == null || mc.level == null || ++ticks % 5 != 0) return;
		String spawnedBy = "Spawned by: " + mc.player.getName().getString();
		ArmorStand stand = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand a && a.getCustomName() != null && e.distanceToSqr(mc.player) < SEARCH_RANGE * SEARCH_RANGE
					&& TextUtil.cleanChat(a.getCustomName().getString()).contains(spawnedBy)) {
				stand = a;
				break;
			}
		}
		if (stand == null) {
			standId = -1;
			bossId = -1;
			return;
		}
		if (stand.getId() != standId) {
			standId = stand.getId();
			spawnMs = System.currentTimeMillis();
			if (cfg.spawnAlert) Alert.show("§c§lBOSS SPAWNED", "§fKill it!", AlertsConfig.get().sound);
		}
		bossId = bossUnder(mc, stand);
	}

	/** The mob closest under the hologram (the boss's name tags sit on top of it). */
	private static int bossUnder(Minecraft mc, ArmorStand stand) {
		Entity best = null;
		double bestDist = 4.0 * 4.0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e instanceof Player || !e.isAlive()) continue;
			double dx = e.getX() - stand.getX(), dz = e.getZ() - stand.getZ(), dy = stand.getY() - e.getY();
			if (dy < -1 || dy > 5) continue;
			double d = dx * dx + dz * dz;
			if (d < bestDist) {
				best = e;
				bestDist = d;
			}
		}
		return best == null ? -1 : best.getId();
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		SlayerConfig cfg = SlayerConfig.get();
		if (!cfg.enabled) return;
		if (line.equals("SLAYER QUEST COMPLETE!") && spawnMs > 0) {
			long took = System.currentTimeMillis() - spawnMs;
			spawnMs = 0;
			KILLS.add(took);
			if (cfg.killTimes) {
				long best = KILLS.stream().mapToLong(Long::longValue).min().orElse(took);
				long avg = Math.round(KILLS.stream().mapToLong(Long::longValue).average().orElse(took));
				TazChat.info("Boss killed in " + TextUtil.formatTime(took) + " (best " + TextUtil.formatTime(best) + ", average "
						+ TextUtil.formatTime(avg) + ", " + KILLS.size() + " this session)");
			}
		} else if (line.equals("SLAYER QUEST FAILED!")) {
			spawnMs = 0;
		}
	}

	/** Test hook: kill times this session. */
	public static List<Long> kills() {
		return KILLS;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		SlayerConfig cfg = SlayerConfig.get();
		Entity boss = cfg.enabled && cfg.highlight ? boss() : null;
		if (boss == null) return;
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 p = boss.getPosition(partial);
		double w = boss.getBbWidth() / 2 + 0.05;
		AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + boss.getBbHeight() + 0.05, p.z + w);
		int color = cfg.argb();
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			batch.filled(box, (color & 0xFFFFFF) | 0x30000000, cfg.throughWalls);
			batch.outline(box, color, 3.0f, cfg.throughWalls);
		} catch (Exception e) {
			Taz.LOGGER.error("Slayer boss highlight failed", e);
		}
	}
}
