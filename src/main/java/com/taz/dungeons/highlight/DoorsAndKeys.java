package com.taz.dungeons.highlight;

import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Doors &amp; Keys: in a dungeon's clear, boxes the wither doors (coal) and the blood door (red terracotta) next to
 * rooms you've been in, white once the party holds a key that opens it, red until then. From Odin's Door Highlight and
 * NoammAddons' Door Keys.
 */
public final class DoorsAndKeys {
	private static final Pattern WITHER_KEY = Pattern.compile("^(?:\\[[^]]*?] )?\\w{1,16} has obtained Wither Key!?$|^A Wither Key was picked up!$");
	private static final Pattern WITHER_DOOR = Pattern.compile("^(?:\\[[^]]*?] )?\\w{1,16} opened a WITHER door!$");
	private static final Pattern BLOOD_KEY = Pattern.compile("^(?:\\[[^]]*?] )?\\w{1,16} has obtained Blood Key!$|^A Blood Key was picked up!$");
	private static final String BLOOD_OPENED = "The BLOOD DOOR has been opened!";
	private static final int ORIGIN = -185, TILE = 32, TILES = 6;

	public record Door(BlockPos pos, boolean blood) {
		AABB box() {
			return new AABB(pos.getX() - 1, 69, pos.getZ() - 1, pos.getX() + 2, 73, pos.getZ() + 2);
		}
	}

	private static final List<Door> DOORS = new ArrayList<>();
	private static final Set<Long> VISITED = new HashSet<>();
	private static final Set<Integer> ANNOUNCED = new HashSet<>();
	private static ClientLevel level;
	private static int witherKeys;
	private static boolean bloodKey, bloodOpened;
	private static ArmorStand key;
	private static int ticks;

	private DoorsAndKeys() {
	}

	/** Every client tick: note the room tile you're in, find the doors next to it, and look for a key. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			reset();
		}
		DoorsAndKeysConfig cfg = DoorsAndKeysConfig.get();
		if (!cfg.enabled || mc.level == null || mc.player == null || !DungeonLocation.inDungeon() || DungeonLocation.inBoss()) return;
		int tx = tile(mc.player.getX()), tz = tile(mc.player.getZ());
		if (tx >= 0 && tz >= 0 && tx < TILES && tz < TILES) VISITED.add(key(tx, tz));
		if (++ticks % 10 != 0) return;
		DOORS.clear();
		for (long v : VISITED) {
			int x = (int) (v >> 32), z = (int) v;
			int cx = ORIGIN + x * TILE, cz = ORIGIN + z * TILE;
			door(mc.level, cx + 16, cz);
			door(mc.level, cx - 16, cz);
			door(mc.level, cx, cz + 16);
			door(mc.level, cx, cz - 16);
		}
		if (key != null && (!key.isAlive() || key.isRemoved())) key = null;
		if (cfg.keys && key == null) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof ArmorStand stand) || !stand.hasCustomName()) continue;
				String name = stand.getCustomName().getString();
				if (!name.equals("Wither Key") && !name.equals("Blood Key")) continue;
				key = stand;
				if (cfg.announce && ANNOUNCED.add(stand.getId())) {
					Alert.show(name.equals("Wither Key") ? "§8§lWither Key §7spawned!" : "§c§lBlood Key §7spawned!", null, Alert.Sound.PLING);
				}
				break;
			}
		}
	}

	private static void door(ClientLevel level, int x, int z) {
		Block b = level.getBlockState(new BlockPos(x, 69, z)).getBlock();
		boolean blood = b == Blocks.RED_TERRACOTTA;
		if (!blood && b != Blocks.COAL_BLOCK) return;
		if (blood && bloodOpened) return;
		Door d = new Door(new BlockPos(x, 69, z), blood);
		if (!DOORS.contains(d)) DOORS.add(d);
	}

	private static int tile(double v) {
		return (int) Math.floor((v - ORIGIN + 16) / TILE);
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** Every chat line: keys picked up and doors opened. */
	public static void onChat(String line) {
		if (!DungeonLocation.inDungeon()) return;
		if (WITHER_KEY.matcher(line).matches()) witherKeys++;
		else if (WITHER_DOOR.matcher(line).matches()) witherKeys = Math.max(0, witherKeys - 1);
		else if (BLOOD_KEY.matcher(line).matches()) bloodKey = true;
		else if (line.equals(BLOOD_OPENED)) {
			bloodKey = false;
			bloodOpened = true;
		}
	}

	/** Whether the party holds a key for this door. */
	public static boolean openable(Door d) {
		return d.blood() ? bloodKey : witherKeys > 0;
	}

	public static void reset() {
		DOORS.clear();
		VISITED.clear();
		ANNOUNCED.clear();
		witherKeys = 0;
		bloodKey = bloodOpened = false;
		key = null;
	}

	/** Test hooks. */
	public static List<Door> doors() {
		return DOORS;
	}

	public static ArmorStand key() {
		return key;
	}

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		DoorsAndKeysConfig cfg = DoorsAndKeysConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon() || DungeonLocation.inBoss() || (DOORS.isEmpty() && key == null)) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.doors) {
				for (Door d : DOORS) {
					boolean open = openable(d);
					batch.filled(d.box(), open ? 0x40F2E9E9 : 0x50FF2E2E, true);
					batch.outline(d.box(), open ? 0xFFFFFFFF : 0xFFFF2E2E, 2.5f, true);
				}
			}
			if (cfg.keys && key != null) {
				boolean wither = key.getCustomName().getString().equals("Wither Key");
				Vec3 at = key.position().add(0, 1.2, 0);
				AABB box = new AABB(at.x - 0.4, at.y, at.z - 0.4, at.x + 0.4, at.y + 0.8, at.z + 0.4);
				batch.filled(box, wither ? 0x80000000 : 0x60FF2E2E, true);
				batch.outline(box, wither ? 0xFFB3121B : 0xFFFF2E2E, 2.5f, true);
				if (cfg.tracer) batch.line(WorldRenderer.crosshair(batch.camera), at.add(0, 0.4, 0), 0xFFFF2E2E, 2f, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Doors and keys failed to draw", e);
		}
	}
}
