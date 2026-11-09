package no.cantara.llmweather.model;

/** Result of one live round-trip against a provider's API. */
public record Probe(boolean ok, long latencyMs, String model, String error) {}
