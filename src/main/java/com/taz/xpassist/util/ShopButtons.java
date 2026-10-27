package com.taz.xpassist.util;

import com.taz.xpassist.xp.XpTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Clickable [Bazaar] / [AH] / [Recipe] buttons for chat. */
public final class ShopButtons {
	private ShopButtons() {
	}

	/** Appends the buttons for a task's purchases to a chat line. */
	public static MutableComponent append(MutableComponent line, List<XpTask.Buy> buys) {
		if (buys.isEmpty()) {
			return line;
		}
		boolean single = buys.size() == 1;
		int shown = 0;
		for (XpTask.Buy buy : buys) {
			if (shown++ >= 4) {
				break;
			}
			line.append(Component.literal(" ")).append(button(buy, single));
		}
		return line;
	}

	public static MutableComponent button(XpTask.Buy buy, boolean shortLabel) {
		String kind;
		String command;
		ChatFormatting color;
		switch (buy.kind()) {
			case "bz" -> {
				kind = "Bazaar";
				command = "/taz bz " + buy.query();
				color = ChatFormatting.GOLD;
			}
			case "ah" -> {
				kind = "AH";
				command = "/taz ah " + buy.query();
				color = ChatFormatting.YELLOW;
			}
			default -> {
				kind = "Recipe";
				command = "/taz recipe " + buy.query();
				color = ChatFormatting.LIGHT_PURPLE;
			}
		}
		String label = shortLabel ? "[" + kind + "]" : "[" + kind + ": " + buy.label() + "]";
		String hover = switch (buy.kind()) {
			case "bz" -> "Open the Bazaar for " + buy.label() + " (/bz, needs a Booster Cookie away from the Hub)";
			case "ah" -> "Search the Auction House for " + buy.label() + " (/ahs, needs a Booster Cookie away from the Hub)";
			default -> "Show the recipe for " + buy.label();
		};
		return Component.literal(label).withStyle(style -> style
			.withColor(color)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Sends a command to the server as if you typed it. */
	public static void sendServerCommand(String command) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() != null) {
			minecraft.getConnection().sendCommand(command);
		}
	}
}
