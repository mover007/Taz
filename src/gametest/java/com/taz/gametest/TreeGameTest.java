package com.taz.gametest;

import com.taz.foraging.TreeTracker;
import com.taz.foraging.TreesConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class TreeGameTest implements FabricClientGameTest {
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;
	private int x0, y0, z0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip("trees")) return;
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			x0 = p.getX();
			y0 = p.getY();
			z0 = p.getZ();
			cmd("time set noon");
			cmd("tp @p " + x0 + " " + y0 + " " + z0 + " facing " + (x0 + 14) + " " + (y0 + 4) + " " + (z0 + 4));
			ctx.waitTicks(20);

			int fx = x0 + 8, fz = z0;          // fig: 8 trunk + 4 branch = 12 logs
			int mx = x0 + 20, mz = z0 + 8;     // mangrove: 10 trunk + 4 branch = 14 logs
			List<int[]> fig = figBlocks(fx, fz);
			for (int[] b : fig) set(b, "stripped_spruce_wood");
			cmd("fill " + mx + " " + y0 + " " + mz + " " + mx + " " + (y0 + 9) + " " + mz + " mangrove_wood");
			cmd("fill " + mx + " " + (y0 + 7) + " " + (mz - 2) + " " + mx + " " + (y0 + 7) + " " + (mz + 2) + " mangrove_wood");
			// Placed by commands, so they look like they just grew: grown once settled (3.5s).
			ctx.waitTicks(15);
			expect("fresh fig just placed", fx, fz, TreeTracker.State.REGROWING, 12);
			ctx.waitTicks(80);
			expect("fresh fig", fx, fz, TreeTracker.State.GROWN, 12);
			expect("fresh mangrove", mx, mz, TreeTracker.State.GROWN, 14);
			expectNext("fig is closest", fx, fz);
			ctx.takeScreenshot("tree-1-grown");

			// Only Mangrove selected: the fig is no longer a target.
			ctx.runOnClient(mc -> TreesConfig.get().fig = false);
			ctx.waitTicks(15);
			expectNext("fig deselected, mangrove is next", mx, mz);
			ctx.takeScreenshot("tree-1b-only-mangrove");
			ctx.runOnClient(mc -> TreesConfig.get().fig = true);
			ctx.waitTicks(15);
			expectNext("fig selected again", fx, fz);

			// Chop 3 of the fig's 12 logs and walk away: stays chopped.
			cmd("setblock " + fx + " " + (y0 + 7) + " " + fz + " air");
			cmd("setblock " + (fx + 2) + " " + (y0 + 5) + " " + fz + " air");
			cmd("setblock " + (fx - 2) + " " + (y0 + 5) + " " + fz + " air");
			ctx.waitTicks(15);
			expect("fig being chopped", fx, fz, TreeTracker.State.CHOPPED, 9);
			expectNext("mangrove is next once the fig is chopped", mx, mz);
			ctx.waitTicks(100);
			expect("fig chopped and left", fx, fz, TreeTracker.State.CHOPPED, 9);
			ctx.takeScreenshot("tree-2-chopped");

			// Someone else chopping the mangrove: only the progress hologram shows it.
			cmd("summon armor_stand " + mx + " " + (y0 + 11) + " " + mz
					+ " {CustomName:\"MANGROVE TREE 42%\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b}");
			ctx.waitTicks(15);
			expect("mangrove with hologram", mx, mz, TreeTracker.State.CHOPPED, 14);
			expectNext("no grown tree left", Integer.MIN_VALUE, 0);
			cmd("kill @e[type=armor_stand]");
			ctx.waitTicks(60);
			expect("mangrove after hologram left", mx, mz, TreeTracker.State.GROWN, 14);

			// Fell the fig, then regrow it one log at a time in random order (like the regrowth animation).
			cmd("fill " + (fx - 2) + " " + y0 + " " + fz + " " + (fx + 2) + " " + (y0 + 7) + " " + fz + " air");
			ctx.waitTicks(15);
			expectLogs("fig felled", 0);
			List<int[]> order = new ArrayList<>(fig);
			Collections.shuffle(order, new Random(42));
			for (int i = 0; i < order.size(); i++) {
				set(order.get(i), "stripped_spruce_wood");
				ctx.waitTicks(4);
			}
			ctx.waitTicks(10);
			expect("fig regrowing", fx, fz, TreeTracker.State.REGROWING, 12);
			ctx.takeScreenshot("tree-3-regrowing");
			ctx.waitTicks(80);
			expect("fig regrown", fx, fz, TreeTracker.State.GROWN, 12);
			expectNext("regrown fig is closest again", fx, fz);
			int records = ctx.computeOnClient(mc -> TreeTracker.trees().size());
			if (records != 2) throw new AssertionError("expected 2 tree records (regrowth kept its identity), got " + records + ": " + dump());
			ctx.takeScreenshot("tree-4-regrown");
			System.out.println("[TreeGameTest] PASSED: " + dump());
		}
	}

	private List<int[]> figBlocks(int fx, int fz) {
		List<int[]> out = new ArrayList<>();
		for (int y = 0; y < 8; y++) out.add(new int[]{fx, y0 + y, fz});
		for (int dx : new int[]{-2, -1, 1, 2}) out.add(new int[]{fx + dx, y0 + 5, fz});
		return out;
	}

	private void set(int[] b, String block) {
		cmd("setblock " + b[0] + " " + b[1] + " " + b[2] + " " + block);
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private TreeTracker.Tree treeAt(int x, int z) {
		return ctx.computeOnClient(mc -> {
			for (TreeTracker.Tree t : TreeTracker.trees()) {
				if (t.logs > 0 && x >= t.minX && x <= t.maxX && z >= t.minZ && z <= t.maxZ && y0 >= t.minY && y0 <= t.maxY) return t;
			}
			return null;
		});
	}

	private void expect(String what, int x, int z, TreeTracker.State state, int logs) {
		TreeTracker.Tree t = treeAt(x, z);
		if (t == null || t.state != state || t.logs != logs) {
			throw new AssertionError(what + ": expected " + state + " with " + logs + " logs, got "
					+ (t == null ? "no tree" : t.state + " with " + t.logs + " logs") + " | " + dump());
		}
		System.out.println("[TreeGameTest] ok: " + what + " -> " + state + " (" + logs + " logs)");
	}

	private void expectLogs(String what, int logs) {
		int total = ctx.computeOnClient(mc -> TreeTracker.trackedLogs());
		if (total != 14 + logs) throw new AssertionError(what + ": expected " + (14 + logs) + " tracked logs, got " + total);
		System.out.println("[TreeGameTest] ok: " + what);
	}

	private void expectNext(String what, int x, int z) {
		TreeTracker.Tree next = ctx.computeOnClient(mc -> TreeTracker.nearestGrown());
		boolean ok = x == Integer.MIN_VALUE ? next == null
				: next != null && x >= next.minX && x <= next.maxX && z >= next.minZ && z <= next.maxZ;
		if (!ok) throw new AssertionError(what + ": next tree was " + (next == null ? "none" : next.kind + " @" + next.minX + "," + next.minZ));
		System.out.println("[TreeGameTest] ok: " + what);
	}

	private String dump() {
		return ctx.computeOnClient(mc -> {
			StringBuilder sb = new StringBuilder();
			for (TreeTracker.Tree t : TreeTracker.trees()) {
				sb.append(t.kind).append(' ').append(t.state).append(' ').append(t.logs).append(" logs [")
						.append(t.minX).append(',').append(t.minY).append(',').append(t.minZ).append(" .. ")
						.append(t.maxX).append(',').append(t.maxY).append(',').append(t.maxZ).append("]; ");
			}
			return sb.toString();
		});
	}
}
