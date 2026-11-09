package no.cantara.llmweather.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastSourceTest {

    @Test
    void servesStaleDataWhenRefreshFails(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("forecast.json");
        Files.copy(Path.of("src/test/resources/forecast.json"), f);
        Instant[] now = { Instant.parse("2026-11-09T10:00:00Z") };
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now[0]; }
        };
        ForecastSource src = new ForecastSource(f.toUri(), null, Duration.ofSeconds(60), clock);
        ForecastSource.Data first = src.get();
        assertFalse(first.stale());
        String headline = first.forecast().path("headline").asText();

        Files.delete(f);
        now[0] = now[0].plusSeconds(30);
        assertEquals(headline, src.get().forecast().path("headline").asText(), "within ttl: cached");
        assertFalse(src.get().stale());

        now[0] = now[0].plusSeconds(60);
        ForecastSource.Data later = src.get();
        assertTrue(later.stale(), "refresh failed, previous data served and marked stale");
        assertEquals(headline, later.forecast().path("headline").asText());
    }

    @Test
    void pathsBecomeFileUris() {
        assertEquals("file", ForecastSource.toUri("src/test/resources/forecast.json").getScheme());
        assertEquals("https", ForecastSource.toUri("https://example.org/forecast.json").getScheme());
    }
}
