package com.taz.foraging;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.KeyWatcher;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Tree Route Assist. Normal mode draws the whole route of the island you're on. */
public final class RouteAssist extends TextHud {
	private static final double DRAW_RANGE = 160;
	/** The tree-done messages count when you're this close to the tree. */
	private static final double TREE_NEAR = 16;
	/** A tree counts as felled when this share of the logs seen on arrival are gone. */
	private static final double FELLED_SHARE = 0.35;
	/** Fewer logs than this around a tree's point: it isn't grown (chopped, or not back yet). */
	private static final int MIN_TREE_LOGS = 8;
	/** Logs are counted for this long after you reach a tree, before it can be called not grown. */
	private static final long LOOK_MS = 1000;
	/** Going this far from the tree means you've moved on. */
	private static final double LEAVE = 16;
	private static final long NOTE_MS = 5000;
	private static final String REGENERATING = "You cannot damage a tree while it is regenerating!";
	private static final Pattern TREE_DONE = Pattern.compile("^(?:You helped cut [\\d.]+% of the \\w+ Tree\\.|TIMBER! You felled the entire \\w+ Tree!)");
	private static final KeyWatcher RESTART = new KeyWatcher();
	private static final KeyWatcher SKIP = new KeyWatcher();

	private static TreeRoutes.Route route;
	/** The next point to reach: a warp of the current leg, or its tree. */
	private static int next;
	/** At the current leg's tree, chopping it. */
	private static boolean atTree;
	/** Logs around the tree when you got there (the most seen in the first second). */
	private static int logsOnArrival;
	private static long arrivedMs;
	/** "Skipped Tree 3 (not grown)", shown for a few seconds. */
	private static String note;
	private static long noteUntilMs;
	/** Lap start (ms), 0 before the first point is reached. */
	private static long lapStartMs;
	private static long[] splits = new long[0];
	private static long lastSplitDelta = Long.MIN_VALUE;
	private static String lastSplitName;
	private static long lastLapMs;
	private static int ticks;

	public RouteAssist() {
		super("Tree Route", 0.70, 0.08);
	}

	// ---- settings buttons

	public static void importFromClipboard() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		try {
			List<TreeRoutes.Route> routes = TreeRoutes.parse(text, Islands.current());
			TreeRoutes.add(routes);
			for (TreeRoutes.Route r : routes) {
				TreeRoutes.Route own = TreeRoutes.all().contains(r) ? null : TreeRoutes.activeOn(r.island);
				if (own != null && own.builtIn) {
					TazChat.info("That's Taz's " + own.name + ", which is built in: using it.");
					continue;
				}
				TazChat.info("Imported \"" + r.name + "\" (" + r.points.size() + " points) for " + (Islands.UNKNOWN.equals(r.island)
						? "any island (import it while standing on the island to tie it to that island)" : Islands.name(r.island)) + ".");
			}
			restart();
		} catch (Exception e) {
			TazChat.info("Couldn't import: copy a Skyblocker waypoint export first (" + e.getMessage() + ").");
		}
	}

	public static void exportToClipboard() {
		TreeRoutes.Route r = TreeRoutes.active();
		if (r == null) {
			TazChat.info("No route for this island to export.");
			return;
		}
		try {
			Minecraft.getInstance().keyboardHandler.setClipboard(TreeRoutes.export(r));
			TazChat.info("Copied \"" + r.name + "\" in Skyblocker's format.");
		} catch (Exception e) {
			TazChat.info("Couldn't export: " + e.getMessage());
		}
	}

	public static void switchRoute() {
		TreeRoutes.Route r = TreeRoutes.cycle();
		TazChat.info(r == null ? "No route for this island." : "Using \"" + r.name + "\".");
		restart();
	}

	public static void listRoutes() {
		if (TreeRoutes.all().isEmpty()) {
			TazChat.info("No routes yet: copy one from Skyblocker and press Import.");
			return;
		}
		for (TreeRoutes.Route r : TreeRoutes.all()) {
			TazChat.info(r.name + ": " + r.points.size() + " points on " + Islands.name(r.island)
					+ (r.bestLapMs > 0 ? ", best lap " + TextUtil.formatTimeTenths(r.bestLapMs) : ""));
		}
	}

	public static void deleteRoute() {
		TreeRoutes.Route r = TreeRoutes.active();
		if (r == null) {
			TazChat.info("No route for this island.");
			return;
		}
		if (!TreeRoutes.delete(r)) {
			TazChat.info("\"" + r.name + "\" comes with Taz and can't be deleted. Choose another route instead.");
			return;
		}
		TazChat.info("Deleted \"" + r.name + "\".");
		restart();
	}

	public static void resetBest() {
		TreeRoutes.Route r = TreeRoutes.active();
		if (r == null) return;
		r.bestLapMs = 0;
		r.bestSplits = new long[0];
		TreeRoutes.save();
		TazChat.info("Best times for \"" + r.name + "\" forgotten.");
	}

	// ---- legs

	private static boolean isTree(int i) {
		return route.points.get(i).kind() == TreeRoutes.Kind.TREE;
	}

	/** The route's last point: its last tree when the end stops (teleports, Miria) are turned off. */
	static int last() {
		if (!RouteConfig.get().endStops) {
			for (int i = route.points.size() - 1; i >= 0; i--) if (isTree(i)) return i;
		}
		return route.points.size() - 1;
	}

	/** The point a leg starting at {@code from} leads to: the first tree from there, or the route's last point. */
	static int legEnd(int from) {
		int last = last();
		for (int i = from; i < last; i++) if (isTree(i)) return i;
		return last;
	}

	private static boolean near(Vec3 me, int i, double reach) {
		return me.distanceToSqr(Vec3.atBottomCenterOf(route.points.get(i).pos())) <= reach * reach;
	}

	/** Tree logs around a route point (Galatea's trees, or any vanilla log). */
	private static int logsAround(Minecraft mc, BlockPos p) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -6; x <= 6; x++) {
			for (int y = -4; y <= 16; y++) {
				for (int z = -6; z <= 6; z++) {
					BlockState s = mc.level.getBlockState(m.set(p.getX() + x, p.getY() + y, p.getZ() + z));
					if (TreeTracker.kindOf(s) != null || s.is(BlockTags.LOGS)) n++;
				}
			}
		}
		return n;
	}

	// ---- training

	/** Back to the first point, timer stopped. */
	public static void restart() {
		next = 0;
		atTree = false;
		logsOnArrival = 0;
		arrivedMs = 0;
		lapStartMs = 0;
		lastSplitDelta = Long.MIN_VALUE;
		lastSplitName = null;
		route = TreeRoutes.active();
		splits = route == null ? new long[0] : new long[route.points.size()];
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		RouteConfig cfg = RouteConfig.get();
		boolean restartKey = RESTART.pressed(mc, cfg.restartKey);
		boolean skipKey = SKIP.pressed(mc, cfg.skipKey);
		if (!cfg.enabled || mc.player == null || mc.level == null) return;
		TreeRoutes.Route active = TreeRoutes.active();
		if (active != route) restart(); // another island, or another route
		if (route == null || route.points.isEmpty() || cfg.mode != RouteConfig.Mode.TRAINING) return;
		long now = System.currentTimeMillis();
		if (restartKey) {
			restart();
			TazChat.info("Route restarted.");
		}
		if (skipKey) legDone(now, false);
		Vec3 me = mc.player.position();
		double reach = cfg.reach;
		int end = legEnd(next);

		// A lap starts at the first point.
		if (lapStartMs == 0) {
			if (!near(me, 0, reach)) return;
			lapStartMs = now;
			next = 0;
			end = legEnd(0);
		}
		// Warps and teleports on the way tick off as you reach them (or a later one).
		for (int i = end - 1; i >= next; i--) {
			if (near(me, i, reach)) {
				next = i + 1;
				break;
			}
		}
		if (!atTree && near(me, end, reach)) {
			if (isTree(end)) {
				arrive(mc, end, now);
			} else {
				legDone(now, true); // the last stop
				return;
			}
		}
		if (atTree) {
			if (!near(me, end, LEAVE)) {
				legDone(now, true); // you went on: done with it, or skipping it
				return;
			}
			if (++ticks % 5 == 0) {
				int logs = logsAround(mc, route.points.get(end).pos());
				if (now - arrivedMs < LOOK_MS) {
					logsOnArrival = Math.max(logsOnArrival, logs);
				} else if (logsOnArrival < MIN_TREE_LOGS) {
					skip(now, "not grown");
					return;
				} else if (logs <= logsOnArrival * (1 - FELLED_SHARE)) {
					legDone(now, true); // its logs are gone: felled
					return;
				}
			}
		}
		// Warped straight on to the next leg.
		if (end < last()) {
			int after = legEnd(end + 1);
			for (int i = after; i > end; i--) {
				if (near(me, i, reach)) {
					legDone(now, true);
					if (i == after && isTree(after)) arrive(mc, after, now);
					else next = i + 1;
					return;
				}
			}
		}
	}

	private static void arrive(Minecraft mc, int i, long now) {
		atTree = true;
		next = i;
		arrivedMs = now;
		logsOnArrival = logsAround(mc, route.points.get(i).pos());
	}

	/** The current tree can't be chopped now: on to the next one, saying so on the HUD. */
	private static void skip(long now, String why) {
		note = "Skipped " + route.points.get(legEnd(next)).describe() + " (" + why + ")";
		noteUntilMs = now + NOTE_MS;
		legDone(now, true);
	}

	/** A plain-text chat line: your tree-done message finishes the leg's tree; a regenerating one is skipped. */
	public static void onChat(String line) {
		RouteConfig cfg = RouteConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || cfg.mode != RouteConfig.Mode.TRAINING || route == null || lapStartMs == 0 || mc.player == null) return;
		boolean done = TREE_DONE.matcher(line).find();
		boolean regenerating = line.startsWith(REGENERATING);
		if (!done && !regenerating) return;
		int end = legEnd(next);
		if (!isTree(end) || !near(mc.player.position(), end, TREE_NEAR)) return;
		if (done) legDone(System.currentTimeMillis(), true);
		else if (atTree) skip(System.currentTimeMillis(), "regenerating");
	}

	/** The current leg's tree (or last stop) is done: split, then on to the next leg. */
	private static void legDone(long now, boolean reached) {
		int end = legEnd(next);
		if (reached && lapStartMs > 0) {
			splits[end] = now - lapStartMs;
			if (route.bestSplits.length == route.points.size() && route.bestSplits[end] > 0) {
				lastSplitDelta = splits[end] - route.bestSplits[end];
				lastSplitName = route.points.get(end).describe();
			}
		}
		atTree = false;
		logsOnArrival = 0;
		next = end + 1;
		if (next > last()) finishLap(now);
	}

	private static void finishLap(long now) {
		next = 0;
		atTree = false;
		if (lapStartMs == 0) return;
		long lap = now - lapStartMs;
		lastLapMs = lap;
		lapStartMs = 0;
		if (route.bestLapMs == 0 || lap < route.bestLapMs) {
			long old = route.bestLapMs;
			route.bestLapMs = lap;
			route.bestSplits = splits.clone();
			TreeRoutes.save();
			TazChat.info("Lap " + TextUtil.formatTimeTenths(lap) + (old == 0 ? " (first best!)" : " (new best, -" + TextUtil.formatTimeTenths(old - lap) + ")"));
		} else {
			TazChat.info("Lap " + TextUtil.formatTimeTenths(lap) + " (+" + TextUtil.formatTimeTenths(lap - route.bestLapMs) + " on your best)");
		}
		splits = new long[route.points.size()];
	}

	/** Test hooks. */
	public static int nextIndex() {
		return next;
	}

	public static boolean atTree() {
		return atTree;
	}

	public static long lastLapMs() {
		return lastLapMs;
	}

	// ---- drawing

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		RouteConfig cfg = RouteConfig.get();
		if (!cfg.enabled || route == null || route.points.isEmpty()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.mode == RouteConfig.Mode.TRAINING) renderLeg(batch, cfg);
			else renderAll(batch, cfg);
		} catch (Exception e) {
			Taz.LOGGER.error("Tree route failed to draw", e);
		}
	}

	/** Normal mode: every point, numbered, with path lines. */
	private static void renderAll(WorldRenderer.Batch batch, RouteConfig cfg) {
		List<TreeRoutes.Point> pts = route.points;
		int last = last();
		for (int i = 0; i <= last; i++) {
			TreeRoutes.Point p = pts.get(i);
			Vec3 c = Vec3.atCenterOf(p.pos());
			if (c.distanceToSqr(batch.camera) > DRAW_RANGE * DRAW_RANGE) continue;
			int color = 0xFF000000 | p.rgb();
			batch.outline(new AABB(p.pos()), (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
			batch.label(c.add(0, 0.8, 0), p.name(), color, true);
			if (cfg.lines && i + 1 <= last) batch.line(c, Vec3.atCenterOf(pts.get(i + 1).pos()), (color & 0xFFFFFF) | 0x60000000, 2.0f, true);
		}
	}

	/** Training mode: the next tree as the target, and the warps to it numbered on a path from your crosshair. */
	private static void renderLeg(WorldRenderer.Batch batch, RouteConfig cfg) {
		List<TreeRoutes.Point> pts = route.points;
		boolean started = lapStartMs > 0;
		int end = started ? legEnd(next) : 0;
		int from = started ? Math.min(next, end) : 0;
		int last = last();
		if (cfg.showRest) {
			for (int i = 0; i <= last; i++) {
				if (i >= from && i <= end) continue;
				batch.outline(new AABB(pts.get(i).pos()), 0x40000000 | pts.get(i).rgb(), 1.5f, true);
			}
		}
		Vec3 prev = WorldRenderer.crosshair(batch.camera);
		int step = 1;
		for (int i = from; i <= end; i++) {
			TreeRoutes.Point p = pts.get(i);
			Vec3 c = Vec3.atCenterOf(p.pos());
			int color = 0xFF000000 | p.rgb();
			AABB box = new AABB(p.pos());
			if (cfg.lines || i == from) batch.line(prev, c, (color & 0xFFFFFF) | 0xC0000000, 2.0f, true);
			prev = c;
			long dist = Math.round(Math.sqrt(c.distanceToSqr(batch.camera)));
			if (i == end) {
				// the target: big box, a beam, and what to do
				batch.filled(box.inflate(0.1), (color & 0xFFFFFF) | 0x40000000, true);
				batch.outline(box.inflate(0.1), color, 3.5f, true);
				batch.line(c, c.add(0, 40, 0), (color & 0xFFFFFF) | 0x90000000, 4.0f, true);
				String what = !started ? "Start: " + p.describe() : atTree ? "Chop " + p.describe() : p.describe();
				batch.label(c.add(0, 1.0, 0), what + "  " + dist + "m", color, true, 1);
			} else {
				batch.outline(box, color, 2.5f, true);
				batch.label(c.add(0, 0.8, 0), step++ + ". " + p.describe() + "  " + dist + "m", color, true);
			}
		}
		// While chopping, where to go after: the next leg's first point (Tree 1 again after the last).
		if (started && atTree) {
			TreeRoutes.Point p = pts.get(end < last ? end + 1 : 0);
			Vec3 c = Vec3.atCenterOf(p.pos());
			long dist = Math.round(Math.sqrt(c.distanceToSqr(batch.camera)));
			batch.outline(new AABB(p.pos()), 0x90000000 | p.rgb(), 2.0f, true);
			batch.line(Vec3.atCenterOf(pts.get(end).pos()), c, 0x60000000 | p.rgb(), 1.5f, true);
			batch.label(c.add(0, 0.8, 0), "then " + p.describe() + "  " + dist + "m", 0xFFAAAAAA, true);
		}
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return RouteConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return RouteConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		RouteConfig cfg = RouteConfig.get();
		if (route == null) {
			if (preview) {
				lines.add("§a§lTree Route §7· Lap §e1:12.3 §7(best 2:31.4)");
				lines.add("§fNext: §bTree 4 §7· 35m");
				lines.add("§7Warp: §fE4 §7→ §fE5");
			}
			return lines;
		}
		boolean training = cfg.mode == RouteConfig.Mode.TRAINING;
		if (!training) {
			lines.add("§a§l" + route.name);
			lines.add("§7" + route.points.size() + " points" + (route.bestLapMs > 0 ? " · best " + TextUtil.formatTimeTenths(route.bestLapMs) : ""));
			return lines;
		}
		long now = System.currentTimeMillis();
		String best = route.bestLapMs > 0 ? " §7(best " + TextUtil.formatTimeTenths(route.bestLapMs) + ")" : "";
		if (lapStartMs == 0) {
			lines.add("§a§l" + route.name + best);
			lines.add("§fGo to §b" + route.points.get(0).describe() + " §fto start");
			if (lastLapMs > 0) lines.add("§7Last lap §f" + TextUtil.formatTimeTenths(lastLapMs));
			return lines;
		}
		lines.add("§a§l" + route.name + " §7· Lap §e" + TextUtil.formatTimeTenths(now - lapStartMs) + best);
		int end = legEnd(next);
		TreeRoutes.Point target = route.points.get(end);
		Minecraft mc = Minecraft.getInstance();
		long dist = mc.player == null ? 0 : Math.round(Math.sqrt(mc.player.position().distanceToSqr(Vec3.atCenterOf(target.pos()))));
		if (atTree) {
			lines.add("§fChop §b" + target.describe());
		} else {
			lines.add("§fNext: §b" + target.describe() + " §7· " + dist + "m" + (isTree(end) ? "" : " §7(end of lap)"));
		}
		List<String> warps = new ArrayList<>();
		for (int i = next; i < end; i++) warps.add(route.points.get(i).name());
		if (!warps.isEmpty()) lines.add("§7Warp: §f" + String.join(" §7→ §f", warps));
		if (lastSplitDelta != Long.MIN_VALUE) {
			String sign = lastSplitDelta <= 0 ? "§a-" : "§c+";
			lines.add("§7" + lastSplitName + " " + sign + TextUtil.formatTimeTenths(Math.abs(lastSplitDelta)));
		}
		if (note != null && now < noteUntilMs) lines.add("§6" + note);
		return lines;
	}
}
