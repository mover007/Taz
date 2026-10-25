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

/**
 * Features ported from Odin and NoammAddons. Parts: performance, blessings, masks, ragnarock, ticktimers, terracotta,
 * scorealerts, springboots, pets, kicktimer ("ported" runs them all). Hypixel sends a ping packet every server tick but
 * singleplayer doesn't, so the tests run the server-tick clock themselves. Runs with -Dtaz.dungeons.anywhere=true.
 */
public class PortedGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"performance", "blessings", "masks", "ragnarock", "ticktimers", "terracotta", "scorealerts",
			"springboots", "pets", "kicktimer"};
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
			ctx.takeScreenshot("ported-huds");
		}
		System.out.println("[PortedGameTest] PASSED");
	}

	private void performance() {
		ctx.runOnClient(mc -> Taz.config().general.performance.enabled = true);
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

	// ---- helpers

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
