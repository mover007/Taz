package com.taz.general;

import com.taz.util.Alert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns chat lines and the action bar into big alerts (General > Alerts). */
public final class Alerts {
	/** "CRAZY RARE DROP! Judgement Core (+312% ✯ Magic Find)". Group 1 = the kind, group 2 = the item. */
	private static final Pattern DROP = Pattern.compile(
			"^(RARE DROP!|VERY RARE DROP!|CRAZY RARE DROP!|INSANE DROP!|PRAY TO RNGESUS DROP!|PET DROP!) ?(.*?)(?: \\(\\+[\\d,]+% ✯ Magic Find\\))?$");
	/** "[Bazaar] Your Buy Order for 64x Enchanted Iron was filled!" */
	private static final Pattern BAZAAR = Pattern.compile("^\\[Bazaar] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+) was filled!");
	/** "[Auction] Steve bought Hyperion for 900,000,000 coins CLICK" */
	private static final Pattern AUCTION = Pattern.compile("^\\[Auction] (.+?) bought (.+) for ([\\d,]+) coins");
	/** "[MVP+] Steve has invited you to join their party!" */
	private static final Pattern INVITE = Pattern.compile("^(?:\\[[A-Z+]+] )?(\\w+) has invited you to join (?:their|[\\w']+) party!");
	/** "1,234/5,000❤" on the action bar. */
	private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");

	private static boolean lowHealthShown;

	private Alerts() {
	}

	/** A plain-text chat line. */
	public static void onChat(String line) {
		AlertsConfig cfg = AlertsConfig.get();
		if (!cfg.enabled) return;
		Matcher m;
		if (cfg.rareDrops && (m = DROP.matcher(line)).find() && tier(m.group(1)) >= cfg.dropTier.ordinal()) {
			String kind = m.group(1);
			String color = tier(kind) >= 2 ? "§d§l" : tier(kind) == 1 ? "§5§l" : "§6§l";
			Alert.show(color + kind, "§f" + m.group(2), cfg.sound);
		} else if (cfg.bazaar && (m = BAZAAR.matcher(line)).find()) {
			Alert.show("§6§l" + m.group(1).toUpperCase() + " FILLED", "§f" + m.group(2) + "x " + m.group(3), cfg.sound);
		} else if (cfg.auctions && (m = AUCTION.matcher(line)).find()) {
			Alert.show("§6§lAUCTION SOLD", "§f" + m.group(2) + " §7for §6" + m.group(3) + " coins", cfg.sound);
		} else if (cfg.partyInvites && (m = INVITE.matcher(line)).find()) {
			Alert.show("§9§lPARTY INVITE", "§ffrom " + m.group(1), cfg.sound);
		}
	}

	/** 0 = rare, 1 = very rare, 2 = crazy rare and up. */
	static int tier(String kind) {
		return switch (kind) {
			case "VERY RARE DROP!", "PET DROP!" -> 1;
			case "CRAZY RARE DROP!", "INSANE DROP!", "PRAY TO RNGESUS DROP!" -> 2;
			default -> 0;
		};
	}

	/** A plain-text action bar message. */
	public static void onActionBar(String text) {
		AlertsConfig cfg = AlertsConfig.get();
		if (!cfg.enabled || !cfg.lowHealth) return;
		Matcher m = HEALTH.matcher(text);
		if (!m.find()) return;
		double hp = Double.parseDouble(m.group(1).replace(",", ""));
		double max = Double.parseDouble(m.group(2).replace(",", ""));
		if (max <= 0) return;
		double percent = hp * 100 / max;
		if (percent < cfg.lowHealthPercent && !lowHealthShown) {
			lowHealthShown = true;
			Alert.show("§c§lLOW HEALTH", "§c" + m.group(1) + "§7/§c" + m.group(2) + "❤", Alert.Sound.ANVIL);
		} else if (percent >= cfg.lowHealthPercent + 5) {
			lowHealthShown = false; // healed up: warn again next time
		}
	}
}
