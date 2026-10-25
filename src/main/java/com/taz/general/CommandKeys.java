package com.taz.general;

import com.taz.util.KeyWatcher;
import net.minecraft.client.Minecraft;

/** Runs the command bound to a key (General > Command Keys). */
public final class CommandKeys {
	private static final KeyWatcher[] WATCHERS = new KeyWatcher[8];

	static {
		for (int i = 0; i < WATCHERS.length; i++) WATCHERS[i] = new KeyWatcher();
	}

	private CommandKeys() {
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		CommandKeysConfig cfg = CommandKeysConfig.get();
		CommandKeysConfig.Slot[] slots = cfg.slots();
		for (int i = 0; i < slots.length; i++) {
			boolean pressed = WATCHERS[i].pressed(mc, slots[i].key); // keep every watcher's state current
			if (pressed && cfg.enabled) run(mc, slots[i].command);
		}
	}

	/** "/warp hub" runs a command; anything else is said in chat. */
	public static void run(Minecraft mc, String command) {
		if (command == null || command.isBlank() || mc.getConnection() == null) return;
		String c = command.trim();
		if (c.startsWith("/")) mc.getConnection().sendCommand(c.substring(1));
		else mc.getConnection().sendChat(c);
	}
}
