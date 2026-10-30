package com.taz.general.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.general.PlayerViewerConfig;
import com.taz.util.TazChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SkyCrypt's API (api.shiiyu.moe) with the player's own key, for /pv and /nw. Every answer is kept 5 minutes, as SkyCrypt asks,
 * and SkyCrypt only has players someone looked up on its site recently.
 */
public final class SkyCrypt {
	public static final String API = "https://api.shiiyu.moe/api/";
	public static final String SITE = "https://sky.shiiyu.moe/stats/";
	private static final long KEEP_MS = 5 * 60_000L;
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final Map<String, Answer> KEPT = new ConcurrentHashMap<>();
	/** Tests: canned answers by path ("stats/Steve"), so nothing goes to SkyCrypt. A path that isn't here is "no such player". */
	public static final Map<String, Fake> FAKE = new ConcurrentHashMap<>();
	/** Test hook: requests sent (or answered from {@link #FAKE}). */
	public static final AtomicInteger REQUESTS = new AtomicInteger();

	public record Fake(int status, String body) {
		public static Fake ok(String body) {
			return new Fake(200, body);
		}
	}

	private record Answer(JsonElement json, long at) {
	}

	private record Reply(int status, String body, long retryAfter) {
	}

	/** Why there's no data: SkyCrypt's error code ("NOT_CACHED", "RATE_LIMITED") or Taz's own ("NO_KEY", "OFFLINE"). */
	public static final class Failure extends RuntimeException {
		public final String code;
		public final long retrySeconds;

		Failure(String code, String detail, long retrySeconds) {
			super(detail);
			this.code = code;
			this.retrySeconds = retrySeconds;
		}
	}

	private SkyCrypt() {
	}

	/** api/{path} as JSON. Fails with a {@link Failure}. */
	public static CompletableFuture<JsonElement> get(String path) {
		Answer kept = KEPT.get(path);
		if (kept != null && System.currentTimeMillis() - kept.at < KEEP_MS) return CompletableFuture.completedFuture(kept.json);
		String key = PlayerViewerConfig.get().apiKey.trim();
		if (key.isEmpty()) return CompletableFuture.failedFuture(new Failure("NO_KEY", "No SkyCrypt API key", 0));
		REQUESTS.incrementAndGet();
		return send(path, key).handle((r, error) -> {
			if (error != null) throw new Failure("OFFLINE", cause(error).getMessage(), 0);
			if (r.status / 100 != 2) throw failure(r);
			JsonElement json = JsonParser.parseString(r.body);
			KEPT.put(path, new Answer(json, System.currentTimeMillis()));
			return json;
		});
	}

	/** The final texture link SkyCrypt shows for an item id: it names the Minecraft item underneath ("mc=minecraft_bone"). No key needed. */
	static CompletableFuture<String> resolve(String itemId) {
		REQUESTS.incrementAndGet();
		return send("item/" + part(itemId) + "/resolve", null).thenApply(r -> r.status == 200 ? Json.str(JsonParser.parseString(r.body), "texture") : "");
	}

	private static CompletableFuture<Reply> send(String path, String key) {
		if (!FAKE.isEmpty()) {
			Fake f = FAKE.getOrDefault(path, new Fake(404, "{\"code\":\"PLAYER_NOT_FOUND\",\"detail\":\"No such player or profile.\"}"));
			return CompletableFuture.completedFuture(new Reply(f.status, f.body, 0));
		}
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(API + path))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Taz/" + Taz.VERSION + " (Fabric mod)")
				.GET();
		if (key != null) req.header("x-api-key", key);
		return CLIENT.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
				.thenApply(r -> new Reply(r.statusCode(), r.body(), r.headers().firstValueAsLong("Retry-After").orElse(0)));
	}

	private static Failure failure(Reply r) {
		String code = "HTTP_" + r.status, detail = "";
		try {
			JsonElement json = JsonParser.parseString(r.body);
			if (!Json.str(json, "code").isEmpty()) code = Json.str(json, "code");
			detail = Json.str(json, "detail");
		} catch (RuntimeException ignored) {
			// not JSON: keep the status
		}
		if (r.status == 401) code = "BAD_KEY";
		return new Failure(code, detail.isEmpty() ? "HTTP " + r.status : detail, r.retryAfter);
	}

	/** A path part: a name, UUID or profile name, escaped. */
	static String part(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	static Throwable cause(Throwable t) {
		while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
		return t;
	}

	/** Says in chat why there's nothing to show for this player, with a link where one helps. */
	static void explain(Throwable error, String player) {
		Throwable t = cause(error);
		String code = t instanceof Failure f ? f.code : "OFFLINE";
		MutableComponent msg = switch (code) {
			case "NO_KEY" -> red("/pv and /nw need your own SkyCrypt API key. Request one on your SkyCrypt dashboard's Developer page, "
					+ "then paste it in /taz → General → Player Viewer. ").append(link("[Get a key]", "https://sky.shiiyu.moe/dashboard"));
			case "BAD_KEY" -> red("SkyCrypt didn't take your API key (" + t.getMessage() + "). Check it in /taz → General → Player Viewer.");
			case "NOT_CACHED" -> red("SkyCrypt only has players someone looked up on its site recently. Open " + player
					+ "'s page once, then try again. ").append(link("[Open SkyCrypt]", SITE + part(player)));
			case "PLAYER_NOT_FOUND" -> red("SkyCrypt has no player called " + player + ".");
			case "PROFILE_NOT_FOUND" -> red(player + " has no SkyBlock profile on SkyCrypt.");
			case "RATE_LIMITED" -> red("Your SkyCrypt key's rate limit is used up. Try again in "
					+ Math.max(1, ((Failure) t).retrySeconds) + " seconds.");
			case "OFFLINE" -> red("Couldn't reach SkyCrypt: " + t.getMessage());
			default -> red("SkyCrypt couldn't load " + player + ": " + t.getMessage());
		};
		TazChat.send(msg);
	}

	/** "Data from SkyCrypt", grey, opening the player's SkyCrypt page. */
	static MutableComponent credit(String player, String profile) {
		String url = SITE + part(player) + (profile.isEmpty() ? "" : "/" + part(profile));
		return Component.literal("Data from SkyCrypt").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open " + player + " on sky.shiiyu.moe"))));
	}

	private static MutableComponent red(String text) {
		return Component.literal(text).withStyle(s -> s.withColor(0xFF6B6B));
	}

	private static MutableComponent link(String text, String url) {
		return Component.literal(text).withStyle(s -> s.withColor(0xFFB0B0).withUnderlined(true)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
	}

	/** Tests: forget every kept answer. */
	public static void clear() {
		KEPT.clear();
	}
}
