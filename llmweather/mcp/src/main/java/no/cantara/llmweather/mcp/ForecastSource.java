package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Loads forecast.json and history.json from a URL or a local file and caches them for a short time.
 * When a refresh fails the previous data is served and marked stale, so a hiccup at the publisher never
 * takes the server down. Thread-safe.
 */
public final class ForecastSource {
    public static final URI DEFAULT_FORECAST = URI.create("https://cantara.github.io/LLMWeather/data/forecast.json");
    public static final URI DEFAULT_HISTORY = URI.create("https://cantara.github.io/LLMWeather/data/history.json");

    public record Data(JsonNode forecast, JsonNode history, Instant fetchedAt, boolean stale, String error) {}

    private final URI forecastUri;
    private final URI historyUri;
    private final Duration ttl;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private Data cached;

    public ForecastSource(URI forecastUri, URI historyUri, Duration ttl, Clock clock) {
        this.forecastUri = forecastUri;
        this.historyUri = historyUri;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Accepts a URL or a filesystem path. */
    public static URI toUri(String s) {
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file:")) {
            return URI.create(s);
        }
        return Path.of(s).toAbsolutePath().toUri();
    }

    public synchronized Data get() {
        Instant now = clock.instant();
        if (cached != null && !cached.stale() && cached.fetchedAt().plus(ttl).isAfter(now)) {
            return cached;
        }
        if (cached != null && cached.stale() && cached.fetchedAt().plus(Duration.ofSeconds(15)).isAfter(now)) {
            return cached; // do not hammer a failing source
        }
        try {
            JsonNode forecast = load(forecastUri);
            JsonNode history = null;
            try {
                history = historyUri == null ? null : load(historyUri);
            } catch (IOException e) {
                history = cached == null ? null : cached.history();
            }
            cached = new Data(forecast, history, now, false, null);
        } catch (IOException | RuntimeException e) {
            if (cached == null) {
                throw new IllegalStateException("Forecast unavailable: " + e.getMessage(), e);
            }
            cached = new Data(cached.forecast(), cached.history(), now, true, e.getMessage());
        }
        return cached;
    }

    JsonNode load(URI uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            return Json.mapper().readTree(Files.readString(Path.of(uri)));
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "LLMWeather-MCP/0.1 (+https://github.com/Cantara/LLMWeather)")
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + res.statusCode() + " from " + uri);
            }
            return Json.mapper().readTree(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        }
    }
}
