package com.taz.general;

import com.taz.Taz;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.Items;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Spring Boots: how high the boots will throw you as you charge them (sneaking), read from the note-block pitches they
 * play, as a HUD and a box at the height you'll reach. From NoammAddons.
 */
public final class SpringBoots extends TextHud {
	private static final float[] HIGH = {0.82539684f, 0.8888889f, 0.93650794f, 1.0476191f, 1.1746032f, 1.3174603f, 1.7777778f};
	private static final float LOW = 0.6984127f;
	private static final float[] RESET = {0.0952381f, 1.6984127f};
	private static final float[] HEIGHTS = {0, 3, 6.5f, 9, 11.5f, 13.5f, 16, 18, 19, 20.5f, 22.5f, 25, 26.5f, 28, 29, 30, 31, 33, 34, 35.5f, 37,
			38, 39.5f, 40, 41, 42.5f, 43.5f, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61};
	private static float height;
	private static int highs, lows;

	public SpringBoots() {
		super("Spring Boots", 0.47, 0.60);
	}

	private static boolean same(float a, float b) {
		return Math.abs(a - b) < 0.0001f;
	}

	/** Every sound the server plays: the boots' charging notes, or the firework that resets them. */
	public static void onSound(String path, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (!SpringBootsConfig.get().enabled || mc.player == null) return;
		if (path.equals("entity.firework_rocket.launch")) {
			for (float r : RESET) if (same(r, pitch)) stop();
			return;
		}
		if (!path.equals("block.note_block.pling") || !mc.player.onGround() || !mc.player.isCrouching()) return;
		if (!Items.id(mc.player.getItemBySlot(EquipmentSlot.FEET)).equals("SPRING_BOOTS")) return;
		if (same(pitch, LOW)) lows = Math.min(lows + 1, 2);
		else for (float h : HIGH) if (same(h, pitch)) highs++;
		height = HEIGHTS[Math.min(lows + highs, HEIGHTS.length - 1)];
	}

	/** Every client tick: standing up or leaving the ground ends the charge. */
	public static void tick(Minecraft mc) {
		if (height > 0 && (mc.player == null || !mc.player.isCrouching() || !mc.player.onGround())) stop();
	}

	private static void stop() {
		height = 0;
		highs = lows = 0;
	}

	public static float height() {
		return height;
	}

	/** LevelRenderEvents.END_MAIN: a box where you'll land. */
	public static void render(LevelRenderContext context) {
		SpringBootsConfig cfg = SpringBootsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || !cfg.box || height <= 0 || mc.player == null) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			Vec3 p = mc.player.position().add(0, height, 0);
			AABB box = new AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 1, p.z + 0.5);
			batch.filled(box, 0x40FF2E2E, true);
			batch.outline(box, 0xFFFF2E2E, 2, true);
		} catch (Exception e) {
			Taz.LOGGER.error("Spring Boots box failed to draw", e);
		}
	}

	@Override
	protected HudPos pos() {
		return SpringBootsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return SpringBootsConfig.get().enabled && SpringBootsConfig.get().hudOn;
	}

	@Override
	protected List<String> lines(boolean preview) {
		float h = preview ? 33 : height;
		if (h <= 0) return List.of();
		boolean percent = SpringBootsConfig.get().percent;
		String value = percent ? String.format("%.0f%%", h / HEIGHTS[HEIGHTS.length - 1] * 100) : String.format("%.1f blocks", h);
		return List.of("§cSpring Boots §f" + value);
	}
}
