package com.taz.dungeons.puzzles;

import com.taz.render.WorldRenderer;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blaze: the blazes in the order to kill them (lowest health first upstairs, highest first downstairs): the next one
 * red, then pale red, dark red, the rest faint, with a line from each to the next. From Odin.
 */
final class Blaze {
	private record Tag(int id, int maxHealth) {
	}

	private static final Pattern HEALTH = Pattern.compile("^\\[Lv\\d+] .*?Blaze [\\d,]+/([\\d,]+)❤$");
	private static final List<Tag> BLAZES = new ArrayList<>();
	private static boolean seen;
	private static int ticks;

	private Blaze() {
	}

	private static boolean inRoom() {
		return Puzzles.room("Lower Blaze", "Higher Blaze") != null;
	}

	/** Every client tick in a blaze room: read the blazes' health tags. */
	static void tick(Minecraft mc) {
		if (!Puzzles.cfg().blaze || !inRoom() || ++ticks % 5 != 0) return;
		boolean changed = BLAZES.removeIf(t -> {
			Entity e = mc.level.getEntity(t.id());
			return e == null || !e.isAlive();
		});
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName() || BLAZES.stream().anyMatch(t -> t.id() == e.getId())) continue;
			Matcher m = HEALTH.matcher(e.getCustomName().getString());
			if (!m.matches()) continue;
			BLAZES.add(new Tag(e.getId(), Integer.parseInt(m.group(1).replace(",", ""))));
			seen = changed = true;
		}
		if (changed) sort(Puzzles.room("Lower Blaze", "Higher Blaze").data.name());
		if (seen && BLAZES.isEmpty()) done();
	}

	static void sort(String room) {
		if ("Lower Blaze".equals(room)) BLAZES.sort(Comparator.comparingInt(Tag::maxHealth).reversed());
		else BLAZES.sort(Comparator.comparingInt(Tag::maxHealth));
	}

	/** A blaze died (its tag is the entity after it). */
	static void onEntityEvent(Entity e, byte id) {
		if (id != 3 || !(e instanceof net.minecraft.world.entity.monster.Blaze) || !inRoom()) return;
		if (BLAZES.removeIf(t -> t.id() == e.getId() + 1) && BLAZES.isEmpty()) done();
	}

	private static void done() {
		seen = false;
		String room = Puzzles.room("Lower Blaze", "Higher Blaze") == null ? "Blaze" : Puzzles.room("Lower Blaze", "Higher Blaze").data.name();
		Puzzles.solved(room);
		if (Puzzles.cfg().blazeParty) TazChat.party("Blaze puzzle solved!");
	}

	/** The blazes and their tags, while the solver shows them. */
	static boolean hides(Entity e) {
		return (e instanceof net.minecraft.world.entity.monster.Blaze || BLAZES.stream().anyMatch(t -> t.id() == e.getId())) && inRoom();
	}

	/** Test hook. */
	static int count() {
		return BLAZES.size();
	}

	static void reset() {
		BLAZES.clear();
		seen = false;
	}

	static void render(WorldRenderer.Batch batch) {
		Minecraft mc = Minecraft.getInstance();
		if (BLAZES.isEmpty() || !inRoom()) return;
		AABB prev = null;
		for (int i = 0; i < BLAZES.size(); i++) {
			Entity e = mc.level.getEntity(BLAZES.get(i).id());
			if (e == null) continue;
			AABB box = e.getBoundingBox().inflate(0.5, 1, 0.5).move(0, -1, 0);
			int color = i == 0 ? Puzzles.FIRST : i == 1 ? Puzzles.SECOND : i == 2 ? Puzzles.THIRD : Puzzles.OTHER;
			batch.filled(box, (color & 0x00FFFFFF) | (i < 3 ? 0x50000000 : 0x20000000), false);
			batch.outline(box, color, i == 0 ? 3f : 2f, false);
			if (prev != null && i <= Puzzles.cfg().blazeLines) batch.line(prev.getCenter(), box.getCenter(), color, 2f, false);
			prev = box;
		}
	}
}
