package no.cantara.llmweather.sources;

import java.io.IOException;

/** Minimal GET abstraction so sources can be fed from recorded fixtures in tests. */
public interface HttpFetcher {
    String get(String url) throws IOException;
}
