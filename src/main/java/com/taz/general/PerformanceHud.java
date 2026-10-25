package com.taz.general;

import com.taz.dungeons.util.LagMonitor;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

import java.util.ArrayList;
import java.util.List;

/** Performance HUD: your FPS, the server's TPS and your ping (from Odin). Ping is measured the way F3's network chart does it. */
public final class PerformanceHud extends TextHud {
	private static long pingMs = -1;
	private static int ticks;

	public PerformanceHud() {
		super("Performance", 0.01, 0.01);
	}

	/** Every client tick: ask the server for a ping answer every two seconds. */
	public static void tick(Minecraft mc) {
		PerformanceHudConfig cfg = PerformanceHudConfig.get();
		if (!cfg.enabled || !cfg.ping || mc.getConnection() == null || ++ticks % 40 != 0) return;
		mc.getConnection().send(new ServerboundPingRequestPacket(System.currentTimeMillis()));
	}

	/** Your ping in ms: measured when the HUD shows it, otherwise the server's figure from the tab list. */
	public static long ping() {
		if (pingMs >= 0) return pingMs;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) return 0;
		var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	/** The server answered a ping (ClientPacketListenerMixin). */
	public static void onPong(long sentAt) {
		long ms = System.currentTimeMillis() - sentAt;
		if (ms >= 0 && ms < 60_000) pingMs = ms;
	}

	@Override
	protected HudPos pos() {
		return PerformanceHudConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return PerformanceHudConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		PerformanceHudConfig cfg = PerformanceHudConfig.get();
		List<String> parts = new ArrayList<>();
		if (cfg.tps) parts.add("§cTPS §f" + (preview || !LagMonitor.hasData() ? "20.0" : String.format("%.1f", LagMonitor.tps())));
		if (cfg.fps) parts.add("§cFPS §f" + (preview ? 144 : Minecraft.getInstance().getFps()));
		if (cfg.ping) parts.add("§cPing §f" + (preview ? "45ms" : pingMs < 0 ? "..." : pingMs + "ms"));
		if (parts.isEmpty()) return parts;
		return cfg.vertical ? parts : List.of(String.join("  ", parts));
	}
}
