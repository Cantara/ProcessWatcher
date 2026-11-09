package no.cantara.llmweather.model;

/** Whether a data source answered during collection. Published so readers can judge the forecast. */
public record SourceStatus(String source, boolean ok, String detail) {}
