package no.cantara.llmweather;

import no.cantara.llmweather.model.ModelInfo;
import no.cantara.llmweather.sources.FixtureFetcher;
import no.cantara.llmweather.sources.OpenRouterClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenRouterClientTest {

    @Test
    void convertsPerTokenPricesToPerMillion() throws IOException {
        List<ModelInfo> models = new OpenRouterClient(new FixtureFetcher(Fixtures.DIR)).fetchAll();
        ModelInfo opus = models.stream().filter(m -> m.id().equals("anthropic/claude-opus-5.5")).findFirst().orElseThrow();
        assertEquals(4.0, opus.promptPerMTok());
        assertEquals(20.0, opus.completionPerMTok());
        assertEquals(1_000_000, opus.contextLength());
        assertEquals("anthropic", opus.prefix());
    }

    @Test
    void dynamicPricingBecomesNull() throws IOException {
        List<ModelInfo> models = new OpenRouterClient(new FixtureFetcher(Fixtures.DIR)).fetchAll();
        ModelInfo auto = models.stream().filter(m -> m.id().equals("openrouter/auto")).findFirst().orElseThrow();
        assertNull(auto.promptPerMTok());
    }
}
