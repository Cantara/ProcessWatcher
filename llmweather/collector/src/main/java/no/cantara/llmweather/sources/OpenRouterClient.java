package no.cantara.llmweather.sources;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.llmweather.Json;
import no.cantara.llmweather.model.ModelInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads the public OpenRouter model list, which carries context lengths and per-token prices for every provider. */
public final class OpenRouterClient {
    public static final String MODELS_URL = "https://openrouter.ai/api/v1/models";

    private final HttpFetcher fetcher;

    public OpenRouterClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public List<ModelInfo> fetchAll() throws IOException {
        return parse(fetcher.get(MODELS_URL));
    }

    public static List<ModelInfo> parse(String json) throws IOException {
        JsonNode root = Json.mapper().readTree(json);
        List<ModelInfo> models = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            String id = n.path("id").asText(null);
            if (id == null) {
                continue;
            }
            JsonNode pricing = n.path("pricing");
            Integer context = n.path("context_length").isNumber() ? n.path("context_length").asInt() : null;
            models.add(new ModelInfo(
                    id,
                    n.path("name").asText(id),
                    n.path("created").asLong(0),
                    context,
                    perMillion(pricing.path("prompt")),
                    perMillion(pricing.path("completion"))));
        }
        return models;
    }

    /** OpenRouter publishes USD per token as a string; negative values mean dynamic pricing. */
    static Double perMillion(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            double perToken = Double.parseDouble(node.asText());
            if (perToken < 0) {
                return null;
            }
            return Math.round(perToken * 1_000_000 * 10_000.0) / 10_000.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
