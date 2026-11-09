package no.cantara.llmweather.model;

/** Weather condition of a provider, ordered from best to worst. UNKNOWN means no observation. */
public enum Condition {
    SUNNY(0, "clear skies"),
    PARTLY_CLOUDY(1, "a few clouds"),
    CLOUDY(2, "overcast"),
    RAIN(3, "rain"),
    STORM(4, "thunderstorms"),
    UNKNOWN(-1, "no observation");

    private final int severity;
    private final String phrase;

    Condition(int severity, String phrase) {
        this.severity = severity;
        this.phrase = phrase;
    }

    public int severity() {
        return severity;
    }

    public String phrase() {
        return phrase;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public static Condition ofSeverity(int severity) {
        for (Condition c : values()) {
            if (c.severity == severity) {
                return c;
            }
        }
        return severity < 0 ? UNKNOWN : STORM;
    }

    /** The worse of two conditions; UNKNOWN never wins over a known condition. */
    public static Condition worse(Condition a, Condition b) {
        if (!a.isKnown()) return b;
        if (!b.isKnown()) return a;
        return a.severity >= b.severity ? a : b;
    }

    /** One step worse, capped at STORM. UNKNOWN stays UNKNOWN. */
    public Condition worsen() {
        if (!isKnown() || this == STORM) return this;
        return ofSeverity(severity + 1);
    }
}
