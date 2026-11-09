package no.cantara.llmweather.model;

/** A model as listed on OpenRouter. Prices are USD per million tokens; null when not published or dynamic. */
public record ModelInfo(
        String id,
        String name,
        long created,
        Integer contextLength,
        Double promptPerMTok,
        Double completionPerMTok) {

    /** The provider prefix of an OpenRouter model id, e.g. "anthropic" for "anthropic/claude-sonnet-4.5". */
    public String prefix() {
        int slash = id.indexOf('/');
        return slash < 0 ? id : id.substring(0, slash);
    }
}
