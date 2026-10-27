package com.taz.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.util.TazChat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keys in menus. Both from Odin. */
public final class InventoryKeys {
	private static final Pattern LOADOUT = Pattern.compile("\\((\\d)/(\\d)\\) Loadout");
	private static final int[] LOADOUT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
	private static Integer first;

	private InventoryKeys() {
	}

	/** A key (or mouse button, as MoulConfig numbers them: -100 + button) pressed in a menu; true when Taz used it. */
	public static boolean onKey(AbstractContainerScreen<?> screen, Slot hovered, int key) {
		return slotBindKey(screen, hovered, key) || loadoutKey(screen, key);
	}

	/** A mouse click in a menu; true when Taz used it (a shift-click swap). */
	public static boolean onClick(AbstractContainerScreen<?> screen, Slot hovered, int button) {
		if (loadoutKey(screen, -100 + button)) return true;
		SlotBindsConfig cfg = SlotBindsConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || button != 0 || !mc.hasShiftDown() || !(screen instanceof InventoryScreen) || hovered == null) return false;
		int slot = hovered.index;
		if (slot < 5 || slot >= 45) return false;
		Integer bound = cfg.binds.get(String.valueOf(slot));
		if (bound == null) {
			for (Map.Entry<String, Integer> e : cfg.binds.entrySet()) if (e.getValue() == slot) bound = Integer.parseInt(e.getKey());
		}
		if (bound == null) return false;
		int from, hotbar;
		if (slot >= 36) {
			from = bound;
			hotbar = slot;
		} else if (bound >= 36) {
			from = slot;
			hotbar = bound;
		} else return false;
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, from, hotbar % 36, ContainerInput.SWAP, mc.player);
		return true;
	}

	private static boolean slotBindKey(AbstractContainerScreen<?> screen, Slot hovered, int key) {
		SlotBindsConfig cfg = SlotBindsConfig.get();
		if (!cfg.enabled || key != cfg.bindKey || !(screen instanceof InventoryScreen) || hovered == null) return false;
		int slot = hovered.index;
		if (slot < 5 || slot >= 45) return false;
		if (first == null) {
			if (cfg.binds.remove(String.valueOf(slot)) != null) {
				Taz.save();
				TazChat.info("Removed the bind on slot " + slot + ".");
				return true;
			}
			first = slot;
			TazChat.info("Slot " + slot + " picked; press the key on the slot to bind it to.");
			return true;
		}
		int a = first;
		first = null;
		if (a == slot) {
			TazChat.info("You can't bind a slot to itself.");
		} else if (a < 36 && slot < 36) {
			TazChat.info("One of the slots must be in the hotbar.");
		} else {
			cfg.binds.put(String.valueOf(a), slot);
			Taz.save();
			TazChat.info("Bound slot " + a + " to slot " + slot + ". Shift + click either to swap them.");
		}
		return true;
	}

	private static boolean loadoutKey(AbstractContainerScreen<?> screen, int key) {
		LoadoutKeybindsConfig cfg = LoadoutKeybindsConfig.get();
		if (!cfg.enabled || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return false;
		Matcher m = LOADOUT.matcher(screen.getTitle().getString());
		if (!m.find()) return false;
		int page = Integer.parseInt(m.group(1)), pages = Integer.parseInt(m.group(2));
		int slot = -1;
		if (key == cfg.nextPage) slot = page < pages ? 44 : -1;
		else if (key == cfg.previousPage) slot = page > 1 ? 17 : -1;
		else {
			int[] keys = cfg.keys();
			for (int i = 0; i < keys.length; i++) if (keys[i] == key) slot = LOADOUT_SLOTS[i];
		}
		if (slot < 0) return false;
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
		return true;
	}

	/** After a menu draws: the slot the hovered one is bound to gets a red frame. */
	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot hovered, int left, int top) {
		SlotBindsConfig cfg = SlotBindsConfig.get();
		if (!cfg.enabled || !(screen instanceof InventoryScreen) || hovered == null) return;
		int slot = first != null ? first : hovered.index;
		Integer other = cfg.binds.get(String.valueOf(slot));
		if (other == null) for (Map.Entry<String, Integer> e : cfg.binds.entrySet()) if (e.getValue() == slot) other = Integer.parseInt(e.getKey());
		frame(g, screen.getMenu().getSlot(slot), left, top, first != null ? 0xFFFFFFFF : 0xFFFF2E2E);
		if (other != null && other < screen.getMenu().slots.size()) frame(g, screen.getMenu().getSlot(other), left, top, 0xFFFF2E2E);
	}

	private static void frame(GuiGraphicsExtractor g, Slot s, int left, int top, int color) {
		int x = left + s.x - 1, y = top + s.y - 1;
		g.fill(x, y, x + 18, y + 1, color);
		g.fill(x, y + 17, x + 18, y + 18, color);
		g.fill(x, y, x + 1, y + 18, color);
		g.fill(x + 17, y, x + 18, y + 18, color);
	}

	public static void onScreenClosed() {
		first = null;
	}

	/** Copies your Odin slot binds (its current profile). */
	public static void importFromOdin() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("odin").resolve("odin-config.json");
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			for (JsonElement m : root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("modules")) {
				JsonObject mod = m.getAsJsonObject();
				if (!"Slot Binds".equals(mod.get("name").getAsString())) continue;
				JsonObject settings = mod.getAsJsonObject("settings");
				String profile = settings.has("Profile") ? settings.get("Profile").getAsString() : "Profile 1";
				JsonObject data = settings.getAsJsonObject("ProfileData").getAsJsonObject(profile);
				int n = 0;
				for (Map.Entry<String, JsonElement> e : data.entrySet()) {
					SlotBindsConfig.get().binds.put(e.getKey(), e.getValue().getAsInt());
					n++;
				}
				Taz.save();
				TazChat.info("Copied " + n + " slot binds from Odin (" + profile + ").");
				return;
			}
			TazChat.info("Odin has no slot binds.");
		} catch (Exception e) {
			TazChat.info("Couldn't read Odin's settings (" + file + ").");
		}
	}
}
