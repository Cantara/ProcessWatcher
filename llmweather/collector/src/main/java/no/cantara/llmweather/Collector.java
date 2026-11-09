package no.cantara.llmweather;

import no.cantara.llmweather.config.ProviderConfig;
import no.cantara.llmweather.model.ModelInfo;
import no.cantara.llmweather.model.Probe;
import no.cantara.llmweather.model.SourceStatus;
import no.cantara.llmweather.sources.HttpFetcher;
import no.cantara.llmweather.sources.OpenRouterClient;
import no.cantara.llmweather.sources.ProbeRunner;
import no.cantara.llmweather.sources.StatusPageClient;
import no.cantara.llmweather.sources.StatusPageClient.Summary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gathers every observation for one run. A failing source is recorded, never fatal. */
public final class Collector {

    public record Result(Map<String, Summary> statuses, Map<String, Probe> probes, List<ModelInfo> models, List<SourceStatus> sources) {}

    private final StatusPageClient statusPages;
    private final OpenRouterClient openRouter;
    private final ProbeRunner probes;
    private final boolean probesEnabled;

    public Collector(HttpFetcher fetcher, boolean probesEnabled) {
        this.statusPages = new StatusPageClient(fetcher);
        this.openRouter = new OpenRouterClient(fetcher);
        this.probes = new ProbeRunner();
        this.probesEnabled = probesEnabled;
    }

    public Result collect(List<ProviderConfig> providers) {
        Map<String, Summary> statuses = new LinkedHashMap<>();
        Map<String, Probe> probeResults = new LinkedHashMap<>();
        List<SourceStatus> sources = new ArrayList<>();

        for (ProviderConfig p : providers) {
            if (p.statusPage() != null) {
                try {
                    statuses.put(p.id(), statusPages.fetch(p.statusPage()));
                    sources.add(new SourceStatus(p.name() + " status page", true, p.statusPage()));
                } catch (IOException | RuntimeException e) {
                    sources.add(new SourceStatus(p.name() + " status page", false, e.getMessage()));
                }
            }
            if (probesEnabled && p.probe() != null) {
                probes.probe(p.probe()).ifPresent(result -> {
                    probeResults.put(p.id(), result);
                    sources.add(new SourceStatus(p.name() + " API probe", result.ok(), result.ok() ? result.latencyMs() + " ms" : result.error()));
                });
            }
        }

        List<ModelInfo> models = List.of();
        try {
            models = openRouter.fetchAll();
            sources.add(new SourceStatus("OpenRouter model list", true, models.size() + " models"));
        } catch (IOException | RuntimeException e) {
            sources.add(new SourceStatus("OpenRouter model list", false, e.getMessage()));
        }
        return new Result(statuses, probeResults, models, sources);
    }
}
