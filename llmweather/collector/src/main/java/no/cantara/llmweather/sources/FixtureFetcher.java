package no.cantara.llmweather.sources;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves recorded responses from a directory, keyed by host: a request to https://status.openai.com/...
 * is answered with {@code <dir>/status.openai.com.json}. A missing file behaves like a network failure.
 */
public final class FixtureFetcher implements HttpFetcher {
    private final Path dir;

    public FixtureFetcher(Path dir) {
        this.dir = dir;
    }

    @Override
    public String get(String url) throws IOException {
        String host = URI.create(url).getHost();
        Path file = dir.resolve(host + ".json");
        if (!Files.exists(file)) {
            throw new IOException("No fixture " + file.getFileName() + " for " + url);
        }
        return Files.readString(file);
    }
}
