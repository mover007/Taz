package com.taz.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.foraging.WhisperConfig;
import com.taz.foraging.WhisperHud;
import com.taz.foraging.WhisperTracker;
import com.taz.foraging.WhisperTracker.Type;
import com.taz.hud.MovableHud;
import com.taz.hud.TazHud;
import com.taz.hud.TazHudEditor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;

/**
 * Whisper Counter: fake tab list totals, logs breaking next to the player, hitting a log, the pause after 3 seconds
 * without foraging, spending whispers, switching servers, and moving the HUD in the HUD editor.
 */
public class WhisperGameTest implements FabricClientGameTest {
	private ClientGameTestContext ctx;
	private TestSingleplayerContext sp;
	private long forest = 12_000;
	private long desert = 500;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip("whispers")) return;
		ctx = context;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			sp = world;
			world.getClientLevel().waitForChunksRender();
			cmd("time set noon");
			cmd("gamemode creative @p");
			ctx.runOnClient(mc -> {
				WhisperConfig.get().enabled = true;
				WhisperConfig.get().forest = true;
				WhisperConfig.get().desert = true;
				WhisperConfig.get().pauseSeconds = 3;
				whisperHud().reset();
				WhisperTracker.reset();
			});
			setTotals();
			realWait(1000);
			check("totals read from the tab list", ctx.computeOnClient(mc -> WhisperTracker.seen(Type.FOREST) && WhisperTracker.seen(Type.DESERT)));
			check("nothing counted before foraging", gained(Type.FOREST) == 0 && gained(Type.DESERT) == 0 && activeMs() == 0 && paused());
			check("no rate before 10 seconds of foraging", ctx.computeOnClient(mc -> WhisperTracker.perHour(Type.FOREST)) == -1);

			// Chop for ~9 seconds: a log breaks next to you every second and whispers come in.
			BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
			long start = 0, last = 0;
			for (int i = 0; i < 10; i++) {
				String at = (p.getX() + 3) + " " + p.getY() + " " + (p.getZ() - 5 + i);
				cmd("setblock " + at + " oak_log");
				ctx.waitTicks(2);
				long tick = System.currentTimeMillis();
				cmd("setblock " + at + " air");
				forest += 120;
				if (i % 2 == 0) desert += 15;
				setTotals();
				if (i == 0) start = tick;
				last = tick;
				if (i == 5) {
					check("counting while chopping", !paused());
					ctx.takeScreenshot("whisper-hud-counting");
				}
				realWait(1000 - (System.currentTimeMillis() - tick));
			}

			// Stop: after 3 seconds without foraging the clock stops.
			realWait(4500);
			check("paused 3 seconds after the last log", paused());
			long expected = last - start + 3000;
			long counted = activeMs();
			check("clock ran only while foraging (" + counted + " ms, expected ~" + expected + ")",
					counted >= expected - 800 && counted <= expected + 1500);
			realWait(2000);
			check("clock stays stopped while not foraging", activeMs() == counted);
			check("forest gained 1,200 (" + gained(Type.FOREST) + ")", gained(Type.FOREST) == 1200);
			check("desert gained 75 (" + gained(Type.DESERT) + ")", gained(Type.DESERT) == 75);
			long rate = ctx.computeOnClient(mc -> WhisperTracker.perHour(Type.FOREST));
			check("forest per hour = gained / foraging time (" + rate + "/h)", rate == Math.round(1200 * 3_600_000.0 / counted));
			ctx.takeScreenshot("whisper-hud-paused");

			// Spending whispers isn't a loss, and the next gain counts from the new total.
			forest -= 5000;
			setTotals();
			realWait(1000);
			check("spending ignored", gained(Type.FOREST) == 1200 && paused() && activeMs() == counted);
			forest += 40;
			setTotals();
			realWait(1000);
			check("gain after spending counts from the new total", gained(Type.FOREST) == 1240 && !paused());
			realWait(3500);
			check("paused again", paused());

			// Hitting a log you can't break yet (mining fatigue) also counts as foraging.
			cmd("gamemode survival @p");
			cmd("effect give @p mining_fatigue 60 255 true");
			BlockPos log = p.offset(2, 1, 0);
			cmd("setblock " + log.getX() + " " + log.getY() + " " + log.getZ() + " oak_log");
			ctx.waitTicks(2);
			ctx.getInput().lookAt(log);
			ctx.waitTicks(2);
			long beforeHit = activeMs();
			ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			realWait(2000);
			check("counting while hitting a log", !paused());
			ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			check("the log wasn't broken", ctx.computeOnClient(mc -> !mc.level.getBlockState(log).isAir()));
			realWait(3500);
			long hitTime = activeMs() - beforeHit;
			check("hitting for 2 s counted ~5 s (" + hitTime + " ms)", paused() && hitTime >= 4200 && hitTime <= 6500);
			cmd("gamemode creative @p");

			// Another server or profile: the new totals are a fresh baseline, not a gain.
			ctx.runOnClient(mc -> WhisperTracker.onServerChange());
			forest = 900_000;
			desert = 40_000;
			setTotals();
			realWait(1000);
			check("server change isn't a gain", gained(Type.FOREST) == 1240 && gained(Type.DESERT) == 75 && paused());

			moveHud();

			ctx.runOnClient(mc -> WhisperTracker.reset());
			check("reset", gained(Type.FOREST) == 0 && gained(Type.DESERT) == 0 && activeMs() == 0);
			ctx.runOnClient(mc -> {
				WhisperTracker.testLines = null;
				whisperHud().reset();
				Taz.save();
			});
			System.out.println("[WhisperGameTest] PASSED");
		}
	}

	/** Drag the Whisper HUD in the HUD editor, scroll it bigger, and check it's saved. */
	private void moveHud() {
		ctx.setScreen(TazHudEditor::new);
		ctx.waitTicks(5);
		ctx.takeScreenshot("whisper-hud-editor");
		int[] b = ctx.computeOnClient(mc -> whisperHud().bounds(mc.font, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), true));
		check("HUD shown in the editor", b != null);
		double gs = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		double sx = b[0] + b[2] / 2.0, sy = b[1] + b[3] / 2.0;
		ctx.getInput().setCursorPos(sx * gs, sy * gs);
		ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 10; i++) {
			ctx.getInput().setCursorPos((sx - 12 * i) * gs, (sy + 4 * i) * gs);
			ctx.waitTick();
		}
		ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().scroll(1);
		ctx.waitTicks(5);
		ctx.takeScreenshot("whisper-hud-editor-moved");
		int[] moved = ctx.computeOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
			return new int[]{(int) Math.round(WhisperConfig.get().hud.x * w), (int) Math.round(WhisperConfig.get().hud.y * h)};
		});
		check("HUD dragged 120 left and 40 down (" + b[0] + "," + b[1] + " -> " + moved[0] + "," + moved[1] + ")",
				Math.abs(moved[0] - (b[0] - 120)) <= 2 && Math.abs(moved[1] - (b[1] + 40)) <= 2);
		check("HUD scrolled to 110%", Math.abs(ctx.computeOnClient(mc -> WhisperConfig.get().hud.scale) - 1.1f) < 0.01f);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(5);
		ctx.takeScreenshot("whisper-hud-moved");
		try {
			JsonObject saved = JsonParser.parseString(Files.readString(Taz.CONFIG_DIR.resolve("config.json"))).getAsJsonObject()
					.getAsJsonObject("galatea").getAsJsonObject("whispers").getAsJsonObject("hud");
			check("new spot saved", Math.abs(saved.get("scale").getAsFloat() - 1.1f) < 0.01f
					&& Math.abs(saved.get("x").getAsDouble() - ctx.computeOnClient(mc -> WhisperConfig.get().hud.x)) < 1e-6);
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static MovableHud whisperHud() {
		for (MovableHud hud : TazHud.all()) if (hud instanceof WhisperHud) return hud;
		throw new AssertionError("Whisper HUD not registered");
	}

	/** What Hypixel's tab list shows (with its colour codes and spacing). */
	private void setTotals() {
		List<String> lines = List.of(" Skills: Foraging 50", String.format(" Forest Whispers: %,d", forest), String.format(" Desert Whispers: %,d", desert));
		ctx.runOnClient(mc -> WhisperTracker.testLines = lines);
	}

	/** Ticks until this much real time has passed. */
	private void realWait(long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) ctx.waitTick();
	}

	private long gained(Type t) {
		return ctx.computeOnClient(mc -> WhisperTracker.gained(t));
	}

	private long activeMs() {
		return ctx.computeOnClient(mc -> WhisperTracker.activeMs());
	}

	private boolean paused() {
		return ctx.computeOnClient(mc -> WhisperTracker.paused());
	}

	private void cmd(String c) {
		sp.getServer().runCommand(c);
	}

	private static void check(String what, boolean ok) {
		if (!ok) throw new AssertionError("whispers: " + what);
		System.out.println("[WhisperGameTest] ok: " + what);
	}
}
