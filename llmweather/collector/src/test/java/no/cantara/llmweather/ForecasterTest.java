package no.cantara.llmweather;

import no.cantara.llmweather.config.ProviderConfig;
import no.cantara.llmweather.forecast.Forecaster;
import no.cantara.llmweather.history.History;
import no.cantara.llmweather.history.HistoryStore;
import no.cantara.llmweather.model.Condition;
import no.cantara.llmweather.model.Front;
import no.cantara.llmweather.model.Pressure;
import no.cantara.llmweather.model.Probe;
import no.cantara.llmweather.model.ProviderReport;
import no.cantara.llmweather.model.Snapshot;
import no.cantara.llmweather.sources.FixtureFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecasterTest {
    private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");

    private List<ProviderConfig> providers;
    private Collector.Result observed;
    private History history;

    @BeforeEach
    void collect() throws Exception {
        providers = ProviderConfig.loadDefault();
        observed = new Collector(new FixtureFetcher(Fixtures.DIR), false).collect(providers);
        history = HistoryStore.load(Fixtures.DIR);
    }

    private Snapshot forecast(Map<String, Probe> probes) {
        return new Forecaster().forecast(new Forecaster.Inputs(
                NOW, providers, observed.statuses(), probes, observed.models(), history, observed.sources()));
    }

    private static ProviderReport report(Snapshot s, String id) {
        return s.providers().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void mapsStatusIndicatorsToConditions() {
        Snapshot s = forecast(Map.of());
        assertEquals(Condition.PARTLY_CLOUDY, report(s, "openai").condition());
        assertEquals(Condition.SUNNY, report(s, "anthropic").condition());
        assertEquals(Condition.STORM, report(s, "mistral").condition());
        assertEquals(Condition.RAIN, report(s, "cohere").condition());
        assertEquals(Condition.UNKNOWN, report(s, "google").condition(), "no status page and no probe");
        assertEquals(Condition.UNKNOWN, report(s, "xai").condition(), "status page fetch failed");
        assertEquals("stormy", s.severity());
        assertTrue(s.headline().startsWith("Storms over Mistral"), s.headline());
    }

    @Test
    void probesRefineConditions() {
        Snapshot s = forecast(Map.of(
                "google", new Probe(true, 650, "gemini-2.5-flash", null),
                "anthropic", new Probe(true, 12_000, "claude-haiku-5-5", null),
                "deepseek", new Probe(false, 30_000, "deepseek-chat", "HTTP 503")));
        assertEquals(Condition.SUNNY, report(s, "google").condition(), "a healthy probe is enough for a station without a status page");
        assertEquals(Condition.PARTLY_CLOUDY, report(s, "anthropic").condition(), "slow probe worsens one step");
        assertEquals(Condition.RAIN, report(s, "deepseek").condition(), "failed probe means at least rain");
        assertTrue(s.alerts().stream().anyMatch(a -> a.provider().equals("DeepSeek") && a.title().contains("probe failed")));
    }

    @Test
    void pressureFollowsTheTrendInHistory() {
        Snapshot s = forecast(Map.of());
        assertEquals(Pressure.FALLING, report(s, "openai").pressure(), "sunny yesterday, rain this morning");
        assertEquals(Pressure.RISING, report(s, "anthropic").pressure(), "rain yesterday, sunny now");
        assertEquals(Pressure.STEADY, report(s, "deepseek").pressure(), "too few points");
    }

    @Test
    void detectsPriceFrontsNewModelsAndDelistings() {
        Snapshot s = forecast(Map.of());
        List<Front> fronts = s.fronts();
        Front gpt52 = fronts.stream().filter(f -> f.model().contains("GPT-5.2")).findFirst().orElseThrow();
        assertEquals("price-drop", gpt52.type());
        assertEquals(-0.30, gpt52.magnitude(), 0.001);
        assertTrue(gpt52.detail().contains("input down 30%"), gpt52.detail());
        assertTrue(fronts.stream().anyMatch(f -> f.type().equals("new-model") && f.model().contains("Opus 5.5")));
        assertTrue(fronts.stream().anyMatch(f -> f.type().equals("deprecation") && f.model().contains("GPT-4.1")));
        assertFalse(fronts.stream().anyMatch(f -> f.model().contains("Haiku")), "unchanged prices are not fronts");
        assertEquals(gpt52, fronts.get(0), "largest price move comes first");
    }

    @Test
    void alertsCoverMajorAndCriticalIncidentsSeverestFirst() {
        Snapshot s = forecast(Map.of());
        assertEquals(2, s.alerts().size());
        assertEquals("severe", s.alerts().get(0).level());
        assertEquals("Mistral", s.alerts().get(0).provider());
        assertEquals("warning", s.alerts().get(1).level());
    }

    @Test
    void bulletinAndOutlookMentionTheFacts() {
        Snapshot s = forecast(Map.of());
        assertTrue(s.bulletin().contains("Severe weather warning"));
        assertTrue(s.bulletin().contains("Mistral: thunderstorms"));
        assertTrue(s.bulletin().contains("High pressure building over Anthropic"));
        assertTrue(s.bulletin().contains("No observations from Google, xAI and Meta Llama"), s.bulletin());
        assertEquals("Next 24 hours", s.outlook().get(0).label());
        assertTrue(s.outlook().get(0).text().contains("Console database maintenance"));
        assertTrue(s.outlook().get(1).text().contains("price cuts"));
    }

    @Test
    void firstRunWithoutHistoryHasNoFronts() {
        Snapshot s = new Forecaster().forecast(new Forecaster.Inputs(
                NOW, providers, observed.statuses(), Map.of(), observed.models(), History.empty(), observed.sources()));
        assertTrue(s.fronts().isEmpty());
        assertTrue(s.providers().stream().allMatch(r -> r.pressure() == Pressure.STEADY));
    }

    @Test
    void historyAppendsOnePointPerProviderAndRemembersModels() {
        Snapshot s = forecast(Map.of());
        History updated = HistoryStore.append(history, s, observed.models(), NOW);
        assertEquals(8, updated.pointsFor("openai").size());
        assertEquals(NOW, updated.pointsFor("openai").get(7).ts());
        assertTrue(updated.models().containsKey("anthropic/claude-opus-5.5"));
        assertFalse(updated.models().containsKey("openai/gpt-4.1"));
    }
}
