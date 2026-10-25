package com.taz.dungeons.f7;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Wither Dragons: in Master Mode 7's last phase, when each dragon starts spawning (Hypixel's ring of flame at its statue)
 * a title and a countdown to it, over its spawn and on a HUD for the first one due; a box round each dragon's area and a
 * line to the next one; each dragon's health over it; and how long each lived. From Odin.
 */
public final class WitherDragons extends TextHud {
	public enum Dragon {
		RED("Red", "§c", 0xFFFF5555, new BlockPos(27, 14, 59), new BlockPos(32, 22, 59), new AABB(14.5, 13, 45.5, 39.5, 28, 70.5), 24, 30, 56, 62),
		ORANGE("Orange", "§6", 0xFFFFAA00, new BlockPos(85, 14, 56), new BlockPos(80, 23, 56), new AABB(72, 8, 47, 102, 28, 77), 82, 88, 53, 59),
		GREEN("Green", "§a", 0xFF55FF55, new BlockPos(27, 14, 94), new BlockPos(32, 23, 94), new AABB(7, 8, 80, 37, 28, 110), 23, 29, 91, 97),
		BLUE("Blue", "§b", 0xFF55FFFF, new BlockPos(84, 14, 94), new BlockPos(79, 23, 94), new AABB(71.5, 13, 82.5, 96.5, 26, 107.5), 82, 88, 91, 97),
		PURPLE("Purple", "§5", 0xFFAA00AA, new BlockPos(56, 14, 125), new BlockPos(56, 22, 120), new AABB(45.5, 13, 113.5, 68.5, 23, 136.5), 53, 59, 122, 128);

		final String label, code;
		final int color;
		final BlockPos spawn, statue;
		final AABB area;
		final double minX, maxX, minZ, maxZ;
		int ticksToSpawn, timesSpawned, entity = -1;
		State state = State.DEAD;
		long spawnedTick;

		Dragon(String label, String code, int color, BlockPos spawn, BlockPos statue, AABB area, double minX, double maxX, double minZ, double maxZ) {
			this.label = label;
			this.code = code;
			this.color = color;
			this.spawn = spawn;
			this.statue = statue;
			this.area = area;
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		public State state() {
			return state;
		}

		public int ticksToSpawn() {
			return ticksToSpawn;
		}
	}

	public enum State {
		SPAWNING, ALIVE, DEAD
	}

	private static final List<Dragon> ORDER = new ArrayList<>();
	private static ClientLevel level;
	private static long tick;

	public WitherDragons() {
		super("Dragon Timer", 0.45, 0.25);
	}

	private static boolean here() {
		return WitherDragonsConfig.get().enabled && DungeonLocation.f7Phase() == 5;
	}

	/** A particle from the server: a ring of flame at a statue means that dragon is coming. */
	public static void onParticle(ParticleOptions particle, double x, double y, double z, float dx, float dy, float dz, float speed, int count) {
		if (!here() || particle.getType() != ParticleTypes.FLAME || count != 20 || y != 19 || dx != 2 || dy != 3 || dz != 2 || speed != 0
				|| x % 1 != 0 || z % 1 != 0) return;
		for (Dragon d : Dragon.values()) {
			if (d.state != State.DEAD || x < d.minX || x > d.maxX || z < d.minZ || z > d.maxZ) continue;
			d.state = State.SPAWNING;
			d.ticksToSpawn = 100;
			ORDER.add(d);
			if (WitherDragonsConfig.get().title) Alert.show(d.code + "§l" + d.label + " is spawning!", null, Alert.Sound.PLING);
			return;
		}
	}

	/** Every server tick: the countdowns. */
	public static void onServerTick() {
		tick++;
		for (Dragon d : Dragon.values()) {
			if (d.ticksToSpawn > 0) d.ticksToSpawn--;
			else if (d.state == State.SPAWNING) alive(d, -1);
		}
	}

	private static void alive(Dragon d, int entity) {
		if (entity >= 0) d.entity = entity;
		if (d.state != State.SPAWNING) return;
		d.state = State.ALIVE;
		d.timesSpawned++;
		d.spawnedTick = tick;
		if (here() && WitherDragonsConfig.get().spawned) TazChat.info(d.label + " dragon spawned (" + d.timesSpawned + ")");
	}

	private static void dead(Dragon d, boolean killed) {
		if (killed && here() && WitherDragonsConfig.get().aliveTime && d.state == State.ALIVE) {
			TazChat.info(d.label + " was alive for " + String.format("%.1f", (tick - d.spawnedTick) / 20.0) + "s");
		}
		d.state = State.DEAD;
		d.entity = -1;
		ORDER.remove(d);
	}

	/** Every client tick: match new dragons to their spawn, and see which died. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			clear();
		}
		if (!here() || mc.level == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EnderDragon dragon) || !dragon.isAlive()) continue;
			boolean known = false;
			for (Dragon d : Dragon.values()) if (d.entity == e.getId()) known = true;
			if (known) continue;
			Dragon nearest = null;
			double best = Double.MAX_VALUE;
			for (Dragon d : Dragon.values()) {
				if (d.state == State.DEAD) continue;
				double dist = e.distanceToSqr(Vec3.atCenterOf(d.spawn));
				if (dist < best) {
					best = dist;
					nearest = d;
				}
			}
			if (nearest != null && best < 30 * 30) alive(nearest, e.getId());
		}
		for (Dragon d : Dragon.values()) {
			if (d.entity < 0) continue;
			Entity e = mc.level.getEntity(d.entity);
			if (e == null || !e.isAlive() || (e instanceof EnderDragon dragon && dragon.getHealth() <= 0)) dead(d, true);
		}
	}

	/** A block changed: a statue broken means its dragon is gone for good. */
	public static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		if (!here() || !now.isAir()) return;
		for (Dragon d : Dragon.values()) if (d.statue.equals(pos)) dead(d, false);
	}

	public static void clear() {
		for (Dragon d : Dragon.values()) {
			d.ticksToSpawn = d.timesSpawned = 0;
			d.state = State.DEAD;
			d.entity = -1;
			d.spawnedTick = 0;
		}
		ORDER.clear();
		tick = 0;
	}

	/** The first dragon due, or null. */
	public static Dragon priority() {
		return ORDER.isEmpty() ? null : ORDER.getFirst();
	}

	private static String timer(int ticks) {
		String color = ticks <= 20 ? "§c" : ticks <= 60 ? "§f" : "§7";
		return color + String.format("%.1fs", ticks / 20.0);
	}

	private static String health(float hp) {
		String n = hp >= 1e9 ? String.format("%.1fb", hp / 1e9) : hp >= 1e6 ? String.format("%.1fm", hp / 1e6) : hp >= 1e3 ? String.format("%.1fk", hp / 1e3)
				: String.valueOf((int) hp);
		return n;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		WitherDragonsConfig cfg = WitherDragonsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!here() || mc.level == null) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (Dragon d : Dragon.values()) {
				if (cfg.boxes && d.state != State.DEAD) batch.outline(d.area, d.color, 2f, false);
				if (cfg.timer && d.ticksToSpawn > 0) {
					batch.label(Vec3.atCenterOf(d.spawn), d.label + " " + String.format("%.1fs", d.ticksToSpawn / 20.0), d.color, true);
				}
				if (cfg.health && d.entity >= 0 && mc.level.getEntity(d.entity) instanceof EnderDragon e && e.getHealth() > 0) {
					float frac = e.getHealth() / e.getMaxHealth();
					int color = frac > 0.75 ? 0xFFFFFFFF : frac > 0.5 ? 0xFFFFB0B0 : frac > 0.25 ? 0xFFFF6B6B : 0xFFFF2E2E;
					batch.label(e.position().add(0, 2, 0), health(e.getHealth()), color, true);
				}
			}
			Dragon p = priority();
			if (cfg.tracer && p != null && p.state == State.SPAWNING) batch.line(WorldRenderer.crosshair(batch.camera), Vec3.atCenterOf(p.spawn), p.color, 2f, true);
		} catch (Exception e) {
			Taz.LOGGER.error("Wither dragons failed to draw", e);
		}
	}

	@Override
	protected HudPos pos() {
		return WitherDragonsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return WitherDragonsConfig.get().enabled && WitherDragonsConfig.get().timer;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) return List.of("§5§lPurple §f4.5s");
		Dragon p = priority();
		if (p == null || p.ticksToSpawn <= 0) return List.of();
		return List.of(p.code + "§l" + p.label + " " + timer(p.ticksToSpawn));
	}
}
