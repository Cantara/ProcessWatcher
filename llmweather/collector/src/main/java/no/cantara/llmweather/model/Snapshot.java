package no.cantara.llmweather.model;

import java.time.Instant;
import java.util.List;

/** The full forecast as published to the site as forecast.json. severity is calm, unsettled or stormy. */
public record Snapshot(
        Instant generatedAt,
        String headline,
        String bulletin,
        String bulletinAuthor,
        String severity,
        List<Alert> alerts,
        List<ProviderReport> providers,
        List<Front> fronts,
        List<Outlook> outlook,
        List<SourceStatus> sources) {

    public Snapshot withBulletin(String newBulletin, String author) {
        return new Snapshot(generatedAt, headline, newBulletin, author, severity, alerts, providers, fronts, outlook, sources);
    }
}
