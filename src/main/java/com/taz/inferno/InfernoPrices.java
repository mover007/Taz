package com.taz.inferno;

import com.taz.Taz;
import com.taz.lowballer.Cofl;
import com.taz.lowballer.Lowball;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SkyCofl prices for the Inferno items, asked one at a time and kept 10 minutes. What a thing costs and what it sells for
 * follow the Bazaar prices setting: orders (buy order in, sell offer out) or instant.
 */
public final class InfernoPrices {
	private static final long KEEP_MS = 10 * 60_000L;
	/** Between two requests, to stay well under SkyCofl's 30 every 10 seconds. */
	private static final long GAP_MS = 350;
	private static final double BAZAAR_TAX = 0.0125;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Taz inferno prices");
		t.setDaemon(true);
		return t;
	});

	private record Entry(Cofl.Current price, long at) {
	}

	private static final Map<String, Entry> PRICES = new ConcurrentHashMap<>();
	private static final java.util.Set<String> ASKING = ConcurrentHashMap.newKeySet();
	private static volatile long waitUntil;
	private static volatile String problem;
	/** Bumped whenever a price comes in, so the tracker values what it couldn't before. */
	private static volatile int version;

	private InfernoPrices() {
	}

	/** Asks SkyCofl for any of these it hasn't priced in the last 10 minutes (all of them when {@code force}). */
	public static void want(Collection<String> ids, boolean force) {
		long now = System.currentTimeMillis();
		if (now < waitUntil) return;
		List<String> todo = new ArrayList<>();
		for (String id : ids) {
			if (id == null || id.isEmpty() || ASKING.contains(id)) continue;
			Entry e = PRICES.get(id);
			if (force || e == null || now - e.at > KEEP_MS) todo.add(id);
		}
		if (todo.isEmpty()) return;
		ASKING.addAll(todo);
		WORKER.execute(() -> {
			try {
				for (String id : todo) {
					try {
						Cofl.Current c = Cofl.current(id, 1);
						if (c != null) {
							PRICES.put(id, new Entry(c, System.currentTimeMillis()));
							version++;
						}
						problem = null;
					} catch (Cofl.Busy busy) {
						waitUntil = System.currentTimeMillis() + busy.seconds * 1000L;
						problem = "SkyCofl is busy: try again in " + busy.seconds + "s";
						return;
					} catch (Exception e) {
						Taz.LOGGER.warn("Inferno prices: couldn't price {} ({})", id, e.toString());
						problem = "Couldn't reach SkyCofl";
					}
					if (Cofl.fake == null) Thread.sleep(GAP_MS);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				ASKING.removeAll(todo);
			}
		});
	}

	public static boolean asking() {
		return !ASKING.isEmpty();
	}

	public static String problem() {
		return problem;
	}

	public static int version() {
		return version;
	}

	public static boolean known(String id) {
		return PRICES.containsKey(id);
	}

	/** What one costs to buy: a buy order (Orders) or insta-buy; on the AH its lowest BIN. -1 when unknown. */
	public static double cost(String id) {
		Entry e = PRICES.get(id);
		if (e == null) return -1;
		Cofl.Current c = e.price;
		if (c.auction()) return c.buy() > 0 ? c.buy() : c.sell();
		boolean orders = InfernoConfig.get().prices == InfernoConfig.Prices.ORDERS;
		double v = orders ? c.sell() : c.buy();
		return v > 0 ? v : Math.max(c.sell(), c.buy());
	}

	/** What one costs to insta-buy right now (the planner's price), whatever the Bazaar prices setting. -1 when unknown. */
	public static double instaBuy(String id) {
		Entry e = PRICES.get(id);
		if (e == null) return -1;
		return e.price.buy() > 0 ? e.price.buy() : e.price.sell() > 0 ? e.price.sell() : -1;
	}

	/** What one sells for after tax: a sell offer (Orders) or insta-sell; on the AH a quick sale less AH tax. -1 when unknown. */
	public static double value(String id) {
		Entry e = PRICES.get(id);
		if (e == null) {
			// A compacted item SkyCofl doesn't know is worth its base items.
			int n = InfernoData.PRODUCTS.getOrDefault(id, 1);
			String base = base(id);
			return n > 1 && base != null && PRICES.containsKey(base) ? n * value(base) : -1;
		}
		Cofl.Current c = e.price;
		if (c.auction()) {
			double v = c.sell() > 0 ? c.sell() : c.buy();
			return v * (1 - Lowball.tax(Math.round(v), false));
		}
		boolean orders = InfernoConfig.get().prices == InfernoConfig.Prices.ORDERS;
		double v = orders ? c.buy() : c.sell();
		if (v <= 0) v = Math.max(c.sell(), c.buy());
		return v * (1 - BAZAAR_TAX);
	}

	/** The plain item a compacted one is made of: ENCHANTED_GLOWSTONE → GLOWSTONE_DUST. */
	private static String base(String id) {
		return switch (id) {
			case "VERY_CRUDE_GABAGOOL" -> "CRUDE_GABAGOOL";
			case "STUFFED_CHILI_PEPPER" -> "CHILI_PEPPER";
			case "ENCHANTED_BLAZE_POWDER", "ENCHANTED_BLAZE_ROD" -> "BLAZE_ROD";
			case "ENCHANTED_MAGMA_CREAM", "WHIPPED_MAGMA_CREAM" -> "MAGMA_CREAM";
			case "ENCHANTED_NETHER_STALK", "MUTANT_NETHER_STALK" -> "NETHER_STALK";
			case "ENCHANTED_GLOWSTONE_DUST", "ENCHANTED_GLOWSTONE" -> "GLOWSTONE_DUST";
			default -> null;
		};
	}

	/** What a fuel costs to craft from bazaar parts: 6 distillates, 2 fuel blocks and the gabagool. -1 when a price is missing. */
	public static double craftCost(InfernoData.Fuel f) {
		double d = cost(f.kind().distillate()), b = cost(InfernoData.FUEL_BLOCK), g = cost(f.grade().gabagool);
		return d < 0 || b < 0 || g < 0 ? -1 : 6 * d + 2 * b + g;
	}

	/** The cheaper of crafting a fuel and buying it on the AH. -1 when neither is known. */
	public static double fuelCost(InfernoData.Fuel f) {
		double craft = craftCost(f), ah = cost(f.id());
		if (craft < 0) return ah;
		return ah > 0 ? Math.min(craft, ah) : craft;
	}

	/** Coins a fuel's day makes, by {@link InfernoData#makes}. -1 when a price is missing. */
	public static double worth(Map<String, Double> makes) {
		double total = 0;
		for (Map.Entry<String, Double> e : makes.entrySet()) {
			double v = value(e.getKey());
			if (v < 0) {
				if (e.getValue() >= 0.01) return -1;
				continue;
			}
			total += v * e.getValue();
		}
		return total;
	}

	/** Every item the calculator and the tracker price. */
	public static List<String> all() {
		List<String> ids = new ArrayList<>();
		for (InfernoData.Kind k : InfernoData.Kind.values()) {
			ids.add(k.distillate());
			ids.add(k.product());
		}
		for (InfernoData.Grade g : InfernoData.Grade.values()) ids.add(g.gabagool);
		ids.add(InfernoData.FUEL_BLOCK);
		ids.add(InfernoData.EYEDROPS);
		for (InfernoData.Rare r : InfernoData.RARES) ids.add(r.id());
		ids.add(InfernoData.CERAMICS);
		ids.add("VERY_CRUDE_GABAGOOL");
		ids.add("STUFFED_CHILI_PEPPER");
		for (String id : InfernoPlanner.ids()) if (!ids.contains(id)) ids.add(id);
		return ids;
	}

	/** Test hook. */
	public static void clear() {
		PRICES.clear();
		ASKING.clear();
		waitUntil = 0;
		problem = null;
	}
}
