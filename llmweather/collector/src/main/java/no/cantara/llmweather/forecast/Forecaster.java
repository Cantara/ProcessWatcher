package no.cantara.llmweather.forecast;

import no.cantara.llmweather.config.ProviderConfig;
import no.cantara.llmweather.history.History;
import no.cantara.llmweather.model.Alert;
import no.cantara.llmweather.model.Condition;
import no.cantara.llmweather.model.Front;
import no.cantara.llmweather.model.HistoryPoint;
import no.cantara.llmweather.model.Incident;
import no.cantara.llmweather.model.Maintenance;
import no.cantara.llmweather.model.ModelInfo;
import no.cantara.llmweather.model.Outlook;
import no.cantara.llmweather.model.Pressure;
import no.cantara.llmweather.model.Probe;
import no.cantara.llmweather.model.ProviderReport;
import no.cantara.llmweather.model.Snapshot;
import no.cantara.llmweather.model.SourceStatus;
import no.cantara.llmweather.sources.StatusPageClient.Summary;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns raw observations into a forecast. Pure and deterministic: the same inputs give the same snapshot,
 * which is what the tests rely on. All thresholds are documented constants.
 */
public final class Forecaster {
    /** A successful probe slower than this worsens the condition by one step. */
    static final long SLOW_PROBE_MS = 8_000;
    /** Price moves smaller than this fraction are noise, not fronts. */
    static final double PRICE_FRONT_THRESHOLD = 0.05;
    static final int MAX_FRONTS = 12;
    static final Duration SPARKLINE_WINDOW = Duration.ofHours(48);
    static final Duration PRESSURE_WINDOW = Duration.ofHours(24);
    static final int PRESSURE_RECENT_POINTS = 6;

    public record Inputs(
            Instant now,
            List<ProviderConfig> providers,
            Map<String, Summary> statuses,
            Map<String, Probe> probes,
            List<ModelInfo> models,
            History history,
            List<SourceStatus> sources) {}

    public Snapshot forecast(Inputs in) {
        List<ProviderReport> reports = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();

        for (ProviderConfig p : in.providers()) {
            Summary s = in.statuses().get(p.id());
            Probe probe = in.probes().get(p.id());
            Condition condition = conditionFor(s, probe);

            List<HistoryPoint> points = new ArrayList<>(in.history().pointsFor(p.id()));
            Pressure pressure = pressureFor(points, condition, in.now());
            points.add(new HistoryPoint(in.now(), condition, probe == null ? null : probe.latencyMs()));
            Instant sparkCutoff = in.now().minus(SPARKLINE_WINDOW);
            points.removeIf(hp -> hp.ts() == null || hp.ts().isBefore(sparkCutoff));

            List<Incident> incidents = s == null ? List.of() : s.incidents().stream().filter(Incident::isActive).toList();
            List<Maintenance> maintenances = s == null ? List.of() : s.maintenances();

            for (Incident i : incidents) {
                if (i.isMajorOrWorse()) {
                    String level = "critical".equalsIgnoreCase(i.impact()) ? "severe" : "warning";
                    alerts.add(new Alert(p.name(), level, i.name(), i.createdAt(), i.url()));
                }
            }
            if (probe != null && !probe.ok()) {
                alerts.add(new Alert(p.name(), "warning", "API probe failed: " + probe.error(), in.now(), null));
            }

            reports.add(new ProviderReport(
                    p.id(),
                    p.name(),
                    p.statusPage(),
                    condition,
                    s == null ? null : s.indicator(),
                    s == null ? (probe == null ? "No weather station" : "Probe only") : s.description(),
                    incidents,
                    maintenances,
                    pressure,
                    pressureNote(pressure),
                    probe,
                    modelsFor(p, in.models()),
                    points));
        }

        alerts.sort(Comparator.comparing((Alert a) -> levelRank(a.level())).reversed());
        List<Front> fronts = frontsFor(in.models(), in.history(), in.providers());
        String severity = severityFor(reports);
        String headline = headlineFor(reports);
        String bulletin = BulletinWriter.write(reports, fronts, alerts, severity);
        List<Outlook> outlook = outlookFor(reports, fronts, in.now());

        return new Snapshot(in.now(), headline, bulletin, "rules", severity, alerts, reports, fronts, outlook, in.sources());
    }

    static Condition conditionFor(Summary s, Probe probe) {
        if (s == null && probe == null) {
            return Condition.UNKNOWN;
        }
        Condition c = Condition.SUNNY;
        if (s != null) {
            c = switch (s.indicator() == null ? "none" : s.indicator().toLowerCase()) {
                case "none" -> Condition.SUNNY;
                case "minor", "maintenance" -> s.activeIncidents() >= 2 ? Condition.CLOUDY : Condition.PARTLY_CLOUDY;
                case "major" -> Condition.RAIN;
                case "critical" -> Condition.STORM;
                default -> Condition.CLOUDY;
            };
        }
        if (probe != null) {
            if (!probe.ok()) {
                c = Condition.worse(c, Condition.RAIN);
            } else if (probe.latencyMs() > SLOW_PROBE_MS) {
                c = c.worsen();
            }
        }
        return c;
    }

    /** Compares the mean severity of the most recent observations with the rest of the last day. */
    static Pressure pressureFor(List<HistoryPoint> history, Condition current, Instant now) {
        Instant cutoff = now.minus(PRESSURE_WINDOW);
        List<Integer> severities = new ArrayList<>();
        for (HistoryPoint p : history) {
            if (p.ts() != null && !p.ts().isBefore(cutoff) && p.condition() != null && p.condition().isKnown()) {
                severities.add(p.condition().severity());
            }
        }
        if (current.isKnown()) {
            severities.add(current.severity());
        }
        if (severities.size() < 4) {
            return Pressure.STEADY;
        }
        int split = Math.max(1, severities.size() - PRESSURE_RECENT_POINTS);
        double earlier = severities.subList(0, split).stream().mapToInt(Integer::intValue).average().orElse(0);
        double recent = severities.subList(split, severities.size()).stream().mapToInt(Integer::intValue).average().orElse(0);
        double delta = earlier - recent;
        if (delta > 0.5) return Pressure.RISING;
        if (delta < -0.5) return Pressure.FALLING;
        return Pressure.STEADY;
    }

    static String pressureNote(Pressure p) {
        return switch (p) {
            case RISING -> "Improving over the last 24 hours";
            case FALLING -> "Deteriorating over the last 24 hours";
            case STEADY -> "Stable over the last 24 hours";
        };
    }

    static List<ModelInfo> modelsFor(ProviderConfig p, List<ModelInfo> all) {
        if (p.openRouterPrefix() == null || all == null) {
            return List.of();
        }
        return all.stream()
                .filter(m -> m.prefix().equals(p.openRouterPrefix()))
                .sorted(Comparator.comparingLong(ModelInfo::created).reversed())
                .toList();
    }

    static List<Front> frontsFor(List<ModelInfo> current, History history, List<ProviderConfig> providers) {
        if (current == null || current.isEmpty() || !history.hasModels()) {
            return List.of();
        }
        Map<String, String> providerByPrefix = providers.stream()
                .filter(p -> p.openRouterPrefix() != null)
                .collect(Collectors.toMap(ProviderConfig::openRouterPrefix, ProviderConfig::name, (a, b) -> a));
        Map<String, ModelInfo> previous = history.models();
        List<Front> fronts = new ArrayList<>();

        for (ModelInfo m : current) {
            String provider = providerByPrefix.get(m.prefix());
            if (provider == null) {
                continue; // only report on providers we have a station for
            }
            ModelInfo old = previous.get(m.id());
            if (old == null) {
                fronts.add(new Front("new-model", provider, displayName(m),
                        "New model listed" + (m.contextLength() == null ? "" : ", " + formatContext(m.contextLength()) + " context"), 0));
                continue;
            }
            double promptChange = relativeChange(old.promptPerMTok(), m.promptPerMTok());
            double completionChange = relativeChange(old.completionPerMTok(), m.completionPerMTok());
            double magnitude = Math.abs(promptChange) >= Math.abs(completionChange) ? promptChange : completionChange;
            if (Math.abs(magnitude) >= PRICE_FRONT_THRESHOLD) {
                String type = magnitude < 0 ? "price-drop" : "price-rise";
                fronts.add(new Front(type, provider, displayName(m), priceDetail(old, m, promptChange, completionChange), magnitude));
            }
        }
        for (ModelInfo old : previous.values()) {
            String provider = providerByPrefix.get(old.prefix());
            if (provider != null && current.stream().noneMatch(m -> m.id().equals(old.id()))) {
                fronts.add(new Front("deprecation", provider, displayName(old), "No longer listed on OpenRouter", 0));
            }
        }
        fronts.sort(Comparator.comparingDouble((Front f) -> Math.abs(f.magnitude())).reversed()
                .thenComparing(Front::type)
                .thenComparing(Front::model));
        return fronts.size() > MAX_FRONTS ? fronts.subList(0, MAX_FRONTS) : fronts;
    }

    /** OpenRouter names models "Provider: Model"; the provider is already a separate field. */
    static String displayName(ModelInfo m) {
        String name = m.name() == null ? m.id() : m.name();
        int colon = name.indexOf(':');
        return colon > 0 && colon < 24 ? name.substring(colon + 1).trim() : name;
    }

    static double relativeChange(Double before, Double after) {
        if (before == null || after == null || before <= 0) {
            return 0;
        }
        return (after - before) / before;
    }

    static String priceDetail(ModelInfo old, ModelInfo now, double promptChange, double completionChange) {
        List<String> parts = new ArrayList<>();
        if (Math.abs(promptChange) >= PRICE_FRONT_THRESHOLD) {
            parts.add("input " + percent(promptChange) + " to $" + money(now.promptPerMTok()) + "/M");
        }
        if (Math.abs(completionChange) >= PRICE_FRONT_THRESHOLD) {
            parts.add("output " + percent(completionChange) + " to $" + money(now.completionPerMTok()) + "/M");
        }
        return String.join(", ", parts);
    }

    static String percent(double change) {
        long pct = Math.round(Math.abs(change) * 100);
        return (change < 0 ? "down " : "up ") + pct + "%";
    }

    static String money(Double v) {
        if (v == null) return "?";
        return v < 1 ? String.format(java.util.Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "")
                : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    static String formatContext(int tokens) {
        if (tokens >= 1_000_000) return (tokens / 1_000_000) + "M";
        if (tokens >= 1_000) return (tokens / 1_000) + "K";
        return String.valueOf(tokens);
    }

    static String severityFor(List<ProviderReport> reports) {
        Condition worst = Condition.UNKNOWN;
        for (ProviderReport r : reports) {
            worst = Condition.worse(worst, r.condition());
        }
        return switch (worst) {
            case STORM -> "stormy";
            case RAIN, CLOUDY -> "unsettled";
            default -> "calm";
        };
    }

    static String headlineFor(List<ProviderReport> reports) {
        List<ProviderReport> known = reports.stream().filter(r -> r.condition().isKnown()).toList();
        if (known.isEmpty()) {
            return "No observations available";
        }
        long sunny = known.stream().filter(r -> r.condition() == Condition.SUNNY).count();
        List<String> storms = names(known, Condition.STORM);
        List<String> rain = names(known, Condition.RAIN);
        List<String> clouds = known.stream()
                .filter(r -> r.condition() == Condition.PARTLY_CLOUDY || r.condition() == Condition.CLOUDY)
                .map(ProviderReport::name).toList();

        if (!storms.isEmpty()) {
            return "Storms over " + join(storms) + (rain.isEmpty() ? "" : ", showers over " + join(rain));
        }
        if (!rain.isEmpty()) {
            return (sunny * 2 >= known.size() ? "Mostly sunny, showers over " : "Unsettled, showers over ") + join(rain);
        }
        if (clouds.isEmpty()) {
            return "Clear skies across the LLM market";
        }
        return (sunny * 2 >= known.size() ? "Mostly sunny, a few clouds over " : "Cloudy over ") + join(clouds);
    }

    static List<Outlook> outlookFor(List<ProviderReport> reports, List<Front> fronts, Instant now) {
        List<Outlook> out = new ArrayList<>();
        List<String> maint = new ArrayList<>();
        Instant horizon = now.plus(Duration.ofHours(48));
        for (ProviderReport r : reports) {
            for (Maintenance m : r.maintenances()) {
                if (m.scheduledFor() != null && m.scheduledFor().isBefore(horizon) && !"completed".equalsIgnoreCase(m.status())) {
                    maint.add(r.name() + ": " + m.name());
                }
            }
        }
        long monitoring = reports.stream()
                .flatMap(r -> r.incidents().stream())
                .filter(i -> "monitoring".equalsIgnoreCase(i.status()))
                .count();
        long active = reports.stream().mapToLong(r -> r.incidents().size()).sum();

        StringBuilder next24 = new StringBuilder();
        if (!maint.isEmpty()) {
            next24.append("Scheduled maintenance ahead: ").append(String.join("; ", maint)).append(". ");
        }
        if (active == 0) {
            next24.append("Settled conditions expected to continue.");
        } else if (monitoring == active) {
            next24.append("All open incidents are in monitoring; clearing expected.");
        } else {
            next24.append(active).append(active == 1 ? " incident still open" : " incidents still open")
                    .append("; expect intermittent showers until providers post the all-clear.");
        }
        out.add(new Outlook("Next 24 hours", next24.toString().trim()));

        long drops = fronts.stream().filter(f -> f.type().equals("price-drop")).count();
        long rises = fronts.stream().filter(f -> f.type().equals("price-rise")).count();
        long fresh = fronts.stream().filter(f -> f.type().equals("new-model")).count();
        StringBuilder week = new StringBuilder();
        if (drops > 0) week.append("A cold front of price cuts is moving through (").append(drops).append(drops == 1 ? " model). " : " models). ");
        if (rises > 0) week.append("Warm air aloft: ").append(rises).append(rises == 1 ? " price increase. " : " price increases. ");
        if (fresh > 0) week.append(fresh).append(fresh == 1 ? " new model has" : " new models have").append(" appeared on the chart. ");
        if (week.isEmpty()) week.append("No fronts on the map; pricing and the model line-up are unchanged since the last observation.");
        out.add(new Outlook("The week ahead", week.toString().trim()));
        return out;
    }

    private static List<String> names(List<ProviderReport> reports, Condition c) {
        return reports.stream().filter(r -> r.condition() == c).map(ProviderReport::name).toList();
    }

    static String join(List<String> names) {
        if (names.size() <= 1) return String.join("", names);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private static int levelRank(String level) {
        return switch (level) {
            case "severe" -> 3;
            case "warning" -> 2;
            default -> 1;
        };
    }
}
