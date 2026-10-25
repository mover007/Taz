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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree Route Assist. Normal mode draws the active route of the island you're on. Training mode leads you through it
 * in order: the next point gets a box and a pointer, reaching it (within the reach distance) moves on, and each lap is
 * timed with splits against your best. Reaching one of the next few points counts too, so skipping one doesn't stall.
 */
public final class RouteAssist extends TextHud {
	/** How many points ahead reaching one still counts (a skipped warp). */
	private static final int LOOKAHEAD = 3;
	private static final double DRAW_RANGE = 160;
	private static final KeyWatcher RESTART = new KeyWatcher();
	private static final KeyWatcher SKIP = new KeyWatcher();

	private static TreeRoutes.Route route;
	/** Next point to reach. */
	private static int next;
	/** Lap start (ms), 0 before the first point is reached. */
	private static long lapStartMs;
	private static long[] splits = new long[0];
	private static long lastSplitDelta = Long.MIN_VALUE;
	private static String lastSplitName;
	private static long lastLapMs;

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
		TreeRoutes.delete(r);
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

	// ---- training

	/** Back to the first point, timer stopped. */
	public static void restart() {
		next = 0;
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
		if (!cfg.enabled || mc.player == null) return;
		TreeRoutes.Route active = TreeRoutes.active();
		if (active != route) restart(); // another island, or another route
		if (route == null || route.points.isEmpty() || cfg.mode != RouteConfig.Mode.TRAINING) return;
		if (restartKey) {
			restart();
			TazChat.info("Route restarted.");
		}
		if (skipKey) next = Math.min(next + 1, route.points.size() - 1);
		long now = System.currentTimeMillis();
		Vec3 me = mc.player.position();
		double reach = cfg.reach * (double) cfg.reach;
		// A lap always starts at the first point; after that, reaching one of the next few counts (a skipped warp).
		int last = next == 0 ? 1 : Math.min(next + LOOKAHEAD, route.points.size());
		for (int i = next; i < last; i++) {
			if (me.distanceToSqr(Vec3.atBottomCenterOf(route.points.get(i).pos())) > reach) continue;
			reached(i, now);
			break;
		}
	}

	private static void reached(int i, long now) {
		if (i == 0) lapStartMs = now; // leaving the start counts from here
		if (lapStartMs > 0) {
			splits[i] = now - lapStartMs;
			if (route.bestSplits.length == route.points.size() && route.bestSplits[i] > 0 && i > 0) {
				lastSplitDelta = splits[i] - route.bestSplits[i];
				lastSplitName = route.points.get(i).name();
			}
		}
		next = i + 1;
		if (next >= route.points.size()) finishLap(now);
	}

	private static void finishLap(long now) {
		next = 0;
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

	public static long lastLapMs() {
		return lastLapMs;
	}

	// ---- drawing

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		RouteConfig cfg = RouteConfig.get();
		if (!cfg.enabled || route == null || route.points.isEmpty()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			List<TreeRoutes.Point> pts = route.points;
			boolean training = cfg.mode == RouteConfig.Mode.TRAINING;
			for (int i = 0; i < pts.size(); i++) {
				TreeRoutes.Point p = pts.get(i);
				boolean isNext = training && i == next, isAfter = training && i == next + 1;
				boolean faint = training && !isNext && !isAfter;
				if (faint && !cfg.showRest) continue;
				Vec3 c = Vec3.atCenterOf(p.pos());
				if (c.distanceToSqr(batch.camera) > DRAW_RANGE * DRAW_RANGE && !isNext) continue;
				int color = 0xFF000000 | p.rgb();
				int alpha = isNext ? 0xFF000000 : faint ? 0x50000000 : 0xC0000000;
				AABB box = new AABB(p.pos());
				batch.outline(box, (color & 0xFFFFFF) | alpha, isNext ? 3.0f : 2.0f, true);
				if (isNext) batch.filled(box, (color & 0xFFFFFF) | 0x30000000, true);
				if (!faint || isAfter) {
					String label = (training ? (isNext ? "Next: " : "Then: ") + p.describe() : p.name())
							+ (isNext ? "  " + Math.round(Math.sqrt(c.distanceToSqr(batch.camera))) + "m" : "");
					batch.label(c.add(0, 0.8, 0), label, (color & 0xFFFFFF) | 0xFF000000, true);
				}
				if (cfg.lines && i + 1 < pts.size() && (!training || i == next || cfg.showRest)) {
					batch.line(c, Vec3.atCenterOf(pts.get(i + 1).pos()), (color & 0xFFFFFF) | (training ? 0x90000000 : 0x60000000), 2.0f, true);
				}
			}
			if (training && next < pts.size()) {
				Vec3 target = Vec3.atCenterOf(pts.get(next).pos());
				batch.line(WorldRenderer.crosshair(batch.camera), target, 0xC0000000 | pts.get(next).rgb(), 2.0f, true);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Tree route failed to draw", e);
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
				lines.add("§a§lTree Route §7(Training)");
				lines.add("§fNext: §bEtherwarp E4 §7(9/37) · 23m");
				lines.add("§fLap §e1:12.3 §7· best 2:31.4");
			}
			return lines;
		}
		boolean training = cfg.mode == RouteConfig.Mode.TRAINING;
		lines.add("§a§l" + route.name + (training ? " §7(Training)" : ""));
		if (!training) {
			lines.add("§7" + route.points.size() + " points" + (route.bestLapMs > 0 ? " · best " + TextUtil.formatTimeTenths(route.bestLapMs) : ""));
			return lines;
		}
		Minecraft mc = Minecraft.getInstance();
		if (next < route.points.size() && mc.player != null) {
			TreeRoutes.Point p = route.points.get(next);
			long dist = Math.round(Math.sqrt(mc.player.position().distanceToSqr(Vec3.atCenterOf(p.pos()))));
			lines.add("§fNext: §b" + p.describe() + " §7(" + (next + 1) + "/" + route.points.size() + ") · " + dist + "m");
		}
		long now = System.currentTimeMillis();
		String lap = lapStartMs > 0 ? "§e" + TextUtil.formatTimeTenths(now - lapStartMs) : "§7go to " + route.points.get(0).describe() + " to start";
		lines.add("§fLap " + lap + (route.bestLapMs > 0 ? " §7· best " + TextUtil.formatTimeTenths(route.bestLapMs) : ""));
		if (lastSplitDelta != Long.MIN_VALUE) {
			String sign = lastSplitDelta <= 0 ? "§a-" : "§c+";
			lines.add("§7" + lastSplitName + " " + sign + TextUtil.formatTimeTenths(Math.abs(lastSplitDelta)));
		}
		if (lastLapMs > 0 && lapStartMs == 0) lines.add("§7Last lap §f" + TextUtil.formatTimeTenths(lastLapMs));
		return lines;
	}
}
