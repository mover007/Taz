package com.taz.dungeons.f7;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.List;

/**
 * Tick Timers for the Floor 7 boss, counted in server ticks so they stay right through lag (from NoammAddons and Odin):
 * Maxor's start (83 ticks after his first line), Storm's pads (every 20 ticks), Storm's purple pad (62 ticks after
 * "ENERGY HEED MY CALL!" or "THUNDER LET ME BE YOUR CATALYST!") and Goldor's death ticks (every 60 ticks until the core opens).
 */
public final class TickTimers extends TextHud {
	private static int start = -1, goldor = -1, pad = -1, py = -1;
	private static boolean storm, pyDone;
	private static ClientLevel level;

	public TickTimers() {
		super("Tick Timers", 0.47, 0.55);
	}

	public static void onChat(String line) {
		TickTimersConfig cfg = TickTimersConfig.get();
		if (!cfg.enabled) return;
		switch (line) {
			case "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
				if (cfg.maxor) start = 83;
			}
			case "[BOSS] Storm: Pathetic Maxor, just like expected." -> {
				if (cfg.pad) {
					pad = 20;
					storm = true;
				}
			}
			case "[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!" -> {
				if (cfg.py && !pyDone) {
					pyDone = true;
					py = 62;
				}
			}
			case "[BOSS] Storm: I should have known that I stood no chance." -> {
				py = -1;
				pad = -1;
				storm = false;
			}
			case "[BOSS] Goldor: Who dares trespass into my domain?" -> {
				if (cfg.goldor) goldor = 60;
			}
			case "The Core entrance is opening!" -> goldor = -1;
			default -> {
			}
		}
	}

	/** Every server tick. */
	public static void onServerTick() {
		if (start >= 0) start--;
		if (py >= 0) py--;
		if (storm && pad >= 0 && --pad <= 0) pad = 20;
		if (goldor >= 0 && --goldor == 0) goldor = 60;
	}

	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			start = goldor = pad = py = -1;
			storm = pyDone = false;
		}
	}

	/** Test hook: the timer shown, or "" for none. */
	public static String shown() {
		List<String> lines = timer(false);
		return lines.isEmpty() ? "" : net.minecraft.ChatFormatting.stripFormatting(lines.get(0));
	}

	private static List<String> timer(boolean preview) {
		if (preview) return List.of(format("Start", 83, 83));
		if (start >= 0) return List.of(format("Start", start, 83));
		if (goldor >= 0) return List.of(format("Goldor", goldor, 60));
		if (py >= 0) return List.of(format("PY", py, 62));
		if (pad >= 0) return List.of(format("Pad", pad, 20));
		return List.of();
	}

	/** "Goldor 2.35s", coloured by how much is left. */
	private static String format(String name, int ticks, int max) {
		TickTimersConfig cfg = TickTimersConfig.get();
		double left = ticks / (double) max;
		String color = left > 0.66 ? "§a" : left > 0.33 ? "§e" : "§c";
		String time = cfg.inTicks ? ticks + (cfg.suffix ? "t" : "") : String.format("%.2f", ticks / 20.0) + (cfg.suffix ? "s" : "");
		return (cfg.prefix ? "§c" + name + " " : "") + color + time;
	}

	@Override
	protected HudPos pos() {
		return TickTimersConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return TickTimersConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (!preview && !DungeonLocation.inDungeon()) return List.of();
		return timer(preview);
	}
}
