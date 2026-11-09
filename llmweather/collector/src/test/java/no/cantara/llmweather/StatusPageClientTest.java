package no.cantara.llmweather;

import no.cantara.llmweather.sources.FixtureFetcher;
import no.cantara.llmweather.sources.StatusPageClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPageClientTest {
    private final StatusPageClient client = new StatusPageClient(new FixtureFetcher(Fixtures.DIR));

    @Test
    void parsesIndicatorIncidentsAndLatestUpdate() throws IOException {
        StatusPageClient.Summary s = client.fetch("https://status.openai.com/");
        assertEquals("minor", s.indicator());
        assertEquals("Partially Degraded Service", s.description());
        assertEquals(1, s.incidents().size());
        assertEquals("monitoring", s.incidents().get(0).status());
        assertTrue(s.incidents().get(0).latestUpdate().startsWith("A fix has been implemented"));
        assertNotNull(s.incidents().get(0).createdAt());
        assertEquals(1, s.activeIncidents());
    }

    @Test
    void parsesScheduledMaintenance() throws IOException {
        StatusPageClient.Summary s = client.fetch("https://status.claude.com");
        assertEquals("none", s.indicator());
        assertEquals(1, s.maintenances().size());
        assertEquals("Console database maintenance", s.maintenances().get(0).name());
        assertNotNull(s.maintenances().get(0).scheduledFor());
    }

    @Test
    void missingFixtureBehavesLikeNetworkFailure() {
        assertThrows(IOException.class, () -> client.fetch("https://status.x.ai"));
    }
}
