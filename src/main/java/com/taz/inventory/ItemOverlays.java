package com.taz.inventory;

import com.taz.xpassist.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rarity backgrounds and small numbers on items in every inventory and menu (Inventory > Item Overlays). */
public final class ItemOverlays {
	public enum Rarity {
		COMMON(0xFFFFFF), UNCOMMON(0x55FF55), RARE(0x5555FF), EPIC(0xAA00AA), LEGENDARY(0xFFAA00), MYTHIC(0xFF55FF),
		DIVINE(0x55FFFF), SPECIAL(0xFF5555), VERY_SPECIAL(0xFF5555), ULTIMATE(0xAA0000), ADMIN(0xAA0000);

		public final int rgb;

		Rarity(int rgb) {
			this.rgb = rgb;
		}
	}

	/** "LEGENDARY SWORD", "a MYTHIC ACCESSORY a" (recombobulated), "VERY SPECIAL". */
	private static final Pattern RARITY = Pattern.compile("^(?:a )?(VERY SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)\\b");
	private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d+)]");
	private static final Pattern MINION = Pattern.compile("_GENERATOR_(\\d+)$");
	private static final Pattern CAKE = Pattern.compile("^New Year Cake \\(Year (\\d+)\\)");
	private static final Pattern ROMAN_END = Pattern.compile(" ([IVXLC]+)$");

	/** What to draw for one item, worked out once per stack. */
	public record Overlay(Rarity rarity, String number) {
	}

	private static final Map<ItemStack, Overlay> CACHE = new WeakHashMap<>();

	private ItemOverlays() {
	}

	public static Overlay of(ItemStack stack) {
		return CACHE.computeIfAbsent(stack, ItemOverlays::compute);
	}

	static Overlay compute(ItemStack stack) {
		return new Overlay(rarity(ItemUtil.lore(stack)), number(stack));
	}

	/** From the rarity line at the bottom of the lore. */
	static Rarity rarity(List<String> lore) {
		for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 3; i--) {
			Matcher m = RARITY.matcher(lore.get(i));
			if (m.find()) return Rarity.valueOf(m.group(1).replace(' ', '_'));
		}
		return null;
	}

	static String number(ItemStack stack) {
		String name = ItemUtil.name(stack);
		String id = ItemUtil.skyblockId(stack);
		Matcher m;
		if ((m = PET.matcher(name)).find()) return m.group(1);
		if ((m = CAKE.matcher(name)).find()) return m.group(1);
		if ((m = MINION.matcher(id)).find()) return m.group(1);
		if (id.equals("ENCHANTED_BOOK")) {
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			CompoundTag enchants = data == null ? null : data.copyTag().getCompoundOrEmpty("enchantments");
			if (enchants != null && enchants.size() == 1) return String.valueOf(enchants.getIntOr(enchants.keySet().iterator().next(), 0));
			return null;
		}
		// Skill and collection levels in menus: items with no SkyBlock id named like "Farming XXV".
		if (id.isEmpty() && (m = ROMAN_END.matcher(name)).find()) {
			int level = roman(m.group(1));
			return level > 0 && level <= 60 ? String.valueOf(level) : null;
		}
		return null;
	}

	static int roman(String s) {
		int total = 0, prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> 0;
			};
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}

	/** Before the item is drawn (AbstractContainerScreenMixin). */
	public static void background(GuiGraphicsExtractor g, Slot slot) {
		ItemOverlayConfig cfg = ItemOverlayConfig.get();
		if (!cfg.enabled || !cfg.rarity || !slot.hasItem()) return;
		Rarity r = of(slot.getItem()).rarity();
		if (r == null || r == Rarity.COMMON && cfg.skipCommon) return;
		int alpha = Math.round(cfg.opacity / 100f * 255) << 24;
		g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, alpha | r.rgb);
	}

	/** After the item is drawn (AbstractContainerScreenMixin). */
	public static void foreground(GuiGraphicsExtractor g, Slot slot) {
		ItemOverlayConfig cfg = ItemOverlayConfig.get();
		if (!cfg.enabled || !cfg.numbers || !slot.hasItem()) return;
		String n = of(slot.getItem()).number();
		if (n == null) return;
		g.pose().pushMatrix();
		g.pose().translate(slot.x, slot.y);
		g.pose().scale(0.75f, 0.75f);
		g.text(Minecraft.getInstance().font, n, 0, 0, com.taz.hud.TazTheme.PALE_RED, true);
		g.pose().popMatrix();
	}
}
