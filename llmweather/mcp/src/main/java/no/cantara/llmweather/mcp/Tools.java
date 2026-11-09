package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tool surface. Every tool reads the latest forecast snapshot and returns a JSON object that carries a
 * {@code meta} block with the observation time and whether the data is stale.
 */
public final class Tools {
    static final Map<String, Integer> SEVERITY = Map.of(
            "SUNNY", 0, "PARTLY_CLOUDY", 1, "CLOUDY", 2, "RAIN", 3, "STORM", 4, "UNKNOWN", -1);

    public record ToolDef(String name, String description, ObjectNode inputSchema) {}

    private final ForecastSource source;

    public Tools(ForecastSource source) {
        this.source = source;
    }

    public List<ToolDef> list() {
        return List.of(
                new ToolDef("get_bulletin",
                        "Today's LLM market weather in one read: headline, severity (calm, unsettled, stormy), the prose bulletin and the outlook. Start here when you need the overall picture.",
                        schema(Map.of())),
                new ToolDef("get_conditions",
                        "Current conditions per provider (weather station): condition from SUNNY to STORM, status page description, pressure trend, probe latency, active incidents and scheduled maintenance. Optionally filter to one provider by id or name.",
                        schema(Map.of("provider", prop("string", "Provider id or name, e.g. 'anthropic' or 'OpenAI'. Omit for all providers.")))),
                new ToolDef("get_alerts",
                        "Severe weather alerts: major and critical incidents and failed API probes, severest first. Empty when the market is calm.",
                        schema(Map.of())),
                new ToolDef("get_fronts",
                        "Changes since the previous observation: price cuts, price increases, newly listed models and delistings. Optionally filter by type.",
                        schema(Map.of("type", enumProp(List.of("price-drop", "price-rise", "new-model", "deprecation"), "Only fronts of this type.")))),
                new ToolDef("get_model_prices",
                        "Model listings with context length and USD prices per million tokens (input and output), as listed on OpenRouter. Matches the query against model id, model name and provider name; omit the query for every model.",
                        schema(Map.of("query", prop("string", "Substring to match, e.g. 'sonnet', 'gpt-5', 'mistral'. Case-insensitive.")))),
                new ToolDef("pick_fallback",
                        "Given a model you depend on, recommends currently sunny alternatives in a similar price band, preferring other providers. Use when a provider is stormy or before a long batch job.",
                        schema(Map.of(
                                "model", prop("string", "The model id or name you currently use, e.g. 'anthropic/claude-sonnet-5.5' or 'GPT-5.2'."),
                                "max_price_ratio", prop("number", "Allow candidates up to this multiple of the current model's input price. Default 1.5."),
                                "same_provider_ok", prop("boolean", "Also consider models from the same provider. Default false.")),
                                List.of("model"))));
    }

    public ObjectNode call(String name, JsonNode args) throws ToolError {
        ForecastSource.Data data = source.get();
        JsonNode f = data.forecast();
        ObjectNode out = switch (name) {
            case "get_bulletin" -> bulletin(f);
            case "get_conditions" -> conditions(f, text(args, "provider"));
            case "get_alerts" -> alerts(f);
            case "get_fronts" -> fronts(f, text(args, "type"));
            case "get_model_prices" -> prices(f, text(args, "query"));
            case "pick_fallback" -> fallback(f, args);
            default -> throw new ToolError("Unknown tool: " + name);
        };
        ObjectNode meta = out.putObject("meta");
        meta.put("observedAt", f.path("generatedAt").asText(null));
        meta.put("fetchedAt", data.fetchedAt().toString());
        meta.put("stale", data.stale());
        if (data.error() != null) meta.put("error", data.error());
        meta.put("source", "https://cantara.github.io/LLMWeather");
        meta.put("disclaimer", "An indication derived from public status pages and listings, not a monitoring system.");
        return out;
    }

    // ---- tools -------------------------------------------------------------------------------------

    static ObjectNode bulletin(JsonNode f) {
        ObjectNode o = Json.object();
        o.put("headline", f.path("headline").asText());
        o.put("severity", f.path("severity").asText());
        o.put("bulletin", f.path("bulletin").asText());
        o.set("outlook", f.path("outlook"));
        o.put("alerts", f.path("alerts").size());
        return o;
    }

    static ObjectNode conditions(JsonNode f, String provider) throws ToolError {
        ArrayNode stations = Json.array();
        for (JsonNode p : f.path("providers")) {
            if (provider != null && !matches(p, provider)) continue;
            ObjectNode s = stations.addObject();
            s.put("id", p.path("id").asText());
            s.put("name", p.path("name").asText());
            s.put("condition", p.path("condition").asText());
            s.put("statusDescription", p.path("statusDescription").asText(null));
            s.put("statusUrl", p.path("statusUrl").asText(null));
            s.put("pressure", p.path("pressure").asText());
            s.put("pressureNote", p.path("pressureNote").asText(null));
            if (p.hasNonNull("probe")) {
                JsonNode pr = p.get("probe");
                ObjectNode probe = s.putObject("probe");
                probe.put("ok", pr.path("ok").asBoolean());
                probe.put("latencyMs", pr.path("latencyMs").asLong());
                if (pr.hasNonNull("error")) probe.put("error", pr.get("error").asText());
            }
            ArrayNode incidents = s.putArray("incidents");
            for (JsonNode i : p.path("incidents")) {
                ObjectNode inc = incidents.addObject();
                inc.put("name", i.path("name").asText());
                inc.put("impact", i.path("impact").asText(null));
                inc.put("status", i.path("status").asText(null));
                inc.put("since", i.path("createdAt").asText(null));
                inc.put("url", i.path("url").asText(null));
                inc.put("latestUpdate", i.path("latestUpdate").asText(null));
            }
            ArrayNode maint = s.putArray("maintenances");
            for (JsonNode m : p.path("maintenances")) {
                ObjectNode mn = maint.addObject();
                mn.put("name", m.path("name").asText());
                mn.put("status", m.path("status").asText(null));
                mn.put("scheduledFor", m.path("scheduledFor").asText(null));
                mn.put("scheduledUntil", m.path("scheduledUntil").asText(null));
            }
            s.put("models", p.path("models").size());
        }
        if (provider != null && stations.isEmpty()) {
            throw new ToolError("No provider matches '" + provider + "'. Known providers: " + providerNames(f));
        }
        ObjectNode o = Json.object();
        o.put("headline", f.path("headline").asText());
        o.put("severity", f.path("severity").asText());
        o.set("stations", stations);
        return o;
    }

    static ObjectNode alerts(JsonNode f) {
        ObjectNode o = Json.object();
        o.put("severity", f.path("severity").asText());
        o.set("alerts", f.path("alerts"));
        return o;
    }

    static ObjectNode fronts(JsonNode f, String type) throws ToolError {
        if (type != null && !List.of("price-drop", "price-rise", "new-model", "deprecation").contains(type)) {
            throw new ToolError("Unknown front type '" + type + "'. Use price-drop, price-rise, new-model or deprecation.");
        }
        ArrayNode fronts = Json.array();
        for (JsonNode fr : f.path("fronts")) {
            if (type == null || type.equals(fr.path("type").asText())) fronts.add(fr);
        }
        ObjectNode o = Json.object();
        o.set("fronts", fronts);
        o.put("note", f.path("fronts").isEmpty() ? "No fronts: pricing and the model line-up are unchanged since the previous observation." : null);
        return o;
    }

    static ObjectNode prices(JsonNode f, String query) {
        String q = query == null ? null : query.toLowerCase(Locale.ROOT);
        ArrayNode models = Json.array();
        for (JsonNode p : f.path("providers")) {
            String provider = p.path("name").asText();
            for (JsonNode m : p.path("models")) {
                String hay = (m.path("id").asText() + " " + m.path("name").asText() + " " + provider).toLowerCase(Locale.ROOT);
                if (q != null && !hay.contains(q)) continue;
                ObjectNode row = models.addObject();
                row.put("id", m.path("id").asText());
                row.put("name", displayName(m));
                row.put("provider", provider);
                row.put("providerCondition", p.path("condition").asText());
                row.set("contextLength", m.path("contextLength"));
                row.set("inputPerMTok", m.path("promptPerMTok"));
                row.set("outputPerMTok", m.path("completionPerMTok"));
                row.put("listed", m.path("created").asLong(0) > 0 ? java.time.Instant.ofEpochSecond(m.path("created").asLong()).toString().substring(0, 10) : null);
                for (JsonNode fr : f.path("fronts")) {
                    if (fr.path("model").asText().equals(displayName(m)) && fr.path("type").asText().startsWith("price")) {
                        row.put("recentPriceChange", fr.path("detail").asText());
                    }
                }
            }
        }
        ObjectNode o = Json.object();
        o.put("unit", "USD per million tokens, as listed on OpenRouter");
        o.set("models", models);
        return o;
    }

    static ObjectNode fallback(JsonNode f, JsonNode args) throws ToolError {
        String wanted = text(args, "model");
        if (wanted == null || wanted.isBlank()) throw new ToolError("'model' is required");
        double ratio = args.path("max_price_ratio").isNumber() ? args.get("max_price_ratio").asDouble() : 1.5;
        boolean sameOk = args.path("same_provider_ok").asBoolean(false);

        // find the model and its station
        JsonNode origStation = null;
        JsonNode orig = null;
        String w = wanted.toLowerCase(Locale.ROOT);
        for (JsonNode p : f.path("providers")) {
            for (JsonNode m : p.path("models")) {
                if (m.path("id").asText().equalsIgnoreCase(wanted)) { orig = m; origStation = p; }
            }
        }
        if (orig == null) {
            for (JsonNode p : f.path("providers")) {
                for (JsonNode m : p.path("models")) {
                    if (m.path("name").asText().toLowerCase(Locale.ROOT).contains(w) || m.path("id").asText().toLowerCase(Locale.ROOT).contains(w)) {
                        if (orig == null || m.path("created").asLong() > orig.path("created").asLong()) { orig = m; origStation = p; }
                    }
                }
            }
        }
        if (orig == null) throw new ToolError("No listed model matches '" + wanted + "'. Try get_model_prices to find the id.");

        Double origPrice = orig.path("promptPerMTok").isNumber() ? orig.get("promptPerMTok").asDouble() : null;
        Integer origCtx = orig.path("contextLength").isNumber() ? orig.get("contextLength").asInt() : null;
        String origCond = origStation.path("condition").asText();
        int origSev = SEVERITY.getOrDefault(origCond, -1);

        record Cand(JsonNode model, JsonNode station, double score, String reason) {}
        List<Cand> cands = new ArrayList<>();
        for (JsonNode p : f.path("providers")) {
            boolean same = p.path("id").asText().equals(origStation.path("id").asText());
            if (same && !sameOk) continue;
            int sev = SEVERITY.getOrDefault(p.path("condition").asText(), -1);
            if (sev < 0 || sev > 1) continue; // only sunny or partly cloudy stations
            for (JsonNode m : p.path("models")) {
                if (m.path("id").asText().equals(orig.path("id").asText())) continue;
                Double price = m.path("promptPerMTok").isNumber() ? m.get("promptPerMTok").asDouble() : null;
                Integer ctx = m.path("contextLength").isNumber() ? m.get("contextLength").asInt() : null;
                if (origPrice != null && price != null && price > origPrice * ratio) continue;
                if (origCtx != null && ctx != null && ctx < origCtx * 0.5) continue;
                double score = sev * 10;
                List<String> why = new ArrayList<>();
                why.add(p.path("name").asText() + " is " + p.path("condition").asText().toLowerCase(Locale.ROOT).replace('_', ' '));
                if (origPrice != null && price != null && origPrice > 0 && price > 0) {
                    double gap = Math.abs(Math.log(price / origPrice));
                    score += gap * 4;
                    why.add(price <= origPrice ? "same price or cheaper" : "within " + Math.round(ratio * 100 - 100) + "% of your price");
                } else {
                    score += 3;
                }
                if (origCtx != null && ctx != null && ctx < origCtx) { score += 2; why.add("smaller context"); }
                if ("RISING".equals(p.path("pressure").asText())) { score -= 1; why.add("pressure rising"); }
                if ("FALLING".equals(p.path("pressure").asText())) { score += 2; why.add("pressure falling"); }
                cands.add(new Cand(m, p, score, String.join(", ", why)));
            }
        }
        cands.sort(Comparator.comparingDouble(Cand::score));

        ObjectNode o = Json.object();
        ObjectNode current = o.putObject("current");
        current.put("id", orig.path("id").asText());
        current.put("name", displayName(orig));
        current.put("provider", origStation.path("name").asText());
        current.put("condition", origCond);
        current.put("statusDescription", origStation.path("statusDescription").asText(null));
        current.set("inputPerMTok", orig.path("promptPerMTok"));
        current.set("contextLength", orig.path("contextLength"));

        String advice;
        if (origSev == 0) advice = "No need to switch: " + origStation.path("name").asText() + " is sunny. Candidates listed in case you want a standby.";
        else if (origSev == 1) advice = origStation.path("name").asText() + " has a few clouds. Keep using it but wire up one of these as a fallback.";
        else if (origSev < 0) advice = "No observation for " + origStation.path("name").asText() + ". Pick a fallback from a reporting provider if the job matters.";
        else advice = origStation.path("name").asText() + " is reporting " + origCond.toLowerCase(Locale.ROOT) + ". Route to one of these now.";
        o.put("advice", advice);

        ArrayNode list = o.putArray("candidates");
        for (Cand c : cands.subList(0, Math.min(3, cands.size()))) {
            ObjectNode row = list.addObject();
            row.put("id", c.model().path("id").asText());
            row.put("name", displayName(c.model()));
            row.put("provider", c.station().path("name").asText());
            row.put("condition", c.station().path("condition").asText());
            row.set("inputPerMTok", c.model().path("promptPerMTok"));
            row.set("outputPerMTok", c.model().path("completionPerMTok"));
            row.set("contextLength", c.model().path("contextLength"));
            row.put("reason", c.reason());
        }
        if (cands.isEmpty()) o.put("note", "No candidate met the price and context constraints on a sunny provider. Relax max_price_ratio or allow same_provider_ok.");
        return o;
    }

    // ---- helpers -----------------------------------------------------------------------------------

    static boolean matches(JsonNode provider, String q) {
        String x = q.toLowerCase(Locale.ROOT);
        return provider.path("id").asText().toLowerCase(Locale.ROOT).equals(x)
                || provider.path("name").asText().toLowerCase(Locale.ROOT).equals(x)
                || provider.path("name").asText().toLowerCase(Locale.ROOT).contains(x);
    }

    static String providerNames(JsonNode f) {
        List<String> names = new ArrayList<>();
        for (JsonNode p : f.path("providers")) names.add(p.path("id").asText());
        return String.join(", ", names);
    }

    static String displayName(JsonNode m) {
        String name = m.path("name").asText(m.path("id").asText());
        int colon = name.indexOf(':');
        return colon > 0 && colon < 24 ? name.substring(colon + 1).trim() : name;
    }

    static String text(JsonNode args, String key) {
        if (args == null) return null;
        JsonNode v = args.path(key);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    static ObjectNode prop(String type, String description) {
        ObjectNode p = Json.object();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    static ObjectNode enumProp(List<String> values, String description) {
        ObjectNode p = prop("string", description);
        ArrayNode e = p.putArray("enum");
        values.forEach(e::add);
        return p;
    }

    static ObjectNode schema(Map<String, ObjectNode> props) {
        return schema(props, List.of());
    }

    static ObjectNode schema(Map<String, ObjectNode> props, List<String> required) {
        ObjectNode s = Json.object();
        s.put("type", "object");
        ObjectNode p = s.putObject("properties");
        props.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> p.set(e.getKey(), e.getValue()));
        if (!required.isEmpty()) {
            ArrayNode r = s.putArray("required");
            required.forEach(r::add);
        }
        s.put("additionalProperties", false);
        return s;
    }
}
