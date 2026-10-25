package com.taz.xpassist.hud;

import com.taz.xpassist.config.ModConfig;
import com.taz.xpassist.data.DataManager;
import com.taz.xpassist.data.Rules;
import com.taz.xpassist.data.NeuRepo;
import com.taz.xpassist.npc.Npc;
import com.taz.xpassist.npc.NpcTracker;
import com.taz.xpassist.xp.TaskGroups;
import com.taz.xpassist.xp.TripPlanner;
import net.minecraft.world.phys.Vec3;
import com.taz.xpassist.npc.SkyblockLocation;
import com.taz.xpassist.util.TextUtil;
import com.taz.xpassist.xp.XpResult;
import com.taz.xpassist.xp.XpTask;
import com.taz.hud.MovableHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Small overlay: your level progress, the next few cheapest upgrades, and the NPC tracker. A movable Taz
 * HUD; hiding it in the HUD editor turns off the level and upgrade lines (the tracker still shows).
 */
public final class XpHud implements MovableHud {
	private static final int PADDING = 3;
	private static final float MIN_SCALE = 0.5F, MAX_SCALE = 3.0F;

	@Override
	public String name() {
		return "SkyBlock XP";
	}

	@Override
	public boolean enabled() {
		return ModConfig.get().enabled;
	}

	@Override
	public boolean hidden() {
		return !ModConfig.get().hud.hudEnabled;
	}

	@Override
	public void setHidden(boolean hidden) {
		ModConfig.get().hud.hudEnabled = !hidden;
	}

	@Override
	public float scale() {
		return ModConfig.get().hud.hudScale;
	}

	@Override
	public void setScale(float scale) {
		ModConfig.get().hud.hudScale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
	}

	@Override
	public void reset() {
		ModConfig.Hud hud = ModConfig.get().hud;
		hud.hudX = 4;
		hud.hudY = 4;
		hud.hudScale = 1.0F;
	}

	@Override
	public int[] bounds(Font font, int screenW, int screenH, boolean preview) {
		List<String> lines = lines(Minecraft.getInstance(), preview);
		if (lines.isEmpty()) return null;
		ModConfig.Hud hud = ModConfig.get().hud;
		int[] pos = clamp(font, lines, hud.hudX, hud.hudY, hud.hudScale, screenW, screenH);
		int[] size = size(font, lines);
		int pad = Math.round(PADDING * hud.hudScale);
		return new int[]{pos[0] - pad, pos[1] - pad, Math.round(size[0] * hud.hudScale), Math.round(size[1] * hud.hudScale)};
	}

	@Override
	public void moveTo(int x, int y, int screenW, int screenH) {
		ModConfig.Hud hud = ModConfig.get().hud;
		int pad = Math.round(PADDING * hud.hudScale);
		hud.hudX = x + pad;
		hud.hudY = y + pad;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, boolean preview) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!preview && minecraft.getDebugOverlay().showDebugScreen()) return;
		List<String> lines = lines(minecraft, preview);
		if (!lines.isEmpty()) {
			ModConfig config = ModConfig.get();
			draw(graphics, font, lines, config.hud.hudX, config.hud.hudY, config.hud.hudScale);
		}
	}

	/**
	 * The HUD text. With {@code preview} (the HUD editor), sample lines are used when there is nothing to show
	 * yet, and the level lines show even when hidden so the HUD can be shown again.
	 */
	public static List<String> lines(Minecraft minecraft, boolean preview) {
		ModConfig config = ModConfig.get();
		List<String> lines = new ArrayList<>();
		boolean showXp = (config.hud.hudEnabled || preview) && (preview || !config.hud.hudOnlyOnSkyblock || SkyblockLocation.onSkyblock());
		if (showXp) {
			xpLines(lines, config);
			freeLines(lines);
			if (config.hud.hudShoppingHints) {
				shoppingLines(lines, minecraft);
			}
		}
		List<String> tracker = NpcTracker.hudLines(minecraft);
		if (!tracker.isEmpty()) {
			if (!lines.isEmpty()) {
				lines.add("");
			}
			lines.addAll(tracker);
		}
		if (preview && lines.size() < 2) {
			lines.clear();
			lines.add("§6SkyBlock Level §b376 §8(31/100 XP)");
			lines.add("§71. §fMuseum - Donate Decent Bow §6500 §8(3 XP)");
			lines.add("§72. §fUnique Minions - Craft Mycelium Minion 1 §61.8K §8(1 XP)");
			lines.add("§a" + "Level 377 §7for §6192.6K");
		}
		return lines;
	}

	/** "Ready to donate: 3 items (+7 XP)" for things you already carry. */
	private static void freeLines(List<String> lines) {
		XpResult result = DataManager.result();
		if (result == null) {
			return;
		}
		int museum = 0;
		double museumXp = 0;
		int other = 0;
		double otherXp = 0;
		for (XpResult.Entry entry : result.entries()) {
			if (entry instanceof XpResult.TaskEntry taskEntry && taskEntry.task().owned) {
				if ("MUSEUM".equals(taskEntry.task().finish)) {
					museum++;
					museumXp += taskEntry.task().xp;
				} else {
					other++;
					otherXp += taskEntry.task().xp;
				}
			}
		}
		if (museum > 0) {
			lines.add("§a✔ Ready to donate: " + museum + " item" + (museum == 1 ? "" : "s") + " §2(+" + TextUtil.xp(museumXp) + " XP) §7/warp museum");
		}
		if (other > 0) {
			lines.add("§a✔ " + other + " free upgrade" + (other == 1 ? "" : "s") + " in your inventory §2(+" + TextUtil.xp(otherXp) + " XP)");
		}
	}

	/** Near (or tracking) an NPC from your level plan: what to buy there. */
	private static void shoppingLines(List<String> lines, Minecraft minecraft) {
		TripPlanner.Plan plan = DataManager.plan();
		NeuRepo neu = DataManager.neu();
		if (plan == null || neu == null || minecraft.player == null) {
			return;
		}
		TripPlanner.Stop here = null;
		double nearest = 25;
		String island = SkyblockLocation.island();
		Npc tracked = NpcTracker.target();
		for (TripPlanner.Stop stop : plan.stops()) {
			if (stop.npcId() == null) {
				continue;
			}
			if (tracked != null && tracked.id().equals(stop.npcId())) {
				here = stop;
				break;
			}
			Npc npc = neu.npcs.get(stop.npcId());
			if (npc == null || !npc.hasLocation() || (island != null && !island.equals(npc.island()))) {
				continue;
			}
			double distance = minecraft.player.position().distanceTo(new Vec3(npc.x() + 0.5, npc.y(), npc.z() + 0.5));
			if (distance < nearest) {
				nearest = distance;
				here = stop;
			}
		}
		if (here == null) {
			return;
		}
		lines.add("§eBuy at " + here.title() + ": §a+" + TextUtil.xp(here.xp()) + " XP §7for §6" + TextUtil.coins(here.cost()));
		List<TaskGroups.Line> shopping = TaskGroups.merge(here.tasks());
		int shown = 0;
		for (TaskGroups.Line line : shopping) {
			if (shown++ >= 4) {
				lines.add("§7  ...and " + (shopping.size() - 4) + " more (Level plan in /taz)");
				break;
			}
			lines.add("§7  • §f" + line.label() + " §6" + TextUtil.coins(line.cost()));
		}
	}

	/** Width and height of the box at scale 1, padding included. */
	public static int[] size(Font font, List<String> lines) {
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}
		return new int[]{width + PADDING * 2, lines.size() * (font.lineHeight + 1) + PADDING + 1};
	}

	/** Keeps the box on screen; returns the corrected {x, y} of the text's top-left corner. */
	public static int[] clamp(Font font, List<String> lines, int x, int y, float scale, int screenWidth, int screenHeight) {
		int[] size = size(font, lines);
		int pad = Math.round(PADDING * scale);
		int maxX = Math.max(pad, screenWidth - Math.round((size[0] - PADDING) * scale));
		int maxY = Math.max(pad, screenHeight - Math.round((size[1] - PADDING) * scale));
		return new int[]{Math.clamp(x, pad, maxX), Math.clamp(y, pad, maxY)};
	}

	/** Draws the text (no background) with its top-left corner at x, y. */
	public static void draw(GuiGraphicsExtractor graphics, Font font, List<String> lines, int x, int y, float scale) {
		int[] pos = clamp(font, lines, x, y, scale, graphics.guiWidth(), graphics.guiHeight());
		graphics.pose().pushMatrix();
		graphics.pose().translate(pos[0], pos[1]);
		graphics.pose().scale(scale, scale);
		int lineY = 0;
		for (String line : lines) {
			graphics.text(font, line, 0, lineY, 0xFFFFFFFF, true);
			lineY += font.lineHeight + 1;
		}
		graphics.pose().popMatrix();
	}

	private static void xpLines(List<String> lines, ModConfig config) {
		XpResult result = DataManager.result();
		Rules rules = DataManager.rules();
		if (result == null || rules == null) {
			lines.add("§6SkyBlock XP Assist §7- " + DataManager.status() + "...");
			if (DataManager.error() != null) {
				lines.add("§c" + DataManager.error());
			}
			return;
		}
		int perLevel = rules.xpPerLevel();
		if (result.progressKnown()) {
			int level = result.currentLevel(perLevel);
			int into = (int) Math.floor(result.startXp() - level * (double) perLevel);
			lines.add("§6SkyBlock Level §b" + level + " §8(" + into + "/" + perLevel + " XP)");
		} else {
			lines.add("§6SkyBlock XP Assist §8(no API key)");
		}
		if (DataManager.error() != null) {
			lines.add("§c" + DataManager.error());
		}
		double nextLevelCost = Double.NaN;
		int nextLevel = -1;
		List<XpTask> upcoming = new ArrayList<>();
		for (XpResult.Entry entry : result.entries()) {
			if (entry instanceof XpResult.TaskEntry taskEntry && upcoming.size() < config.hud.hudRows * 12) {
				upcoming.add(taskEntry.task());
			} else if (entry instanceof XpResult.LevelEntry levelEntry && nextLevel < 0) {
				nextLevel = levelEntry.level();
				nextLevelCost = levelEntry.cumulativeCost();
			}
			if (upcoming.size() >= config.hud.hudRows * 12 && nextLevel >= 0) {
				break;
			}
		}
		int shown = 0;
		for (TaskGroups.Line line : TaskGroups.merge(upcoming)) {
			if (shown >= config.hud.hudRows) {
				break;
			}
			String price = line.owned() ? "§a§lFREE" : "§6" + (line.partial() ? "~" : "") + TextUtil.coins(line.cost());
			lines.add("§7" + (shown + 1) + ". §f" + line.fullLabel() + " " + price + " §8(" + TextUtil.xp(line.xp()) + " XP)");
			shown++;
		}
		if (shown == 0) {
			lines.add("§7Nothing left to buy in the enabled categories.");
		}
		if (nextLevel >= 0) {
			String label = result.progressKnown() ? "Level " + nextLevel : "Next level";
			lines.add("§a" + label + " §7for §6" + TextUtil.coins(nextLevelCost, 2));
		}
	}
}
