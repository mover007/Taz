package com.taz.inferno;

import com.taz.Taz;
import com.taz.lowballer.Cofl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Fuel Planner: the cheapest way to make any Inferno Minion Fuel with insta-buy prices. Each part is bought or
 * crafted, whichever is cheaper, down to Coal and Sulphur (you can make Taz buy any of them instead). For {@code n} fuels
 * it says exactly what to buy and what to craft, and asks SkyCofl what that many cost on the bazaar right now.
 */
public final class InfernoPlanner {
	/** Makes {@code makes} from {@code in}. */
	public record Recipe(int makes, Map<String, Integer> in) {
	}

	/** Craft {@code crafts} times: {@code makes} come out, {@code spare} more than needed. */
	public record Step(String id, long crafts, long makes, long spare, Recipe recipe) {
	}

	/** What to buy (id → how many, at each's insta-buy price), what to craft, and what that comes to. */
	public record Plan(String fuel, int amount, Map<String, Long> buy, Map<String, Double> each, List<Step> steps, double total,
			double ah, double plain, boolean complete) {
		public double perFuel() {
			return amount > 0 ? total / amount : total;
		}

		/** What tells two plans apart, for their exact price. */
		String key() {
			return fuel + "|" + amount + "|" + buy;
		}
	}

	private static final Map<String, List<Recipe>> RECIPES = new LinkedHashMap<>();
	/** Things are crafted in this order, so everything a step needs is counted before it's crafted. */
	private static final List<String> ORDER = List.of("HYPERGOLIC_GABAGOOL", "HEAVY_GABAGOOL", "FUEL_GABAGOOL", "VERY_CRUDE_GABAGOOL",
			"SULPHURIC_COAL", "ENCHANTED_COAL", "ENCHANTED_SULPHUR");
	/** What you'd rather buy than craft (clicked in the planner). */
	public static final Set<String> BUY = new HashSet<>();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Taz fuel planner");
		t.setDaemon(true);
		return t;
	});
	private static volatile String exactFor = "", askingFor = "";
	private static volatile Map<String, Long> exact = Map.of();

	static {
		RECIPES.put("HYPERGOLIC_GABAGOOL", List.of(new Recipe(1, Map.of("HEAVY_GABAGOOL", 12, "SULPHURIC_COAL", 1))));
		RECIPES.put("HEAVY_GABAGOOL", List.of(new Recipe(1, Map.of("FUEL_GABAGOOL", 24, "SULPHURIC_COAL", 1))));
		RECIPES.put("FUEL_GABAGOOL", List.of(new Recipe(1, Map.of("CRUDE_GABAGOOL", 24, "SULPHURIC_COAL", 1)),
				new Recipe(8, Map.of("SULPHURIC_COAL", 8, "VERY_CRUDE_GABAGOOL", 1))));
		RECIPES.put("VERY_CRUDE_GABAGOOL", List.of(new Recipe(1, Map.of("CRUDE_GABAGOOL", 192))));
		RECIPES.put("SULPHURIC_COAL", List.of(new Recipe(4, Map.of("ENCHANTED_COAL", 16, "ENCHANTED_SULPHUR", 1))));
		RECIPES.put("ENCHANTED_COAL", List.of(new Recipe(1, Map.of("COAL", 160))));
		RECIPES.put("ENCHANTED_SULPHUR", List.of(new Recipe(1, Map.of("SULPHUR_ORE", 160))));
	}

	private InfernoPlanner() {
	}

	/** A fuel's own recipe: 6 distillates, 2 Inferno Fuel Blocks and the gabagool. */
	public static Recipe recipe(InfernoData.Fuel f) {
		Map<String, Integer> in = new LinkedHashMap<>();
		in.put(f.kind().distillate(), 6);
		in.put(InfernoData.FUEL_BLOCK, 2);
		in.put(f.grade().gabagool, 1);
		return new Recipe(1, in);
	}

	public static boolean craftable(String id) {
		return RECIPES.containsKey(id);
	}

	/** Everything the planner may price. */
	public static List<String> ids() {
		List<String> ids = new ArrayList<>(List.of("CRUDE_GABAGOOL", "VERY_CRUDE_GABAGOOL", "FUEL_GABAGOOL", "HEAVY_GABAGOOL", "HYPERGOLIC_GABAGOOL",
				"SULPHURIC_COAL", "ENCHANTED_COAL", "ENCHANTED_SULPHUR", "COAL", "SULPHUR_ORE", InfernoData.FUEL_BLOCK));
		for (InfernoData.Kind k : InfernoData.Kind.values()) ids.add(k.distillate());
		for (InfernoData.Fuel f : InfernoData.fuels()) ids.add(f.id());
		return ids;
	}

	/** One of these at its cheapest, and whether to craft it (by which recipe) or buy it. -1 when it can't be priced. */
	private record Unit(double price, Recipe recipe) {
	}

	private static Unit unit(String id, Map<String, Unit> memo) {
		Unit known = memo.get(id);
		if (known != null) return known;
		double buy = InfernoPrices.instaBuy(id);
		Unit best = new Unit(buy, null);
		if (!BUY.contains(id) || buy < 0) {
			for (Recipe r : RECIPES.getOrDefault(id, List.of())) {
				double c = cost(r, memo);
				if (c >= 0 && (best.price < 0 || c < best.price)) best = new Unit(c, r);
			}
		}
		memo.put(id, best);
		return best;
	}

	/** One craft's worth of a recipe's parts, per item made. */
	private static double cost(Recipe r, Map<String, Unit> memo) {
		double total = 0;
		for (Map.Entry<String, Integer> e : r.in.entrySet()) {
			double u = unit(e.getKey(), memo).price;
			if (u < 0) return -1;
			total += u * e.getValue();
		}
		return total / r.makes;
	}

	/** One crafted the cheapest way (its parts bought or crafted as planned); -1 when it can't be priced or crafted. */
	public static double craftPrice(String id) {
		Map<String, Unit> memo = new HashMap<>();
		double best = -1;
		for (Recipe r : RECIPES.getOrDefault(id, List.of())) {
			double c = cost(r, memo);
			if (c >= 0 && (best < 0 || c < best)) best = c;
		}
		return best;
	}

	/** The plan for {@code amount} of a fuel. */
	public static Plan plan(InfernoData.Fuel f, int amount) {
		Map<String, Unit> memo = new HashMap<>();
		Map<String, Long> need = new LinkedHashMap<>();
		Map<String, Long> buy = new LinkedHashMap<>();
		List<Step> steps = new ArrayList<>();
		Recipe top = recipe(f);
		steps.add(new Step(f.id(), amount, amount, 0, top));
		top.in.forEach((k, v) -> need.merge(k, (long) v * amount, Long::sum));
		for (String id : ORDER) {
			long n = need.getOrDefault(id, 0L);
			if (n <= 0) continue;
			need.remove(id);
			Unit u = unit(id, memo);
			if (u.recipe == null) {
				buy.merge(id, n, Long::sum);
				continue;
			}
			long crafts = (n + u.recipe.makes - 1) / u.recipe.makes;
			steps.add(new Step(id, crafts, crafts * u.recipe.makes, crafts * u.recipe.makes - n, u.recipe));
			for (Map.Entry<String, Integer> e : u.recipe.in.entrySet()) need.merge(e.getKey(), crafts * e.getValue(), Long::sum);
		}
		need.forEach((k, v) -> buy.merge(k, v, Long::sum));
		Map<String, Double> each = new LinkedHashMap<>();
		double total = 0;
		boolean complete = true;
		for (Map.Entry<String, Long> e : buy.entrySet()) {
			double p = InfernoPrices.instaBuy(e.getKey());
			each.put(e.getKey(), p);
			if (p < 0) complete = false;
			else total += p * e.getValue();
		}
		// To compare: the fuel on the AH, and the plain recipe with its gabagool bought.
		double ah = InfernoPrices.instaBuy(f.id());
		double plain = 0;
		for (Map.Entry<String, Integer> e : top.in.entrySet()) {
			double p = InfernoPrices.instaBuy(e.getKey());
			if (p < 0) {
				plain = -1;
				break;
			}
			plain += p * e.getValue();
		}
		return new Plan(f.id(), amount, buy, each, steps, total, ah, plain, complete);
	}

	/** Asks SkyCofl what exactly that many of each cost to insta-buy now (bigger amounts climb the bazaar's orders). */
	public static void priceExactly(Plan p) {
		String key = p.key();
		if (key.equals(exactFor) || key.equals(askingFor) || !p.complete) return;
		askingFor = key;
		Map<String, Long> buy = new LinkedHashMap<>(p.buy);
		WORKER.execute(() -> {
			Map<String, Long> out = new LinkedHashMap<>();
			try {
				for (Map.Entry<String, Long> e : buy.entrySet()) {
					Cofl.Current c = Cofl.current(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()));
					if (c == null || c.buy() <= 0) return;
					out.put(e.getKey(), Math.round(c.buy()));
					if (Cofl.fake == null) Thread.sleep(350);
				}
				exact = out;
				exactFor = key;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				Taz.LOGGER.warn("Fuel planner: couldn't price the shopping list ({})", e.toString());
			} finally {
				if (key.equals(askingFor)) askingFor = "";
			}
		});
	}

	/** What each bought item comes to exactly, once SkyCofl answered for this plan; else empty. */
	public static Map<String, Long> exact(Plan p) {
		return p.key().equals(exactFor) ? exact : Map.of();
	}

	public static boolean pricing(Plan p) {
		return p.key().equals(askingFor);
	}

	/** Test hook. */
	public static void clear() {
		BUY.clear();
		exactFor = "";
		askingFor = "";
		exact = Map.of();
	}
}
