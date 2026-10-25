package com.taz.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything Taz says starts with [TAZ]: chat lines shown only to you, and what features say in party chat.
 * Also click-twice confirmation for settings buttons that delete things.
 */
public final class TazChat {
	public static final String PREFIX = "[TAZ] ";
	private static final long CONFIRM_MS = 5000;
	private static final Map<String, Long> ARMED = new HashMap<>();

	private TazChat() {
	}

	public static MutableComponent prefix() {
		return Component.literal(PREFIX).withStyle(ChatFormatting.AQUA);
	}

	public static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.getChat().addClientSystemMessage(prefix().append(message)));
	}

	public static void info(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	/** Says something in party chat ("[TAZ] ..."); returns what was said, or null when not connected. */
	public static String party(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return null;
		String said = PREFIX + message;
		mc.getConnection().sendCommand("pc " + said);
		return said;
	}

	/**
	 * True on the second click within 5 seconds; the first click says in chat what will happen.
	 * For buttons that delete data.
	 */
	public static boolean confirm(String key, String what) {
		long now = System.currentTimeMillis();
		Long armed = ARMED.remove(key);
		if (armed != null && now - armed <= CONFIRM_MS) return true;
		ARMED.put(key, now);
		send(Component.literal("Click again within 5 seconds to " + what + ".").withStyle(ChatFormatting.YELLOW));
		return false;
	}
}
