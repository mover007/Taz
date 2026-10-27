package com.taz.foraging.safari;

import com.taz.Taz;
import com.taz.render.WorldRenderer;
import com.taz.util.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Throw preview: while you hold a Critter Capsule in the Safari, the arc a throw would fly and where it ends, green
 * when it would hit a critter you need. Hypixel's capsule isn't a vanilla projectile, so the arc is measured: after you
 * throw, the capsule that appears at your eyes is followed in flight, its speed, drag and gravity are worked out and
 * kept (vanilla snowball physics until the first throw), and each measured throw goes to the game log. It only draws:
 * aiming and throwing stay yours.
 */
public final class SafariThrow {
	/** Where a throw would go: the points it passes, and the critter it would hit (or null). */
	public record Arc(List<Vec3> points, SafariHelper.Sighting hits) {
	}

	/** Until a throw has been measured: vanilla snowball physics (1.5 blocks a tick, 1% drag, 0.03 gravity). */
	static final double SPEED = 1.5, DRAG = 0.99, GRAVITY = 0.03;
	private static final int MAX_TICKS = 120, MAX_SAMPLES = 30, LOGGED = 5;
	/** The capsule appears this close to your eyes, this soon after you throw (ms). */
	private static final double SPAWN_RANGE = 3.0;
	private static final long SPAWN_MS = 1000;
	/** A capsule is about this big: what it hits. */
	private static final double SIZE = 0.15;

	private static long thrownAt;
	private static Entity capsule;
	private static final List<Vec3> SAMPLES = new ArrayList<>();
	private static Arc arc;
	private static int logged;

	private SafariThrow() {
	}

	public static boolean holdingCapsule(Player player) {
		return Items.id(player.getMainHandItem()).endsWith("CRITTER_CAPSULE");
	}

	/** A right-click (on air, a block or an entity): with a capsule in hand, that's a throw. */
	public static void onUse(Player player) {
		if (holdingCapsule(player)) thrownAt = System.currentTimeMillis();
	}

	/** Every tick in the Safari: follow a capsule in flight, and work out the arc while you hold one. */
	public static void tick(Minecraft mc, SafariConfig cfg) {
		arc = null;
		if (!cfg.throwPreview) {
			clear();
			return;
		}
		follow(mc, cfg);
		if (holdingCapsule(mc.player)) arc = simulate(mc, cfg);
	}

	public static void clear() {
		arc = null;
		capsule = null;
		SAMPLES.clear();
	}

	/** The arc while you hold a capsule, or null. */
	public static Arc arc() {
		return arc;
	}

	private static void follow(Minecraft mc, SafariConfig cfg) {
		if (capsule != null) {
			if (capsule.isRemoved() || SAMPLES.size() >= MAX_SAMPLES) finish(cfg);
			else SAMPLES.add(capsule.position());
			return;
		}
		if (System.currentTimeMillis() - thrownAt > SPAWN_MS) return;
		Vec3 eye = mc.player.getEyePosition();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e.tickCount > 3 || !(e instanceof Display.ItemDisplay || e instanceof ArmorStand || e instanceof Projectile)) continue;
			if (e.position().distanceTo(eye) > SPAWN_RANGE) continue;
			capsule = e;
			SAMPLES.clear();
			SAMPLES.add(e.position());
			thrownAt = 0;
			return;
		}
	}

	/** The flight's over (or long enough): what it showed about the arc, kept as a running average. */
	private static void finish(SafariConfig cfg) {
		double[] f = fit(SAMPLES);
		if (logged++ < LOGGED) {
			Vec3 o = SAMPLES.get(0);
			List<String> pts = SAMPLES.stream().map(p -> String.format(Locale.ROOT, "(%.2f %.2f %.2f)", p.x - o.x, p.y - o.y, p.z - o.z)).toList();
			Taz.LOGGER.info("Safari: capsule flight ({}): {} ticks {}, {}", capsule.getType().toShortString(), SAMPLES.size(), pts,
					f == null ? "no fit" : String.format(Locale.ROOT, "speed %.3f drag %.4f gravity %.4f", f[0], f[1], f[2]));
		}
		if (f != null) {
			double w = cfg.throwsMeasured == 0 ? 1 : 0.3;
			cfg.throwSpeed = cfg.throwSpeed * (1 - w) + f[0] * w;
			cfg.throwDrag = cfg.throwDrag * (1 - w) + f[1] * w;
			cfg.throwGravity = cfg.throwGravity * (1 - w) + f[2] * w;
			cfg.throwsMeasured++;
			Taz.saveIfChanged();
		}
		capsule = null;
		SAMPLES.clear();
	}

	/**
	 * Speed, drag and gravity (a tick) from a capsule's positions, one a tick; null when there's too little clean flight.
	 * The flight ends where it hits something (it slows sharply), and ticks where it didn't move are skipped (the server
	 * may move it every other tick).
	 */
	public static double[] fit(List<Vec3> points) {
		List<Integer> at = new ArrayList<>();
		List<Vec3> pos = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			if (pos.isEmpty() || points.get(i).distanceToSqr(pos.get(pos.size() - 1)) > 1e-6) {
				at.add(i);
				pos.add(points.get(i));
			}
		}
		List<Vec3> v = new ArrayList<>();
		List<Integer> dt = new ArrayList<>();
		for (int k = 0; k + 1 < pos.size(); k++) {
			int d = at.get(k + 1) - at.get(k);
			Vec3 vk = pos.get(k + 1).subtract(pos.get(k)).scale(1.0 / d);
			if (!v.isEmpty() && flat(vk) < 0.6 * flat(v.get(v.size() - 1))) break;
			v.add(vk);
			dt.add(d);
		}
		if (v.size() < 4) return null;
		List<Double> drags = new ArrayList<>(), gravities = new ArrayList<>();
		for (int k = 0; k + 1 < v.size(); k++) {
			double step = (dt.get(k) + dt.get(k + 1)) / 2.0, h0 = flat(v.get(k)), h1 = flat(v.get(k + 1));
			double d = h0 > 0.05 ? Math.pow(h1 / h0, 1 / step) : 1;
			drags.add(d);
			gravities.add((v.get(k).y * Math.pow(d, step) - v.get(k + 1).y) / step);
		}
		double speed = v.get(0).length(), drag = Math.min(1, median(drags)), gravity = Math.max(0, median(gravities));
		if (speed < 0.2 || speed > 5 || drag < 0.8 || gravity > 0.2) return null;
		return new double[] {speed, drag, gravity};
	}

	private static double flat(Vec3 v) {
		return Math.sqrt(v.x * v.x + v.z * v.z);
	}

	private static double median(List<Double> values) {
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	/** The arc a throw from your eyes along your look would fly: tick by tick until it hits a block or a critter. */
	public static Arc simulate(Minecraft mc, SafariConfig cfg) {
		boolean measured = cfg.throwsMeasured > 0;
		double speed = measured ? cfg.throwSpeed : SPEED, drag = measured ? cfg.throwDrag : DRAG, gravity = measured ? cfg.throwGravity : GRAVITY;
		Player player = mc.player;
		Vec3 pos = player.getEyePosition().add(0, -0.1, 0);
		Vec3 v = player.getLookAngle().scale(speed);
		List<Vec3> points = new ArrayList<>(List.of(pos));
		for (int i = 0; i < MAX_TICKS; i++) {
			Vec3 next = pos.add(v);
			BlockHitResult block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			SafariHelper.Sighting hit = null;
			for (SafariHelper.Sighting s : SafariHelper.sightings()) {
				Optional<Vec3> at = SafariHelper.box(s).inflate(SIZE).clip(pos, end);
				if (at.isPresent() && at.get().distanceToSqr(pos) <= end.distanceToSqr(pos)) {
					end = at.get();
					hit = s;
				}
			}
			points.add(end);
			if (hit != null) return new Arc(points, hit);
			if (block.getType() != HitResult.Type.MISS || next.y < mc.level.getMinY()) break;
			pos = next;
			v = v.scale(drag).subtract(0, gravity, 0);
		}
		return new Arc(points, null);
	}

	/** The arc from just in front of you, and a small box where it ends; green on a critter you need, with its name. */
	public static void render(WorldRenderer.Batch batch, SafariConfig cfg) {
		Arc a = arc;
		if (a == null || a.points().size() < 2) return;
		int color = a.hits() == null ? 0xE0FFFFFF : SafariHelper.needed(a.hits(), cfg) ? 0xE040FF60 : 0xE0FFB040;
		Vec3 last = null;
		for (Vec3 p : a.points()) {
			if (last == null && p.distanceToSqr(batch.camera) < 1.5 * 1.5) continue;
			if (last != null) batch.line(last, p, color, 2.5f, true);
			last = p;
		}
		Vec3 end = a.points().get(a.points().size() - 1);
		batch.outline(new AABB(end, end).inflate(0.2), color, 2.0f, true);
		if (a.hits() != null) batch.label(end.add(0, 0.8, 0), a.hits().critter().name(), color, true);
	}
}
