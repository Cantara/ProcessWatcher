package no.cantara.llmweather.config;

import com.fasterxml.jackson.core.type.TypeReference;
import no.cantara.llmweather.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A provider to report on. statusPage is the base URL of a Statuspage.io-hosted status site (or null),
 * openRouterPrefix the id prefix of its models on OpenRouter (or null).
 */
public record ProviderConfig(String id, String name, String statusPage, String openRouterPrefix, ProbeConfig probe) {

    public static List<ProviderConfig> loadDefault() throws IOException {
        try (InputStream in = ProviderConfig.class.getResourceAsStream("/providers.json")) {
            if (in == null) {
                throw new IOException("providers.json not found on classpath");
            }
            return Json.mapper().readValue(in, new TypeReference<List<ProviderConfig>>() {});
        }
    }

    public static List<ProviderConfig> load(Path file) throws IOException {
        return Json.mapper().readValue(Files.readString(file), new TypeReference<List<ProviderConfig>>() {});
    }
}
