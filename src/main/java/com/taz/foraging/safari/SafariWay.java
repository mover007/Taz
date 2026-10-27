package com.taz.foraging.safari;

import com.taz.foraging.safari.Critters.Biome;
import com.taz.foraging.safari.Critters.Critter;
import com.taz.foraging.safari.Critters.QuestItem;
import com.taz.hud.TazTheme;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Show the way": a path along the Safari map ({@link SafariMap}) to what to do next, like SkyHanni's navigation. In
 * this order: the place a quest item you carry is used at (the Ritual for Soothing Incense, the Gem Podiums for a gem,
 * the Birdfeeder for bait) while what it brings is still to catch; the spots of critters you still need in your biome
 * (Hideonwalls, Areitas, Hideyho's hiding places), skipping the ones you've been to; the next biome once yours is
 * done; and otherwise the nearest floor drop, where quest items come from.
 */
public final class SafariWay {
	/** Somewhere to go: its name, why, where (the nearest of these), and whether they're spots to check one by one. */
	public record Goal(String name, String why, List<Vec3> targets, boolean spots) {
	}

	private static final double ARRIVED = 4;
	private static final Map<String, String> PLACE_NAMES = Map.of("Luigis Mansion 64", "Haunted mansion");

	private static Goal goal;
	private static SafariMap.Walk walk;
	/** Spots you've checked this run. */
	private static final Set<Vec3> VISITED = new HashSet<>();
	private static int ticks;

	private SafariWay() {
	}

	/** Every 5 ticks while you're in the Safari (SafariHelper); the way is worked out every 10. */
	public static void tick(Minecraft mc, SafariConfig cfg) {
		if (!cfg.showWay) {
			clear();
			return;
		}
		if (++ticks % 2 != 0) return;
		Vec3 me = mc.player.position();
		if (goal != null && goal.spots()) {
			for (Vec3 t : goal.targets()) if (t.distanceToSqr(me) < ARRIVED * ARRIVED) VISITED.add(t);
		}
		goal = null;
		walk = null;
		for (List<Goal> tier : goals(cfg)) {
			List<Vec3> targets = new ArrayList<>();
			List<Goal> owners = new ArrayList<>();
			for (Goal g : tier) {
				for (Vec3 t : g.targets()) {
					if (g.spots() && VISITED.contains(t)) continue;
					targets.add(t);
					owners.add(g);
				}
			}
			SafariMap.Walk w = SafariMap.path(me, targets);
			if (w == null) continue;
			goal = owners.get(w.target());
			walk = w;
			return;
		}
	}

	private static List<List<Goal>> goals(SafariConfig cfg) {
		List<Goal> items = new ArrayList<>(), spots = new ArrayList<>(), next = new ArrayList<>(), drops = new ArrayList<>();
		for (String name : SafariHelper.questItems().keySet()) {
			QuestItem q = Critters.quest(name);
			if (q.place() == null || q.brings().stream().noneMatch(c -> SafariHelper.toDo(Critters.named(c)))) continue;
			items.add(new Goal(PLACE_NAMES.getOrDefault(q.place(), q.place()), "use your " + q.name(), named(q.place()), false));
		}
		Biome here = SafariHelper.biome();
		for (Critter c : Critters.ALL) {
			if (here != null && c.biome() != here || !SafariHelper.toDo(c)) continue;
			if (Critters.ON_MAP.contains(c.name())) spots.add(new Goal(c.name(), c.hint(), named(c.name()), true));
			if (c.name().equals("Hideyho")) {
				spots.add(new Goal("Hideyho", "his hiding spots", SafariMap.matching(n -> n.tagged("hideyho")).stream()
						.map(SafariMap.Node::pos).toList(), true));
			}
		}
		if (here != null && SafariHelper.left(here) == 0) {
			Biome b = SafariHelper.nextBiome();
			if (b != null) next.add(new Goal(b.label + " Biome", "yours is done", named(b.label + " Biome"), false));
		}
		if (cfg.floorDrops) drops.add(new Goal("Floor drop", "quest items and Safari Essence", SafariFinds.drops(), false));
		return List.of(items, spots, next, drops);
	}

	private static List<Vec3> named(String name) {
		return SafariMap.matching(n -> n.is(name)).stream().map(SafariMap.Node::pos).toList();
	}

	public static void clear() {
		goal = null;
		walk = null;
	}

	public static void reset() {
		clear();
		VISITED.clear();
	}

	/** Where the way goes, or null. */
	public static Goal goal() {
		return goal;
	}

	public static SafariMap.Walk walk() {
		return walk;
	}

	/** "Go to Ritual (use your Soothing Incense) 45m" for the HUD, or null. */
	public static String hudLine() {
		if (goal == null || walk == null) return null;
		return "§7Go to §f" + goal.name() + " §7(" + goal.why() + ") §f" + Math.round(walk.length()) + "m";
	}

	/** The path from your crosshair along the map, and a beam on where it ends. */
	public static void render(WorldRenderer.Batch batch) {
		SafariMap.Walk w = walk;
		Goal g = goal;
		if (w == null || g == null) return;
		List<Vec3> points = w.points();
		Vec3 end = points.get(points.size() - 1);
		double left = Math.sqrt(end.distanceToSqr(batch.camera));
		if (left < ARRIVED) return;
		int line = (TazTheme.RED & 0xFFFFFF) | 0xD0000000;
		Vec3 last = WorldRenderer.crosshair(batch.camera);
		for (Vec3 p : points) {
			Vec3 at = p.add(0, 0.2, 0);
			batch.line(last, at, line, 3.0f, true);
			last = at;
		}
		batch.line(end, end.add(0, 40, 0), (TazTheme.RED & 0xFFFFFF) | 0xA0000000, 4.0f, true);
		batch.label(end.add(0, 2.2, 0), g.name() + "  " + Math.round(w.length()) + "m", TazTheme.RED, true);
	}
}
