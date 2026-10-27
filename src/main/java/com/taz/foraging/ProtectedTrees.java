package com.taz.foraging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.general.AlertsConfig;
import com.taz.util.Alert;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Trees the Next Tree Pointer must not send you to. */
public final class ProtectedTrees {
	static final String FINED = "You tried to cut the protected Tree and were fined";
	private static final long REGEN_MS = 30_000;
	private static final double NEAR = 6.0;
	private static final Path FILE = Taz.CONFIG_DIR.resolve("foraging").resolve("protected-trees.json");
	private static final Set<BlockPos> SPOTS = new LinkedHashSet<>();
	private static boolean loaded;
	private static int fines;

	private ProtectedTrees() {
	}

	public static boolean isProtected(TreeTracker.Tree t) {
		if (!loaded) load();
		for (BlockPos p : SPOTS) {
			if (p.getX() >= t.minX - 1 && p.getX() <= t.maxX + 1 && p.getY() >= t.minY - 1 && p.getY() <= t.maxY + 1
					&& p.getZ() >= t.minZ - 1 && p.getZ() <= t.maxZ + 1) return true;
		}
		return false;
	}

	public static int fines() {
		return fines;
	}

	public static int count() {
		if (!loaded) load();
		return SPOTS.size();
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		TreesConfig cfg = TreesConfig.get();
		if (line.startsWith(FINED)) {
			fines++;
			TreeTracker.Tree t = treeHit();
			if (t != null && cfg.skipProtected) {
				if (!loaded) load();
				SPOTS.add(new BlockPos((t.minX + t.maxX) / 2, (t.minY + t.maxY) / 2, (t.minZ + t.maxZ) / 2));
				save();
			}
			if (cfg.enabled && cfg.protectedAlert) {
				Alert.show("§c§lPROTECTED TREE", "§f-1,000 coins" + (t != null && cfg.skipProtected ? " §7· Taz won't point here again" : ""),
						AlertsConfig.get().sound);
			}
		} else if (line.startsWith(TreeGifts.REGENERATING)) {
			TreeTracker.Tree t = treeHit();
			if (t != null) t.regenUntilMs = System.currentTimeMillis() + REGEN_MS;
		}
	}

	/** The tree under your crosshair, or the closest one within a few blocks. */
	static TreeTracker.Tree treeHit() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos p = hit.getBlockPos();
			for (TreeTracker.Tree t : TreeTracker.trees()) {
				if (p.getX() >= t.minX - 1 && p.getX() <= t.maxX + 1 && p.getY() >= t.minY - 1 && p.getY() <= t.maxY + 1
						&& p.getZ() >= t.minZ - 1 && p.getZ() <= t.maxZ + 1) return t;
			}
		}
		TreeTracker.Tree best = null;
		double bestD = NEAR;
		for (TreeTracker.Tree t : TreeTracker.trees()) {
			double d = t.distanceTo(mc.player.position());
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	public static void forget() {
		SPOTS.clear();
		save();
	}

	private static void load() {
		loaded = true;
		try {
			if (!Files.exists(FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("trees")) {
				JsonArray a = e.getAsJsonArray();
				SPOTS.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not read {}", FILE, e);
		}
	}

	private static void save() {
		try {
			JsonArray trees = new JsonArray();
			for (BlockPos p : SPOTS) {
				JsonArray a = new JsonArray();
				a.add(p.getX());
				a.add(p.getY());
				a.add(p.getZ());
				trees.add(a);
			}
			JsonObject root = new JsonObject();
			root.add("trees", trees);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Taz.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
