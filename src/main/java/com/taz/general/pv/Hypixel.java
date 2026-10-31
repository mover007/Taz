package com.taz.general.pv;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.taz.Taz;
import com.taz.util.TazChat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hypixel's SkyBlock data for /pv and /nw, through Taz's server (Desktop/taz-api, a Cloudflare Worker), which holds Taz's
 * one Hypixel API key so players need nothing: Hypixel's policy says never to put a key in a mod. A name becomes a UUID from
 * the tab list, else from Mojang. Answers are kept 5 minutes here and 10 on the server.
 */
public final class Hypixel {
	/** Taz's server: it asks Hypixel's API with Taz's key. */
	public static final String SERVER = "https://taz-api.taz-api.workers.dev/hypixel/";
	private static final String MOJANG = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
	/** Where the networth's prices come from; their API asks to be credited. */
	public static final String COFL = "https://sky.coflnet.com/player/";
	private static final long KEEP_MS = 5 * 60_000L;
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final Map<String, Answer> KEPT = new ConcurrentHashMap<>();
	private static final Map<String, String> UUIDS = new ConcurrentHashMap<>();
	/** Tests: canned answers by path ("uuid/Steve", "skyblock/<uuid>"), so nothing leaves the game. A path that isn't here is a 404. */
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

	/** Why there's no data: the server's code ("SERVER_KEY", "RATE_LIMITED"), or Taz's ("PLAYER_NOT_FOUND", "NO_SKYBLOCK", "OFFLINE"). */
	public static final class Failure extends RuntimeException {
		public final String code;
		public final long retrySeconds;

		Failure(String code, String detail, long retrySeconds) {
			super(detail);
			this.code = code;
			this.retrySeconds = retrySeconds;
		}
	}

	private Hypixel() {
	}

	/** A player's SkyBlock profiles and rank: {uuid, player, profiles}. Fails with a {@link Failure}. */
	static CompletableFuture<JsonElement> skyblock(String player) {
		return uuid(player).thenCompose(uuid -> {
			Answer kept = KEPT.get(uuid);
			if (kept != null && System.currentTimeMillis() - kept.at < KEEP_MS) return CompletableFuture.completedFuture(kept.json);
			return send(SERVER + "skyblock/" + uuid, "skyblock/" + uuid).thenApply(r -> {
				if (r.status / 100 != 2) throw failure(r);
				JsonElement json = JsonParser.parseString(r.body);
				KEPT.put(uuid, new Answer(json, System.currentTimeMillis()));
				return json;
			});
		});
	}

	/** A profile's museum: {members: {uuid: {items, special}}}. Fails with a {@link Failure}. Kept 5 minutes. */
	static CompletableFuture<JsonElement> museum(String profileId) {
		String key = "museum:" + profileId;
		Answer kept = KEPT.get(key);
		if (kept != null && System.currentTimeMillis() - kept.at < KEEP_MS) return CompletableFuture.completedFuture(kept.json);
		return send(SERVER + "museum/" + profileId, "museum/" + profileId).thenApply(r -> {
			if (r.status / 100 != 2) throw failure(r);
			JsonElement json = JsonParser.parseString(r.body);
			KEPT.put(key, new Answer(json, System.currentTimeMillis()));
			return json;
		});
	}

	/** A name's UUID without dashes: from the tab list when they're in your lobby, else from Mojang. */
	static CompletableFuture<String> uuid(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		String known = UUIDS.get(key);
		if (known != null) return CompletableFuture.completedFuture(known);
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null || !FAKE.isEmpty() ? null : connection.getPlayerInfo(name);
		// Hypixel's tab list also has made-up entries; real players have version 4 UUIDs.
		if (info != null && info.getProfile().id().version() == 4) {
			String id = info.getProfile().id().toString().replace("-", "");
			UUIDS.put(key, id);
			return CompletableFuture.completedFuture(id);
		}
		return send(MOJANG + URLEncoder.encode(name, StandardCharsets.UTF_8), "uuid/" + name).thenApply(r -> {
			if (r.status == 404 || r.status == 204) throw new Failure("PLAYER_NOT_FOUND", "No such player.", 0);
			if (r.status / 100 != 2) throw new Failure("OFFLINE", "Mojang answered " + r.status, r.retryAfter);
			String id = Json.str(JsonParser.parseString(r.body), "id");
			if (id.isEmpty()) throw new Failure("PLAYER_NOT_FOUND", "No such player.", 0);
			UUIDS.put(key, id);
			return id;
		});
	}

	private static CompletableFuture<Reply> send(String url, String path) {
		REQUESTS.incrementAndGet();
		if (!FAKE.isEmpty()) {
			Fake f = FAKE.getOrDefault(path, new Fake(404, "{\"code\":\"NOT_FOUND\",\"detail\":\"Not found.\"}"));
			return CompletableFuture.completedFuture(new Reply(f.status, f.body, 0));
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
				.header("User-Agent", "Taz/" + Taz.VERSION + " (Fabric mod)").GET().build();
		return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.handle((r, error) -> {
					if (error != null) throw new Failure("OFFLINE", cause(error).getMessage(), 0);
					return new Reply(r.statusCode(), r.body(), r.headers().firstValueAsLong("Retry-After").orElse(0));
				});
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
		return new Failure(code, detail.isEmpty() ? "HTTP " + r.status : detail, r.retryAfter);
	}

	static Throwable cause(Throwable t) {
		while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
		return t;
	}

	/** Says in chat why there's nothing to show for this player. */
	static void explain(Throwable error, String player) {
		Throwable t = cause(error);
		String code = t instanceof Failure f ? f.code : "OFFLINE";
		TazChat.send(red(switch (code) {
			case "SERVER_KEY" -> "Taz can't get Hypixel's data right now. Try again later.";
			case "PLAYER_NOT_FOUND" -> "There's no Minecraft player called " + player + ".";
			case "NO_SKYBLOCK" -> player + " hasn't played SkyBlock.";
			case "RATE_LIMITED" -> "Too many lookups right now. Try again in " + Math.max(1, ((Failure) t).retrySeconds) + " seconds.";
			case "OFFLINE" -> "Couldn't reach Taz's server: " + t.getMessage();
			default -> "Couldn't load " + player + ": " + t.getMessage();
		}));
		if (!(t instanceof Failure)) Taz.LOGGER.warn("Player viewer: {} failed", player, t);
	}

	/** "Data from Hypixel · prices from SkyCofl", grey; the prices part opens the player on SkyCofl. */
	static MutableComponent credit(String uuid) {
		return Component.literal("Data from Hypixel · ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("prices from SkyCofl").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withUnderlined(true)
						.withClickEvent(new ClickEvent.OpenUrl(URI.create(COFL + uuid)))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Prices provided by SkyCofl (sky.coflnet.com).\n"
								+ "Taz isn't affiliated with SkyCofl or Hypixel.")))));
	}

	private static MutableComponent red(String text) {
		return Component.literal(text).withStyle(s -> s.withColor(0xFF6B6B));
	}

	/** "28667672039044989b0019b14a2c34d6" as a UUID. */
	static UUID dashed(String id) {
		String s = id.replace("-", "");
		if (s.length() != 32) return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
	}

	/** Tests: forget every kept answer and name. */
	public static void clear() {
		KEPT.clear();
		UUIDS.clear();
	}
}
