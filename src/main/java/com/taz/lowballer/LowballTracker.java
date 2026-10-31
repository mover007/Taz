package com.taz.lowballer;

import com.mojang.serialization.DynamicOps;
import com.taz.Taz;
import com.taz.dungeons.util.TextUtil;
import com.taz.hud.TazTheme;
import com.taz.util.Items;
import com.taz.util.TazChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lowball Tracker: every trade where you get items, with what you paid and what each item was worth, and then what it
 * sold for when it goes on the AH, the bazaar, to an NPC or out in another trade. What a listed lowball is on the AH for
 * (and whether it sold or ended while you were away) comes from SkyCofl's copy of your auctions. Saved per account in
 * config/taz/lowballer/&lt;uuid&gt;.nbt; {@link TrackerScreen} (/taz lowballs) shows it.
 */
public final class LowballTracker {
	private static final Pattern COMPLETED = Pattern.compile("^Trade completed with (?:\\[[^\\]]+\\] )?(\\w+)!$");
	/** The lines after it: "+ 1x Hyperion" you got, "- 800M coins" you gave. */
	private static final Pattern SUMMARY = Pattern.compile("^([+-]) (.+)$");
	private static final Pattern STARTED = Pattern.compile("^(BIN )?Auction started for (.+)!$");
	private static final Pattern AH_BOUGHT = Pattern.compile("^\\[Auction] (\\w+) bought (.+) for ([\\d,]+) coins(?: CLICK)?$");
	private static final Pattern AH_COLLECTED = Pattern.compile("^You collected ([\\d,]+) coins from selling (.+) to (?:\\[[^\\]]+\\] )?(\\w+) in an auction!$");
	private static final Pattern BZ_SOLD = Pattern.compile("^\\[Bazaar] Sold ([\\d,]+)x (.+) for ([\\d,.]+) coins!$");
	private static final Pattern BZ_CLAIMED = Pattern.compile("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+) at ([\\d,.]+) each!$");
	private static final Pattern NPC_SOLD = Pattern.compile("^You sold (.+) x([\\d,]+) for ([\\d,]+) Coins?!$");
	private static final Pattern STARS = Pattern.compile("[✪➊➋➌➍➎]");
	private static final Set<String> CREATE = Set.of("Create BIN Auction", "Create Auction");
	/** The trade's lines come right after "Trade completed". */
	private static final long SUMMARY_MS = 1000;
	/** The trade menu seen this recently is the trade that just went through. */
	private static final long MENU_MS = 20_000;
	/** "Auction started" this long after the create menu is about the item in it. */
	private static final long LISTING_MS = 120_000;
	/** Worth now is asked again after this long. */
	private static final long REPRICE_MS = 10 * 60_000L;
	/** SkyCofl estimates at most 90 items a request. */
	private static final int CHUNK = 90;
	/** Your auctions are asked for this long after you list a lowball (SkyCofl sees new auctions within a minute or so)... */
	private static final long LISTED_SYNC_MS = 90_000;
	/** ...and when the menu opens, at most this often. */
	private static final long SYNC_MS = 2 * 60_000L;
	/** A stackable's auction is the one that started this close to when you listed it. */
	private static final long MATCH_MS = 10 * 60_000L;
	private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Taz lowball tracker");
		t.setDaemon(true);
		return t;
	});

	private static final List<Flip> FLIPS = new ArrayList<>();
	private static String loadedFor;
	private static TradeMenu.Trade last;
	private static long lastAt;
	private static Done done;
	private static ItemStack listing;
	private static long listingAt;
	private static volatile boolean pricing, syncing;
	private static volatile String problem;
	private static long lastSync, syncAt;
	/** The item and start of auctions SkyCofl has told us about (they don't change). */
	private static final Map<String, Cofl.AuctionItem> AUCTIONS = new ConcurrentHashMap<>();
	/** Test hook: the account to save under instead of yours. */
	public static volatile String testAccount;

	/** A trade that went through, waiting for its "+"/"-" lines. */
	private static final class Done {
		final TradeMenu.Trade trade;
		final String other;
		final long at;
		long got, given;
		boolean summary;

		Done(TradeMenu.Trade trade, String other, long at) {
			this.trade = trade;
			this.other = other;
			this.at = at;
		}
	}

	private LowballTracker() {
	}

	private static boolean on() {
		LowballerConfig cfg = LowballerConfig.get();
		return cfg.enabled && cfg.track;
	}

	/** The trade menu as it is this tick (TradeMenu). */
	static void seen(TradeMenu.Trade trade) {
		last = trade;
		lastAt = System.currentTimeMillis();
	}

	/** Every client tick: records a trade once its lines are in, and notes the item in the Create Auction menu. */
	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (done != null && now - done.at >= SUMMARY_MS) {
			Done d = done;
			done = null;
			if (on()) record(d, now);
		}
		if (!on()) return;
		if (syncAt > 0 && now >= syncAt) {
			syncAt = 0;
			syncListings(true);
		}
		if (mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu().slots.size() > 13
				&& CREATE.contains(TextUtil.cleanChat(s.getTitle().getString()))) {
			ItemStack item = s.getMenu().getSlot(13).getItem();
			if (!Items.id(item).isEmpty()) {
				listing = item.copy();
				listingAt = now;
			}
		}
	}

	/** A chat line from the server. */
	public static void onChat(String text) {
		if (!on()) return;
		for (String line : text.split("\n")) line(line.trim());
	}

	private static void line(String line) {
		long now = System.currentTimeMillis();
		Matcher m = COMPLETED.matcher(line);
		if (m.matches()) {
			if (done != null) record(done, now);
			TradeMenu.Trade t = last;
			done = t != null && now - lastAt < MENU_MS && trader(t.other(), m.group(1)) ? new Done(t, m.group(1), now) : null;
			last = null;
			return;
		}
		if (done != null && (m = SUMMARY.matcher(line)).matches()) {
			Long coins = TradeMenu.coins(m.group(2));
			done.summary = true;
			if (coins != null && m.group(1).equals("+")) done.got += coins;
			else if (coins != null) done.given += coins;
			return;
		}
		if ((m = STARTED.matcher(line)).matches()) {
			listed(m.group(2), m.group(1) != null, now);
		} else if ((m = AH_BOUGHT.matcher(line)).matches()) {
			Flip f = forAuction(m.group(2));
			if (f == null) return;
			long price = num(m.group(3));
			f.sell(price, Math.round(price * Lowball.tax(price, false)), f.bin ? "BIN" : "Auction", m.group(1), now);
			sold(List.of(f));
		} else if ((m = AH_COLLECTED.matcher(line)).matches()) {
			collected(num(m.group(1)), m.group(2), m.group(3), now);
		} else if ((m = BZ_SOLD.matcher(line)).matches()) {
			sell(m.group(2), (int) num(m.group(1)), num(m.group(3)), 0, "Bazaar", now);
		} else if ((m = BZ_CLAIMED.matcher(line)).matches()) {
			int n = (int) num(m.group(2));
			long got = num(m.group(1)), gross = Math.max(got, Math.round(dec(m.group(4)) * n));
			sell(m.group(3), n, gross, gross - got, "Bazaar", now);
		} else if ((m = NPC_SOLD.matcher(line)).matches()) {
			sell(m.group(1), (int) num(m.group(2)), num(m.group(3)), 0, "NPC", now);
		}
	}

	// ---- trades

	/**
	 * What went through: their items are lowballs, costing what you gave (your coins less theirs, plus what your items
	 * were worth), split by their worth. Lowballs of yours that you gave are sold for what you got, split the same way.
	 */
	private static void record(Done d, long now) {
		if (!load()) return;
		TradeMenu.Trade t = d.trade;
		long given = d.summary ? d.given : t.yourCoins(), got = d.summary ? d.got : t.coins();
		List<TradeMenu.Item> theirs = t.items(), yours = t.yourItems();

		// Your side: which stacks are lowballs being sold, and what every stack was worth.
		List<List<Flip>> gone = new ArrayList<>();
		long[] yourWorth = new long[yours.size()];
		long yourTotal = 0;
		List<Flip> taken = new ArrayList<>();
		for (int k = 0; k < yours.size(); k++) {
			TradeMenu.Item i = yours.get(k);
			List<Flip> flips = take(i.stack(), taken);
			taken.addAll(flips);
			gone.add(flips);
			long flipWorth = flips.stream().mapToLong(Flip::worthLatest).sum();
			yourWorth[k] = i.priced() != null ? i.priced().worth() : flipWorth;
			yourTotal += yourWorth[k];
		}

		List<Flip> added = new ArrayList<>();
		if (!theirs.isEmpty() && (given > 0 || !yours.isEmpty())) {
			long cost = Math.max(0, given - got + yourTotal);
			long[] paid = shares(cost, worths(theirs));
			for (int k = 0; k < theirs.size(); k++) {
				TradeMenu.Item i = theirs.get(k);
				Flip f = new Flip();
				f.id = now * 100 + k;
				f.boughtAt = now;
				f.from = d.other;
				f.stack = i.stack().copy();
				f.name = name(i.stack());
				f.uuid = uuid(i.stack());
				f.count = i.stack().getCount();
				f.paid = paid[k];
				f.tradeItems = theirs.size();
				f.tradePaid = cost;
				if (i.priced() != null) {
					f.worth = i.priced().worth();
					f.volume = i.priced().volume();
					f.bazaar = i.priced().bazaar();
				}
				added.add(f);
			}
			FLIPS.addAll(added);
		}

		List<Flip> sold = new ArrayList<>();
		if (!taken.isEmpty()) {
			long proceeds = Math.max(0, got - given + t.itemsWorth());
			long[] share = shares(proceeds, yourWorth);
			for (int k = 0; k < yours.size(); k++) {
				List<Flip> flips = gone.get(k);
				long[] each = shares(share[k], flips.stream().mapToLong(f -> Math.max(1, f.worthLatest())).toArray());
				for (int j = 0; j < flips.size(); j++) {
					flips.get(j).sell(each[j], 0, "Trade", d.other, now);
					sold.add(flips.get(j));
				}
			}
		}
		if (added.isEmpty() && sold.isEmpty()) return;
		save();
		if (!added.isEmpty()) bought(added);
		if (!sold.isEmpty()) tell(sold);
		if (added.stream().anyMatch(f -> f.worth <= 0)) refresh(true);
	}

	/** The worth of each stack (0 without a price, so it costs nothing until its price comes in and the cost is split again). */
	private static long[] worths(List<TradeMenu.Item> items) {
		return items.stream().mapToLong(i -> i.priced() == null ? 0 : Math.max(0, i.priced().worth())).toArray();
	}

	/** {@code total} split by weight; the last one gets what rounding leaves. */
	static long[] shares(long total, long[] weights) {
		long[] out = new long[weights.length];
		long sum = 0, given = 0;
		for (long w : weights) sum += Math.max(0, w);
		for (int i = 0; i < weights.length; i++) {
			if (i == weights.length - 1) out[i] = total - given;
			else out[i] = sum > 0 ? Math.round(total * (Math.max(0, weights[i]) / (double) sum)) : total / weights.length;
			given += out[i];
		}
		return out;
	}

	/** The open lowballs a stack you gave away is: the one with its uuid, or for stackables as many as it holds. */
	private static List<Flip> take(ItemStack stack, List<Flip> taken) {
		String id = uuid(stack);
		if (!id.isEmpty()) {
			Flip f = first(x -> x.open() && id.equals(x.uuid) && !taken.contains(x));
			return f == null ? List.of() : List.of(f);
		}
		return portion(name(stack), stack.getCount(), x -> x.open() && x.uuid.isEmpty() && !taken.contains(x));
	}

	// ---- sales

	/** "BIN Auction started for X!": the item in the Create Auction menu is listed. */
	private static void listed(String name, boolean bin, long now) {
		ItemStack item = listing;
		listing = null;
		if (item == null || now - listingAt > LISTING_MS) return;
		String id = uuid(item);
		Flip f = id.isEmpty() ? first(x -> x.status == Flip.Status.HOLDING && x.uuid.isEmpty() && same(x.name, name(item)))
				: first(x -> x.open() && id.equals(x.uuid));
		if (f == null) return;
		f.status = Flip.Status.LISTED;
		f.listedAs = name;
		f.listedAt = now;
		f.bin = bin;
		f.listPrice = 0;
		syncAt = now + LISTED_SYNC_MS;
		save();
	}

	/** The lowball an auction sale is about: the one listed under that name, else a stackable by that name. */
	private static Flip forAuction(String name) {
		Flip f = first(x -> x.status == Flip.Status.LISTED && x.listedAs.equals(name));
		return f != null ? f : first(x -> x.status == Flip.Status.HOLDING && x.uuid.isEmpty() && same(x.name, name));
	}

	/** "You collected": the exact claim tax for a sale seen, or the sale itself when it sold while you were away. */
	private static void collected(long got, String name, String buyer, long now) {
		Flip f = first(x -> x.status == Flip.Status.SOLD && !x.collected && (x.soldHow.equals("BIN") || x.soldHow.equals("Auction"))
				&& (x.soldTo.isEmpty() || x.soldTo.equalsIgnoreCase(buyer))
				&& (x.listedAs.equals(name) || same(x.name, name)));
		if (f != null) {
			f.fees = Math.round(f.sold * Lowball.listingFee(f.sold)) + Math.max(0, f.sold - got);
			f.collected = true;
			f.soldTo = buyer;
			save();
			return;
		}
		f = forAuction(name);
		if (f == null) return;
		long price = got / 0.99 > 1_000_000 ? Math.round(got / 0.99) : got;
		f.sell(price, Math.round(price * Lowball.listingFee(price)) + price - got, f.bin ? "BIN" : "Auction", buyer, now);
		f.collected = true;
		sold(List.of(f));
	}

	/** {@code n} of an item sold on the bazaar or to an NPC for {@code total}: the lowballs among them, oldest first. */
	private static void sell(String name, int n, long total, long fees, String how, long now) {
		if (n <= 0) return;
		List<Flip> flips = portion(name, n, x -> x.status == Flip.Status.HOLDING);
		for (Flip f : flips) f.sell(total * f.count / n, fees * f.count / n, how, "", now);
		if (!flips.isEmpty()) sold(flips);
	}

	/** Up to {@code n} of an item from the lowballs that pass {@code which}, oldest first, splitting the last stack if needed. */
	private static List<Flip> portion(String name, int n, Predicate<Flip> which) {
		List<Flip> out = new ArrayList<>();
		int left = n;
		for (Flip f : byAge()) {
			if (left <= 0) break;
			if (!which.test(f) || !same(f.name, name)) continue;
			if (f.count > left) {
				Flip part = f.split(left);
				FLIPS.add(FLIPS.indexOf(f), part);
				f = part;
			}
			out.add(f);
			left -= f.count;
		}
		return out;
	}

	private static void sold(List<Flip> flips) {
		save();
		tell(flips);
	}

	// ---- chat

	private static void bought(List<Flip> added) {
		if (!LowballerConfig.get().trackerChat) return;
		long paid = added.stream().mapToLong(f -> f.paid).sum(), worth = added.stream().mapToLong(f -> f.worth).sum();
		String what = added.size() == 1 ? "§f" + label(added.get(0)) : "§f" + added.size() + " items";
		String value = worth > 0 ? " §8(worth " + coins(worth) + ", " + Lowball.pct(1 - paid / (double) worth) + " under)" : "";
		TazChat.send(TazTheme.styled("§7Lowball tracked: " + what + " §7from §f" + added.get(0).from + " §7for §6" + coins(paid) + value).append(open()));
	}

	private static void tell(List<Flip> sold) {
		if (!LowballerConfig.get().trackerChat) return;
		for (Flip f : sold) {
			long profit = f.profit();
			String pct = f.paid > 0 && Math.abs(profit) <= 100.0 * f.paid ? " (" + (profit >= 0 ? "+" : "") + Lowball.pct(profit / (double) f.paid) + ")" : "";
			TazChat.send(TazTheme.styled("§7Lowball sold: §f" + label(f) + " §7for §6" + coins(f.sold) + "§7: " + (profit >= 0 ? "§a+" : "§c-")
					+ coins(Math.abs(profit)) + " profit" + pct + " §8in " + span(f.soldAt - f.boughtAt)).append(open()));
		}
	}

	/** Lowballs you just listed, once SkyCofl says what for. */
	private static void onAh(List<Flip> listed) {
		if (!LowballerConfig.get().trackerChat) return;
		for (Flip f : listed) {
			long profit = f.expectedProfit();
			TazChat.send(TazTheme.styled("§7Lowball on the AH: §f" + label(f) + " §7for §6" + coins(f.listPrice) + "§7: " + (profit >= 0 ? "§a+" : "§c-")
					+ coins(Math.abs(profit)) + " §7if it sells §8(after tax)").append(open()));
		}
	}

	private static MutableComponent open() {
		return Component.literal(" [Tracker]").withStyle(s -> s.withColor(TazTheme.RED & 0xFFFFFF)
				.withClickEvent(new ClickEvent.RunCommand("/taz lowballs"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the Lowball Tracker"))));
	}

	// ---- the menu's actions

	/** Every lowball, newest first. */
	public static List<Flip> flips() {
		load();
		List<Flip> out = new ArrayList<>(FLIPS);
		out.sort(Comparator.comparingLong((Flip f) -> f.boughtAt).thenComparingLong(f -> f.id).reversed());
		return out;
	}

	/** Whether your lowballs could be read (they need a world to read the items). */
	public static boolean loaded() {
		return load();
	}

	public static void sellByHand(Flip f, long price) {
		f.sell(price, 0, "By hand", "", System.currentTimeMillis());
		save();
	}

	public static void unsell(Flip f) {
		f.unsell();
		save();
	}

	/**
	 * You paid {@code paid} for this one. The rest of what you gave in that trade is split again between its other items
	 * (by worth), and a late price won't split it again.
	 */
	public static void editPaid(Flip f, long paid) {
		List<Flip> trade = FLIPS.stream().filter(x -> x.boughtAt == f.boughtAt && x.from.equals(f.from)).toList();
		f.paid = Math.max(0, paid);
		f.manualPaid = true;
		long fixed = trade.stream().filter(x -> x.manualPaid).mapToLong(x -> x.paid).sum();
		List<Flip> rest = trade.stream().filter(x -> !x.manualPaid).toList();
		long total = rest.isEmpty() ? fixed : Math.max(f.tradePaid, fixed);
		long[] share = shares(total - fixed, rest.stream().mapToLong(x -> Math.max(1, x.worth > 0 ? x.worth : x.paid)).toArray());
		for (int i = 0; i < rest.size(); i++) rest.get(i).paid = share[i];
		for (Flip x : trade) x.tradePaid = total;
		save();
	}

	/** You're keeping it: it's left out of the profit and what's unsold. */
	public static void keep(Flip f, boolean keep) {
		if (keep) f.status = Flip.Status.KEPT;
		else f.unsell();
		save();
	}

	public static void delete(Flip f) {
		FLIPS.remove(f);
		save();
	}

	public static boolean pricing() {
		return pricing || syncing;
	}

	public static String problem() {
		return problem;
	}

	/** Asks SkyCofl what the lowballs you still have are worth now (all of them when forced, else ones not asked lately). */
	public static void refresh(boolean force) {
		if (pricing || !load()) return;
		long now = System.currentTimeMillis();
		List<Flip> todo = FLIPS.stream().filter(f -> (f.open() || f.worth <= 0) && !f.stack.isEmpty()
				&& (force || now - f.worthNowAt > REPRICE_MS)).toList();
		if (todo.isEmpty()) return;
		pricing = true;
		List<ItemStack> stacks = todo.stream().map(f -> f.stack.copy()).toList();
		LowballerConfig.Worth basis = LowballerConfig.get().worth;
		WORKER.execute(() -> {
			long[] worth = new long[stacks.size()];
			String trouble = null;
			try {
				for (int from = 0; from < stacks.size(); from += CHUNK) {
					List<ItemStack> chunk = stacks.subList(from, Math.min(stacks.size(), from + CHUNK));
					List<Cofl.Estimate> est = Cofl.estimate(chunk);
					for (int i = 0; i < chunk.size(); i++) {
						Lowball.Priced p = est.get(i) != null ? TradeMenu.priced(est.get(i), basis) : TradeMenu.latest(chunk.get(i), true);
						worth[from + i] = p == null ? 0 : p.worth();
					}
				}
			} catch (Cofl.Busy busy) {
				trouble = "SkyCofl is busy: try again in " + busy.seconds + "s";
			} catch (Exception e) {
				Taz.LOGGER.warn("Lowball tracker: pricing failed ({})", e.toString());
				trouble = "Couldn't reach SkyCofl";
			}
			String why = trouble;
			Minecraft.getInstance().execute(() -> {
				long at = System.currentTimeMillis();
				List<Flip> filled = new ArrayList<>();
				for (int i = 0; i < todo.size(); i++) {
					Flip f = todo.get(i);
					if (worth[i] <= 0) continue;
					if (f.open()) {
						f.worthNow = worth[i];
						f.worthNowAt = at;
					}
					if (f.worth <= 0) {
						f.worth = worth[i];
						filled.add(f);
					}
				}
				for (Flip f : filled) resplit(f);
				problem = why;
				pricing = false;
				save();
			});
		});
	}

	/** A trade's worth came in late: its cost is split again by worth, when none of it was split, sold or typed in since. */
	private static void resplit(Flip one) {
		List<Flip> trade = FLIPS.stream().filter(f -> f.boughtAt == one.boughtAt && f.from.equals(one.from)).toList();
		if (trade.size() != one.tradeItems || trade.stream().mapToLong(f -> f.paid).sum() != one.tradePaid
				|| trade.stream().anyMatch(f -> f.worth <= 0 || f.status == Flip.Status.SOLD || f.manualPaid)) return;
		long[] paid = shares(one.tradePaid, trade.stream().mapToLong(f -> f.worth).toArray());
		for (int i = 0; i < trade.size(); i++) trade.get(i).paid = paid[i];
	}

	/**
	 * Asks SkyCofl for your auctions: what each lowball on the AH is listed for, and whether it sold or ended while Taz
	 * wasn't looking (forced, or at most every 2 minutes).
	 */
	public static void syncListings(boolean force) {
		if (syncing || !load()) return;
		long now = System.currentTimeMillis();
		if (!force && now - lastSync < SYNC_MS) return;
		List<Flip> listed = FLIPS.stream().filter(f -> f.status == Flip.Status.LISTED).toList();
		if (listed.isEmpty()) return;
		syncing = true;
		lastSync = now;
		String player = account();
		List<String> names = listed.stream().map(f -> f.listedAs).toList(), uuids = listed.stream().map(f -> f.uuid).toList();
		List<Long> times = listed.stream().map(f -> f.listedAt).toList();
		WORKER.execute(() -> {
			Map<Integer, Cofl.Auction> found = new HashMap<>();
			String trouble = null;
			try {
				Set<String> used = new HashSet<>();
				for (int page = 0; page < 3 && found.size() < listed.size(); page++) {
					List<Cofl.Auction> auctions = Cofl.playerAuctions(player, page);
					for (int i = 0; i < listed.size(); i++) {
						if (found.containsKey(i)) continue;
						Cofl.Auction best = null;
						long gap = Long.MAX_VALUE;
						for (Cofl.Auction a : auctions) {
							if (used.contains(a.id()) || !a.name().equals(names.get(i))) continue;
							Cofl.AuctionItem item = AUCTIONS.get(a.id());
							if (item == null) {
								item = Cofl.auctionItem(a.id());
								if (item == null) continue;
								AUCTIONS.put(a.id(), item);
							}
							long g = Math.abs(item.start() - times.get(i));
							// The same item by uuid (its latest listing), else a stackable listed at about that time.
							boolean match = !uuids.get(i).isEmpty() && !item.uuid().isEmpty() ? uuids.get(i).equals(item.uuid()) : g <= MATCH_MS;
							if (match && g < gap) {
								best = a;
								gap = g;
							}
						}
						if (best != null) {
							found.put(i, best);
							used.add(best.id());
						}
					}
					if (auctions.size() < 10) break;
				}
			} catch (Cofl.Busy busy) {
				trouble = "SkyCofl is busy: try again in " + busy.seconds + "s";
			} catch (Exception e) {
				Taz.LOGGER.warn("Lowball tracker: couldn't read your auctions ({})", e.toString());
				trouble = "Couldn't reach SkyCofl";
			}
			String why = trouble;
			Minecraft.getInstance().execute(() -> {
				syncing = false;
				if (why != null) problem = why;
				applyAuctions(listed, found);
			});
		});
	}

	/** What SkyCofl said about your listed lowballs: their price, and sold or ended ones. */
	private static void applyAuctions(List<Flip> listed, Map<Integer, Cofl.Auction> found) {
		long now = System.currentTimeMillis();
		List<Flip> sold = new ArrayList<>(), priced = new ArrayList<>();
		for (Map.Entry<Integer, Cofl.Auction> e : found.entrySet()) {
			Flip f = listed.get(e.getKey());
			Cofl.Auction a = e.getValue();
			if (f.status != Flip.Status.LISTED) continue;
			boolean fresh = f.listPrice <= 0;
			f.listPrice = a.price();
			f.bin = a.bin();
			if (a.end() > 0 && a.end() <= now) {
				if (a.highestBid() > 0) {
					f.sell(a.highestBid(), Math.round(a.highestBid() * Lowball.tax(a.highestBid(), false)), f.bin ? "BIN" : "Auction", "", a.end());
					sold.add(f);
				} else { // ended or cancelled with no buyer: back in your hands
					f.status = Flip.Status.HOLDING;
					f.listedAt = 0;
					f.listedAs = "";
				}
			} else if (fresh && now - f.listedAt < 30 * 60_000L) {
				priced.add(f);
			}
		}
		if (found.isEmpty()) return;
		save();
		tell(sold);
		onAh(priced);
	}

	// ---- stats

	/**
	 * The numbers for the tracker menu: sales since {@code since} (0: ever), lowballs bought since then, and what's unsold
	 * now (how many, how many on the AH, what you paid, what they're for sale at, and the profit if they sell at that).
	 */
	public record Stats(int sold, int wins, long profit, long soldPaid, long heldMs, Flip best, Flip worst, int bought, long spent,
			double under, int unsold, int listed, long unsoldPaid, long unsoldPrice, long unsoldProfit) {
		public double winRate() {
			return sold == 0 ? 0 : wins / (double) sold;
		}

		public double roi() {
			return soldPaid <= 0 ? 0 : profit / (double) soldPaid;
		}
	}

	public static Stats stats(long since) {
		int sold = 0, wins = 0, bought = 0, underN = 0, unsold = 0, listed = 0;
		long profit = 0, soldPaid = 0, held = 0, spent = 0, uPaid = 0, uPrice = 0, uProfit = 0;
		double under = 0;
		Flip best = null, worst = null;
		for (Flip f : flips()) {
			if (f.status == Flip.Status.SOLD && f.soldAt >= since) {
				sold++;
				profit += f.profit();
				soldPaid += f.paid;
				held += Math.max(0, f.soldAt - f.boughtAt);
				if (f.profit() > 0) wins++;
				if (best == null || f.profit() > best.profit()) best = f;
				if (worst == null || f.profit() < worst.profit()) worst = f;
			}
			if (f.boughtAt >= since) {
				bought++;
				spent += f.paid;
				if (f.worth > 0) {
					under += f.under();
					underN++;
				}
			}
			if (f.open()) {
				unsold++;
				if (f.status == Flip.Status.LISTED) listed++;
				uPaid += f.paid;
				if (f.askingPrice() > 0) {
					uPrice += f.askingPrice();
					uProfit += f.expectedProfit();
				}
			}
		}
		return new Stats(sold, wins, profit, soldPaid, sold == 0 ? 0 : held / sold, best, worst, bought, spent,
				underN == 0 ? Double.NaN : under / underN, unsold, listed, uPaid, uPrice, uProfit);
	}

	// ---- export

	/** Every lowball as a spreadsheet (config/taz/lowballer/lowballs.csv). */
	public static Path exportCsv() throws IOException {
		StringBuilder csv = new StringBuilder("Bought,From,Item,Count,Paid,Worth then,Status,Listed for,Sold for,Tax and fees,Sold,Sold how,Sold to,"
				+ "Profit,Return %,Held (hours)\n");
		for (Flip f : flips()) {
			boolean sold = f.status == Flip.Status.SOLD;
			String[] cells = {when(f.boughtAt), f.from, f.name, String.valueOf(f.count), String.valueOf(f.paid), f.worth > 0 ? String.valueOf(f.worth) : "",
					statusName(f), f.listPrice > 0 ? String.valueOf(f.listPrice) : "", sold ? String.valueOf(f.sold) : "", sold ? String.valueOf(f.fees) : "",
					sold ? when(f.soldAt) : "", f.soldHow, f.soldTo, sold ? String.valueOf(f.profit()) : "",
					sold && f.paid > 0 ? String.format(Locale.ROOT, "%.1f", 100.0 * f.profit() / f.paid) : "",
					sold ? String.format(Locale.ROOT, "%.1f", (f.soldAt - f.boughtAt) / 3_600_000.0) : ""};
			for (int i = 0; i < cells.length; i++) {
				if (i > 0) csv.append(',');
				String c = cells[i];
				csv.append(c.contains(",") || c.contains("\"") ? "\"" + c.replace("\"", "\"\"") + "\"" : c);
			}
			csv.append('\n');
		}
		Path out = Taz.CONFIG_DIR.resolve("lowballer").resolve("lowballs.csv");
		Files.createDirectories(out.getParent());
		Files.writeString(out, csv);
		return out;
	}

	private static String when(long ms) {
		return CSV_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
	}

	/** "Not listed", "On AH", "Sold", "Kept". */
	public static String statusName(Flip f) {
		return switch (f.status) {
			case HOLDING -> "Not listed";
			case LISTED -> "On AH";
			case SOLD -> "Sold";
			case KEPT -> "Kept";
		};
	}

	// ---- helpers

	private static Flip first(Predicate<Flip> which) {
		for (Flip f : byAge()) if (which.test(f)) return f;
		return null;
	}

	private static List<Flip> byAge() {
		load();
		List<Flip> out = new ArrayList<>(FLIPS);
		out.sort(Comparator.comparingLong((Flip f) -> f.boughtAt).thenComparingLong(f -> f.id));
		return out;
	}

	/** The same item by name, ignoring stars (the chat lines and the item's name can differ in those). */
	/** The trade menu was with {@code name}: its title cuts long names short ("villagersen" for villagersenpai). */
	private static boolean trader(String menu, String name) {
		String cut = menu.replaceAll("\\W+$", "").toLowerCase(Locale.ROOT);
		return !cut.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(cut);
	}

	private static boolean same(String a, String b) {
		return a.equals(b) || STARS.matcher(a).replaceAll("").trim().equals(STARS.matcher(b).replaceAll("").trim());
	}

	static String name(ItemStack stack) {
		return TextUtil.cleanChat(stack.getHoverName().getString());
	}

	static String uuid(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr("uuid", "");
	}

	/** "Hyperion", "64x Enchanted Diamond". */
	static String label(Flip f) {
		return (f.count > 1 ? f.count + "x " : "") + f.name;
	}

	static String coins(long coins) {
		return TradeHud.coins(coins);
	}

	/** 45s, 12m, 3h 20m, 2d 4h. */
	public static String span(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s < 60) return s + "s";
		long m = s / 60;
		if (m < 60) return m + "m";
		long h = m / 60;
		if (h < 24) return h + "h" + (m % 60 > 0 ? " " + m % 60 + "m" : "");
		long d = h / 24;
		return d + "d" + (h % 24 > 0 ? " " + h % 24 + "h" : "");
	}

	private static long num(String s) {
		return Math.round(dec(s));
	}

	private static double dec(String s) {
		try {
			return Double.parseDouble(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---- saving (config/taz/lowballer/<your uuid>.nbt)

	private static String account() {
		String test = testAccount;
		return test != null ? test : String.valueOf(Minecraft.getInstance().getUser().getProfileId());
	}

	private static Path file() {
		return Taz.CONFIG_DIR.resolve("lowballer").resolve(account() + ".nbt");
	}

	private static DynamicOps<Tag> ops() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? NbtOps.INSTANCE : mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	/** Reads your lowballs once per account; items need a world to be read, so until then this is false. */
	private static boolean load() {
		String who = account();
		if (who.equals(loadedFor)) return true;
		if (Minecraft.getInstance().level == null) return false;
		loadedFor = who;
		FLIPS.clear();
		try {
			Path f = file();
			if (!Files.exists(f)) return true;
			CompoundTag root = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
			for (Tag t : root.getListOrEmpty("flips")) {
				if (!(t instanceof CompoundTag c)) continue;
				Flip flip = new Flip();
				flip.load(c);
				if (flip.stackTag != null) flip.stack = ItemStack.OPTIONAL_CODEC.parse(ops(), flip.stackTag).result().orElse(ItemStack.EMPTY);
				FLIPS.add(flip);
			}
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't read the Lowball Tracker's lowballs", e);
		}
		return true;
	}

	private static void save() {
		if (loadedFor == null) return;
		ListTag list = new ListTag();
		DynamicOps<Tag> ops = ops();
		for (Flip f : FLIPS) list.add(f.save(ops));
		CompoundTag root = new CompoundTag();
		root.put("flips", list);
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			NbtIo.writeCompressed(root, f);
		} catch (Exception e) {
			Taz.LOGGER.error("Couldn't save the Lowball Tracker's lowballs", e);
		}
	}

	/** Test hook: read the file again next time. */
	public static void reload() {
		loadedFor = null;
	}

	/** Test hook: forget everything, on disk too, and read the file again next time. */
	public static void reset() {
		load();
		FLIPS.clear();
		save();
		loadedFor = null;
		last = null;
		done = null;
		listing = null;
		problem = null;
		syncAt = lastSync = 0;
		AUCTIONS.clear();
	}
}
