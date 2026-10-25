package com.taz.dungeons.f7;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Leap Counter: standing at a Floor 7 leap spot (simon says, early enter 2 and 3, core, inside core, relics), how many
 * teammates have leaped to you, and an alert once everyone has. From NoammAddons; its spots and how many come to each.
 */
public final class LeapCounter extends TextHud {
	private enum Spot {
		SS(box(106, 119, 92, 109, 121, 96), 3, v -> section(v) == 1),
		EE2(box(57, 108, 130, 59, 110, 132), 4, v -> section(v) == 2),
		HIGH_EE2(box(57, 132, 138, 62, 133, 140), 4, v -> section(v) == 2),
		EE3(box(1, 108, 101, 3, 110, 107), 3, v -> section(v) == 3),
		CORE(box(51, 114, 54, 58, 117, 49), 4, v -> section(v) == 4),
		IN_CORE(box(51, 114, 60, 58, 117, 55), 4, v -> box(68, 106, 54, 42, 155, 119).contains(v)),
		RELICS(box(46.5, 3, 68.5, 62.5, 8, 84.5), 4, v -> v.y <= 45);

		final AABB box;
		final int most;
		final Predicate<Vec3> arrived;
		final Set<Integer> leaped = new HashSet<>();
		long doneMs;

		Spot(AABB box, int most, Predicate<Vec3> arrived) {
			this.box = box;
			this.most = most;
			this.arrived = arrived;
		}
	}

	private static Spot spot;
	private static int teammates;
	private static ClientLevel level;

	public LeapCounter() {
		super("Leap Counter", 0.45, 0.62);
	}

	private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return new AABB(x1, y1, z1, x2, y2, z2);
	}

	private static int section(Vec3 v) {
		return DungeonLocation.p3SectionAt(v.x, v.y, v.z);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.level != level) {
			level = mc.level;
			for (Spot s : Spot.values()) {
				s.leaped.clear();
				s.doneMs = 0;
			}
		}
		spot = null;
		LeapCounterConfig cfg = LeapCounterConfig.get();
		if (!cfg.enabled || mc.player == null || mc.level == null || DungeonLocation.floorNumber() != 7 || !DungeonLocation.inBoss()) return;
		long now = System.currentTimeMillis();
		for (Spot s : Spot.values()) {
			if (s.box.contains(mc.player.position()) && (s.doneMs == 0 || now - s.doneMs < 1000)) {
				spot = s;
				break;
			}
		}
		if (spot == null) return;
		List<? extends Player> others = mc.level.players().stream().filter(p -> p != mc.player).toList();
		teammates = others.size();
		for (Player p : others) if (spot.arrived.test(p.position())) spot.leaped.add(p.getId());
		int most = Math.min(teammates, spot.most);
		if (spot.doneMs == 0 && most > 0 && spot.leaped.size() >= most) {
			spot.doneMs = now;
			if (cfg.alert) Alert.show("§cEveryone Leaped!", null, Alert.Sound.ORB);
		}
	}

	@Override
	protected HudPos pos() {
		return LeapCounterConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return LeapCounterConfig.get().enabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (preview) return List.of("§c3§f/4 leaped");
		if (spot == null) return List.of();
		int most = Math.min(teammates, spot.most);
		if (most <= 0) return List.of();
		return List.of((spot.leaped.size() >= most ? "§a" : "§c") + spot.leaped.size() + "§f/" + most + " leaped");
	}
}
