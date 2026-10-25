package com.taz.dungeons.secrets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.taz.Taz;
import com.taz.dungeons.dungeon.DungeonState;
import com.taz.dungeons.secrets.SecretRoutes.Kind;
import com.taz.foraging.GalateaRecorder;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.KeyWatcher;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * the room; Guide mode follows the route: first the red start, then the steps (waypoints titled with a number, in order),
 * showing only the next one and the one after.
 *
 * Every waypoint goes away once it's done, steps and hints alike: a secret when it's taken (a chest's lid opens, a lever
 * flips, an essence skull or item goes, a bat dies near it, or the room's secret count goes up next to it), a block to
 * mine or blow up when it breaks, a spot when you arrive on it (or on one of the next two, for a step someone else
 * did). A spot that says "bat" or "item" waits for that secret. Each room is timed from its first secret to its last
 * step, against your best; progress lasts the whole run.
 */
public final class SecretTrainer extends TextHud {
	/** The red start of a room's route, before step 1. */
	public static final int START = -1;
	/** No step left: the route is done. */
	public static final int DONE = Integer.MAX_VALUE;
	/** A waypoint that isn't a step (a hint, an aim spot, a note). */
	private static final int HINT = Integer.MIN_VALUE;
	private static final Pattern SECRET_COUNT = Pattern.compile("(\\d+)/(\\d+) Secrets");
	/** Notes that mean "break this block" (or the ones next to it). */
	private static final Pattern MINING = Pattern.compile("(?i)mine|\\bline|linetp|boom|break");
	/** How far from its waypoint a secret counts for it (a Hyperion reaches a bat from further), and a mined block. */
	private static final double ITEM_NEAR = 5, BAT_NEAR = 6, HYPERION_NEAR = 12, COUNT_NEAR = 8, BLOCK_NEAR = 1.5, MINE_NEAR = 1.6;
	/** Items and bats further than this from you went out of sight rather than being taken. */
	private static final double ENTITY_RANGE = 32;
	private static final double HINT_RANGE = 24;
	/** Steps after the current one that arriving on also ticks off (with everything before them). */
	private static final int LOOKAHEAD = 2;
	/** A secret count going up this soon after a secret was already counted. */
	private static final long SAME_SECRET_MS = 1500;
	/** A chest screen opening this soon after you clicked a chest. */
	private static final long CHEST_SCREEN_MS = 1500;
	private static final long NOTE_MS = 6000;
	private static final Path TIMES = Taz.CONFIG_DIR.resolve("dungeons").resolve("secret-times.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final KeyWatcher SKIP = new KeyWatcher();
	private static final KeyWatcher RESTART = new KeyWatcher();

	/** A waypoint in the world, with the step it belongs to (START, a number, or HINT). */
	record Placed(SecretRoutes.Waypoint wp, Kind kind, BlockPos pos, AABB box, int step) {
		Vec3 top() {
			return new Vec3(box.getCenter().x, box.maxY, box.getCenter().z);
		}

		/** A chest, lever, essence, or a spot waiting for its bat / item. */
		boolean secret() {
			return kind == Kind.SECRET || wp.needsSecret();
		}

		boolean mining() {
			return MINING.matcher(wp.note()).find();
		}
	}

	/** One room's progress this run. */
	static final class Progress {
		final TreeSet<Integer> done = new TreeSet<>();
		/** Waypoints done one by one (a secret taken, a block mined), by position. */
		final Set<BlockPos> cleared = new HashSet<>();
		/** When the first secret was found (0 before), and when the route ended. */
		long startMs, endMs;
	}

	private static DungeonRooms.Room room;
	private static List<Placed> placed = List.of();
	private static int[] steps = new int[0];
	private static Progress progress;
	/** The steps whose spot you were standing on last tick. */
	private static Set<Integer> standing = Set.of();
	private static final Map<String, Progress> RUN = new HashMap<>();
	private static ClientLevel runLevel;
	private static int routesSeen = -1;
	private static Map<String, Long> best;
	private static int secretsSeen = -1;
	private static long lastCountedMs;
	/** The chest you last clicked, until its screen opens. */
	private static BlockPos clickedChest;
	private static long clickedChestMs;
	private static String note;
	private static long noteUntilMs;
	private static String lastParty;

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
		if (r != room) enter(r);
		if (room == null || steps.length == 0) return;
		Vec3 feet = mc.player.position();
		Set<Integer> on = new HashSet<>();
		for (Placed p : placed) {
			if (p.step() != HINT && p.kind().spot() && !progress.cleared.contains(p.pos()) && feet.distanceTo(p.top()) <= cfg.reach) on.add(p.step());
		}
		if (restartKey) {
			progress.done.clear();
			progress.cleared.clear();
			progress.startMs = 0;
			progress.endMs = 0;
			standing = on; // where you are now doesn't count until you come back to it
			TazChat.info(room.data.name() + " restarted: go to the red start.");
			return;
		}
		int cur = currentStep();
		if (skipKey && cur != DONE) progress.done.add(cur);
		// Arriving on a spot of this step (or one of the next ones) ticks it off, with everything before it.
		// A spot with a bat or an item waits for that secret.
		int from = indexOf(currentStep());
		for (int i = Math.min(steps.length - 1, from + LOOKAHEAD); from >= 0 && i >= from; i--) {
			int step = steps[i];
			if (!on.contains(step) || standing.contains(step)) continue;
			if (needsSecret(step)) {
				if (i > from) markThrough(steps[i - 1]);
			} else {
				markThrough(step);
			}
			break;
		}
		standing = on;
		if (progress.endMs == 0 && currentStep() == DONE) finish(now);
	}

	private static void enter(DungeonRooms.Room r) {
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
			Kind kind = w.kind();
			int step = w.step() >= 0 ? w.step() : kind == Kind.START ? START : HINT;
			list.add(new Placed(w, kind, p, r.toReal(w.box(), p), step));
		}
		placed = list;
		steps = list.stream().mapToInt(Placed::step).filter(s -> s != HINT).distinct().sorted().toArray();
		progress = RUN.computeIfAbsent(r.data.name(), n -> new Progress());
		Taz.LOGGER.info("[Secret Routes] In {} ({}, clay {}): {} waypoints, {} steps", r.data.name(), r.rotation, r.clay.toShortString(), placed.size(), steps.length);
	}

	/** The first step not done yet (START for the red start), or DONE. */
	private static int currentStep() {
		if (progress == null) return DONE;
		for (int s : steps) if (!progress.done.contains(s)) return s;
		return DONE;
	}

	private static int nextStep(int step) {
		for (int s : steps) if (s > step && !progress.done.contains(s)) return s;
		return DONE;
	}

	private static int indexOf(int step) {
		for (int i = 0; i < steps.length; i++) if (steps[i] == step) return i;
		return -1;
	}

	private static boolean needsSecret(int step) {
		return placed.stream().anyMatch(p -> p.step() == step && p.wp().needsSecret());
	}

	private static boolean isDone(Placed p) {
		return progress.cleared.contains(p.pos()) || (p.step() != HINT && progress.done.contains(p.step()));
	}

	private static void markThrough(int step) {
		for (int s : steps) if (s <= step) progress.done.add(s);
	}

	/**
	 * One waypoint done: hidden from now on, and its step done once nothing else of it is left to do (aim spots and notes
	 * don't count). A secret starts the room's timer.
	 */
	private static void clear(Placed p, boolean secret, String how) {
		if (!progress.cleared.add(p.pos())) return;
		if (p.step() != HINT && placed.stream().allMatch(o -> o.step() != p.step() || o.kind() == Kind.AIM || o.kind() == Kind.TEXT
				|| progress.cleared.contains(o.pos()))) {
			progress.done.add(p.step());
		}
		long now = System.currentTimeMillis();
		if (secret) {
			if (progress.startMs == 0) progress.startMs = now;
			lastCountedMs = now;
		}
		Taz.LOGGER.info("[Secret Routes] {}: {} done ({})", room.data.name(), label(p), how);
		if (progress.endMs == 0 && currentStep() == DONE) finish(now);
	}

	private static void finish(long now) {
		progress.endMs = now;
		SecretsConfig cfg = SecretsConfig.get();
		boolean guide = cfg.mode == SecretsConfig.Mode.GUIDE;
		String name = room.data.name();
		noteUntilMs = now + NOTE_MS;
		if (progress.startMs == 0) { // a route with no secrets in it: nothing to time
			if (guide) note = name + " done";
			return;
		}
		long time = now - progress.startMs;
		if (cfg.partyChat && !solo()) lastParty = TazChat.party(name + " secrets done in " + TextUtil.formatTimeTenths(time));
		if (!guide) return;
		Long old = best().get(name);
		if (old == null || time < old) {
			best().put(name, time);
			saveBest();
			note = name + " done in " + TextUtil.formatTimeTenths(time) + (old == null ? " (first best!)" : " (new best, -" + TextUtil.formatTimeTenths(old - time) + ")");
		} else {
			note = name + " done in " + TextUtil.formatTimeTenths(time) + " (+" + TextUtil.formatTimeTenths(time - old) + ")";
		}
	}

	/** Solo in the dungeon (the sidebar says "Solo", or the tab list "Party (1)"): no party to tell. */
	private static boolean solo() {
		Minecraft mc = Minecraft.getInstance();
		if (GalateaRecorder.sidebarLines().stream().anyMatch(l -> l.trim().equals("Solo"))) return true;
		DungeonState state = new DungeonState();
		state.update(mc);
		return state.partySize == 1;
	}

	// ---- secrets taken and blocks broken

	/**
	 * A secret was taken here (by you or a teammate): the nearest secret waypoint still left, within reach of it, is done.
	 * Bat spots reach further (a Hyperion kills from a distance).
	 */
	private static void secretAt(Vec3 at, double near, String how) {
		if (room == null || progress == null) return;
		Placed best = null;
		double bestD = Double.MAX_VALUE;
		for (Placed p : placed) {
			if (!p.secret() || isDone(p)) continue;
			boolean batSpot = how.equals("bat") && p.wp().note().toLowerCase().contains("bat");
			double d = p.box().getCenter().distanceTo(at);
			if (d <= (batSpot ? HYPERION_NEAR : near) && d < bestD) {
				best = p;
				bestD = d;
			}
		}
		if (best != null) clear(best, true, how);
	}

	/** You right-clicked a block: a lever or skull counts now, a chest once its screen opens. */
	public static void onInteract(BlockPos pos, BlockState state) {
		if (room == null || progress == null) return;
		if (state.getBlock() instanceof ChestBlock) {
			clickedChest = pos;
			clickedChestMs = System.currentTimeMillis();
		} else if (state.getBlock() instanceof LeverBlock || state.getBlock() instanceof AbstractSkullBlock) {
			secretAt(Vec3.atCenterOf(pos), BLOCK_NEAR, "clicked");
		}
	}

	/** A screen opened: the chest you just clicked was opened. */
	public static void onScreenOpened(Screen screen) {
		if (clickedChest == null || !(screen instanceof AbstractContainerScreen<?>)) return;
		if (System.currentTimeMillis() - clickedChestMs <= CHEST_SCREEN_MS) secretAt(Vec3.atCenterOf(clickedChest), BLOCK_NEAR, "chest opened");
		clickedChest = null;
	}

	/** A block event: a chest's lid opening (anyone's). */
	public static void onBlockEvent(BlockPos pos, Block block, int type, int data) {
		if (block instanceof ChestBlock && type == 1 && data > 0) secretAt(Vec3.atCenterOf(pos), BLOCK_NEAR, "chest opened");
	}

	/** A block changed: a lever flipped, an essence skull taken, or a block mined or blown up. */
	public static void onBlockChanged(BlockPos pos, BlockState old, BlockState now) {
		if (room == null || progress == null) return;
		if (old.getBlock() instanceof LeverBlock && now.getBlock() instanceof LeverBlock) {
			if (old.getValue(LeverBlock.POWERED) != now.getValue(LeverBlock.POWERED)) secretAt(Vec3.atCenterOf(pos), BLOCK_NEAR, "lever");
			return;
		}
		if (old.isAir() || !now.isAir()) return;
		Vec3 c = Vec3.atCenterOf(pos);
		for (Placed p : placed) {
			if (isDone(p)) continue;
			boolean here = p.pos().equals(pos) || p.box().inflate(0.01).contains(c);
			if (here || (p.mining() && p.box().getCenter().distanceTo(c) <= MINE_NEAR)) {
				boolean skull = old.getBlock() instanceof AbstractSkullBlock;
				clear(p, skull && p.secret(), skull ? "taken" : "mined");
			}
		}
	}

	/** An entity went away: an item picked up, or a bat killed. */
	public static void onEntityGone(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		if (room == null || progress == null || mc.player == null || e.distanceTo(mc.player) > ENTITY_RANGE) return;
		if (e instanceof ItemEntity) secretAt(e.position(), ITEM_NEAR, "item");
		else if (e.getType() == EntityType.BAT) secretAt(e.position(), BAT_NEAR, "bat");
	}

	/** The action bar: "3/7 Secrets" going up near a secret that nothing else caught yet ticks it off. */
	public static void onActionBar(String text) {
		Matcher m = SECRET_COUNT.matcher(text);
		if (!m.find()) return;
		int found = Integer.parseInt(m.group(1));
		Minecraft mc = Minecraft.getInstance();
		if (room != null && progress != null && mc.player != null && secretsSeen >= 0 && found > secretsSeen
				&& System.currentTimeMillis() - lastCountedMs > SAME_SECRET_MS) {
			secretAt(mc.player.position(), COUNT_NEAR, "secret count");
		}
		secretsSeen = found;
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

	/** The current step: START, a step number, or DONE. */
	public static int step() {
		return currentStep();
	}

	/** The room's timer is running (a secret was found). */
	public static boolean started() {
		return progress != null && progress.startMs > 0;
	}

	/** The last "room done" party chat message ("[TAZ] Mossy secrets done in 0:11.2"). */
	public static String lastParty() {
		return lastParty;
	}

	public static boolean cleared(BlockPos pos) {
		return progress != null && progress.cleared.contains(pos);
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
			if (isDone(p)) continue;
			boolean walls = !p.wp().depth();
			int rgb = p.wp().argb() & 0xFFFFFF;
			if (p.wp().visible()) {
				if (p.wp().filled()) batch.filled(p.box(), (p.wp().argb() & 0xFF000000) == 0xFF000000 ? 0x60000000 | rgb : p.wp().argb(), walls);
				else batch.outline(p.box(), 0xFF000000 | rgb, 2.0f, walls);
			}
			if (p.wp().title() != null) batch.label(p.top().add(0, 0.35, 0), p.wp().title(), 0xFF000000 | rgb, walls);
		}
	}

	/** Guide mode: the next step bright, with a path from your crosshair and what to do there, the one after faint, and the hints. */
	private static void renderGuide(WorldRenderer.Batch batch, SecretsConfig cfg) {
		int cur = currentStep();
		if (cfg.hints) {
			for (Placed p : placed) {
				if (p.step() != HINT || isDone(p) || p.top().distanceTo(batch.camera) > HINT_RANGE) continue;
				if (p.wp().visible()) batch.outline(p.box(), 0x80000000 | (p.wp().argb() & 0xFFFFFF), 1.5f, true);
				String text = p.kind() == Kind.AIM ? "aim here" + (p.wp().title() == null ? "" : ": " + p.wp().title())
						: p.kind() == Kind.PRINCE && p.wp().title() == null ? "prince" : p.wp().title();
				if (text != null) batch.label(p.top().add(0, 0.35, 0), text, p.kind() == Kind.TEXT ? 0xFFFFFFFF : 0xFFAAAAAA, true);
			}
		}
		if (cur == DONE) return;
		int next = nextStep(cur);
		if (cfg.showNext && next != DONE) {
			for (Placed p : placed) {
				if (p.step() != next || isDone(p)) continue;
				if (p.wp().visible()) batch.outline(p.box(), 0x90000000 | (p.wp().argb() & 0xFFFFFF), 1.5f, true);
				batch.label(p.top().add(0, 0.35, 0), "then " + label(p), 0xFFAAAAAA, true);
			}
		}
		Placed nearest = null;
		for (Placed p : placed) {
			if (p.step() != cur || isDone(p)) continue;
			int rgb = p.wp().argb() & 0xFFFFFF;
			if (p.wp().visible()) {
				if (p.kind() != Kind.AIM) batch.filled(p.box().inflate(0.02), 0x40000000 | rgb, true);
				batch.outline(p.box().inflate(0.02), 0xFF000000 | rgb, 3.5f, true);
			}
			long dist = Math.round(p.top().distanceTo(batch.camera));
			batch.label(p.top().add(0, 0.45, 0), label(p) + "  " + dist + "m", 0xFF000000 | rgb, true, 1);
			String how = how(p);
			if (!how.isEmpty()) batch.label(p.top().add(0, 0.45, 0), how, 0xFFDDDDDD, true);
			if (p.kind() != Kind.TEXT && (nearest == null || p.top().distanceToSqr(batch.camera) < nearest.top().distanceToSqr(batch.camera))) nearest = p;
		}
		if (cfg.lines && nearest != null) {
			batch.line(WorldRenderer.crosshair(batch.camera), nearest.box().getCenter(), 0xC0000000 | (nearest.wp().argb() & 0xFFFFFF), 2.0f, true);
		}
	}

	/** "Start", "Start bat", "4 item", "2 down". */
	private static String label(Placed p) {
		String n = p.wp().note();
		String head = p.step() == START ? "Start" : p.step() == HINT ? "" : String.valueOf(p.step());
		return (head + (n.isEmpty() ? "" : " " + n)).trim();
	}

	/** What to do at a waypoint, from the legend: "Etherwarp · pick up the item", "Aim here and right-click to teleport...". */
	private static String how(Placed p) {
		String explain = SecretRoutes.explain(p.kind(), p.wp().note());
		String kind = switch (p.kind()) {
			case START -> "Start here";
			case ETHERWARP -> "Etherwarp";
			case SECRET -> "Chest / lever / essence";
			case PRINCE -> "Prince";
			default -> "";
		};
		if (explain.isEmpty()) return kind;
		return kind.isEmpty() ? capital(explain) : kind + " · " + explain;
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The waypoint that says what a step is (a spot or secret over an aim spot or note). */
	private static Placed main(int step) {
		Placed best = null;
		for (Placed p : placed) {
			if (p.step() != step) continue;
			if (best == null || (best.kind() == Kind.AIM || best.kind() == Kind.TEXT) && p.kind() != Kind.AIM && p.kind() != Kind.TEXT) best = p;
		}
		return best;
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
				lines.add("§fStep §b4§7/7 §dEtherwarp §7· 6m");
				lines.add("§fitem §7· pick up the item");
				lines.add("§7then §f5 down");
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
		Long b = best().get(name);
		String bestText = b != null ? " §7(best " + TextUtil.formatTimeTenths(b) + ")" : "";
		if (progress.startMs == 0) {
			lines.add("§c§lSecrets §7· §f" + name + bestText);
		} else {
			long time = (progress.endMs > 0 ? progress.endMs : now) - progress.startMs;
			lines.add("§c§lSecrets §7· §f" + name + " §e" + TextUtil.formatTimeTenths(time) + bestText);
		}
		int cur = currentStep();
		if (cur == DONE) {
			lines.add("§aRoute done");
		} else {
			Minecraft mc = Minecraft.getInstance();
			double d = Double.MAX_VALUE;
			for (Placed p : placed) {
				if (p.step() == cur && p.kind() != Kind.TEXT && !isDone(p) && mc.player != null) d = Math.min(d, p.top().distanceTo(mc.player.position()));
			}
			String dist = d < Double.MAX_VALUE ? " §7· " + Math.round(d) + "m" : "";
			Placed main = main(cur);
			if (cur == START) {
				lines.add("§c§lStart §7· stand on the red block" + dist);
			} else {
				lines.add("§fStep §b" + cur + "§7/" + steps[steps.length - 1] + " " + main.kind().color + main.kind().label + dist);
			}
			String explain = SecretRoutes.explain(main.kind(), main.wp().note());
			String noteText = main.wp().note();
			if (!noteText.isEmpty() || !explain.isEmpty()) {
				lines.add("§f" + noteText + (explain.isEmpty() ? "" : (noteText.isEmpty() ? "§7" : " §7· ") + explain));
			}
			int next = nextStep(cur);
			if (cfg.showNext && next != DONE) {
				Placed n = main(next);
				lines.add("§7then §f" + label(n) + " §7(" + n.kind().label.toLowerCase() + ")");
			}
		}
		if (note != null && now < noteUntilMs) lines.add("§6" + note);
		return lines;
	}
}
