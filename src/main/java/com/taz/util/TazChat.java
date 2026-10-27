package com.taz.util;

import com.taz.Taz;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything Taz says starts with [TAZ]: chat lines shown only to you, and what features say in party chat.
 * Also click-twice confirmation for settings buttons that delete things.
 *
 * Party messages go out one at a time. Hypixel quietly drops a message sent in the same moment as another (a mask
 * proc message from another mod, say), so each waits until nothing has been sent for a moment, and one Hypixel
 * doesn't show back in party chat within a couple of seconds is sent once more.
 */
public final class TazChat {
	public static final String PREFIX = "[TAZ] ";
	/** Test hook: the last lines said with {@link #info}. */
	public static final java.util.Deque<String> RECENT = new java.util.concurrent.ConcurrentLinkedDeque<>();
	private static final long CONFIRM_MS = 5000;
	/** Nothing else may have been sent this recently (ms) when a party message goes out. */
	private static final long GAP_MS = 700;
	/** A party message Hypixel hasn't shown back after this long (ms) is sent again, once. */
	private static final long ECHO_MS = 2000;
	private static final Map<String, Long> ARMED = new HashMap<>();
	private static final ArrayDeque<String> OUTBOX = new ArrayDeque<>();
	/** The party message sent last, until Hypixel shows it back. */
	private static String awaiting;
	private static long awaitingSentMs;
	private static boolean resent;
	/** When anything (any mod, or you) last sent a chat message or command. */
	private static long lastOutgoingMs;
	private static int sent;

	private TazChat() {
	}

	/** "[TAZ] " in Taz's red and black. */
	public static MutableComponent prefix() {
		return com.taz.hud.TazTheme.chatPrefix();
	}

	public static void send(Component message) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.getChat().addClientSystemMessage(prefix().append(message)));
	}

	public static void info(String message) {
		RECENT.addLast(message);
		while (RECENT.size() > 50) RECENT.pollFirst();
		send(Component.literal(message).withStyle(s -> s.withColor(0xD8C8C8)));
	}

	/** Says something in party chat ("[TAZ] ..."), straight away when nothing else was just sent; returns what it says. */
	public static String party(String message) {
		String said = PREFIX + message;
		OUTBOX.add(said);
		flush();
		return said;
	}

	/** Any chat message or command leaving the client (ClientPacketListenerMixin). */
	public static void onOutgoing() {
		lastOutgoingMs = System.currentTimeMillis();
	}

	/** Every client tick: send what's waiting, and resend a party message Hypixel didn't show. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (awaiting != null && now - awaitingSentMs > ECHO_MS) {
			if (resent) {
				Taz.LOGGER.warn("Hypixel didn't show Taz's party message: {}", awaiting);
				awaiting = null;
			} else if (now - lastOutgoingMs >= GAP_MS && sendNow(awaiting)) {
				resent = true;
				awaitingSentMs = now;
			}
		}
		flush();
	}

	/** Every system chat line: Hypixel showing our party message back, or saying there's no party. */
	public static void onChat(String line) {
		if (awaiting == null) return;
		Minecraft mc = Minecraft.getInstance();
		String me = mc.player == null ? "" : mc.player.getName().getString();
		if (line.startsWith("Party >") && line.contains(me + ": " + PREFIX)) {
			awaiting = null;
			flush();
		} else if (line.startsWith("You are not in a party")) {
			awaiting = null;
			OUTBOX.clear();
		}
	}

	private static void flush() {
		if (awaiting != null || OUTBOX.isEmpty() || System.currentTimeMillis() - lastOutgoingMs < GAP_MS) return;
		String next = OUTBOX.peek();
		if (!sendNow(next)) return;
		OUTBOX.poll();
		awaiting = next;
		awaitingSentMs = System.currentTimeMillis();
		resent = false;
	}

	private static boolean sendNow(String said) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return false;
		mc.getConnection().sendCommand("pc " + said);
		lastOutgoingMs = System.currentTimeMillis();
		sent++;
		return true;
	}

	/** Test hooks: party messages still waiting to go out, and the one waiting to be shown back. */
	public static int waiting() {
		return OUTBOX.size() + (awaiting == null ? 0 : 1);
	}

	/** Test hook: party messages sent so far (resends too). */
	public static int sent() {
		return sent;
	}

	/** Tests: forget what's waiting. */
	public static void clearParty() {
		OUTBOX.clear();
		awaiting = null;
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
