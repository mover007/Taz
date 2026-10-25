package com.taz.gametest;

import com.taz.foraging.TreeTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

import java.util.Random;

/**
 * A dense forest with logs being chopped and growing back every tick, like a busy Galatea lobby.
 * Prints how long the tree tracker's update takes, so its cost on the game can be compared.
 */
public class TreeStressGameTest implements FabricClientGameTest {
	private static final int SPACING = 10;
	private static final int HALF = 7; // 15 x 15 trees

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip("trees")) return;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			int x0 = p.getX(), y0 = p.getY(), z0 = p.getZ();
			for (int i = -HALF; i <= HALF; i++) {
				for (int j = -HALF; j <= HALF; j++) {
					if (i == 0 && j == 0) continue;
					int tx = x0 + i * SPACING, tz = z0 + j * SPACING;
					// 3x3 trunk, 14 tall, with a 5x5 two-layer crown of logs: 176 logs per tree.
					cmd(world, "fill " + (tx - 1) + " " + y0 + " " + (tz - 1) + " " + (tx + 1) + " " + (y0 + 13) + " " + (tz + 1) + " stripped_spruce_wood");
					cmd(world, "fill " + (tx - 2) + " " + (y0 + 10) + " " + (tz - 2) + " " + (tx + 2) + " " + (y0 + 11) + " " + (tz + 2) + " stripped_spruce_wood");
				}
			}
			ctx.waitTicks(120);
			int logs = ctx.computeOnClient(mc -> TreeTracker.trackedLogs());
			int trees = ctx.computeOnClient(mc -> (int) TreeTracker.trees().stream().filter(t -> t.logs > 0).count());
			ctx.runOnClient(mc -> TreeTracker.resetTiming());

			// 10 seconds of chopping: one log broken every tick somewhere near you, one regrown every other tick.
			Random r = new Random(7);
			for (int tick = 0; tick < 200; tick++) {
				int i = r.nextInt(7) - 3, j = r.nextInt(7) - 3;
				if (i == 0 && j == 0) i = 1;
				int tx = x0 + i * SPACING + r.nextInt(3) - 1, ty = y0 + r.nextInt(10), tz = z0 + j * SPACING + r.nextInt(3) - 1;
				cmd(world, "setblock " + tx + " " + ty + " " + tz + (tick % 2 == 0 ? " air" : " stripped_spruce_wood"));
				ctx.waitTick();
			}
			double[] ms = ctx.computeOnClient(mc -> TreeTracker.timingMs());
			System.out.printf("[TreeStressGameTest] %d logs in %d trees; tracker time per tick while chopping: avg %.3f ms, worst %.3f ms%n",
					logs, trees, ms[0], ms[1]);
			if (logs < 20000) throw new AssertionError("forest not tracked: only " + logs + " logs");
			// Before the incremental tracker this was ~17 ms (a dropped frame twice a second).
			if (ms[1] > 8) throw new AssertionError("tracker too slow: worst tick " + ms[1] + " ms");
		}
	}

	private static void cmd(TestSingleplayerContext world, String c) {
		world.getServer().runCommand(c);
	}
}
