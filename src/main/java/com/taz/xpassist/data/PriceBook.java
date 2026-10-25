package com.taz.xpassist.data;

import com.taz.xpassist.config.ModConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Works out the cheapest way to get one of an item: bazaar, auction house, NPC shop or crafting. */
public final class PriceBook {
	public enum Source {
		BAZAAR("Bazaar"),
		AUCTION("Auction House"),
		NPC_SHOP("NPC shop"),
		CRAFT("Craft"),
		COINS("Coins"),
		NONE("No price");

		public final String label;

		Source(String label) {
			this.label = label;
		}
	}

	public record Quote(double cost, Source source, String npcId) {
		public static final Quote NONE = new Quote(Double.NaN, Source.NONE, null);

		public boolean priced() {
			return !Double.isNaN(cost);
		}
	}

	private final Prices prices;
	private final NeuRepo neu;
	private final boolean buyOrder;
	private final boolean useNpc;
	private final boolean useCraft;
	private final java.util.function.Predicate<String> craftAllowed;
	private final Map<String, Quote> memo = new HashMap<>();
	private final Set<String> visiting = new HashSet<>();

	public PriceBook(Prices prices, NeuRepo neu, ModConfig config) {
		this(prices, neu, config, id -> true);
	}

	/** {@code craftAllowed} says whether you have unlocked an item's recipe; locked recipes are never "cheapest". */
	public PriceBook(Prices prices, NeuRepo neu, ModConfig config, java.util.function.Predicate<String> craftAllowed) {
		this.craftAllowed = craftAllowed;
		this.prices = prices;
		this.neu = neu;
		this.buyOrder = config.xpList.bazaarMode == ModConfig.BazaarMode.BUY_ORDER;
		this.useNpc = config.xpList.useNpcShops;
		this.useCraft = config.xpList.useCraftCosts;
	}

	public Quote quote(String id) {
		if (NeuRepo.COIN.equals(id)) {
			return new Quote(1, Source.COINS, null);
		}
		Quote cached = memo.get(id);
		if (cached != null) {
			return cached;
		}
		if (!visiting.add(id)) {
			return Quote.NONE;
		}
		Quote best = Quote.NONE;
		try {
			// NEU writes damage values as "INK_SACK-4", Hypixel as "INK_SACK:4".
			String marketId = id.matches("^[A-Z0-9_]+-\\d+$") ? id.replace('-', ':') : id;
			if (prices.isBazaar(marketId)) {
				best = better(best, new Quote(prices.bazaar(marketId, buyOrder), Source.BAZAAR, null));
			} else {
				best = better(best, new Quote(prices.market(marketId), Source.AUCTION, null));
			}
			if (useNpc && neu.offersByItem.containsKey(id)) {
				for (NeuRepo.ShopOffer offer : neu.offersByItem.get(id)) {
					double total = 0;
					for (Map.Entry<String, Double> cost : offer.costs().entrySet()) {
						Quote part = quote(cost.getKey());
						total += part.cost() * cost.getValue();
					}
					best = better(best, new Quote(total / offer.resultCount(), Source.NPC_SHOP, offer.npcId()));
				}
			}
			if (useCraft && neu.craftRecipes.containsKey(id) && craftAllowed.test(id)) {
				NeuRepo.Recipe recipe = neu.craftRecipes.get(id);
				double total = 0;
				for (Map.Entry<String, Double> input : recipe.inputs().entrySet()) {
					total += quote(input.getKey()).cost() * input.getValue();
				}
				best = better(best, new Quote(total / recipe.outputCount(), Source.CRAFT, null));
			}
		} finally {
			visiting.remove(id);
		}
		memo.put(id, best);
		return best;
	}

	private static Quote better(Quote current, Quote candidate) {
		if (Double.isNaN(candidate.cost()) || candidate.cost() < 0) {
			return current;
		}
		if (Double.isNaN(current.cost()) || candidate.cost() < current.cost()) {
			return candidate;
		}
		// On a tie prefer the NPC shop: it is a fixed price and we can show you where the NPC is.
		if (candidate.npcId() != null && candidate.cost() <= current.cost() * 1.001) {
			return candidate;
		}
		return current;
	}

	public Prices prices() {
		return prices;
	}
}
