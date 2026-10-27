package com.taz.dungeons.f7;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terminal Solver: in Floor 7's terminals, everything but the slots to click goes dark, and those slots light up in red
 * (Red-Green, Colors, Start-With), in order with their numbers (Numbers), with how many left or right clicks each needs
 * (Rubix, red for left and grey for right), or with the column and the button to press when it lines up (Melody).
 * Odin's solvers, with NoammAddons' names.
 */
public final class TerminalSolver {
	public enum Type {
		RED_GREEN, RUBIX, NUMBERS, STARTS_WITH, COLORS, MELODY
	}

	private static final Pattern STARTS_WITH = Pattern.compile("^What starts with: '(\\w)'\\?$");
	private static final Pattern SELECT = Pattern.compile("^Select all the ([\\w ]+) items!$");
	private static final List<DyeColor> RUBIX_ORDER = List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED);
	private static final int[] RUBIX_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

	private static Type type;
	private static String arg;
	private static AbstractContainerScreen<?> screen;
	/** Slot -> what to draw on it: the colour and an optional label. */
	private static final Map<Integer, Integer> COLOR = new HashMap<>();
	private static final Map<Integer, String> LABEL = new HashMap<>();
	private static final Set<Integer> CLICKED = new HashSet<>();
	private static DyeColor rubixGoal;
	private static int size;

	private TerminalSolver() {
	}

	/** The terminal a window title belongs to, or null. */
	public static Type typeOf(String title) {
		return switch (title) {
			case "Correct all the panes!" -> Type.RED_GREEN;
			case "Change all to same color!" -> Type.RUBIX;
			case "Click in order!" -> Type.NUMBERS;
			case "Click the button on time!" -> Type.MELODY;
			default -> STARTS_WITH.matcher(title).matches() ? Type.STARTS_WITH : SELECT.matcher(title).matches() ? Type.COLORS : null;
		};
	}

	private static boolean on(Type t) {
		TerminalSolverConfig c = TerminalSolverConfig.get();
		return c.enabled && switch (t) {
			case RED_GREEN -> c.redGreen;
			case RUBIX -> c.rubix;
			case NUMBERS -> c.numbers;
			case STARTS_WITH -> c.startsWith;
			case COLORS -> c.colors;
			case MELODY -> c.melody;
		};
	}

	/** Every client tick: which terminal is open, and its answer from the slots as they are now. */
	public static void tick(Minecraft mc) {
		if (!(mc.screen instanceof AbstractContainerScreen<?> s) || !(s.getMenu() instanceof ChestMenu menu)) {
			close();
			return;
		}
		String title = s.getTitle().getString();
		Type t = typeOf(title);
		if (t == null || !on(t)) {
			close();
			return;
		}
		if (s != screen) {
			close();
			screen = s;
			type = t;
			Matcher m = STARTS_WITH.matcher(title);
			if (m.matches()) arg = m.group(1);
			m = SELECT.matcher(title);
			if (m.matches()) arg = m.group(1);
		}
		size = menu.getRowCount() * 9;
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < size; i++) items.add(menu.getSlot(i).getItem());
		solve(items);
	}

	private static void close() {
		screen = null;
		type = null;
		arg = null;
		rubixGoal = null;
		COLOR.clear();
		LABEL.clear();
		CLICKED.clear();
	}

	/** Works out what to show on each slot (also used by the tests with made-up slots). */
	public static void solve(List<ItemStack> items) {
		COLOR.clear();
		LABEL.clear();
		switch (type) {
			case RED_GREEN -> {
				for (int i = 0; i < items.size(); i++) if (pane(items.get(i)) == DyeColor.RED) COLOR.put(i, 0xFFFF2E2E);
			}
			case COLORS -> {
				Set<String> prefixes = prefixes(arg);
				for (int i = 0; i < items.size(); i++) {
					ItemStack s = items.get(i);
					if (s.isEmpty() || pane(s) == DyeColor.BLACK || s.hasFoil()) continue;
					String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
					if (prefixes.stream().anyMatch(name::startsWith)) COLOR.put(i, 0xFFFF2E2E);
				}
			}
			case STARTS_WITH -> {
				for (int i = 0; i < items.size(); i++) {
					ItemStack s = items.get(i);
					if (s.isEmpty() || CLICKED.contains(i) || !s.getHoverName().getString().regionMatches(true, 0, arg, 0, 1)) continue;
					boolean shinyAnyway = s.getItem() == Items.GOLDEN_APPLE || s.getItem().components().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
					if (!s.hasFoil() || shinyAnyway) COLOR.put(i, 0xFFFF2E2E);
				}
			}
			case NUMBERS -> {
				List<Integer> red = new ArrayList<>();
				for (int i = 0; i < items.size(); i++) if (pane(items.get(i)) == DyeColor.RED) red.add(i);
				red.sort((a, b) -> Integer.compare(items.get(a).getCount(), items.get(b).getCount()));
				int[] colors = {0xFFFF2E2E, 0xFFFFB0B0, 0xFFB3121B};
				for (int k = 0; k < red.size(); k++) {
					COLOR.put(red.get(k), k < 3 ? colors[k] : 0x00000000);
					if (TerminalSolverConfig.get().showNumbers || k < 3) LABEL.put(red.get(k), String.valueOf(items.get(red.get(k)).getCount()));
				}
			}
			case RUBIX -> {
				Map<Integer, DyeColor> panes = new HashMap<>();
				for (int i : RUBIX_SLOTS) {
					DyeColor c = i < items.size() ? pane(items.get(i)) : null;
					if (c != null && c != DyeColor.BLACK) panes.put(i, c);
				}
				if (rubixGoal == null && panes.size() == RUBIX_SLOTS.length) {
					int best = Integer.MAX_VALUE;
					for (DyeColor goal : RUBIX_ORDER) {
						int sum = clicks(goal, panes).values().stream().mapToInt(Math::abs).sum();
						if (sum < best) {
							best = sum;
							rubixGoal = goal;
						}
					}
				}
				if (rubixGoal == null) return;
				clicks(rubixGoal, panes).forEach((slot, n) -> {
					COLOR.put(slot, n == 1 ? 0xFFFF2E2E : n == 2 ? 0xFFB3121B : n == -1 ? 0xFF6E5A5A : 0xFF3A2E2E);
					LABEL.put(slot, String.valueOf(n));
				});
			}
			case MELODY -> {
				int magenta = -1, lime = -1, clay = -1;
				for (int i = 0; i < items.size(); i++) {
					ItemStack s = items.get(i);
					if (magenta < 0 && s.is(Items.MAGENTA_STAINED_GLASS_PANE)) magenta = i;
					if (s.is(Items.LIME_STAINED_GLASS_PANE)) lime = i;
					if (s.is(Items.LIME_TERRACOTTA)) clay = i;
				}
				if (magenta >= 0) {
					COLOR.put(magenta, 0xFFB3121B);
					if (magenta + 36 < items.size()) COLOR.put(magenta + 36, 0xFFB3121B);
				}
				if (lime >= 0) COLOR.put(lime, 0xFFFFB0B0);
				if (lime >= 0 && magenta >= 0 && lime % 9 == magenta % 9 && clay >= 0) {
					COLOR.put(clay, 0xFFFF2E2E);
					LABEL.put(clay, "!");
				}
			}
		}
	}

	/** Clicks to turn each pane to the goal colour: positive is left clicks (forward), negative right clicks. */
	private static Map<Integer, Integer> clicks(DyeColor goal, Map<Integer, DyeColor> panes) {
		Map<Integer, Integer> out = new HashMap<>();
		int g = RUBIX_ORDER.indexOf(goal), n = RUBIX_ORDER.size();
		panes.forEach((slot, color) -> {
			int c = RUBIX_ORDER.indexOf(color);
			if (c < 0) return;
			int forward = c > g ? g + n - c : g - c;
			int clicks = forward > 2 && !TerminalSolverConfig.get().rubixLeftOnly ? forward - n : forward;
			if (clicks != 0) out.put(slot, clicks);
		});
		return out;
	}

	private static Set<String> prefixes(String color) {
		String c = color.toLowerCase(Locale.ROOT).replace("silver", "light gray");
		return switch (c) {
			case "black" -> Set.of("black", "ink");
			case "blue" -> Set.of("blue", "lapis");
			case "brown" -> Set.of("brown", "cocoa");
			case "white" -> Set.of("white", "bone", "wool");
			case "green" -> Set.of("green", "cactus");
			case "red" -> Set.of("red", "rose");
			case "yellow" -> Set.of("yellow", "dandelion");
			case "light gray" -> Set.of("silver", "light gray");
			default -> Set.of(c);
		};
	}

	private static DyeColor pane(ItemStack s) {
		return s.getItem() instanceof BlockItem b && b.getBlock() instanceof StainedGlassPaneBlock p ? p.getColor() : null;
	}

	public static boolean active(AbstractContainerScreen<?> s) {
		return s == screen && type != null;
	}

	/** Before the slots draw: a dark panel over the terminal's grid. */
	public static void background(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		if (!active(s)) return;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (int i = 0; i < size && i < s.getMenu().slots.size(); i++) {
			Slot slot = s.getMenu().slots.get(i);
			minX = Math.min(minX, slot.x);
			minY = Math.min(minY, slot.y);
			maxX = Math.max(maxX, slot.x + 16);
			maxY = Math.max(maxY, slot.y + 16);
		}
		if (minX > maxX) return;
		g.fill(minX - 1, minY - 1, maxX + 1, maxY + 1, 0xF0120A0A);
	}

	/** One slot of the terminal; true when Taz drew it (the item itself stays hidden). */
	public static boolean slot(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
		if (!active(s) || slot.index >= size || slot.container == Minecraft.getInstance().player.getInventory()) return false;
		Integer color = COLOR.get(slot.index);
		if (color != null && (color >>> 24) != 0) {
			g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
			g.fill(slot.x, slot.y, slot.x + 16, slot.y + 1, 0x40FFFFFF);
		}
		String label = LABEL.get(slot.index);
		if (label != null) {
			Font font = Minecraft.getInstance().font;
			com.taz.hud.TazTheme.outlined(g, font, net.minecraft.network.chat.Component.literal(label), slot.x + 8 - font.width(label) / 2, slot.y + 4,
					0xFFFFFFFF);
		}
		return true;
	}

	/** You clicked a slot in the terminal. */
	public static void onClick(AbstractContainerScreen<?> s, int slotId) {
		if (!active(s)) return;
		if (type == Type.STARTS_WITH && COLOR.containsKey(slotId)) CLICKED.add(slotId);
		TerminalSolverConfig c = TerminalSolverConfig.get();
		if (c.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), c.pitch, 1f));
	}

	/** Hypixel's own click sound in a terminal, muted when Taz plays its own. */
	public static boolean muteSound(String id, float pitch) {
		return screen != null && TerminalSolverConfig.get().sounds && id.equals("block.note_block.pling") && Math.abs(pitch - 4.047619f) < 0.01f;
	}

	/** Test hooks. */
	public static Type type() {
		return type;
	}

	public static void testOpen(Type t, String argument) {
		close();
		type = t;
		arg = argument;
	}

	public static Map<Integer, Integer> colors() {
		return COLOR;
	}

	public static Map<Integer, String> labels() {
		return LABEL;
	}
}
