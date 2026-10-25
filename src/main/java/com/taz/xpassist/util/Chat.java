package com.taz.xpassist.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class Chat {
	private Chat() {
	}

	public static MutableComponent prefix() {
		return com.taz.util.TazChat.prefix();
	}

	public static void send(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.getChat().addClientSystemMessage(prefix().append(message));
	}

	public static void info(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	public static void error(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.RED));
	}

	/** A clickable "[label]" that runs a command. */
	public static MutableComponent button(String label, String command, String hover) {
		return Component.literal("[" + label + "]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
