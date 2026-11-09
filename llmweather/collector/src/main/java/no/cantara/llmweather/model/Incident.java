package no.cantara.llmweather.model;

import java.time.Instant;

/** An incident reported by a provider's status page. */
public record Incident(
        String id,
        String name,
        String status,
        String impact,
        Instant createdAt,
        Instant updatedAt,
        String url,
        String latestUpdate) {

    public boolean isActive() {
        return status == null || !(status.equalsIgnoreCase("resolved") || status.equalsIgnoreCase("postmortem"));
    }

    public boolean isMajorOrWorse() {
        return impact != null && (impact.equalsIgnoreCase("major") || impact.equalsIgnoreCase("critical"));
    }
}
