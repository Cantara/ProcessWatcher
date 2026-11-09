package no.cantara.llmweather.model;

import java.time.Instant;

/** One past observation of a provider. latencyMs is null when no probe ran. */
public record HistoryPoint(Instant ts, Condition condition, Long latencyMs) {}
