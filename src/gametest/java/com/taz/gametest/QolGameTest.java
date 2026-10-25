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

/**
 * Quality-of-life features from Odin and NoammAddons. Parts: chatcommands, chattweaks, shortcuts, slotbinds, loadouts,
 * autorequeue, renderopt, tweaks, helditem, blockoverlay, soundvolumes ("qol" runs them all).
 */
public class QolGameTest implements FabricClientGameTest {
	private static final String[] PARTS = {"chatcommands", "chattweaks", "shortcuts", "slotbinds", "loadouts", "autorequeue", "renderopt", "tweaks",
			"helditem", "blockoverlay", "soundvolumes"};
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
		}
		System.out.println("[QolGameTest] PASSED");
	}

	private String me() {
		return ctx.computeOnClient(mc -> mc.player.getName().getString());
	}

	private void chatCommands() {
		ctx.runOnClient(mc -> {
			Taz.config().general.chatCommands.enabled = true;
			com.taz.general.ChatCommands.clear();
		});
		int before = ctx.computeOnClient(mc -> TazChat.sent());
		tell("Party > [MVP+] Friend: !cf");
		ctx.waitTicks(12);
		check("chat commands: !cf answered in party chat", ctx.computeOnClient(mc -> TazChat.sent()) > before);
		ctx.runOnClient(mc -> TazChat.clearParty());
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
		ctx.runOnClient(mc -> Taz.config().general.chatCommands.enabled = false);
	}

	private void chatTweaks() {
		ctx.runOnClient(mc -> Taz.config().general.chat.enabled = true);
		check("chat: emoji codes", ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emoji("gg :skull: <3 o/")).equals("gg ☠ ❤ ( ﾟ◡ﾟ)/"));
		check("chat: emoji in /pc only", ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emojiCommand("pc <3")).equals("pc ❤")
				&& ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.emojiCommand("warp <3")).equals("warp <3"));
		tell("the newest message");
		String at = ctx.computeOnClient(mc -> com.taz.general.ChatTweaks.messageAt(10, mc.getWindow().getGuiScaledHeight() - 42));
		check("chat: the message under the mouse (" + at + ")", "the newest message".equals(at));
		ctx.runOnClient(mc -> Taz.config().general.chat.enabled = false);
	}

	private void shortcuts() {
		ctx.runOnClient(mc -> Taz.config().general.commandShortcuts.enabled = true);
		check("shortcuts: /dn is /warp dhub", ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dn")).equals("warp dhub")
				&& ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dn now")).equals("warp dhub now")
				&& ctx.computeOnClient(mc -> com.taz.general.CommandShortcuts.rewrite("dnx")).equals("dnx"));
		ctx.runOnClient(mc -> Taz.config().general.commandShortcuts.enabled = false);
	}

	private void slotBinds() {
		cmd("gamemode survival @p");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			Taz.config().general.slotBinds.enabled = true;
			Taz.config().general.slotBinds.bindKey = GLFW.GLFW_KEY_I;
			Taz.config().general.slotBinds.binds.clear();
			mc.setScreen(new InventoryScreen(mc.player));
		});
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.screen;
			com.taz.general.InventoryKeys.onKey(s, s.getMenu().getSlot(9), GLFW.GLFW_KEY_I);
			com.taz.general.InventoryKeys.onKey(s, s.getMenu().getSlot(37), GLFW.GLFW_KEY_I);
		});
		check("slot binds: slot 9 bound to hotbar slot 37 (" + ctx.computeOnClient(mc -> mc.screen.getClass().getSimpleName()) + ")",
				Integer.valueOf(37).equals(ctx.computeOnClient(mc -> Taz.config().general.slotBinds.binds.get("9"))));
		ctx.takeScreenshot("qol-slot-binds");
		ctx.runOnClient(mc -> {
			mc.setScreen(null);
			Taz.config().general.slotBinds.enabled = false;
		});
		cmd("gamemode creative @p");
	}

	private void loadouts() {
		ctx.runOnClient(mc -> Taz.config().general.loadoutKeybinds.enabled = true);
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
			Taz.config().general.loadoutKeybinds.enabled = false;
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
		ctx.runOnClient(mc -> Taz.config().general.renderOptimizer.enabled = true);
		cmd("summon experience_orb ~ ~1 ~3 {Value:5}");
		ctx.waitTicks(5);
		check("render optimizer: XP orbs hidden", ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
				mc.player.getBoundingBox().inflate(10)).stream().findFirst().map(com.taz.render.Hidden::hide).orElse(false)));
		check("render optimizer: explosions and screen fire", ctx.computeOnClient(mc -> com.taz.general.RenderOptimizer.hideParticle(
				net.minecraft.core.particles.ParticleTypes.EXPLOSION) && com.taz.general.RenderOptimizer.hideFire()));
		cmd("kill @e[type=experience_orb]");
		ctx.runOnClient(mc -> Taz.config().general.renderOptimizer.enabled = false);
	}

	private void tweaks() {
		ctx.runOnClient(mc -> {
			Taz.config().general.camera.enabled = true;
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(3);
		check("camera: no front view", ctx.computeOnClient(mc -> mc.options.getCameraType()) == CameraType.FIRST_PERSON);
		ctx.runOnClient(mc -> {
			Taz.config().general.camera.enabled = false;
			Taz.config().general.arrowFix.enabled = true;
		});
		check("arrow fix: a shortbow", ctx.computeOnClient(mc -> {
			ItemStack bow = new ItemStack(Items.BOW);
			bow.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Shortbow: Instantly shoots!"), Component.literal("LEGENDARY BOW"))));
			return com.taz.general.Tweaks.noPullback(bow) && !com.taz.general.Tweaks.noPullback(new ItemStack(Items.BOW));
		}));
		ctx.runOnClient(mc -> {
			Taz.config().general.arrowFix.enabled = false;
			Taz.config().general.autoSprint.enabled = true;
		});
		cmd("tp @p -20 -60 -20 180 0");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
		ctx.waitTicks(10);
		check("auto sprint: walking is sprinting", ctx.computeOnClient(mc -> mc.player.isSprinting()));
		ctx.runOnClient(mc -> {
			mc.options.keyUp.setDown(false);
			Taz.config().general.autoSprint.enabled = false;
		});
		ctx.waitTicks(5);
	}

	private void heldItem() {
		ctx.runOnClient(mc -> {
			var c = Taz.config().general.heldItem;
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
		ctx.runOnClient(mc -> Taz.config().general.heldItem.enabled = false);
		cmd("item replace entity @p weapon.mainhand with air");
	}

	private void blockOverlay() {
		ctx.runOnClient(mc -> Taz.config().general.blockOverlay.enabled = true);
		cmd("setblock 0 -60 3 stone");
		cmd("tp @p 0 -59 0 0 30");
		ctx.waitTicks(10);
		check("block overlay: replaces the outline", ctx.computeOnClient(mc -> com.taz.general.BlockOverlay.replaces()));
		ctx.takeScreenshot("qol-block-overlay");
		ctx.runOnClient(mc -> Taz.config().general.blockOverlay.enabled = false);
	}

	private void soundVolumes() {
		ctx.runOnClient(mc -> Taz.config().general.soundVolumes.enabled = true);
		check("sound volumes: amethyst at 1.35", ctx.computeOnClient(mc -> com.taz.general.SoundVolumes.multiplier("minecraft:block.amethyst_block.break")) == 1.35f
				&& ctx.computeOnClient(mc -> com.taz.general.SoundVolumes.multiplier("minecraft:block.stone.break")) == 1f);
		ctx.runOnClient(mc -> {
			Taz.config().general.soundVolumes.enabled = false;
			Taz.config().general.etherwarp.enabled = true;
			Taz.config().general.etherwarp.sound = true;
		});
		check("etherwarp sound: Hypixel's swapped for the XP orb", ctx.computeOnClient(mc -> com.taz.general.EtherwarpGuess.onSound("entity.ender_dragon.hurt", 0.53968257f)));
		ctx.runOnClient(mc -> Taz.config().general.etherwarp.enabled = false);
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
