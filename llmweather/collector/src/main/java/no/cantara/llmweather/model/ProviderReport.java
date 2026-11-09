package no.cantara.llmweather.model;

import java.util.List;

/** The forecast for one provider ("weather station"). */
public record ProviderReport(
        String id,
        String name,
        String statusUrl,
        Condition condition,
        String statusIndicator,
        String statusDescription,
        List<Incident> incidents,
        List<Maintenance> maintenances,
        Pressure pressure,
        String pressureNote,
        Probe probe,
        List<ModelInfo> models,
        List<HistoryPoint> history) {}
