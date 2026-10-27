package com.taz.gametest;

import com.google.gson.JsonPrimitive;
import com.taz.Taz;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.stream.Stream;

/** Quality-of-life features from Odin and NoammAddons. */
public class QolGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"chatcommands", "chattweaks", "shortcuts", "slotbinds", "loadouts", "autorequeue", "renderopt", "tweaks",
			"helditem", "blockoverlay", "soundvolumes", "leapmenu", "partyfinder", "auctioninput", "runextras", "dungeonmap", "mapinfo", "darkmode",
			"storage", "predictor"};
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "qol");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Stream.of(PARTS).noneMatch(QolGameTest::runs)) {
			System.out.println("[TestFilter] skipped qol");
			return;
		}
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("gamemode creative @p");
			ctx.waitTicks(20);
			if (runs("chatcommands")) chatCommands();
			if (runs("chattweaks")) chatTweaks();
			if (runs("shortcuts")) shortcuts();
			if (runs("slotbinds")) slotBinds();
			if (runs("loadouts")) loadouts();
			if (runs("autorequeue")) autoRequeue();
			if (runs("renderopt")) renderOptimizer();
			if (runs("tweaks")) tweaks();
			if (runs("helditem")) heldItem();
			if (runs("blockoverlay")) blockOverlay();
			if (runs("soundvolumes")) soundVolumes();
			if (runs("leapmenu")) leapMenu();
			if (runs("partyfinder")) partyFinder();
			if (runs("auctioninput")) auctionInput();
			if (runs("runextras")) runExtras();
			if (runs("dungeonmap")) dungeonMap();
			if (runs("mapinfo")) mapInfo();
			if (runs("darkmode")) darkMode();
			if (runs("storage")) storage();
			if (runs("predictor")) predictor();
		}
		System.out.println("[QolGameTest] PASSED");
	}

	private String me() {
		return ctx.computeOnClient(mc -> mc.player.getName().getString());
	}

	private void chatCommands() {
		ctx.runOnClient(mc -> {
			Taz.config().chat.chatCommands.enabled = true;
			com.taz.general.ChatCommands.clear();
		});
		int before = ctx.computeOnClient(mc -> TazChat.sent());
		tell("Party > [MVP+] Friend: !cf");
		ctx.waitTicks(12);
		check("chat commands: !cf answered in party chat", ctx.computeOnClient(mc -> TazChat.sent()) > before);
		ctx.runOnClient(mc -> TazChat.clearParty());
		tell("Party > [MVP+] Friend: !taz");
		ctx.waitTicks(12);
		check("chat commands: !taz sends the Discord invite (" + com.taz.general.ChatCommands.lastReply + ")",
				"Join the official Taz Discord: https://discord.gg/D4rUnmbpJr".equals(com.taz.general.ChatCommands.lastReply));
		tell("Party > [VIP] Friend: !dt need food");
		ctx.waitTicks(12);
		check("chat commands: !dt sets a downtime reminder", ctx.computeOnClient(mc -> com.taz.general.ChatCommands.downtime()));
		tell("Party > [VIP] Friend: !undt");
		ctx.waitTicks(12);
		check("chat commands: !undt removes it", !ctx.computeOnClient(mc -> com.taz.general.ChatCommands.downtime()));
		tell("Party > [VIP] Friend: hello !cf");
		ctx.waitTicks(12);
		check("chat commands: only messages starting with !", ctx.computeOnClient(mc -> com.taz.general.ChatCommands.pending()) == 0);
		tell("Party Members (3)");
		tell("Party Leader: [MVP+] Friend ●");
		tell("Party Members: [VIP] Other ● " + me() + " ●");
		check("party: leader and members from /p list", "Friend".equals(com.taz.util.Party.leader())
				&& ctx.computeOnClient(mc -> com.taz.util.Party.members().contains("Other") && !com.taz.util.Party.maybeLeader()));
		tell("You left the party.");
		check("party: gone after leaving", com.taz.util.Party.leader() == null);
		ctx.runOnClient(mc -> Taz.config().chat.chatCommands.enabled = false);
	}

	private void chatTweaks() {
		ctx.runOnClient(mc -> Taz.config().chat.chat.enabled = true);
		check("chat: emoji codes", ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emoji("gg :skull: <3 o/")).equals("gg ☠ ❤ ( ﾟ◡ﾟ)/"));
		check("chat: emoji in /pc only", ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emojiCommand("pc <3")).equals("pc ❤")
				&& ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emojiCommand("warp <3")).equals("warp <3"));
		tell("the newest message");
		String at = ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.messageAt(10, mc.getWindow().getGuiScaledHeight() - 42));
		check("chat: the message under the mouse (" + at + ")", "the newest message".equals(at));
		ctx.runOnClient(mc -> Taz.config().chat.chat.enabled = false);
	}

	private void shortcuts() {
		ctx.runOnClient(mc -> Taz.config().chat.commandShortcuts.enabled = true);
		check("shortcuts: /dn is /warp dhub", ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dn")).equals("warp dhub")
				&& ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dn now")).equals("warp dhub now")
				&& ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dnx")).equals("dnx"));
		ctx.runOnClient(mc -> Taz.config().chat.commandShortcuts.enabled = false);
	}

	private void slotBinds() {
		cmd("gamemode survival @p");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			Taz.config().controls.slotBinds.enabled = true;
			Taz.config().controls.slotBinds.bindKey = GLFW.GLFW_KEY_I;
			Taz.config().controls.slotBinds.binds.clear();
			mc.setScreen(new InventoryScreen(mc.player));
		});
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
			com.taz.general.InventoryKeys.onKey(s, s.getMenu().getSlot(9), GLFW.GLFW_KEY_I);
			com.taz.general.InventoryKeys.onKey(s, s.getMenu().getSlot(37), GLFW.GLFW_KEY_I);
		});
		check("slot binds: slot 9 bound to hotbar slot 37 (" + ctx.computeOnClient(mc -> mc.screen.getClass().getSimpleName()) + ")",
				Integer.valueOf(37).equals(ctx.computeOnClient(mc -> Taz.config().controls.slotBinds.binds.get("9"))));
		ctx.takeScreenshot("qol-slot-binds");
		ctx.runOnClient(mc -> {
			mc.setScreen(null);
			Taz.config().controls.slotBinds.enabled = false;
		});
		cmd("gamemode creative @p");
	}

	private void loadouts() {
		ctx.runOnClient(mc -> Taz.config().controls.loadoutKeybinds.enabled = true);
		cmd("setblock 2 -60 2 chest{CustomName:\"(1/2) Loadout\"}");
		cmd("tp @p 2 -59 0");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(2, -60, 2)), net.minecraft.core.Direction.UP,
						new BlockPos(2, -60, 2), false)));
		ctx.waitTicks(10);
		check("loadouts: 1 picks the first loadout, the right arrow the next page", ctx.computeOnClient(mc -> mc.screen instanceof AbstractContainerScreen<?> s
				&& com.taz.general.InventoryKeys.onKey(s, null, GLFW.GLFW_KEY_1) && com.taz.general.InventoryKeys.onKey(s, null, GLFW.GLFW_KEY_RIGHT)
				&& !com.taz.general.InventoryKeys.onKey(s, null, GLFW.GLFW_KEY_LEFT)));
		ctx.runOnClient(mc -> {
			mc.player.closeContainer();
			Taz.config().controls.loadoutKeybinds.enabled = false;
		});
	}

	private void autoRequeue() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.autoRequeue.enabled = true;
			Taz.config().dungeons.autoRequeue.delaySeconds = 5;
			DungeonLocation.setTestFloor("M7");
		});
		ctx.waitTicks(25);
		tell("                             > EXTRA STATS <");
		check("auto requeue: the same floor queued (" + com.taz.dungeons.info.AutoRequeue.pending() + ")",
				"joininstance MASTER_CATACOMBS_FLOOR_SEVEN".equals(com.taz.dungeons.info.AutoRequeue.pending()));
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.autoRequeue.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
		ctx.waitTicks(25);
	}

	private void renderOptimizer() {
		ctx.runOnClient(mc -> Taz.config().visuals.renderOptimizer.enabled = true);
		cmd("summon experience_orb ~ ~1 ~3 {Value:5}");
		ctx.waitTicks(5);
		check("render optimizer: XP orbs hidden", ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
				mc.player.getBoundingBox().inflate(10)).stream().findFirst().map(com.taz.render.Hidden::hide).orElse(false)));
		check("render optimizer: explosions and screen fire", ctx.computeOnClient(mc -> com.taz.general.RenderOptimizer.hideParticle(
				net.minecraft.core.particles.ParticleTypes.EXPLOSION) && com.taz.general.RenderOptimizer.hideFire()));
		cmd("kill @e[type=experience_orb]");
		ctx.runOnClient(mc -> Taz.config().visuals.renderOptimizer.enabled = false);
	}

	private void tweaks() {
		ctx.runOnClient(mc -> {
			Taz.config().controls.camera.enabled = true;
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(3);
		check("camera: no front view", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
		ctx.runOnClient(mc -> {
			Taz.config().controls.camera.enabled = false;
			Taz.config().visuals.arrowFix.enabled = true;
		});
		check("arrow fix: a shortbow", ctx.computeOnClient(mc -> {
			ItemStack bow = new ItemStack(Items.BOW);
			bow.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Shortbow: Instantly shoots!"), Component.literal("LEGENDARY BOW"))));
			return com.taz.general.Tweaks.noPullback(bow) && !com.taz.general.Tweaks.noPullback(new ItemStack(Items.BOW));
		}));
		ctx.runOnClient(mc -> {
			Taz.config().visuals.arrowFix.enabled = false;
			Taz.config().controls.autoSprint.enabled = true;
		});
		cmd("tp @p -20 -60 -20 180 0");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
		ctx.waitTicks(10);
		check("auto sprint: walking is sprinting", ctx.computeOnClient(mc -> mc.player.isSprinting()));
		ctx.runOnClient(mc -> {
			mc.options.keyUp.setDown(false);
			Taz.config().controls.autoSprint.enabled = false;
		});
		ctx.waitTicks(5);
	}

	private void heldItem() {
		ctx.runOnClient(mc -> {
			var c = Taz.config().visuals.heldItem;
			c.enabled = true;
			c.scale = -0.45f;
			c.x = -0.18f;
			c.y = 0.18f;
			c.swingSpeed = -1.15f;
			c.ignoreHaste = true;
		});
		check("held item: slower swing (18 ticks)", ctx.computeOnClient(mc -> com.taz.general.HeldItem.swingTicks(0, 0)) == 18);
		cmd("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.waitTicks(5);
		ctx.takeScreenshot("qol-held-item");
		ctx.runOnClient(mc -> Taz.config().visuals.heldItem.enabled = false);
		cmd("item replace entity @p weapon.mainhand with air");
	}

	private void blockOverlay() {
		ctx.runOnClient(mc -> Taz.config().visuals.blockOverlay.enabled = true);
		cmd("setblock 0 -60 3 stone");
		cmd("tp @p 0 -59 0 0 30");
		ctx.waitTicks(10);
		check("block overlay: replaces the outline", ctx.computeOnClient(mc -> com.taz.general.BlockOverlay.replaces()));
		ctx.takeScreenshot("qol-block-overlay");
		ctx.runOnClient(mc -> Taz.config().visuals.blockOverlay.enabled = false);
	}

	private void soundVolumes() {
		ctx.runOnClient(mc -> Taz.config().visuals.soundVolumes.enabled = true);
		check("sound volumes: amethyst at 1.35", ctx.computeOnClient(mc -> com.taz.general.SoundVolumes.multiplier("minecraft:block.amethyst_block.break")) == 1.35f
				&& ctx.computeOnClient(mc -> com.taz.general.SoundVolumes.multiplier("minecraft:block.stone.break")) == 1f);
		ctx.runOnClient(mc -> {
			Taz.config().visuals.soundVolumes.enabled = false;
			Taz.config().visuals.etherwarp.enabled = true;
			Taz.config().visuals.etherwarp.sound = true;
		});
		check("etherwarp sound: Hypixel's swapped for the XP orb", ctx.computeOnClient(mc -> com.taz.general.EtherwarpGuess.onSound("entity.ender_dragon.hurt", 0.53968257f)));
		ctx.runOnClient(mc -> Taz.config().visuals.etherwarp.enabled = false);
	}

	private String head(int slot, String name) {
		return "{Slot:" + slot + "b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"" + name + "\"}}";
	}

	private void openChest(int x, int y, int z) {
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(x, y, z)), net.minecraft.core.Direction.UP,
						new BlockPos(x, y, z), false)));
		ctx.waitTicks(10);
	}

	private void leapMenu() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.leapMenu.enabled = true;
			Taz.config().dungeons.leapMenu.message = "ILY ❤ {name}";
			com.taz.dungeons.util.DungeonParty.setClass(mc.player.getName().getString(), "Archer");
			com.taz.dungeons.util.DungeonParty.setClass("Alice", "Mage");
			com.taz.dungeons.util.DungeonParty.setClass("Bob", "Tank");
			com.taz.dungeons.util.DungeonParty.setClass("Cara", "Healer");
			com.taz.dungeons.util.DungeonParty.setClass("Dan", "Berserk");
		});
		cmd("tp @p 6 -60 0");
		cmd("setblock 6 -60 2 chest{CustomName:\"Spirit Leap\",Items:[" + head(11, "[MVP+] Alice") + "," + head(12, "[VIP] Bob") + "," + head(13, "Cara") + ","
				+ head(14, "[MVP++] Dan") + "]}");
		ctx.waitTicks(25);
		openChest(6, -60, 2);
		check("leap menu: the Spirit Leap menu", ctx.computeOnClient(mc -> mc.screen instanceof AbstractContainerScreen<?> s && com.taz.dungeons.info.LeapMenu.active(s)));
		check("leap menu: placed by class like Odin (Mage, Berserk, Healer, Tank for an Archer)", ctx.computeOnClient(mc -> {
			var e = com.taz.dungeons.info.LeapMenu.entries((AbstractContainerScreen<?>) mc.screen);
			return e[0].name().equals("Alice") && e[1].name().equals("Dan") && e[2].name().equals("Cara") && e[3].name().equals("Bob") && e[0].slot() == 11;
		}));
		ctx.takeScreenshot("qol-leap-menu");
		check("leap menu: key 1 leaps", ctx.computeOnClient(mc -> com.taz.dungeons.info.LeapMenu.onKey((AbstractContainerScreen<?>) mc.screen, GLFW.GLFW_KEY_1)));
		ctx.runOnClient(mc -> mc.player.closeContainer());
		int before = ctx.computeOnClient(mc -> TazChat.sent());
		tell("You have teleported to Alice!");
		check("leap menu: says who you leaped to", ctx.computeOnClient(mc -> TazChat.sent()) > before);
		ctx.runOnClient(mc -> {
			TazChat.clearParty();
			Taz.config().dungeons.leapMenu.enabled = false;
		});
	}

	private void partyFinder() {
		ctx.runOnClient(mc -> {
			var c = Taz.config().dungeons.partyFinder;
			c.enabled = true;
			c.floor = 6;
			c.master = true;
			c.maxSeconds = 150;
		});
		String member = "{\"stats\":{\"dungeons\":{\"catacombsLevel\":50,\"classAverage\":48.4,\"secretsFound\":56139,"
				+ "\"floors\":{\"total\":{\"completions\":722}},\"masterFloors\":{\"6\":{\"fastestTimeSPlus\":126961},\"total\":{\"completions\":4338}}},"
				+ "\"accessories\":{\"highestMagicalPower\":2048}}}";
		check("party finder: stats read", ctx.computeOnClient(mc -> {
			var st = com.taz.dungeons.info.PartyFinder.parse("mTaz", com.google.gson.JsonParser.parseString(member).getAsJsonObject());
			return st.cata() == 50 && st.mp() == 2048 && st.pbMs() == 126961 && Math.abs(st.secretsPerRun() - 56139.0 / 5060) < 0.01
					&& com.taz.dungeons.info.PartyFinder.describe(st).contains("M6 S+ 2:06")
					&& com.taz.dungeons.info.PartyFinder.shortfall(st) == null;
		}));
		check("party finder: too slow for 2:30 when M6 is 2:31", ctx.computeOnClient(mc -> com.taz.dungeons.info.PartyFinder.shortfall(
				new com.taz.dungeons.info.PartyFinder.Stats("x", 40, 40, 1000, 10, 900, 151000)) != null));
		check("party finder: missing classes from the lore", ctx.computeOnClient(mc -> {
			ItemStack h = new ItemStack(Items.PLAYER_HEAD);
			h.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(" Friend: Archer (50)"), Component.literal(" Other: Mage (48)"))));
			return com.taz.dungeons.info.PartyFinder.missing(h).equals(List.of("Berserk", "Healer", "Tank"));
		}));
		ctx.runOnClient(mc -> Taz.config().dungeons.partyFinder.enabled = false);
	}

	private void auctionInput() {
		ctx.runOnClient(mc -> Taz.config().inventory.auctionInput.enabled = true);
		check("auction input: 10m, 2.5k, 1,000", ctx.computeOnClient(mc -> com.taz.general.AuctionInput.parse("10m")) == 10_000_000L
				&& ctx.computeOnClient(mc -> com.taz.general.AuctionInput.parse("2.5k")) == 2500L
				&& ctx.computeOnClient(mc -> com.taz.general.AuctionInput.parse("1,000")) == 1000L
				&& ctx.computeOnClient(mc -> com.taz.general.AuctionInput.parse("abc")) == null);
		cmd("tp @p 10 -60 0");
		cmd("setblock 10 -60 2 chest{CustomName:\"Confirm BIN Auction\",Items:[{Slot:11b,id:\"minecraft:green_terracotta\",count:1}]}");
		ctx.waitTicks(10);
		openChest(10, -60, 2);
		check("auction input: Enter confirms", ctx.computeOnClient(mc -> mc.screen instanceof AbstractContainerScreen<?> s
				&& com.taz.general.AuctionInput.onKey(s, GLFW.GLFW_KEY_ENTER)));
		ctx.runOnClient(mc -> {
			mc.player.closeContainer();
			Taz.config().inventory.auctionInput.enabled = false;
		});
	}

	private void runExtras() {
		tell("[MVP+] Friend opened a WITHER door!");
		check("run extras: a wither door counted", ctx.computeOnClient(mc -> com.taz.dungeons.run.RunExtras.witherDoors()) == 1);
	}

	private static void paint(byte[] map, int x1, int z1, int x2, int z2, int color) {
		for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++) map[z * 128 + x] = (byte) color;
	}

	private void dungeonMap() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeonMap.map.enabled = true;
			DungeonLocation.setTestFloor("F7");
		});
		ctx.waitTicks(25);
		byte[] map = new byte[128 * 128];
		paint(map, 5, 5, 20, 20, 30);
		paint(map, 25, 5, 40, 20, 63);
		paint(map, 31, 11, 34, 14, 34);
		paint(map, 21, 11, 24, 15, 63);
		paint(map, 45, 5, 60, 20, 66);
		paint(map, 51, 11, 54, 14, 18);
		paint(map, 41, 11, 44, 15, 119);
		paint(map, 5, 25, 20, 40, 85);
		check("dungeon map: found the entrance (16-block rooms)", ctx.computeOnClient(mc -> com.taz.dungeons.map.DungeonMap.testRead(map))
				&& ctx.computeOnClient(mc -> com.taz.dungeons.map.DungeonMap.roomSize()) == 16);
		check("dungeon map: the rooms and doors (" + ctx.computeOnClient(mc -> com.taz.dungeons.map.DungeonMap.describe(2, 0) + ", "
				+ com.taz.dungeons.map.DungeonMap.describe(3, 0)) + ")", ctx.computeOnClient(mc ->
				com.taz.dungeons.map.DungeonMap.describe(0, 0).equals("ROOM ENTRANCE DISCOVERED")
						&& com.taz.dungeons.map.DungeonMap.describe(2, 0).equals("ROOM NORMAL CLEARED")
						&& com.taz.dungeons.map.DungeonMap.describe(1, 0).startsWith("DOOR")
						&& com.taz.dungeons.map.DungeonMap.describe(4, 0).equals("ROOM PUZZLE FAILED")
						&& com.taz.dungeons.map.DungeonMap.describe(3, 0).startsWith("DOOR")
						&& com.taz.dungeons.map.DungeonMap.describe(0, 2).equals("ROOM NORMAL UNOPENED")));
		ctx.takeScreenshot("qol-dungeon-map");
		ctx.runOnClient(mc -> {
			Taz.config().dungeonMap.map.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	private void mapInfo() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeonMap.mapInfo.enabled = true;
			com.taz.dungeons.map.MapInfo.testScore(302);
			com.taz.dungeons.map.MapInfo.onActionBar("§c1200/1200❤     §a2/5 Secrets     §b500/500✎ Mana");
		});
		check("map info: score and room secrets (" + hudLines(com.taz.dungeons.map.MapInfo.Score.class) + " "
				+ hudLines(com.taz.dungeons.map.MapInfo.RoomSecrets.class) + ")",
				hudLines(com.taz.dungeons.map.MapInfo.Score.class).equals(List.of("Score 302"))
						&& hudLines(com.taz.dungeons.map.MapInfo.RoomSecrets.class).equals(List.of("2/5")));
		ctx.runOnClient(mc -> Taz.config().dungeonMap.mapInfo.enabled = false);
	}

	private void darkMode() {
		ctx.runOnClient(mc -> Taz.config().visuals.darkMode.enabled = true);
		ctx.waitTicks(3);
		ctx.takeScreenshot("qol-dark-mode");
		ctx.runOnClient(mc -> Taz.config().visuals.darkMode.enabled = false);
	}

	private void storage() {
		ctx.runOnClient(mc -> {
			Taz.config().inventory.storageOverlay.enabled = true;
			com.taz.general.StorageOverlay.pages().clear();
		});
		cmd("tp @p 14 -60 0");
		cmd("setblock 14 -60 2 chest{CustomName:\"Ender Chest (1/9)\",Items:[{Slot:9b,id:\"minecraft:diamond\",count:5},"
				+ "{Slot:10b,id:\"minecraft:golden_apple\",count:1}]}");
		cmd("setblock 16 -60 2 chest{CustomName:\"Storage\",Items:[{Slot:9b,id:\"minecraft:ender_chest\",count:1},"
				+ "{Slot:10b,id:\"minecraft:ender_chest\",count:1},{Slot:27b,id:\"minecraft:red_stained_glass_pane\",count:1}]}");
		ctx.waitTicks(10);
		openChest(14, -60, 2);
		ctx.waitTicks(15);
		check("storage: ender chest page 1 remembered", ctx.computeOnClient(mc -> {
			var page = com.taz.general.StorageOverlay.pages().get(0);
			return page != null && page.get(0).is(Items.DIAMOND) && page.get(0).getCount() == 5 && page.get(1).is(Items.GOLDEN_APPLE);
		}));
		ctx.runOnClient(mc -> mc.player.closeContainer());
		cmd("tp @p 16 -60 0");
		ctx.waitTicks(5);
		openChest(16, -60, 2);
		ctx.waitTicks(15);
		check("storage: the overlay on the Storage menu, page 2 known but not seen yet", ctx.computeOnClient(mc -> mc.screen instanceof AbstractContainerScreen<?> sc
				&& com.taz.general.StorageOverlay.active(sc) && com.taz.general.StorageOverlay.pages().containsKey(1)
				&& com.taz.general.StorageOverlay.pages().get(1) == null && !com.taz.general.StorageOverlay.pages().containsKey(9)));
		ctx.takeScreenshot("qol-storage-overlay");
		ctx.runOnClient(mc -> {
			mc.player.closeContainer();
			Taz.config().inventory.storageOverlay.enabled = false;
		});
	}

	private void predictor() {
		check("predictor: a run whose tick clock started on another server uses its real times", ctx.computeOnClient(mc -> {
			com.taz.dungeons.run.RunRecord bad = new com.taz.dungeons.run.RunRecord();
			bad.floor = "M7";
			bad.totalMs = 374_000;
			bad.totalGameMs = 450_892;
			bad.splits.put("BOSS_ENTER", 127_748L);
			bad.gameSplits.put("BOSS_ENTER", 211_874L);
			com.taz.dungeons.run.RunRecord good = new com.taz.dungeons.run.RunRecord();
			good.floor = "M7";
			good.totalMs = 240_000;
			good.totalGameMs = 235_891;
			good.splits.put("BOSS_ENTER", 82_051L);
			good.gameSplits.put("BOSS_ENTER", 79_778L);
			return bad.gameSplit(com.taz.dungeons.dungeon.Milestone.BOSS_ENTER) == 127_748L && bad.gameTotal() == 374_000L
					&& good.gameSplit(com.taz.dungeons.dungeon.Milestone.BOSS_ENTER) == 79_778L && good.gameTotal() == 235_891L;
		}));
	}

	private List<String> hudLines(Class<?> type) {
		return ctx.computeOnClient(mc -> {
			for (com.taz.hud.MovableHud h : com.taz.hud.TazHud.all()) {
				if (type.isInstance(h)) return ((com.taz.hud.TextHud) h).shownLines(false).stream().map(net.minecraft.ChatFormatting::stripFormatting).toList();
			}
			throw new AssertionError(type.getSimpleName() + " not registered");
		});
	}

	// ---- helpers

	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("qol: " + what);
		System.out.println("[QolGameTest] ok: " + what);
	}
}
