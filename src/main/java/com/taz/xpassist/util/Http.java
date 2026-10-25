package com.taz.xpassist.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

/** Small blocking HTTP helper. Always call from a background thread. */
public final class Http {
	private static final String USER_AGENT = "SkyblockXpAssist/1.0 (Fabric mod)";
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private Http() {
	}

	public static final class HttpStatusException extends IOException {
		public final int status;
		public final String body;

		public HttpStatusException(String url, int status, String body) {
			super("HTTP " + status + " from " + url);
			this.status = status;
			this.body = body;
		}
	}

	private static HttpRequest.Builder request(String url, Map<String, String> headers) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(120))
			.header("User-Agent", USER_AGENT);
		headers.forEach(builder::header);
		return builder.GET();
	}

	public static String getString(String url) throws IOException {
		return getString(url, Map.of());
	}

	public static String getString(String url, Map<String, String> headers) throws IOException {
		try {
			HttpResponse<byte[]> response = CLIENT.send(request(url, headers).build(), HttpResponse.BodyHandlers.ofByteArray());
			String body = new String(response.body(), StandardCharsets.UTF_8);
			if (response.statusCode() / 100 != 2) {
				throw new HttpStatusException(url, response.statusCode(), body);
			}
			return body;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while fetching " + url, e);
		}
	}

	/** Downloads to a temp file next to {@code target} and atomically moves it into place. */
	public static void download(String url, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Path temp = target.resolveSibling(target.getFileName() + ".part");
		try {
			HttpResponse<Path> response = CLIENT.send(request(url, Map.of()).build(), HttpResponse.BodyHandlers.ofFile(temp));
			if (response.statusCode() / 100 != 2) {
				Files.deleteIfExists(temp);
				throw new HttpStatusException(url, response.statusCode(), "");
			}
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while downloading " + url, e);
		}
	}
}
