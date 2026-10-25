package com.taz.fishing;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

/** Bite alert, bobber timer and Double Hook alert (Fishing > Fishing Helper). */
public final class FishingHelper {
	private static int hookId = -1;
	private static boolean bitten;

	private FishingHelper() {
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		FishingConfig cfg = FishingConfig.get();
		FishingHook hook = mc.player == null ? null : mc.player.fishing;
		if (!cfg.enabled || hook == null || mc.level == null) {
			hookId = -1;
			return;
		}
		if (hook.getId() != hookId) { // a new cast
			hookId = hook.getId();
			bitten = false;
		}
		if (!cfg.biteAlert || bitten) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand a && a.getCustomName() != null && a.distanceToSqr(hook) < 3.0 * 3.0
					&& TextUtil.cleanChat(a.getCustomName().getString()).equals("!!!")) {
				bitten = true;
				Alert.show("§c§l!!!", "§fReel in!", cfg.biteSound);
				return;
			}
		}
	}

	/** True once a fish bit on the current cast (test hook). */
	public static boolean bitten() {
		return bitten;
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		FishingConfig cfg = FishingConfig.get();
		if (cfg.enabled && cfg.doubleHook && line.startsWith("It's a Double Hook!")) {
			Alert.show("§b§lDOUBLE HOOK", "", cfg.biteSound);
		}
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		FishingConfig cfg = FishingConfig.get();
		Minecraft mc = Minecraft.getInstance();
		FishingHook hook = mc.player == null ? null : mc.player.fishing;
		if (!cfg.enabled || !cfg.timer || hook == null) return;
		Vec3 p = hook.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		String text = String.format("%.1fs", hook.tickCount / 20.0);
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			batch.label(p.add(0, 0.9, 0), text, bitten ? 0xFFFF5555 : 0xFFFFFFFF, true);
		} catch (Exception e) {
			Taz.LOGGER.error("Bobber timer failed", e);
		}
	}
}
