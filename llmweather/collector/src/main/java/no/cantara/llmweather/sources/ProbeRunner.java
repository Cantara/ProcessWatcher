package no.cantara.llmweather.sources;

import no.cantara.llmweather.config.ProbeConfig;
import no.cantara.llmweather.model.Probe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Sends one tiny chat request to a provider and measures the round trip. This is a vendor-neutral
 * HTTP probe across eight different APIs, so it deliberately speaks raw HTTP rather than any one vendor SDK.
 * Probes only run when the configured API key environment variable is set.
 */
public final class ProbeRunner {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Optional<Probe> probe(ProbeConfig config) {
        if (config == null || config.apiKeyEnv() == null) {
            return Optional.empty();
        }
        String key = System.getenv(config.apiKeyEnv());
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        HttpRequest request = buildRequest(config, key);
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - start) / 1_000_000;
            boolean ok = response.statusCode() / 100 == 2;
            return Optional.of(new Probe(ok, ms, config.model(), ok ? null : "HTTP " + response.statusCode()));
        } catch (IOException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return Optional.of(new Probe(false, ms, config.model(), e.getClass().getSimpleName() + ": " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of(new Probe(false, 0, config.model(), "interrupted"));
        }
    }

    static HttpRequest buildRequest(ProbeConfig config, String key) {
        String prompt = "Reply with the single word OK.";
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(config.url()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", JdkHttpFetcher.USER_AGENT);
        String body;
        if ("anthropic".equalsIgnoreCase(config.type())) {
            b.header("x-api-key", key).header("anthropic-version", "2023-06-01");
            body = "{\"model\":\"" + config.model() + "\",\"max_tokens\":5,"
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"" + prompt + "\"}]}";
        } else {
            b.header("Authorization", "Bearer " + key);
            body = "{\"model\":\"" + config.model() + "\",\"max_tokens\":5,"
                    + "\"messages\":[{\"role\":\"user\",\"content\":\"" + prompt + "\"}]}";
        }
        return b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
