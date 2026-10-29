package com.taz.fishing.crimson;

import com.taz.fishing.trophy.Tier;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * What to fish for next on the Crimson Isle: which Trophy Fish can bite where you are right now, the volcano, your mana
 * and bobber, and the fish you still need at your tier with how to get each (✔ when you're set up for it now).
 * <pre>
 * Fish Guide · BRONZE 15/18
 * Can bite here: Blobfish, Lavahorse, Volcanic Stonefish
 * Volcano: INACTIVE · Mana: 558 · Bobber: 12s
 *  ✘ Mana Ray: have 1,200+ mana (558)
 *  • Gusher: Blazing Volcano, 7-16 min after it erupts
 * </pre>
 */
public final class FishGuideHud extends TextHud {
	public FishGuideHud() {
		super("Fish Guide", 0.62, 0.20);
	}

	@Override
	protected HudPos pos() {
		return TrophyFishConfig.get().guideHud;
	}

	@Override
	public boolean enabled() {
		TrophyFishConfig cfg = TrophyFishConfig.get();
		return cfg.enabled && cfg.guide;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) {
			return List.of("§c§lFish Guide §7· §6BRONZE §f15§7/18", "§7Can bite here: §fBlobfish, Lavahorse, Volcanic Stonefish",
					"§7Volcano: §fINACTIVE §7· Mana: §f558 §7· Bobber: §f12s", " §c✘ §fMana Ray§7: have 1,200+ mana §c(558)",
					" §7• §fGusher§7: Blazing Volcano, 7-16 min after it erupts");
		}
		Player p = Minecraft.getInstance().player;
		if (p == null || !TrophyFish.here()) return List.of();
		List<String> out = new ArrayList<>();
		Tier goal = TrophyFish.BOOK.goalTier();
		out.add("§c§lFish Guide" + (goal == null ? " §7· §aall 72 caught" : " §7· " + goal.code + goal.name() + " §f" + TrophyFish.BOOK.caughtAt(goal) + "§7/18"));
		List<String> now = new ArrayList<>();
		for (TrophyFish.Fish f : TrophyFish.Fish.values()) if (Boolean.TRUE.equals(TrophyFish.canBite(f, p))) now.add(f.label);
		out.add("§7Can bite here: §f" + String.join(", ", now));
		String volcano = TrophyFish.volcano();
		int bobber = TrophyFish.bobberSeconds(p);
		out.add("§7Volcano: " + (volcano == null ? "§8?" : "§f" + volcano) + " §7· Mana: " + (TrophyFish.mana() < 0 ? "§8?" : "§f" + TrophyFish.mana())
				+ " §7· Bobber: " + (bobber < 0 ? "§8none" : "§f" + bobber + "s"));
		if (goal == null) return out;
		for (TrophyFish.Fish f : TrophyFish.Fish.values()) {
			if (TrophyFish.has(f, goal)) continue;
			Boolean ready = TrophyFish.canBite(f, p);
			String mark = ready == null ? "§7•" : ready ? "§a✔" : "§c✘";
			out.add(" " + mark + " §f" + f.label + "§7: " + hint(f, p));
		}
		return out;
	}

	/** How to get a fish, with what's missing right now where Taz can tell. */
	private static String hint(TrophyFish.Fish f, Player p) {
		return switch (f) {
			case MANA_RAY -> f.how + (TrophyFish.mana() >= 0 && TrophyFish.mana() < 1200 ? " §c(" + TrophyFish.mana() + ")" : "");
			case SLUGFISH -> f.how + (TrophyFish.bobberSeconds(p) >= 0 ? " §f(" + TrophyFish.bobberSeconds(p) + "s)" : "");
			case VANILLE -> TrophyFish.holdingStarterRod(p) ? "Starter Lava Rod in hand" : f.how;
			default -> f.how;
		};
	}
}
