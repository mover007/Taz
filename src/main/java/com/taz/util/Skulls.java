package com.taz.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Player-head textures: the hash at the end of the skin URL ("textures.minecraft.net/texture/5662b6..."). */
public final class Skulls {
	private Skulls() {
	}

	/** The head's texture hash, or "" for anything that isn't a textured head. */
	public static String texture(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) return "";
		for (Property p : profile.partialProfile().properties().get("textures")) {
			try {
				String json = new String(Base64.getDecoder().decode(p.value()), StandardCharsets.UTF_8);
				JsonObject skin = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
				String url = skin.get("url").getAsString();
				return url.substring(url.lastIndexOf('/') + 1);
			} catch (Exception ignored) {
				// not a texture we can read
			}
		}
		return "";
	}
}
