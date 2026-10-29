package com.taz.fishing.crimson;

import com.taz.dungeons.util.TextUtil;
import com.taz.fishing.trophy.Tier;
import com.taz.fishing.trophy.TrophyBook;
import com.taz.foraging.GalateaRecorder;
import com.taz.foraging.Islands;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trophy Fish on the Crimson Isle: the 18 fish and their 4 tiers, every one you've caught (see {@link TrophyBook}), what
 * catching each takes and whether you're set up for it right now, this session's fish, and titles for rare catches and
 * the Golden Fish. Catch conditions and fillet values are from the SkyHanni repo's trophy fish data.
 */
public final class TrophyFish {
	/** Each fish: its name, the pack's model, its rarity, what catching it takes, and the Magmafish filleting it gives per tier. */
	public enum Fish {
		SULPHUR("Sulphur Skitter", "sulphur_skitter", "§f", "within 4 blocks of Sulphur (sponge)", 40, 60, 80, 120),
		OBF1("Obfuscated-1", "obfuscated_fish_1", "§f", "fish with Corrupted Bait", 16, 24, 32, 48),
		STEAMING("Steaming-Hot Flounder", "steaming_hot_flounder", "§f", "bobber by a geyser in the Blazing Volcano", 20, 28, 40, 60),
		GUSHER("Gusher", "gusher", "§f", "Blazing Volcano, 7-16 min after it erupts", 32, 48, 64, 96),
		BLOBFISH("Blobfish", "blobfish", "§f", "anywhere", 4, 8, 12, 16),
		OBF2("Obfuscated-2", "obfuscated_fish_2", "§a", "use Obfuscated-1 as bait", 40, 60, 80, 120),
		SLUGFISH("Slugfish", "slugfish", "§a", "leave the bobber in 20s+", 40, 60, 80, 120),
		FLYFISH("Flyfish", "flyfish", "§a", "fish from 8+ blocks above the lava, Blazing Volcano", 32, 48, 64, 96),
		OBF3("Obfuscated-3", "obfuscated_fish_3", "§9", "use Obfuscated-2 as bait", 400, 700, 1000, 1300),
		LAVAHORSE("Lavahorse", "lava_horse", "§9", "anywhere", 12, 16, 20, 24),
		MANA_RAY("Mana Ray", "mana_ray", "§9", "have 1,200+ mana", 40, 60, 80, 120),
		STONEFISH("Volcanic Stonefish", "volcanic_stonefish", "§9", "fish in the Blazing Volcano", 20, 28, 40, 60),
		VANILLE("Vanille", "vanille", "§9", "Starter Lava Rod with no enchantments", 80, 120, 160, 240),
		SKELETON("Skeleton Fish", "skeleton_fish", "§5", "fish in the Burning Desert or Dragontail", 32, 48, 64, 96),
		MOLDFIN("Moldfin", "moldfin", "§5", "fish in the Mystic Marsh", 32, 48, 64, 96),
		SOUL("Soul Fish", "soul_fish", "§5", "fish in the Stronghold", 32, 48, 64, 96),
		KARATE("Karate Fish", "karate_fish", "§5", "fish in the Dojo", 40, 60, 80, 120),
		GOLDEN("Golden Fish", "golden_fish", "§6", "shows up after 8+ min of fishing; hook it till it's weak", 400, 700, 1000, 1300);

		public final String label, how;
		final String model, rarity;
		private final int[] fillet;

		Fish(String label, String model, String rarity, String how, int bronze, int silver, int gold, int diamond) {
			this.label = label;
			this.model = "lava/" + model;
			this.rarity = rarity;
			this.how = how;
			this.fillet = new int[]{bronze, silver, gold, diamond};
		}

		/** Magmafish from filleting one. */
		public int fillet(Tier t) {
			return fillet[t.ordinal()];
		}
	}

	public static final TrophyBook BOOK = new TrophyBook("Trophy Fish", "FISH", "fish", entries());
	/** "1,300/1,300✎": Hypixel's resource pack draws the ✎ as a private-use icon (U+E003). */
	private static final Pattern MANA = Pattern.compile("([\\d,]+)/[\\d,]+(?:✎|\uE003)");
	private static final Pattern VOLCANO = Pattern.compile("^Volcano: (.+)$");
	/** Gushers bite 7-16 minutes after the Blazing Volcano erupts. */
	private static final long GUSHER_FROM = 7 * 60_000L, GUSHER_TO = 16 * 60_000L;
	/** Tests: pretend to be on the Crimson Isle, in an area ("Blazing Volcano"). */
	public static boolean testHere;
	public static String testArea;

	private static int sessionFish, sessionMagmafish, ticks, mana = -1;
	private static boolean nearSulphur;
	private static String volcano;
	/** This lobby's volcano: when the eruption Taz sees began (0 = not erupting) and when the last one ended (0 = none seen). */
	private static Object lobby;
	private static long eruptingSince, eruptedAt;
	/** Your Gushers in this lobby: the first of this window's and the last one. */
	private static long gusherFirst, gusherLast;

	static {
		BOOK.onCatch(TrophyFish::caught);
	}

	private TrophyFish() {
	}

	private static List<TrophyBook.Entry> entries() {
		List<TrophyBook.Entry> out = new ArrayList<>();
		for (Fish f : Fish.values()) out.add(new TrophyBook.Entry(f.label, f.model, f.rarity, 100));
		return out;
	}

	public static boolean here() {
		return testHere || "crimson_isle".equals(Islands.current());
	}

	public static boolean has(Fish f, Tier t) {
		return BOOK.has(f.ordinal(), t);
	}

	public static int count(Fish f, Tier t) {
		return BOOK.count(f.ordinal(), t);
	}

	public static int sessionFish() {
		return sessionFish;
	}

	public static int sessionMagmafish() {
		return sessionMagmafish;
	}

	// ---- what's true right now

	public static boolean inArea(String name) {
		if (testArea != null) return testArea.equals(name);
		for (String line : GalateaRecorder.sidebarLines()) if (line.contains(name)) return true;
		return false;
	}

	/** Your mana from the action bar, or -1 before Taz has seen it. */
	public static int mana() {
		return mana;
	}

	/** The Blazing Volcano's state from the tab list ("INACTIVE", or an eruption's level like "MEDIUM"), or null. */
	public static String volcano() {
		return volcano;
	}

	/** Seconds your bobber has been out, or -1 with none. */
	public static int bobberSeconds(Player p) {
		FishingHook hook = p.fishing;
		return hook == null ? -1 : hook.tickCount / 20;
	}

	public static boolean holdingStarterRod(Player p) {
		return TextUtil.cleanChat(p.getMainHandItem().getHoverName().getString()).contains("Starter Lava Rod");
	}

	/** The volcano's state from the tab list, each second: an eruption ends when it says INACTIVE again. */
	public static void volcanoState(String state, long now) {
		volcano = state;
		if (state == null) return;
		if (!state.equals("INACTIVE")) {
			if (eruptingSince == 0) eruptingSince = now;
		} else if (eruptingSince > 0) {
			eruptedAt = now;
			eruptingSince = 0;
		}
	}

	/**
	 * Whether it's Gusher time: you're catching them, or it's 7-16 minutes after an eruption Taz saw end in this lobby.
	 * Null when Taz hasn't seen one.
	 */
	public static Boolean gusherTime(long now) {
		if (gusherLast > 0 && now - gusherLast < 3 * 60_000L && now - gusherFirst < GUSHER_TO - GUSHER_FROM) return true;
		if (eruptingSince > 0) return false;
		if (eruptedAt == 0) return null;
		long since = now - eruptedAt;
		return since >= GUSHER_FROM && since < GUSHER_TO;
	}

	/** The Gusher window for the guide: " (opens in 3m)", " (6m left)" and so on. */
	public static String gusherHint(long now) {
		if (Boolean.TRUE.equals(gusherTime(now)) && (eruptedAt == 0 || now - eruptedAt < GUSHER_FROM || now - eruptedAt >= GUSHER_TO)) {
			return " §a(you're catching them)";
		}
		if (eruptingSince > 0) return " §c(erupting now)";
		if (eruptedAt == 0) return " §8(no eruption seen here)";
		long since = now - eruptedAt;
		if (since < GUSHER_FROM) return " §c(opens in " + minutes(GUSHER_FROM - since) + "m)";
		if (since < GUSHER_TO) return " §a(" + minutes(GUSHER_TO - since) + "m left)";
		return " §c(over for this eruption)";
	}

	private static long minutes(long ms) {
		return (ms + 59_999) / 60_000;
	}

	/** Whether a fish can bite you right now, or null when Taz can't tell (bait, geysers, eruptions, the Golden Fish). */
	public static Boolean canBite(Fish f, Player p) {
		boolean volcanoArea = inArea("Blazing Volcano");
		return switch (f) {
			case BLOBFISH, LAVAHORSE -> true;
			case SULPHUR -> nearSulphur;
			case STEAMING -> volcanoArea ? null : false;
			case GUSHER -> {
				// Gushers on your line say it's their time, wherever the sidebar says you are.
				Boolean time = gusherTime(System.currentTimeMillis());
				yield Boolean.TRUE.equals(time) || volcanoArea ? time : Boolean.FALSE;
			}
			case STONEFISH -> volcanoArea;
			case FLYFISH -> volcanoArea && p.fishing != null && p.getY() - p.fishing.getY() >= 8;
			case SLUGFISH -> bobberSeconds(p) >= 20;
			case MANA_RAY -> mana < 0 ? null : mana >= 1200;
			case VANILLE -> holdingStarterRod(p);
			case SKELETON -> inArea("Burning Desert") || inArea("Dragontail");
			case MOLDFIN -> inArea("Mystic Marsh");
			case SOUL -> inArea("Stronghold");
			case KARATE -> inArea("Dojo");
			case OBF1, OBF2, OBF3, GOLDEN -> null;
		};
	}

	/** Every client tick: Sulphur near your bobber and the volcano, twice a second. */
	public static void tick(Minecraft mc) {
		if (++ticks % 10 != 0 || mc.player == null || mc.level == null || !here()) return;
		Player p = mc.player;
		BlockPos at = p.fishing != null ? p.fishing.blockPosition() : p.blockPosition();
		nearSulphur = false;
		for (BlockPos b : BlockPos.betweenClosed(at.offset(-4, -4, -4), at.offset(4, 4, 4))) {
			BlockState s = mc.level.getBlockState(b);
			if (s.is(Blocks.SPONGE) || s.is(Blocks.WET_SPONGE)) {
				nearSulphur = true;
				break;
			}
		}
		if (ticks % 20 == 0) {
			// A new lobby has its own volcano.
			if (mc.level != lobby) {
				lobby = mc.level;
				eruptingSince = eruptedAt = gusherFirst = gusherLast = 0;
			}
			String state = null;
			for (String line : TrophyBook.tabLines(mc)) {
				Matcher m = VOLCANO.matcher(line);
				if (m.matches()) state = m.group(1).trim();
			}
			volcanoState(state, System.currentTimeMillis());
		}
	}

	/** The action bar: your mana (for Mana Rays). */
	public static void onActionBar(String text) {
		Matcher m = MANA.matcher(text);
		if (m.find()) mana = Integer.parseInt(m.group(1).replace(",", ""));
	}

	/** A plain-text chat line: the Golden Fish (catches come through {@link TrophyBook#onChat}). */
	public static void onChat(String line) {
		TrophyFishConfig cfg = TrophyFishConfig.get();
		if (!cfg.enabled || !cfg.goldenFish) return;
		if (line.equals("You spot a Golden Fish surface from beneath the lava!")) Alert.show("§6§lGOLDEN FISH", "§fCast at it", cfg.sound);
		else if (line.equals("The Golden Fish is weak!")) Alert.show("§6§lREEL IT IN", "§fThe Golden Fish is weak", cfg.sound);
	}

	private static void caught(TrophyBook.Catch c) {
		Fish f = Fish.values()[c.entry()];
		sessionFish++;
		sessionMagmafish += f.fillet(c.tier());
		if (f == Fish.GUSHER) gusherCaught(System.currentTimeMillis());
		TrophyFishConfig cfg = TrophyFishConfig.get();
		if (!cfg.enabled || !cfg.fishTitles) return;
		boolean rare = c.tier() == Tier.GOLD || c.tier() == Tier.DIAMOND;
		if (!rare && !c.isNew()) return;
		String title = (c.isNew() ? "§a§lNEW " : "") + c.tier().code + "§l" + c.tier().name() + " §f" + f.label;
		Alert.show(title, "§7Fillets into §f" + f.fillet(c.tier()) + " Magmafish", cfg.sound);
		if (rare && cfg.party) TazChat.party(c.tier().name() + " " + f.label + "!");
	}

	/** A Gusher of yours: its window is open, for at most 9 minutes from the first one. */
	public static void gusherCaught(long now) {
		if (now - gusherFirst >= GUSHER_TO - GUSHER_FROM) gusherFirst = now;
		gusherLast = now;
	}

	public static void reimport() {
		BOOK.reimport();
	}

	/** Tests: nothing caught, nowhere. */
	public static void resetForTest() {
		BOOK.resetForTest();
		sessionFish = sessionMagmafish = 0;
		mana = -1;
		nearSulphur = false;
		volcano = null;
		eruptingSince = eruptedAt = gusherFirst = gusherLast = 0;
		testHere = false;
		testArea = null;
	}
}
