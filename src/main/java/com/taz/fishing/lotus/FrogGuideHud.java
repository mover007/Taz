package com.taz.fishing.lotus;

import com.taz.fishing.trophy.Tier;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * What to fish for next on the Lotus Atoll: which Trophy Frogs can bite where you are right now, the weather and blessing,
 * and the frogs you still need at your tier with how to get each (✔ when you're set up for it now).
 * <pre>
 * Frog Guide · BRONZE 9/12
 * Can bite here: Common, Wetlands
 * Rain: Tropical Rain · Blessing: 23m
 *  ✘ Leap Frog: jump as you reel in
 *  ✔ Wetlands Frog: fish in the rain
 * </pre>
 */
public final class FrogGuideHud extends TextHud {
	public FrogGuideHud() {
		super("Frog Guide", 0.62, 0.20);
	}

	@Override
	protected HudPos pos() {
		return LotusConfig.get().guideHud;
	}

	@Override
	public boolean enabled() {
		LotusConfig cfg = LotusConfig.get();
		return cfg.enabled && cfg.guide;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) {
			return List.of("§c§lFrog Guide §7· §6BRONZE §f9§7/12", "§7Can bite here: §fCommon, Wetlands",
					"§7Rain: §aTropical Rain §7· Blessing: §a23m", " §c✘ §fLeap Frog§7: jump as you reel in",
					" §a✔ §fWetlands Frog§7: fish in the rain");
		}
		Player p = Minecraft.getInstance().player;
		if (p == null || !LotusAtoll.here()) return List.of();
		List<String> out = new ArrayList<>();
		Tier goal = TrophyFrogs.goalTier();
		out.add("§c§lFrog Guide" + (goal == null ? " §7· §aall 48 caught" : " §7· " + goal.code + goal.name() + " §f" + TrophyFrogs.caughtAt(goal) + "§7/12"));
		List<String> now = new ArrayList<>();
		for (TrophyFrogs.Species s : TrophyFrogs.Species.values()) if (Boolean.TRUE.equals(LotusAtoll.canBite(s, p))) now.add(s.label.replace(" Frog", ""));
		out.add("§7Can bite here: §f" + String.join(", ", now));
		long blessing = LotusAtoll.blessingLeft();
		String weather = LotusAtoll.weather();
		out.add("§7Rain: " + (weather == null ? "§8none" : "§a" + weather) + " §7· Blessing: "
				+ (blessing > 0 ? "§a" + (blessing / 60_000 + 1) + "m" : "§8none"));
		if (goal == null) return out;
		for (TrophyFrogs.Species s : TrophyFrogs.Species.values()) {
			if (TrophyFrogs.has(s, goal)) continue;
			Boolean ready = LotusAtoll.canBite(s, p);
			String mark = ready == null ? "§7•" : ready ? "§a✔" : "§c✘";
			out.add(" " + mark + " §f" + s.label + "§7: " + hint(s, p));
		}
		return out;
	}

	/** How to get a species, with what's missing right now where Taz can tell. */
	private static String hint(TrophyFrogs.Species s, Player p) {
		return switch (s) {
			case REALITY -> s.how + (LotusAtoll.froggles(p) ? "" : " §c(none on)");
			case BULL -> LotusAtoll.sweater(p) ? "Red Sweater on" : s.how;
			case BLESSED -> LotusAtoll.blessingLeft() > 0 ? "blessed, fish anywhere" : s.how;
			default -> s.how;
		};
	}
}
