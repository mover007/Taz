package com.taz.inferno;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.TazTheme;
import com.taz.lowballer.TradeHud;
import com.taz.util.Alert;
import com.taz.util.Items;
import com.taz.util.TazChat;
import com.taz.xpassist.npc.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Inferno minion profit tracker. It sees what you buy to make fuel (bazaar and AH), the fuel you craft (costed from
 * what you paid for its parts), the fuel you put in each minion, Capsaicin Eyedrops, what you collect, and what you sell.
 * Each fuel put in a minion is a "run": what it cost, what it should make in its 24 hours, and what you collected.
 */
public final class InfernoTracker {
	private static final Pattern BZ_BOUGHT = Pattern.compile("^\\[Bazaar] Bought ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");
	private static final Pattern BZ_CLAIMED = Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+)x (.+) worth ([\\d,.]+) coins bought for ([\\d,.]+) each!$");
	private static final Pattern AH_BOUGHT = Pattern.compile("^You purchased (?:([\\d,]+)x )?(.+) for ([\\d,]+) coins!$");
	private static final Pattern BZ_SOLD = Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");
	private static final Pattern BZ_OFFER = Pattern.compile("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+) at ([\\d,.]+) each!$");
	private static final Pattern NPC_SOLD = Pattern.compile("^You sold (.+) x([\\d,]+) for ([\\d,.]+) Coins?!$");
	private static final Pattern AH_SOLD = Pattern.compile("^You collected ([\\d,]+) coins from selling (.+) to (.+) in an auction!$");
	private static final Pattern HOPPER = Pattern.compile("^You received ([\\d,.]+) coins!$");
	private static final Pattern LEFT = Pattern.compile("(?i)(remaining|left|expires|time)");
	private static final Pattern SPAN = Pattern.compile("(\\d+)\\s*([dhms])");
	/** The tab list's Minions widget: "12x Inferno V [ACTIVE]". */
	private static final Pattern TAB = Pattern.compile("^(\\d+)x Inferno ([IVX]+) \\[(.+)]$");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** A minion with a hopper sells by itself: then only what leaves this soon after a click was collected. */
	private static final long CLICK_MS = 3000;
	/** Hypixel reopens the minion menu when you collect: one that opens this soon after, with no new right-click, is the same minion. */
	private static final long REOPEN_MS = 3000;
	/** Collections this close together get one chat line. */
	private static final long CHAT_MS = 4000;
	/** A fuel that shows up this soon after the crafting menu closed was crafted (it may have been on the cursor). */
	private static final long CRAFT_MS = 3000;
	/** An AH fuel purchase waits this long to be told which fuel it was (when you claim it). */
	private static final long CLAIM_MS = 30 * 60_000L;

	// ---- saved

	public static final class Purchase {
		public long at;
		public String id = "";
		public int count;
		public long coins;
		/** How many haven't gone into a fuel yet. */
		public int left;
		public String how = "";
	}

	/** A fuel you have that isn't in a minion yet. */
	public static final class Stock {
		public long at;
		/** "" for an AH fuel not claimed yet (the AH doesn't say which). */
		public String fuel = "";
		public long cost;
		public boolean estimated;
		public String how = "";
	}

	/** One fuel in one minion. */
	public static final class Run {
		public long id;
		public String minion = "";
		public int tier;
		public String fuel = "";
		public long placedAt, startAt, endsAt;
		public long cost;
		public boolean estimated;
		public String how = "";
		/** Capsaicin Eyedrops used on the minion while it burned: how many, and their cost. */
		public int eyedrops;
		public long eyedropsCost;
		/** Harvests a day when it went in, and what its 24 hours should make then (-1 until prices are in). */
		public double harvests;
		public long expected = -1;
		/** Taken out before it ran out. */
		public boolean early;

		public long totalCost() {
			return cost + eyedropsCost;
		}

		public boolean running(long now) {
			return !early && startAt <= now && now < endsAt;
		}

		public boolean queued(long now) {
			return !early && startAt > now;
		}

		/** How much of a day it has burned, 0 to 1 (less than 1 when it was taken out early). */
		public double burned(long now) {
			return Math.max(0, Math.min(1, (Math.min(now, endsAt) - startAt) / (double) InfernoData.FUEL_MS));
		}

		/** What it should make: all of its 24 hours, or the part that burned when it was taken out. -1 until prices are in. */
		public long expectedNow(long now) {
			return expected < 0 || !early ? expected : Math.round(expected * burned(now));
		}
	}

	public static final class Minion {
		public String key = "";
		public int number;
		public int tier;
		public long seenAt;
		public List<String> upgrades = new ArrayList<>();
		/** What was in its storage the last time you opened it, and when. */
		public long storage = -1;
		public long storageAt;
		public long eyedropsEnd;
		/** Eyedrops used while it had no fuel, for the next fuel. */
		public long pendingEyedrops;
		public int pendingEyedropsCount;
		public boolean warned;

		public String label() {
			return "#" + number + (tier > 0 ? " " + InfernoData.roman(tier) : "");
		}
	}

	/** What you collected from a minion at once. */
	public static final class Haul {
		public long at;
		public String minion = "";
		public long run;
		public Map<String, Long> items = new LinkedHashMap<>();
		/** Coins its hopper had sold for. */
		public long coins;
		/** What it's worth after tax when collected (-1 until prices are in). */
		public long value = -1;
	}

	public static final class Sale {
		public long at;
		public String id = "";
		public long count;
		public long coins;
		public String how = "";
	}

	private static final class Data {
		/** From the tab list: how many Inferno minions you have and their highest tier. */
		int tabCount;
		int tabTier;
		List<Purchase> bought = new ArrayList<>();
		List<Stock> stock = new ArrayList<>();
		List<Run> runs = new ArrayList<>();
		List<Minion> minions = new ArrayList<>();
		List<Haul> hauls = new ArrayList<>();
		List<Sale> sales = new ArrayList<>();
	}

	private static Data data = new Data();
	private static String loadedFor;
	private static boolean dirty;
	private static long savedAt;
	public static volatile String testAccount;
	/** Test hook: the tab list's lines. */
	public static volatile List<String> testTab;
	/** The state the tab list gives your Inferno minions ("ACTIVE", "FULL"). */
	private static String tabState = "";

	// ---- what's happening now

	private static Map<String, Integer> lastFuels;
	private static Object lastLevel;
	private static long craftingAt;
	private static String clickedKey;
	private static long clickedAt;
	private static String eyedropsKey;
	private static long eyedropsAt;
	private static int eyedropsHad;
	/** The minion menu open now. */
	private static Screen minionScreen;
	private static Minion open;
	private static String openFuel;
	private static int openFuelCount = -1;
	private static Map<String, Long> openItems;
	private static long menuClickAt;
	private static long openedAt;
	/** The minion menu that closed last, what it held, and when: Hypixel may reopen it right away. */
	private static Minion closedMinion;
	private static Map<String, Long> closedItems;
	private static long closedAt;
	private static Map<String, Long> collecting = new LinkedHashMap<>();
	private static long collectingCoins;
	/** Collections waiting for their chat line. */
	private static final List<Haul> CHAT = new ArrayList<>();
	private static final Map<Haul, Minion> CHAT_FROM = new HashMap<>();
	private static long lastHaulAt;
	private static long tickCount;
	private static int pricedVersion = -1;

	private InfernoTracker() {
	}

	private static boolean on() {
		return InfernoConfig.get().enabled;
	}

	// ---- chat

	public static void onChat(String text) {
		if (!on()) return;
		for (String line : text.split("\n")) line(TextUtil.cleanChat(line).trim());
	}

	private static void line(String line) {
		long now = System.currentTimeMillis();
		Matcher m;
		if ((m = BZ_BOUGHT.matcher(line)).matches()) {
			bought(m.group(2), (int) num(m.group(1)), num(m.group(3)), "Bazaar", now);
		} else if ((m = BZ_CLAIMED.matcher(line)).matches()) {
			bought(m.group(2), (int) num(m.group(1)), num(m.group(3)), "Buy order", now);
		} else if ((m = AH_BOUGHT.matcher(line)).matches()) {
			bought(m.group(2), m.group(1) == null ? 1 : (int) num(m.group(1)), num(m.group(3)), "AH", now);
		} else if ((m = BZ_SOLD.matcher(line)).matches()) {
			sold(m.group(2), num(m.group(1)), num(m.group(3)), "Bazaar", now);
		} else if ((m = BZ_OFFER.matcher(line)).matches()) {
			sold(m.group(3), num(m.group(2)), num(m.group(1)), "Bazaar", now);
		} else if ((m = NPC_SOLD.matcher(line)).matches()) {
			sold(m.group(1), num(m.group(2)), num(m.group(3)), "NPC", now);
		} else if ((m = AH_SOLD.matcher(line)).matches()) {
			sold(m.group(2), 1, num(m.group(1)), "AH", now);
		} else if ((m = HOPPER.matcher(line)).matches() && (open != null || closedMinion != null) && now - menuClickAt < 3000) {
			collectingCoins += num(m.group(1));
		}
	}

	/** Something bought that goes into fuel: distillates, fuel blocks, gabagool, eyedrops, or a fuel on the AH. */
	private static void bought(String name, int count, long coins, String how, long now) {
		String id = InfernoData.BOUGHT.get(name);
		if (id == null || count <= 0 || !load()) return;
		if (id.isEmpty()) { // an Inferno Minion Fuel on the AH: which one shows when you claim it
			for (int i = 0; i < count; i++) {
				Stock s = new Stock();
				s.at = now;
				s.cost = coins / count;
				s.how = "Bought on the AH";
				data.stock.add(s);
			}
		} else {
			Purchase p = new Purchase();
			p.at = now;
			p.id = id;
			p.count = count;
			p.left = count;
			p.coins = coins;
			p.how = how;
			data.bought.add(p);
		}
		dirty = true;
	}

	private static void sold(String name, long count, long coins, String how, long now) {
		String id = InfernoData.SOLD.get(name);
		if (id == null || !load()) return;
		Sale s = new Sale();
		s.at = now;
		s.id = id;
		s.count = count;
		s.coins = coins;
		s.how = how;
		data.sales.add(s);
		dirty = true;
	}

	// ---- clicks

	/** You right-clicked an entity: a minion is an armor stand, and eyedrops are used on one. */
	public static void onUseEntity(Entity entity, ItemStack held) {
		if (!on() || !(entity instanceof ArmorStand)) return;
		long now = System.currentTimeMillis();
		clickedKey = key(entity.blockPosition());
		clickedAt = now;
		String id = Items.id(held);
		if (id.startsWith("CAPSAICIN_EYEDROPS")) {
			eyedropsKey = clickedKey;
			eyedropsAt = now;
			eyedropsHad = count(id2 -> id2.startsWith("CAPSAICIN_EYEDROPS"));
		}
	}

	/** A click in any menu (only one in a minion's matters). */
	public static void onMenuClick(Screen screen) {
		if (screen == minionScreen) menuClickAt = System.currentTimeMillis();
	}

	private static String key(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	// ---- every tick

	public static void tick(Minecraft mc) {
		if (!on() || mc.player == null || mc.level == null) {
			closeMinion();
			flush();
			lastFuels = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			lastFuels = null;
			closeMinion();
			flush();
		}
		if (!load()) return;
		Screen screen = mc.screen;
		String title = screen == null ? "" : TextUtil.cleanChat(screen.getTitle().getString());
		if (screen instanceof CraftingScreen || title.equals("Craft Item")) craftingAt = now;
		fuels(mc, screen, now);
		eyedrops(now);
		if (screen instanceof AbstractContainerScreen<?> s && InfernoData.tier(title) > 0) minion(s, InfernoData.tier(title), now);
		else closeMinion();
		if (minionScreen == null && closedMinion != null && now - closedAt > REOPEN_MS) flush();
		if (!CHAT.isEmpty() && now - lastHaulAt > CHAT_MS) haulChat();
		if (++tickCount % 20 == 0) {
			readTab(mc);
			ranOut(now);
			if (InfernoPrices.version() != pricedVersion) {
				pricedVersion = InfernoPrices.version();
				fillPrices();
			}
		}
		if (dirty && now - savedAt > 5000) save();
	}

	/** Fuels in your inventory (and on the cursor): one more while crafting is a fuel crafted; after an AH buy, which fuel it was. */
	private static void fuels(Minecraft mc, Screen screen, long now) {
		Map<String, Integer> have = new HashMap<>();
		for (ItemStack s : mc.player.getInventory()) add(have, s);
		if (mc.player.containerMenu != null) add(have, mc.player.containerMenu.getCarried());
		Map<String, Integer> before = lastFuels;
		lastFuels = have;
		if (before == null) return;
		boolean crafting = now - craftingAt < CRAFT_MS;
		for (Map.Entry<String, Integer> e : have.entrySet()) {
			int more = e.getValue() - before.getOrDefault(e.getKey(), 0);
			if (more <= 0) continue;
			if (crafting) {
				for (int i = 0; i < more; i++) crafted(e.getKey(), now);
			} else if (minionScreen == null) {
				for (int i = 0; i < more; i++) claimed(e.getKey(), now);
			}
		}
	}

	private static void add(Map<String, Integer> have, ItemStack s) {
		if (s == null || s.isEmpty()) return;
		String id = Items.id(s);
		if (InfernoData.fuel(id) != null) have.merge(id, s.getCount(), Integer::sum);
	}

	private static int count(java.util.function.Predicate<String> which) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return 0;
		int n = 0;
		for (ItemStack s : mc.player.getInventory()) if (!s.isEmpty() && which.test(Items.id(s))) n += s.getCount();
		return n;
	}

	/** A fuel crafted: its cost is what you paid for its parts (the ones you didn't buy at today's price). */
	private static void crafted(String fuelId, long now) {
		InfernoData.Fuel f = InfernoData.fuel(fuelId);
		Cost d = use(f.kind().distillate(), 6), b = use(InfernoData.FUEL_BLOCK, 2), g = use(f.grade().gabagool, 1);
		Stock s = new Stock();
		s.at = now;
		s.fuel = fuelId;
		s.cost = d.coins + b.coins + g.coins;
		s.estimated = d.estimated || b.estimated || g.estimated;
		s.how = "Crafted";
		data.stock.add(s);
		dirty = true;
		if (InfernoConfig.get().chat) {
			// What it makes a day only once Taz knows one of your minions (its speed depends on them).
			double perDay = best() == null ? -1 : perDay(f, best());
			String extra = perDay < 0 ? "" : " §7· " + (perDay - s.cost >= 0 ? "§a≈+" : "§c≈-") + coins(Math.abs(Math.round(perDay - s.cost))) + " §7profit a day";
			TazChat.send(TazTheme.styled("§7Inferno fuel crafted: " + f.grade().color + f.label() + " §7for §6" + (s.estimated ? "≈" : "")
					+ coins(s.cost) + extra).append(link()));
		}
	}

	/** A fuel from the AH showed up: the oldest AH buy that didn't know which fuel it was. */
	private static void claimed(String fuelId, long now) {
		for (Stock s : data.stock) {
			if (s.fuel.isEmpty() && now - s.at < CLAIM_MS) {
				s.fuel = fuelId;
				dirty = true;
				return;
			}
		}
	}

	private record Cost(long coins, boolean estimated) {
	}

	/** {@code n} of an item taken from what you bought, oldest first, at what you paid; any you didn't buy at today's price. */
	private static Cost use(String id, int n) {
		long coins = 0;
		int need = n;
		for (Purchase p : data.bought) {
			if (need == 0) break;
			if (!p.id.equals(id) || p.left <= 0) continue;
			int take = Math.min(need, p.left);
			coins += Math.round(p.coins * (take / (double) p.count));
			p.left -= take;
			need -= take;
		}
		if (need == 0) return new Cost(coins, false);
		double price = InfernoPrices.cost(id);
		return new Cost(coins + Math.round(Math.max(0, price) * need), true);
	}

	/** Eyedrops right-clicked on a minion, and one fewer in your inventory since: used on it. */
	private static void eyedrops(long now) {
		if (eyedropsKey == null) return;
		if (now - eyedropsAt > 3000) {
			eyedropsKey = null;
			return;
		}
		if (count(id -> id.startsWith("CAPSAICIN_EYEDROPS")) >= eyedropsHad) return;
		Minion m = minion(eyedropsKey);
		eyedropsKey = null;
		Cost c = use(InfernoData.EYEDROPS, 1);
		m.eyedropsEnd = Math.max(now, m.eyedropsEnd) + InfernoData.FUEL_MS;
		Run r = running(m, now);
		if (r != null) {
			r.eyedrops++;
			r.eyedropsCost += c.coins;
			r.expected = expected(r, m);
		} else {
			m.pendingEyedrops += c.coins;
			m.pendingEyedropsCount++;
		}
		dirty = true;
	}

	// ---- the minion menu

	private static void minion(AbstractContainerScreen<?> s, int tier, long now) {
		List<Slot> slots = s.getMenu().slots.stream().filter(x -> !(x.container instanceof net.minecraft.world.entity.player.Inventory)).toList();
		if (s != minionScreen) {
			closeMinion();
			minionScreen = s;
			// No right-click since the last minion menu opened, and it closed just now: Hypixel reopened it (it does when you collect).
			boolean fresh = clickedKey != null && clickedAt > openedAt && now - clickedAt < 5000;
			boolean again = closedMinion != null && now - closedAt < REOPEN_MS && (!fresh || clickedKey.equals(closedMinion.key));
			if (again) {
				open = closedMinion;
				openItems = closedItems;
			} else {
				flush();
				open = minion(fresh || clickedKey != null && now - clickedAt < 5000 ? clickedKey : "?" + tier);
				openItems = null;
			}
			// In the game log, so a collection Taz missed can be traced on whichever PC it happened.
			Taz.LOGGER.info("Inferno: minion menu {} (tier {}){}", open.key, tier, again ? ", reopened after a collect" : "");
			closedMinion = null;
			openedAt = now;
			open.tier = tier;
			open.seenAt = now;
			openFuel = null;
			openFuelCount = -1;
		}
		// Hypixel fills the menu a moment after it opens.
		if (slots.stream().allMatch(x -> x.getItem().isEmpty())) return;
		String fuel = null;
		int fuelCount = 0;
		ItemStack fuelStack = ItemStack.EMPTY;
		Map<String, Long> items = new LinkedHashMap<>();
		List<String> upgrades = new ArrayList<>();
		for (Slot slot : slots) {
			ItemStack st = slot.getItem();
			if (st.isEmpty()) continue;
			String id = Items.id(st);
			if (InfernoData.fuel(id) != null) {
				fuel = id;
				fuelCount += st.getCount();
				fuelStack = st;
			} else if (InfernoData.isProduct(id)) {
				items.merge(id, (long) st.getCount(), Long::sum);
			} else if (id.equals("FLYCATCHER_UPGRADE") || id.equals("MINION_EXPANDER") || id.contains("COMPACTOR") || id.endsWith("HOPPER")) {
				upgrades.add(id);
			}
		}
		if (!upgrades.equals(open.upgrades)) {
			open.upgrades = upgrades;
			dirty = true;
		}
		// Fuel in, out, or found there.
		if (openFuelCount < 0) {
			found(open, fuel, fuelCount, left(fuelStack), now);
		} else if (fuelCount > openFuelCount || fuel != null && !fuel.equals(openFuel)) {
			placed(open, fuel, fuelCount - (fuel.equals(openFuel) ? openFuelCount : 0), now);
		} else if (fuelCount == 0 && openFuelCount > 0) {
			takenOut(open, now);
		}
		openFuel = fuel;
		openFuelCount = fuelCount;
		// What left its storage was collected: only you take things out, unless a hopper sells them.
		boolean hopper = open.upgrades.stream().anyMatch(u -> u.endsWith("HOPPER"));
		if (openItems != null && (!hopper || now - menuClickAt < CLICK_MS)) {
			collected(openItems, items).forEach((k, v) -> collecting.merge(k, v, Long::sum));
		}
		if (!items.equals(openItems)) {
			Taz.LOGGER.info("Inferno: {} storage {} -> {}", open.key, openItems, items);
			openItems = items;
			long v = value(items);
			if (v >= 0) {
				open.storage = v;
				open.storageAt = now;
			}
			dirty = true;
		}
	}

	/** What went from {@code before} to {@code after} was collected, except what a compactor turned into something bigger. */
	static Map<String, Long> collected(Map<String, Long> before, Map<String, Long> after) {
		Map<String, Long> gone = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : before.entrySet()) {
			long n = e.getValue() - after.getOrDefault(e.getKey(), 0L);
			if (n > 0) gone.put(e.getKey(), n);
		}
		for (Map.Entry<String, Long> e : after.entrySet()) {
			long made = e.getValue() - before.getOrDefault(e.getKey(), 0L);
			InfernoData.Compacted c = InfernoData.COMPACTED.get(e.getKey());
			if (made <= 0 || c == null) continue;
			long used = gone.getOrDefault(c.from(), 0L) - made * c.ratio();
			if (used > 0) gone.put(c.from(), used);
			else gone.remove(c.from());
		}
		return gone;
	}

	/** The minion menu closed (or another opened): kept a moment, in case Hypixel reopens it. */
	private static void closeMinion() {
		if (minionScreen == null) return;
		closedMinion = open;
		closedItems = openItems;
		closedAt = System.currentTimeMillis();
		minionScreen = null;
		open = null;
	}

	/** What you collected from the last minion becomes a haul. */
	private static void flush() {
		Minion m = closedMinion != null ? closedMinion : open;
		closedMinion = null;
		if (m != null && (!collecting.isEmpty() || collectingCoins > 0)) haul(m, collecting, collectingCoins, System.currentTimeMillis(), null, false);
		collecting = new LinkedHashMap<>();
		collectingCoins = 0;
	}

	/** Its time left from a fuel's lore ("Time Remaining: 23h 12m"), or -1. */
	private static long left(ItemStack fuel) {
		if (fuel.isEmpty()) return -1;
		for (String line : Items.lore(fuel)) {
			String plain = TextUtil.cleanChat(line);
			if (!LEFT.matcher(plain).find()) continue;
			Matcher m = SPAN.matcher(plain);
			long ms = 0;
			boolean any = false;
			while (m.find()) {
				any = true;
				long n = Long.parseLong(m.group(1));
				ms += switch (m.group(2)) {
					case "d" -> n * 86_400_000L;
					case "h" -> n * 3_600_000L;
					case "m" -> n * 60_000L;
					default -> n * 1000L;
				};
			}
			if (any) return ms;
		}
		return -1;
	}

	/** The menu just opened: square what Taz thought was in it with what is. */
	private static void found(Minion m, String fuel, int count, long left, long now) {
		List<Run> live = live(m, now);
		if (fuel == null || count == 0) {
			for (Run r : live) end(r, now);
			return;
		}
		if (live.size() > count) {
			// Some ran out sooner than Taz thought.
			for (Run r : live.subList(0, live.size() - count)) end(r, now);
			live = live(m, now);
		}
		for (int i = live.size(); i < count; i++) {
			Run r = run(m, fuel, now, true);
			// A fuel Taz didn't see go in: when it ends comes from its lore if it says, else it went in when you got it (today).
			if (i == 0 && left > 0) {
				r.startAt = now + left - InfernoData.FUEL_MS;
				r.endsAt = now + left;
			} else if (i == 0 && r.placedAt < now && now - r.placedAt < InfernoData.FUEL_MS) {
				r.startAt = r.placedAt;
				r.endsAt = r.placedAt + InfernoData.FUEL_MS;
			}
		}
		if (left > 0) {
			live = live(m, now);
			if (!live.isEmpty()) {
				long end = now + left;
				for (Run r : live) {
					r.startAt = end - InfernoData.FUEL_MS;
					r.endsAt = end;
					end += InfernoData.FUEL_MS;
				}
			}
		}
		dirty = true;
	}

	/** You put {@code n} fuels in: each one burns after the last. */
	private static void placed(Minion m, String fuel, int n, long now) {
		for (int i = 0; i < n; i++) run(m, fuel, now, false);
		m.warned = false;
		dirty = true;
		if (InfernoConfig.get().chat && n > 0) {
			Run r = data.runs.get(data.runs.size() - 1);
			InfernoData.Fuel f = InfernoData.fuel(fuel);
			String exp = r.expected >= 0 ? " §7· makes §6≈" + coins(r.expected) + " §7in 24h (" + money(r.expected - r.totalCost()) + "§7)" : "";
			TazChat.send(TazTheme.styled("§7Inferno fuel in " + m.label() + ": " + f.grade().color + (n > 1 ? n + "x " : "") + f.label()
					+ " §7cost §6" + (r.estimated ? "≈" : "") + coins(r.cost) + exp).append(link()));
		}
	}

	/** A fuel's run in a minion: its cost from your fuels (crafted or bought), else today's price. */
	private static Run run(Minion m, String fuel, long now, boolean found) {
		Run r = new Run();
		r.id = now * 100 + data.runs.size() % 100;
		r.minion = m.key;
		r.tier = m.tier;
		r.fuel = fuel;
		r.placedAt = now;
		List<Run> live = live(m, now);
		r.startAt = live.isEmpty() ? now : Math.max(now, live.get(live.size() - 1).endsAt);
		r.endsAt = r.startAt + InfernoData.FUEL_MS;
		// A fuel already in a minion is most likely one you crafted or bought and Taz didn't see go in.
		Stock s = stock(fuel);
		if (s != null) {
			data.stock.remove(s);
			r.cost = s.cost;
			r.estimated = s.estimated;
			r.how = s.how;
			if (found) r.placedAt = Math.min(now, s.at);
		} else {
			double price = InfernoPrices.fuelCost(InfernoData.fuel(fuel));
			r.cost = Math.round(Math.max(0, price));
			r.estimated = true;
			r.how = found ? "Already in the minion" : "Not seen bought or crafted";
		}
		if (m.pendingEyedropsCount > 0) {
			r.eyedrops = m.pendingEyedropsCount;
			r.eyedropsCost = m.pendingEyedrops;
			m.pendingEyedrops = 0;
			m.pendingEyedropsCount = 0;
		}
		r.expected = expected(r, m);
		data.runs.add(r);
		return r;
	}

	/** Your oldest fuel of this kind; else an AH one Taz doesn't know the kind of. */
	private static Stock stock(String fuel) {
		for (Stock s : data.stock) if (s.fuel.equals(fuel)) return s;
		for (Stock s : data.stock) if (s.fuel.isEmpty()) return s;
		return null;
	}

	/** You took the fuel out: the burning one is gone, the rest come back to you. */
	private static void takenOut(Minion m, long now) {
		for (Run r : live(m, now)) {
			if (r.running(now)) {
				r.early = true;
				r.endsAt = now;
			} else {
				data.runs.remove(r);
				Stock s = new Stock();
				s.at = now;
				s.fuel = r.fuel;
				s.cost = r.cost;
				s.estimated = r.estimated;
				s.how = r.how;
				data.stock.add(s);
			}
		}
		dirty = true;
	}

	private static void end(Run r, long now) {
		if (r.startAt > now) {
			data.runs.remove(r);
			return;
		}
		r.endsAt = Math.min(r.endsAt, now);
	}

	/** Its fuels burning or waiting to, in order. */
	private static List<Run> live(Minion m, long now) {
		return data.runs.stream().filter(r -> r.minion.equals(m.key) && !r.early && r.endsAt > now)
				.sorted(Comparator.comparingLong(r -> r.startAt)).toList();
	}

	public static Run running(Minion m, long now) {
		for (Run r : data.runs) if (r.minion.equals(m.key) && r.running(now)) return r;
		return null;
	}

	/** When its last fuel runs out (0 when it has none left). */
	public static long fuelEnds(Minion m, long now) {
		List<Run> live = live(m, now);
		return live.isEmpty() ? 0 : live.get(live.size() - 1).endsAt;
	}

	/** What you collected: worth after tax, put on {@code run}, else the fuel burning then (or the last one in that minion). */
	private static void haul(Minion m, Map<String, Long> items, long coins, long now, Run run, boolean quiet) {
		Haul h = new Haul();
		h.at = now;
		h.minion = m == null ? "" : m.key;
		h.items = new LinkedHashMap<>(items);
		h.coins = coins;
		if (run == null && m != null) run = running(m, now);
		if (run == null && m != null) {
			run = data.runs.stream().filter(r -> r.minion.equals(m.key) && r.startAt <= now).max(Comparator.comparingLong(r -> r.startAt)).orElse(null);
		}
		h.run = run == null ? 0 : run.id;
		Taz.LOGGER.info("Inferno: collected {} and {} coins from {}", items, coins, h.minion);
		long v = value(items);
		h.value = v < 0 ? -1 : v + coins;
		data.hauls.add(h);
		dirty = true;
		if (quiet) return;
		InfernoConfig cfg = InfernoConfig.get();
		List<String> rare = new ArrayList<>();
		for (String id : List.of("GABAGOOL_THE_FISH", "INFERNO_APEX", "REAPER_PEPPER", "INFERNO_VERTEX")) {
			if (items.getOrDefault(id, 0L) > 0 && (!id.equals("INFERNO_VERTEX") || cfg.vertexAlert)) rare.add(id);
		}
		if (cfg.rareAlert && !rare.isEmpty()) {
			Alert.show(InfernoData.name(rare.get(0)) + "!", "§7from Inferno minion " + m.label(), Alert.Sound.PLING);
		}
		if (cfg.chat) {
			CHAT.add(h);
			CHAT_FROM.put(h, m);
			lastHaulAt = now;
		}
	}

	/** One chat line for what you just collected, from one minion or several. */
	private static void haulChat() {
		Map<String, Long> items = new LinkedHashMap<>();
		long coins = 0, value = 0;
		boolean priced = true;
		for (Haul h : CHAT) {
			h.items.forEach((k, v) -> items.merge(k, v, Long::sum));
			coins += h.coins;
			if (h.value < 0) priced = false;
			else value += h.value;
		}
		String from = CHAT.size() == 1 ? CHAT_FROM.get(CHAT.get(0)).label() : CHAT.size() + " minions";
		CHAT.clear();
		CHAT_FROM.clear();
		List<String> parts = new ArrayList<>();
		items.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.forEach(e -> parts.add("§f" + String.format(Locale.ROOT, "%,d", e.getValue()) + " " + InfernoData.name(e.getKey())));
		if (coins > 0) parts.add("§6" + coins(coins) + " coins");
		String worth = priced ? " §7· §6" + coins(value) : "";
		TazChat.send(TazTheme.styled("§7Inferno " + from + " collected: " + String.join("§7, ", parts) + worth).append(link()));
	}

	/** Items typed in ("33 chili, 1 vertex"), or null when something isn't an Inferno item. */
	public static Map<String, Long> parseItems(String text) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (String part : text.split("[,;]")) {
			if (part.isBlank()) continue;
			Matcher m = Pattern.compile("^\\s*([\\d.,]+\\s*[kK]?)\\s*x?\\s+(.+?)\\s*$").matcher(part);
			if (!m.matches()) return null;
			Long n = com.taz.general.AuctionInput.parse(m.group(1).replace(" ", ""));
			String id = product(m.group(2));
			if (n == null || n <= 0 || id == null) return null;
			out.merge(id, n, Long::sum);
		}
		return out.isEmpty() ? null : out;
	}

	/** The Inferno item a name means: the shortest one with every word ("chili" → Chili Pepper, "crude" → Crude Gabagool). */
	private static String product(String name) {
		String[] words = name.toLowerCase(Locale.ROOT).trim().split("\\s+");
		String best = null;
		for (String id : InfernoData.PRODUCTS.keySet()) {
			String plain = InfernoData.plain(id).toLowerCase(Locale.ROOT);
			boolean all = true;
			for (String w : words) {
				String one = w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w;
				if (!plain.contains(w) && !plain.contains(one)) all = false;
			}
			if (all && (best == null || plain.length() < InfernoData.plain(best).length())) best = id;
		}
		return best;
	}

	/** What you typed in as collected: on {@code run}, else shared between the fuels burning now. */
	public static void addCollected(Map<String, Long> items, Run run) {
		long now = System.currentTimeMillis();
		List<Run> on = run != null ? List.of(run) : data.runs.stream().filter(r -> r.running(now)).toList();
		if (on.isEmpty()) {
			haul(null, items, 0, now, null, true);
		} else {
			for (int i = 0; i < on.size(); i++) {
				Map<String, Long> share = new LinkedHashMap<>();
				for (Map.Entry<String, Long> e : items.entrySet()) {
					long n = e.getValue() / on.size() + (i < e.getValue() % on.size() ? 1 : 0);
					if (n > 0) share.put(e.getKey(), n);
				}
				if (!share.isEmpty()) haul(minionOf(on.get(i)), share, 0, now, on.get(i), true);
			}
		}
		save();
	}

	/** Coins these items sell for after tax, or -1 when a price is missing. */
	private static long value(Map<String, Long> items) {
		double total = 0;
		for (Map.Entry<String, Long> e : items.entrySet()) {
			double v = InfernoPrices.value(e.getKey());
			if (v < 0) return -1;
			total += v * e.getValue();
		}
		return Math.round(total);
	}

	// ---- speed and what a fuel should make

	/** How many Inferno minions you have and their tier, from the tab list's Minions widget (kept for when it isn't shown). */
	private static void readTab(Minecraft mc) {
		List<String> lines = testTab;
		if (lines == null) {
			lines = new ArrayList<>();
			if (mc.getConnection() == null) return;
			for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
				Component name = info.getTabListDisplayName();
				if (name != null) lines.add(TextUtil.cleanScoreboard(name.getString()).trim());
			}
		}
		int count = 0, tier = 0;
		String state = "";
		for (String line : lines) {
			Matcher m = TAB.matcher(line.trim());
			if (!m.matches()) continue;
			count += Integer.parseInt(m.group(1));
			tier = Math.max(tier, InfernoData.roman(m.group(2)));
			if (state.isEmpty() || !m.group(3).equals("ACTIVE")) state = m.group(3);
		}
		if (count == 0) return;
		tabState = state;
		if (count != data.tabCount || tier != data.tabTier) {
			data.tabCount = count;
			data.tabTier = tier;
			dirty = true;
		}
	}

	/** Your Inferno minions' tier: from the tab list, else your highest you've opened, else XI. */
	public static int tier() {
		if (data.tabTier > 0) return data.tabTier;
		Minion m = best();
		return m != null && m.tier > 0 ? m.tier : 11;
	}

	/** How many Inferno minions the tab list says you have, or 0 before Taz has seen it. */
	public static int tabCount() {
		return data.tabCount;
	}

	/** What the tab list says your Inferno minions are doing ("FULL" when their storage is), or "". */
	public static String tabState() {
		return tabState;
	}

	/** The speed boost of a minion: Rising Celsius, Mithril Infusion, its upgrades and the rest of your island. */
	public static double boost(Minion m) {
		InfernoConfig cfg = InfernoConfig.get();
		int opened = (int) data.minions.stream().filter(x -> !x.key.startsWith("?")).count();
		int placed = cfg.placed > 0 ? Math.round(cfg.placed) : Math.max(data.tabCount, opened);
		double b = Math.min(InfernoData.CELSIUS_MAX, Math.max(1, placed)) * InfernoData.CELSIUS + (cfg.infusion ? 0.1 : 0) + cfg.otherSpeed / 100.0;
		if (m != null) {
			for (String u : m.upgrades) {
				if (u.equals("FLYCATCHER_UPGRADE")) b += 0.2;
				else if (u.equals("MINION_EXPANDER")) b += 0.05;
			}
		}
		return b;
	}

	private static long expected(Run r, Minion m) {
		InfernoData.Fuel f = InfernoData.fuel(r.fuel);
		int tier = r.tier > 0 ? r.tier : tier();
		double boost = boost(m);
		r.harvests = InfernoData.harvests(tier, boost, f);
		double w = InfernoPrices.worth(InfernoData.makes(f, tier, boost, r.eyedrops > 0));
		return w < 0 ? -1 : Math.round(w);
	}

	/** What a fuel makes in a day in a minion like {@code m} (your best one), less nothing: -1 when a price is missing. */
	public static double perDay(InfernoData.Fuel f, Minion m) {
		int tier = m != null && m.tier > 0 ? m.tier : tier();
		return InfernoPrices.worth(InfernoData.makes(f, tier, boost(m), false));
	}

	/** Your highest-tier minion (the calculator's), or null. */
	public static Minion best() {
		return data.minions.stream().max(Comparator.comparingInt((Minion m) -> m.tier).thenComparingInt(m -> m.upgrades.size())).orElse(null);
	}

	/** Prices came in: what Taz couldn't value before. */
	private static void fillPrices() {
		for (Run r : data.runs) {
			if (r.expected < 0) {
				r.expected = expected(r, minion(r.minion));
				if (r.expected >= 0) dirty = true;
			}
		}
		for (Haul h : data.hauls) {
			if (h.value < 0) {
				long v = value(h.items);
				if (v >= 0) {
					h.value = v + h.coins;
					dirty = true;
				}
			}
		}
	}

	/** Fuel that ran out since Taz last looked: a title and a chat line. */
	private static void ranOut(long now) {
		InfernoConfig cfg = InfernoConfig.get();
		List<Minion> out = new ArrayList<>();
		for (Minion m : data.minions) {
			if (m.warned || m.key.startsWith("?")) continue;
			boolean hadFuel = data.runs.stream().anyMatch(r -> r.minion.equals(m.key));
			if (hadFuel && fuelEnds(m, now) == 0) {
				m.warned = true;
				out.add(m);
				dirty = true;
			}
		}
		if (out.isEmpty() || !cfg.emptyAlert) return;
		String which = out.size() == 1 ? "Inferno minion " + out.get(0).label() : out.size() + " Inferno minions";
		Alert.show("§cInferno fuel ran out", "§7" + which, Alert.Sound.PLING);
		TazChat.send(TazTheme.styled("§c" + which + (out.size() == 1 ? " is" : " are") + " out of fuel").append(link()));
	}

	// ---- for the menu

	public static boolean loaded() {
		return loadedFor != null && loadedFor.equals(account());
	}

	public static List<Run> runs() {
		return data.runs;
	}

	public static List<Minion> minions() {
		return data.minions;
	}

	public static List<Haul> hauls() {
		return data.hauls;
	}

	public static List<Purchase> bought() {
		return data.bought;
	}

	public static List<Stock> stock() {
		return data.stock;
	}

	public static List<Sale> sales() {
		return data.sales;
	}

	public static Minion minion(String key) {
		for (Minion m : data.minions) if (m.key.equals(key)) return m;
		Minion m = new Minion();
		m.key = key;
		m.number = data.minions.stream().mapToInt(x -> x.number).max().orElse(0) + 1;
		data.minions.add(m);
		dirty = true;
		return m;
	}

	public static Minion minionOf(Run r) {
		for (Minion m : data.minions) if (m.key.equals(r.minion)) return m;
		return null;
	}

	/** What you've collected for a run so far (-1 while a price is missing). */
	public static long made(Run r) {
		long total = 0;
		for (Haul h : data.hauls) {
			if (h.run != r.id) continue;
			if (h.value < 0) return -1;
			total += h.value;
		}
		return total;
	}

	/** The items collected for a run. */
	public static Map<String, Long> drops(Run r) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Haul h : data.hauls) if (h.run == r.id) h.items.forEach((k, v) -> out.merge(k, v, Long::sum));
		return out;
	}

	public record Stats(int fuels, long fuelCost, int eyedrops, long eyedropsCost, long expected, long made, long sold, long bought,
			int running, long runningProfit, Map<String, Long> drops, Map<String, Double> expectedDrops) {
		public long spent() {
			return fuelCost + eyedropsCost;
		}

		public long profit() {
			return made - spent();
		}
	}

	/** The numbers for fuels put in since {@code since}. */
	public static Stats stats(long since) {
		long now = System.currentTimeMillis();
		int fuels = 0, eyedrops = 0, running = 0;
		long fuelCost = 0, eyedropsCost = 0, expected = 0, made = 0, runningProfit = 0;
		Map<String, Long> drops = new LinkedHashMap<>();
		Map<String, Double> expectedDrops = new LinkedHashMap<>();
		for (Run r : data.runs) {
			if (r.placedAt < since) continue;
			fuels++;
			fuelCost += r.cost;
			eyedrops += r.eyedrops;
			eyedropsCost += r.eyedropsCost;
			if (r.expected > 0) expected += r.expectedNow(now);
			long m = made(r);
			if (m > 0) made += m;
			if (r.running(now) || r.queued(now)) {
				running++;
				if (r.expected > 0) runningProfit += r.expected - r.totalCost();
			}
			InfernoData.Fuel f = InfernoData.fuel(r.fuel);
			if (f != null && f.grade() == InfernoData.Grade.HYPERGOLIC) {
				double days = r.burned(now);
				for (InfernoData.Rare rare : InfernoData.RARES) {
					double n = r.harvests * days / rare.in() * (r.eyedrops > 0 && rare.boosted() ? InfernoData.EYEDROPS_BOOST : 1);
					if (rare.id().equals("INFERNO_APEX") && r.tier >= 10) n *= 2;
					expectedDrops.merge(rare.id(), n, Double::sum);
				}
			}
		}
		for (Haul h : data.hauls) {
			if (h.at < since) continue;
			h.items.forEach((k, v) -> drops.merge(k, v, Long::sum));
			if (h.run == 0 && h.value > 0) made += h.value;
		}
		long sold = data.sales.stream().filter(s -> s.at >= since).mapToLong(s -> s.coins).sum();
		long bought = data.bought.stream().filter(p -> p.at >= since).mapToLong(p -> p.coins).sum()
				+ data.stock.stream().filter(s -> s.at >= since && s.how.equals("Bought on the AH")).mapToLong(s -> s.cost).sum();
		return new Stats(fuels, fuelCost, eyedrops, eyedropsCost, expected, made, sold, bought, running, runningProfit, drops, expectedDrops);
	}

	// ---- changes from the menu

	public static void editCost(Run r, long cost) {
		r.cost = Math.max(0, cost);
		r.estimated = false;
		r.how = "Typed in";
		dirty = true;
		save();
	}

	public static void delete(Run r) {
		data.runs.remove(r);
		for (Haul h : data.hauls) if (h.run == r.id) h.run = 0;
		save();
	}

	public static void delete(Purchase p) {
		data.bought.remove(p);
		save();
	}

	public static void forget(Minion m) {
		data.minions.remove(m);
		data.runs.removeIf(r -> r.minion.equals(m.key) && (r.running(System.currentTimeMillis()) || r.queued(System.currentTimeMillis())));
		save();
	}

	/** The menu opened: your data, even with tracking off (the planner and Best fuel still work). */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (load() && mc.level != null) readTab(mc);
	}

	/** Prices for everything in the menu. */
	public static void refresh(boolean force) {
		InfernoPrices.want(InfernoPrices.all(), force);
	}

	/** On your private island (for the fuel HUD). */
	public static boolean onIsland() {
		return "dynamic".equals(SkyblockLocation.island());
	}

	// ---- chat bits

	private static Component link() {
		return Component.literal(" [Tracker]").withColor(TazTheme.LIGHT_RED & 0xFFFFFF)
				.withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/taz inferno"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the Inferno tracker"))));
	}

	private static String money(long coins) {
		return (coins < 0 ? "§c-" : "§a+") + coins(Math.abs(coins));
	}

	public static String coins(long coins) {
		return com.taz.xpassist.util.TextUtil.coins(coins, 2);
	}

	private static long num(String s) {
		try {
			return Math.round(Double.parseDouble(s.replace(",", "")));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---- saving

	private static String account() {
		String test = testAccount;
		return test != null ? test : String.valueOf(Minecraft.getInstance().getUser().getProfileId());
	}

	private static Path file() {
		return Taz.CONFIG_DIR.resolve("inferno").resolve(account() + ".json");
	}

	private static boolean load() {
		String who = account();
		if (who.equals(loadedFor)) return true;
		if (Minecraft.getInstance().level == null && testAccount == null) return false;
		loadedFor = who;
		data = new Data();
		try {
			Path f = file();
			if (Files.exists(f)) {
				Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read the Inferno tracker", e);
		}
		if (data.bought == null) data.bought = new ArrayList<>();
		if (data.stock == null) data.stock = new ArrayList<>();
		if (data.runs == null) data.runs = new ArrayList<>();
		if (data.minions == null) data.minions = new ArrayList<>();
		if (data.hauls == null) data.hauls = new ArrayList<>();
		if (data.sales == null) data.sales = new ArrayList<>();
		return true;
	}

	public static void save() {
		savedAt = System.currentTimeMillis();
		if (loadedFor == null) return;
		dirty = false;
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Taz.LOGGER.error("Couldn't save the Inferno tracker", e);
		}
	}

	/** Test hook: start over (the next tick loads from the file again). */
	public static void reload() {
		loadedFor = null;
		data = new Data();
		lastFuels = null;
		minionScreen = null;
		open = null;
		clickedKey = null;
		eyedropsKey = null;
		load();
	}

	/** Test hook: a right-click on the minion at this spot. */
	public static void clicked(BlockPos pos) {
		clickedKey = key(pos);
		clickedAt = System.currentTimeMillis();
	}

	/** Test hook: a click in the open menu. */
	public static void menuClicked() {
		menuClickAt = System.currentTimeMillis();
	}
}
