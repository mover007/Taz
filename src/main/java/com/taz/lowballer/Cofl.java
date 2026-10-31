package com.taz.lowballer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.util.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * SkyCofl's public price API (sky.coflnet.com/api). It needs no key; each player may ask 30 times in 10 seconds and 100
 * times a minute. Calls block, so run them off the game thread.
 */
public final class Cofl {
	public static final String API = "https://sky.coflnet.com/api/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	/** Test hook: answers by path ("price/nbt", "item/price/X/current?count=1") and body instead of SkyCofl; null answers 404. */
	public static volatile BiFunction<String, String, String> fake;
	/** Requests made (tests count them). */
	public static final AtomicInteger REQUESTS = new AtomicInteger();

	private Cofl() {
	}

	/** SkyCofl's estimate for one item: lowest BIN and median sale of items like it, sales a day, and a price that sells in 5 minutes. */
	public record Estimate(long lbin, long median, double volume, long fastSell) {
		/** What it would really sell for: the lower of its lowest BIN and median sale. */
		public long worth() {
			return lbin > 0 && median > 0 ? Math.min(lbin, median) : Math.max(lbin, median);
		}
	}

	/** The latest price of an item id: bazaar insta-sell/insta-buy for the whole count, or the lowest BINs. */
	public record Current(double sell, double buy, boolean auction) {
	}

	/** SkyCofl asked us to slow down. */
	public static final class Busy extends IOException {
		public final int seconds;

		Busy(int seconds) {
			super("SkyCofl asked to wait " + seconds + "s");
			this.seconds = seconds;
		}
	}

	/** Estimates for the stacks, in the same order; null where SkyCofl has nothing (bazaar items, things nobody sells). */
	static List<Estimate> estimate(List<ItemStack> stacks) throws IOException, InterruptedException {
		List<CompoundTag> items = new ArrayList<>();
		for (ItemStack stack : stacks) items.add(legacy(stack));
		return estimateNbt(items);
	}

	/** Estimates for items already in 1.8 NBT (Hypixel's profile data is), in the same order. */
	public static List<Estimate> estimateNbt(List<CompoundTag> nbt) throws IOException, InterruptedException {
		ListTag items = new ListTag();
		for (CompoundTag item : nbt) {
			CompoundTag copy = item.copy();
			CompoundTag extra = copy.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
			// One bad uuid makes SkyCofl drop every estimate in the request.
			if (extra.contains("uuid") && !isUuid(extra.getStringOr("uuid", ""))) extra.remove("uuid");
			items.add(copy);
		}
		CompoundTag root = new CompoundTag();
		root.put("i", items);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, bytes);
		JsonObject body = new JsonObject();
		body.addProperty("chestName", "Trade");
		body.addProperty("fullInventoryNbt", Base64.getEncoder().encodeToString(bytes.toByteArray()));
		String reply = send("price/nbt", body.toString());
		List<Estimate> out = new ArrayList<>();
		JsonArray array = reply == null ? new JsonArray() : JsonParser.parseString(reply).getAsJsonArray();
		for (int i = 0; i < nbt.size(); i++) {
			JsonElement e = i < array.size() ? array.get(i) : null;
			if (e == null || !e.isJsonObject()) {
				out.add(null);
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			Estimate est = new Estimate(num(o, "lbin"), num(o, "median"), o.has("volume") && !o.get("volume").isJsonNull() ? o.get("volume").getAsDouble() : -1,
					num(o, "fastSell"));
			out.add(est.lbin() > 0 || est.median() > 0 ? est : null);
		}
		return out;
	}

	/** The latest price of {@code count} of an item id, or null when SkyCofl doesn't know it. */
	public static Current current(String tag, int count) throws IOException, InterruptedException {
		String reply = send("item/price/" + URLEncoder.encode(tag, StandardCharsets.UTF_8) + "/current?count=" + count, null);
		if (reply == null) return null;
		JsonObject o = JsonParser.parseString(reply).getAsJsonObject();
		double sell = o.has("sell") ? o.get("sell").getAsDouble() : 0, buy = o.has("buy") ? o.get("buy").getAsDouble() : 0;
		if (sell <= 0 && buy <= 0) return null;
		return new Current(sell, buy, o.has("isAh") && o.get("isAh").getAsBoolean());
	}

	/** The item as 1.8 NBT ({id, Count, tag: {ExtraAttributes, display}}), the shape SkyCofl's price estimate reads. */
	static CompoundTag legacy(ItemStack stack) {
		CompoundTag item = new CompoundTag();
		item.putShort("id", (short) 1);
		item.putByte("Count", (byte) Math.min(stack.getCount(), 127));
		item.putShort("Damage", (short) 0);
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag extra = data == null ? new CompoundTag() : data.copyTag();
		CompoundTag display = new CompoundTag();
		display.putString("Name", stack.getHoverName().getString());
		ListTag lore = new ListTag();
		for (String line : Items.lore(stack)) lore.add(StringTag.valueOf(line));
		display.put("Lore", lore);
		CompoundTag tag = new CompoundTag();
		tag.put("ExtraAttributes", extra);
		tag.put("display", display);
		item.put("tag", tag);
		return item;
	}

	private static boolean isUuid(String s) {
		try {
			UUID.fromString(s);
			return s.length() == 36;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static long num(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0;
	}

	/** The reply body, or null for "not found". */
	private static String send(String path, String body) throws IOException, InterruptedException {
		REQUESTS.incrementAndGet();
		BiFunction<String, String, String> f = fake;
		if (f != null) return f.apply(path, body);
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(15))
				.header("User-Agent", "Taz/" + Taz.VERSION + " (Fabric mod)");
		if (body != null) req.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
		HttpResponse<String> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 429) {
			int wait = res.headers().firstValue("Retry-After").map(v -> {
				try {
					return Integer.parseInt(v.trim());
				} catch (NumberFormatException e) {
					return 10;
				}
			}).orElse(10);
			throw new Busy(Math.max(1, Math.min(wait, 120)));
		}
		if (res.statusCode() == 400 || res.statusCode() == 404) return null;
		if (res.statusCode() != 200) throw new IOException("SkyCofl answered " + res.statusCode());
		return res.body();
	}
}
