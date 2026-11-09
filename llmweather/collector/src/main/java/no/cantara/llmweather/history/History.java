package no.cantara.llmweather.history;

import no.cantara.llmweather.model.HistoryPoint;
import no.cantara.llmweather.model.ModelInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the collector remembers between runs: recent observations per provider and the model list
 * from the previous run (used to detect price changes, new models and removals).
 */
public record History(Map<String, List<HistoryPoint>> providers, Map<String, ModelInfo> models, Instant updatedAt) {

    public static History empty() {
        return new History(Map.of(), Map.of(), null);
    }

    public List<HistoryPoint> pointsFor(String providerId) {
        return providers == null ? List.of() : providers.getOrDefault(providerId, List.of());
    }

    public boolean hasModels() {
        return models != null && !models.isEmpty();
    }
}
