package no.cantara.llmweather.model;

/**
 * A "weather front": a change in the market since the previous observation.
 * type is one of price-drop, price-rise, new-model, deprecation. magnitude is the relative
 * price change (negative for drops) or 0 for non-price fronts.
 */
public record Front(String type, String provider, String model, String detail, double magnitude) {}
