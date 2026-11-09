package no.cantara.llmweather.history;

import no.cantara.llmweather.Json;
import no.cantara.llmweather.model.HistoryPoint;
import no.cantara.llmweather.model.ModelInfo;
import no.cantara.llmweather.model.ProviderReport;
import no.cantara.llmweather.model.Snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads and saves history.json and folds a new snapshot into it. */
public final class HistoryStore {
    public static final String FILE_NAME = "history.json";
    static final Duration RETENTION = Duration.ofDays(7);
    static final int MAX_POINTS = 400;

    private HistoryStore() {}

    public static History load(Path dir) throws IOException {
        if (dir == null) {
            return History.empty();
        }
        Path file = dir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return History.empty();
        }
        History h = Json.mapper().readValue(Files.readString(file), History.class);
        return new History(
                h.providers() == null ? Map.of() : h.providers(),
                h.models() == null ? Map.of() : h.models(),
                h.updatedAt());
    }

    public static void save(History history, Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(FILE_NAME), Json.mapper().writeValueAsString(history));
    }

    /** Appends one observation per provider and replaces the remembered model list (unless the fetch came back empty). */
    public static History append(History previous, Snapshot snapshot, List<ModelInfo> currentModels, Instant now) {
        Map<String, List<HistoryPoint>> providers = new LinkedHashMap<>();
        Instant cutoff = now.minus(RETENTION);
        for (ProviderReport r : snapshot.providers()) {
            List<HistoryPoint> points = new ArrayList<>(previous.pointsFor(r.id()));
            Long latency = r.probe() == null ? null : r.probe().latencyMs();
            points.add(new HistoryPoint(now, r.condition(), latency));
            points.removeIf(p -> p.ts() == null || p.ts().isBefore(cutoff));
            if (points.size() > MAX_POINTS) {
                points = new ArrayList<>(points.subList(points.size() - MAX_POINTS, points.size()));
            }
            providers.put(r.id(), points);
        }
        Map<String, ModelInfo> models = currentModels == null || currentModels.isEmpty()
                ? previous.models()
                : currentModels.stream().collect(Collectors.toMap(ModelInfo::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return new History(providers, models, now);
    }
}
