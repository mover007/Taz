package com.taz.gametest;

import com.taz.Taz;
import com.taz.foraging.HoneyhiveTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;

/**
 * Fake Torrhus Canyon honeyhives: a ready one ("Click to loot!"), a refilling one ("Refill in: 59m 46s"),
 * a cosmetic one with no hologram, and a ready one whose block isn't a bee nest. Then looting,
 * Queen Bee, walking out of hologram range, and the saved refill times.
 */
public class HoneyhiveGameTest implements FabricClientGameTest {
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip("honeyhive")) return;
		ctx = context;
		// A file saved by the first version: one profile's list of hives with their refill times.
		long future = System.currentTimeMillis() + 30 * 60_000L;
		try {
			Files.createDirectories(Taz.CONFIG_DIR.resolve("foraging"));
			Files.writeString(Taz.CONFIG_DIR.resolve("foraging/honeyhives.json"), "{\"default\":[{\"x\":300,\"y\":70,\"z\":300,\"readyAt\":" + future
					+ "}],\"Banana\":[{\"x\":300,\"y\":70,\"z\":300,\"readyAt\":" + future + "}]}");
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		ctx.runOnClient(mc -> HoneyhiveTracker.resetForTest());
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			int x0 = p.getX(), y0 = p.getY(), z0 = p.getZ();
			cmd("time set noon");
			cmd("tp @p " + x0 + " " + y0 + " " + z0 + " facing " + (x0 + 6) + " " + (y0 + 1) + " " + z0);

			check("refill parse 59m 46s", parse("Refill in: 59m 46s") == (59 * 60 + 46) * 1000L);
			check("refill parse 1h 2m 3s", parse("Refill in: 1h 2m 3s") == (3600 + 2 * 60 + 3) * 1000L);
			check("refill parse 12s", parse("Refill in: 12s") == 12_000L);
			ctx.waitTicks(15);

			// Far-away hives are known before you ever see them, and count as ready until you loot them.
			BlockPos shipped = new BlockPos(-693, 93, 153);
			check("shipped hive known from the start", hive(shipped) != null && !hive(shipped).visible);
			check("shipped hive counts as ready", state(shipped) == HoneyhiveTracker.State.READY);
			int unmapped = ctx.computeOnClient(mc -> HoneyhiveTracker.unmappedClusters().size());
			check("4 clusters not mapped yet (" + unmapped + ")", unmapped == 4);
			check("old file: hive and its refill time read", state(new BlockPos(300, 70, 300)) == HoneyhiveTracker.State.REFILLING);

			BlockPos a = hive(x0 + 6, y0, z0, "bee_nest", "Click to loot!");          // ready
			BlockPos b = hive(x0 + 6, y0, z0 + 5, "bee_nest", "Refill in: 59m 46s");  // refilling
			BlockPos c = hive(x0 + 6, y0, z0 - 5, "bee_nest", null);                  // cosmetic: no hologram
			BlockPos d = hive(x0 + 12, y0, z0, "lapis_block", "Click to loot!");      // ready, block swapped
			ctx.waitTicks(30);

			check("real hives tracked, cosmetic one ignored", count(x0, z0) == 3 && hive(a) != null && hive(b) != null && hive(d) != null && hive(c) == null);
			check("ready hive is ready", state(a) == HoneyhiveTracker.State.READY);
			check("swapped-block hive found on its block", state(d) == HoneyhiveTracker.State.READY);
			HoneyhiveTracker.Hive hiveB = hive(b);
			long left = ctx.computeOnClient(mc -> hiveB.readyAtMs - System.currentTimeMillis());
			check("refilling hive refills in ~59m 46s (" + left / 1000 + "s)", state(b) == HoneyhiveTracker.State.REFILLING
					&& Math.abs(left - (59 * 60 + 46) * 1000L) < 5000);
			check("next hive is the closest ready one", nearestIs(a));
			ctx.takeScreenshot("honeyhive-1-ready");

			// Loot A: the chat line starts its hour, and its hologram switches to a refill timer.
			cmd("tp @p " + (x0 + 5) + " " + y0 + " " + z0);
			cmd("tellraw @a \"You stick your hand into the honeyhive and feel around...\"");
			ctx.waitTicks(2);
			check("loot message starts A's refill", state(a) == HoneyhiveTracker.State.REFILLING);
			relabel(a, "Refill in: 59m 59s");
			ctx.waitTicks(15);
			check("A stays refilling with its hologram", state(a) == HoneyhiveTracker.State.REFILLING);
			check("next hive moves to D", nearestIs(d));

			// Queen Bee refills B straight away (and its hologram goes back to "Click to loot!").
			cmd("tp @p " + (x0 + 5) + " " + y0 + " " + (z0 + 5));
			cmd("tellraw @a \"QUEEN BEE! The Honeyhive instantly refilled with loot!\"");
			ctx.waitTicks(2);
			check("Queen Bee makes B ready", state(b) == HoneyhiveTracker.State.READY);
			relabel(b, "Click to loot!");
			ctx.waitTicks(15);
			check("B stays ready", state(b) == HoneyhiveTracker.State.READY);

			// Walk out of hologram range: the hives and their times are remembered.
			cmd("tp @p " + (x0 - 120) + " " + y0 + " " + z0 + " facing " + x0 + " " + y0 + " " + z0);
			ctx.waitTicks(40);
			check("hives remembered out of range", count(x0, z0) == 3 && !hive(a).visible);
			ctx.takeScreenshot("honeyhive-far-away");
			check("out of range: A still refilling, B and D ready", state(a) == HoneyhiveTracker.State.REFILLING
					&& state(b) == HoneyhiveTracker.State.READY && state(d) == HoneyhiveTracker.State.READY);
			String saved = readSaved();
			check("saved in the new form", saved.contains("\"hives\"") && saved.contains("\"refills\""));
			check("other profile's refill time kept", saved.contains("\"Banana\"") && saved.contains("300,70,300"));
			cmd("tp @p " + x0 + " " + y0 + " " + (z0 - 8) + " facing " + (x0 + 6) + " " + (y0 + 1) + " " + (z0 + 2));
			ctx.waitTicks(30);
			ctx.takeScreenshot("honeyhive-2-after-loot");
			System.out.println("[HoneyhiveGameTest] PASSED");
		}
	}

	/** A hive on a post with an optional "Honeyhive" hologram and second line over it. */
	private BlockPos hive(int x, int y, int z, String block, String secondLine) {
		cmd("setblock " + x + " " + y + " " + z + " oak_fence");
		cmd("setblock " + x + " " + (y + 1) + " " + z + " " + block);
		if (secondLine != null) {
			cmd("summon armor_stand " + (x + 0.5) + " " + (y + 2.55) + " " + (z + 0.5)
					+ " {CustomName:\"Honeyhive\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"hive\"]}");
			cmd("summon armor_stand " + (x + 0.5) + " " + (y + 2.25) + " " + (z + 0.5)
					+ " {CustomName:\"" + secondLine + "\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"line2\"]}");
		}
		return new BlockPos(x, y + 1, z);
	}

	/** Replace a hive's second hologram line. */
	private void relabel(BlockPos hive, String secondLine) {
		double x = hive.getX() + 0.5, y = hive.getY() + 1.25, z = hive.getZ() + 0.5;
		cmd("kill @e[type=armor_stand,tag=line2,x=" + x + ",y=" + y + ",z=" + z + ",distance=..0.5]");
		cmd("summon armor_stand " + x + " " + y + " " + z
				+ " {CustomName:\"" + secondLine + "\",CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"line2\"]}");
	}

	private HoneyhiveTracker.Hive hive(BlockPos pos) {
		return ctx.computeOnClient(mc -> {
			for (HoneyhiveTracker.Hive h : HoneyhiveTracker.hives()) if (h.block.equals(pos)) return h;
			return null;
		});
	}

	private HoneyhiveTracker.State state(BlockPos pos) {
		HoneyhiveTracker.Hive h = hive(pos);
		return h == null ? null : ctx.computeOnClient(mc -> h.state(System.currentTimeMillis()));
	}

	/** Hives in the test area (the shipped Torrhus Canyon hives are always known too). */
	private int count(int x0, int z0) {
		return ctx.computeOnClient(mc -> (int) HoneyhiveTracker.hives().stream()
				.filter(h -> Math.abs(h.block.getX() - x0) < 40 && Math.abs(h.block.getZ() - z0) < 40).count());
	}

	private static String readSaved() {
		try {
			return Files.readString(Taz.CONFIG_DIR.resolve("foraging/honeyhives.json"));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private boolean nearestIs(BlockPos pos) {
		return ctx.computeOnClient(mc -> HoneyhiveTracker.nearestReady() != null && HoneyhiveTracker.nearestReady().block.equals(pos));
	}

	private long parse(String text) {
		return ctx.computeOnClient(mc -> HoneyhiveTracker.parseRefillForTest(text));
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("honeyhive: " + what);
		System.out.println("[HoneyhiveGameTest] ok: " + what);
	}
}
