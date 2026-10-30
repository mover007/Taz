package com.taz.general.pv;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icons for SkyCrypt's items. Its texture links name a head texture, dyed leather, a potion or an item id; an item id becomes
 * Hypixel's resource pack model when the pack is loaded, else the Minecraft item, else the item SkyCrypt says it's drawn from.
 */
final class ViewerItems {
	private static final Map<String, ItemStack> BY_LINK = new HashMap<>();
	/** Item ids SkyCrypt named a Minecraft item for ("HELIX_ARMOR_CHESTPLATE" → leather chestplate). */
	private static final Map<String, Item> RESOLVED = new ConcurrentHashMap<>();
	private static final Set<String> ASKED = ConcurrentHashMap.newKeySet();
	/** Hypixel's pack models by file name: "aspect_of_the_void" → hypixel_skyblock:item/slayer/enderman/aspect_of_the_void. */
	private static Map<String, Identifier> packModels;
	/** One item id is asked about at a time. */
	private static CompletableFuture<?> queue = CompletableFuture.completedFuture(null);

	private ViewerItems() {
	}

	/** When the viewer opens: Hypixel's pack may have loaded or gone since. */
	static void refresh() {
		packModels = null;
		BY_LINK.clear();
	}

	/** The icon for a texture link; empty while SkyCrypt is still being asked which item it is. */
	static ItemStack icon(String link) {
		return stack(link, false);
	}

	/** Like {@link #icon}, but something the player model can wear: never a pack model on paper. */
	static ItemStack wearable(String link) {
		return stack(link, true);
	}

	private static ItemStack stack(String link, boolean wear) {
		if (link == null || link.isEmpty()) return ItemStack.EMPTY;
		String key = (wear ? "wear:" : "") + link;
		ItemStack known = BY_LINK.get(key);
		if (known != null) return known;
		ItemStack made = make(link, wear);
		if (made == null) return ItemStack.EMPTY;
		BY_LINK.put(key, made);
		return made;
	}

	/** Null while waiting for SkyCrypt. */
	private static ItemStack make(String link, boolean wear) {
		String path = link.contains("?") ? link.substring(0, link.indexOf('?')) : link;
		String[] parts = path.split("/");
		String last = parts[parts.length - 1];
		if (path.contains("/api/head/")) return head(last);
		if (path.contains("/api/leather/") && parts.length >= 2) return leather(parts[parts.length - 2], last);
		if (path.contains("/api/potion/") && parts.length >= 2) return potion(parts[parts.length - 2], last);
		if (path.contains("/api/item/")) {
			String id = path.substring(path.indexOf("/api/item/") + 10);
			if (id.contains("/")) id = id.substring(0, id.indexOf('/'));
			return byId(id, wear, true);
		}
		if (path.contains("/cache/rendered/")) {
			// "skyblock=DAVIDS_CLOAK__pack=HYPIXEL_PLUS__mc=minecraft_player_head__model=..."
			String skyblock = param(last, "skyblock"), mc = param(last, "mc");
			ItemStack s = skyblock.isEmpty() ? null : byId(skyblock, wear, false);
			if (s != null) return s;
			Item item = vanilla(mc.replaceFirst("^minecraft_", ""));
			return item != null ? new ItemStack(item) : new ItemStack(Items.PAPER);
		}
		return new ItemStack(Items.PAPER);
	}

	private static ItemStack byId(String id, boolean wear, boolean ask) {
		if (!wear) {
			ItemStack pack = pack(id);
			if (pack != null) return pack;
		}
		Item item = vanilla(id);
		if (item == null) item = RESOLVED.get(id);
		if (item != null) return new ItemStack(item);
		if (!ask) return null;
		if (ASKED.add(id)) {
			queue = queue.thenCompose(v -> SkyCrypt.resolve(id)).handle((texture, error) -> {
				Item found = texture == null ? null : vanilla(param(texture.substring(texture.lastIndexOf('/') + 1), "mc").replaceFirst("^minecraft_", ""));
				RESOLVED.put(id, found != null ? found : Items.PAPER);
				return null;
			});
		}
		return null;
	}

	/** Hypixel's model for this item id, when its pack is loaded. */
	private static ItemStack pack(String id) {
		Minecraft mc = Minecraft.getInstance();
		if (packModels == null) {
			packModels = new HashMap<>();
			for (Identifier file : mc.getResourceManager().listResources("items",
					f -> f.getNamespace().equals("hypixel_skyblock") && f.getPath().endsWith(".json")).keySet()) {
				String model = file.getPath().substring("items/".length(), file.getPath().length() - ".json".length());
				packModels.putIfAbsent(model.substring(model.lastIndexOf('/') + 1), Identifier.fromNamespaceAndPath(file.getNamespace(), model));
			}
		}
		Identifier model = packModels.get(id.toLowerCase(Locale.ROOT));
		if (model == null || mc.getModelManager().getItemModel(model) instanceof MissingItemModel) return null;
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_MODEL, model);
		return s;
	}

	/** The Minecraft item with this name ("IRON_SWORD", "ink_sack:3"), or null. */
	private static Item vanilla(String name) {
		if (name == null || name.isEmpty()) return null;
		String plain = name.toLowerCase(Locale.ROOT).replaceAll(":\\d+$", "");
		Identifier id = Identifier.tryParse("minecraft:" + plain);
		if (id == null) return null;
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		return item.isPresent() && item.get() != Items.AIR ? item.get() : null;
	}

	/** A value from a rendered texture's file name: "a=1__b=2.webp". */
	private static String param(String file, String name) {
		for (String part : file.replaceAll("\\.[a-z]+$", "").split("__")) {
			if (part.startsWith(name + "=")) return part.substring(name.length() + 1);
		}
		return "";
	}

	/** A player head with this texture hash. */
	static ItemStack head(String hash) {
		String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
		String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)), "TazHead",
				new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value))));
		ItemStack s = new ItemStack(Items.PLAYER_HEAD);
		s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		return s;
	}

	/** A player's own head, by UUID. */
	static ItemStack head(UUID player) {
		ItemStack s = new ItemStack(Items.PLAYER_HEAD);
		s.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(player));
		return s;
	}

	private static ItemStack leather(String type, String color) {
		Item item = switch (type) {
			case "helmet" -> Items.LEATHER_HELMET;
			case "leggings" -> Items.LEATHER_LEGGINGS;
			case "boots" -> Items.LEATHER_BOOTS;
			default -> Items.LEATHER_CHESTPLATE;
		};
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb(color)));
		return s;
	}

	private static ItemStack potion(String type, String color) {
		Item item = type.equals("splash") ? Items.SPLASH_POTION : type.equals("lingering") ? Items.LINGERING_POTION : Items.POTION;
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(rgb(color)), List.of(), Optional.empty()));
		return s;
	}

	private static int rgb(String hex) {
		try {
			return Integer.parseInt(hex.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return 0xA06540;
		}
	}

	/** Hypixel's colour for a rarity ("legendary", "VERY_SPECIAL"). */
	static int rarity(String rarity) {
		return switch (rarity.toLowerCase(Locale.ROOT).replace(' ', '_')) {
			case "uncommon" -> 0x55FF55;
			case "rare" -> 0x5555FF;
			case "epic" -> 0xAA00AA;
			case "legendary" -> 0xFFAA00;
			case "mythic" -> 0xFF55FF;
			case "divine" -> 0x55FFFF;
			case "special", "very_special" -> 0xFF5555;
			case "supreme", "admin" -> 0xAA0000;
			default -> 0xFFFFFF;
		};
	}

	/** Whether this item slot has an item (SkyCrypt sends empty slots as {}). */
	static boolean present(JsonElement item) {
		return !Json.str(item, "texture_path").isEmpty() || !Json.str(item, "display_name").isEmpty();
	}

	/** An item's tooltip: its name and lore, with Hypixel's colours. */
	static List<Component> tooltip(JsonElement item) {
		List<Component> out = new ArrayList<>();
		String name = Json.str(item, "display_name");
		int count = (int) Json.num(item, "Count");
		out.add(Component.literal((count > 1 ? "§7" + count + "x " : "") + (name.startsWith("§") ? "" : "§f") + name));
		for (JsonElement line : Json.arr(item, "lore")) out.add(Component.literal(line.isJsonPrimitive() ? line.getAsString() : ""));
		return out;
	}
}
