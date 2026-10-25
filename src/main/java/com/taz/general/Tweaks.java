package com.taz.general;

import com.taz.util.Items;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** The small ones: Auto Sprint, No Cursor Reset, Camera and Arrow Fix (each turned on in its own dropdown). */
public final class Tweaks {
	private static final Map<String, Boolean> SHORTBOWS = new HashMap<>();
	private static long screenChangedMs;
	private static boolean hadScreen;

	private Tweaks() {
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.screen != null) {
			hadScreen = true;
			screenChangedMs = System.currentTimeMillis();
		} else if (hadScreen) {
			hadScreen = false;
			screenChangedMs = System.currentTimeMillis();
		}
		if (CameraConfig.get().enabled && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(CameraType.FIRST_PERSON);
	}

	public static boolean autoSprint() {
		return AutoSprintConfig.get().enabled;
	}

	/** A menu opened soon after the last one closed: keep the mouse where it was (MouseHandlerMixin). */
	public static boolean keepCursor() {
		NoCursorResetConfig cfg = NoCursorResetConfig.get();
		return cfg.enabled && Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
				&& System.currentTimeMillis() - screenChangedMs < cfg.timeoutMs;
	}

	/** A shortbow (its lore says "Shortbow: Instantly shoots!"): no pull-back (PlayerMixin). */
	public static boolean noPullback(ItemStack stack) {
		if (!ArrowFixConfig.get().enabled || stack == null || !stack.is(net.minecraft.world.item.Items.BOW)) return false;
		String id = Items.id(stack);
		if (id.isEmpty()) return Items.lore(stack).stream().anyMatch(l -> l.contains("Shortbow: Instantly shoots!"));
		return SHORTBOWS.computeIfAbsent(id, k -> Items.lore(stack).stream().anyMatch(l -> l.contains("Shortbow: Instantly shoots!")));
	}
}
