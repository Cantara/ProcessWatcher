package no.cantara.llmweather.forecast;

import no.cantara.llmweather.model.Alert;
import no.cantara.llmweather.model.Condition;
import no.cantara.llmweather.model.Front;
import no.cantara.llmweather.model.Incident;
import no.cantara.llmweather.model.Pressure;
import no.cantara.llmweather.model.ProviderReport;

import java.util.ArrayList;
import java.util.List;

/** Rule-based bulletin prose. Always available; an LLM may rewrite it when a key is configured. */
final class BulletinWriter {
    private BulletinWriter() {}

    static String write(List<ProviderReport> reports, List<Front> fronts, List<Alert> alerts, String severity) {
        List<String> paragraphs = new ArrayList<>();

        paragraphs.add(switch (severity) {
            case "stormy" -> "Severe weather warning in effect. Thunderstorms are hitting at least one major provider and "
                    + "applications depending on it should expect errors and retries.";
            case "unsettled" -> "Unsettled conditions across parts of the LLM market today, with rain over one or more providers. "
                    + "Pack a fallback model.";
            default -> "Fair weather across the LLM market. All stations reporting are operational or close to it.";
        });

        List<String> trouble = new ArrayList<>();
        for (ProviderReport r : reports) {
            if (!r.condition().isKnown() || r.condition() == Condition.SUNNY) {
                continue;
            }
            StringBuilder s = new StringBuilder(r.name()).append(": ").append(r.condition().phrase());
            if (r.statusDescription() != null && !r.statusDescription().isBlank()) {
                s.append(" (").append(r.statusDescription().toLowerCase()).append(")");
            }
            if (!r.incidents().isEmpty()) {
                Incident i = r.incidents().get(0);
                s.append(", ").append(i.name().toLowerCase());
                if (i.status() != null) {
                    s.append(" is ").append(i.status().toLowerCase());
                }
            }
            if (r.probe() != null && !r.probe().ok()) {
                s.append("; our own probe did not get through");
            } else if (r.probe() != null && r.probe().latencyMs() > Forecaster.SLOW_PROBE_MS) {
                s.append("; responses are slow at ").append(r.probe().latencyMs() / 1000).append(" seconds");
            }
            trouble.add(s + ".");
        }
        if (!trouble.isEmpty()) {
            paragraphs.add(String.join(" ", trouble));
        }

        List<String> rising = new ArrayList<>();
        List<String> falling = new ArrayList<>();
        for (ProviderReport r : reports) {
            if (r.pressure() == Pressure.RISING) rising.add(r.name());
            if (r.pressure() == Pressure.FALLING) falling.add(r.name());
        }
        List<String> pressure = new ArrayList<>();
        if (!rising.isEmpty()) pressure.add("High pressure building over " + Forecaster.join(rising) + " as conditions improve.");
        if (!falling.isEmpty()) pressure.add("Pressure falling over " + Forecaster.join(falling) + "; conditions have worsened since yesterday.");
        if (!pressure.isEmpty()) {
            paragraphs.add(String.join(" ", pressure));
        }

        if (!fronts.isEmpty()) {
            List<String> parts = new ArrayList<>();
            int shown = 0;
            for (Front f : fronts) {
                if (shown++ == 3) break;
                parts.add(switch (f.type()) {
                    case "price-drop" -> f.provider() + " cut " + f.model() + " (" + f.detail() + ")";
                    case "price-rise" -> f.provider() + " raised " + f.model() + " (" + f.detail() + ")";
                    case "new-model" -> f.provider() + " listed " + f.model();
                    default -> f.provider() + " delisted " + f.model();
                });
            }
            String more = fronts.size() > 3 ? " and " + (fronts.size() - 3) + " more on the fronts map" : "";
            paragraphs.add("Fronts moving through: " + String.join("; ", parts) + more + ".");
        }

        List<String> silent = reports.stream().filter(r -> !r.condition().isKnown()).map(ProviderReport::name).toList();
        if (!silent.isEmpty()) {
            paragraphs.add("No observations from " + Forecaster.join(silent)
                    + (silent.size() == 1 ? ": that station has" : ": those stations have")
                    + " no public status page and no probe configured.");
        }

        if (alerts.isEmpty() && trouble.isEmpty()) {
            paragraphs.add("A good day to ship.");
        }
        return String.join("\n\n", paragraphs);
    }
}
