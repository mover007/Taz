package com.taz.dungeons.highlight;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.dungeons.util.DungeonParty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Starred Mobs: in a dungeon's clear, the starred mobs (their nameplate says "✯ ... ❤"; the mob is the entity just
 * before the nameplate, three before for a Withermancer, or the one under it), the mini-bosses (Shadow Assassin, Lost
 * Adventurer, Diamond Guy, King Midas) and the bonus bats glow through walls. From Odin's Highlight and NoammAddons'
 * Star Mob ESP.
 */
public final class StarredMobs {
	private static final List<String> MOB_NAMES = List.of("Lurker", "Dreadlord", "Souleater", "Zombie", "Skeleton", "Skeletor", "Sniper",
			"Super Archer", "Spider", "Fels", "Withermancer", "Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer");
	private static final Set<String> MINIBOSSES = Set.of("Shadow Assassin", "Lost Adventurer", "Diamond Guy", "King Midas");
	private static final Set<Float> BAT_HEALTH = Set.of(100f, 200f, 400f, 800f);
	private static final int STARRED = 0xFF3030, FEL = 0xFF8A8A, BAT = 0xFFFFFF;
	private static final Set<Integer> GLOWING = new HashSet<>();
	private static final Set<Integer> CHECKED = new HashSet<>();
	private static ClientLevel level;
	private static int ticks;

	private StarredMobs() {
	}

	/** Every client tick: look over the nameplates now and then. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			GLOWING.clear();
			CHECKED.clear();
		}
		StarredMobsConfig cfg = StarredMobsConfig.get();
		if (!cfg.enabled || mc.level == null || mc.player == null || !DungeonLocation.inDungeon() || DungeonLocation.inBoss() || ++ticks % 5 != 0) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand stand && stand.hasCustomName()) {
				String name = stand.getCustomName().getString();
				boolean starred = name.contains("✯") && name.endsWith("❤");
				if (starred && cfg.starred) mark(mc, stand, name);
				else if (!starred && cfg.hideNames && stand.isInvisible() && MOB_NAMES.stream().anyMatch(name::contains)) stand.setCustomNameVisible(false);
			} else if (e instanceof Player p && p != mc.player && cfg.starred && MINIBOSSES.contains(p.getName().getString())) {
				GLOWING.add(p.getId());
			}
		}
		GLOWING.removeIf(id -> {
			Entity e = mc.level.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	/** A starred nameplate: find its mob. */
	private static void mark(Minecraft mc, ArmorStand stand, String name) {
		if (!CHECKED.add(stand.getId())) return;
		int offset = name.toUpperCase().contains("WITHERMANCER") ? 3 : 1;
		Entity mob = mc.level.getEntity(stand.getId() - offset);
		if (mob != null && !(mob instanceof ArmorStand)) {
			GLOWING.add(mob.getId());
			return;
		}
		for (Entity e : mc.level.getEntities(stand, stand.getBoundingBox().move(0, -1, 0))) {
			if (e instanceof ArmorStand || e instanceof WitherBoss || e instanceof AbstractArrow || e == mc.player || GLOWING.contains(e.getId())) continue;
			if (e instanceof Player p && p.isInvisible()) continue;
			GLOWING.add(e.getId());
			return;
		}
	}

	/** The glow colour for an entity, or -1. Called for every entity every frame, so kept cheap. */
	public static int glow(Entity e) {
		StarredMobsConfig cfg = StarredMobsConfig.get();
		if (!cfg.enabled || !DungeonLocation.inDungeon()) return -1;
		if (GLOWING.contains(e.getId())) return e instanceof EnderMan ? FEL : STARRED;
		if (cfg.bats && e instanceof Bat bat && !DungeonLocation.inBoss() && BAT_HEALTH.contains(bat.getMaxHealth())) return BAT;
		if (cfg.teammates && e instanceof Player p && p != Minecraft.getInstance().player) {
			String c = DungeonParty.classOf(p.getName().getString());
			if (c != null) return switch (c) {
				case "Archer" -> 0xAA0000;
				case "Berserk" -> 0xFFAA00;
				case "Healer" -> 0xAA00AA;
				case "Mage" -> 0x00AAAA;
				default -> 0x00AA00;
			};
		}
		return -1;
	}

	/** Test hook. */
	public static boolean glowing(Entity e) {
		return GLOWING.contains(e.getId());
	}
}
