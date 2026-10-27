package com.taz.gametest;

import com.google.gson.JsonPrimitive;
import com.taz.Taz;
import com.taz.dungeons.f7.TickTimers;
import com.taz.dungeons.info.Blessings;
import com.taz.dungeons.info.MaskTimers;
import com.taz.dungeons.info.ScoreAlerts;
import com.taz.dungeons.info.TerracottaTimer;
import com.taz.dungeons.util.DungeonLocation;
import com.taz.general.KickTimer;
import com.taz.general.SpringBoots;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.stream.Stream;

/** Features ported from Odin and NoammAddons. */
public class PortedGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"performance", "blessings", "masks", "ragnarock", "ticktimers", "terracotta", "scorealerts",
			"springboots", "pets", "kicktimer", "starredmobs", "doorskeys", "secretclicked", "etherwarp", "gyro", "magebeam", "bloodcamp", "terminals",
			"doorfix", "termsolver", "simonsays", "arrowalign", "dragons"};
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;

	private static boolean runs(String part) {
		return TestFilter.runs(part, "ported");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Stream.of(PARTS).noneMatch(PortedGameTest::runs)) {
			System.out.println("[TestFilter] skipped ported");
			return;
		}
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("gamemode creative @p");
			ctx.waitTicks(25);
			if (runs("performance")) performance();
			if (runs("blessings")) blessings();
			if (runs("masks")) masks();
			if (runs("ragnarock")) ragnarock();
			if (runs("ticktimers")) tickTimers();
			if (runs("terracotta")) terracotta();
			if (runs("scorealerts")) scoreAlerts();
			if (runs("springboots")) springBoots();
			if (runs("pets")) pets();
			if (runs("kicktimer")) kickTimer();
			if (runs("starredmobs")) starredMobs();
			if (runs("doorskeys")) doorsAndKeys();
			if (runs("secretclicked")) secretClicked();
			if (runs("etherwarp")) etherwarp();
			if (runs("gyro")) gyro();
			if (runs("magebeam")) mageBeam();
			if (runs("bloodcamp")) bloodCamp();
			if (runs("terminals")) terminals();
			if (runs("doorfix")) doorFix();
			if (runs("termsolver")) terminalSolver();
			if (runs("simonsays")) simonSays();
			if (runs("arrowalign")) arrowAlign();
			if (runs("dragons")) dragons();
			ctx.takeScreenshot("ported-huds");
		}
		System.out.println("[PortedGameTest] PASSED");
	}

	private void performance() {
		ctx.runOnClient(mc -> Taz.config().visuals.performance.enabled = true);
		ctx.waitTicks(45);
		List<String> lines = hud(com.taz.general.PerformanceHud.class);
		check("performance: TPS, FPS and ping (" + lines + ")", lines.size() == 1 && lines.get(0).startsWith("TPS ") && lines.get(0).contains("FPS ")
				&& lines.get(0).contains("Ping ") && lines.get(0).matches(".*Ping \\d+ms.*"));
	}

	private void blessings() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.blessings.enabled = true;
			Taz.onTabFooter("Active Effects\nDungeon Buffs\nBlessing of Power XIX\nBlessing of Time V\nBlessing of Life III");
		});
		check("blessings: read from the tab list", ctx.computeOnClient(mc -> Blessings.level(Blessings.Blessing.POWER)) == 19
				&& ctx.computeOnClient(mc -> Blessings.level(Blessings.Blessing.TIME)) == 5 && ctx.computeOnClient(mc -> Blessings.level(Blessings.Blessing.LIFE)) == 3);
		List<String> lines = hud(Blessings.class);
		check("blessings: power and time shown, life is off (" + lines + ")", lines.equals(List.of("Power 19", "Time 5")));
	}

	private void masks() {
		ctx.runOnClient(mc -> Taz.config().dungeons.masks.enabled = true);
		int waiting = ctx.computeOnClient(mc -> TazChat.waiting());
		tell("Your ⚚ Bonzo's Mask saved your life!");
		check("masks: Bonzo timed (180s), alert and party chat", ctx.computeOnClient(mc -> MaskTimers.cooldown(MaskTimers.Mask.BONZO)) == 3600
				&& "§cBonzo".equals(Alert.lastTitle) && ctx.computeOnClient(mc -> TazChat.waiting()) == waiting + 1);
		serverTicks(40);
		check("masks: two seconds of server ticks later", ctx.computeOnClient(mc -> MaskTimers.cooldown(MaskTimers.Mask.BONZO)) == 3560);
		tell("Second Wind Activated! Your Spirit Mask saved your life!");
		List<String> lines = hud(MaskTimers.class);
		check("masks: the HUD (" + lines + ")", lines.contains("Spirit 3.0s") && lines.contains("Bonzo 1.0s") && lines.contains("Phoenix ✔"));
	}

	private void ragnarock() {
		ctx.runOnClient(mc -> Taz.config().general.ragnarock.enabled = true);
		tell("Ragnarock was cancelled due to taking damage!");
		check("ragnarock: cancelled", "§cRagnarock Cancelled!".equals(Alert.lastTitle));
		sp.getServer().runOnServer(server -> {
			ItemStack axe = new ItemStack(Items.IRON_AXE);
			CompoundTag tag = new CompoundTag();
			tag.putString("id", "RAGNAROCK_AXE");
			axe.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			axe.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Damage: +280"), Component.literal("Strength: +100"))));
			server.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.MAINHAND, axe);
		});
		ctx.waitTicks(5);
		cmd("execute as @p at @s run playsound minecraft:entity.wolf.death master @s ~ ~ ~ 1 1.4920635");
		ctx.waitTicks(3);
		check("ragnarock: the cast's howl", "§aCasted Rag".equals(Alert.lastTitle));
	}

	private void tickTimers() {
		ctx.runOnClient(mc -> Taz.config().floor7.tickTimers.enabled = true);
		tell("[BOSS] Goldor: Who dares trespass into my domain?");
		check("tick timers: Goldor (" + shown() + ")", shown().equals("Goldor 3.00s"));
		serverTicks(20);
		check("tick timers: a second later (" + shown() + ")", shown().equals("Goldor 2.00s"));
		serverTicks(40);
		check("tick timers: it loops every 3 seconds (" + shown() + ")", shown().equals("Goldor 3.00s"));
		tell("The Core entrance is opening!");
		check("tick timers: gone when the core opens", shown().isEmpty());
		tell("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!");
		check("tick timers: Maxor's start (" + shown() + ")", shown().equals("Start 4.15s"));
		serverTicks(90);
		tell("[BOSS] Storm: Pathetic Maxor, just like expected.");
		serverTicks(5);
		check("tick timers: Storm's pads (" + shown() + ")", shown().equals("Pad 0.75s"));
		tell("[BOSS] Storm: I should have known that I stood no chance.");
		check("tick timers: none after Storm", shown().isEmpty());
	}

	private void terracotta() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.terracotta.enabled = true;
			DungeonLocation.setTestFloor("F6");
		});
		cmd("tp @p 0 80 40");
		ctx.waitTicks(30);
		check("terracotta: in the Floor 6 boss room", ctx.computeOnClient(mc -> DungeonLocation.floorNumber() == 6 && DungeonLocation.inBoss()));
		BlockPos pot = new BlockPos(2, 80, 40);
		cmd("setblock 2 80 40 flower_pot");
		ctx.waitTicks(3);
		check("terracotta: 15 seconds from its pot", ctx.computeOnClient(mc -> TerracottaTimer.left(pot)) == 300);
		serverTicks(100);
		check("terracotta: counting down", ctx.computeOnClient(mc -> TerracottaTimer.left(pot)) == 200);
		ctx.runOnClient(mc -> DungeonLocation.setTestFloor(null));
	}

	private void scoreAlerts() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.scoreAlerts.enabled = true;
			ScoreAlerts.testScore(250);
			ScoreAlerts.testScore(301);
		});
		check("score alerts: 300 (" + ctx.computeOnClient(mc -> ScoreAlerts.lastMessage()) + ")", "§c300 Score!".equals(Alert.lastTitle)
				&& ctx.computeOnClient(mc -> ScoreAlerts.lastMessage()).startsWith("300 score reached in 5m 12s"));
		ctx.runOnClient(mc -> DungeonLocation.setTestFloor("F7"));
		cmd("tp @p -60 80 -60");
		ctx.waitTicks(30);
		cmd("summon zombie -58 80 -60 {IsBaby:1b,NoAI:1b}");
		ctx.waitTicks(5);
		cmd("kill @e[type=zombie]");
		ctx.waitTicks(10);
		check("score alerts: the Mimic (a baby zombie) killed", ctx.computeOnClient(mc -> ScoreAlerts.mimicKilled()));
		int waiting = ctx.computeOnClient(mc -> TazChat.waiting());
		tell("A Prince falls. +1 Bonus Score");
		check("score alerts: the Prince in party chat", ctx.computeOnClient(mc -> TazChat.waiting()) == waiting + 1);
		ctx.runOnClient(mc -> DungeonLocation.setTestFloor(null));
	}

	private void springBoots() {
		ctx.runOnClient(mc -> Taz.config().general.springBoots.enabled = true);
		sp.getServer().runOnServer(server -> {
			ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
			CompoundTag tag = new CompoundTag();
			tag.putString("id", "SPRING_BOOTS");
			boots.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			server.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.FEET, boots);
		});
		cmd("tp @p 0 -60 0");
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(true));
		ctx.waitTicks(5);
		for (float pitch : new float[]{0.6984127f, 0.6984127f, 0.82539684f, 0.8888889f}) {
			cmd("execute as @p at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 " + pitch);
			ctx.waitTicks(2);
		}
		check("spring boots: 4 notes charged it to 11.5 blocks (" + ctx.computeOnClient(mc -> SpringBoots.height()) + ")",
				ctx.computeOnClient(mc -> SpringBoots.height()) == 11.5f && hud(SpringBoots.class).contains("Spring Boots 11.5 blocks"));
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(false));
		ctx.waitTicks(5);
		check("spring boots: standing up ends it", ctx.computeOnClient(mc -> SpringBoots.height()) == 0);
	}

	private void pets() {
		ctx.runOnClient(mc -> Taz.config().general.pets.enabled = true);
		tell("§aYou summoned your §6Golden Dragon§a!");
		check("pets: summoned (" + hud(com.taz.general.PetDisplay.class) + ")", hud(com.taz.general.PetDisplay.class).contains("Golden Dragon"));
		tell("§cAutopet §eequipped your §7[Lvl 100] §5Ender Dragon§e! §a§lVIEW RULE");
		check("pets: Autopet with its level, and a title", hud(com.taz.general.PetDisplay.class).contains("[Lvl 100] Ender Dragon")
				&& "§5Ender Dragon".equals(Alert.lastTitle));
		tell("Your Ender Dragon leveled up to level 101!");
		check("pets: level up", hud(com.taz.general.PetDisplay.class).contains("[Lvl 101] Ender Dragon"));
	}

	private void kickTimer() {
		ctx.runOnClient(mc -> Taz.config().general.kickTimer.enabled = true);
		tell("You were kicked while joining that server!");
		check("kick timer: counting (" + hud(KickTimer.class) + ")", ctx.computeOnClient(mc -> KickTimer.showing())
				&& hud(KickTimer.class).get(0).startsWith("Kicked from SkyBlock "));
	}

	private void starredMobs() {
		ctx.runOnClient(mc -> Taz.config().dungeons.starredMobs.enabled = true);
		platform(300, 100, 300);
		cmd("summon zombie 303 100 300 {NoAI:1b}");
		cmd("summon armor_stand 303 102 300 {CustomName:\"✯ Zombie 1.2M❤\",Invisible:1b,NoGravity:1b}");
		cmd("summon armor_stand 300 102 304 {CustomName:\"Lurker 50❤\",Invisible:1b,CustomNameVisible:1b,NoGravity:1b}");
		cmd("summon bat 297 101 300 {NoAI:1b,attributes:[{id:\"minecraft:max_health\",base:100}]}");
		cmd("tp @p 299 101 300 -90 15");
		ctx.waitTicks(20);
		check("starred mobs: the zombie under the starred name glows red", ctx.computeOnClient(mc -> {
			net.minecraft.world.entity.Entity z = nearest(mc, net.minecraft.world.entity.monster.zombie.Zombie.class);
			return z != null && com.taz.dungeons.highlight.StarredMobs.glowing(z) && com.taz.render.Glow.color(z) == 0xFF3030;
		}));
		check("starred mobs: the bonus bat glows white", ctx.computeOnClient(mc -> {
			net.minecraft.world.entity.Entity b = nearest(mc, net.minecraft.world.entity.ambient.Bat.class);
			return b != null && com.taz.render.Glow.color(b) == 0xFFFFFF;
		}));
		check("starred mobs: the plain mob's name is hidden", ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(
				net.minecraft.world.entity.decoration.ArmorStand.class, mc.player.getBoundingBox().inflate(10),
				a -> a.hasCustomName() && a.getCustomName().getString().startsWith("Lurker")).stream().noneMatch(a -> a.isCustomNameVisible())));
		ctx.takeScreenshot("ported-starred-mobs");
		cmd("kill @e[type=!player]");
		ctx.runOnClient(mc -> Taz.config().dungeons.starredMobs.enabled = false);
	}

	private void doorsAndKeys() {
		ctx.runOnClient(mc -> Taz.config().dungeons.doorsAndKeys.enabled = true);
		platform(-185, 71, -185);
		cmd("fill -170 69 -186 -168 72 -184 coal_block");
		cmd("fill -186 69 -170 -184 72 -168 red_terracotta");
		ctx.waitTicks(25);
		check("doors: a wither and a blood door next to your room (" + ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors()) + ")",
				ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors().size()) == 2);
		check("doors: locked without a key", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors().stream()
				.noneMatch(com.taz.dungeons.highlight.DoorsAndKeys::openable)));
		tell("[MVP+] Player has obtained Wither Key!");
		check("doors: the wither door opens with the key", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors().stream()
				.anyMatch(d -> !d.blood() && com.taz.dungeons.highlight.DoorsAndKeys.openable(d))));
		tell("Player opened a WITHER door!");
		check("doors: key used", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors().stream()
				.noneMatch(com.taz.dungeons.highlight.DoorsAndKeys::openable)));
		cmd("summon armor_stand -182 72 -182 {CustomName:\"Wither Key\",Invisible:1b,NoGravity:1b}");
		ctx.waitTicks(15);
		check("keys: the Wither Key found and announced (" + Alert.lastTitle + ")",
				ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.key() != null) && Alert.lastTitle.contains("Wither Key"));
		ctx.takeScreenshot("ported-doors");
		tell("The BLOOD DOOR has been opened!");
		ctx.waitTicks(12);
		check("doors: the blood door is gone once opened", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.DoorsAndKeys.doors().stream()
				.noneMatch(com.taz.dungeons.highlight.DoorsAndKeys.Door::blood)));
		cmd("kill @e[type=armor_stand]");
		ctx.runOnClient(mc -> Taz.config().dungeons.doorsAndKeys.enabled = false);
	}

	private void secretClicked() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.secretClicked.enabled = true;
			com.taz.dungeons.highlight.SecretClicked.reset();
		});
		platform(400, 100, 400);
		cmd("setblock 402 101 400 chest");
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			BlockPos chest = new BlockPos(402, 101, 400);
			com.taz.dungeons.highlight.SecretClicked.onInteract(chest, mc.level.getBlockState(chest));
		});
		check("secret clicked: a box on the chest", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.SecretClicked.boxes()) == 1);
		tell("That chest is locked!");
		check("secret clicked: locked", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.SecretClicked.locked()));
		cmd("summon bat 400 103 404 {NoAI:1b}");
		ctx.waitTicks(5);
		cmd("kill @e[type=bat]");
		ctx.waitTicks(5);
		check("secret clicked: a box where the bat died", ctx.computeOnClient(mc -> com.taz.dungeons.highlight.SecretClicked.boxes()) == 2);
		cmd("summon item 400 101 398 {Item:{id:\"minecraft:paper\",count:1},PickupDelay:0}");
		cmd("tp @p 400 101 398");
		ctx.waitTicks(15);
		check("secret clicked: a box where you picked up the item (" + ctx.computeOnClient(mc -> com.taz.dungeons.highlight.SecretClicked.boxes()) + ")",
				ctx.computeOnClient(mc -> com.taz.dungeons.highlight.SecretClicked.boxes()) == 3);
		ctx.runOnClient(mc -> Taz.config().dungeons.secretClicked.enabled = false);
	}

	private void etherwarp() {
		ctx.runOnClient(mc -> Taz.config().visuals.etherwarp.enabled = true);
		platform(500, 100, 500);
		cmd("tp @p 500 101 500 0 90");
		cmd("item replace entity @p weapon.mainhand with diamond_shovel[custom_data={id:\"ASPECT_OF_THE_VOID\",ethermerge:1,tuned_transmission:3}]");
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(true));
		ctx.waitTicks(10);
		check("etherwarp: range 57 + 3", ctx.computeOnClient(mc -> com.taz.general.EtherwarpGuess.range(mc.player.getMainHandItem())) == 60);
		check("etherwarp: lands on the block under you", ctx.computeOnClient(mc -> {
			com.taz.util.EtherRaycast.Hit hit = com.taz.general.EtherwarpGuess.guess();
			return hit != null && hit.ok() && hit.pos().equals(new BlockPos(500, 100, 500));
		}));
		cmd("setblock 500 101 500 skeleton_skull");
		cmd("tp @p 500 102 500 0 90");
		cmd("setblock 500 101 501 stone");
		ctx.waitTicks(5);
		check("etherwarp: fails with a skull where your feet go", ctx.computeOnClient(mc -> {
			com.taz.util.EtherRaycast.Hit hit = com.taz.general.EtherwarpGuess.guess();
			return hit != null && !hit.ok();
		}));
		ctx.runOnClient(mc -> mc.options.keyShift.setDown(false));
		ctx.waitTicks(3);
		check("etherwarp: nothing when not sneaking", ctx.computeOnClient(mc -> com.taz.general.EtherwarpGuess.guess() == null));
		cmd("item replace entity @p weapon.mainhand with air");
		ctx.runOnClient(mc -> Taz.config().visuals.etherwarp.enabled = false);
	}

	private void gyro() {
		ctx.runOnClient(mc -> {
			Taz.config().visuals.gyroWand.enabled = true;
			com.taz.general.GyroWand.reset();
		});
		platform(600, 100, 600);
		cmd("tp @p 600 101 600 0 90");
		cmd("item replace entity @p weapon.mainhand with blaze_rod[custom_data={id:\"GYROKINETIC_WAND\"}]");
		ctx.waitTicks(5);
		check("gyro: aimed at the block under you", ctx.computeOnClient(mc -> new net.minecraft.world.phys.Vec3(600.5, 101, 600.5).equals(com.taz.general.GyroWand.target())));
		ctx.takeScreenshot("ported-gyro");
		sp.getServer().runOnServer(server -> {
			for (int i = 0; i < 5; i++) server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "particle minecraft:witch 604 102.2 600 0 0 0 0 1 force");
		});
		ctx.waitTicks(5);
		check("gyro: the burst of witch particles starts the cooldown", ctx.computeOnClient(mc -> com.taz.general.GyroWand.onCooldown()));
		cmd("item replace entity @p weapon.mainhand with air");
		ctx.runOnClient(mc -> Taz.config().visuals.gyroWand.enabled = false);
	}

	private void mageBeam() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.mageBeam.enabled = true;
			com.taz.dungeons.info.MageBeam.reset();
		});
		platform(700, 100, 700);
		cmd("tp @p 700 101 701 -60 10");
		sp.getServer().runOnServer(server -> {
			for (int i = 0; i < 6; i++) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "particle minecraft:firework " + (702 + i) + " 102.5 700 0 0 0 0 1 force");
			}
		});
		ctx.waitTicks(3);
		check("mage beam: six sparks in a line make one beam", ctx.computeOnClient(mc -> com.taz.dungeons.info.MageBeam.beams()) == 1);
		ctx.takeScreenshot("ported-mage-beam");
		ctx.runOnClient(mc -> Taz.config().dungeons.mageBeam.enabled = false);
	}

	private void bloodCamp() {
		ctx.runOnClient(mc -> {
			Taz.config().dungeons.bloodCamp.enabled = true;
			com.taz.dungeons.info.BloodCamp.clear();
		});
		tell("[BOSS] The Watcher: Ah, you've finally arrived.");
		check("blood camp: he moves in 20.5s (" + hud(com.taz.dungeons.info.BloodCamp.class) + ")",
				hud(com.taz.dungeons.info.BloodCamp.class).equals(List.of("Watcher moves in 20.50s")));
		serverTicks(410);
		check("blood camp: kill mobs", "§c§lKill Mobs".equals(Alert.lastTitle) && hud(com.taz.dungeons.info.BloodCamp.class).isEmpty());
		platform(800, 100, 800);
		cmd("summon zombie 805 101 800 {NoAI:1b,equipment:{head:{id:\"minecraft:player_head\",count:1,components:{\"minecraft:profile\":{properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6IHsiU0tJTiI6IHsidXJsIjogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY2MmI2ZmI0YjhiNTg2ZGM0Y2RmODAzYjA0NDRkOWI0MWQyNDVjZGY2NjhkYWIzOGZhNmMwNjRhZmU4ZTQ2MSJ9fX0=\"}]}}}}}");
		ctx.waitTicks(15);
		check("blood camp: found the Watcher by his head", ctx.computeOnClient(mc -> com.taz.dungeons.info.BloodCamp.watcher() != null));
		cmd("summon armor_stand 805 101 803 {NoGravity:1b,Tags:[\"bloodmob\"],equipment:{head:{id:\"minecraft:player_head\",count:1,components:{\"minecraft:profile\":{properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6IHsiU0tJTiI6IHsidXJsIjogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE3OTg2MGFjYTc5OTQwN2MwZmFhMTBiMWJiY2Y0Mjk5OGZhZDRlYmNmMzFkN2EyMTQxODA4MjZiNGFjOTRlMSJ9fX0=\"}]}}}}}");
		ctx.waitTicks(3);
		check("blood camp: a mob flying out", ctx.computeOnClient(mc -> com.taz.dungeons.info.BloodCamp.flying()) == 1);
		cmd("tp @e[tag=bloodmob] 808 101 803");
		cmd("tp @p 798 101 800 -80 10");
		ctx.waitTicks(10);
		serverTicks(20);
		check("blood camp: it lands 16.1 blocks out, in 2.9s", ctx.computeOnClient(mc -> {
			net.minecraft.world.entity.Entity stand = nearest(mc, net.minecraft.world.entity.decoration.ArmorStand.class);
			net.minecraft.world.phys.Vec3 end = com.taz.dungeons.info.BloodCamp.landing(stand);
			return end != null && Math.abs(end.x - (805.5 + 16.1)) < 0.01 && Math.abs(end.z - 803.5) < 0.01
					&& Math.abs(com.taz.dungeons.info.BloodCamp.secondsLeft(stand) - 2.9) < 0.01;
		}));
		ctx.takeScreenshot("ported-blood-camp");
		cmd("kill @e[type=!player]");
		ctx.runOnClient(mc -> Taz.config().dungeons.bloodCamp.enabled = false);
	}

	private void terminals() {
		ctx.runOnClient(mc -> {
			Taz.config().floor7.terminals.enabled = true;
			DungeonLocation.setTestFloor("F7");
		});
		platform(105, 112, 73);
		cmd("summon armor_stand 110 113 73 {CustomName:\"Inactive Terminal\",NoGravity:1b}");
		cmd("tp @p 105 113 73 -90 10");
		ctx.waitTicks(25);
		check("terminals: in Phase 3, section 1", ctx.computeOnClient(mc -> DungeonLocation.f7Phase() == 3 && DungeonLocation.p3Section() == 1));
		check("terminals: the inactive terminal found", ctx.computeOnClient(mc -> com.taz.dungeons.f7.TerminalHighlight.inactive()) == 1);
		ctx.takeScreenshot("ported-terminals");
		cmd("data merge entity @e[type=armor_stand,limit=1,sort=nearest] {CustomName:\"Terminal Active\"}");
		ctx.waitTicks(10);
		check("terminals: gone once done", ctx.computeOnClient(mc -> com.taz.dungeons.f7.TerminalHighlight.inactive()) == 0);
		cmd("kill @e[type=armor_stand]");
		ctx.runOnClient(mc -> {
			Taz.config().floor7.terminals.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	private void doorFix() {
		ctx.runOnClient(mc -> {
			Taz.config().floor7.doorFix.enabled = true;
			DungeonLocation.setTestFloor("F7");
		});
		cmd("setblock 1 111 104 stone");
		cmd("setblock 1 112 104 iron_door[facing=north,half=lower]");
		cmd("setblock 1 113 104 iron_door[facing=north,half=upper]");
		platform(8, 112, 100);
		ctx.waitTicks(25);
		check("door fix: in section 3", ctx.computeOnClient(mc -> DungeonLocation.p3Section() == 3));
		check("door fix: the door turned east", ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(1, 112, 104)) == com.taz.dungeons.f7.DoorFix.fixed()));
		ctx.runOnClient(mc -> {
			Taz.config().floor7.doorFix.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	private void terminalSolver() {
		ctx.runOnClient(mc -> Taz.config().floor7.terminalSolver.enabled = true);
		check("terminal solver: rubix, one right click to make them all orange", ctx.computeOnClient(mc -> {
			com.taz.dungeons.f7.TerminalSolver.testOpen(com.taz.dungeons.f7.TerminalSolver.Type.RUBIX, null);
			List<ItemStack> items = new java.util.ArrayList<>();
			for (int i = 0; i < 45; i++) items.add(new ItemStack(Items.BLACK_STAINED_GLASS_PANE));
			for (int i : new int[] {12, 13, 14, 21, 22, 23, 30, 31, 32}) items.set(i, new ItemStack(Items.ORANGE_STAINED_GLASS_PANE));
			items.set(12, new ItemStack(Items.YELLOW_STAINED_GLASS_PANE));
			com.taz.dungeons.f7.TerminalSolver.solve(items);
			return com.taz.dungeons.f7.TerminalSolver.labels().equals(java.util.Map.of(12, "-1"));
		}));
		check("terminal solver: starts with 'B', not the shiny one", ctx.computeOnClient(mc -> {
			com.taz.dungeons.f7.TerminalSolver.testOpen(com.taz.dungeons.f7.TerminalSolver.Type.STARTS_WITH, "B");
			ItemStack shiny = new ItemStack(Items.BONE);
			shiny.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			com.taz.dungeons.f7.TerminalSolver.solve(List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE), shiny, new ItemStack(Items.BOOK)));
			return com.taz.dungeons.f7.TerminalSolver.colors().keySet().equals(java.util.Set.of(0, 3));
		}));
		check("terminal solver: select all the white items", ctx.computeOnClient(mc -> {
			com.taz.dungeons.f7.TerminalSolver.testOpen(com.taz.dungeons.f7.TerminalSolver.Type.COLORS, "WHITE");
			com.taz.dungeons.f7.TerminalSolver.solve(List.of(new ItemStack(Items.WHITE_WOOL), new ItemStack(Items.BONE_MEAL), new ItemStack(Items.RED_WOOL)));
			return com.taz.dungeons.f7.TerminalSolver.colors().keySet().equals(java.util.Set.of(0, 1));
		}));
		platform(900, 100, 900);
		cmd("setblock 902 101 900 chest{CustomName:\"Click in order!\",Items:[{Slot:10b,id:\"minecraft:red_stained_glass_pane\",count:3},"
				+ "{Slot:11b,id:\"minecraft:red_stained_glass_pane\",count:1},{Slot:12b,id:\"minecraft:red_stained_glass_pane\",count:2},"
				+ "{Slot:13b,id:\"minecraft:lime_stained_glass_pane\",count:4}]}");
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(new BlockPos(902, 101, 900)), net.minecraft.core.Direction.UP,
						new BlockPos(902, 101, 900), false)));
		ctx.waitTicks(10);
		check("terminal solver: numbers in order (" + ctx.computeOnClient(mc -> com.taz.dungeons.f7.TerminalSolver.labels()) + ")", ctx.computeOnClient(mc ->
				com.taz.dungeons.f7.TerminalSolver.type() == com.taz.dungeons.f7.TerminalSolver.Type.NUMBERS
						&& com.taz.dungeons.f7.TerminalSolver.colors().get(11) == 0xFFFF2E2E && com.taz.dungeons.f7.TerminalSolver.colors().get(12) == 0xFFFFB0B0
						&& com.taz.dungeons.f7.TerminalSolver.colors().get(10) == 0xFFB3121B && !com.taz.dungeons.f7.TerminalSolver.colors().containsKey(13)
						&& "1".equals(com.taz.dungeons.f7.TerminalSolver.labels().get(11))));
		ctx.takeScreenshot("ported-terminal-solver");
		ctx.runOnClient(mc -> mc.player.closeContainer());
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> Taz.config().floor7.terminalSolver.enabled = false);
	}

	private void simonSays() {
		ctx.runOnClient(mc -> {
			Taz.config().floor7.simonSays.enabled = true;
			DungeonLocation.setTestFloor("F7");
			com.taz.dungeons.f7.SimonSays.reset();
		});
		platform(105, 119, 93);
		cmd("fill 111 120 92 111 123 95 sea_lantern");
		cmd("fill 110 120 92 110 123 95 stone_button[facing=west]");
		ctx.waitTicks(25);
		check("simon says: in Phase 3", ctx.computeOnClient(mc -> DungeonLocation.f7Phase() == 3));
		cmd("setblock 111 121 93 obsidian");
		ctx.waitTicks(3);
		check("simon says: one lantern lit", ctx.computeOnClient(mc -> com.taz.dungeons.f7.SimonSays.order().equals(List.of(new BlockPos(111, 121, 93)))));
		cmd("setblock 110 121 93 stone_button[facing=west,powered=true]");
		ctx.waitTicks(3);
		cmd("setblock 111 123 95 obsidian");
		ctx.waitTicks(3);
		cmd("setblock 111 120 92 obsidian");
		ctx.waitTicks(3);
		check("simon says: the next round in order (" + ctx.computeOnClient(mc -> com.taz.dungeons.f7.SimonSays.order()) + ")", ctx.computeOnClient(mc ->
				com.taz.dungeons.f7.SimonSays.order().equals(List.of(new BlockPos(111, 123, 95), new BlockPos(111, 120, 92)))
						&& com.taz.dungeons.f7.SimonSays.next() == 0));
		ctx.runOnClient(mc -> {
			Taz.config().floor7.simonSays.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	private void arrowAlign() {
		ctx.runOnClient(mc -> {
			Taz.config().floor7.arrowAlign.enabled = true;
			DungeonLocation.setTestFloor("F7");
		});
		platform(3, 119, 77);
		cmd("fill -3 120 75 -3 124 79 stone");
		for (int i : new int[] {5, 7, 9, 10, 12, 14, 15, 17, 19}) {
			cmd("summon item_frame -2 " + (120 + i % 5) + " " + (75 + i / 5) + " {Facing:5b,Fixed:1b,Item:{id:\"minecraft:arrow\",count:1},ItemRotation:0b}");
		}
		ctx.waitTicks(25);
		check("arrow align: one click on each of the 9 frames (" + ctx.computeOnClient(mc -> com.taz.dungeons.f7.ArrowAlign.left()) + ")",
				ctx.computeOnClient(mc -> com.taz.dungeons.f7.ArrowAlign.left().size() == 9
						&& com.taz.dungeons.f7.ArrowAlign.left().values().stream().allMatch(n -> n == 1)));
		ctx.takeScreenshot("ported-arrow-align");
		ctx.runOnClient(mc -> com.taz.dungeons.f7.ArrowAlign.onClick(mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
				mc.player.getBoundingBox().inflate(10)).stream().filter(f -> f.blockPosition().equals(new BlockPos(-2, 120, 76))).findFirst().orElseThrow()));
		check("arrow align: done once clicked", ctx.computeOnClient(mc -> com.taz.dungeons.f7.ArrowAlign.left().size()) == 8);
		cmd("kill @e[type=item_frame]");
		ctx.runOnClient(mc -> {
			Taz.config().floor7.arrowAlign.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	private void dragons() {
		ctx.runOnClient(mc -> {
			Taz.config().floor7.witherDragons.enabled = true;
			DungeonLocation.setTestFloor("F7");
			com.taz.dungeons.f7.WitherDragons.clear();
		});
		platform(40, 20, 70);
		ctx.waitTicks(25);
		check("dragons: in Phase 5", ctx.computeOnClient(mc -> DungeonLocation.f7Phase() == 5));
		cmd("particle minecraft:flame 27.0 19.0 59.0 2 3 2 0 20 force");
		ctx.waitTicks(3);
		check("dragons: Red is spawning (" + Alert.lastTitle + ")", ctx.computeOnClient(mc -> com.taz.dungeons.f7.WitherDragons.Dragon.RED.state()
				== com.taz.dungeons.f7.WitherDragons.State.SPAWNING) && "§c§lRed is spawning!".equals(Alert.lastTitle)
				&& hud(com.taz.dungeons.f7.WitherDragons.class).equals(List.of("Red 5.0s")));
		ctx.takeScreenshot("ported-dragons");
		serverTicks(101);
		check("dragons: spawned after 5 seconds", ctx.computeOnClient(mc -> com.taz.dungeons.f7.WitherDragons.Dragon.RED.state()
				== com.taz.dungeons.f7.WitherDragons.State.ALIVE));
		cmd("summon ender_dragon 27 20 59 {NoAI:1b}");
		ctx.waitTicks(5);
		cmd("kill @e[type=ender_dragon]");
		ctx.waitTicks(10);
		check("dragons: dead once killed", ctx.computeOnClient(mc -> com.taz.dungeons.f7.WitherDragons.Dragon.RED.state()
				== com.taz.dungeons.f7.WitherDragons.State.DEAD));
		ctx.runOnClient(mc -> {
			Taz.config().floor7.witherDragons.enabled = false;
			DungeonLocation.setTestFloor(null);
		});
	}

	// ---- helpers

	/** A stone floor under you at x y z, and you on it. */
	private void platform(int x, int y, int z) {
		cmd("tp @p " + x + " " + (y + 1) + " " + z);
		ctx.waitTicks(10);
		sp.getClientLevel().waitForChunksRender();
		cmd("fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + y + " " + (z + 2) + " stone");
		cmd("tp @p " + x + " " + (y + 1) + " " + z);
		ctx.waitTicks(5);
	}

	private static net.minecraft.world.entity.Entity nearest(net.minecraft.client.Minecraft mc, Class<? extends net.minecraft.world.entity.Entity> type) {
		return mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(20)).stream()
				.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
	}

	private String shown() {
		return ctx.computeOnClient(mc -> TickTimers.shown());
	}

	private void serverTicks(int n) {
		ctx.runOnClient(mc -> {
			for (int i = 0; i < n; i++) Taz.onServerTick();
		});
	}

	private List<String> hud(Class<?> type) {
		return ctx.computeOnClient(mc -> {
			for (MovableHud h : TazHud.all()) {
				if (type.isInstance(h)) return ((TextHud) h).shownLines(false).stream().map(net.minecraft.ChatFormatting::stripFormatting).toList();
			}
			throw new AssertionError(type.getSimpleName() + " not registered");
		});
	}

	private void tell(String text) {
		cmd("tellraw @a " + new JsonPrimitive(text));
		ctx.waitTicks(3);
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("ported: " + what);
		System.out.println("[PortedGameTest] ok: " + what);
	}
}
