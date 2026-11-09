package no.cantara.llmweather.bulletin;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import no.cantara.llmweather.Json;
import no.cantara.llmweather.model.Snapshot;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Optional step: asks Claude to rewrite the rule-based bulletin as a proper weather report, given the
 * structured facts. Runs only when ANTHROPIC_API_KEY is set and the collector is started with --llm.
 * The facts come from the snapshot, never from the model, so it cannot invent an outage.
 *
 * Uses the beta Messages endpoint so the request can opt into server-side refusal fallbacks: if a
 * safety classifier declines, the API retries on a fallback model instead of leaving the bulletin empty.
 */
public final class ClaudeBulletinWriter {
    public static final String MODEL = "claude-opus-5-5";

    private static final String SYSTEM = """
            You are the on-air meteorologist for LLMWeather, a daily weather report about the large language
            model market. You receive a JSON snapshot of today's observations: per-provider status page
            conditions, API probe results, pricing changes (fronts) and alerts. Write the bulletin a TV weather
            presenter would read: 3 to 5 short paragraphs, plain language, weather vocabulary used as flavor,
            never as a substitute for facts. Only state what the snapshot supports. Do not invent incidents,
            numbers or causes. Name providers and models exactly as given. No headings, no bullet points,
            no markdown. End with a one-sentence practical recommendation for developers.
            """;

    private final AnthropicClient client;

    public ClaudeBulletinWriter() {
        this(AnthropicOkHttpClient.fromEnv());
    }

    ClaudeBulletinWriter(AnthropicClient client) {
        this.client = client;
    }

    public static boolean isConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }

    public Optional<Snapshot> rewrite(Snapshot snapshot) {
        try {
            String facts = Json.mapper().writeValueAsString(snapshot);
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(4000L)
                    .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                    .systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder().text(SYSTEM).build()))
                    .addUserMessage("Today's snapshot:\n\n" + facts + "\n\nWrite the bulletin.")
                    .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                    .fallbacksDefault()
                    .build();
            BetaMessage message = client.beta().messages().create(params);
            if (message.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
                System.err.println("[llmweather] Claude declined to write the bulletin; keeping the rule-based text");
                return Optional.empty();
            }
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(snapshot.withBulletin(text, MODEL));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("[llmweather] Claude bulletin skipped: " + e.getMessage());
            return Optional.empty();
        }
    }
}
