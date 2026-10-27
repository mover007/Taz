package com.taz.general;

import com.mojang.serialization.DynamicOps;
import com.taz.Taz;
import com.taz.hud.TazTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Storage Overlay: /storage shows every ender chest page and backpack at once, with what's in each (remembered from the
 * last time you opened it), so you can find an item without opening them one by one. From NoammAddons' Storage Overlay.
 */
public final class StorageOverlay {
	private static final Pattern ENDER = Pattern.compile("^Ender Chest (?:✦ )?\\(([1-9])/[1-9]\\)$");
	private static final Pattern BACKPACK = Pattern.compile("^.+Backpack (?:✦ )?\\(Slot #([0-9]+)\\)$");
	private static final int SLOT = 18, PAGE_W = 9 * SLOT + 6, HEADER = 12, GAP = 6;
	/** Page (0-8 ender chest, 9+ backpack) -> its items, or null when it exists but hasn't been seen yet. */
	private static final TreeMap<Integer, List<ItemStack>> PAGES = new TreeMap<>();
	private static String loadedFor;
	private static double scroll;
	private static int ticks;

	private StorageOverlay() {
	}

	public static String pageName(int page) {
		return page < 9 ? "Ender Chest #" + (page + 1) : "Backpack #" + (page - 8);
	}

	/** The Storage menu with the overlay on. */
	public static boolean active(AbstractContainerScreen<?> s) {
		return StorageOverlayConfig.get().enabled && s instanceof ContainerScreen && s.getTitle().getString().equals("Storage");
	}

	/** Every client tick: remember what's in the page or overview that's open. */
	public static void tick(Minecraft mc) {
		if (!StorageOverlayConfig.get().enabled || !(mc.screen instanceof ContainerScreen s) || !(s.getMenu() instanceof ChestMenu menu)) {
			if (mc.screen == null && !StorageOverlayConfig.get().keepScroll) scroll = 0;
			return;
		}
		load();
		if (++ticks % 10 != 0) return;
		String title = s.getTitle().getString();
		if (title.equals("Storage")) {
			boolean changed = false;
			for (int i = 0; i < menu.getRowCount() * 9; i++) {
				int page = i >= 9 && i < 18 ? i - 9 : i >= 27 && i < 45 ? i - 27 + 9 : -1;
				ItemStack stack = menu.getSlot(i).getItem();
				if (page < 0 || stack.isEmpty()) continue;
				boolean none = stack.is(Items.RED_STAINED_GLASS_PANE) || stack.is(Items.BROWN_STAINED_GLASS_PANE) || stack.is(Items.GRAY_DYE);
				if (none && PAGES.containsKey(page)) {
					PAGES.remove(page);
					changed = true;
				} else if (!none && !PAGES.containsKey(page)) {
					PAGES.put(page, null);
					changed = true;
				}
			}
			if (changed) save();
			return;
		}
		int page = page(title);
		if (page < 0 || menu.getRowCount() < 2) return;
		List<ItemStack> items = new ArrayList<>();
		for (int i = 9; i < menu.getRowCount() * 9; i++) items.add(menu.getSlot(i).getItem().copy());
		List<ItemStack> old = PAGES.get(page);
		if (old != null && same(old, items)) return;
		PAGES.put(page, items);
		save();
	}

	private static int page(String title) {
		Matcher m = ENDER.matcher(title);
		if (m.matches()) return Integer.parseInt(m.group(1)) - 1;
		m = BACKPACK.matcher(title);
		return m.matches() ? Integer.parseInt(m.group(1)) - 1 + 9 : -1;
	}

	private static boolean same(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
		return true;
	}

	// ---- saving (config/taz/storage/<your uuid>.nbt)

	private static Path file() {
		Minecraft mc = Minecraft.getInstance();
		return Taz.CONFIG_DIR.resolve("storage").resolve(mc.getUser().getProfileId() + ".nbt");
	}

	private static DynamicOps<Tag> ops() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? NbtOps.INSTANCE : mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private static void load() {
		Minecraft mc = Minecraft.getInstance();
		String who = String.valueOf(mc.getUser().getProfileId());
		if (who.equals(loadedFor) || mc.level == null) return;
		loadedFor = who;
		PAGES.clear();
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			for (String key : root.keySet()) {
				int page = Integer.parseInt(key);
				Tag t = root.get(key);
				if (!(t instanceof ListTag list)) {
					PAGES.put(page, null);
					continue;
				}
				List<ItemStack> items = new ArrayList<>();
				for (Tag item : list) items.add(ItemStack.OPTIONAL_CODEC.parse(ops(), item).result().orElse(ItemStack.EMPTY));
				PAGES.put(page, items);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read the storage overlay's pages", e);
		}
	}

	private static void save() {
		CompoundTag root = new CompoundTag();
		for (Map.Entry<Integer, List<ItemStack>> e : PAGES.entrySet()) {
			if (e.getValue() == null) {
				root.putBoolean(String.valueOf(e.getKey()), true);
				continue;
			}
			ListTag list = new ListTag();
			for (ItemStack s : e.getValue()) list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops(), s).result().orElse(new CompoundTag()));
			root.put(String.valueOf(e.getKey()), list);
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save the storage overlay's pages", e);
		}
	}

	// ---- drawing and clicks

	private record Box(int page, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static float scale(int screenW) {
		StorageOverlayConfig cfg = StorageOverlayConfig.get();
		int cols = Math.max(1, cfg.columns);
		return Math.min(cfg.scale, (screenW - 20f) / (cols * PAGE_W + (cols - 1) * GAP));
	}

	private static int pageHeight(List<ItemStack> items) {
		int rows = items == null ? 1 : Math.max(1, (items.size() + 8) / 9);
		return HEADER + rows * SLOT + 4;
	}

	/** Page boxes in overlay space (unscaled, before scrolling). */
	private static List<Box> layout() {
		int cols = Math.max(1, StorageOverlayConfig.get().columns);
		List<Box> out = new ArrayList<>();
		int col = 0, y = 0, rowH = 0;
		for (Map.Entry<Integer, List<ItemStack>> e : PAGES.entrySet()) {
			int h = pageHeight(e.getValue());
			out.add(new Box(e.getKey(), col * (PAGE_W + GAP), y, PAGE_W, h));
			rowH = Math.max(rowH, h);
			if (++col == cols) {
				col = 0;
				y += rowH + GAP;
				rowH = 0;
			}
		}
		return out;
	}

	private static int contentHeight(List<Box> boxes) {
		int h = 0;
		for (Box b : boxes) h = Math.max(h, b.y() + b.h());
		return h;
	}

	/** Top-left of the overlay and the visible height, on screen. */
	private static int[] frame(int screenW, int screenH, List<Box> boxes) {
		float s = scale(screenW);
		int cols = Math.max(1, Math.min(StorageOverlayConfig.get().columns, Math.max(1, boxes.size())));
		int w = Math.round((cols * PAGE_W + (cols - 1) * GAP) * s);
		int visible = Math.min(Math.round(contentHeight(boxes) * s), Math.min(Math.round(StorageOverlayConfig.get().maxHeight * s), screenH - 40));
		return new int[] {(screenW - w) / 2, Math.max(24, (screenH - visible) / 2), w, Math.max(visible, 20)};
	}

	/** Draws the overlay instead of the Storage menu (AbstractContainerScreenMixin); true when it did. */
	public static boolean render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!active(screen)) return false;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int sw = g.guiWidth(), sh = g.guiHeight();
		g.fill(0, 0, sw, sh, 0xC0080404);
		List<Box> boxes = layout();
		int[] f = frame(sw, sh, boxes);
		float s = scale(sw);
		Component title = Component.literal("Storage");
		TazTheme.outlined(g, font, title, sw / 2 - font.width(title) / 2, f[1] - 16, TazTheme.RED);
		if (boxes.isEmpty()) {
			String hint = "Open each ender chest page and backpack once so Taz can show them here";
			g.text(font, hint, sw / 2 - font.width(hint) / 2, sh / 2, TazTheme.GREY, true);
			return true;
		}
		double max = Math.max(0, contentHeight(boxes) * s - f[3]);
		scroll = Math.max(0, Math.min(scroll, max));
		g.enableScissor(f[0] - 2, f[1] - 2, f[0] + f[2] + 2, f[1] + f[3] + 2);
		g.pose().pushMatrix();
		g.pose().translate(f[0], (float) (f[1] - scroll));
		g.pose().scale(s, s);
		double lx = (mouseX - f[0]) / s, ly = (mouseY - f[1] + scroll) / s;
		boolean inside = mouseY >= f[1] && mouseY < f[1] + f[3];
		ItemStack hovered = null;
		for (Box b : boxes) {
			boolean hover = inside && b.contains(lx, ly);
			g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover ? 0xE0281010 : 0xE0140A0A);
			int border = hover ? TazTheme.RED : TazTheme.DARK_RED;
			g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 1, border);
			g.fill(b.x(), b.y() + b.h() - 1, b.x() + b.w(), b.y() + b.h(), border);
			g.fill(b.x(), b.y(), b.x() + 1, b.y() + b.h(), border);
			g.fill(b.x() + b.w() - 1, b.y(), b.x() + b.w(), b.y() + b.h(), border);
			g.text(font, pageName(b.page()), b.x() + 4, b.y() + 3, TazTheme.PALE_RED, true);
			List<ItemStack> items = PAGES.get(b.page());
			if (items == null) {
				g.text(font, "Open it once to see it here", b.x() + 4, b.y() + HEADER + 4, TazTheme.GREY, false);
				continue;
			}
			for (int i = 0; i < items.size(); i++) {
				int x = b.x() + 3 + (i % 9) * SLOT, y = b.y() + HEADER + (i / 9) * SLOT;
				g.fill(x, y, x + 16, y + 16, 0x40000000);
				ItemStack stack = items.get(i);
				if (stack.isEmpty()) continue;
				g.item(stack, x, y);
				g.itemDecorations(font, stack, x, y);
				if (inside && lx >= x && lx < x + 16 && ly >= y && ly < y + 16) hovered = stack;
			}
		}
		g.pose().popMatrix();
		g.disableScissor();
		if (hovered != null) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
		return true;
	}

	/** A click: open the page under the mouse. True when Taz handled it. */
	public static boolean onClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
		if (!active(screen)) return false;
		Minecraft mc = Minecraft.getInstance();
		List<Box> boxes = layout();
		int[] f = frame(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), boxes);
		float s = scale(mc.getWindow().getGuiScaledWidth());
		if (mouseY < f[1] || mouseY >= f[1] + f[3]) return true;
		double lx = (mouseX - f[0]) / s, ly = (mouseY - f[1] + scroll) / s;
		for (Box b : boxes) {
			if (!b.contains(lx, ly)) continue;
			int slot = b.page() < 9 ? 9 + b.page() : 27 + b.page() - 9;
			mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
			return true;
		}
		return true;
	}

	/** The mouse wheel scrolls the overlay. */
	public static boolean onScroll(AbstractContainerScreen<?> screen, double amount) {
		if (!active(screen)) return false;
		scroll -= amount * StorageOverlayConfig.get().scrollSpeed;
		return true;
	}

	/** Test hooks. */
	public static TreeMap<Integer, List<ItemStack>> pages() {
		return PAGES;
	}
}
