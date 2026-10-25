package com.taz.general;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.util.Items;
import com.taz.util.TazChat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Held Item: what the mixins ask, and copying NoammAddons' Animations values. */
public final class HeldItem {
	private HeldItem() {
	}

	public static HeldItemConfig on() {
		HeldItemConfig cfg = HeldItemConfig.get();
		return cfg.enabled ? cfg : null;
	}

	/** Whether the swing is drawn at its end (no swing). */
	public static boolean noSwing() {
		HeldItemConfig cfg = on();
		if (cfg == null || !cfg.noSwing) return false;
		Minecraft mc = Minecraft.getInstance();
		return !cfg.terminatorOnly || (mc.player != null && Items.id(mc.player.getMainHandItem()).equals("TERMINATOR"));
	}

	/** Your swing's length in ticks, or -1 to leave it be. */
	public static int swingTicks(int haste, int fatigue) {
		HeldItemConfig cfg = on();
		if (cfg == null) return -1;
		int length = cfg.ignoreHaste ? 6 : haste > 0 ? 6 - haste : fatigue > 0 ? 6 + fatigue * 2 : 6;
		return Math.max(1, (int) (length * Math.exp(-cfg.swingSpeed)));
	}

	public static void importFromNoamm() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("NoammAddons").resolve("config.json");
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("config")) {
				JsonObject f = e.getAsJsonObject();
				if (!"Animations".equals(f.get("name").getAsString())) continue;
				JsonObject s = f.getAsJsonObject("configSettings");
				HeldItemConfig c = HeldItemConfig.get();
				c.scale = s.get("Item Scale").getAsFloat();
				c.x = s.get("X").getAsFloat();
				c.y = s.get("Y").getAsFloat();
				c.z = s.get("Z").getAsFloat();
				c.rotX = s.get("Rotation X").getAsFloat();
				c.rotY = s.get("Rotation Y").getAsFloat();
				c.rotZ = s.get("Rotation Z").getAsFloat();
				c.swingX = s.get("swingX").getAsFloat();
				c.swingY = s.get("swingY").getAsFloat();
				c.swingZ = s.get("swingZ").getAsFloat();
				c.noSway = s.get("Disable hand movement").getAsBoolean();
				c.noEquip = s.get("Disable equip animation").getAsBoolean();
				c.noSwing = s.get("Disable swing animation").getAsBoolean();
				c.terminatorOnly = s.get("Terminator Only").getAsBoolean();
				c.swingSpeed = s.get("Swing Speed").getAsFloat();
				c.ignoreHaste = s.get("Ignore Haste").getAsBoolean();
				Taz.save();
				TazChat.info("Copied your held item settings from NoammAddons.");
				return;
			}
			TazChat.info("NoammAddons has no Animations settings.");
		} catch (Exception ex) {
			TazChat.info("Couldn't read NoammAddons' settings (" + file + ").");
		}
	}
}
