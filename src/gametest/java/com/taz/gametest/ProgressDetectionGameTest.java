package com.taz.gametest;

import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.hud.TazHudEditor;
import com.taz.xpassist.screen.PlanScreen;
import com.taz.xpassist.screen.XpListScreen;
import com.taz.xpassist.progress.LocalProgress;
import com.taz.xpassist.progress.ProgressDetector;
import com.taz.xpassist.xp.XpResult;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

/**
 * Fakes Hypixel's menus (chests with the same titles and lore) and action bar messages,
 * and checks the mod ticks the matching upgrades off.
 */
public class ProgressDetectionGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip("xp")) return;
		context.waitFor(mc -> DataManager.result() != null || DataManager.error() != null, 20 * 300);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			context.runOnClient(mc -> {
				LocalProgress.setActiveProfile("TestProfile");
				LocalProgress.forgetCurrent();
			});
			// A sidebar titled SKYBLOCK, like Hypixel's, so the mod knows it is "on SkyBlock".
			server.runCommand("scoreboard objectives add sb dummy \"SKYBLOCK\"");
			server.runCommand("scoreboard objectives setdisplay sidebar sb");
			server.runCommand("scoreboard players set Purse sb 1");
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			server.runCommand("tp @p " + player.getX() + " " + player.getY() + " " + player.getZ() + " 180 0");
			BlockPos chest = player.north(2);

			// 1. SkyBlock Menu: "SkyBlock Leveling" item with your level.
			openChest(context, server, chest, "SkyBlock Menu",
				"{Slot:0b,id:\"minecraft:experience_bottle\",count:1,components:{\"minecraft:custom_name\":\"SkyBlock Leveling\","
					+ "\"minecraft:lore\":[\"Your SkyBlock Level: [376]\",\"Determine your level...\",\"45/100 XP\"]}}");
			check(context, "level from SkyBlock Menu", () -> LocalProgress.current().skyblockXp == 37645);

			// 2. Crafted Minions menu: ✔ / ✖ per tier.
			openChest(context, server, chest, "(1/2) Crafted Minions",
				"{Slot:0b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"Mycelium Minion\","
					+ "\"minecraft:lore\":[\"✔ Tier I\",\"✔ Tier II\",\"✖ Tier III\"]}}");
			check(context, "crafted minions from menu", () -> LocalProgress.current().craftedMinions.containsAll(Set.of("MYCELIUM_1", "MYCELIUM_2"))
				&& !LocalProgress.current().craftedMinions.contains("MYCELIUM_3"));

			// 3. Abiphone contacts task menu.
			openChest(context, server, chest, "Miscellaneous ➜ Abiphone Contacts",
				"{Slot:0b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"Rusty\",\"minecraft:lore\":[\"Add Rusty to your contacts\",\"COMPLETED\"]}},"
					+ "{Slot:1b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"Taylor\",\"minecraft:lore\":[\"This task can only be completed once!\"]}}");
			check(context, "abiphone contacts from menu", () -> LocalProgress.current().abiphoneContacts.contains("rusty")
				&& !LocalProgress.current().abiphoneContacts.contains("taylor"));

			// 4. Consumable items task menu.
			openChest(context, server, chest, "Miscellaneous ➜ Consumable Items",
				"{Slot:0b,id:\"minecraft:potion\",count:1,components:{\"minecraft:custom_name\":\"Metaphysical Serum\","
					+ "\"minecraft:custom_data\":{id:\"METAPHYSICAL_SERUM\"},\"minecraft:lore\":[\"Total Progress: 66.7%\"]}}");
			check(context, "consumables from menu", () -> LocalProgress.current().consumed.getOrDefault("METAPHYSICAL_SERUM", 0) == 2);

			// 5. Museum: clicking Confirm Donation.
			openChest(context, server, chest, ProgressDetector.CONFIRM_DONATION,
				"{Slot:0b,id:\"minecraft:bow\",count:1,components:{\"minecraft:custom_name\":\"Decent Bow\",\"minecraft:custom_data\":{id:\"DECENT_BOW\"}}}");
			context.runOnClient(mc -> ProgressDetector.onSlotClick((AbstractContainerScreen<?>) mc.screen, 20));
			context.setScreen(() -> null);
			check(context, "museum donation", () -> LocalProgress.current().museumDonated.contains("DECENT_BOW"));

			// 6. Live: craft a new minion -> "+12 SkyBlock XP (Minion Crafting) (57/100)" while holding it.
			server.runCommand("give @p minecraft:player_head[minecraft:custom_data={id:\"FLOWER_GENERATOR_11\"},minecraft:custom_name=\"Flower Minion XI\"]");
			context.waitTicks(10);
			server.runCommand("title @a actionbar \"+12 SkyBlock XP (Minion Crafting) (57/100)\"");
			check(context, "live minion craft", () -> LocalProgress.current().craftedMinions.contains("FLOWER_11")
				&& LocalProgress.current().skyblockXp == 37657);

			// 7. Live: eat a consumable (count drops) then the XP message arrives.
			server.runCommand("give @p minecraft:paper[minecraft:custom_data={id:\"WRIGGLING_LARVA\"}] 3");
			context.waitTicks(30);
			server.runCommand("clear @p minecraft:paper[minecraft:custom_data={id:\"WRIGGLING_LARVA\"}] 1");
			context.waitTicks(30);
			server.runCommand("title @a actionbar \"+5 SkyBlock XP (Consumable Items) (62/100)\"");
			check(context, "live consumable", () -> LocalProgress.current().consumed.getOrDefault("WRIGGLING_LARVA", 0) == 1
				&& LocalProgress.current().skyblockXp == 37662);

			// 8. Level up: progress wraps from 62/100 to 2/100.
			server.runCommand("title @a actionbar \"+40 SkyBlock XP (Accessory Bag) (2/100)\"");
			check(context, "level up", () -> LocalProgress.current().skyblockXp == 37702);

			// 9. "✆ Tony has been added to your Abiphone's contacts!" then the XP message: only Tony gets marked.
			int contactsBefore = context.computeOnClient(mc -> LocalProgress.current().abiphoneContacts.size());
			server.runCommand("tellraw @a \"✆ Tony has been added to your Abiphone's contacts!\"");
			check(context, "contact from chat", () -> LocalProgress.current().abiphoneContacts.contains("tony"));
			server.runCommand("title @a actionbar \"+10 SkyBlock XP (Abiphone Contacts) (12/100)\"");
			check(context, "contact XP not double counted", () -> LocalProgress.current().abiphoneContacts.size() == contactsBefore + 1
				&& LocalProgress.current().skyblockXp == 37712);

			// 10. Opening the Abiphone itself lists every contact you have.
			openChest(context, server, chest, "Abiphone XII Mega Color",
				"{Slot:0b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"Mad Redstone Engineer\"}},"
					+ "{Slot:1b,id:\"minecraft:player_head\",count:1,components:{\"minecraft:custom_name\":\"Kat\"}},"
					+ "{Slot:2b,id:\"minecraft:book\",count:1,components:{\"minecraft:custom_name\":\"Contacts Sorted By Name\",\"minecraft:lore\":[\"Your contacts: 4/60\"]}}");
			check(context, "contacts from Abiphone menu", () -> LocalProgress.current().abiphoneContacts.containsAll(Set.of("madredstoneengineer", "kat"))
				&& !LocalProgress.current().abiphoneContacts.contains("ted"));

			// The list no longer contains what was just done.
			context.waitTicks(20);
			check(context, "list updated", () -> {
				XpResult result = DataManager.result();
				for (XpResult.Entry entry : result.entries()) {
					if (entry instanceof XpResult.TaskEntry task) {
						String id = task.task().id;
						if (id.equals("minion:FLOWER_GENERATOR_11") || id.equals("museum:DECENT_BOW") || id.equals("minion:MYCELIUM_GENERATOR_1")
							|| id.equals("abiphone:Tony") || id.equals("abiphone:Mad Redstone Engineer")) {
							return false;
						}
					}
				}
				return result.progressKnown() && result.startXp() == 37712;
			});
			context.takeScreenshot("sbxp-progress-chat");

			// 12. Carrying a museum item makes it free and shows "Ready to donate" on the HUD.
			server.runCommand("give @p minecraft:iron_pickaxe[minecraft:custom_data={id:\"ROOKIE_PICKAXE\"},minecraft:custom_name=\"Rookie Pickaxe\"]");
			context.waitTicks(40);
			check(context, "carried museum item is free", () -> {
				for (XpResult.Entry entry : DataManager.result().entries()) {
					if (entry instanceof XpResult.TaskEntry task && task.task().id.equals("museum:ROOKIE_PICKAXE")) {
						return task.task().owned && task.task().cost == 0;
					}
				}
				return false;
			});
			context.takeScreenshot("sbxp-ready-to-donate");
			check(context, "auction items get an [AH] button", () -> {
				for (XpResult.Entry entry : DataManager.result().entries()) {
					if (entry instanceof XpResult.TaskEntry task && task.task().id.equals("museum:ZOMBIE_PICKAXE")) {
						return task.task().buys.stream().anyMatch(buy -> buy.kind().equals("ah"));
					}
				}
				return false;
			});

			// 14. /skyblockxp task pages: open tasks go to the "Other XP" tab, bank upgrades become tasks.
			openChest(context, server, chest, "Core ➜ Fast Travels Unlocked",
				"{Slot:0b,id:\"minecraft:paper\",count:1,components:{\"minecraft:custom_name\":\"Crypts\",\"minecraft:lore\":[\"Unlock this travel scroll\",\"+15 SkyBlock XP\",\"This task can only be completed once!\"]}},"
					+ "{Slot:1b,id:\"minecraft:paper\",count:1,components:{\"minecraft:custom_name\":\"Museum\",\"minecraft:lore\":[\"+15 SkyBlock XP\",\"COMPLETED\"]}}");
			check(context, "other XP tasks from /skyblockxp", () -> {
				List<LocalProgress.MenuTask> open = LocalProgress.openMenuTasks();
				return open.size() == 1 && open.getFirst().name.equals("Crypts") && open.getFirst().xp == 15;
			});
			openChest(context, server, chest, "Core ➜ Bank Upgrades",
				"{Slot:0b,id:\"minecraft:gold_ingot\",count:1,components:{\"minecraft:custom_name\":\"Gold Account\",\"minecraft:lore\":[\"COMPLETED\"]}},"
					+ "{Slot:1b,id:\"minecraft:gold_block\",count:1,components:{\"minecraft:custom_name\":\"Deluxe Account\",\"minecraft:lore\":[\"This task can only be completed once!\"]}}");
			check(context, "bank upgrades from /skyblockxp", () -> LocalProgress.current().bankUpgrades != null
				&& LocalProgress.current().bankUpgrades.equals(Set.of("BANK_UPGRADE_GOLD")));
			context.waitTicks(20);
			check(context, "bank upgrade tasks listed", () -> {
				for (XpResult.Entry entry : DataManager.result().entries()) {
					if (entry instanceof XpResult.TaskEntry task && task.task().id.equals("bank:BANK_UPGRADE_DELUXE")) {
						return !task.task().buys.isEmpty();
					}
				}
				return false;
			});
			context.setScreen(XpListScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("sbxp-list-tabs");
			context.setScreen(() -> null);

			// 13. Level plan screen.
			context.setScreen(() -> new PlanScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("sbxp-level-plan");
			check(context, "plan built", () -> DataManager.plan() != null && !DataManager.plan().stops().isEmpty());
			context.setScreen(() -> null);

			// 11. HUD editor: drag the box from the top-left to the middle, then scroll to make it bigger.
			context.runOnClient(mc -> {
				ModConfig.get().hud.hudX = 4;
				ModConfig.get().hud.hudY = 4;
				ModConfig.get().hud.hudScale = 1.0F;
			});
			context.setScreen(TazHudEditor::new);
			context.waitTicks(5);
			double guiScale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
			context.getInput().setCursorPos(20 * guiScale, 8 * guiScale);
			context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			for (int i = 1; i <= 10; i++) {
				context.getInput().setCursorPos((20 + 18 * i) * guiScale, (8 + 9 * i) * guiScale);
				context.waitTick();
			}
			context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.getInput().scroll(1);
			context.waitTicks(5);
			context.takeScreenshot("sbxp-hud-editor");
			check(context, "HUD dragged and resized", () -> ModConfig.get().hud.hudX >= 150 && ModConfig.get().hud.hudY >= 70
				&& Math.abs(ModConfig.get().hud.hudScale - 1.1F) < 0.01F);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(5);
			context.takeScreenshot("sbxp-hud-moved");
			context.runOnClient(mc -> {
				ModConfig.get().hud.hudX = 4;
				ModConfig.get().hud.hudY = 4;
				ModConfig.get().hud.hudScale = 1.0F;
				ModConfig.save();
			});
			context.runOnClient(mc -> LocalProgress.forgetCurrent());
		}
	}

	/** Places a named chest with items in front of the player and opens it like a Hypixel menu. */
	private static void openChest(ClientGameTestContext context, TestServerContext server, BlockPos pos, String title, String items) {
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " air");
		server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
			+ " minecraft:chest[facing=south]{CustomName:\"" + title + "\",Items:[" + items + "]}");
		context.waitTicks(5);
		context.getInput().lookAt(pos);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitForScreen(ContainerScreen.class);
		// The detector scans open menus every 10 ticks.
		context.waitTicks(25);
		context.takeScreenshot("sbxp-menu-" + title.replaceAll("[^A-Za-z]", ""));
		if (!title.equals(ProgressDetector.CONFIRM_DONATION)) {
			context.setScreen(() -> null);
		}
		context.waitTicks(5);
	}

	private static void check(ClientGameTestContext context, String what, java.util.function.BooleanSupplier condition) {
		context.waitTicks(10);
		boolean ok = context.computeOnClient(mc -> condition.getAsBoolean());
		if (!ok) {
			LocalProgress.Profile p = context.computeOnClient(mc -> LocalProgress.current());
			throw new AssertionError("Check failed: " + what + " | xp=" + p.skyblockXp + " minions=" + p.craftedMinions
				+ " contacts=" + p.abiphoneContacts + " consumed=" + p.consumed + " museum=" + p.museumDonated);
		}
		System.out.println("[SBXP TEST] OK: " + what);
	}
}
