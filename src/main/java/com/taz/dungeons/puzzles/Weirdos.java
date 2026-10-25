package com.taz.dungeons.puzzles;

import com.taz.dungeons.secrets.DungeonRooms;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Three Weirdos: the chest of the one telling the truth in red, the liars' in black. From Odin. */
final class Weirdos {
	private static final List<Pattern> RIGHT = patterns("The reward is not in my chest!",
			"At least one of them is lying, and the reward is not in .+'s chest.?", "My chest doesn't have the reward. We are all telling the truth.?",
			"My chest has the reward and I'm telling the truth!", "The reward isn't in any of our chests.?",
			"Both of them are telling the truth. Also, .+ has the reward in their chest.?");
	private static final List<Pattern> WRONG = patterns("One of us is telling the truth!", "They are both telling the truth. The reward isn't in .+'s chest.",
			"We are all telling the truth!", ".+ is telling the truth and the reward is in his chest.",
			"My chest doesn't have the reward. At least one of the others is telling the truth!", "One of the others is lying.",
			"They are both telling the truth, the reward is in .+'s chest.", "They are both lying, the reward is in my chest!", "The reward is in my chest.",
			"The reward is not in my chest. They are both lying.", ".+ is telling the truth.", "My chest has the reward.");
	private static BlockPos right;
	private static final Set<BlockPos> WRONG_CHESTS = new HashSet<>();

	private Weirdos() {
	}

	private static List<Pattern> patterns(String... regexes) {
		return java.util.Arrays.stream(regexes).map(Pattern::compile).toList();
	}

	static void onNpc(String npc, String message) {
		if (!Puzzles.cfg().weirdos) return;
		boolean truth = RIGHT.stream().anyMatch(p -> p.matcher(message).matches());
		if (!truth && WRONG.stream().noneMatch(p -> p.matcher(message).matches())) return;
		DungeonRooms.Room room = Puzzles.room("Three Weirdos");
		Minecraft mc = Minecraft.getInstance();
		if (room == null || mc.level == null) return;
		Entity who = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand && e.getName().getString().equals(npc)) {
				who = e;
				break;
			}
		}
		if (who == null) return;
		BlockPos rel = room.toRelative(new BlockPos((int) who.getX() - 1, 69, (int) who.getZ() - 1));
		BlockPos chest = room.toReal(rel.offset(1, 0, 0));
		if (truth) {
			right = chest;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1f));
		} else {
			WRONG_CHESTS.add(chest);
		}
	}

	/** Test hooks. */
	static BlockPos right() {
		return right;
	}

	static void reset() {
		right = null;
		WRONG_CHESTS.clear();
	}

	static void render(WorldRenderer.Batch batch) {
		if (Puzzles.room("Three Weirdos") == null) return;
		if (right != null) {
			batch.filled(new AABB(right), 0x70FF2E2E, false);
			batch.outline(new AABB(right), Puzzles.FIRST, 2.5f, false);
		}
		for (BlockPos p : WRONG_CHESTS) {
			batch.filled(new AABB(p), 0xA0000000, false);
			batch.outline(new AABB(p), Puzzles.THIRD, 2f, false);
		}
	}
}
