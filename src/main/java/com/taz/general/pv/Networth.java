package com.taz.general.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taz.Taz;
import com.taz.hud.TazTheme;
import com.taz.inventory.ItemPrices;
import com.taz.lowballer.Cofl;
import com.taz.util.TazChat;
import com.taz.xpassist.data.Prices;
import com.taz.xpassist.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * /nw [player]: a SkyBlock networth from Hypixel's profile data: with cosmetics, without them, and what isn't soulbound.
 * Items are worth what SkyHelper-Networth says (their lowest BIN plus their upgrades, see {@link ItemWorth}), priced with
 * SkyCofl's lowest BINs and Hypixel's bazaar, and at least SkyCofl's median sale for items like them. Pets are worth SkyCofl's
 * estimate. Museum items count too, as soulbound.
 */
public final class Networth {
	/** Items per SkyCofl request: it estimates the first 90 of a request and drops the rest. */
	private static final int CHUNK = 90;
	private static final long KEEP_MS = 5 * 60_000L;
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Taz networth");
		t.setDaemon(true);
		return t;
	});
	private static final Map<String, Kept> KEPT = new ConcurrentHashMap<>();
	/** Test hook: the last networth said, as plain text. */
	public static volatile String lastShown = "";

	private record Kept(CompletableFuture<JsonObject> networth, long at) {
	}

	/** One thing with a price, where it is, its worth without cosmetics (0 for a cosmetic), and whether it's soulbound. */
	private record Part(String where, double total, double nonCosmetic, boolean soulbound) {
	}

	private Networth() {
	}

	/** Gets the player's selected profile, works out its networth, and says it in chat. */
	static void show(String player) {
		Hypixel.skyblock(player).thenApplyAsync(answer -> ProfileData.of(answer, player, null))
				.thenCompose(d -> of(d).thenApply(nw -> message(d, nw)))
				.whenCompleteAsync((msg, error) -> {
					if (error != null) {
						Hypixel.explain(error, player);
						return;
					}
					lastShown = msg.getString();
					TazChat.send(msg);
				}, Minecraft.getInstance());
	}

	/** The profile's networth, worked out in the background and kept 5 minutes. */
	static CompletableFuture<JsonObject> of(ProfileData d) {
		Kept kept = KEPT.get(d.profileId);
		if (kept != null && System.currentTimeMillis() - kept.at < KEEP_MS && !kept.networth.isCompletedExceptionally()) return kept.networth;
		CompletableFuture<JsonObject> nw = CompletableFuture.supplyAsync(() -> compute(d), WORKER);
		KEPT.put(d.profileId, new Kept(nw, System.currentTimeMillis()));
		return nw;
	}

	private static JsonObject compute(ProfileData d) {
		Prices prices = prices();
		ItemWorth.Prices price = id -> unit(prices, id);
		Map<String, JsonObject> items = SkyblockItems.get();
		List<ProfileItems.Item> all = new ArrayList<>();
		List<String> where = new ArrayList<>();
		for (Map.Entry<String, List<ProfileItems.Item>> c : d.containers.entrySet()) {
			for (ProfileItems.Item item : c.getValue()) {
				all.add(item);
				where.add(c.getKey());
			}
		}
		for (ProfileItems.Item pet : d.pets) {
			all.add(pet);
			where.add("pets");
		}
		for (ProfileItems.Item item : museum(d)) {
			all.add(item);
			where.add("museum");
		}
		double[] estimates = estimates(all);
		List<Part> parts = new ArrayList<>();
		Map<JsonObject, Double> values = new IdentityHashMap<>();
		for (int i = 0; i < all.size(); i++) {
			ProfileItems.Item item = all.get(i);
			// Pets have no upgrades to add up: SkyCofl's estimate already counts their level, held item and skin.
			if (where.get(i).equals("pets") || item.id().equals("PET")) {
				parts.add(new Part(where.get(i), estimates[i], estimates[i], false));
				values.put(item.json(), estimates[i]);
				continue;
			}
			ItemWorth.Worth w = ItemWorth.of(item.nbt(), price, items, estimates[i]);
			parts.add(new Part(where.get(i), w.total(), w.cosmetic() ? 0 : w.nonCosmetic(), w.soulbound() || where.get(i).equals("museum")));
			values.put(item.json(), w.total());
		}
		for (Map.Entry<String, Long> s : d.sacks.entrySet()) {
			double v = s.getValue() * unit(prices, s.getKey());
			parts.add(new Part("sacks", v, v, false));
		}
		for (Map.Entry<String, Long> e : d.essence.entrySet()) {
			double v = e.getValue() * unit(prices, "ESSENCE_" + e.getKey().toUpperCase(Locale.ROOT));
			parts.add(new Part("essence", v, v, true));
		}
		parts.add(new Part("purse", d.purse, d.purse, false));
		parts.add(new Part("bank", d.bank + d.personalBank, d.bank + d.personalBank, false));
		d.values = values;

		JsonObject out = new JsonObject();
		out.add("normal", total(parts, false, d));
		out.add("nonCosmetic", total(parts, true, d));
		return out;
	}

	private static JsonObject total(List<Part> parts, boolean noCosmetics, ProfileData d) {
		Map<String, double[]> types = new LinkedHashMap<>();
		double all = 0, unsoulbound = 0;
		for (Part p : parts) {
			double v = noCosmetics ? p.nonCosmetic : p.total;
			if (v <= 0) continue;
			double[] t = types.computeIfAbsent(p.where, k -> new double[2]);
			t[0] += v;
			all += v;
			if (!p.soulbound) {
				t[1] += v;
				unsoulbound += v;
			}
		}
		JsonObject o = new JsonObject(), byType = new JsonObject();
		o.addProperty("networth", all);
		o.addProperty("unsoulboundNetworth", unsoulbound);
		o.addProperty("purse", d.purse);
		o.addProperty("bank", d.bank);
		o.addProperty("personalBank", d.personalBank);
		o.addProperty("noInventory", d.noInventory);
		types.forEach((k, v) -> {
			JsonObject t = new JsonObject();
			t.addProperty("total", v[0]);
			t.addProperty("unsoulboundTotal", v[1]);
			byType.add(k, t);
		});
		o.add("types", byType);
		return o;
	}

	/** The profile's museum items (not ones borrowed back out), or none when it can't be had. */
	private static List<ProfileItems.Item> museum(ProfileData d) {
		List<ProfileItems.Item> out = new ArrayList<>();
		try {
			JsonElement museum = Hypixel.museum(d.profileId).join();
			JsonObject member = Json.obj(museum, "members", d.uuid);
			for (Map.Entry<String, JsonElement> e : Json.obj(member, "items").entrySet()) {
				if (Json.bool(e.getValue(), "borrowing")) continue;
				for (ProfileItems.Item item : ProfileItems.decode(Json.at(e.getValue(), "items"))) if (item != null) out.add(item);
			}
			for (JsonElement special : Json.arr(member, "special")) {
				for (ProfileItems.Item item : ProfileItems.decode(Json.at(special, "items"))) if (item != null) out.add(item);
			}
		} catch (Exception e) {
			Taz.LOGGER.warn("Networth: no museum for {} ({})", d.name, Hypixel.cause(e).toString());
		}
		return out;
	}

	/** SkyCofl's median sale (else lowest BIN) for each item, 0 where it has none. */
	private static double[] estimates(List<ProfileItems.Item> items) {
		double[] out = new double[items.size()];
		for (int from = 0; from < items.size(); from += CHUNK) {
			List<ProfileItems.Item> chunk = items.subList(from, Math.min(items.size(), from + CHUNK));
			List<Cofl.Estimate> estimates = ask(chunk.stream().map(ProfileItems.Item::nbt).toList());
			for (int i = 0; i < chunk.size(); i++) {
				Cofl.Estimate e = estimates == null ? null : estimates.get(i);
				out[from + i] = e == null ? 0 : e.median() > 0 ? e.median() : e.lbin();
			}
		}
		return out;
	}

	/** SkyCofl's estimates for a chunk, waiting once if it asks Taz to slow down; null when it can't be reached. */
	private static List<Cofl.Estimate> ask(List<CompoundTag> nbt) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				return Cofl.estimateNbt(nbt);
			} catch (Cofl.Busy busy) {
				if (attempt > 0 || busy.seconds > 15) return null;
				try {
					Thread.sleep(busy.seconds * 1000L);
				} catch (InterruptedException e) {
					return null;
				}
			} catch (Exception e) {
				Taz.LOGGER.warn("Networth: SkyCofl's estimate failed ({})", e.toString());
				return null;
			}
		}
		return null;
	}

	/** One of an item: its bazaar insta-buy price, else its lowest BIN, else 0 (SkyHelper prices bazaar items near insta-buy too). */
	private static double unit(Prices prices, String id) {
		if (prices == null || id.isEmpty()) return 0;
		double v = prices.isBazaar(id) ? prices.bazaar(id, false) : prices.market(id);
		return Double.isNaN(v) ? 0 : v;
	}

	/** Taz's price lists (SkyCofl's lowest BINs and Hypixel's bazaar), waiting up to 10 seconds for the first download. */
	private static Prices prices() {
		for (int i = 0; i < 50; i++) {
			Prices p = ItemPrices.get();
			if (p != null) return p;
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				return null;
			}
		}
		return null;
	}

	/** A line per networth; hover one to see what it's made of. */
	static MutableComponent message(ProfileData d, JsonElement nw) {
		JsonObject normal = Json.obj(nw, "normal"), nonCosmetic = Json.obj(nw, "nonCosmetic");
		String profile = Json.str(d.stats, "profile_cute_name");
		MutableComponent out = Component.literal(d.name + "'s networth").withStyle(s -> s.withColor(TazTheme.RED & 0xFFFFFF))
				.append(grey(profile.isEmpty() ? "" : " · " + profile));
		out.append(line("With cosmetics", Json.num(normal, "networth"), parts(normal, "total"), "Everything, skins and dyes included."));
		out.append(line("Non-cosmetic", Json.num(nonCosmetic, "networth"), parts(nonCosmetic, "total"), "Without skins, dyes and other cosmetics."));
		out.append(line("Unsoulbound", Json.num(normal, "unsoulboundNetworth"), parts(normal, "unsoulboundTotal"),
				"What isn't soulbound, so it could be traded. Without cosmetics: "
						+ TextUtil.coins(Json.num(nonCosmetic, "unsoulboundNetworth"), 2) + "."));
		out.append(grey("\n Purse " + TextUtil.coins(Json.num(normal, "purse"), 2) + " · Bank " + TextUtil.coins(Json.num(normal, "bank"), 2)
				+ (Json.num(normal, "personalBank") > 0 ? " · Personal " + TextUtil.coins(Json.num(normal, "personalBank"), 2) : "") + " · "))
				.append(Hypixel.credit(d.uuid));
		if (Json.bool(normal, "noInventory")) out.append(grey("\n Their inventory API is off, so items they carry aren't counted."));
		return out;
	}

	private static MutableComponent line(String label, double value, List<Component> parts, String about) {
		List<Component> hover = new ArrayList<>();
		hover.add(Component.literal(about).withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF)));
		hover.addAll(parts);
		return Component.literal("\n " + label + ": ").withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF))
				.append(Component.literal(TextUtil.coins(value, 2)).withStyle(s -> s.withColor(TazTheme.TEXT & 0xFFFFFF)
						.withHoverEvent(new HoverEvent.ShowText(Component.empty().append(join(hover))))));
	}

	/** "Inventory 12.3B" for each part of a networth, biggest first. */
	static List<Component> parts(JsonObject result, String field) {
		List<Map.Entry<String, Double>> parts = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : Json.obj(result, "types").entrySet()) {
			double v = Json.num(e.getValue(), field);
			if (v >= 1) parts.add(Map.entry(e.getKey(), v));
		}
		parts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		List<Component> out = new ArrayList<>();
		for (Map.Entry<String, Double> p : parts) {
			out.add(Component.literal(category(p.getKey()) + " ").withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF))
					.append(Component.literal(TextUtil.coins(p.getValue(), 2)).withStyle(s -> s.withColor(TazTheme.TEXT & 0xFFFFFF))));
		}
		return out;
	}

	/** "personal_vault" → "Personal Vault". */
	static String category(String key) {
		if (key.equals("enderchest")) return "Ender Chest";
		return ProfileData.words(key);
	}

	private static Component join(List<Component> lines) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) out.append("\n");
			out.append(lines.get(i));
		}
		return out;
	}

	private static MutableComponent grey(String text) {
		return Component.literal(text).withStyle(s -> s.withColor(TazTheme.GREY & 0xFFFFFF));
	}

	/** Tests: forget every kept networth. */
	public static void clear() {
		KEPT.clear();
	}
}
