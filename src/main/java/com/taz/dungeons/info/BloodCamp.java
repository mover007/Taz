package com.taz.dungeons.info;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import com.taz.util.Skulls;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Blood Camp: when the Watcher starts talking, a countdown to when he moves (20.5 s) and a "Kill Mobs" alert. While his
 * mobs fly out, a box where each will land and how long until it does, so you can kill it the moment it spawns. Timed in
 * server ticks. From Odin's Blood Camp.
 */
public final class BloodCamp extends TextHud {
	private static final Pattern START = Pattern.compile("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've "
			+ "finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your "
			+ "way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, "
			+ "eh\\?|Ah, we meet again\\. As I foresaw\\.\\.\\.)$");
	private static final String SECOND_WAVE = "[BOSS] The Watcher: Let's see how you can handle this.";
	/** The Watcher's head textures, and his mobs' (skull data by DocilElm, via Odin). */
	private static final Set<String> WATCHER = Set.of("5662b6fb4b8b586dc4cdf803b0444d9b41d245cdf668dab38fa6c064afe8e461",
			"2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5", "bf6e1e7ed36586c2d98057002bc1adc981e2889f7bd7b5b3852bc55cc7802204",
			"4cec40008e1c31c1984f4d650abb3410f2037119fd624afc953563b73515a077", "b37dd18b5983a767e556dc64424af4b9abdb75d4c9e8b097818afbc431bf0e09",
			"f5f0d78fe38d1d7f75f08cdcf2a1855d6da0337e114a3c63e3bf3c618bc732b0", "51967db5e3199916252021903cf4e9952ef7cec220faaca1ba79bafe5938bd80",
			"9fd61e8055f6ee97ab5b6196a8d7ec98078ac37e00376157b6b520eaaa2f93af", "e5c1dc47a04ce57001a8b726f018cdef40b7ea9d7bd6d835ca495a0ef169f893");
	private static final Set<String> MOBS = Set.of("5a79860aca799407c0faa10b1bbcf42998fad4ebcf31d7a214180826b4ac94e1",
			"4774871190c878c9a2c4496c1e10257c6c4ea13807d72c15d7ac6ab3a7a9a8dc", "f4624a9a8c69ca204504abb043d47456cd9b09749a36357462303f276a229d4",
			"c919e5b8d56f062a21d224de14af771e2f55d09b59e7b099d09daa57540b79cf", "a89f6303af85877610912dc04b8b1e89724752f0a7eea05ab6547e228179c06f",
			"67237eddaebdbbdaacfa912885560ccdc65da93b4c3d513532868ec23bb5b448", "ff184c19e725623d32828a0a4e741e86f135ac63dbc828ff3c8468338f3683b",
			"5cccd53f5191c29a9dc8f0170fbdc4e59e66476aae33de27b468f1de1b7cf3b2", "b5ba76e02cab72fa7d8ac54ceec849976ab0b00a01068d68c266766bf70c3997",
			"aa23c8cde2943c84249de8351bc3540be5f8afaaba8b2cb032fc5acad78a269b", "9171f35b8f508142bd8c65417d0f324153ab9147739ee4d10dea733cc80eaa20",
			"7d12b2ade413a6cd7cca3c95e961ba9f0ae7165fa41fc7b5d5f094a01240c609", "96c3e31cfc66733275c42fcfb5d9a44342d643b55cd14c9c77d273a2352",
			"8421ba5b8e3573ef97beb5b40e15d15b20f30631c4c5330c3deda3047df0e92", "ad22772f769045fdc5be819ad68b01a97ac04c60886d2ca7afee39b282f7a383",
			"ad67f97d7f821729beb34a82c3f13592b40439fe5248e72576fde7aa180bf77", "fb3973a752b24a2f3abb003427f6dbe6ca3a61db0a1bcf351c6eab27ec27e50",
			"12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c", "3260325171a7ba8460830c0eea515c757a665e5b16a14207ba1a3182752bee87",
			"62d8fd3aa5617b1dac0aae9c81f6dd70ad93a59942f460d27e4d55a5cb8918e8", "56fc854bb84cf4b7697297973e02b79bc10698460b51a639c60e5e417734e11",
			"7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d", "c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b");
	/** How far a mob flies out (the first wave further), and when it lands after it appears (ms). */
	private static final double FIRST_FLIGHT = 16.1, LATER_FLIGHT = 11.9;
	private static final long FIRST_SPAWN_MS = 3900, LATER_SPAWN_MS = 1900;
	private static final int MOVE_TICKS = 410;

	private record Mob(Vec3 start, long startedMs, boolean firstWave) {
	}

	private static final Map<Integer, Mob> FLYING = new HashMap<>();
	private static final Map<Integer, Boolean> HEADS = new HashMap<>();
	private static ClientLevel level;
	private static Zombie watcher;
	private static boolean firstWave = true;
	private static int moveTicks = -1;
	private static long serverMs;
	private static int ticks;

	public BloodCamp() {
		super("Blood Camp", 0.42, 0.38);
	}

	public static void onChat(String line) {
		BloodCampConfig cfg = BloodCampConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon()) return;
		if (START.matcher(line).matches()) {
			moveTicks = MOVE_TICKS;
			if (cfg.moveAlert) TazChat.info("Watcher will move in " + String.format("%.2f", MOVE_TICKS / 20.0) + "s.");
		} else if (line.equals(SECOND_WAVE)) {
			firstWave = false;
		}
	}

	/** Every server tick: the clock everything here runs on. */
	public static void onServerTick() {
		serverMs += 50;
		if (moveTicks > 0 && --moveTicks == 0) {
			moveTicks = -1;
			if (BloodCampConfig.get().moveAlert) Alert.show("§c§lKill Mobs", null, Alert.Sound.PLING);
		}
	}

	/** Every client tick: find the Watcher, then the mobs flying out from him. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			clear();
		}
		BloodCampConfig cfg = BloodCampConfig.get();
		if (!cfg.enabled || !cfg.assist || mc.level == null || !DungeonLocation.inDungeon() || DungeonLocation.inBoss()) return;
		if (watcher != null && (!watcher.isAlive() || watcher.isRemoved())) watcher = null;
		ticks++;
		if (watcher == null) {
			if (ticks % 10 != 0) return;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Zombie z && WATCHER.contains(Skulls.texture(z.getItemBySlot(EquipmentSlot.HEAD)))) {
					watcher = z;
					break;
				}
			}
			if (watcher == null) return;
		}
		for (Entity e : mc.level.getEntities(watcher, watcher.getBoundingBox().inflate(20))) {
			if (!(e instanceof ArmorStand stand) || FLYING.containsKey(stand.getId())) continue;
			boolean mob = HEADS.computeIfAbsent(stand.getId(), id -> MOBS.contains(Skulls.texture(stand.getItemBySlot(EquipmentSlot.HEAD))));
			if (mob) FLYING.put(stand.getId(), new Mob(stand.position(), serverMs, firstWave));
		}
		FLYING.keySet().removeIf(id -> {
			Entity e = mc.level.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	/** Where a mob will land, once it has started moving, or null. */
	public static Vec3 landing(Entity stand) {
		Mob m = FLYING.get(stand.getId());
		if (m == null) return null;
		Vec3 moved = stand.position().subtract(m.start());
		if (moved.lengthSqr() < 1e-4) return null;
		return m.start().add(moved.normalize().scale(m.firstWave() ? FIRST_FLIGHT : LATER_FLIGHT));
	}

	/** Seconds until a mob lands (negative once it has). */
	public static double secondsLeft(Entity stand) {
		Mob m = FLYING.get(stand.getId());
		if (m == null) return 0;
		return ((m.firstWave() ? FIRST_SPAWN_MS : LATER_SPAWN_MS) - (serverMs - m.startedMs())) / 1000.0;
	}

	public static void clear() {
		FLYING.clear();
		HEADS.clear();
		watcher = null;
		firstWave = true;
		moveTicks = -1;
	}

	/** Test hooks. */
	public static int flying() {
		return FLYING.size();
	}

	public static int moveTicks() {
		return moveTicks;
	}

	public static Zombie watcher() {
		return watcher;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		BloodCampConfig cfg = BloodCampConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || !cfg.assist || FLYING.isEmpty() || mc.level == null) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			for (int id : FLYING.keySet()) {
				Entity stand = mc.level.getEntity(id);
				if (stand == null) continue;
				Vec3 end = landing(stand);
				if (end == null) continue;
				double s = secondsLeft(stand);
				AABB spawn = new AABB(end.x - 0.5, end.y + 1.5, end.z - 0.5, end.x + 0.5, end.y + 2.5, end.z + 0.5);
				AABB now = new AABB(stand.getX() - 0.5, stand.getY() + 1.5, stand.getZ() - 0.5, stand.getX() + 0.5, stand.getY() + 2.5, stand.getZ() + 0.5);
				batch.outline(now, 0xFFFFB0B0, 2f, false);
				batch.outline(spawn, s > 0 ? 0xFFFF2E2E : 0xFFFFFFFF, 2.5f, false);
				if (cfg.line) batch.line(stand.position().add(0, 2, 0), end.add(0, 2, 0), 0xFFB3121B, 2f, false);
				if (cfg.time) {
					int color = s > 1.5 ? 0xFFFFFFFF : s > 0.5 ? 0xFFFFB0B0 : s > 0 ? 0xFFFF2E2E : 0xFFA89393;
					batch.label(end.add(0, 2.8, 0), String.format("%.2fs", s), color, true);
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Blood camp failed to draw", e);
		}
	}

	@Override
	protected HudPos pos() {
		return BloodCampConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return BloodCampConfig.get().enabled && BloodCampConfig.get().moveHud;
	}

	@Override
	protected List<String> lines(boolean preview) {
		int t = preview ? 247 : moveTicks;
		if (t <= 0) return List.of();
		return List.of("§cWatcher moves in §f" + String.format("%.2fs", t / 20.0));
	}
}
