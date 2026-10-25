package com.taz.xpassist.npc;

import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.util.Chat;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks one NPC: makes the NPC glow through walls (vanilla outline), draws an always-on-top box,
 * beam and label at its location, and shows distance/direction on the HUD.
 */
public final class NpcTracker {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static Npc target;
	private static int glowEntityId = -1;
	private static boolean arrived;
	private static int ticks;

	private NpcTracker() {
	}

	public static Npc target() {
		return target;
	}

	public static void track(Npc npc) {
		target = npc;
		glowEntityId = -1;
		arrived = false;
		NeuRepo neu = DataManager.neu();
		if (!npc.hasLocation()) {
			Chat.error("The NEU repo has no location for " + npc.name() + ".");
			target = null;
			return;
		}
		String islandName = neu != null ? neu.islandName(npc.island()) : npc.island();
		Component message = Component.literal("Tracking ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(npc.name()).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" in " + islandName + " at " + npc.x() + ", " + npc.y() + ", " + npc.z() + " ").withStyle(ChatFormatting.GRAY))
			.append(Chat.button("Stop", "/taz untrack", "Stop tracking"));
		Chat.send(message);
		String warp = warpCommand(npc);
		if (warp != null && !npc.island().equals(SkyblockLocation.island())) {
			Chat.send(Component.literal("Fastest way there: ").withStyle(ChatFormatting.GRAY)
				.append(Chat.button(warp, warp, "Click to run " + warp)));
		}
		for (String line : npc.lore()) {
			Chat.info(line);
		}
		// Everything worth buying here, so one trip covers it all.
		List<com.taz.xpassist.xp.XpTask> shopping = com.taz.xpassist.xp.TripPlanner.tasksAtNpc(
			DataManager.plan(), DataManager.result(), npc.id());
		if (!shopping.isEmpty()) {
			Chat.send(Component.literal("While you're there (" + shopping.size() + "):").withStyle(ChatFormatting.YELLOW));
			com.taz.xpassist.screen.PlanScreen.printShoppingList(shopping);
		}
	}

	public static void untrack() {
		if (target != null) {
			Chat.info("Stopped tracking " + target.name() + ".");
		}
		target = null;
		glowEntityId = -1;
	}

	public static boolean isHighlighted(Entity entity) {
		return target != null && glowEntityId != -1 && ModConfig.get().tracker.trackerGlow && entity.getId() == glowEntityId;
	}

	/** True when we are on the NPC's island, or when we cannot tell (so the marker still shows). */
	public static boolean onTargetIsland() {
		String island = SkyblockLocation.island();
		return target != null && (island == null || island.equals(target.island()));
	}

	public static String warpCommand(Npc npc) {
		NeuRepo neu = DataManager.neu();
		if (neu == null || !npc.hasLocation()) {
			return null;
		}
		NeuRepo.Warp warp = neu.bestWarp(npc.island(), npc.x(), npc.y(), npc.z());
		return warp != null ? "/warp " + warp.name() : null;
	}

	private static Vec3 targetPos() {
		return new Vec3(target.x() + 0.5, target.y(), target.z() + 0.5);
	}

	public static void tick(Minecraft minecraft) {
		if (target == null || minecraft.level == null || minecraft.player == null) {
			glowEntityId = -1;
			return;
		}
		if (++ticks % 10 != 0) {
			return;
		}
		if (!onTargetIsland()) {
			glowEntityId = -1;
			return;
		}
		Entity entity = findEntity(minecraft);
		glowEntityId = entity != null ? entity.getId() : -1;
		double distance = minecraft.player.position().distanceTo(targetPos());
		if (!arrived && distance < 4) {
			arrived = true;
			Chat.send(Component.literal("You found " + target.name() + "! ").withStyle(ChatFormatting.GREEN)
				.append(Chat.button("Stop tracking", "/taz untrack", "Stop tracking this NPC")));
		} else if (arrived && distance > 12) {
			arrived = false;
		}
	}

	/** Hypixel NPCs are usually fake players or mobs standing on the recorded block, with a name tag armor stand above. */
	private static Entity findEntity(Minecraft minecraft) {
		Vec3 pos = targetPos();
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : minecraft.level.getEntities(minecraft.player, new AABB(pos, pos).inflate(3, 3, 3),
			e -> e instanceof LivingEntity && !(e instanceof ArmorStand))) {
			double distance = horizontalDistance(entity.position(), pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = entity;
			}
		}
		if (best != null) {
			return best;
		}
		// Fall back to the name tag: find the armor stand named like the NPC, then the mob under it.
		String wanted = TextUtil.normalize(target.name());
		List<Entity> tags = new ArrayList<>();
		for (Entity entity : minecraft.level.getEntities(minecraft.player, new AABB(pos, pos).inflate(8, 6, 8),
			e -> e instanceof ArmorStand && e.hasCustomName())) {
			if (TextUtil.normalize(entity.getCustomName().getString()).contains(wanted)) {
				tags.add(entity);
			}
		}
		for (Entity tag : tags) {
			for (Entity entity : minecraft.level.getEntities(minecraft.player, tag.getBoundingBox().inflate(1, 3, 1),
				e -> e instanceof LivingEntity)) {
				if (entity != tag && !(entity instanceof ArmorStand && !entity.isInvisible())) {
					return entity;
				}
			}
			if (!tag.isInvisible()) {
				return tag;
			}
		}
		return null;
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Called from Fabric's END_MAIN level render hook. */
	public static void drawMarker(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ModConfig config = ModConfig.get();
		if (target == null || minecraft.player == null || !config.tracker.trackerBeam || !onTargetIsland()) {
			return;
		}
		Vec3 base = targetPos();
		double distance = minecraft.player.position().distanceTo(base);
		AABB box = new AABB(target.x(), target.y(), target.z(), target.x() + 1, target.y() + 2, target.z() + 1);
		double beam = distance > 6 ? Math.min(120, 20 + distance) : 0;
		String label = target.name() + " (" + Math.round(distance) + "m)";
		MarkerRenderer.render(context, new MarkerRenderer.Marker(box, beam, label, config.tracker.rgb(), config.tracker.trackerTracer));
	}

	/** HUD lines for the tracker, or an empty list when nothing is tracked. */
	public static List<String> hudLines(Minecraft minecraft) {
		List<String> lines = new ArrayList<>();
		if (target == null || minecraft.player == null) {
			return lines;
		}
		NeuRepo neu = DataManager.neu();
		String islandName = neu != null ? neu.islandName(target.island()) : target.island();
		lines.add("§bTracking §f" + target.name() + " §8(" + islandName + ")");
		if (!onTargetIsland()) {
			String warp = warpCommand(target);
			lines.add("§7Go to §e" + islandName + (warp != null ? " §7with §a" + warp : ""));
			return lines;
		}
		Vec3 pos = targetPos();
		Vec3 player = minecraft.player.position();
		double dx = pos.x - player.x;
		double dz = pos.z - player.z;
		double dy = pos.y - player.y;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
		float relative = Mth.wrapDegrees(targetYaw - minecraft.player.getYRot());
		int index = Math.floorMod(Math.round(relative / 45F), 8);
		String vertical = Math.abs(dy) > 4 ? (dy > 0 ? " §7(" + Math.round(dy) + " up)" : " §7(" + Math.round(-dy) + " down)") : "";
		lines.add("§e" + Math.round(distance) + "m §f" + ARROWS[index] + vertical + " §8" + target.x() + ", " + target.y() + ", " + target.z());
		if (glowEntityId != -1) {
			lines.add("§aNPC highlighted");
		}
		return lines;
	}
}
