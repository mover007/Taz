package com.taz.dungeons.info;

import com.taz.dungeons.util.DungeonLocation;
import com.taz.dungeons.util.DungeonParty;
import com.taz.hud.TazTheme;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leap Menu: the Spirit Leap menu as four big boxes, one per teammate (their face, name and class, dead ones in dark red),
 * placed by class the way Odin does; click a box or press 1-4. Can also say in party chat who you leaped to. From
 * NoammAddons' Leap Menu.
 */
public final class LeapMenu {
	private static final Pattern NAME = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16})");
	private static final Pattern TELEPORTED = Pattern.compile("^You have teleported to (\\w{1,16})!$");
	private static final Map<String, List<String>> ODIN_ORDER = Map.of(
			"Archer", List.of("Mage", "Berserk", "Healer", "Tank"),
			"Mage", List.of("Archer", "Berserk", "Healer", "Tank"),
			"Berserk", List.of("Archer", "Mage", "Healer", "Tank"),
			"Healer", List.of("Archer", "Berserk", "Mage", "Tank"),
			"Tank", List.of("Archer", "Berserk", "Healer", "Mage"));
	private static final List<String> CLASS_ORDER = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");

	public record Entry(String name, String dungeonClass, int slot, boolean dead) {
	}

	private LeapMenu() {
	}

	public static boolean active(AbstractContainerScreen<?> s) {
		LeapMenuConfig cfg = LeapMenuConfig.get();
		if (!cfg.enabled || !cfg.menu || !DungeonLocation.inDungeon()) return false;
		String title = s.getTitle().getString().toLowerCase(Locale.ROOT);
		return title.contains("spirit leap") || title.contains("teleport to player");
	}

	/** The four quadrants, top-left, top-right, bottom-left, bottom-right (null where nobody is). */
	public static Entry[] entries(AbstractContainerScreen<?> s) {
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player == null ? "" : mc.player.getName().getString();
		Map<String, Integer> heads = new HashMap<>();
		int container = s.getMenu().slots.size() - 36;
		for (int i = 0; i < container; i++) {
			ItemStack stack = s.getMenu().getSlot(i).getItem();
			if (!stack.is(Items.PLAYER_HEAD)) continue;
			Matcher m = NAME.matcher(net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()));
			if (m.find()) heads.put(m.group(1).toLowerCase(Locale.ROOT), i);
		}
		List<Entry> mates = new ArrayList<>();
		heads.forEach((name, slot) -> {
			if (name.equalsIgnoreCase(me)) return;
			String c = DungeonParty.classOf(name);
			String shown = s.getMenu().getSlot(slot).getItem().getHoverName().getString();
			Matcher m = NAME.matcher(net.minecraft.ChatFormatting.stripFormatting(shown));
			mates.add(new Entry(m.find() ? m.group(1) : name, c == null ? "?" : c, slot, DungeonParty.dead(name)));
		});
		Entry[] out = new Entry[4];
		switch (LeapMenuConfig.get().order) {
			case ODIN -> {
				List<String> wanted = ODIN_ORDER.get(DungeonParty.classOf(me));
				List<Entry> rest = new ArrayList<>(mates);
				if (wanted != null) {
					for (Entry e : mates) {
						int i = wanted.indexOf(e.dungeonClass());
						if (i >= 0 && out[i] == null) {
							out[i] = e;
							rest.remove(e);
						}
					}
				}
				for (int i = 0; i < 4 && !rest.isEmpty(); i++) if (out[i] == null) out[i] = rest.removeFirst();
				return out;
			}
			case CLASS -> mates.sort(Comparator.comparingInt((Entry e) -> CLASS_ORDER.indexOf(e.dungeonClass())).thenComparing(Entry::name));
			case NAME -> mates.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
			case NONE -> {
			}
		}
		for (int i = 0; i < 4 && i < mates.size(); i++) out[i] = mates.get(i);
		return out;
	}

	private static int quadrant(double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		int cx = mc.getWindow().getGuiScaledWidth() / 2, cy = mc.getWindow().getGuiScaledHeight() / 2;
		return (mouseX < cx ? 0 : 1) + (mouseY < cy ? 0 : 2);
	}

	/** Draws the menu instead of the chest (AbstractContainerScreenMixin); true when it did. */
	public static boolean render(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (!active(s)) return false;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		g.fill(0, 0, w, h, 0xB0000000);
		Entry[] entries = entries(s);
		boolean any = false;
		for (Entry e : entries) any |= e != null;
		if (!any) {
			Component none = Component.literal("No teammates found");
			TazTheme.outlined(g, font, none, w / 2 - font.width(none) / 2, h / 2 - 4, TazTheme.RED);
			return true;
		}
		float scale = LeapMenuConfig.get().scale / 50f;
		int boxW = Math.round(150 * scale), boxH = Math.round(48 * scale), gap = Math.round(16 * scale), face = Math.round(32 * scale);
		int hovered = quadrant(mouseX, mouseY);
		for (int i = 0; i < 4; i++) {
			Entry e = entries[i];
			if (e == null) continue;
			int x = i % 2 == 0 ? w / 2 - gap / 2 - boxW : w / 2 + gap / 2;
			int y = i < 2 ? h / 2 - gap / 2 - boxH : h / 2 + gap / 2;
			boolean hover = i == hovered;
			g.fill(x, y, x + boxW, y + boxH, e.dead() ? 0xE04A1010 : hover ? 0xE0301414 : 0xE0141010);
			int border = hover ? TazTheme.RED : TazTheme.DARK_RED;
			g.fill(x, y, x + boxW, y + 1, border);
			g.fill(x, y + boxH - 1, x + boxW, y + boxH, border);
			g.fill(x, y, x + 1, y + boxH, border);
			g.fill(x + boxW - 1, y, x + boxW, y + boxH, border);
			PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(e.name());
			int fx = x + Math.round(8 * scale), fy = y + (boxH - face) / 2;
			if (info != null) PlayerFaceExtractor.extractRenderState(g, info.getSkin(), fx, fy, face);
			int tx = fx + face + Math.round(6 * scale);
			g.text(font, e.name(), tx, y + boxH / 2 - 10, TazTheme.TEXT, true);
			g.text(font, e.dead() ? "DEAD" : e.dungeonClass(), tx, y + boxH / 2 + 2, e.dead() ? TazTheme.RED : TazTheme.PALE_RED, true);
			g.text(font, String.valueOf(i + 1), x + boxW - Math.round(10 * scale), y + Math.round(4 * scale), TazTheme.GREY, true);
		}
		return true;
	}

	/** A click in the menu (AbstractContainerScreenMixin); true when Taz handled it. */
	public static boolean onClick(AbstractContainerScreen<?> s, double mouseX, double mouseY, int button) {
		if (!active(s)) return false;
		if (button != 0 && LeapMenuConfig.get().leftClickOnly) return true;
		leap(s, quadrant(mouseX, mouseY));
		return true;
	}

	/** A key in the menu; true when it picked a teammate. */
	public static boolean onKey(AbstractContainerScreen<?> s, int key) {
		LeapMenuConfig cfg = LeapMenuConfig.get();
		if (!active(s) || !cfg.keys) return false;
		int[] keys = {cfg.key1, cfg.key2, cfg.key3, cfg.key4};
		for (int i = 0; i < 4; i++) {
			if (keys[i] == key && key != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
				leap(s, i);
				return true;
			}
		}
		return false;
	}

	private static void leap(AbstractContainerScreen<?> s, int i) {
		Entry e = entries(s)[i];
		if (e == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (e.dead()) {
			TazChat.info(e.name() + " is dead!");
			return;
		}
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		mc.gameMode.handleContainerInput(s.getMenu().containerId, e.slot(), 0, ContainerInput.PICKUP, mc.player);
	}

	public static void onChat(String line) {
		LeapMenuConfig cfg = LeapMenuConfig.get();
		if (!cfg.enabled || !cfg.announce) return;
		Matcher m = TELEPORTED.matcher(line);
		if (m.matches() && !cfg.message.isBlank()) TazChat.party(cfg.message.replace("{name}", m.group(1)));
	}
}
