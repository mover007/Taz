package com.taz.general;

import com.taz.util.Alert;
import com.taz.util.Items;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;

import java.util.regex.Pattern;

/**
 * Ragnarock: alerts when you cast the Ragnarock Axe (its wolf howl, at the pitch Hypixel plays it at, while you hold the
 * axe) or it's cancelled, and how much Strength the cast gave you (1.5x the axe's Strength). From Odin.
 */
public final class Ragnarock {
	private static final Pattern CANCELLED = Pattern.compile("^Ragnarock was cancelled due to (?:being hit|taking damage)!$");
	private static final float CAST_PITCH = 1.4920635f;
	private static long lastCastMs;

	private Ragnarock() {
	}

	public static void onChat(String line) {
		RagnarockConfig cfg = RagnarockConfig.get();
		if (cfg.enabled && cfg.cancelAlert && CANCELLED.matcher(line).matches()) Alert.show("§cRagnarock Cancelled!", null, Alert.Sound.ANVIL);
	}

	/** Every sound the server plays: the cast's wolf howl. */
	public static void onSound(String path, float pitch) {
		RagnarockConfig cfg = RagnarockConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || mc.player == null || Math.abs(pitch - CAST_PITCH) > 0.0005f) return;
		if (!path.contains("wolf") || !path.endsWith("death")) return;
		if (!Items.id(mc.player.getMainHandItem()).equals("RAGNAROCK_AXE")) return;
		long now = System.currentTimeMillis();
		if (now - lastCastMs < 1000) return;
		lastCastMs = now;
		if (cfg.castAlert) Alert.show("§aCasted Rag", null, Alert.Sound.PLING);
		int strength = (int) (Items.stat(mc.player.getMainHandItem(), "Strength") * 1.5);
		if (cfg.strength) {
			TazChat.info("Ragnarock gave you " + strength + " Strength.");
			if (cfg.announce) TazChat.party("Gained " + strength + " Strength from Ragnarock");
		}
	}
}
