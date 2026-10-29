package com.taz.fishing.lotus;

import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.foraging.GalateaRecorder;
import com.taz.foraging.Islands;
import com.taz.fishing.trophy.TrophyBook;
import com.taz.render.WorldRenderer;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Lotus Atoll: where you are on it, what's true right now that decides which Trophy Frogs can bite (rain, a Frogcoin
 * blessing, a Red Sweater, Froggles, being underwater, on leaves or in the air, the Cave or the Highlands), alerts for
 * rare catches, and waypoints for its places and the 21 spots a Wormhole can open at. Places and spots are from the wiki.
 */
public final class LotusAtoll {
	public record Place(String name, String what, Vec3 pos) {
	}

	public static final List<Place> PLACES = List.of(
			new Place("Researcher Ribery", "Trade frogs for Lotus", new Vec3(-11.5, 71, -6.5)),
			new Place("Researcher Betty", "Chapters", new Vec3(-13.5, 71, 3.5)),
			new Place("Lotus Eater's Cave", "Cave Frogs; throw Frogcoins in the pond", new Vec3(90, 66, -4)),
			new Place("Researcher Kay", "Launches you up to the Lotus Highlands", new Vec3(23.5, 83, -50.5)),
			new Place("Lotus Highlands", "Highlands Frogs; Researcher Tay launches you back", new Vec3(26.5, 141, -60.5)),
			new Place("Tewtil Tunnel", "Feed a Tewtil 3 Trophy Frogs for its shard", new Vec3(75.5, 64, -10.5)));

	/** Where a Wormhole can open (2 at a time; one closes after 4 to 6 catches and opens somewhere else). */
	public static final List<BlockPos> WORMHOLES = List.of(
			new BlockPos(13, 72, 28), new BlockPos(78, 59, -19), new BlockPos(83, 88, -4), new BlockPos(98, 78, -5),
			new BlockPos(25, 64, 3), new BlockPos(16, 64, -14), new BlockPos(54, 63, -20), new BlockPos(67, 59, -6),
			new BlockPos(37, 62, 7), new BlockPos(27, 63, -11), new BlockPos(52, 62, 5), new BlockPos(73, 60, 7),
			new BlockPos(13, 63, -5), new BlockPos(41, 63, 17), new BlockPos(37, 63, -20), new BlockPos(17, 63, 3),
			new BlockPos(62, 63, -30), new BlockPos(43, 62, -12), new BlockPos(92, 78, -4), new BlockPos(25, 64, 20),
			new BlockPos(36, 64, -37));

	private static final long BLESSING_MS = 30 * 60_000L;
	private static final int PLACE = 0xFF55FF55, WORMHOLE = 0xFFAA55FF;
	/** Tests: pretend to be on the Atoll, in a sub-area ("Lotus Highlands"), and whether it's raining. */
	public static boolean testHere;
	public static String testArea;
	public static Boolean testRain;

	private static long blessedUntil;
	private static boolean warned;
	private static String weather;
	private static int ticks;

	private LotusAtoll() {
	}

	public static boolean here() {
		return testHere || "lotus_atoll".equals(Islands.current());
	}

	static boolean on() {
		return LotusConfig.get().enabled && here();
	}

	// ---- what's true right now

	/** "Tropical Rain", "Blossoming" or "Rain" while it rains on the Atoll, else null. */
	public static String weather() {
		return weather;
	}

	public static boolean inCave() {
		return inArea("Lotus Eater's Cave");
	}

	public static boolean inHighlands() {
		return inArea("Lotus Highlands");
	}

	private static boolean inArea(String name) {
		if (testArea != null) return testArea.equals(name);
		for (String line : GalateaRecorder.sidebarLines()) if (line.contains(name)) return true;
		return false;
	}

	/** Milliseconds of Frogcoin blessing left (Blessed Frogs bite while one is on), 0 when none. */
	public static long blessingLeft() {
		return Math.max(0, blessedUntil - System.currentTimeMillis());
	}

	public static boolean wearing(Player p, EquipmentSlot slot, String name) {
		return TextUtil.cleanChat(p.getItemBySlot(slot).getHoverName().getString()).contains(name);
	}

	public static boolean sweater(Player p) {
		return wearing(p, EquipmentSlot.CHEST, "Red Sweater");
	}

	public static boolean froggles(Player p) {
		return wearing(p, EquipmentSlot.HEAD, "Froggles");
	}

	public static boolean onLeaves(Player p) {
		return p.level().getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ())).is(BlockTags.LEAVES);
	}

	public static boolean midair(Player p) {
		return !p.onGround() && !p.isInWater() && !p.isPassenger();
	}

	/** Whether a species can bite you right now, or null when Taz can't tell (Wormholes, lily pads, the sea creature). */
	public static Boolean canBite(TrophyFrogs.Species s, Player p) {
		return switch (s) {
			case COMMON -> true;
			case LEAP -> midair(p);
			case WETLANDS -> weather != null;
			case BLESSED -> blessingLeft() > 0;
			case BULL -> sweater(p);
			case SEA -> p.isUnderWater();
			case CAVE -> inCave();
			case HIGHLANDS -> inHighlands();
			case TREE -> onLeaves(p);
			case REALITY, EXPLODING, PUDDLE -> null;
		};
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (!here() || mc.level == null) {
			weather = null;
			return;
		}
		if (++ticks % 20 == 0) {
			weather = readWeather(mc, TrophyBook.tabLines(mc));
		}
		LotusConfig cfg = LotusConfig.get();
		long left = blessingLeft();
		if (cfg.enabled && cfg.blessingAlert && !warned && left > 0 && left < 60_000) {
			warned = true;
			Alert.show("§d§lBLESSING ENDS", "§fBlessed Frogs stop in 1 minute", cfg.sound);
		}
	}

	/** The weather from the tab list or sidebar, or the game's own rain. */
	private static String readWeather(Minecraft mc, List<String> tab) {
		if (testRain != null) return testRain ? "Rain" : null;
		for (String name : new String[]{"Blossoming", "Tropical Rain"}) {
			for (String line : GalateaRecorder.sidebarLines()) if (line.contains(name)) return name;
			for (String line : tab) if (line.contains(name)) return name;
		}
		return mc.level.isRaining() ? "Rain" : null;
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		TrophyFrogs.onChat(line);
		if (line.startsWith("WISE! You've been granted ") && line.endsWith(" for 30m while on the Lotus Atoll!")) {
			blessedUntil = System.currentTimeMillis() + BLESSING_MS;
			warned = false;
			return;
		}
		LotusConfig cfg = LotusConfig.get();
		if (!cfg.enabled || !cfg.rareAlert) return;
		if (line.equals("Bow down before the Frog Prince... or pay the hefty price!")) rare("§d§lFROG PRINCE", "§fMythic sea creature", "Frog Prince!", cfg);
		else if (line.startsWith("A Puddle Jumper is preparing for liftoff")) rare("§6§lPUDDLE JUMPER", "§fCast into it and hold on", "Puddle Jumper!", cfg);
	}

	private static void rare(String title, String subtitle, String party, LotusConfig cfg) {
		Alert.show(title, subtitle, cfg.sound);
		if (cfg.party) TazChat.party(party);
	}

	// ---- waypoints

	/** LevelRenderEvents.END_MAIN. */
	public static void render(LevelRenderContext context) {
		LotusConfig cfg = LotusConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!on() || mc.player == null || !cfg.places && !cfg.wormholes) return;
		try (WorldRenderer.Batch batch = WorldRenderer.begin(context)) {
			if (cfg.places) {
				for (Place p : PLACES) {
					double d = p.pos().distanceTo(batch.camera);
					batch.label(p.pos().add(0, 2.2, 0), "§a" + p.name() + " §7" + Math.round(d) + "m", PLACE, true, 1);
					if (d < 48) batch.label(p.pos().add(0, 2.2, 0), "§7" + p.what(), PLACE, true);
				}
			}
			if (cfg.wormholes && (cfg.wormholesAlways || froggles(mc.player))) {
				for (BlockPos w : WORMHOLES) {
					Vec3 c = Vec3.atCenterOf(w);
					AABB ring = new AABB(c.x - 1.5, c.y - 0.5, c.z - 1.5, c.x + 1.5, c.y + 0.5, c.z + 1.5);
					batch.outline(ring, WORMHOLE, 1.5f, true);
					if (c.distanceTo(batch.camera) < 24) batch.label(c.add(0, 1, 0), "§5Wormhole spot", WORMHOLE, true);
				}
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Lotus Atoll waypoints failed", e);
		}
	}

	/** Tests: no blessing, nothing pretended. */
	public static void resetForTest() {
		blessedUntil = 0;
		warned = false;
		weather = null;
		testHere = false;
		testArea = null;
		testRain = null;
	}
}
