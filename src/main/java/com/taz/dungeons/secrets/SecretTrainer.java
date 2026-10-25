package com.taz.dungeons.secrets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.KeyWatcher;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secret Routes: the route for the dungeon room you're in, from Odin waypoint packs. Normal mode draws every waypoint of
 * the room; Guide mode draws only the next step (waypoints titled with a number, in order) and the one after. A step
 * ticks off when you stand on it (or on one of the next two, for a step someone else already did), click its chest,
 * lever or skull, pick up its item, kill its bat, or the room's secret count goes up next to it. Each room is timed
 * from when you walk in to its last step, against your best; progress lasts the whole run.
 */
public final class SecretTrainer extends TextHud {
	private static final Pattern SECRET_COUNT = Pattern.compile("(\\d+)/(\\d+) Secrets");
	/** Secret items lying in rooms (from Odin's list). */
	private static final List<String> DROPS = List.of("Healing", "Health Potion", "Decoy", "Inflatable Jerry", "Spirit Leap", "Trap",
			"Training Weights", "Defuse Kit", "Dungeon Chest Key", "Treasure Talisman", "Revive Stone", "Architect's First Draft",
			"Secret Dye", "Candycomb");
	private static final double ITEM_NEAR = 4, BAT_NEAR = 6, COUNT_NEAR = 8, HINT_RANGE = 24;
	/** Steps after the current one that standing on also ticks off (with everything before them). */
	private static final int LOOKAHEAD = 2;
	/** A secret count going up this soon after a click / item / bat was already counted for it. */
	private static final long SAME_SECRET_MS = 1500;
	private static final long NOTE_MS = 6000;
	private static final Path TIMES = Taz.CONFIG_DIR.resolve("dungeons").resolve("secret-times.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final KeyWatcher SKIP = new KeyWatcher();
	private static final KeyWatcher RESTART = new KeyWatcher();

	/** A waypoint in the world. */
	record Placed(SecretRoutes.Waypoint wp, BlockPos pos, AABB box, int step) {
		Vec3 top() {
			return new Vec3(box.getCenter().x, box.maxY, box.getCenter().z);
		}
	}

	/** One room's progress this run. */
	static final class Progress {
		final TreeSet<Integer> done = new TreeSet<>();
		long startMs, endMs;
	}

	private static DungeonRooms.Room room;
	private static List<Placed> placed = List.of();
	private static int[] steps = new int[0];
	private static Progress progress;
	/** The steps whose spot you were standing on last tick. */
	private static Set<Integer> standing = Set.of();
	/** Names of the items lying near you, by entity id: a picked-up item's stack is already empty when it goes. */
	private static final Map<Integer, String> ITEM_NAMES = new HashMap<>();
	private static final Map<String, Progress> RUN = new HashMap<>();
	private static ClientLevel runLevel;
	private static int routesSeen = -1;
	private static Map<String, Long> best;
	private static int secretsSeen = -1;
	private static long lastCountedMs;
	private static String note;
	private static long noteUntilMs;

	public SecretTrainer() {
		super("Secret Routes", 0.70, 0.30);
	}

	// ---- settings buttons

	public static void importFromClipboard() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
		if (text.startsWith("https://")) {
			TazChat.info("Downloading the routes...");
			HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(text)).build(), HttpResponse.BodyHandlers.ofString())
					.whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
						if (err != null || res.statusCode() != 200) TazChat.info("Couldn't download them (" + (err != null ? err.getMessage() : "HTTP " + res.statusCode()) + ").");
						else importText(SecretRoutes.nextName(), res.body());
					}));
			return;
		}
		importText(SecretRoutes.nextName(), text);
	}

	private static void importText(String name, String text) {
		try {
			SecretRoutes.Pack pack = SecretRoutes.add(name, SecretRoutes.parse(text));
			TazChat.info("Imported \"" + pack.name() + "\": " + pack.rooms().size() + " rooms, " + pack.waypoints() + " waypoints.");
			refresh();
		} catch (Exception e) {
			TazChat.info("Couldn't import: copy an Odin dungeon waypoint export first (" + e.getMessage() + ").");
		}
	}

	public static void importFromOdin() {
		try {
			List<Path> files = SecretRoutes.odinPacks(Minecraft.getInstance().gameDirectory.toPath());
			if (files.isEmpty()) {
				TazChat.info("No Odin waypoint packs found (config/odin/dungeon-waypoints).");
				return;
			}
			for (Path f : files) {
				String name = "Odin " + f.getFileName().toString().replaceFirst("\\.json$", "");
				SecretRoutes.Pack pack = SecretRoutes.add(name, SecretRoutes.parse(Files.readString(f)));
				TazChat.info("Imported \"" + pack.name() + "\": " + pack.rooms().size() + " rooms, " + pack.waypoints() + " waypoints.");
			}
			refresh();
		} catch (Exception e) {
			TazChat.info("Couldn't import from Odin: " + e.getMessage());
		}
	}

	public static void exportToClipboard() {
		try {
			Map<String, List<SecretRoutes.Waypoint>> rooms = SecretRoutes.active();
			Minecraft.getInstance().keyboardHandler.setClipboard(SecretRoutes.encode(rooms));
			TazChat.info("Copied " + rooms.size() + " rooms as an Odin waypoint export.");
		} catch (Exception e) {
			TazChat.info("Couldn't export: " + e.getMessage());
		}
	}

	public static void listPacks() {
		if (SecretsConfig.get().defaultRoutes) TazChat.info("Taz's routes: " + SecretRoutes.defaults().rooms().size() + " rooms");
		for (SecretRoutes.Pack p : SecretRoutes.imported()) TazChat.info(p.name() + ": " + p.rooms().size() + " rooms, " + p.waypoints() + " waypoints");
		if (!SecretsConfig.get().defaultRoutes && SecretRoutes.imported().isEmpty()) TazChat.info("No routes: import some, or turn Taz's routes on.");
	}

	public static void removeImported() {
		try {
			SecretRoutes.removeImported();
			TazChat.info("Imported routes removed.");
			refresh();
		} catch (Exception e) {
			TazChat.info("Couldn't remove them: " + e.getMessage());
		}
	}

	public static void resetBest() {
		best().clear();
		saveBest();
		TazChat.info("Room best times forgotten.");
	}

	/** Routes changed: place this room's waypoints again (its progress stays). */
	static void refresh() {
		room = null;
	}

	// ---- progress

	/** Every client tick (after DungeonRooms.tick). */
	public static void tick(Minecraft mc) {
		SecretsConfig cfg = SecretsConfig.get();
		boolean skipKey = SKIP.pressed(mc, cfg.skipKey);
		boolean restartKey = RESTART.pressed(mc, cfg.restartKey);
		if (mc.level != runLevel) {
			RUN.clear();
			runLevel = mc.level;
			room = null;
		}
		if (!cfg.enabled || mc.player == null) {
			room = null;
			return;
		}
		int routes = SecretRoutes.version() * 2 + (cfg.defaultRoutes ? 1 : 0);
		if (routes != routesSeen) {
			routesSeen = routes;
			room = null;
		}
		long now = System.currentTimeMillis();
		DungeonRooms.Room r = DungeonRooms.current();
		if (r != room) enter(r, now);
		if (room == null || steps.length == 0) return;
		if (ITEM_NAMES.size() > 64) ITEM_NAMES.clear();
		for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(8))) {
			if (!item.getItem().isEmpty()) ITEM_NAMES.put(item.getId(), item.getItem().getHoverName().getString());
		}
		Vec3 feet = mc.player.position();
		Set<Integer> on = new HashSet<>();
		for (Placed p : placed) if (p.step() >= 0 && !p.wp().small() && feet.distanceTo(p.top()) <= cfg.reach) on.add(p.step());
		if (restartKey) {
			progress.done.clear();
			progress.startMs = now;
			progress.endMs = 0;
			standing = on; // where you are now doesn't count until you come back to it
			TazChat.info(room.data.name() + " restarted.");
			return;
		}
		int cur = currentStep();
		if (skipKey && cur >= 0) progress.done.add(cur);
		// Arriving on a spot of this step (or one of the next ones) ticks it off, with everything before it.
		int from = indexOf(currentStep());
		for (int i = Math.min(steps.length - 1, from + LOOKAHEAD); from >= 0 && i >= from; i--) {
			if (on.contains(steps[i]) && !standing.contains(steps[i])) {
				markThrough(steps[i]);
				break;
			}
		}
		standing = on;
		if (progress.endMs == 0 && currentStep() < 0) finish(now);
	}

	private static void enter(DungeonRooms.Room r, long now) {
		room = r;
		secretsSeen = -1;
		standing = Set.of();
		if (r == null) {
			placed = List.of();
			steps = new int[0];
			progress = null;
			return;
		}
		List<Placed> list = new ArrayList<>();
		for (SecretRoutes.Waypoint w : SecretRoutes.forRoom(r.data.name())) {
			BlockPos p = r.toReal(w.pos());
			list.add(new Placed(w, p, r.toReal(w.box(), p), w.step()));
		}
		placed = list;
		steps = list.stream().mapToInt(Placed::step).filter(s -> s >= 0).distinct().sorted().toArray();
		progress = RUN.computeIfAbsent(r.data.name(), n -> new Progress());
		if (progress.startMs == 0) progress.startMs = now;
	}

	/** The lowest step not done yet, or -1 when the route is done. */
	private static int currentStep() {
		if (progress == null) return -1;
		for (int s : steps) if (!progress.done.contains(s)) return s;
		return -1;
	}

	private static int nextStep(int step) {
		for (int s : steps) if (s > step && !progress.done.contains(s)) return s;
		return -1;
	}

	private static int indexOf(int step) {
		for (int i = 0; i < steps.length; i++) if (steps[i] == step) return i;
		return -1;
	}

	private static void markThrough(int step) {
		for (int s : steps) if (s <= step) progress.done.add(s);
	}

	private static void finish(long now) {
		progress.endMs = now;
		if (SecretsConfig.get().mode != SecretsConfig.Mode.GUIDE) return;
		String name = room.data.name();
		long time = now - progress.startMs;
		Long old = best().get(name);
		if (old == null || time < old) {
			best().put(name, time);
			saveBest();
			note = name + " done in " + TextUtil.formatTimeTenths(time) + (old == null ? " (first best!)" : " (new best, -" + TextUtil.formatTimeTenths(old - time) + ")");
		} else {
			note = name + " done in " + TextUtil.formatTimeTenths(time) + " (+" + TextUtil.formatTimeTenths(time - old) + ")";
		}
		noteUntilMs = now + NOTE_MS;
	}

	// ---- secrets found

	/** You right-clicked a block (a chest, lever or skull ticks off its step). */
	public static void onInteract(BlockPos pos) {
		if (room == null || progress == null) return;
		Vec3 c = Vec3.atCenterOf(pos);
		for (int step : steps) {
			if (progress.done.contains(step)) continue;
			if (placed.stream().anyMatch(p -> p.step() == step && (p.pos().equals(pos) || p.box().inflate(0.1).contains(c)))) {
				counted(step);
				return;
			}
		}
	}

	/** An entity went away: a secret item you picked up, or a secret bat killed. */
	public static void onEntityGone(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (room == null || progress == null || mc.player == null) return;
		if (e instanceof ItemEntity item) {
			String name = ITEM_NAMES.remove(item.getId());
			if (name == null) name = item.getItem().getHoverName().getString();
			if (item.distanceTo(mc.player) <= 6 && DROPS.stream().anyMatch(name::contains)) secretAt(item.position(), ITEM_NEAR);
		} else if (e.getType() == EntityType.BAT && e.distanceTo(mc.player) <= 20) {
			secretAt(e.position(), BAT_NEAR);
		}
	}

	private static void secretAt(Vec3 at, double near) {
		for (int step : steps) {
			if (progress.done.contains(step)) continue;
			if (placed.stream().anyMatch(p -> p.step() == step && p.box().getCenter().distanceTo(at) <= near)) {
				counted(step);
				return;
			}
		}
	}

	private static void counted(int step) {
		progress.done.add(step);
		lastCountedMs = System.currentTimeMillis();
	}

	/** The action bar: "3/7 Secrets" going up next to a secret step ticks it off. */
	public static void onActionBar(String text) {
		Matcher m = SECRET_COUNT.matcher(text);
		if (!m.find()) return;
		int found = Integer.parseInt(m.group(1));
		Minecraft mc = Minecraft.getInstance();
		if (room != null && progress != null && mc.player != null && secretsSeen >= 0 && found > secretsSeen
				&& System.currentTimeMillis() - lastCountedMs > SAME_SECRET_MS) {
			int cur = currentStep();
			Vec3 me = mc.player.position();
			if (cur >= 0 && placed.stream().anyMatch(p -> p.step() == cur && secretish(p) && p.box().getCenter().distanceTo(me) <= COUNT_NEAR)) {
				counted(cur);
			}
		}
		secretsSeen = found;
	}

	private static boolean secretish(Placed p) {
		String n = p.wp().note().toLowerCase();
		return p.wp().small() || n.contains("item") || n.contains("bat") || n.contains("secret");
	}

	// ---- best times

	private static Map<String, Long> best() {
		if (best == null) {
			best = new HashMap<>();
			try {
				if (Files.exists(TIMES)) best.putAll(GSON.fromJson(Files.readString(TIMES), new TypeToken<Map<String, Long>>() {}.getType()));
			} catch (Exception e) {
				Taz.LOGGER.error("Couldn't read the room best times", e);
			}
		}
		return best;
	}

	private static void saveBest() {
		try {
			Files.createDirectories(TIMES.getParent());
			Files.writeString(TIMES, GSON.toJson(best()));
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save the room best times", e);
		}
	}

	// ---- test hooks

	public static String roomName() {
		return room == null ? null : room.data.name();
	}

	public static int step() {
		return currentStep();
	}

	public static List<BlockPos> positions() {
		return placed.stream().map(Placed::pos).toList();
	}

	public static Long bestMs(String room) {
		return best().get(room);
	}

	// ---- drawing

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		SecretsConfig cfg = SecretsConfig.get();
		if (!cfg.enabled || room == null || placed.isEmpty()) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.mode == SecretsConfig.Mode.GUIDE && steps.length > 0) renderGuide(batch, cfg);
			else renderAll(batch);
		} catch (Exception e) {
			Taz.LOGGER.error("Secret routes failed to draw", e);
		}
	}

	/** Normal mode: every waypoint left in the room, as Odin draws it. */
	private static void renderAll(WorldRenderer.Batch batch) {
		for (Placed p : placed) {
			if (p.step() >= 0 && progress.done.contains(p.step())) continue;
			boolean walls = !p.wp().depth();
			int rgb = p.wp().argb() & 0xFFFFFF;
			if (p.wp().visible()) {
				if (p.wp().filled()) batch.filled(p.box(), (p.wp().argb() & 0xFF000000) == 0xFF000000 ? 0x60000000 | rgb : p.wp().argb(), walls);
				else batch.outline(p.box(), 0xFF000000 | rgb, 2.0f, walls);
			}
			if (p.wp().title() != null) batch.label(p.top().add(0, 0.35, 0), p.wp().title(), 0xFF000000 | rgb, walls);
		}
	}

	/** Guide mode: the next step bright, with a path from your crosshair, the one after faint, and the hints. */
	private static void renderGuide(WorldRenderer.Batch batch, SecretsConfig cfg) {
		int cur = currentStep();
		if (cfg.hints) {
			for (Placed p : placed) {
				if (p.step() >= 0 || p.top().distanceTo(batch.camera) > HINT_RANGE) continue;
				if (p.wp().visible()) batch.outline(p.box(), 0x70000000 | (p.wp().argb() & 0xFFFFFF), 1.5f, true);
				if (p.wp().title() != null) batch.label(p.top().add(0, 0.35, 0), p.wp().title(), 0xFFAAAAAA, true);
			}
		}
		if (cur < 0) return;
		int next = nextStep(cur);
		if (cfg.showNext && next >= 0) {
			for (Placed p : placed) {
				if (p.step() != next) continue;
				batch.outline(p.box(), 0x90000000 | (p.wp().argb() & 0xFFFFFF), 1.5f, true);
				batch.label(p.top().add(0, 0.35, 0), "then " + label(p), 0xFFAAAAAA, true);
			}
		}
		Placed nearest = null;
		for (Placed p : placed) {
			if (p.step() != cur) continue;
			int rgb = p.wp().argb() & 0xFFFFFF;
			batch.filled(p.box().inflate(0.02), 0x40000000 | rgb, true);
			batch.outline(p.box().inflate(0.02), 0xFF000000 | rgb, 3.5f, true);
			long dist = Math.round(p.top().distanceTo(batch.camera));
			batch.label(p.top().add(0, 0.45, 0), label(p) + "  " + dist + "m", 0xFF000000 | rgb, true, 1);
			if (nearest == null || p.top().distanceToSqr(batch.camera) < nearest.top().distanceToSqr(batch.camera)) nearest = p;
		}
		if (cfg.lines && nearest != null) {
			batch.line(WorldRenderer.crosshair(batch.camera), nearest.box().getCenter(), 0xC0000000 | (nearest.wp().argb() & 0xFFFFFF), 2.0f, true);
		}
	}

	/** "4 item", "7", "2 down". */
	private static String label(Placed p) {
		String n = p.wp().note();
		return p.step() + (n.isEmpty() ? "" : " " + n);
	}

	/** What a step is, for the HUD: its note and whether it's a secret to get or a spot to stand on. */
	private static String describe(int step) {
		String n = "";
		boolean secret = false;
		for (Placed p : placed) {
			if (p.step() != step) continue;
			if (n.isEmpty()) n = p.wp().note();
			secret |= p.wp().small();
		}
		return (n.isEmpty() ? "" : "§f" + n + " ") + "§7(" + (secret ? "secret" : "spot") + ")";
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return SecretsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return SecretsConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		SecretsConfig cfg = SecretsConfig.get();
		if (room == null || progress == null) {
			if (preview) {
				lines.add("§c§lSecrets §7· §fMossy §e0:12.3 §7(best 0:10.1)");
				lines.add("§fStep §b4§7/7 §fitem §7(secret) · 6m");
				lines.add("§7then §f5 §7(spot)");
			}
			return lines;
		}
		String name = room.data.name();
		if (placed.isEmpty()) {
			lines.add("§c§lSecrets §7· §f" + name);
			lines.add("§7No route for this room");
			return lines;
		}
		if (cfg.mode != SecretsConfig.Mode.GUIDE || steps.length == 0) {
			lines.add("§c§lSecrets §7· §f" + name);
			lines.add("§7" + placed.size() + " waypoints" + (steps.length > 0 ? " · " + progress.done.size() + "/" + steps.length + " steps" : ""));
			return lines;
		}
		long now = System.currentTimeMillis();
		long time = (progress.endMs > 0 ? progress.endMs : now) - progress.startMs;
		Long b = best().get(name);
		lines.add("§c§lSecrets §7· §f" + name + " §e" + TextUtil.formatTimeTenths(time) + (b != null ? " §7(best " + TextUtil.formatTimeTenths(b) + ")" : ""));
		int cur = currentStep();
		if (cur < 0) {
			lines.add("§aRoute done");
		} else {
			Minecraft mc = Minecraft.getInstance();
			double d = Double.MAX_VALUE;
			for (Placed p : placed) if (p.step() == cur && mc.player != null) d = Math.min(d, p.top().distanceTo(mc.player.position()));
			lines.add("§fStep §b" + cur + "§7/" + steps[steps.length - 1] + " " + describe(cur) + (d < Double.MAX_VALUE ? " · " + Math.round(d) + "m" : ""));
			int next = nextStep(cur);
			if (cfg.showNext && next >= 0) lines.add("§7then §f" + next + " " + describe(next));
		}
		if (note != null && now < noteUntilMs) lines.add("§6" + note);
		return lines;
	}
}
