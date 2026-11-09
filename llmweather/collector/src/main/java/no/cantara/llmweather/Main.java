package no.cantara.llmweather;

import no.cantara.llmweather.bulletin.ClaudeBulletinWriter;
import no.cantara.llmweather.config.ProviderConfig;
import no.cantara.llmweather.forecast.Forecaster;
import no.cantara.llmweather.history.History;
import no.cantara.llmweather.history.HistoryStore;
import no.cantara.llmweather.model.Snapshot;
import no.cantara.llmweather.model.SourceStatus;
import no.cantara.llmweather.sources.FixtureFetcher;
import no.cantara.llmweather.sources.HttpFetcher;
import no.cantara.llmweather.sources.JdkHttpFetcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Command line entry point.
 *
 * <pre>
 * java -jar llmweather-collector.jar [--out DIR] [--history DIR] [--providers FILE] [--fixtures DIR] [--no-probes] [--llm]
 * </pre>
 *
 * Writes forecast.json (and a copy of history.json) to --out, and updates history.json in --history.
 */
public final class Main {
    public static final String FORECAST_FILE = "forecast.json";

    record Options(Path out, Path history, Path providers, Path fixtures, boolean probes, boolean llm) {
        static Options parse(String[] args) {
            Path out = Path.of("out");
            Path history = Path.of(".history");
            Path providers = null;
            Path fixtures = null;
            boolean probes = true;
            boolean llm = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--out" -> out = Path.of(args[++i]);
                    case "--history" -> history = Path.of(args[++i]);
                    case "--providers" -> providers = Path.of(args[++i]);
                    case "--fixtures" -> fixtures = Path.of(args[++i]);
                    case "--no-probes" -> probes = false;
                    case "--llm" -> llm = true;
                    case "--help", "-h" -> {
                        System.out.println("usage: llmweather-collector [--out DIR] [--history DIR] [--providers FILE] [--fixtures DIR] [--no-probes] [--llm]");
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            return new Options(out, history, providers, fixtures, probes, llm);
        }
    }

    public static void main(String[] args) throws Exception {
        Options o = Options.parse(args);
        Snapshot snapshot = run(o);
        System.out.println("[llmweather] " + snapshot.headline());
        for (SourceStatus s : snapshot.sources()) {
            System.out.println("[llmweather]   " + (s.ok() ? "ok   " : "FAIL ") + s.source() + (s.detail() == null ? "" : " - " + s.detail()));
        }
        System.out.println("[llmweather] wrote " + o.out().resolve(FORECAST_FILE));
    }

    static Snapshot run(Options o) throws Exception {
        List<ProviderConfig> providers = o.providers() != null ? ProviderConfig.load(o.providers()) : ProviderConfig.loadDefault();
        HttpFetcher fetcher = o.fixtures() != null ? new FixtureFetcher(o.fixtures()) : new JdkHttpFetcher();
        boolean probes = o.probes() && o.fixtures() == null;

        Collector.Result observed = new Collector(fetcher, probes).collect(providers);
        History history = HistoryStore.load(o.history());
        Instant now = Instant.now();

        Snapshot snapshot = new Forecaster().forecast(new Forecaster.Inputs(
                now, providers, observed.statuses(), observed.probes(), observed.models(), history, observed.sources()));

        if (o.llm() && ClaudeBulletinWriter.isConfigured()) {
            snapshot = new ClaudeBulletinWriter().rewrite(snapshot).orElse(snapshot);
        }

        History updated = HistoryStore.append(history, snapshot, observed.models(), now);
        HistoryStore.save(updated, o.history());
        Files.createDirectories(o.out());
        Files.writeString(o.out().resolve(FORECAST_FILE), Json.mapper().writeValueAsString(snapshot));
        Files.copy(o.history().resolve(HistoryStore.FILE_NAME), o.out().resolve(HistoryStore.FILE_NAME),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return snapshot;
    }
}
