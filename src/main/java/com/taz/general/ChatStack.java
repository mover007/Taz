package com.taz.general;

import com.taz.dungeons.util.TextUtil;
import com.taz.mixin.ChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stack Duplicates: a chat message that repeats one of the newest few takes its place, with a count ("Blobfish BRONZE!
 * (x3)"). The game log gets each repeat with its count, which Taz's chat log readers already allow for.
 */
public final class ChatStack {
	/** The count Taz adds, as the message's last part. */
	private static final Pattern COUNT = Pattern.compile("^ \\(x(\\d+)\\)$");
	/** Blank lines and separators ("▬▬▬▬") are left alone. */
	private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]");

	private ChatStack() {
	}

	/** ChatComponentMixin, as a message is added: the message to add instead, with the repeat it replaces taken out. */
	public static Component stack(ChatComponent chat, Component message) {
		ChatStackConfig cfg = ChatStackConfig.get();
		if (!cfg.enabled) return message;
		String text = TextUtil.cleanChat(message.getString());
		if (!WORDS.matcher(text).find()) return message;
		ChatComponentAccessor acc = (ChatComponentAccessor) chat;
		List<GuiMessage> all = acc.taz$messages();
		for (int i = 0; i < Math.min(cfg.range, all.size()); i++) {
			Component old = all.get(i).content();
			int count = count(old);
			String oldText = TextUtil.cleanChat(old.getString());
			if (count > 1) oldText = oldText.substring(0, oldText.length() - ("(x" + count + ")").length()).trim();
			if (!oldText.equals(text)) continue;
			all.remove(i);
			acc.taz$refresh();
			return message.copy().append(Component.literal(" (x" + (count + 1) + ")").withStyle(ChatFormatting.GRAY));
		}
		return message;
	}

	/** How many times a message in the chat has been said: the count Taz added to it, or 1. */
	private static int count(Component message) {
		List<Component> parts = message.getSiblings();
		if (parts.isEmpty()) return 1;
		Matcher m = COUNT.matcher(parts.getLast().getString());
		return m.matches() ? Integer.parseInt(m.group(1)) : 1;
	}
}
