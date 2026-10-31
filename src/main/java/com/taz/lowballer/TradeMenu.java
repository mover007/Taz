package com.taz.lowballer;

import com.google.gson.JsonObject;
import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.inventory.ItemPrices;
import com.taz.mixin.AbstractSignEditScreenAccessor;
import com.taz.util.Items;
import com.taz.xpassist.data.Prices;
import com.taz.xpassist.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's trade menu: a 5-row chest titled "You       Name", your items in the left four columns, theirs in the right
 * four, glass panes between. Their items are priced through SkyCofl as they go in, and the totals kept for the trade HUD.
 */
public final class TradeMenu {
	static final int[] YOURS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
	static final int[] THEIRS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
	private static final int[] DIVIDER = {4, 13, 22, 31, 40};
	private static final Pattern COINS = Pattern.compile("^([\\d,]*\\.?\\d+)\\s*([kmb]?)\\s+coins$", Pattern.CASE_INSENSITIVE);
	/** SkyCofl keeps its estimates 2 minutes, so asking sooner gets the same answer. */
	private static final long KEEP_MS = 3 * 60_000L;
	/** Wait until their side stops changing, so items put in one by one are priced together. */
	private static final long SETTLE_MS = 400;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Taz lowballer");
		t.setDaemon(true);
		return t;
	});

	/** A stack's price: SkyCofl's estimate, or else the latest price of its id; both null when nobody knows it. */
	private record Entry(Cofl.Estimate estimate, Lowball.Priced latest, long at) {
	}

	/** One of their stacks: priced null while it's being priced or when it has no price (then {@code pricing} says which). */
	public record Item(ItemStack stack, Lowball.Priced priced, Lowball.Offer offer, boolean pricing) {
	}

	/** Their side of the open trade. */
	public record Trade(String other, List<Item> items, long coins, long yourCoins, String problem) {
		/** Their priced items. */
		public long itemsWorth() {
			long sum = 0;
			for (Item i : items) if (i.priced() != null) sum += i.priced().worth();
			return sum;
		}

		/** Their items plus their coins. */
		public long worth() {
			return itemsWorth() + coins;
		}

		/** The smart offers for each item, plus their coins. */
		public long smart() {
			long sum = coins;
			for (Item i : items) if (i.offer() != null) sum += i.offer().coins();
			return sum;
		}

		public int pricing() {
			return (int) items.stream().filter(Item::pricing).count();
		}

		public int unpriced() {
			return (int) items.stream().filter(i -> i.priced() == null && !i.pricing()).count();
		}
	}

	private static final Map<String, Entry> PRICES = new ConcurrentHashMap<>();
	private static final Set<String> ASKING = ConcurrentHashMap.newKeySet();
	private static volatile long busyUntil;
	private static volatile String problem;
	private static volatile Trade trade;
	private static String lastSide = "";
	private static long changedAt;
	private static long lastTradeClick;
	private static volatile long chosen;
	private static volatile long chosenAt;

	private TradeMenu() {
	}

	/** The open trade, or null when the trade menu isn't open (or the Lowballer is off). */
	public static Trade trade() {
		return trade;
	}

	/** The offer you clicked last, and when (for the HUD's "copied" line). */
	public static long chosen() {
		return chosen;
	}

	public static long chosenAt() {
		return chosenAt;
	}

	public static boolean isTrade(Screen screen) {
		if (!(screen instanceof ContainerScreen chest)) return false;
		Container c = chest.getMenu().getContainer();
		if (c.getContainerSize() != 45 || !TextUtil.cleanChat(chest.getTitle().getString()).startsWith("You")) return false;
		for (int slot : DIVIDER) {
			ItemStack s = c.getItem(slot);
			if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains("glass_pane")) return false;
		}
		return true;
	}

	/** Every client tick: reads the trade menu, prices what's new, and works out the offers. */
	public static void tick(Minecraft mc) {
		LowballerConfig cfg = LowballerConfig.get();
		if (!cfg.enabled || !isTrade(mc.screen)) {
			trade = null;
			lastSide = "";
			return;
		}
		ContainerScreen screen = (ContainerScreen) mc.screen;
		Container c = screen.getMenu().getContainer();
		long now = System.currentTimeMillis();
		List<ItemStack> theirs = new ArrayList<>();
		long coins = 0, yourCoins = 0;
		for (int slot : THEIRS) {
			ItemStack s = c.getItem(slot);
			if (s.isEmpty()) continue;
			Long co = coins(s);
			if (co != null) coins += co;
			else theirs.add(s);
		}
		for (int slot : YOURS) {
			Long co = coins(c.getItem(slot));
			if (co != null) yourCoins += co;
		}

		StringBuilder side = new StringBuilder();
		List<ItemStack> todo = new ArrayList<>();
		for (ItemStack s : theirs) {
			String key = key(s);
			side.append(key).append('\n');
			Entry e = PRICES.get(key);
			if ((e == null || now - e.at() > KEEP_MS) && !ASKING.contains(key)) todo.add(s);
		}
		if (!side.toString().equals(lastSide)) {
			lastSide = side.toString();
			changedAt = now;
		}
		if (!todo.isEmpty() && now - changedAt >= SETTLE_MS && now >= busyUntil) ask(todo);

		List<Item> items = new ArrayList<>();
		for (ItemStack s : theirs) {
			Entry e = PRICES.get(key(s));
			Lowball.Priced p = e == null ? null : e.estimate() != null ? priced(e.estimate(), cfg.worth) : e.latest();
			items.add(new Item(s, p, p == null ? null : Lowball.offer(p, cfg), e == null));
		}
		String title = TextUtil.cleanChat(screen.getTitle().getString());
		trade = new Trade(title.substring(3).trim(), items, coins, yourCoins, problem);
	}

	/** What a SkyCofl estimate makes the item worth, by the Worth setting. */
	static Lowball.Priced priced(Cofl.Estimate e, LowballerConfig.Worth basis) {
		long bin = e.lbin(), median = e.median();
		long worth = switch (basis) {
			case BIN -> bin > 0 ? bin : median;
			case MEDIAN -> median > 0 ? median : bin;
			case LOWER -> e.worth();
		};
		return new Lowball.Priced(worth, e.volume(), e.fastSell(), false);
	}

	/** Prices the stacks in the background: one estimate for all, then the latest price of each one SkyCofl couldn't estimate. */
	private static void ask(List<ItemStack> todo) {
		List<ItemStack> stacks = todo.stream().map(ItemStack::copy).toList();
		List<String> keys = stacks.stream().map(TradeMenu::key).toList();
		ASKING.addAll(keys);
		WORKER.execute(() -> {
			try {
				List<Cofl.Estimate> estimates;
				boolean reached = true;
				try {
					estimates = Cofl.estimate(stacks);
				} catch (Cofl.Busy busy) {
					throw busy;
				} catch (Exception e) {
					Taz.LOGGER.warn("Lowballer: SkyCofl's estimate failed ({})", e.toString());
					estimates = null;
					reached = false;
				}
				for (int i = 0; i < stacks.size(); i++) {
					Cofl.Estimate est = estimates == null ? null : estimates.get(i);
					Lowball.Priced latest = est != null ? null : latest(stacks.get(i), reached);
					// Taz's own price list is a stand-in: ask SkyCofl again in 30 seconds.
					long at = System.currentTimeMillis() - (reached ? 0 : KEEP_MS - 30_000);
					PRICES.put(keys.get(i), new Entry(est, latest, at));
				}
				problem = reached ? null : "Couldn't reach SkyCofl: Taz's own prices for now";
			} catch (Cofl.Busy busy) {
				busyUntil = System.currentTimeMillis() + busy.seconds * 1000L;
				problem = "SkyCofl is busy: trying again in " + busy.seconds + "s";
			} catch (Exception e) {
				Taz.LOGGER.warn("Lowballer: pricing failed", e);
				busyUntil = System.currentTimeMillis() + 10_000;
				problem = "Couldn't price their items: trying again soon";
			} finally {
				ASKING.removeAll(keys);
			}
		});
	}

	/** The latest bazaar or lowest BIN price of a stack's id from SkyCofl, else Taz's price list; null when neither has it. */
	private static Lowball.Priced latest(ItemStack stack, boolean askCofl) throws Exception {
		int count = stack.getCount();
		String tag = coflTag(stack);
		if (askCofl && tag != null) {
			Cofl.Current cur;
			try {
				cur = Cofl.current(tag, count);
			} catch (Cofl.Busy busy) {
				throw busy;
			} catch (Exception e) {
				cur = null;
			}
			if (cur != null) {
				double price = cur.auction() ? cur.buy() : cur.sell();
				if (price > 0) return new Lowball.Priced(Math.round(price), -1, 0, !cur.auction());
			}
		}
		Prices prices = ItemPrices.get();
		String key = ItemPrices.key(stack);
		if (prices == null || key == null) return null;
		if (prices.isBazaar(key)) {
			double sell = prices.bazaar(key, true);
			return Double.isNaN(sell) ? null : new Lowball.Priced(Math.round(sell * count), -1, 0, true);
		}
		double bin = prices.market(key);
		return Double.isNaN(bin) ? null : new Lowball.Priced(Math.round(bin * count), -1, 0, false);
	}

	/** SkyCofl's id for an item: the SkyBlock id, "PET_ENDER_DRAGON" for pets, "ENCHANTMENT_ULTIMATE_WISE_5" for books. */
	static String coflTag(ItemStack stack) {
		String id = Items.id(stack);
		if (id.isEmpty()) return null;
		if (id.equals("PET")) {
			JsonObject pet = ItemUtil.petInfo(stack);
			return pet != null && pet.has("type") ? "PET_" + pet.get("type").getAsString() : null;
		}
		return ItemPrices.key(stack);
	}

	/** Coins in a trade slot ("64M coins", "1,500,000 coins"), or null when it isn't coins. */
	static Long coins(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		Matcher m = COINS.matcher(TextUtil.cleanChat(stack.getHoverName().getString()).trim());
		if (!m.matches()) return null;
		double n;
		try {
			n = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
		double mult = switch (m.group(2).toLowerCase(Locale.ROOT)) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			default -> 1;
		};
		return Math.round(n * mult);
	}

	/** Same item, same count: same price. */
	private static String key(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return (data == null ? stack.getItem().toString() : data.copyTag().toString()) + "|" + stack.getCount() + "|" + stack.getHoverName().getString();
	}

	/** You clicked an offer on the HUD: copy it, and type it into the coins sign when that opens. */
	static void choose(long coins) {
		chosen = coins;
		chosenAt = System.currentTimeMillis();
		Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(coins));
	}

	/** A click in the trade menu (the Coins button opens a sign next). */
	public static void onSlotClick(AbstractContainerScreen<?> screen) {
		if (LowballerConfig.get().enabled && isTrade(screen)) lastTradeClick = System.currentTimeMillis();
	}

	/** A sign opened right after a click in the trade menu: the offer you clicked goes on its first line. */
	public static void onScreen(Screen screen) {
		LowballerConfig cfg = LowballerConfig.get();
		if (!cfg.enabled || !cfg.clickToOffer || !(screen instanceof AbstractSignEditScreen sign) || chosen <= 0) return;
		long now = System.currentTimeMillis();
		if (now - lastTradeClick > 5_000 || now - chosenAt > 5 * 60_000L) return;
		String[] lines = ((AbstractSignEditScreenAccessor) sign).taz$lines();
		if (lines.length < 2 || !lines[0].isEmpty() || !lines[1].startsWith("^^^")) return;
		lines[0] = String.valueOf(chosen);
		chosen = 0;
	}

	/** Their items' tooltips in the trade menu: worth, sales a day and the smart offer. */
	public static void tooltip(ItemStack stack, List<Component> lines) {
		LowballerConfig cfg = LowballerConfig.get();
		Trade t = trade;
		if (!cfg.enabled || !cfg.tooltips || t == null) return;
		for (Item item : t.items()) {
			if (item.stack() != stack && !ItemStack.matches(item.stack(), stack)) continue;
			if (item.pricing()) {
				lines.add(Component.literal("§8Lowballer: pricing…"));
			} else if (item.priced() == null) {
				lines.add(Component.literal("§8Lowballer: no price for this"));
			} else {
				Lowball.Priced p = item.priced();
				lines.add(Component.literal("§7Worth: §6" + TradeHud.coins(p.worth()) + (p.bazaar() ? " §8(bazaar insta-sell)"
						: " §8· sells " + Lowball.perDay(p.volume()))));
				lines.add(Component.literal("§aSmart offer: §6" + TradeHud.coins(item.offer().coins()) + " §8(" + Lowball.pct(item.offer().under()) + " under)"));
				lines.add(Component.literal("§8" + String.join(", ", item.offer().why())));
			}
			return;
		}
	}

	/** Test hook: forget every price. */
	public static void clear() {
		PRICES.clear();
		busyUntil = 0;
		problem = null;
		chosen = 0;
	}
}
