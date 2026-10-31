package com.taz.general.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Items in Hypixel's profile data: each inventory is base64, gzipped 1.8 NBT ({i: [...]}). Each item becomes the JSON the
 * viewer draws (texture_path, display_name, lore, rarity, Count) and keeps its NBT, which SkyCofl prices as it is.
 */
final class ProfileItems {
	private static final Pattern RARITY = Pattern.compile("^(?:a )?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL|ULTIMATE|ADMIN)(?: (.*?))?(?: a)?$");
	private static final Pattern CODES = Pattern.compile("§.");
	private static Map<String, String> legacy;

	/** An item: the viewer's JSON and its NBT. */
	record Item(JsonObject json, CompoundTag nbt) {
		String id() {
			return Json.str(json, "id");
		}

		/** The type after the rarity on the last line ("DUNGEON SWORD", "COSMETIC"), or "". */
		String type() {
			return Json.str(json, "type");
		}

		boolean soulbound() {
			for (JsonElement line : Json.arr(json, "lore")) if (plain(line.getAsString()).contains("Soulbound")) return true;
			return nbt.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes").contains("donated_museum");
		}

		boolean cosmetic() {
			String id = id();
			return type().contains("COSMETIC") || type().contains("DYE") || id.startsWith("PET_SKIN_") || id.startsWith("DYE_");
		}
	}

	private ProfileItems() {
	}

	/** One inventory's slots, in order; an empty slot is null. */
	static List<Item> decode(JsonElement inventory) {
		String data = Json.str(inventory, "data");
		if (data.isEmpty()) return List.of();
		try {
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(data)), NbtAccounter.create(64L << 20));
			ListTag list = root.getListOrEmpty("i");
			List<Item> out = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				out.add(t.contains("id") && t.getShortOr("id", (short) 0) != 0 ? new Item(json(t), t) : null);
			}
			return out;
		} catch (Exception e) {
			Taz.LOGGER.warn("Player viewer: couldn't read an inventory ({})", e.toString());
			return List.of();
		}
	}

	/** The viewer's JSON for one item. */
	private static JsonObject json(CompoundTag t) {
		CompoundTag tag = t.getCompoundOrEmpty("tag"), display = tag.getCompoundOrEmpty("display"), extra = tag.getCompoundOrEmpty("ExtraAttributes");
		JsonObject o = new JsonObject();
		String id = extra.getStringOr("id", "");
		o.addProperty("id", id);
		o.addProperty("display_name", display.getStringOr("Name", ""));
		JsonArray lore = new JsonArray();
		ListTag lines = display.getListOrEmpty("Lore");
		for (int i = 0; i < lines.size(); i++) lore.add(lines.getStringOr(i, ""));
		o.add("lore", lore);
		o.addProperty("Count", t.getByteOr("Count", (byte) 1));
		for (int i = lore.size() - 1; i >= 0; i--) {
			Matcher m = RARITY.matcher(plain(lore.get(i).getAsString()).trim());
			if (m.matches()) {
				o.addProperty("rarity", m.group(1).toLowerCase(Locale.ROOT).replace(' ', '_'));
				o.addProperty("type", m.group(2) == null ? "" : m.group(2));
				break;
			}
		}
		o.addProperty("texture_path", texture(t, id));
		return o;
	}

	/** A texture link ViewerItems understands: a head texture, dyed leather, or the SkyBlock id with the Minecraft item under it. */
	private static String texture(CompoundTag t, String id) {
		CompoundTag tag = t.getCompoundOrEmpty("tag");
		String hash = headHash(tag);
		if (!hash.isEmpty()) return "/api/head/" + hash;
		int legacyId = t.getShortOr("id", (short) 0), damage = t.getShortOr("Damage", (short) 0);
		String mc = legacy(legacyId, damage);
		CompoundTag display = tag.getCompoundOrEmpty("display");
		if (legacyId >= 298 && legacyId <= 301 && display.contains("color")) {
			String part = switch (legacyId) {
				case 298 -> "helmet";
				case 299 -> "chestplate";
				case 300 -> "leggings";
				default -> "boots";
			};
			return "/api/leather/" + part + "/" + String.format("%06x", display.getIntOr("color", 0xA06540) & 0xFFFFFF);
		}
		boolean glint = !tag.getListOrEmpty("ench").isEmpty();
		return "/api/item/" + (id.isEmpty() ? (mc.isEmpty() ? "PAPER" : mc.toUpperCase(Locale.ROOT)) : id) + "?mc=" + mc + (glint ? "&glint=1" : "");
	}

	/** The texture hash of a player head ("SkullOwner" → Properties → textures), or "". */
	private static String headHash(CompoundTag tag) {
		ListTag textures = tag.getCompoundOrEmpty("SkullOwner").getCompoundOrEmpty("Properties").getListOrEmpty("textures");
		if (textures.isEmpty()) return "";
		return hashOf(textures.getCompoundOrEmpty(0).getStringOr("Value", ""));
	}

	/** The hash in a base64 textures value ({"textures":{"SKIN":{"url":".../texture/<hash>"}}}), or "". */
	static String hashOf(String value) {
		try {
			JsonElement json = JsonParser.parseString(new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8));
			String url = Json.str(json, "textures", "SKIN", "url");
			return url.contains("/texture/") ? url.substring(url.lastIndexOf('/') + 1) : "";
		} catch (RuntimeException e) {
			return "";
		}
	}

	/** The Minecraft item a 1.8 id and damage became ("397", 3 → "player_head"), or "". */
	static String legacy(int id, int damage) {
		if (legacy == null) {
			Map<String, String> map = new HashMap<>();
			try (InputStream in = ProfileItems.class.getResourceAsStream("/assets/taz/pv/legacy_items.txt")) {
				if (in != null) {
					BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
					for (String line; (line = r.readLine()) != null; ) {
						if (line.startsWith("#") || line.isBlank()) continue;
						String[] kv = line.trim().split(" ");
						if (kv.length == 2) map.put(kv[0], kv[1]);
					}
				}
			} catch (Exception e) {
				Taz.LOGGER.warn("Player viewer: couldn't read the 1.8 item table", e);
			}
			legacy = map;
		}
		String exact = legacy.get(id + ":" + damage);
		return exact != null ? exact : legacy.getOrDefault(String.valueOf(id), "");
	}

	static String plain(String text) {
		return CODES.matcher(text).replaceAll("");
	}
}
