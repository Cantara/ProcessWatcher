package no.cantara.llmweather.sources;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.llmweather.Json;
import no.cantara.llmweather.model.Incident;
import no.cantara.llmweather.model.Maintenance;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Reads the public Statuspage.io v2 summary endpoint that most LLM providers host their status page on. */
public final class StatusPageClient {

    /** indicator is none, minor, major, critical or maintenance. */
    public record Summary(
            String indicator,
            String description,
            List<Incident> incidents,
            List<Maintenance> maintenances,
            Instant updatedAt) {

        public long activeIncidents() {
            return incidents.stream().filter(Incident::isActive).count();
        }
    }

    private final HttpFetcher fetcher;

    public StatusPageClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public Summary fetch(String baseUrl) throws IOException {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return parse(fetcher.get(base + "/api/v2/summary.json"));
    }

    public static Summary parse(String json) throws IOException {
        JsonNode root = Json.mapper().readTree(json);
        JsonNode status = root.path("status");
        String indicator = status.path("indicator").asText("none");
        String description = status.path("description").asText("");
        Instant updatedAt = instant(root.path("page").path("updated_at"));

        List<Incident> incidents = new ArrayList<>();
        for (JsonNode n : root.path("incidents")) {
            String latest = null;
            JsonNode updates = n.path("incident_updates");
            if (updates.isArray() && updates.size() > 0) {
                latest = updates.get(0).path("body").asText(null);
            }
            incidents.add(new Incident(
                    n.path("id").asText(null),
                    n.path("name").asText(""),
                    n.path("status").asText(null),
                    n.path("impact").asText(null),
                    instant(n.path("created_at")),
                    instant(n.path("updated_at")),
                    n.path("shortlink").asText(null),
                    latest));
        }

        List<Maintenance> maintenances = new ArrayList<>();
        for (JsonNode n : root.path("scheduled_maintenances")) {
            maintenances.add(new Maintenance(
                    n.path("id").asText(null),
                    n.path("name").asText(""),
                    n.path("status").asText(null),
                    instant(n.path("scheduled_for")),
                    instant(n.path("scheduled_until")),
                    n.path("shortlink").asText(null)));
        }
        return new Summary(indicator, description, incidents, maintenances, updatedAt);
    }

    private static Instant instant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText()).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
