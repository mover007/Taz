package com.taz.gametest;

import com.google.gson.JsonPrimitive;
import com.taz.Taz;
import com.taz.foraging.FairySouls;
import com.taz.foraging.GalateaRecorder;
import com.taz.foraging.Islands;
import com.taz.foraging.PressureWarning;
import com.taz.foraging.ProtectedTrees;
import com.taz.foraging.RouteAssist;
import com.taz.foraging.RouteConfig;
import com.taz.foraging.TreeRoutes;
import com.taz.foraging.WhisperTracker;
import com.taz.foraging.StarlynContest;
import com.taz.foraging.TreeGifts;
import com.taz.foraging.TreeTracker;
import com.taz.general.EventTimers;
import com.taz.general.SkyblockEvent;
import com.taz.foraging.HuntingMobs;
import com.taz.foraging.IslandGuide;
import com.taz.foraging.MoongladeResources;
import com.taz.foraging.RubyVeilshrooms;
import com.taz.foraging.SweepDetails;
import com.taz.foraging.TreeProgress;
import com.taz.foraging.safari.Critters;
import com.taz.foraging.safari.SafariConfig;
import com.taz.foraging.safari.SafariFinds;
import com.taz.foraging.safari.SafariGuide;
import com.taz.foraging.safari.SafariHelper;
import com.taz.foraging.safari.SafariMap;
import com.taz.foraging.safari.SafariSpots;
import com.taz.foraging.safari.SafariThrow;
import com.taz.foraging.safari.SafariWay;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * The Moonglade Marsh / Torrhus Canyon features: Ruby Veilshroom finder, tree progress, sweep details, Sea Lumies and
 * Lushlilac, hunting mob glows, Fairy Souls and the island guide.
 */
public class GalateaGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"veilshrooms", "treeprogress", "sweep", "resources", "huntingmobs", "fairysouls", "guide",
			"treegifts", "starlyn", "protected", "recorder", "hotfreset", "contesttiming", "pressure", "route", "safari", "safariway", "fullclear"};
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;
	private final List<String> shown = new CopyOnWriteArrayList<>();
	private BlockPos start;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "galatea");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Stream.of(PARTS).noneMatch(GalateaGameTest::runs)) {
			System.out.println("[TestFilter] skipped galatea");
			return;
		}
		ctx = context;
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) shown.add(message.getString());
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			cmd("gamerule doMobSpawning false");
			start = ctx.computeOnClient(mc -> mc.player.blockPosition());
			if (runs("veilshrooms")) veilshrooms();
			if (runs("treeprogress")) treeProgress();
			if (runs("sweep")) sweep();
			if (runs("resources")) resources();
			if (runs("huntingmobs")) huntingMobs();
			if (runs("fairysouls")) fairySouls();
			if (runs("guide")) guide();
			if (runs("treegifts")) treeGifts();
			if (runs("starlyn")) starlyn();
			if (runs("protected")) protectedTrees();
			if (runs("recorder")) recorder();
			if (runs("hotfreset")) hotfReset();
			if (runs("contesttiming")) contestTiming();
			if (runs("pressure")) pressure();
			if (runs("route")) route();
			if (runs("safari")) safari();
			if (runs("safariway")) safariWay();
			if (runs("fullclear")) fullClear();
		}
		System.out.println("[GalateaGameTest] PASSED");
	}

	// ---- Ruby Veilshrooms

	private void veilshrooms() {
		ctx.runOnClient(mc -> Taz.config().galatea.veilshrooms.enabled = true);
		ctx.waitTicks(5);
		int x = start.getX(), y = start.getY(), z = start.getZ();
		BlockPos near = new BlockPos(x + 4, y, z);
		BlockPos mid = new BlockPos(x + 9, y, z + 3);
		BlockPos underground = new BlockPos(x - 12, y - 2, z); // inside the dirt: found through it
		BlockPos far = new BlockPos(x + 40, y, z - 6);
		for (BlockPos p : List.of(near, mid, underground, far)) setblock(p, "crimson_fungus");
		ctx.waitTicks(5);
		check("veilshrooms: placed ones found (" + count() + ")", count() == 4);

		// Off and on again: found by scanning the chunks, not just from block changes.
		ctx.runOnClient(mc -> Taz.config().galatea.veilshrooms.enabled = false);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> Taz.config().galatea.veilshrooms.enabled = true);
		int waited = 0;
		while (count() < 4 && waited < 400) {
			ctx.waitTicks(5);
			waited += 5;
		}
		check("veilshrooms: found again by scanning chunks in " + waited + " ticks", count() == 4);
		check("veilshrooms: pointer goes to the closest", next().equals(near));

		cmd("tp @p " + (x + 2) + " " + y + " " + z + " facing " + (x + 4) + " " + y + " " + z);
		ctx.waitTicks(3);
		setblock(near, "air"); // picked
		ctx.waitTicks(3);
		long respawn = ctx.computeOnClient(mc -> RubyVeilshrooms.respawnSeconds(near));
		check("veilshrooms: picked one respawns in ~50s (" + respawn + ")", respawn >= 45 && respawn <= 50);
		check("veilshrooms: counted as yours", ctx.computeOnClient(mc -> RubyVeilshrooms.harvested()) == 1);
		check("veilshrooms: pointer moves to the next", next().equals(mid));
		List<String> lines = hudLines(RubyVeilshrooms.class);
		check("veilshrooms: HUD (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Ready") && l.contains("3"))
				&& lines.stream().anyMatch(l -> l.contains("Picked") && l.contains("1")));
		cmd("tp @p " + (x - 6) + " " + (y + 6) + " " + (z - 8) + " facing " + (x + 4) + " " + y + " " + z);
		ctx.waitTicks(10);
		ctx.takeScreenshot("galatea-veilshrooms");
		setblock(near, "crimson_fungus"); // grew back
		ctx.waitTicks(3);
		check("veilshrooms: back after respawning", count() == 4 && ctx.computeOnClient(mc -> RubyVeilshrooms.respawnSeconds(near)) == -1);
		ctx.waitTicks(3);
		try {
			String saved = Files.readString(Taz.CONFIG_DIR.resolve("foraging/veilshrooms.json"));
			check("veilshrooms: spots saved for next time", saved.contains("[" + far.getX() + "," + far.getY() + "," + far.getZ() + "]"));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		for (BlockPos p : List.of(near, mid, far)) setblock(p, "air");
		setblock(underground, "dirt");
		cmd("tp @p " + x + " " + y + " " + z);
	}

	private int count() {
		return ctx.computeOnClient(mc -> RubyVeilshrooms.FINDER.count());
	}

	private BlockPos next() {
		return ctx.computeOnClient(mc -> RubyVeilshrooms.next(mc.player.position()));
	}

	// ---- tree progress

	private void treeProgress() {
		ctx.runOnClient(mc -> Taz.config().foraging.treeProgress.enabled = true);
		int x = start.getX(), y = start.getY(), z = start.getZ();
		cmd("summon armor_stand " + (x + 5.5) + " " + (y + 2) + " " + (z + 0.5)
				+ " {CustomName:\"FIG TREE 37%\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"taztest\"]}");
		ctx.waitTicks(10);
		String tree = ctx.computeOnClient(mc -> TreeProgress.nearestTree());
		check("tree progress: closest tree hologram (" + tree + ")", "Fig Tree 37%".equals(tree));
		clearAlert();
		tell("TIMBER! You felled the entire Fig Tree!");
		check("tree progress: TIMBER alert", Alert.lastTitle != null && Alert.lastTitle.contains("TIMBER"));
		check("tree progress: felled counted", ctx.computeOnClient(mc -> TreeProgress.felled("Fig")) == 1);
		List<String> lines = hudLines(TreeProgress.class);
		check("tree progress: HUD (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Fig Tree") && l.contains("37%"))
				&& lines.stream().anyMatch(l -> l.contains("Felled") && l.contains("Fig 1")));
		cmd("tp @p " + x + " " + y + " " + z + " facing " + (x + 5) + " " + (y + 2) + " " + z);
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-tree-progress");
		cmd("kill @e[tag=taztest]");
	}

	// ---- sweep details

	private void sweep() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.sweep.enabled = true;
			Taz.config().foraging.sweep.hideChat = true;
		});
		clearAlert();
		tell("Sweep Details: 120∮ Sweep");
		tell("  Fig Tree Toughness: 10 12.0 Logs");
		tell("  Wrong Style: -50% Sweep 6.0 Logs Cut the trunk first!!");
		List<String> lines = hudLines(SweepDetails.class);
		check("sweep: HUD (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Sweep") && l.contains("120"))
				&& lines.stream().anyMatch(l -> l.contains("Fig Tree") && l.contains("10"))
				&& lines.stream().anyMatch(l -> l.contains("Wrong order -50%") && l.contains("Cut the trunk first")));
		check("sweep: wrong order alert (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("WRONG ORDER"));
		check("sweep: chat lines hidden", shown.stream().noneMatch(s -> s.contains("Sweep Details") || s.contains("Tree Toughness")));
		tell("Toughness is a word in a normal message");
		check("sweep: other lines still show", shown.stream().anyMatch(s -> s.contains("normal message")));
		ctx.takeScreenshot("galatea-sweep");
	}

	// ---- Sea Lumies and Lushlilac

	private void resources() {
		ctx.runOnClient(mc -> Taz.config().galatea.resources.enabled = true);
		int x = start.getX(), y = start.getY(), z = start.getZ();
		BlockPos full = new BlockPos(x + 3, y, z + 2), small = new BlockPos(x + 5, y, z + 2), lilac = new BlockPos(x + 4, y, z - 2);
		setblock(full, "sea_pickle[pickles=4,waterlogged=false]");
		setblock(small, "sea_pickle[pickles=1,waterlogged=false]");
		setblock(lilac, "flowering_azalea");
		int waited = 0;
		while ((lumies() < 2 || lilacs() < 1) && waited < 400) {
			ctx.waitTicks(5);
			waited += 5;
		}
		check("resources: Sea Lumies found (" + lumies() + ")", lumies() == 2);
		check("resources: Lushlilac found", lilacs() == 1);
		check("resources: only clumps of 3+ highlighted", ctx.computeOnClient(mc -> MoongladeResources.bigEnough(mc.level.getBlockState(full))
				&& !MoongladeResources.bigEnough(mc.level.getBlockState(small))));
		cmd("tp @p " + x + " " + (y + 1) + " " + (z - 4) + " facing " + (x + 4) + " " + y + " " + z);
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-resources");
		for (BlockPos p : List.of(full, small, lilac)) setblock(p, "air");
		ctx.waitTicks(3);
		check("resources: picked ones go away", lumies() == 0 && lilacs() == 0);
		cmd("tp @p " + x + " " + y + " " + z);
	}

	private int lumies() {
		return ctx.computeOnClient(mc -> MoongladeResources.LUMIES.count());
	}

	private int lilacs() {
		return ctx.computeOnClient(mc -> MoongladeResources.LUSHLILAC.count());
	}

	// ---- hunting mobs

	private void huntingMobs() {
		ctx.runOnClient(mc -> Taz.config().galatea.huntingMobs.enabled = true);
		int x = start.getX(), y = start.getY(), z = start.getZ();
		String tags = "NoAI:1b,PersistenceRequired:1b,Tags:[\"taztest\"]";
		cmd("summon shulker " + (x + 4) + " " + y + " " + (z - 4) + " {Color:13b," + tags + "}");  // green: Hideonleaf
		cmd("summon shulker " + (x + 4) + " " + y + " " + z + " {Color:4b," + tags + "}");        // yellow: Hideonsun
		cmd("summon shulker " + (x + 4) + " " + y + " " + (z + 4) + " {Color:10b," + tags + "}"); // purple: not a hunting mob
		cmd("summon turtle " + (x + 7) + " " + y + " " + (z - 3) + " {" + tags + "}");
		cmd("summon axolotl " + (x + 7) + " " + y + " " + z + " {" + tags + "}");
		cmd("summon parrot " + (x + 7) + " " + y + " " + (z + 3) + " {Variant:1," + tags + "}");
		cmd("summon parrot " + (x + 7) + " " + (y + 1) + " " + (z + 5) + " {Variant:0," + tags + "}");
		cmd("tp @p " + (x - 2) + " " + (y + 1) + " " + z + " facing " + (x + 6) + " " + y + " " + z);
		ctx.waitTicks(15);
		String result = ctx.computeOnClient(mc -> {
			StringBuilder sb = new StringBuilder();
			for (Entity e : mc.level.entitiesForRendering()) {
				int glow = HuntingMobs.glowColor(e);
				boolean glowing = mc.shouldEntityAppearGlowing(e);
				String what = e instanceof Shulker s ? "shulker:" + (s.getColor() == null ? "none" : s.getColor().getName())
						: e instanceof Parrot p ? "parrot:" + p.getVariant().name()
						: e instanceof Turtle ? "turtle" : e instanceof Axolotl ? "axolotl" : null;
				if (what != null) sb.append(what).append('=').append(glow >= 0 && glowing ? Integer.toHexString(glow) : "no").append(' ');
			}
			return sb.toString().trim();
		});
		check("hunting mobs: glows (" + result + ")", result.contains("shulker:green=" + Integer.toHexString(HuntingMobs.HIDEONLEAF))
				&& result.contains("shulker:yellow=" + Integer.toHexString(HuntingMobs.HIDEONSUN)) && result.contains("shulker:purple=no")
				&& result.contains("turtle=" + Integer.toHexString(HuntingMobs.SHELLWISE)) && result.contains("axolotl=" + Integer.toHexString(HuntingMobs.CORALOT))
				&& result.contains("parrot:BLUE=" + Integer.toHexString(HuntingMobs.BLUE_JAY)) && result.contains("parrot:RED_BLUE=no"));
		check("hunting mobs: Phantoms muted, other sounds not", ctx.computeOnClient(mc ->
				HuntingMobs.muted(SimpleSoundInstance.forUI(SoundEvents.PHANTOM_AMBIENT, 1f))
						&& !HuntingMobs.muted(SimpleSoundInstance.forUI(SoundEvents.ZOMBIE_AMBIENT, 1f))));
		ctx.takeScreenshot("galatea-hunting-mobs");
		cmd("kill @e[tag=taztest]");
		cmd("tp @p " + x + " " + y + " " + z);
		ctx.waitTicks(5);
	}

	// ---- Fairy Souls

	private void fairySouls() {
		ctx.runOnClient(mc -> {
			Taz.config().galatea.fairySouls.enabled = true;
			FairySouls.forgetFound();
		});
		check("fairy souls: 24 to find on both islands", ctx.computeOnClient(mc -> FairySouls.missing().size()) == 24);
		cmd("gamemode creative @p");
		cmd("tp @p -565 95 18");
		ctx.waitTicks(3);
		tell("SOUL! You found a Fairy Soul!");
		check("fairy souls: picking one up ticks off the closest", ctx.computeOnClient(mc ->
				FairySouls.isFound(FairySouls.SOULS.get(0)) && FairySouls.missing().size() == 23));
		cmd("tp @p -706 98 143");
		ctx.waitTicks(3);
		tell("You have already found that Fairy Soul!");
		check("fairy souls: one you had also ticks off", ctx.computeOnClient(mc -> FairySouls.missing().size()) == 22);
		cmd("tp @p -560 98 -20");
		ctx.waitTicks(3);
		tell("SOUL! You found a Fairy Soul!");
		check("fairy souls: nothing ticked off when none is near", ctx.computeOnClient(mc -> FairySouls.missing().size()) == 22);
		try {
			String saved = Files.readString(Taz.CONFIG_DIR.resolve("foraging/fairysouls.json"));
			check("fairy souls: saved (" + saved + ")", saved.contains("MM-1") && saved.contains("TC-3"));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		cmd("tp @p -585 132 -20 facing -601 128 -20");
		ctx.waitTicks(20);
		ctx.takeScreenshot("galatea-fairy-souls");
		cmd("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
		ctx.waitTicks(10);
	}

	// ---- island guide

	private void guide() {
		ctx.runOnClient(mc -> {
			Taz.config().galatea.guide.enabled = true;
			Taz.config().galatea.guide.destination = IslandGuide.Place.AGATHA;
		});
		Vec3 agatha = IslandGuide.Place.AGATHA.pos;
		cmd(String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f", agatha.x + 25, agatha.y + 5, agatha.z + 10, agatha.x, agatha.y, agatha.z));
		ctx.waitTicks(20);
		ctx.takeScreenshot("galatea-island-guide");
		check("guide: still going", ctx.computeOnClient(mc -> Taz.config().galatea.guide.destination) == IslandGuide.Place.AGATHA);
		cmd(String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f", agatha.x + 1, agatha.y, agatha.z));
		ctx.waitTicks(5);
		check("guide: cleared when you get there", ctx.computeOnClient(mc -> Taz.config().galatea.guide.destination) == IslandGuide.Place.NONE);
		cmd("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
	}

	// ---- tree gifts (lines exactly as in the game log)

	private static final String BAR = "▬".repeat(64);

	private void treeGifts() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.treeGifts.enabled = true;
			Taz.config().foraging.treeGifts.compact = true;
			Taz.config().foraging.treeGifts.rareChance = 1.0f;
			TreeGifts.resetCounts();
		});
		clearAlert();
		int shownBefore = shown.size();
		tellAll("KARMA! Your Tree Gift contained double loot!", BAR, "TREE GIFT", "", "You helped cut 100% of the Fig Tree.",
				"{\"text\":\"+5 rewards gained! (hover)\",\"hover_event\":{\"action\":\"show_text\",\"value\":\"+20x Forest Essence\\nFig Log x96\"}}",
				"", "BONUS GIFT", "Hummingbird Shard (0.15%)", "A Phanpyre fell from the Tree!", BAR);
		check("tree gifts: counted (Fig " + gifts("Fig") + ")", gifts("Fig") == 1
				&& ctx.computeOnClient(mc -> TreeGifts.karma() == 1 && TreeGifts.bonusGifts() == 1 && TreeGifts.mobs() == 1));
		check("tree gifts: rare bonus alert (" + Alert.lastTitle + " / " + Alert.lastSubtitle + ")", Alert.lastTitle != null
				&& Alert.lastTitle.contains("RARE BONUS GIFT") && Alert.lastSubtitle.contains("Hummingbird Shard"));
		List<String> block = shown.subList(shownBefore, shown.size());
		check("tree gifts: the block is hidden (" + block + ")", block.stream().noneMatch(l -> l.contains("TREE GIFT") || l.contains("helped cut")
				|| l.contains("BONUS GIFT") || l.contains("KARMA") || l.contains("▬")));
		String compact = ctx.computeOnClient(mc -> TreeGifts.lastCompact);
		check("tree gifts: one line instead (" + compact + ")", compact != null && compact.contains("TREE GIFT Fig 100%") && compact.contains("+5 rewards")
				&& compact.contains("KARMA") && compact.contains("Hummingbird Shard (0.15%)") && compact.contains("Phanpyre"));
		check("tree gifts: rewards still on hover", ctx.computeOnClient(mc -> TreeGifts.lastCompactHasHover));
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-tree-gift-line");

		int putBack = ctx.computeOnClient(mc -> TreeGifts.barsPutBack);
		tellAll(BAR, "SKYBLOCK LEVEL UP", BAR);
		ctx.waitTicks(5);
		check("tree gifts: other ▬▬▬ lines are put back", ctx.computeOnClient(mc -> TreeGifts.barsPutBack) == putBack + 2);
		tellAll("You helped cut 64% of the Mangrove Tree.");
		check("tree gifts: a lone line outside a gift isn't counted", gifts("Mangrove") == 0);
		tellAll(BAR, "TREE GIFT", "", "You helped cut 64% of the Mangrove Tree.", "+5 rewards gained! (hover)", BAR);
		check("tree gifts: second gift", gifts("Mangrove") == 1);

		int before = shown.size();
		tell("You cannot damage a tree while it is regenerating!");
		check("tree gifts: regenerating spam hidden", shown.subList(before, shown.size()).stream().noneMatch(l -> l.contains("regenerating")));
		tell("HIVE! You found Honeycomb x30 and Enchanted Honeycomb!");
		tell("New buff: Gain +50 Fig Fortune.");
		check("tree gifts: honeycomb and lottery", ctx.computeOnClient(mc -> TreeGifts.honeycomb() == 30 && "Gain +50 Fig Fortune".equals(TreeGifts.lottery())));
		List<String> lines = hudLines(TreeGifts.class);
		check("tree gifts: HUD (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Fig") && l.contains("Mangrove"))
				&& lines.stream().anyMatch(l -> l.contains("KARMA")) && lines.stream().anyMatch(l -> l.contains("Honeycomb"))
				&& lines.stream().anyMatch(l -> l.contains("Lottery")));
		ctx.takeScreenshot("galatea-tree-gifts");
	}

	private int gifts(String tree) {
		return ctx.computeOnClient(mc -> TreeGifts.gifts(tree));
	}

	// ---- Starlyn contests

	private void starlyn() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.starlyn.enabled = true;
			Taz.config().foraging.starlyn.compactClaims = true;
		});
		int before = shown.size();
		tellAll("STARLYN CONTEST REWARDS CLAIMED", "» Agatha Contest on Spring 22nd, Year 521", "REWARDS", "Agatha's Coupon x15",
				"+20x Forest Essence", "+20,000 Foraging Experience", "+200 HOTF Exp", "+1,200 HOTF Exp (Daily Bonus)", "+5,000 Forest Whispers", "+71 Bits",
				"STARLYN CONTEST REWARDS CLAIMED", "» Agatha Contest on Spring 23rd, Year 521", "REWARDS", "Agatha's Coupon x30", "+50x Forest Essence",
				"+50,000 Foraging Experience", "+750 HOTF Exp", "+12,500 Forest Whispers", "+178 Bits", "Agatha's Prize");
		realWait(1500);
		check("starlyn: claims added up", ctx.computeOnClient(mc -> StarlynContest.claimedContests() == 2
				&& StarlynContest.claimed("Agatha's Coupon") == 45 && StarlynContest.claimed("Forest Whispers") == 17_500
				&& StarlynContest.claimed("HOTF Exp") == 2_150 && StarlynContest.claimed("Agatha's Prize") == 1));
		check("starlyn: claim blocks hidden", shown.subList(before, shown.size()).stream().noneMatch(l -> l.contains("STARLYN") || l.contains("Coupon")));
		tell("[NPC] Miria: You earned a total of 4,999 points!");
		check("starlyn: score and bracket", ctx.computeOnClient(mc -> StarlynContest.lastPoints()) == 4999
				&& "EPIC".equals(StarlynContest.bracket("Miria", 4999)) && "LEGENDARY".equals(StarlynContest.bracket("Agatha", 4999)));
		long day = SkyblockEvent.EPOCH_S + 5 * SkyblockEvent.DAY_S;
		check("starlyn: a contest ends 30s before its SkyBlock day", StarlynContest.endsAt(day + 100) == day + SkyblockEvent.DAY_S - 30);
		ctx.runOnClient(mc -> {
			Taz.config().foraging.starlyn.endAlert = true;
			EventTimers.testNowMs = (StarlynContest.endsAt(System.currentTimeMillis() / 1000) - 30) * 1000 + 500;
		});
		clearAlert();
		ctx.waitTicks(3);
		check("starlyn: ending soon alert (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("Contest"));
		List<String> lines = hudLines(StarlynContest.class);
		check("starlyn: HUD (" + lines + ")", lines.get(0).contains("Contest") && (lines.get(0).contains("0:30") || lines.get(0).contains("0:29"))
				&& lines.stream().anyMatch(l -> l.contains("4,999") && l.contains("EPIC")) && lines.stream().anyMatch(l -> l.contains("Claimed 2")));
		ctx.takeScreenshot("galatea-starlyn");
		ctx.runOnClient(mc -> EventTimers.testNowMs = 0);
	}

	// ---- protected and regenerating trees

	private void protectedTrees() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.treePointer.enabled = true;
			Taz.config().foraging.treePointer.skipProtected = true;
			Taz.config().foraging.treePointer.protectedAlert = true;
			ProtectedTrees.forget();
		});
		int x = start.getX(), y = start.getY(), z = start.getZ();
		int ax = x + 6, bx = x + 14;
		cmd("fill " + ax + " " + y + " " + z + " " + (ax + 1) + " " + (y + 4) + " " + (z + 1) + " stripped_spruce_wood");
		cmd("fill " + bx + " " + y + " " + z + " " + (bx + 1) + " " + (y + 4) + " " + (z + 1) + " stripped_spruce_wood");
		cmd("tp @p " + (ax - 2) + " " + y + " " + z + " facing " + ax + " " + (y + 1) + " " + z);
		realWait(5000);
		Integer near = nextTreeX();
		check("protected: the close tree is next (" + near + ")", near != null && near == ax);
		clearAlert();
		tell("You tried to cut the protected Tree and were fined 1,000 Coins by the Tree Protection Agency! Talk to Honey in Torrhus Canyon to learn more!");
		check("protected: alert", Alert.lastTitle != null && Alert.lastTitle.contains("PROTECTED TREE"));
		check("protected: remembered", ctx.computeOnClient(mc -> ProtectedTrees.count()) == 1);
		ctx.waitTicks(15);
		Integer next = nextTreeX();
		check("protected: pointer skips it (" + next + ")", next != null && next == bx);
		cmd("tp @p " + (bx - 2) + " " + y + " " + z + " facing " + bx + " " + (y + 1) + " " + z);
		ctx.waitTicks(5);
		tell("You cannot damage a tree while it is regenerating!");
		ctx.waitTicks(15);
		check("protected: a regenerating tree isn't pointed at", nextTreeX() == null);
		ctx.runOnClient(mc -> ProtectedTrees.forget());
		cmd("fill " + ax + " " + y + " " + z + " " + (bx + 1) + " " + (y + 4) + " " + (z + 1) + " air");
		cmd("tp @p " + x + " " + y + " " + z);
		ctx.runOnClient(mc -> Taz.config().foraging.treePointer.enabled = false);
	}

	private Integer nextTreeX() {
		return ctx.computeOnClient(mc -> {
			TreeTracker.Tree t = TreeTracker.nearestGrown();
			return t == null ? null : t.minX;
		});
	}

	// ---- recorder

	private void recorder() {
		ctx.runOnClient(mc -> com.taz.foraging.GalateaRecorder.testOn = true);
		cmd("title @p actionbar " + new JsonPrimitive("1,000/5,000❤     Pressure: 45%     100/100✎ Mana"));
		cmd("title @p title " + new JsonPrimitive("TOO DEEP!"));
		cmd("scoreboard objectives add taztest dummy \"SKYBLOCK\"");
		cmd("scoreboard objectives setdisplay sidebar taztest");
		cmd("scoreboard players set Contest:1,234 taztest 1");
		ctx.waitTicks(50);
		check("recorder: action bar, title and sidebar lines logged", ctx.computeOnClient(mc ->
				GalateaRecorder.recorded("action bar", "Pressure: 45%") && GalateaRecorder.recorded("title", "TOO DEEP!")
						&& GalateaRecorder.recorded("sidebar", "Contest:1,234")));
		check("recorder: numbers don't make a new line", ctx.computeOnClient(mc -> GalateaRecorder.recorded("action bar", "Pressure: 99%")));
		check("recorder: Taz's own alerts aren't logged", ctx.computeOnClient(mc -> !GalateaRecorder.recorded("title", "PROTECTED TREE")));
		cmd("scoreboard objectives remove taztest");
	}

	private void realWait(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) ctx.waitTick();
	}

	/** Several chat lines in the same tick, like Hypixel sends a block. A line starting with { is raw JSON text. */
	private void tellAll(String... lines) {
		for (String l : lines) cmd("tellraw @a " + (l.startsWith("{") ? l : new JsonPrimitive(l).toString()));
		ctx.waitTicks(3);
	}

	// ---- whisper counter: a HOTF reset refund isn't income

	private void hotfReset() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.whispers.enabled = true;
			WhisperTracker.reset();
			WhisperTracker.onServerChange();
			WhisperTracker.testLines = List.of("Forest Whispers: 10,000", "Desert Whispers: 500");
		});
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> WhisperTracker.testLines = List.of("Forest Whispers: 10,100", "Desert Whispers: 500"));
		ctx.waitTicks(15);
		check("hotf reset: normal gains count", ctx.computeOnClient(mc -> WhisperTracker.gained(WhisperTracker.Type.FOREST)) == 100);
		tell("You have reset your Heart of the Forest! Your Perks and Abilities have been reset.");
		ctx.runOnClient(mc -> WhisperTracker.testLines = List.of("Forest Whispers: 260,100", "Desert Whispers: 90,500"));
		ctx.waitTicks(15);
		check("hotf reset: the refund isn't counted", ctx.computeOnClient(mc -> WhisperTracker.gained(WhisperTracker.Type.FOREST) == 100
				&& WhisperTracker.gained(WhisperTracker.Type.DESERT) == 0));
		ctx.runOnClient(mc -> {
			WhisperTracker.testLines = null;
			WhisperTracker.reset();
			Taz.config().foraging.whispers.enabled = false;
		});
	}

	// ---- contest timing: ends 30 s before the SkyBlock day, and the sidebar timer wins

	private void contestTiming() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.starlyn.enabled = true;
			Taz.config().foraging.starlyn.endAlert = true;
		});
		long day = SkyblockEvent.EPOCH_S + 7 * SkyblockEvent.DAY_S;
		check("contest timing: ends at :x4:30, not :x5:00", StarlynContest.endsAt(day + 60) == day + SkyblockEvent.DAY_S - 30);
		check("contest timing: in the last 30s of the day it's between contests", StarlynContest.between(day + SkyblockEvent.DAY_S - 10)
				&& StarlynContest.endsAt(day + SkyblockEvent.DAY_S - 10) == day + 2 * SkyblockEvent.DAY_S - 30);
		// Live sidebar, as recorded on Hypixel: the alert must use it.
		clearAlert();
		ctx.runOnClient(mc -> {
			EventTimers.testNowMs = 0;
			StarlynContest.readSidebar(List.of("Agatha's Contest 45s", "RARE with 1.2k", "Epic requires +1.3k"), System.currentTimeMillis());
		});
		ctx.waitTicks(3);
		check("contest timing: alert from the sidebar timer (" + Alert.lastSubtitle + ")", Alert.lastSubtitle != null
				&& (Alert.lastSubtitle.contains("45s") || Alert.lastSubtitle.contains("44s")));
		List<String> lines = hudLines(StarlynContest.class);
		check("contest timing: HUD shows the live time and bracket (" + lines + ")", lines.get(0).contains("0:4")
				&& lines.stream().anyMatch(l -> l.contains("RARE with 1.2k") && l.contains("Epic requires +1.3k")));
		ctx.runOnClient(mc -> StarlynContest.readSidebar(List.of("Miria's Contest 16m12s"), System.currentTimeMillis()));
		lines = hudLines(StarlynContest.class);
		check("contest timing: minutes and seconds read (" + lines.get(0) + ")", lines.get(0).contains("16:1"));
		ctx.runOnClient(mc -> Taz.config().foraging.starlyn.endAlert = false);
	}

	// ---- pressure warning

	private void pressure() {
		ctx.runOnClient(mc -> {
			Taz.config().galatea.pressure.enabled = true;
			Taz.config().galatea.pressure.warnAt = 80;
		});
		clearAlert();
		actionBar("1,000/5,000❤     Pressure: 40%     100/100✎ Mana");
		check("pressure: read, no warning at 40%", ctx.computeOnClient(mc -> PressureWarning.last()) == 40 && Alert.lastTitle == null);
		actionBar("1,000/5,000❤     Pressure: 85%     100/100✎ Mana");
		check("pressure: warning at 85% (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("PRESSURE 85%"));
		clearAlert();
		actionBar("1,000/5,000❤     Pressure: 90%     100/100✎ Mana");
		check("pressure: only once while it stays high", Alert.lastTitle == null);
		actionBar("1,000/5,000❤     Pressure: 60%     100/100✎ Mana");
		actionBar("1,000/5,000❤     Pressure: 82%     100/100✎ Mana");
		check("pressure: again after swimming up", Alert.lastTitle != null && Alert.lastTitle.contains("82%"));
	}

	// ---- Tree Route Assist

	private static final String USER_ROUTE = "[Skyblocker-Waypoint-Data-V1]H4sIAAAAAAAA/91aTW/bMAy951cEPmeFqG/tOvQ4wBh8K3oIFgMNltpBPlAUw/77bLdNQ7WjKtubtRjIQVbgiI/kI/mcm9l8/rP5zOdZtbwvs8/zrNiV5XxXHw9ltnjaWe83y2rV7h2rH1X9UL1sPCwft/W6OuybvZvu1svTuu1tfb7RXp+0VYuzNTCDlpqdVrevG6ejQXZ283u9qXdf6vttXZX4CO3Frhh6Ml42u+/+0HKzvVs2T2JXZ8fM9nf1cbP6VlarctdsHnbH8nnz1+JDVhsu0GEkXlogrb4e0WxnLRdKBIyXzjLhOB8VA8EpDBztef7feh5bLdBhuAh4vr/ZratbH4L2wJjC9QDnh7AWYaAVjYEYgoHhSroUEGAUAppEIAoA3vpcad/muBT4GxBoI9GZODoiN4EokEOiQDNjMd80N5/vTgGFQPkAeMkNp6FQA6CwT+ZMnxHaI0Y/HOhy0D8api0HSmPPA+qGGq6iPa8vgAoV7gMcagCb3oRGwAxBQDoGLoF6GICADoL+2T9x6CuMPEhs9ftEfHK8jTK7iSPuJHAvAEA0SaASCACPBhiqTRxoLPqzQI9GoM0YZkc13qDuBziKC84C1c9dRvXDGChc/VggGYBdwkSoBR4BcVJwRvNg/0owLQ9qja3GkRBi/2sYTQyYKPI1H2B+XBVIyOke53l9Hwt0PTBIB2CdGxNwvXEkCLQWMoT3XTcTWx0XAf9kCsa9H6eVgGsYIoZY1VG9TCASHD0A2wAKQ8QAy41UkAIGpDJoDYnBgA6gdzpIw5oIEmOC4Avj3lDwh7bkNRCGSCGunRDAmOlDIYQCXRPH6wg+EAO6u8yoiWBxIniKtXKk9UUeZb7SbRS7BYnJFCHgvR1ReGlpRizyqAahIcC2J1DpoQCUMBKQyYs8qjh2ZMZlchj4wqjAZKDpulDkUbVRdxSoTXIoKKwHA0OghNSiIo8qDPpJLUovFpTyxgbcIwBdGIo8SilS70lmCYDwRibArBAYG4o8SijQUoAwIj0QLD4EeIJRiBqjBmcj3rwrSAQETAoCD5A21ClEjZDJFkmNhXSvSAINwtf1br38OAxvJaMY+9HXR2BDh6V73DWHciBNy2dnTzj9nap43HZHro+Hzboqs1n7xdvfExuQa6slAAA=";

	private void route() {
		ctx.runOnClient(mc -> {
			Taz.config().foraging.route.enabled = true;
			Taz.config().foraging.route.mode = RouteConfig.Mode.NORMAL;
			for (TreeRoutes.Route r : new java.util.ArrayList<>(TreeRoutes.all())) TreeRoutes.delete(r);
		});
		String builtIn = ctx.computeOnClient(mc -> TreeRoutes.all().stream().map(r -> r.name + "/" + r.island + "/" + r.points.size() + "/" + r.builtIn).toList()
				+ " in use " + TreeRoutes.activeOn("foraging_3").name);
		check("route: Taz comes with the Helix route, used by default, and it can't be deleted (" + builtIn + ")",
				builtIn.equals("[Helix route/foraging_3/37/true] in use Helix route"));
		ctx.runOnClient(mc -> {
			mc.keyboardHandler.setClipboard(USER_ROUTE);
			RouteAssist.importFromClipboard();
		});
		TreeRoutes.Route imported = ctx.computeOnClient(mc -> TreeRoutes.all().isEmpty() ? null : TreeRoutes.all().get(0));
		check("route: your export of it is the Helix route, not a second copy", imported != null && imported.name.equals("Helix route")
				&& imported.points.size() == 37 && ctx.computeOnClient(mc -> TreeRoutes.all().size()) == 1 && said("That's Taz's Helix route"));
		check("route: island set to Torrhus Canyon (" + imported.island + ")", "foraging_3".equals(imported.island));
		// Your saved copy from before (named "Tree route", with its best lap) folds into it, and stays the one in use.
		ctx.runOnClient(mc -> {
			try {
				String data = USER_ROUTE.substring(TreeRoutes.PREFIX.length());
				String groups = new String(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data)))
						.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				com.google.gson.JsonArray routes = com.google.gson.JsonParser.parseString(groups).getAsJsonArray();
				routes.get(0).getAsJsonObject().addProperty("name", "Tree route");
				routes.get(0).getAsJsonObject().addProperty("bestLapMs", 75701);
				com.google.gson.JsonObject file = new com.google.gson.JsonObject();
				file.add("routes", routes);
				com.google.gson.JsonObject active = new com.google.gson.JsonObject();
				active.addProperty("foraging_3", "Tree route");
				file.add("active", active);
				java.nio.file.Files.writeString(Taz.CONFIG_DIR.resolve("foraging").resolve("routes.json"), file.toString());
				TreeRoutes.reload();
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		});
		String merged = ctx.computeOnClient(mc -> TreeRoutes.all().stream().map(r -> r.name + "/" + r.bestLapMs).toList() + " in use "
				+ TreeRoutes.activeOn("foraging_3").name);
		check("route: your old copy folds into the Helix route with its best lap (" + merged + ")", merged.equals("[Helix route/75701] in use Helix route"));
		// Another route for the island: imported, it's the one in use; Choose route lists both and switches back.
		ctx.runOnClient(mc -> {
			mc.keyboardHandler.setClipboard("[{\"name\":\"Fig route\",\"island\":\"foraging_3\",\"waypoints\":[" + point(-600, 100, 150, "1") + ","
					+ point(-610, 100, 150, "2") + "]}]");
			RouteAssist.importFromClipboard();
		});
		check("route: an imported route is used on its island", ctx.computeOnClient(mc -> TreeRoutes.activeOn("foraging_3").name).equals("Fig route"));
		ctx.runOnClient(mc -> RouteAssist.chooseRoute());
		ctx.waitTicks(3);
		String chooser = ctx.computeOnClient(mc -> mc.screen instanceof com.taz.foraging.TreeRouteScreen s ? s.children().stream()
				.filter(c -> c instanceof net.minecraft.client.gui.components.Button).map(c -> ((net.minecraft.client.gui.components.Button) c)
						.getMessage().getString() + (((net.minecraft.client.gui.components.Button) c).active ? "" : " [off]")).toList().toString() : "not open");
		check("route: Choose route lists every route, the one in use ticked (" + chooser + ")",
				chooser.contains("Helix route · Torrhus Canyon · 11 trees · best 1:15.7 · Taz") && chooser.contains("✔ Fig route · Torrhus Canyon · 2 trees [off]"));
		ctx.runOnClient(mc -> RouteAssist.useRoute(TreeRoutes.all().get(0)));
		check("route: picking the Helix route uses it", ctx.computeOnClient(mc -> TreeRoutes.activeOn("foraging_3").name).equals("Helix route"));
		ctx.runOnClient(mc -> {
			mc.setScreen(null);
			for (TreeRoutes.Route r : new java.util.ArrayList<>(TreeRoutes.all())) TreeRoutes.delete(r);
		});
		check("route: kinds (1 = tree, E1 = etherwarp, TP1 = teleport)", imported.points.get(0).kind() == TreeRoutes.Kind.TREE
				&& imported.points.get(1).kind() == TreeRoutes.Kind.WARP && imported.points.get(26).kind() == TreeRoutes.Kind.TELEPORT);
		String exported = ctx.computeOnClient(mc -> {
			RouteAssist.exportToClipboard();
			return mc.keyboardHandler.getClipboard();
		});
		check("route: export is Skyblocker's format", exported.startsWith(TreeRoutes.PREFIX));
		List<TreeRoutes.Route> again = ctx.computeOnClient(mc -> {
			try {
				return TreeRoutes.parse(exported, "unknown");
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		});
		check("route: export reads back the same", again.get(0).points.equals(imported.points) && "foraging_3".equals(again.get(0).island));
		check("route: islands told apart", Islands.guess(-685, 160).equals("foraging_3") && Islands.guess(-600, 0).equals("foraging_2")
				&& Islands.guess(0, 0).equals("unknown") && Islands.name("foraging_3").equals("Torrhus Canyon"));
		ctx.runOnClient(mc -> TreeRoutes.delete(TreeRoutes.all().get(0)));

		// A short route next to you, as plain JSON: tree 1, two etherwarps, tree 2, a teleport, then the end.
		int x = start.getX(), y = start.getY(), z = start.getZ();
		String json = "[{\"name\":\"Test route\",\"island\":\"unknown\",\"waypoints\":["
				+ point(x + 4, y, z, "1") + "," + point(x + 14, y, z, "E1") + "," + point(x + 22, y, z, "E2") + ","
				+ point(x + 30, y, z, "2") + "," + point(x + 40, y, z, "TP1") + "," + point(x + 48, y, z, "Miria") + "]}]";
		cmd("fill " + (x + 5) + " " + y + " " + z + " " + (x + 6) + " " + (y + 5) + " " + (z + 1) + " stripped_spruce_wood");
		cmd("fill " + (x + 31) + " " + y + " " + z + " " + (x + 32) + " " + (y + 5) + " " + (z + 1) + " stripped_spruce_wood");
		ctx.runOnClient(mc -> {
			mc.keyboardHandler.setClipboard(json);
			RouteAssist.importFromClipboard();
			Taz.config().foraging.route.mode = RouteConfig.Mode.TRAINING;
			Taz.config().foraging.route.reach = 2;
			Taz.config().foraging.route.endStops = true;
			RouteAssist.restart();
		});
		cmd("tp @p " + x + " " + y + " " + (z - 6) + " facing " + (x + 12) + " " + y + " " + z);
		ctx.waitTicks(5);
		List<String> lines = hudLines(RouteAssist.class);
		check("route: waits for the first tree (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Go to Tree 1 to start")));
		goTo(x + 4, y, z);
		check("route: at tree 1, chop it", ctx.computeOnClient(mc -> RouteAssist.atTree()) && hudLines(RouteAssist.class).stream().anyMatch(l -> l.contains("Chop Tree 1")));
		cmd("tp @p " + (x + 1) + " " + (y + 2) + " " + (z - 5) + " facing " + (x + 22) + " " + y + " " + z);
		ctx.waitTicks(3);
		cmd("tp @p " + (x + 4) + " " + y + " " + z);
		ctx.waitTicks(3);
		ctx.takeScreenshot("galatea-route-training");
		cmd("fill " + (x + 5) + " " + y + " " + z + " " + (x + 6) + " " + (y + 5) + " " + (z + 1) + " air");
		ctx.waitTicks(30);
		lines = hudLines(RouteAssist.class);
		check("route: tree 1's logs gone, next is tree 2 via E1 and E2 (" + lines + ")", !ctx.computeOnClient(mc -> RouteAssist.atTree())
				&& lines.stream().anyMatch(l -> l.startsWith("Next: Tree 2")) && lines.stream().anyMatch(l -> l.equals("Warp: E1 → E2")));
		cmd("tp @p " + (x + 8) + " " + (y + 2) + " " + (z - 6) + " facing " + (x + 26) + " " + y + " " + z);
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-route-next-tree");
		goTo(x + 14, y, z);
		check("route: E1 ticked off", hudLines(RouteAssist.class).stream().anyMatch(l -> l.equals("Warp: E2")));
		goTo(x + 30, y, z);
		check("route: at tree 2", ctx.computeOnClient(mc -> RouteAssist.atTree()) && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 3);
		tell("You helped cut 100% of the Fig Tree.");
		lines = hudLines(RouteAssist.class);
		check("route: tree gift message moves on to the end stops (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Miria") && l.contains("end of lap"))
				&& lines.stream().anyMatch(l -> l.equals("Warp: TP1")));
		goTo(x + 48, y, z);
		long lap = ctx.computeOnClient(mc -> RouteAssist.lastLapMs());
		check("route: lap finished and timed (" + lap + " ms)", lap > 0 && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 0
				&& ctx.computeOnClient(mc -> TreeRoutes.active().bestLapMs) == lap);

		// Lap 2: tree 1 isn't grown (its logs are gone), so it's skipped by itself; tree 2 says it's regenerating.
		goTo(x + 4, y, z);
		check("route: lap 2 at tree 1", ctx.computeOnClient(mc -> RouteAssist.atTree()) && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 0);
		ctx.waitTicks(30);
		lines = hudLines(RouteAssist.class);
		check("route: a tree that isn't grown is skipped, with a split (" + lines + ")", !ctx.computeOnClient(mc -> RouteAssist.atTree())
				&& ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 1 && lines.contains("Skipped Tree 1 (not grown)")
				&& lines.stream().anyMatch(l -> l.startsWith("Next: Tree 2")) && lines.stream().anyMatch(l -> l.startsWith("Tree 1 ")));
		goTo(x + 30, y, z);
		tell("You cannot damage a tree while it is regenerating!");
		lines = hudLines(RouteAssist.class);
		check("route: a regenerating tree is skipped (" + lines + ")", !ctx.computeOnClient(mc -> RouteAssist.atTree())
				&& lines.contains("Skipped Tree 2 (regenerating)") && lines.stream().anyMatch(l -> l.contains("Miria")));

		// End stops off: leaving a tree moves on, and the lap ends at the last tree.
		cmd("fill " + (x + 5) + " " + y + " " + z + " " + (x + 6) + " " + (y + 5) + " " + (z + 1) + " stripped_spruce_wood");
		ctx.runOnClient(mc -> {
			Taz.config().foraging.route.endStops = false;
			RouteAssist.restart();
		});
		goTo(x + 4, y, z);
		goTo(x + 4, y, z - 20);
		check("route: leaving the tree moves on", !ctx.computeOnClient(mc -> RouteAssist.atTree()) && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 1);
		ctx.runOnClient(mc -> Taz.config().foraging.route.restartKey = org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4);
		ctx.getInput().holdKeyFor(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4, 2);
		ctx.waitTicks(3);
		check("route: restart key", ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 0
				&& hudLines(RouteAssist.class).stream().anyMatch(l -> l.contains("Go to Tree 1 to start")));
		goTo(x + 4, y, z);
		goTo(x + 30, y, z);
		check("route: no end stops, tree 2 is the last", ctx.computeOnClient(mc -> RouteAssist.atTree()) && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 3);
		tell("You helped cut 100% of the Fig Tree.");
		long lap3 = ctx.computeOnClient(mc -> RouteAssist.lastLapMs());
		lines = hudLines(RouteAssist.class);
		check("route: the lap ends at the last tree and starts over (" + lines + ")", lap3 != lap && ctx.computeOnClient(mc -> RouteAssist.nextIndex()) == 0
				&& lines.stream().anyMatch(l -> l.contains("Go to Tree 1 to start")));
		cmd("fill " + (x + 5) + " " + y + " " + z + " " + (x + 6) + " " + (y + 5) + " " + (z + 1) + " air");
		ctx.runOnClient(mc -> {
			Taz.config().foraging.route.restartKey = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
			Taz.config().foraging.route.mode = RouteConfig.Mode.NORMAL;
		});
		cmd("tp @p " + x + " " + (y + 3) + " " + (z - 14) + " facing " + (x + 24) + " " + y + " " + z);
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-route-normal");
		cmd("fill " + (x + 31) + " " + y + " " + z + " " + (x + 32) + " " + (y + 5) + " " + (z + 1) + " air");
		ctx.runOnClient(mc -> {
			for (TreeRoutes.Route r : new java.util.ArrayList<>(TreeRoutes.all())) TreeRoutes.delete(r);
			Taz.config().foraging.route.enabled = false;
		});
		cmd("tp @p " + x + " " + y + " " + z);
	}

	// ---- Critter Safari

	private void safari() {
		ctx.runOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			cfg.enabled = true;
			cfg.highlight = SafariConfig.Highlight.RUN;
			cfg.caught.clear();
			SafariHelper.testWhere = SafariHelper.Where.ENTRANCE;
		});
		ctx.waitTicks(10);
		List<String> lines = hudLines(SafariGuide.class);
		check("safari: entrance guide (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Safari Manager"))
				&& lines.stream().anyMatch(l -> l.contains("Hunting XV")) && lines.stream().anyMatch(l -> l.contains("none with you")));
		check("safari: entrance tip in chat", said("way into the Critter Safari"));

		ctx.runOnClient(mc -> {
			SafariHelper.testWhere = SafariHelper.Where.INSIDE;
			SafariHelper.testBiome = Critters.Biome.FOREST;
			SafariHelper.reset();
			Taz.config().safari.callouts = true;
			com.taz.util.Party.reset();
			com.taz.util.TazChat.PARTY_RECENT.clear();
		});
		int x = start.getX() + 5, y = start.getY(), z = start.getZ();
		String mob = " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"taztest\"]}";
		cmd("summon fox " + x + " " + y + " " + (z - 6) + mob);
		label(x, y + 1.0, z - 6, "Foxtrot");
		cmd("summon frog " + x + " " + y + " " + (z - 3) + mob);
		label(x, y + 0.8, z - 3, "§9Treefrog \uE001"); // a colour and an icon glyph, as Hypixel's tags have
		cmd("summon parrot " + x + " " + y + " " + z + mob);
		label(x, y + 1.0, z, "SPARKLING Bluebird");
		cmd("summon armor_stand " + x + " " + y + " " + (z + 3)
				+ " {Invisible:1b,NoGravity:1b,equipment:{head:{id:\"minecraft:player_head\",count:1}},Tags:[\"taztest\"]}");
		label(x, y + 2.2, z + 3, "Cavernfish");
		cmd("summon item_display " + x + " " + (y + 0.5) + " " + (z + 6) + " {item:{id:\"minecraft:green_shulker_box\",count:1},Tags:[\"taztest\"]}");
		label(x, y + 1.3, z + 6, "Hideonfloor");
		cmd("summon silverfish " + x + " " + y + " " + (z + 9) + mob);
		label(x, y + 1.0, z + 9, "Litterbug");
		label(x + 0.5, y + 1.0, z + 9, "CAPTURING"); // a catch going on: not one to find
		label(x, y + 1.0, z + 12, "Tepid Shard"); // not a critter
		cmd("tp @p " + (x - 8) + " " + (y + 1) + " " + z + " facing " + x + " " + y + " " + z);
		clearAlert();
		ctx.waitTicks(15);

		String seen = ctx.computeOnClient(mc -> SafariHelper.sightings().stream()
				.map(s -> s.critter().name() + (s.sparkling() ? "*" : "") + ":" + (s.body() == null ? "none" : s.body().getType().toShortString()))
				.sorted().toList().toString());
		check("safari: critters found with their bodies (" + seen + ")",
				seen.equals("[Bluebird*:parrot, Cavernfish:armor_stand, Foxtrot:fox, Hideonfloor:item_display, Treefrog:frog]"));
		String boxes = ctx.computeOnClient(mc -> SafariHelper.sightings().stream().sorted(java.util.Comparator.comparing(s -> s.critter().name()))
				.map(s -> s.critter().name() + ":" + String.format(java.util.Locale.ROOT, "%.2f", SafariHelper.box(s).getYsize())).toList().toString());
		check("safari: boxes round the bodies, block-sized for a display (" + boxes + ")", boxes.contains("Hideonfloor:0.90")
				&& boxes.contains("Foxtrot:0.80") && boxes.contains("Treefrog:0.60"));
		String glows = glows();
		String red = Integer.toHexString(SafariHelper.RED), gold = Integer.toHexString(SafariHelper.GOLD);
		check("safari: the critters you need glow (" + glows + ")", glows.contains("fox=" + red) && glows.contains("frog=" + red)
				&& glows.contains("parrot=" + gold) && glows.contains("armor_stand=" + red) && glows.contains("item_display=" + red)
				&& glows.contains("silverfish=no"));
		check("safari: Sparkling alert (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("SPARKLING"));
		check("safari: no Sparkling callout when you're not in a party", com.taz.util.TazChat.PARTY_RECENT.isEmpty());
		tell("Party > [MVP+] Bob: hi");
		label(x, y + 1.0, z + 15, "SPARKLING Treefrog");
		ctx.waitTicks(10);
		String callout = String.valueOf(com.taz.util.TazChat.PARTY_RECENT);
		check("safari: in a party, a Sparkling one is called out with where it is (" + callout + ")",
				callout.equals("[Sparkling Treefrog in the Forest biome at " + x + ", " + y + ", " + (z + 15) + "]")
						|| callout.startsWith("[Sparkling Treefrog in the Forest biome at " + x + ", "));
		ctx.runOnClient(mc -> {
			com.taz.util.TazChat.clearParty();
			com.taz.util.Party.reset();
		});
		check("safari: welcome and biome tips", said("Welcome to the Critter Safari")
				&& said("Forest Biome: 9 critters"));
		lines = hudLines(SafariGuide.class);
		check("safari: guide HUD (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Forest (you're here) 9 left"))
				&& lines.stream().anyMatch(l -> l.contains("Macaw - feed the Birdfeeder a Bag of Seeds"))
				&& lines.stream().anyMatch(l -> l.contains("Cavern 9 | Haunted 10 | Icy 9")));

		tell("CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!");
		tell("LOOT SHARE! You received a Treefrog Shard from Steve catching a Treefrog!");
		tell("Party > [MVP+] Bob: CAPTURE! You caught a Hideonfloor and gained a Hideonfloor Shard!");
		ctx.waitTicks(6);
		glows = glows();
		check("safari: caught ones stop glowing, a quoted catch doesn't count (" + glows + ")", glows.contains("fox=no")
				&& glows.contains("frog=no") && glows.contains("item_display=" + red));
		check("safari: your own catch is remembered, a party member's isn't", ctx.computeOnClient(mc ->
				Taz.config().safari.caught.equals(List.of("Foxtrot"))) && said("Your first Foxtrot!"));
		lines = hudLines(SafariGuide.class);
		List<String> afterCatch = lines;
		check("safari: guide counts the catches (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Forest (you're here) 7 left"))
				&& lines.stream().noneMatch(l -> l.startsWith(" Foxtrot")) && afterCatch.get(0).contains("2/37"));

		ctx.runOnClient(mc -> Taz.config().safari.highlight = SafariConfig.Highlight.MINE);
		ctx.waitTicks(6);
		glows = glows();
		check("safari: never-caught-by-me mode (" + glows + ")", glows.contains("fox=no") && glows.contains("frog=" + red));
		ctx.runOnClient(mc -> Taz.config().safari.highlight = SafariConfig.Highlight.RUN);

		safariThrow(x, y, z);

		tell("HOTSPOT! Your Hunting Hotspot is the Icy Biome!");
		clearAlert();
		tell("Two Macaws were attracted to the Birdfeeder!");
		check("safari: Macaw alert (" + Alert.lastTitle + ")", Alert.lastTitle != null && Alert.lastTitle.contains("MACAW"));
		cmd("give @p paper[custom_name=\"Bag of Seeds\"]");
		cmd("give @p player_head[custom_data={id:\"CRITTER_CAPSULE\"},custom_name=\"Critter Capsule\"] 4");
		ctx.waitTicks(25);
		check("safari: quest item and low capsule tips", said("You have a Bag of Seeds: feed it to the Birdfeeder")
				&& said("Only 4 Critter Capsules left"));
		lines = hudLines(SafariGuide.class);
		check("safari: HUD shows capsules, hotspot and quest items (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Capsules 4")
				&& l.contains("Hotspot Icy")) && lines.stream().anyMatch(l -> l.startsWith("Bag of Seeds: feed it to the Birdfeeder")));

		tell("The Shyworm surfaced nearby! Don't let it see you!");
		tell("You hear the sound of massive footsteps echoing through the Icy Biome... What could it be?");
		tell("[MOB] Hideyho: No peeking!");
		ctx.waitTicks(5);
		lines = hudLines(SafariGuide.class);
		check("safari: countdowns on the HUD (" + lines + ")", lines.stream().anyMatch(l -> l.matches("Shyworm up 0:(1\\d|20) - don't let it see you"))
				&& lines.stream().anyMatch(l -> l.matches("Wumpa wakes in ~0:(1\\d|20) - in the Polaris cave"))
				&& lines.stream().anyMatch(l -> l.startsWith("Hideyho hiding 0:0")));
		ctx.takeScreenshot("galatea-safari");
		tell("The Shyworm hid back into the ground.");
		tell("The Wumpa has awoken.");
		tell("[MOB] Hideyho: Aah! You found me!");
		ctx.waitTicks(5);
		lines = hudLines(SafariGuide.class);
		check("safari: ...gone when they end, the Wumpa awake (" + lines + ")", lines.stream().noneMatch(l -> l.startsWith("Shyworm up")
				|| l.startsWith("Wumpa wakes") || l.startsWith("Hideyho")) && lines.stream().anyMatch(l -> l.startsWith("Wumpa awake 0:0")));

		for (String c : List.of("Bluebird", "Honeybug", "Woodchucker", "Fluffling", "Hideonfloor", "Parakeet")) {
			tell("CAPTURE! You caught a " + c + " and gained a " + c + " Shard!");
		}
		check("safari: Forest done except the Macaw", said("Forest is done except the Macaw"));
		tell("CAPTURE! You caught a Macaw and gained 2x Macaw Shard!");
		check("safari: biome done, go to your hotspot next", said("Forest Biome is done! Next: the Icy Biome (9 left)."));
		ctx.waitTicks(3);
		lines = hudLines(SafariGuide.class);
		check("safari: a quest item for caught critters leaves the HUD (" + lines + ")", lines.stream().noneMatch(l -> l.startsWith("Bag of Seeds")));
		ctx.waitTicks(6);
		glows = glows();
		check("safari: a Sparkling one glows even when caught (" + glows + ")", glows.contains("parrot=" + gold) && glows.contains("item_display=no"));

		ctx.runOnClient(mc -> SafariHelper.testBiome = null);
		ctx.waitTicks(6);
		lines = hudLines(SafariGuide.class);
		check("safari: no biome known, done biomes left out (" + lines + ")", lines.stream().anyMatch(l -> l.contains("Walk into a biome"))
				&& lines.contains("Cavern 9 | Haunted 10 | Icy 9"));

		ctx.runOnClient(mc -> Taz.config().safari.bestCaught = 0);
		tell("  SAFARI REWARD SUMMARY");
		tell("");
		tell("    +43 Shards");
		tell("    +268 Safari Essence");
		tell("    +318,977 Hunting Exp");
		check("safari: run report (" + com.taz.util.TazChat.RECENT + ")", said("Safari report: you caught 8, your party 9/37 in ")
				&& said("+268 Safari Essence, +318,977 Hunting XP, +43 shards.") && said("Cavernfish (seen, not caught)")
				&& said("Gemzie (needed Purple Gem, Lime Gem, Orange Gem)") && said("Wumpa (it wakes once the rest of Icy is caught)")
				&& said("Litterbug (not found)") && ctx.computeOnClient(mc -> Taz.config().safari.bestCaught) == 9);

		// The Critterdex shows caught critters as themselves, the rest as "???".
		ctx.setScreen(() -> {
			net.minecraft.world.entity.player.Inventory inv = net.minecraft.client.Minecraft.getInstance().player.getInventory();
			net.minecraft.world.SimpleContainer dex = new net.minecraft.world.SimpleContainer(54);
			dex.setItem(10, menuItem("Polaris", null));
			dex.setItem(11, menuItem("???", null));
			dex.setItem(12, menuItem("Snoozle", "Not caught yet"));
			return new net.minecraft.client.gui.screens.inventory.ContainerScreen(net.minecraft.world.inventory.ChestMenu.sixRows(1, inv, dex), inv,
					net.minecraft.network.chat.Component.literal("Critterdex"));
		});
		ctx.waitTicks(25);
		ctx.setScreen(() -> null);
		ctx.waitTicks(12);
		String dexCaught = ctx.computeOnClient(mc -> Taz.config().safari.caught.toString());
		check("safari: Critterdex sync (" + dexCaught + ")", dexCaught.contains("Polaris") && !dexCaught.contains("Snoozle")
				&& said("Critterdex synced: Polaris now counts as caught"));

		cmd("kill @e[tag=taztest]");
		cmd("clear @p paper");
		cmd("clear @p player_head");
		ctx.runOnClient(mc -> {
			SafariHelper.testWhere = null;
			SafariHelper.testBiome = null;
			Taz.config().safari.caught.clear();
			Taz.config().safari.callouts = false;
			Taz.config().safari.bestCaught = 0;
			Taz.config().safari.enabled = false;
		});
		cmd("tp @p " + start.getX() + " " + y + " " + z);
		ctx.waitTicks(5);
	}

	/** The capsule throw preview: the arc ends on the critter in the way or at a wall, and real throws teach it the arc. */
	private void safariThrow(int x, int y, int z) {
		ctx.runOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			cfg.throwPreview = true;
			cfg.throwsMeasured = 0;
		});
		cmd("item replace entity @p weapon.mainhand with player_head[custom_data={id:\"CRITTER_CAPSULE\"},custom_name=\"Critter Capsule\"] 4");
		// The Cavernfish (an armour stand) 6 blocks ahead, aimed at its middle from your eyes (tp's facing is from the feet).
		cmd("tp @p " + (x - 6) + " " + y + " " + (z + 3) + " facing " + x + " " + (y - 0.6) + " " + (z + 3));
		ctx.waitTicks(10);
		String arc = ctx.computeOnClient(mc -> {
			SafariThrow.Arc a = SafariThrow.arc();
			return a == null ? "none" : (a.hits() == null ? "nothing" : a.hits().critter().name()) + " after " + (a.points().size() - 1) + " ticks";
		});
		check("safari throw: holding a capsule, the arc ends on the critter in the way (" + arc + ")", arc.startsWith("Cavernfish after "));
		cmd("tp @p " + (x - 9) + " " + y + " " + (z + 6) + " facing " + (x - 40) + " " + (y + 4) + " " + (z + 6));
		clearAlert();
		ctx.runOnClient(mc -> {
			mc.gui.getChat().clearMessages(false);
			mc.gui.clearTitles();
		});
		ctx.runOnClient(mc -> mc.options.hideGui = true);
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-safari-throw");
		ctx.runOnClient(mc -> mc.options.hideGui = false);
		cmd("tp @p " + (x - 6) + " " + y + " " + (z + 3) + " facing " + x + " " + (y - 0.6) + " " + (z + 3));
		ctx.waitTicks(3);
		cmd("fill " + (x - 3) + " " + y + " " + (z + 2) + " " + (x - 3) + " " + (y + 3) + " " + (z + 4) + " stone");
		ctx.waitTicks(5);
		arc = ctx.computeOnClient(mc -> {
			SafariThrow.Arc a = SafariThrow.arc();
			return a == null ? "none" : (a.hits() == null ? "nothing" : a.hits().critter().name()) + " at x " + Math.round(a.points().get(a.points().size() - 1).x);
		});
		check("safari throw: ...and at a wall in the way (" + arc + ")", arc.equals("nothing at x " + (x - 3)));
		cmd("fill " + (x - 3) + " " + y + " " + (z + 2) + " " + (x - 3) + " " + (y + 3) + " " + (z + 4) + " air");

		// The fit: 1 block a tick, no drag, 0.05 gravity; the server moved it only every other tick at the start, and it
		// hit something at the end (stopped dead), which isn't flight.
		List<Vec3> flight = new java.util.ArrayList<>();
		Vec3 p = Vec3.ZERO, v = new Vec3(1, 0, 0);
		for (int t = 0; t < 12; t++) {
			flight.add(t == 1 ? Vec3.ZERO : p);
			p = p.add(v);
			v = v.subtract(0, 0.05, 0);
		}
		for (int t = 0; t < 3; t++) flight.add(flight.get(flight.size() - 1).add(0.05, 0, 0));
		double[] f = SafariThrow.fit(flight);
		String fit = f == null ? "none" : String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", f[0], f[1], f[2]);
		check("safari throw: speed, drag and gravity from a flight (" + fit + ")", fit.equals("1.000 1.000 0.050"));

		// A real throw: right after you throw, a capsule appears at your eyes and flies (0.8 a tick, 0.04 gravity).
		Vec3 eye = ctx.computeOnClient(mc -> mc.player.getEyePosition());
		ctx.runOnClient(mc -> SafariThrow.onUse(mc.player));
		Vec3 at = eye.add(0, 0, -0.5);
		cmd(String.format(java.util.Locale.ROOT, "summon item_display %.3f %.3f %.3f {item:{id:\"minecraft:player_head\",count:1},Tags:[\"cap\"]}", at.x, at.y, at.z));
		Vec3 speed = new Vec3(0, 0.2, -0.8);
		for (int t = 0; t < 10; t++) {
			ctx.waitTicks(1);
			at = at.add(speed);
			speed = speed.subtract(0, 0.04, 0);
			cmd(String.format(java.util.Locale.ROOT, "tp @e[tag=cap,limit=1] %.3f %.3f %.3f", at.x, at.y, at.z));
		}
		ctx.waitTicks(2);
		cmd("kill @e[tag=cap]");
		ctx.waitTicks(3);
		String learnt = ctx.computeOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			return cfg.throwsMeasured + String.format(java.util.Locale.ROOT, " %.2f %.3f %.3f", cfg.throwSpeed, cfg.throwDrag, cfg.throwGravity);
		});
		String[] parts = learnt.split(" ");
		check("safari throw: a throw is followed and its arc learnt (" + learnt + ")", parts[0].equals("1")
				&& Math.abs(Double.parseDouble(parts[1]) - Math.hypot(0.8, 0.2)) < 0.1 && Double.parseDouble(parts[2]) > 0.97
				&& Math.abs(Double.parseDouble(parts[3]) - 0.04) < 0.01);
		cmd("item replace entity @p weapon.mainhand with air");
		ctx.runOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			cfg.throwPreview = false;
			cfg.throwsMeasured = 0;
		});
		cmd("tp @p " + (x - 8) + " " + (y + 1) + " " + z + " facing " + x + " " + y + " " + z);
		ctx.waitTicks(5);
	}

	private static net.minecraft.world.item.ItemStack menuItem(String name, String lore) {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
		if (lore != null) {
			stack.set(net.minecraft.core.component.DataComponents.LORE,
					new net.minecraft.world.item.component.ItemLore(List.of(net.minecraft.network.chat.Component.literal(lore))));
		}
		return stack;
	}

	/** Full clear: every critter in your hotspot counts, caught or not, and the route stays there. */
	private void fullClear() {
		ctx.runOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			cfg.enabled = true;
			cfg.fullClear = true;
			cfg.zone = SafariConfig.Zone.AUTO;
			cfg.highlight = SafariConfig.Highlight.RUN;
			cfg.caught.clear();
			SafariHelper.testWhere = SafariHelper.Where.INSIDE;
			SafariHelper.testBiome = Critters.Biome.FOREST;
			SafariHelper.reset();
		});
		int x = start.getX() + 5, y = start.getY(), z = start.getZ();
		String mob = " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"taztest\"]}";
		cmd("summon fox " + x + " " + y + " " + (z - 3) + mob);
		label(x, y + 1.0, z - 3, "Foxtrot");
		cmd("summon frog " + x + " " + y + " " + z + mob);
		label(x, y + 0.8, z, "Treefrog");
		cmd("summon armor_stand " + x + " " + y + " " + (z + 3)
				+ " {Invisible:1b,NoGravity:1b,equipment:{head:{id:\"minecraft:player_head\",count:1}},Tags:[\"taztest\"]}");
		label(x, y + 2.2, z + 3, "Cavernfish");
		cmd("tp @p " + (x - 8) + " " + (y + 1) + " " + z + " facing " + x + " " + y + " " + z);
		tell("HOTSPOT! Your Hunting Hotspot is the Forest Biome!");
		ctx.waitTicks(15);
		check("full clear: your hotspot is the biome", ctx.computeOnClient(mc -> SafariHelper.fullClearBiome()) == Critters.Biome.FOREST);
		tell("CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!");
		tell("CAPTURE! You caught a Treefrog and gained a Treefrog Shard!");
		ctx.waitTicks(6);
		String glows = glows();
		String red = Integer.toHexString(SafariHelper.RED);
		check("full clear: hotspot critters still glow once caught, others don't (" + glows + ")", glows.contains("fox=" + red)
				&& glows.contains("frog=" + red) && glows.contains("armor_stand=no"));
		check("full clear: no 'biome done' talk", !said("Biome is done"));
		// The Treefrog you caught goes: it isn't somewhere to go back to.
		cmd("kill @e[type=frog,tag=taztest]");
		cmd("kill @e[type=armor_stand,name=\"Treefrog\"]");
		ctx.waitTicks(12);
		String lost = ctx.computeOnClient(mc -> SafariHelper.lastSeen().stream().map(s -> s.critter().name()).toList().toString());
		check("full clear: a caught critter isn't kept as last seen (" + lost + ")", !lost.contains("Treefrog"));
		tell("CAPTURE! You caught a Foxtrot and gained 2x Foxtrot Shard!");
		ctx.waitTicks(6);
		List<String> lines = hudLines(SafariGuide.class);
		check("full clear: the HUD counts your catches there (" + lines + ")", lines.get(0).startsWith("Full clear Forest")
				&& lines.stream().anyMatch(l -> l.startsWith("Caught here 3  In view 1")) && lines.stream().anyMatch(l -> l.startsWith(" Foxtrot x2 - "))
				&& lines.stream().anyMatch(l -> l.startsWith(" Treefrog x1 - ")));
		// Somewhere else, the route still goes back to the hotspot.
		ctx.runOnClient(mc -> SafariHelper.testBiome = Critters.Biome.CAVERN);
		ctx.waitTicks(12);
		lines = hudLines(SafariGuide.class);
		check("full clear: the route stays on your hotspot (" + lines + ")", ctx.computeOnClient(mc -> com.taz.foraging.safari.SafariWay.zone())
				== Critters.Biome.FOREST && lines.contains("Head to the Forest Biome"));
		ctx.runOnClient(mc -> Taz.config().safari.bestCaught = 0);
		tell("  SAFARI REWARD SUMMARY");
		tell("    +318,977 Hunting Exp");
		check("full clear: the run report counts each catch (" + com.taz.util.TazChat.RECENT + ")",
				said("Full clear: you caught 3 critters: Foxtrot x2, Treefrog."));
		cmd("kill @e[tag=taztest]");
		ctx.runOnClient(mc -> {
			SafariHelper.testWhere = null;
			SafariHelper.testBiome = null;
			Taz.config().safari.fullClear = false;
			Taz.config().safari.caught.clear();
			Taz.config().safari.bestCaught = 0;
			Taz.config().safari.enabled = false;
		});
		ctx.waitTicks(5);
	}

	private void label(double x, double y, double z, String name) {
		cmd(String.format(java.util.Locale.ROOT, "summon armor_stand %.2f %.2f %.2f {CustomName:\"%s\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,"
				+ "Marker:1b,Tags:[\"taztest\"]}", x, y, z, name));
	}

	/** "fox=ff2e2e frog=no ..." for the test entities, from the glow the game draws. */
	private String glows() {
		return ctx.computeOnClient(mc -> {
			StringBuilder sb = new StringBuilder();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.player.Player || e.hasCustomName()) continue;
				int color = com.taz.render.Glow.color(e);
				boolean glowing = mc.shouldEntityAppearGlowing(e) && e.getTeamColor() == color;
				sb.append(e.getType().toShortString()).append('=').append(color >= 0 && glowing ? Integer.toHexString(color) : "no").append(' ');
			}
			return sb.toString().trim();
		});
	}

	// ---- Critter Safari: the route through each biome, floor drops and Rockmite mounds

	private void safariWay() {
		SafariMap.Walk toRitual = SafariMap.path(new Vec3(-51.5, 69, 21.5), SafariMap.matching(n -> n.is("Ritual")).stream()
				.map(SafariMap.Node::pos).toList());
		Vec3 ritual = SafariMap.matching(n -> n.is("Ritual")).get(0).pos();
		check("safari way: the map loads (" + SafariMap.nodes().size() + " points)", SafariMap.nodes().size() > 1000);
		check("safari way: a walk from the Safari Manager to the Ritual (" + (toRitual == null ? "none" : Math.round(toRitual.length()) + "m, "
				+ toRitual.points().size() + " points") + ")", toRitual != null && toRitual.points().get(toRitual.points().size() - 1).equals(ritual)
				&& toRitual.length() > ritual.distanceTo(new Vec3(-51.5, 69, 21.5)) && toRitual.points().size() > 5);
		String zones = "Birdfeeder=" + zoneOf("Birdfeeder") + " Ritual=" + zoneOf("Ritual") + " Gem Podiums=" + zoneOf("Gem Podiums")
				+ " Polaris cave=" + SafariMap.zoneAt(new Vec3(-123.5, 81, -88.5));
		check("safari way: map points know their biome (" + zones + ")",
				zones.equals("Birdfeeder=FOREST Ritual=HAUNTED Gem Podiums=CAVERN Polaris cave=ICY"));

		ctx.runOnClient(mc -> {
			SafariConfig cfg = Taz.config().safari;
			cfg.enabled = true;
			cfg.highlight = SafariConfig.Highlight.RUN;
			cfg.zone = SafariConfig.Zone.AUTO;
			SafariHelper.testWhere = SafariHelper.Where.INSIDE;
			SafariHelper.testBiome = Critters.Biome.FOREST;
			SafariHelper.reset();
		});
		ctx.waitTicks(15);
		String goal = goalName();
		check("safari way: in the Forest with no bait, it searches for the roaming critters or goes to a floor drop (" + goal + ")",
				goal.startsWith("Search the Forest") || goal.startsWith("Floor drop spot"));

		int x = start.getX() + 3, y = start.getY(), z = start.getZ() + 4;
		cmd("summon fox " + (x + 6) + " " + y + " " + (z - 8) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		label(x + 6.5, y + 1.0, z - 7.5, "Foxtrot");
		ctx.waitTicks(15);
		goal = goalName();
		check("safari way: a critter you still need in view comes first (" + goal + ")", goal.equals("Foxtrot"));
		cmd("kill @e[type=fox]");
		cmd("kill @e[type=armor_stand]");
		ctx.waitTicks(15);
		goal = goalWithWhy();
		String lost = ctx.computeOnClient(mc -> SafariHelper.lastSeen().stream().map(s -> s.critter().name() + "@" + BlockPos.containing(s.pos())).toList().toString());
		check("safari way: gone out of view, it goes to where it was last seen (" + goal + ", " + lost + ")",
				goal.startsWith("Foxtrot - last seen here ") && lost.equals("[Foxtrot@" + new BlockPos(x + 6, y, z - 8) + "]"));
		cmd("tp @p " + (x + 6) + " " + (y + 1) + " " + (z - 6));
		ctx.waitTicks(15);
		goal = goalName();
		check("safari way: ...and once you're there and it isn't, it's forgotten (" + goal + ")",
				!goal.equals("Foxtrot") && ctx.computeOnClient(mc -> SafariHelper.lastSeen().isEmpty()));
		cmd("summon silverfish " + (x - 3) + " " + y + " " + (z - 1) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		label(x - 2.5, y + 0.8, z - 0.5, "Cavernfish"); // a Cavern critter right next to you
		ctx.waitTicks(15);
		goal = goalName();
		check("safari way: working the Forest, a Cavern critter close by isn't gone for (" + goal + ")", !goal.equals("Cavernfish")
				&& ctx.computeOnClient(mc -> SafariHelper.sightings().stream().anyMatch(s -> s.critter().name().equals("Cavernfish"))));
		cmd("kill @e[type=silverfish]");
		cmd("kill @e[type=armor_stand]");
		cmd("tp @p " + (x - 3) + " " + (y + 1) + " " + (z - 1)); // where it was: so it isn't left as last seen
		ctx.waitTicks(10);

		cmd("give @p paper[custom_name=\"Yogi Berry\"]");
		ctx.waitTicks(30);
		goal = ctx.computeOnClient(mc -> SafariWay.goal() == null ? "none" : SafariWay.goal().name() + " - " + SafariWay.goal().why());
		check("safari way: with bird bait, the Birdfeeder (" + goal + ")", goal.equals("Bluebird - feed the Birdfeeder your bait"));
		List<String> lines = hudLines(SafariGuide.class);
		check("safari way: HUD says what's next (" + lines + ")", lines.stream().anyMatch(l -> l.startsWith("Next: Bluebird - feed the Birdfeeder")));
		// Once it's picked something, a critter coming into view on the way doesn't pull it off.
		cmd("summon fox " + (x + 2) + " " + y + " " + (z - 2) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		label(x + 2.5, y + 1.0, z - 1.5, "Woodchucker");
		ctx.waitTicks(15);
		goal = goalName();
		check("safari way: sticks with what it picked (" + goal + ")", goal.equals("Bluebird")
				&& ctx.computeOnClient(mc -> SafariHelper.sightings().stream().anyMatch(s -> s.critter().name().equals("Woodchucker"))));
		cmd("kill @e[type=fox]");
		cmd("kill @e[type=armor_stand]");
		ctx.runOnClient(mc -> SafariWay.clear());

		ctx.runOnClient(mc -> Taz.config().safari.zone = SafariConfig.Zone.CAVERN);
		ctx.waitTicks(15);
		String cavern = ctx.computeOnClient(mc -> SafariWay.zone() + " " + (SafariWay.goal() == null ? "none" : SafariMap.zoneAt(SafariWay.goal().pos())));
		lines = hudLines(SafariGuide.class);
		check("safari way: a picked biome is done first (" + cavern + ", " + lines + ")", cavern.equals("CAVERN CAVERN")
				&& lines.stream().anyMatch(l -> l.startsWith("Next: (Cavern)")));

		// A Scrappy's armour stops capsules until it's had its food 4 times; the food is one of three, picked each Safari.
		cmd("summon silverfish " + (x + 5) + " " + y + " " + (z - 6) + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		label(x + 5.5, y + 0.8, z - 5.5, "Scrappy");
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> SafariWay.clear());
		ctx.waitTicks(5);
		goal = goalName();
		check("safari way: a Scrappy in view but no food: get food first (" + goal + ")",
				List.of("Wholesale Wheat", "Flavor-packed Fish", "Lush Lily Pad").contains(goal));
		ctx.runOnClient(mc -> SafariSpots.testFoodAnywhere = true);
		setblock(new BlockPos(x - 3, y, z - 3), "lily_pad");
		ctx.waitTicks(20);
		check("safari way: lily pads found for Scrappy food", ctx.computeOnClient(mc -> SafariSpots.LILY_PADS.count()) == 1);
		cmd("item replace entity @p weapon.mainhand with paper[custom_name=\"Lush Lily Pad\"] 7");
		ctx.waitTicks(30);
		goal = goalWithWhy();
		check("safari way: with Lily Pads, go feed the Scrappy (" + goal + ")", goal.equals("Scrappy - feed it Lush Lily Pad (4 times), then throw"));
		tell("This particular Scrappy doesn't seem to care for this type of food...");
		ctx.waitTicks(15);
		goal = goalName();
		check("safari way: refused Lily Pads: the other foods (" + goal + ")", said("Not Lush Lily Pad. Try Wholesale Wheat or Flavor-packed Fish.")
				&& List.of("Wholesale Wheat", "Flavor-packed Fish").contains(goal));
		cmd("give @p paper[custom_name=\"Wholesale Wheat\"] 4");
		ctx.waitTicks(30);
		goal = goalWithWhy();
		check("safari way: with Wheat, feed that (" + goal + ")", goal.equals("Scrappy - feed it Wholesale Wheat (4 times), then throw"));
		tell("The Scrappy started eating the Wholesale Wheat.");
		check("safari way: the food it eats is learnt", said("This run's Scrappies eat Wholesale Wheat"));
		tell("The Scrappy ate the Wholesale Wheat and came out of its shell!");
		ctx.waitTicks(15);
		goal = goalWithWhy();
		check("safari way: out of its shell, catch it (" + goal + ")", goal.equals("Scrappy - it's out of its shell: catch it!")
				&& said("It's out of its shell"));
		tell("Your Critter Capsule can't get through Scrappy's armor!");
		check("safari way: the armour message explained", said("A Scrappy has to be fed before a capsule works"));
		cmd("kill @e[type=silverfish]");
		cmd("kill @e[type=armor_stand]");
		setblock(new BlockPos(x - 3, y, z - 3), "air");
		ctx.runOnClient(mc -> SafariSpots.testFoodAnywhere = false);
		ctx.runOnClient(mc -> Taz.config().safari.zone = SafariConfig.Zone.AUTO);

		cmd("clear @p paper");
		tell("HOTSPOT! Your Hunting Hotspot is the Icy Biome!");
		ctx.waitTicks(15);
		String hot = ctx.computeOnClient(mc -> SafariWay.zone() + " " + (SafariWay.goal() == null ? "none" : SafariMap.zoneAt(SafariWay.goal().pos())));
		check("safari way: your hotspot comes first, even standing in the Forest (" + hot + ")", hot.equals("ICY ICY"));
		for (Critters.Critter c : Critters.in(Critters.Biome.FOREST)) {
			if (!c.name().equals("Macaw")) tell("CAPTURE! You caught a " + c.name() + " and gained a " + c.name() + " Shard!");
		}
		ctx.runOnClient(mc -> Taz.config().safari.zone = SafariConfig.Zone.FOREST);
		ctx.waitTicks(30);
		String next = ctx.computeOnClient(mc -> String.valueOf(SafariWay.zone()));
		check("safari way: a picked biome done but the Macaw moves on (" + next + ")", next.equals("ICY"));

		// The Doomspiral takes a Soothing Incense for each of the Ritual's 4 candles: with fewer, floor drops first.
		for (Critters.Critter c : Critters.in(Critters.Biome.HAUNTED)) {
			if (!c.name().equals("Doomspiral")) tell("CAPTURE! You caught a " + c.name() + " and gained a " + c.name() + " Shard!");
		}
		ctx.runOnClient(mc -> {
			Taz.config().safari.zone = SafariConfig.Zone.HAUNTED;
			SafariHelper.testBiome = Critters.Biome.HAUNTED;
		});
		cmd("give @p paper[custom_name=\"Soothing Incense\"]");
		ctx.waitTicks(30);
		goal = goalWithWhy();
		lines = hudLines(SafariGuide.class);
		check("safari way: 1 Soothing Incense of 4: floor drops, not the Ritual (" + goal + ", " + lines + ")",
				goal.equals("Floor drop spot - for 3 more Soothing Incense")
				&& lines.contains(" Doomspiral - light 4 ritual candles with Soothing Incense (1/4 incense: find 3 more)"));
		cmd("give @p paper[custom_name=\"Soothing Incense\"] 3");
		ctx.waitTicks(30);
		goal = goalWithWhy();
		check("safari way: with all 4, the Ritual (" + goal + ")", goal.equals("Doomspiral - light a candle with your Soothing Incense"));
		tell("You used the Soothing Incense to light the candle! You begin to feel uneasy.");
		tell("You used the Soothing Incense to light the candle! Something is off about this place...");
		cmd("clear @p paper 2");
		ctx.waitTicks(30);
		goal = goalWithWhy();
		lines = hudLines(SafariGuide.class);
		check("safari way: candles lit count towards the 4 (" + goal + ", " + lines + ")",
				goal.equals("Doomspiral - light a candle with your Soothing Incense")
				&& lines.contains(" Doomspiral - light 4 ritual candles with Soothing Incense (2 lit, 2/2 incense: go light them)"));
		tell("Your ritual summoned a Doomspiral into this world. Stay still.");
		ctx.waitTicks(15);
		goal = goalWithWhy();
		check("safari way: summoned: catch it at the Ritual (" + goal + ")", goal.equals("Doomspiral - summoned at the Ritual: catch it!"));
		// Gemzies take all 3 gems, one on each podium.
		cmd("clear @p paper");
		cmd("give @p paper[custom_name=\"Purple Gem\"]");
		ctx.waitTicks(30);
		String gems = ctx.computeOnClient(mc -> SafariHelper.stillToFind("Gemzie").toString());
		tell("You placed the Purple Gem on its podium!");
		cmd("clear @p paper");
		ctx.waitTicks(30);
		gems += " " + ctx.computeOnClient(mc -> SafariHelper.stillToFind("Gemzie").toString());
		check("safari way: Gemzies need all 3 gems, placed ones count (" + gems + ")",
				gems.equals("[Lime Gem, Orange Gem] [Lime Gem, Orange Gem]"));
		ctx.runOnClient(mc -> {
			Taz.config().safari.zone = SafariConfig.Zone.AUTO;
			SafariHelper.testBiome = Critters.Biome.FOREST;
		});

		// Hypixel draws the strings off their entity's position with the display's transformation: half a block up here.
		String lifted = ",transformation:{translation:[0f,0.5f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f]}";
		for (int i = 0; i < 3; i++) {
			cmd(String.format(java.util.Locale.ROOT, "summon item_display %.2f %.2f %.2f {item:{id:\"minecraft:string\",count:1}%s,Tags:[\"drop\"]}",
					x + 0.3 * i + 0.2, y + 0.1, z + 0.5, lifted));
		}
		for (int i = 0; i < 2; i++) { // two strings are scenery, not a drop
			cmd(String.format(java.util.Locale.ROOT, "summon item_display %.2f %.2f %.2f {item:{id:\"minecraft:string\",count:1}}", x + 0.3 * i + 0.2, y + 0.1, z + 6.5));
		}
		String texture = java.util.Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
				+ SafariFinds.MOUND_TEXTURE + "\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		cmd("summon item_display " + (x + 4) + " " + (y + 0.5) + " " + (z + 3) + " {item:{id:\"minecraft:player_head\",count:1,components:{"
				+ "\"minecraft:profile\":{properties:[{name:\"textures\",value:\"" + texture + "\"}]}}}}");
		cmd("tp @p " + (x - 6) + " " + (y + 1) + " " + (z + 2) + " facing " + x + " " + y + " " + (z + 2));
		ctx.waitTicks(30);
		String finds = ctx.computeOnClient(mc -> "drops=" + SafariFinds.drops().size() + " mounds=" + SafariFinds.mounds().size());
		check("safari finds: a floor drop (3 strings) and a Rockmite mound (" + finds + ")", finds.equals("drops=1 mounds=1"));
		goal = goalName();
		Vec3 end = ctx.computeOnClient(mc -> SafariWay.walk() == null ? Vec3.ZERO : SafariWay.walk().points().get(SafariWay.walk().points().size() - 1));
		double length = ctx.computeOnClient(mc -> SafariWay.walk() == null ? -1 : SafariWay.walk().length());
		check("safari way: a floor drop close by is grabbed on the way, straight there (" + goal + " at " + end + ", " + length + "m)",
				goal.equals("Floor drop") && end.distanceTo(new Vec3(x + 0.5, y + 0.6, z + 0.5)) < 0.05 && length > 0 && length < 12);

		// A wall in the way: the path goes round it over the blocks, not through it like a tracer.
		cmd("fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x - 3) + " " + (y + 2) + " " + (z + 6) + " stone");
		ctx.waitTicks(15);
		String around = ctx.computeOnClient(mc -> {
			SafariMap.Walk w = SafariWay.walk();
			if (w == null) return "none";
			boolean through = false;
			for (int i = 1; i < w.points().size(); i++) {
				Vec3 a = w.points().get(i - 1), b = w.points().get(i);
				for (double f = 0; f <= 1; f += 0.05) {
					BlockPos at = BlockPos.containing(a.lerp(b, f).add(0, 0.2, 0));
					if (mc.level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.STONE)) through = true;
				}
			}
			return Math.round(w.length()) + "m, " + w.points().size() + " points" + (through ? ", through the wall" : "");
		});
		check("safari way: round a wall, not through it (" + around + ")", !around.contains("through") && !around.equals("none")
				&& Integer.parseInt(around.substring(0, around.indexOf('m'))) > 8);
		ctx.takeScreenshot("galatea-safari-way");
		cmd("fill " + (x - 3) + " " + y + " " + (z - 3) + " " + (x - 3) + " " + (y + 2) + " " + (z + 6) + " air");

		cmd("tp @p " + (x + 0.5) + " " + (y + 1) + " " + (z - 2.5) + " facing " + (x + 0.5) + " " + y + " " + (z + 0.5));
		ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
		ctx.waitTicks(5);
		ctx.takeScreenshot("galatea-safari-drop");
		Vec3 drop = new Vec3(x + 0.5, y + 0.6, z + 0.5);
		ctx.runOnClient(mc -> SafariFinds.onInteract(drop));
		check("safari finds: a drop you hit is gone straight away", ctx.computeOnClient(mc -> SafariFinds.drops().isEmpty()));
		ctx.waitTicks(70);
		check("safari finds: ...and back if its strings are still there 3s later", ctx.computeOnClient(mc -> SafariFinds.drops().size()) == 1);
		cmd("kill @e[type=item_display,tag=drop,limit=1]");
		ctx.waitTicks(2);
		check("safari finds: a drop whose strings go is gone that tick", ctx.computeOnClient(mc -> SafariFinds.drops().isEmpty()));

		Vec3 walkway = ctx.computeOnClient(mc -> SafariSpots.task("Gimmiegold").spots().get(0));
		check("safari way: a Shining Coin goes to the mansion basement (" + walkway + ")", walkway.y() < 66);
		cmd("fill 3 59 -56 7 61 -54 glass hollow"); // a pool, so the water stays put
		for (int wx = 4; wx <= 6; wx++) setblock(new BlockPos(wx, 60, -55), "water");
		ctx.waitTicks(50);
		ctx.runOnClient(mc -> SafariSpots.task("Gimmiegold"));
		ctx.waitTicks(2);
		Vec3 water = ctx.computeOnClient(mc -> SafariSpots.task("Gimmiegold").spots().get(0));
		check("safari way: ...and once it's loaded, the middle of its water (" + water + ")", water.equals(new Vec3(5.5, 60.5, -54.5)));
		cmd("fill 3 59 -56 7 61 -54 air");

		// Gazers come from the haunted house's beds: a bed anywhere else (here, one by the Birdfeeder) isn't gone to.
		BlockPos mansionBed = new BlockPos(-4, 70, -46), forestBed = new BlockPos(-2, 90, 44);
		setblock(mansionBed, "red_bed[part=head]");
		setblock(forestBed, "red_bed[part=head]");
		ctx.waitTicks(20);
		String beds = ctx.computeOnClient(mc -> SafariSpots.BEDS.count() + " beds, Gazer to " + SafariSpots.task("Gazer").spots());
		check("safari way: Gazers: only the haunted house's beds (" + beds + ")",
				beds.equals("2 beds, Gazer to [" + Vec3.atBottomCenterOf(mansionBed) + "]"));
		setblock(mansionBed, "air");
		setblock(forestBed, "air");

		cmd("kill @e[type=item_display]");
		ctx.runOnClient(mc -> {
			SafariHelper.testWhere = null;
			SafariHelper.testBiome = null;
			Taz.config().safari.caught.clear();
			Taz.config().safari.enabled = false;
		});
		cmd("tp @p " + start.getX() + " " + y + " " + start.getZ());
		ctx.waitTicks(5);
	}

	private String goalWithWhy() {
		return ctx.computeOnClient(mc -> SafariWay.goal() == null ? "none" : SafariWay.goal().name() + " - " + SafariWay.goal().why());
	}

	private String goalName() {
		return ctx.computeOnClient(mc -> SafariWay.goal() == null ? "none" : SafariWay.goal().name());
	}

	private static String zoneOf(String place) {
		return String.valueOf(SafariMap.zoneOf(SafariMap.matching(n -> n.is(place)).get(0)));
	}

	/** Taz said this in chat. */
	private static boolean said(String text) {
		return com.taz.util.TazChat.RECENT.stream().anyMatch(l -> l.contains(text));
	}

	private static String point(int x, int y, int z, String name) {
		return "{\"pos\":[" + x + "," + y + "," + z + "],\"name\":\"" + name + "\",\"colorComponents\":[0.0,1.0,0.0],\"alpha\":0.5}";
	}

	private void goTo(int x, int y, int z) {
		cmd("tp @p " + x + " " + y + " " + z);
		ctx.waitTicks(4);
	}

	private void actionBar(String text) {
		cmd("title @p actionbar " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	// ---- helpers

	private List<String> hudLines(Class<?> type) {
		return ctx.computeOnClient(mc -> {
			for (MovableHud h : TazHud.all()) {
				if (type.isInstance(h)) return ((TextHud) h).shownLines(false).stream().map(net.minecraft.ChatFormatting::stripFormatting).toList();
			}
			throw new AssertionError(type.getSimpleName() + " not registered");
		});
	}

	private static void clearAlert() {
		Alert.lastTitle = null;
		Alert.lastSubtitle = null;
	}

	private void setblock(BlockPos p, String block) {
		cmd("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + block);
	}

	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("galatea: " + what);
		System.out.println("[GalateaGameTest] ok: " + what);
	}
}
