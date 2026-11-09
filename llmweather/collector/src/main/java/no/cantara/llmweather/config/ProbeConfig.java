package no.cantara.llmweather.config;

/**
 * How to probe a provider's API. type is "openai" (OpenAI-compatible chat completions) or "anthropic"
 * (Messages API). apiKeyEnv names the environment variable holding the key; the probe is skipped when unset.
 */
public record ProbeConfig(String type, String url, String model, String apiKeyEnv) {}
