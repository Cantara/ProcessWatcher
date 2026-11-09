package no.cantara.llmweather.model;

/** A forward-looking statement, e.g. label "Next 24 hours". */
public record Outlook(String label, String text) {}
