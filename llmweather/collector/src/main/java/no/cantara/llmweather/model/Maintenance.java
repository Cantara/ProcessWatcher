package no.cantara.llmweather.model;

import java.time.Instant;

/** A scheduled maintenance window announced on a status page. */
public record Maintenance(String id, String name, String status, Instant scheduledFor, Instant scheduledUntil, String url) {}
