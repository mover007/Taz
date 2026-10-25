package com.taz.foraging;

import com.taz.hud.HudPos;
import com.taz.hud.TextHud;
import com.taz.util.Alert;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's Tree Gift, as it appears in chat (one packet per line):
 * <pre>
 * KARMA! Your Tree Gift contained double loot!       (sometimes, just before)
 * ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 * TREE GIFT
 *
 * You helped cut 100% of the Fig Tree.
 * +5 rewards gained! (hover)                           (the rewards are in its hover text)
 *
 * BONUS GIFT
 * Stretching Sticks x2 (10%)
 * A Phanpyre fell from the Tree!
 * ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
 * </pre>
 * Counted in a HUD, optionally shrunk to one line. Also counts honeyhive Honeycomb ("HIVE! You found Honeycomb x30 and
 * Enchanted Honeycomb!") and remembers the HOTF Lottery buff ("New buff: Gain +50 Fig Fortune.").
 */
public final class TreeGifts extends TextHud {
	private static final Pattern BAR = Pattern.compile("^▬{10,}$");
	private static final Pattern HELPED = Pattern.compile("^You helped cut ([\\d.]+)% of the (\\w+) Tree\\.$");
	private static final Pattern REWARDS = Pattern.compile("^\\+(\\d+) rewards? gained!");
	private static final Pattern BONUS_ITEM = Pattern.compile("^(.+?) \\(([\\d.]+)%\\)$");
	private static final Pattern MOB = Pattern.compile("^An? (.+?) fell from the Tree!$");
	private static final Pattern KARMA = Pattern.compile("^KARMA! Your Tree Gift contained double loot!");
	private static final Pattern HIVE = Pattern.compile("^HIVE! You found Honeycomb x(\\d+)");
	private static final Pattern LOTTERY = Pattern.compile("^New buff: (.+?)\\.?$");
	static final String REGENERATING = "You cannot damage a tree while it is regenerating!";
	/** A gift's lines all come within the same moment; a stuck one is closed after this. */
	private static final long GIFT_TIMEOUT_MS = 2000;
	/** How long a held-back ▬▬▬ line waits to see whether a TREE GIFT follows. */
	private static final long BAR_HOLD_MS = 150;

	private static final class Gift {
		String tree = "?";
		String percent = "?";
		int rewards;
		Component rewardsHover;
		boolean karma;
		final List<String> bonus = new ArrayList<>();
		final List<String> mobs = new ArrayList<>();
		boolean inBonus;
		long startMs;
	}

	private static final Map<String, Integer> GIFTS = new LinkedHashMap<>();
	private static final Map<String, Integer> MOBS = new LinkedHashMap<>();
	private static int bonusGifts;
	private static int karma;
	private static String rarest;
	private static double rarestChance = Double.MAX_VALUE;
	private static int honeycomb;
	private static int hives;
	private static String lottery;

	private static Gift gift;
	private static boolean karmaPending;
	/** The current line belongs to a Tree Gift (hide it when compacting). */
	private static boolean partOfGift;
	private static Component heldBar;
	private static long heldBarMs;
	/** Test hooks. */
	public static String lastCompact;
	public static boolean lastCompactHasHover;
	public static int barsPutBack;

	public TreeGifts() {
		super("Tree Gifts", 0.005, 0.40);
	}

	// ---- chat

	/** Every chat line (Taz.onSystemChat), before it reaches the chat. */
	public static void onChat(Component message, String line) {
		partOfGift = false;
		TreeGiftsConfig cfg = TreeGiftsConfig.get();
		if (!cfg.enabled) {
			gift = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (gift != null && now - gift.startMs > GIFT_TIMEOUT_MS) finish();
		boolean bar = BAR.matcher(line).find();
		// A ▬▬▬ line held back last time: drop it if a gift starts, else put it back before this line.
		if (heldBar != null) {
			if (line.equals("TREE GIFT")) heldBar = null;
			else releaseBar();
		}
		Matcher m;
		if (gift == null) {
			if (KARMA.matcher(line).find()) {
				karmaPending = true;
				partOfGift = true;
			} else if (line.equals("TREE GIFT")) {
				gift = new Gift();
				gift.startMs = now;
				gift.karma = karmaPending;
				karmaPending = false;
				partOfGift = true;
			} else if (bar && cfg.compact) {
				heldBar = message;
				heldBarMs = now;
				partOfGift = true; // held back; put back unless a TREE GIFT follows
			} else if ((m = HIVE.matcher(line)).find()) {
				honeycomb += Integer.parseInt(m.group(1));
				hives++;
			} else if ((m = LOTTERY.matcher(line)).find()) {
				lottery = m.group(1);
			} else if ((m = MOB.matcher(line)).find()) {
				MOBS.merge(m.group(1), 1, Integer::sum); // a mob without a gift block around it
			}
			return;
		}
		partOfGift = true;
		if (bar) {
			finish();
		} else if ((m = HELPED.matcher(line)).find()) {
			gift.percent = m.group(1);
			gift.tree = m.group(2);
		} else if ((m = REWARDS.matcher(line)).find()) {
			gift.rewards = Integer.parseInt(m.group(1));
			gift.rewardsHover = hoverOf(message);
		} else if (line.equals("BONUS GIFT")) {
			gift.inBonus = true;
		} else if ((m = MOB.matcher(line)).find()) {
			gift.mobs.add(m.group(1));
		} else if (gift.inBonus && (m = BONUS_ITEM.matcher(line)).find()) {
			gift.bonus.add(line);
			double chance = Double.parseDouble(m.group(2));
			if (chance < rarestChance) {
				rarestChance = chance;
				rarest = line;
			}
			if (chance <= cfg.rareChance) Alert.show("§6§lRARE BONUS GIFT", "§f" + m.group(1) + " §7(" + m.group(2) + "%)", cfg.sound);
		} else if (!line.isEmpty()) {
			partOfGift = false; // someone's chat in the middle: leave it alone
		}
	}

	/** The hover text of the first part of a message that has one. */
	private static Component hoverOf(Component message) {
		Optional<Component> found = message.visit((style, text) -> {
			HoverEvent hover = style.getHoverEvent();
			return hover instanceof HoverEvent.ShowText show ? Optional.of(show.value()) : Optional.empty();
		}, Style.EMPTY);
		return found.orElse(null);
	}

	private static void finish() {
		Gift g = gift;
		gift = null;
		if (g == null) return;
		GIFTS.merge(g.tree, 1, Integer::sum);
		if (!g.bonus.isEmpty()) bonusGifts++;
		if (g.karma) karma++;
		for (String mob : g.mobs) MOBS.merge(mob, 1, Integer::sum);
		if (TreeGiftsConfig.get().compact) {
			Component line = compactLine(g);
			lastCompact = net.minecraft.ChatFormatting.stripFormatting(line.getString());
			lastCompactHasHover = hoverOf(line) != null;
			Minecraft.getInstance().gui.getChat().addClientSystemMessage(line);
		}
	}

	/** "TREE GIFT Fig 100% · +5 rewards · BONUS Stretching Sticks x2 (10%) · Phanpyre! · KARMA" */
	static Component compactLine(Gift g) {
		MutableComponent line = Component.literal("§2§lTREE GIFT §a" + g.tree + " §f" + g.percent.replace(".0", "") + "%");
		MutableComponent rewards = Component.literal(" §7· §f+" + g.rewards + " rewards");
		if (g.rewardsHover != null) {
			Component hover = g.rewardsHover;
			rewards = rewards.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)));
			rewards.append(Component.literal(" §8(hover)"));
		}
		line.append(rewards);
		if (g.karma) line.append(Component.literal(" §d§lKARMA x2"));
		for (String b : g.bonus) line.append(Component.literal(" §7· §6BONUS §f" + b));
		for (String mob : g.mobs) line.append(Component.literal(" §7· §c" + mob + "!"));
		return line;
	}

	private static void releaseBar() {
		Component bar = heldBar;
		heldBar = null;
		if (bar != null) {
			barsPutBack++;
			Minecraft.getInstance().gui.getChat().addClientSystemMessage(bar);
		}
	}

	/** Every client tick: a held-back ▬▬▬ line with nothing after it goes back in the chat. */
	public static void tick() {
		if (heldBar != null && System.currentTimeMillis() - heldBarMs > BAR_HOLD_MS) releaseBar();
		if (gift != null && System.currentTimeMillis() - gift.startMs > GIFT_TIMEOUT_MS) finish();
	}

	/** ClientReceiveMessageEvents.ALLOW_GAME (after onChat saw the same line). */
	public static boolean allow(Component message, boolean overlay) {
		TreeGiftsConfig cfg = TreeGiftsConfig.get();
		if (overlay || !cfg.enabled) return true;
		if (cfg.hideRegenerating && message.getString().contains(REGENERATING)) return false;
		return !(cfg.compact && partOfGift);
	}

	// ---- counts

	public static int gifts(String tree) {
		return GIFTS.getOrDefault(tree, 0);
	}

	public static int bonusGifts() {
		return bonusGifts;
	}

	public static int karma() {
		return karma;
	}

	public static int mobs() {
		return MOBS.values().stream().mapToInt(Integer::intValue).sum();
	}

	public static int honeycomb() {
		return honeycomb;
	}

	public static String lottery() {
		return lottery;
	}

	public static void resetCounts() {
		GIFTS.clear();
		MOBS.clear();
		bonusGifts = karma = honeycomb = hives = 0;
		rarest = null;
		rarestChance = Double.MAX_VALUE;
		TazChat.info("Tree Gift counts reset.");
	}

	// ---- HUD

	@Override
	protected HudPos pos() {
		return TreeGiftsConfig.get().hud;
	}

	@Override
	public boolean enabled() {
		return TreeGiftsConfig.get().enabled && TreeGiftsConfig.get().hudEnabled;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> lines = new ArrayList<>();
		boolean any = !GIFTS.isEmpty() || honeycomb > 0 || lottery != null;
		if (!preview && (!Galatea.onIslands() || !any)) return lines;
		lines.add("§2§lTree Gifts");
		if (GIFTS.isEmpty()) {
			if (preview) lines.add("§fFig §a12 §7· §fMangrove §a3");
		} else {
			List<String> parts = new ArrayList<>();
			int total = 0;
			for (Map.Entry<String, Integer> e : GIFTS.entrySet()) {
				parts.add("§f" + e.getKey() + " §a" + e.getValue());
				total += e.getValue();
			}
			lines.add(String.join(" §7· ", parts) + (GIFTS.size() > 1 ? " §7(" + total + ")" : ""));
			lines.add("§7Bonus §6" + bonusGifts + " §7· KARMA §d" + karma + " §7· Mobs §c" + mobs());
			if (rarest != null) lines.add("§7Rarest: §6" + rarest);
		}
		if (honeycomb > 0) lines.add("§7Honeycomb §e+" + String.format("%,d", honeycomb) + " §7from " + hives + " hive" + (hives == 1 ? "" : "s"));
		if (lottery != null) lines.add("§7Lottery: §f" + lottery);
		return lines;
	}
}
