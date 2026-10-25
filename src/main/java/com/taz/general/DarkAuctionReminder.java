package com.taz.general;

import com.taz.util.Alert;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** "Dark Auction in 3m 0s!" before every Dark Auction (General > Dark Auction Reminder). */
public final class DarkAuctionReminder {
	/** Start time (Unix seconds) of the auction already reminded about, before it and when it starts. */
	private static long remindedBefore;
	private static long remindedStart;

	private DarkAuctionReminder() {
	}

	/** Every client tick. */
	public static void tick() {
		DarkAuctionConfig cfg = DarkAuctionConfig.get();
		if (!cfg.enabled || cfg.onlySkyblock && !SkyblockLocation.onSkyblock() && EventTimers.testNowMs == 0) return;
		long nowS = EventTimers.now() / 1000;
		long start = SkyblockEvent.DARK_AUCTION.nextStart(nowS);
		if (nowS < start && start - nowS <= cfg.minutesBefore * 60L && remindedBefore != start) {
			remindedBefore = start;
			remind("Dark Auction in " + EventTimers.span(start - nowS) + "!");
		} else if (cfg.atStart && nowS >= start && nowS < start + 30 && remindedStart != start) {
			remindedStart = start;
			remindedBefore = start;
			remind("Dark Auction starting now!");
		}
	}

	/** The title, sound and chat line. */
	static void remind(String text) {
		DarkAuctionConfig cfg = DarkAuctionConfig.get();
		if (cfg.title) Alert.show("§5§lDARK AUCTION", "§d" + text, cfg.sound);
		else Alert.play(cfg.sound);
		MutableComponent line = Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE);
		if (cfg.warpButton) {
			line.append(Component.literal(" [Warp]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
					.withClickEvent(new ClickEvent.RunCommand("/taz warp da"))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("/warp da")))));
		}
		TazChat.send(line);
	}

	/** Test hook. */
	public static void clear() {
		remindedBefore = 0;
		remindedStart = 0;
	}
}
