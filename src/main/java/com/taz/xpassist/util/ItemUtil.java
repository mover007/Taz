package com.taz.xpassist.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {
	private ItemUtil() {
	}

	/** Hypixel's item id (e.g. "DECENT_BOW"), stored in the item's custom data, or "" if none. */
	public static String skyblockId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().getStringOr("id", "") : "";
	}

	/** Pet items carry their type and rarity as a JSON string ("petInfo") in custom data. */
	public static com.google.gson.JsonObject petInfo(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String json = data.copyTag().getStringOr("petInfo", "");
		if (json.isEmpty()) {
			return null;
		}
		try {
			return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	public static String name(ItemStack stack) {
		return stack == null || stack.isEmpty() ? "" : TextUtil.strip(stack.getHoverName().getString()).trim();
	}

	/** Lore lines as plain text. */
	public static List<String> lore(ItemStack stack) {
		List<String> lines = new ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return lines;
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				lines.add(TextUtil.strip(line.getString()).trim());
			}
		}
		return lines;
	}
}
