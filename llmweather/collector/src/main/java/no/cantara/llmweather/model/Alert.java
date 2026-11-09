package no.cantara.llmweather.model;

import java.time.Instant;

/** A severe weather alert. level is "severe", "warning" or "advisory". */
public record Alert(String provider, String level, String title, Instant since, String url) {}
