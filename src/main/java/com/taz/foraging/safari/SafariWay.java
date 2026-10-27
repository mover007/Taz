package com.taz.foraging.safari;

import com.taz.foraging.safari.Critters.Biome;
import com.taz.foraging.safari.Critters.Critter;
import com.taz.foraging.safari.SafariSpots.Task;
import com.taz.hud.TazTheme;
import com.taz.render.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Show the way": a route through the whole run, one biome at a time, walked along the Safari map ({@link SafariMap})
 * like SkyHanni's navigation. In the biome being worked it goes, nearest first within each step:
 * <ol>
 * <li>to a critter of that biome you still need that's in view (its name tag is loaded); other biomes' critters wait
 * for their own turn;
 * <li>to anything you can do now ({@link SafariSpots}): a bee nest, a Hideonwall perch, a wall to break, a bed, a
 * mound, a critter's spot, or the Ritual, Gem Podiums or Birdfeeder when you carry what they need;
 * <li>round the biome to find the critters that roam it, and to floor drops when a critter needs a quest item you
 * don't have.
 * </ol>
 * When the biome has nothing left (the Macaw doesn't count: it may not come at all), the route moves on to the next.
 */
public final class SafariWay {
	/** Somewhere to go: what for, where, and how near counts as having been there (0: it goes when it's done). */
	public record Goal(String name, String why, Vec3 pos, double reach) {
	}

	private static final double ARRIVED = 4, SEARCHED = 10, DROP_CHECKED = 6;
	/** Floor drops this close are worth grabbing on the way, needed or not. */
	private static final double DROP_NEARBY = 16;

	private static Biome zone;
	/** The "Your biome" setting the route was worked out with: changing it starts the choice over. */
	private static SafariConfig.Zone zoneSetting;
	private static Goal goal;
	private static SafariMap.Walk walk;
	private static List<Goal> marks = List.of();
	/** Spots you've been to this run. */
	private static final Set<Vec3> VISITED = new HashSet<>();
	private static boolean wumpaWaking;
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
		zone = pickZone(mc, cfg);
		goal = null;
		walk = null;
		marks = List.of();
		if (zone == null) return;
		List<List<Goal>> plan = plan(mc, zone);
		marks = plan.get(1);
		for (List<Goal> step : plan) {
			SafariMap.Walk w = SafariMap.path(mc.level, mc.player.position(), step.stream().map(Goal::pos).toList());
			if (w == null) continue;
			goal = step.get(w.target());
			walk = w;
			return;
		}
	}

	/**
	 * The biome to work: the one you picked; else your Hunting Hotspot (each player in a party is given a different one,
	 * so going to yours splits the party by itself, and it takes over when Hypixel names it a few seconds in); else the
	 * one already being worked, where you are, and the one with most left.
	 */
	private static Biome pickZone(Minecraft mc, SafariConfig cfg) {
		if (cfg.zone != zoneSetting) {
			zoneSetting = cfg.zone;
			zone = null;
		}
		Biome picked = cfg.zone.biome();
		if (picked != null && hasWork(mc, picked)) return picked;
		Biome hot = SafariHelper.hotspot();
		if (hot != null && hasWork(mc, hot)) return hot;
		if (zone != null && hasWork(mc, zone)) return zone;
		Biome here = SafariHelper.biome();
		if (here != null && hasWork(mc, here)) return here;
		Biome best = null;
		for (Biome b : Biome.values()) {
			if (hasWork(mc, b) && (best == null || left(b) > left(best))) best = b;
		}
		return best;
	}

	private static boolean hasWork(Minecraft mc, Biome b) {
		return plan(mc, b).stream().anyMatch(step -> !step.isEmpty());
	}

	/** Critters still to catch in a biome, not counting the Macaw. */
	private static int left(Biome b) {
		return (int) Critters.in(b).stream().filter(c -> !c.name().equals("Macaw") && SafariHelper.toDo(c)).count();
	}

	/** The three steps of the route through a biome, each a list of places (the nearest one is gone to). */
	static List<List<Goal>> plan(Minecraft mc, Biome z) {
		Vec3 me = mc.player.position();
		List<Goal> now = new ArrayList<>(), tasks = new ArrayList<>(), search = new ArrayList<>();
		for (SafariHelper.Sighting s : SafariHelper.sightings()) {
			if (!SafariHelper.toDo(s.critter()) && !s.sparkling()) continue;
			Entity body = s.body() != null ? s.body() : s.label();
			if (s.critter().biome() != z) continue;
			// A Scrappy's armour stops capsules until it's had its food 4 times; one in view without the food means
			// going for it first.
			if (s.critter().name().equals("Scrappy") && !SafariHelper.scrappyOut()) {
				String food = SafariHelper.scrappyFoodCarried();
				if (food != null) now.add(new Goal("Scrappy", "feed it " + food + " (4 times), then throw", body.position(), 0));
				else now.addAll(scrappyFood("for the Scrappy you found"));
				continue;
			}
			now.add(new Goal(s.critter().name(), SafariHelper.scrappyOut() && s.critter().name().equals("Scrappy")
					? "it's out of its shell: catch it!" : SafariSpots.howToCatch(s.critter().name()), body.position(), 0));
		}
		Set<String> roaming = new LinkedHashSet<>(), missing = new LinkedHashSet<>();
		for (Critter c : Critters.in(z)) {
			if (!SafariHelper.toDo(c)) continue;
			boolean optional = c.name().equals("Macaw");
			if (SafariSpots.ROAMING.contains(c.name())) roaming.add(c.name());
			Task t = SafariSpots.task(c.name());
			if (t == null || c.name().equals("Wumpa") && !wumpaReady()) continue;
			// Not until you have it all (4 Soothing Incense for the 4 candles, all 3 gems): floor drops first.
			List<String> toFind = SafariHelper.stillToFind(c.name());
			if (!toFind.isEmpty()) {
				// Stays the goal until you have the food: wait at the pond for fish to come down if need be.
				if (c.name().equals("Scrappy")) tasks.addAll(scrappyFood("for a Scrappy"));
				else if (!optional) missing.addAll(toFind);
				continue;
			}
			double reach = t.needs() != null && !c.name().equals("Troodon") ? 0 : ARRIVED;
			String what = summoned(c.name()) ? SafariHelper.progressHint(c) : t.what();
			for (Vec3 p : t.spots()) {
				if (stillThere(mc.level, c.name(), p)) tasks.add(new Goal(c.name(), what, p, reach));
			}
		}
		for (Vec3 p : SafariFinds.drops()) {
			if (p.distanceTo(me) <= DROP_NEARBY) tasks.add(new Goal("Floor drop", "grab it", p, 0));
		}
		if (!roaming.isEmpty()) {
			String names = String.join(", ", roaming.stream().limit(3).toList()) + (roaming.size() > 3 ? "..." : "");
			List<Goal> points = SafariSpots.searchPoints(z).stream().map(p -> new Goal("Search the " + z.label, names, p, SEARCHED)).toList();
			// Walked it all and still some to find: round again.
			if (points.stream().allMatch(g -> VISITED.contains(g.pos()))) points.forEach(g -> VISITED.remove(g.pos()));
			search.addAll(points);
		}
		if (!missing.isEmpty()) {
			String why = "for " + String.join(", ", missing);
			for (Vec3 p : SafariFinds.drops()) {
				if (SafariMap.zoneAt(p) == z) search.add(new Goal("Floor drop", why, p, 0));
			}
			for (Vec3 p : SafariSpots.dropSpots(z)) search.add(new Goal("Floor drop spot", why, p, DROP_CHECKED));
		}
		return List.of(fresh(now, me), fresh(tasks, me), fresh(search, me));
	}

	/** Leaves out places you've been to, noting the ones you're at now. */
	private static List<Goal> fresh(List<Goal> goals, Vec3 me) {
		List<Goal> out = new ArrayList<>();
		for (Goal g : goals) {
			if (g.reach() > 0 && (VISITED.contains(g.pos()) || g.pos().distanceToSqr(me) < g.reach() * g.reach())) {
				VISITED.add(g.pos());
				continue;
			}
			out.add(g);
		}
		return out;
	}

	/** Whether a spot still has something: a bee nest (not the hive decoy), a wall not yet broken. */
	private static boolean stillThere(ClientLevel level, String critter, Vec3 p) {
		BlockPos pos = BlockPos.containing(p);
		if (!level.isLoaded(pos)) return true;
		return switch (critter) {
			case "Snoozle", "Troodon" -> !level.getBlockState(pos).isAir();
			case "Honeybug" -> BlockPos.betweenClosedStream(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))
					.anyMatch(b -> level.getBlockState(b).is(Blocks.BEE_NEST));
			default -> true;
		};
	}

	/** The Doomspiral once all 4 candles are lit, the Gemzies once all 3 gems are placed: they're out, go catch them. */
	private static boolean summoned(String critter) {
		return critter.equals("Doomspiral") ? SafariHelper.candlesLit() >= 4 : critter.equals("Gemzie") && SafariHelper.gemsPlaced().size() == 3;
	}

	/** Where to get the food this run's Scrappies may eat (the ones not refused yet). */
	private static List<Goal> scrappyFood(String forWhat) {
		List<Goal> out = new ArrayList<>();
		for (String food : SafariHelper.scrappyFoods()) {
			for (Vec3 p : SafariSpots.foodSources(food)) out.add(new Goal(food, forWhat + ": " + SafariSpots.howToHarvest(food), p, 0));
		}
		return out;
	}

	/** The Wumpa wakes once every other Icy critter is caught ("You hear the sound of massive footsteps"). */
	private static boolean wumpaReady() {
		return wumpaWaking || Critters.in(Biome.ICY).stream().filter(c -> !c.name().equals("Wumpa")).noneMatch(SafariHelper::toDo);
	}

	public static void onWumpaWaking() {
		wumpaWaking = true;
	}

	public static void clear() {
		goal = null;
		walk = null;
		marks = List.of();
	}

	public static void reset() {
		clear();
		zone = null;
		VISITED.clear();
		wumpaWaking = false;
	}

	/** The biome the route is working, or null. */
	public static Biome zone() {
		return zone;
	}

	/** Where the way goes, or null. */
	public static Goal goal() {
		return goal;
	}

	public static SafariMap.Walk walk() {
		return walk;
	}

	/** "Next: Honeybug - punch the bee nest 23m" for the HUD, with the biome when it isn't the one you're in; or null. */
	public static String hudLine() {
		if (goal == null || walk == null) return null;
		String elsewhere = zone != null && zone != SafariHelper.biome() ? "§7(" + zone.label + ") " : "";
		return "§7Next: " + elsewhere + "§f" + goal.name() + " §7- " + goal.why() + " §f" + Math.round(walk.length()) + "m";
	}

	/** The path from your crosshair along the map, a beam where it ends, and a small box on the other things to do. */
	public static void render(WorldRenderer.Batch batch) {
		SafariMap.Walk w = walk;
		Goal g = goal;
		for (Goal m : marks) {
			if (m == g) continue;
			Vec3 p = m.pos();
			batch.outline(new AABB(p.x - 0.4, p.y, p.z - 0.4, p.x + 0.4, p.y + 0.8, p.z + 0.4), (TazTheme.RED & 0xFFFFFF) | 0x90000000, 1.5f, true);
		}
		if (w == null || g == null) return;
		List<Vec3> points = w.points();
		Vec3 end = points.get(points.size() - 1);
		if (Math.sqrt(end.distanceToSqr(batch.camera)) < ARRIVED) return;
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
