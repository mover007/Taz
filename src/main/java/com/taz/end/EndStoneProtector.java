package com.taz.end;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The End Stone Protector: its stage from the tab list's "Protector:" line (Hypixel's End Stone Protector widget), which of its
 * six spots in the Dragon's Nest it will rise at (its head shows there, from GanKura's and Divine Golem's spot lists), and the
 * 20 seconds between "The ground begins to shake" and it rising.
 */
public final class EndStoneProtector extends TextHud {
	/** Its stages, 0 to 5, as the tab list names them. */
	public enum Stage {
		RESTING("Resting", "§7"), DORMANT("Dormant", "§7"), AGITATED("Agitated", "§f"), DISTURBED("Disturbed", "§e"),
		AWAKENING("Awakening", "§6"), SUMMONED("Summoned", "§c");

		public final String label;
		private final String colour;

		Stage(String label, String colour) {
			this.label = label;
			this.colour = colour;
		}

		public int number() {
			return ordinal();
		}

		/** "§e3 Disturbed". */
		public String shown() {
			return colour + number() + " " + label;
		}

		static Stage of(String word) {
			for (Stage s : values()) {
				if (s.label.equalsIgnoreCase(word)) return s;
			}
			return null;
		}
	}

	/** A spot it rises at, and the column its head shows in. */
	public record Spot(String name, int x, int z) {
	}

	public static final List<Spot> SPOTS = List.of(new Spot("Middle Front", -644, -268), new Spot("Right Front", -639, -327),
			new Spot("Right Behind", -678, -331), new Spot("Left", -649, -218), new Spot("Middle Behind", -727, -283),
			new Spot("Middle Center", -689, -272));
	/** From "The ground begins to shake" to "BEWARE - An Endstone Protector has risen!". */
	static final long RISE_MS = 20_000;
	private static final Pattern TAB = Pattern.compile("^Protector:\\s*(\\w+)");
	private static final Pattern RISING = Pattern.compile("^The ground begins to shake as an End ?Stone Protector rises from below!$", Pattern.CASE_INSENSITIVE);
	private static final Pattern RISEN = Pattern.compile("^BEWARE - An End ?Stone Protector has risen!$", Pattern.CASE_INSENSITIVE);
	private static final Pattern DOWN = Pattern.compile("^END ?STONE PROTECTOR DOWN!$", Pattern.CASE_INSENSITIVE);
	/** A tab list this long has loaded, so a missing "Protector:" line means the widget is off. */
	private static final int LOADED_TAB = 20;

	/** Test hooks: the tab list's lines (with "Area: The End" for being there). */
	public static volatile List<String> testTab;

	private static Stage stage;
	private static long stageAt;
	/** Whether Taz saw it change to this stage (so how long it's been is known), not just found it there. */
	private static boolean sawChange;
	private static Spot spot;
	/** Its head's block at that spot (it climbs from y 5 to 9 as the stage goes up). */
	private static BlockPos head;
	/** The protector once it's up (an iron golem under its name tag), or -1. */
	private static int protectorId = -1;
	private static long risesAt;
	private static boolean fighting;
	private static boolean widgetMissing;
	private static boolean here;
	private static int ticks;

	public EndStoneProtector() {
		super("End Stone Protector", 0.01, 0.35);
	}

	public static Stage stage() {
		return stage;
	}

	public static Spot spot() {
		return spot;
	}

	public static boolean here() {
		return here;
	}

	/** Its head's block, or null when Taz hasn't seen it. */
	public static BlockPos head() {
		return head;
	}

	/** The protector itself while it's up, or null. */
	public static Entity protector() {
		Minecraft mc = Minecraft.getInstance();
		if (protectorId < 0 || mc.level == null) return null;
		Entity e = mc.level.getEntity(protectorId);
		return e != null && e.isAlive() ? e : null;
	}

	/** Seconds until it rises, or -1 when it isn't coming. */
	public static double risesIn() {
		return risesAt == 0 ? -1 : Math.max(0, risesAt - System.currentTimeMillis()) / 1000.0;
	}

	public static boolean fighting() {
		return fighting;
	}

	public static boolean widgetMissing() {
		return widgetMissing;
	}

	/** A new lobby has its own protector. */
	public static void onServerChange() {
		stage = null;
		stageAt = 0;
		sawChange = false;
		spot = null;
		head = null;
		protectorId = -1;
		risesAt = 0;
		fighting = false;
		widgetMissing = false;
		here = false;
		LobbyTracker.onServerChange();
	}

	public static void tick(Minecraft mc) {
		if (!ProtectorConfig.get().enabled || mc.level == null) return;
		if (++ticks % 5 != 0) return;
		List<String> tab = tab(mc);
		here = tab.stream().anyMatch(l -> l.startsWith("Area:") && l.substring(5).trim().equals("The End"));
		if (here) {
			readStage(tab);
			// Every second: its head climbs with the stage, and is gone once it has risen (keep the last one seen).
			if (ticks % 20 == 0 && stage != null && stage.number() >= 1) {
				BlockPos h = findHead(mc);
				if (h != null) {
					head = h;
					spot = spotAt(h);
				}
			}
			protectorId = findProtector(mc);
		} else {
			protectorId = -1;
		}
		LobbyTracker.tick(mc, here);
	}

	public static void onChat(String line) {
		ProtectorConfig cfg = ProtectorConfig.get();
		if (!cfg.enabled) return;
		String text = line.trim();
		long now = System.currentTimeMillis();
		if (RISING.matcher(text).matches()) {
			risesAt = now + RISE_MS;
			fighting = false;
			if (stage != Stage.SUMMONED) set(Stage.SUMMONED, now);
		} else if (RISEN.matcher(text).matches()) {
			risesAt = 0;
			fighting = true;
			if (stage != Stage.SUMMONED) set(Stage.SUMMONED, now);
		} else if (DOWN.matcher(text).matches()) {
			risesAt = 0;
			fighting = false;
			set(Stage.RESTING, now);
			LobbyTracker.onKilled();
		}
	}

	private static List<String> tab(Minecraft mc) {
		List<String> lines = testTab;
		if (lines != null) return lines;
		lines = new ArrayList<>();
		if (mc.getConnection() == null) return lines;
		for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
			Component name = info.getTabListDisplayName();
			if (name != null) lines.add(TextUtil.cleanScoreboard(name.getString()).trim());
		}
		return lines;
	}

	private static void readStage(List<String> tab) {
		for (String line : tab) {
			Matcher m = TAB.matcher(line);
			if (!m.find()) continue;
			widgetMissing = false;
			Stage s = Stage.of(m.group(1));
			if (s != null) set(s, System.currentTimeMillis());
			return;
		}
		widgetMissing = tab.size() >= LOADED_TAB;
	}

	private static void set(Stage s, long now) {
		// Once it's coming, the tab list can still say Awakening for a moment.
		if (stage == Stage.SUMMONED && s == Stage.AWAKENING && (risesAt > 0 || fighting)) return;
		if (s == stage) return;
		Stage old = stage;
		stage = s;
		stageAt = now;
		sawChange = old != null;
		if (s.number() < Stage.SUMMONED.number()) {
			risesAt = 0;
			fighting = false;
		}
		if (s == Stage.RESTING) {
			spot = null;
			head = null;
		}
		LobbyTracker.onStage(s);
		ProtectorConfig cfg = ProtectorConfig.get();
		String where = spot == null ? "" : " at " + spot.name();
		if (s == Stage.AWAKENING && cfg.stage4) {
			Alert.show("§6§lPROTECTOR STAGE 4", old == null ? "§7This lobby is at stage 4" + where : "§7Awakening" + where, cfg.sound);
		} else if (s == Stage.SUMMONED && cfg.stage5 && !fighting) {
			Alert.show("§c§lPROTECTOR STAGE 5", risesAt > 0 ? "§7It rises in 20 seconds" + where : "§7It rises any moment" + where, cfg.sound);
		}
	}

	/** Its head shows at the spot it will rise at: the head's block, or null. */
	static BlockPos findHead(Minecraft mc) {
		for (Spot s : SPOTS) {
			for (int x = s.x() - 1; x <= s.x() + 1; x++) {
				for (int z = s.z() - 2; z <= s.z() + 2; z++) {
					for (int y = 3; y <= 10; y++) {
						BlockPos p = new BlockPos(x, y, z);
						BlockState b = mc.level.getBlockState(p);
						if (b.is(Blocks.PLAYER_HEAD) || b.is(Blocks.PLAYER_WALL_HEAD)) return p;
					}
				}
			}
		}
		return null;
	}

	/** The spot nearest a head. */
	static Spot spotAt(BlockPos p) {
		Spot best = SPOTS.get(0);
		for (Spot s : SPOTS) {
			if (Math.abs(s.x() - p.getX()) + Math.abs(s.z() - p.getZ()) < Math.abs(best.x() - p.getX()) + Math.abs(best.z() - p.getZ())) best = s;
		}
		return best;
	}

	/** The protector: the one iron golem in The End, or the mob under its "End Stone Protector" name tag. */
	private static int findProtector(Minecraft mc) {
		ArmorStand tag = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof IronGolem && e.isAlive()) return e.getId();
			if (e instanceof ArmorStand a && a.getCustomName() != null
					&& TextUtil.cleanChat(a.getCustomName().getString()).toLowerCase().contains("protector")) tag = a;
		}
		if (tag == null) return -1;
		Entity best = null;
		double bestDist = 5.0 * 5.0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e instanceof Player || !e.isAlive()) continue;
			double dx = e.getX() - tag.getX(), dz = e.getZ() - tag.getZ(), dy = tag.getY() - e.getY();
			if (dy < -1 || dy > 8) continue;
			double d = dx * dx + dz * dz;
			if (d < bestDist) {
				best = e;
				bestDist = d;
			}
		}
		return best == null ? -1 : best.getId();
	}

	private static final int PURPLE = 0xFFC040FF;

	/** LevelRenderEvents.END_MAIN: a beam at the spot it will rise at, and a box around it once it's up. */
	public static void render(LevelRenderContext context) {
		ProtectorConfig cfg = ProtectorConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || !here || mc.player == null) return;
		Entity golem = cfg.highlight ? protector() : null;
		boolean showSpot = cfg.waypoint && golem == null && !fighting && head != null && spot != null && stage != null
				&& stage.number() >= cfg.fromStage;
		if (golem == null && !showSpot) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (showSpot) {
				double x = head.getX(), y = head.getY(), z = head.getZ();
				AABB block = new AABB(x, y, z, x + 1, y + 1, z + 1);
				batch.filled(block, 0x50C040FF, true);
				batch.outline(block, PURPLE, 2, true);
				// A beam up from it, so you can see where it is from anywhere on the island.
				batch.filled(new AABB(x + 0.3, y + 1, z + 0.3, x + 0.7, y + 80, z + 0.7), 0x40C040FF, true);
				Vec3 top = new Vec3(x + 0.5, y + 1.2, z + 0.5);
				String far = " §8· §7" + Math.round(batch.camera.distanceTo(top)) + "m";
				batch.label(top, "§d§lProtector §8· §f" + spot.name(), 0xFFFFFFFF, true, 1);
				batch.label(top, (risesAt > 0 ? "§cRises in §f" + String.format("%.1fs", risesIn()) : "§7Stage " + stage.shown()) + far,
						0xFFFFFFFF, true, 0);
			}
			if (golem != null) {
				float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				Vec3 p = golem.getPosition(partial);
				double w = golem.getBbWidth() / 2 + 0.05;
				AABB box = new AABB(p.x - w, p.y, p.z - w, p.x + w, p.y + golem.getBbHeight() + 0.05, p.z + w);
				batch.filled(box, 0x30C040FF, true);
				batch.outline(box, PURPLE, 3, true);
				Vec3 top = new Vec3(p.x, box.maxY + 0.3, p.z);
				batch.label(top, "§d§lEnd Stone Protector §8· §7" + Math.round(batch.camera.distanceTo(top)) + "m", 0xFFFFFFFF, true, 0);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("End Stone Protector waypoint failed to draw", e);
		}
	}

	/** For tests: back to no lobby. */
	public static void resetForTest() {
		onServerChange();
		testTab = null;
	}

	@Override
	protected HudPos pos() {
		return ProtectorConfig.get().stagePos;
	}

	@Override
	public boolean enabled() {
		ProtectorConfig cfg = ProtectorConfig.get();
		return cfg.enabled && cfg.hud;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> out = new ArrayList<>();
		if (preview && !here) {
			out.add("§5§lEnd Stone Protector");
			out.add("§7Stage " + Stage.AWAKENING.shown() + " §8(2m 30s)");
			out.add("§7Spot: §fMiddle Front");
			out.add("§7Lobby §fm147AS §8· §anew");
			return out;
		}
		if (!here) return out;
		out.add("§5§lEnd Stone Protector");
		if (widgetMissing) {
			out.add("§cIt isn't in your tab list. Turn it on with");
			out.add("§f/widget §7→ General Info Widget →");
			out.add("§7Show End Stone Protector Status");
		} else if (stage == null) {
			out.add("§7Stage §8reading the tab list…");
		} else {
			String line = "§7Stage " + stage.shown();
			if (stage == Stage.AWAKENING && sawChange) line += " §8(" + clock(System.currentTimeMillis() - stageAt) + ")";
			out.add(line);
			if (spot != null) out.add("§7Spot: §f" + spot.name());
			if (risesAt > 0) out.add("§cRises in §f" + String.format("%.1fs", risesIn()));
			else if (fighting) out.add("§cFight!");
		}
		String lobby = LobbyTracker.hudLine();
		if (lobby != null) out.add(lobby);
		return out;
	}

	/** "1h 5m", "2m 30s", "45s". */
	static String clock(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s >= 3600) return s / 3600 + "h " + s % 3600 / 60 + "m";
		return s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s";
	}
}
