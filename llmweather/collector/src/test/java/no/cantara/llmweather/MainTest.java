package no.cantara.llmweather;

import no.cantara.llmweather.model.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void endToEndRunWritesForecastAndHistory(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("out");
        Path history = tmp.resolve("history");
        Files.createDirectories(history);
        Files.copy(Fixtures.DIR.resolve("history.json"), history.resolve("history.json"));

        Main.Options options = new Main.Options(out, history, null, Fixtures.DIR, true, false);
        Snapshot first = Main.run(options);

        assertTrue(Files.exists(out.resolve("forecast.json")));
        assertTrue(Files.exists(out.resolve("history.json")));
        Snapshot reread = Json.mapper().readValue(Files.readString(out.resolve("forecast.json")), Snapshot.class);
        assertEquals(first.headline(), reread.headline());
        assertEquals(8, reread.providers().size());
        assertTrue(reread.sources().stream().anyMatch(s -> s.source().contains("xAI") && !s.ok()), "failed fetch is reported, not hidden");

        Snapshot second = Main.run(options);
        assertTrue(second.fronts().isEmpty(), "second run against identical data has no fronts");
    }
}
