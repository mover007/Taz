package com.taz.xpassist.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Live prices: Hypixel's bazaar API for bazaar items, and the same public price file the
 * SkyHelper bot uses (github.com/SkyHelperBot/Prices) for auction house lowest-BIN prices.
 */
public final class Prices {
	private final Map<String, Double> market = new HashMap<>();
	private final Map<String, double[]> bazaar = new HashMap<>();
	public final long loadedAt = System.currentTimeMillis();

	public static Prices parse(String skyHelperBody, String bazaarBody) {
		Prices prices = new Prices();
		if (skyHelperBody != null) {
			JsonObject json = JsonParser.parseString(skyHelperBody).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
					double value = entry.getValue().getAsDouble();
					if (value > 0) {
						prices.market.put(entry.getKey(), value);
					}
				}
			}
		}
		if (bazaarBody != null) {
			JsonObject json = JsonParser.parseString(bazaarBody).getAsJsonObject();
			if (json.has("products")) {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("products").entrySet()) {
					JsonObject status = entry.getValue().getAsJsonObject().getAsJsonObject("quick_status");
					if (status != null) {
						prices.bazaar.put(entry.getKey(), new double[]{
							status.get("buyPrice").getAsDouble(),
							status.get("sellPrice").getAsDouble()
						});
					}
				}
			}
		}
		return prices;
	}

	public boolean isBazaar(String id) {
		return bazaar.containsKey(id);
	}

	/** Bazaar price (insta-buy or top buy order), or NaN when not on the bazaar or nobody sells it. */
	public double bazaar(String id, boolean buyOrder) {
		double[] values = bazaar.get(id);
		if (values == null) {
			return Double.NaN;
		}
		double value = buyOrder ? values[1] : values[0];
		if (value <= 0) {
			value = buyOrder ? values[0] : values[1];
		}
		return value > 0 ? value : Double.NaN;
	}

	/** Lowest BIN / market price from the SkyHelper price file, or NaN. */
	public double market(String id) {
		Double value = market.get(id);
		return value != null ? value : Double.NaN;
	}

	public Map<String, Double> marketPrices() {
		return market;
	}
}
