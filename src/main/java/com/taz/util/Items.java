package com.taz.util;

import com.taz.dungeons.util.TextUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SkyBlock item details: its id ("RAGNAROCK_AXE") and lore lines. */
public final class Items {
	private Items() {
	}

	/** The SkyBlock id from the item's custom data, or "" for a vanilla item. */
	public static String id(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().getStringOr("id", "") : "";
	}

	/** The lore lines, without formatting. */
	public static List<String> lore(ItemStack stack) {
		List<String> out = new ArrayList<>();
		if (stack == null || stack.isEmpty()) return out;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return out;
		for (Component line : lore.lines()) out.add(TextUtil.cleanChat(line.getString()));
		return out;
	}

	/** The number after a lore label ("Strength: +250" gives 250 for "Strength"), or 0. */
	public static double stat(ItemStack stack, String label) {
		Pattern p = Pattern.compile("^" + Pattern.quote(label) + ": \\+?([\\d,.]+)");
		for (String line : lore(stack)) {
			Matcher m = p.matcher(line);
			if (m.find()) return Double.parseDouble(m.group(1).replace(",", ""));
		}
		return 0;
	}
}
