package com.taz.inferno;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.lowballer.LowballTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The Fuel HUD: each Inferno minion's fuel and how long it lasts, soonest first; "No fuel" in red. */
public final class InfernoHud extends TextHud {
	public InfernoHud() {
		super("Inferno Fuel", 0.01, 0.30);
	}

	@Override
	protected HudPos pos() {
		return InfernoConfig.get().pos;
	}

	@Override
	public boolean enabled() {
		InfernoConfig cfg = InfernoConfig.get();
		return cfg.enabled && cfg.hud;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) {
			return List.of("§cInferno Fuel", "§7#1 XI §6Hypergolic §f6h 12m", "§7#2 XI §6Hypergolic §f6h 40m §8+ eyedrops", "§7#3 X §cNo fuel");
		}
		InfernoConfig cfg = InfernoConfig.get();
		if (cfg.hudIsland && !InfernoTracker.onIsland() || !InfernoTracker.loaded()) return List.of();
		long now = System.currentTimeMillis();
		List<InfernoTracker.Minion> minions = new ArrayList<>(InfernoTracker.minions().stream().filter(m -> !m.key.startsWith("?")).toList());
		if (minions.isEmpty()) return List.of();
		minions.sort(Comparator.comparingLong((InfernoTracker.Minion m) -> InfernoTracker.fuelEnds(m, now)).thenComparingInt(m -> m.number));
		List<String> out = new ArrayList<>();
		out.add("§cInferno Fuel" + (InfernoTracker.tabState().equals("FULL") ? " §c· storage full!" : ""));
		for (InfernoTracker.Minion m : minions) {
			long ends = InfernoTracker.fuelEnds(m, now);
			InfernoTracker.Run r = InfernoTracker.running(m, now);
			if (ends == 0 || r == null) {
				out.add("§7" + m.label() + " §cNo fuel");
				continue;
			}
			InfernoData.Fuel f = InfernoData.fuel(r.fuel);
			String left = LowballTracker.span(ends - now);
			out.add("§7" + m.label() + " " + f.grade().color + f.grade().label + " " + (ends - now < 3_600_000L ? "§c" : "§f") + left
					+ (m.eyedropsEnd > now ? " §8+ eyedrops" : ""));
		}
		return out;
	}
}
