package com.taz.general;

import com.taz.mixin.ChatComponentAccessor;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat: Ctrl + left click a chat message to copy it; type emoji codes (":skull:", "<3", "o/") and they're sent as the
 * symbols; Explosive Shot's damage split per enemy. From NoammAddons' Chat.
 */
public final class ChatTweaks {
	private static final Pattern EXPLOSIVE = Pattern.compile("^Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,.]+) damage\\.$");
	private static final Map<String, String> EMOJI = new LinkedHashMap<>();
	private static final Pattern CODES;

	static {
		String[][] pairs = {{"<3", "❤"}, {"h/", "ヽ(^◇^*)/"}, {"o/", "( ﾟ◡ﾟ)/"}, {":star:", "✮"}, {":yes:", "✔"}, {":no:", "✖"}, {":java:", "☕"},
				{":arrow:", "➜"}, {":shrug:", "¯\\_(ツ)_/¯"}, {":tableflip:", "(╯°□°）╯︵ ┻━┻"}, {":totem:", "☉_☉"}, {":typing:", "✎..."},
				{":maths:", "√(π+x)=L"}, {":snail:", "@'-'"}, {":thinking:", "(0.o?)"}, {":gimme:", "༼つ◕_◕༽つ"}, {":wizard:", "('-')⊃━☆ﾟ.*･｡ﾟ"},
				{":pvp:", "⚔"}, {":peace:", "✌"}, {":puffer:", "<('O')>"}, {":yey:", "ヽ (◕◡◕) ﾉ"}, {":cat:", "= ＾● ⋏ ●＾ ="}, {":dab:", "<o/"},
				{":dj:", "ヽ(⌐■_■)ノ♬"}, {":snow:", "☃"}, {":sloth:", "(・⊝・)"}, {":cute:", "(✿◠‿◠)"}, {":dog:", "(ᵔᴥᵔ)"}, {":sob:", ".ᐟ(つ╥﹏╥)つ"},
				{":joy:", "৻(≧ᗜ≦৻)"}, {":skull:", "☠"}};
		StringBuilder re = new StringBuilder();
		for (String[] p : pairs) {
			EMOJI.put(p[0], p[1]);
			if (!re.isEmpty()) re.append('|');
			re.append(Pattern.quote(p[0]));
		}
		CODES = Pattern.compile(re.toString());
	}

	private ChatTweaks() {
	}

	/** Emoji codes in a chat message you send, as the symbols. */
	public static String emoji(String message) {
		ChatTweaksConfig cfg = ChatTweaksConfig.get();
		if (!cfg.enabled || !cfg.emoji) return message;
		Matcher m = CODES.matcher(message);
		StringBuilder out = new StringBuilder();
		while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(EMOJI.get(m.group())));
		m.appendTail(out);
		return out.toString();
	}

	/** The same for a command you send (only chat commands: /pc, /ac, /gc, /cc, /msg, /w, /r). */
	public static String emojiCommand(String command) {
		String c = command.toLowerCase();
		for (String chat : List.of("pc ", "ac ", "gc ", "cc ", "msg ", "w ", "r ", "tell ")) if (c.startsWith(chat)) return emoji(command);
		return command;
	}

	public static void onChat(String line) {
		ChatTweaksConfig cfg = ChatTweaksConfig.get();
		if (!cfg.enabled || !cfg.explosiveShot) return;
		Matcher m = EXPLOSIVE.matcher(line);
		if (!m.matches()) return;
		int hits = Integer.parseInt(m.group(1));
		double total = Double.parseDouble(m.group(2).replace(",", ""));
		TazChat.info("Explosive Shot did " + String.format("%,d", Math.round(hits > 0 ? total / hits : total)) + " damage per enemy.");
	}

	/** The chat message under the mouse in the chat screen, or null. Follows ChatComponent's own layout. */
	public static String messageAt(double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		ChatComponent chat = mc.gui.getChat();
		ChatComponentAccessor acc = (ChatComponentAccessor) chat;
		List<GuiMessage.Line> lines = acc.taz$lines();
		double scale = mc.options.chatScale().get();
		int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		double x = mouseX / scale - 4;
		double y = (mc.getWindow().getGuiScaledHeight() - mouseY - 40.0) / (scale * lineHeight);
		if (x < -4 || x > Math.floor(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale)) return null;
		int shown = Math.min(chat.getLinesPerPage(), lines.size());
		if (y < 0 || y >= shown) return null;
		int index = (int) Math.floor(y + acc.taz$scroll());
		if (index < 0 || index >= lines.size()) return null;
		return net.minecraft.ChatFormatting.stripFormatting(lines.get(index).parent().content().getString());
	}

	/** Ctrl + left click in the chat screen: copy that message. True when it copied. */
	public static boolean onChatClick(double mouseX, double mouseY, int button) {
		ChatTweaksConfig cfg = ChatTweaksConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.enabled || !cfg.copy || button != 0 || !(mc.hasControlDown())) return false;
		String message = messageAt(mouseX, mouseY);
		if (message == null || message.isBlank()) return false;
		mc.keyboardHandler.setClipboard(message);
		TazChat.info("Copied: " + (message.length() > 60 ? message.substring(0, 60) + "..." : message));
		return true;
	}
}
