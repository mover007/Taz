package com.taz.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * A keybind set in the Taz settings GUI (a GLFW key code, -1 = none). Fires once per press,
 * only while no screen is open, the same way vanilla keybinds behave in game.
 */
public final class KeyWatcher {
	private boolean wasDown;

	/** Call every tick with the bound key; true on the tick it goes down. */
	public boolean pressed(Minecraft mc, int key) {
		boolean down = key != InputConstants.UNKNOWN.getValue() && key >= 0 && mc.screen == null
				&& InputConstants.isKeyDown(mc.getWindow(), key);
		boolean fired = down && !wasDown;
		wasDown = down;
		return fired;
	}
}
