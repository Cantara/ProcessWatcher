package no.cantara.llmweather.mcp;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

final class TestSource {
    static ForecastSource fixtures() {
        return new ForecastSource(
                Path.of("src/test/resources/forecast.json").toUri(),
                Path.of("src/test/resources/history.json").toUri(),
                Duration.ofMinutes(5), Clock.systemUTC());
    }
}
