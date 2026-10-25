package com.taz.gametest;

import com.taz.xpassist.XpActions;
import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.screen.NpcFinderScreen;
import com.taz.xpassist.screen.XpListScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

import java.util.List;

public class XpAssistClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip("xp")) return;
		// Live data download (NEU repo, Hypixel items, prices) can take a while on first run.
		context.waitFor(mc -> DataManager.result() != null, 20 * 300);
		context.runOnClient(mc -> {
			// The test account has no SkyBlock profile, so a profile error is expected; the list itself must load.
			if (DataManager.result() == null) {
				throw new AssertionError("Data failed to load: " + DataManager.error());
			}
			ModConfig.get().hud.hudOnlyOnSkyblock = false;
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientLevel().waitForChunksRender();
			BlockPos player = context.computeOnClient(mc -> mc.player.blockPosition());
			int x = player.getX();
			int y = player.getY();
			int z = player.getZ();
			// A stand-in "NPC" 14 blocks south behind a stone wall.
			singleplayer.getServer().runCommand("tp @p " + x + " " + y + " " + z + " 0 10");
			singleplayer.getServer().runCommand("fill " + (x - 4) + " " + y + " " + (z + 6) + " " + (x + 4) + " " + (y + 4) + " " + (z + 6) + " stone");
			singleplayer.getServer().runCommand("summon villager " + x + " " + y + " " + (z + 14) + " {NoAI:1b,CustomName:\"Gregory\",CustomNameVisible:1b}");
			context.waitTicks(20);
			context.runOnClient(mc -> NpcTracker.track(new Npc("TEST_NPC", "Gregory", "hub", x, y, z + 14,
				List.of("This NPC only appears during an Ender Dragon fight in The End."), "NPC")));
			context.waitTicks(40);
			context.takeScreenshot("sbxp-tracker-through-wall");

			// Far away target: beam + label, and the HUD arrow pointing to the side.
			singleplayer.getServer().runCommand("tp @p " + x + " " + y + " " + z + " 90 0");
			context.waitTicks(20);
			context.takeScreenshot("sbxp-tracker-hud-arrow");
			singleplayer.getServer().runCommand("tp @p " + x + " " + y + " " + (z - 60) + " 0 0");
			context.waitTicks(40);
			context.takeScreenshot("sbxp-tracker-far");

			context.setScreen(XpListScreen::new);
			context.waitTicks(10);
			context.takeScreenshot("sbxp-xp-list");

			context.setScreen(() -> new NpcFinderScreen(null));
			context.waitTicks(5);
			context.getInput().typeChars("decent bow");
			context.waitTicks(5);
			context.takeScreenshot("sbxp-npc-finder");
			context.setScreen(() -> null);

			context.runOnClient(mc -> XpActions.next(4));
			context.waitTicks(10);
			context.takeScreenshot("sbxp-chat-next");
		}
	}
}
