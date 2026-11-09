package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * Transport-independent Model Context Protocol server: takes a JSON-RPC 2.0 message, returns the response.
 * Implements initialize, ping, tools, resources and prompts for a stateless, read-only server.
 * Notifications produce no response. The protocol is small enough that speaking it directly keeps this
 * module free of framework dependencies and easy to audit.
 */
public final class McpServer {
    public static final String NAME = "llmweather";
    public static final String VERSION = "0.1.0";
    static final List<String> PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");

    static final String INSTRUCTIONS = """
            LLMWeather is a weather report for the LLM market, refreshed hourly from public provider status
            pages, API probes and OpenRouter listings. Call get_bulletin for the overall picture, get_conditions
            for a provider, get_alerts before retrying or rerouting, get_fronts for price and line-up changes,
            get_model_prices to look up a model, and pick_fallback when a provider you depend on is stormy.
            Every result carries meta.observedAt and meta.stale; treat the forecast as an indication, not as
            monitoring, and never as the only signal for automatic failover.
            """;

    private final Tools tools;
    private final ForecastSource source;

    public McpServer(ForecastSource source) {
        this.source = source;
        this.tools = new Tools(source);
    }

    /** Handles one message or a batch. Returns empty when nothing should be sent back (notifications only). */
    public Optional<JsonNode> handle(JsonNode message) {
        if (message == null || message.isMissingNode()) {
            return Optional.of(error(null, -32700, "Parse error"));
        }
        if (message.isArray()) {
            ArrayNode out = Json.array();
            for (JsonNode m : message) {
                handleOne(m).ifPresent(out::add);
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        }
        return handleOne(message);
    }

    Optional<JsonNode> handleOne(JsonNode msg) {
        if (!msg.isObject() || !msg.has("method")) {
            if (msg.isObject() && (msg.has("result") || msg.has("error"))) {
                return Optional.empty(); // a response from the client, nothing to do
            }
            return Optional.of(error(msg.path("id"), -32600, "Invalid request"));
        }
        String method = msg.get("method").asText();
        JsonNode id = msg.get("id");
        JsonNode params = msg.path("params");
        boolean notification = id == null || id.isNull();
        if (notification) {
            return Optional.empty(); // notifications/initialized, notifications/cancelled, ...
        }
        try {
            JsonNode result = switch (method) {
                case "initialize" -> initialize(params);
                case "ping" -> Json.object();
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(params);
                case "resources/list" -> resourcesList();
                case "resources/templates/list" -> Json.object().set("resourceTemplates", Json.array());
                case "resources/read" -> resourcesRead(params);
                case "prompts/list" -> promptsList();
                case "prompts/get" -> promptsGet(params);
                case "logging/setLevel" -> Json.object();
                default -> null;
            };
            if (result == null) {
                return Optional.of(error(id, -32601, "Method not found: " + method));
            }
            ObjectNode res = Json.object();
            res.put("jsonrpc", "2.0");
            res.set("id", id);
            res.set("result", result);
            return Optional.of(res);
        } catch (InvalidParams e) {
            return Optional.of(error(id, -32602, e.getMessage()));
        } catch (RuntimeException e) {
            return Optional.of(error(id, -32603, "Internal error: " + e.getMessage()));
        }
    }

    static final class InvalidParams extends RuntimeException {
        InvalidParams(String m) { super(m); }
    }

    JsonNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText(PROTOCOL_VERSIONS.get(0));
        String version = PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.get(0);
        ObjectNode r = Json.object();
        r.put("protocolVersion", version);
        ObjectNode caps = r.putObject("capabilities");
        caps.putObject("tools").put("listChanged", false);
        caps.putObject("resources").put("subscribe", false).put("listChanged", false);
        caps.putObject("prompts").put("listChanged", false);
        ObjectNode info = r.putObject("serverInfo");
        info.put("name", NAME);
        info.put("title", "LLMWeather");
        info.put("version", VERSION);
        r.put("instructions", INSTRUCTIONS.strip());
        return r;
    }

    JsonNode toolsList() {
        ObjectNode r = Json.object();
        ArrayNode arr = r.putArray("tools");
        for (Tools.ToolDef t : tools.list()) {
            ObjectNode o = arr.addObject();
            o.put("name", t.name());
            o.put("description", t.description());
            o.set("inputSchema", t.inputSchema());
            ObjectNode ann = o.putObject("annotations");
            ann.put("readOnlyHint", true);
            ann.put("destructiveHint", false);
            ann.put("idempotentHint", true);
            ann.put("openWorldHint", false);
        }
        return r;
    }

    JsonNode toolsCall(JsonNode params) {
        String name = params.path("name").asText(null);
        if (name == null) throw new InvalidParams("tools/call requires 'name'");
        JsonNode args = params.path("arguments");
        ObjectNode r = Json.object();
        ArrayNode content = r.putArray("content");
        try {
            ObjectNode out = tools.call(name, args.isMissingNode() ? Json.object() : args);
            content.addObject().put("type", "text").put("text", pretty(out));
            r.set("structuredContent", out);
            r.put("isError", false);
        } catch (ToolError e) {
            content.addObject().put("type", "text").put("text", e.getMessage());
            r.put("isError", true);
        } catch (IllegalStateException e) {
            content.addObject().put("type", "text").put("text", "Forecast temporarily unavailable: " + e.getMessage());
            r.put("isError", true);
        }
        return r;
    }

    JsonNode resourcesList() {
        ObjectNode r = Json.object();
        ArrayNode arr = r.putArray("resources");
        resource(arr, "llmweather://forecast", "Current forecast", "The full forecast snapshot: headline, bulletin, alerts, per-provider stations with models, fronts, outlook and source status.", "application/json");
        resource(arr, "llmweather://bulletin", "Bulletin", "Headline and prose bulletin as plain text.", "text/plain");
        resource(arr, "llmweather://history", "Observation history", "Per-provider observations for the last seven days and the previously seen model list.", "application/json");
        resource(arr, "llmweather://schema", "forecast.json schema notes", "How conditions, pressure and fronts are derived.", "text/markdown");
        return r;
    }

    static void resource(ArrayNode arr, String uri, String name, String description, String mime) {
        ObjectNode o = arr.addObject();
        o.put("uri", uri);
        o.put("name", name);
        o.put("description", description);
        o.put("mimeType", mime);
    }

    JsonNode resourcesRead(JsonNode params) {
        String uri = params.path("uri").asText(null);
        if (uri == null) throw new InvalidParams("resources/read requires 'uri'");
        ForecastSource.Data data = source.get();
        ObjectNode r = Json.object();
        ArrayNode contents = r.putArray("contents");
        ObjectNode c = contents.addObject();
        c.put("uri", uri);
        switch (uri) {
            case "llmweather://forecast" -> { c.put("mimeType", "application/json"); c.put("text", pretty(data.forecast())); }
            case "llmweather://bulletin" -> {
                c.put("mimeType", "text/plain");
                c.put("text", data.forecast().path("headline").asText() + "\n\n" + data.forecast().path("bulletin").asText());
            }
            case "llmweather://history" -> {
                c.put("mimeType", "application/json");
                c.put("text", data.history() == null ? "{}" : pretty(data.history()));
            }
            case "llmweather://schema" -> { c.put("mimeType", "text/markdown"); c.put("text", SCHEMA_NOTES); }
            default -> throw new InvalidParams("Unknown resource: " + uri);
        }
        return r;
    }

    JsonNode promptsList() {
        ObjectNode r = Json.object();
        ArrayNode arr = r.putArray("prompts");
        ObjectNode brief = arr.addObject();
        brief.put("name", "weather_briefing");
        brief.put("title", "LLM weather briefing");
        brief.put("description", "A short spoken-style briefing on today's LLM market weather and what it means for the user's own stack.");
        brief.putArray("arguments");
        ObjectNode plan = arr.addObject();
        plan.put("name", "fallback_plan");
        plan.put("title", "Fallback plan for a model");
        plan.put("description", "Checks the weather over a model's provider and proposes a concrete fallback routing.");
        ArrayNode args = plan.putArray("arguments");
        ObjectNode a = args.addObject();
        a.put("name", "model");
        a.put("description", "Model id or name the user depends on");
        a.put("required", true);
        return r;
    }

    JsonNode promptsGet(JsonNode params) {
        String name = params.path("name").asText(null);
        JsonNode args = params.path("arguments");
        ObjectNode r = Json.object();
        ArrayNode messages = r.putArray("messages");
        switch (name == null ? "" : name) {
            case "weather_briefing" -> {
                r.put("description", "LLM weather briefing");
                user(messages, """
                        Give me today's LLM weather briefing. Call get_bulletin, then get_alerts. Summarise in the voice of
                        a TV weather presenter, three short paragraphs at most: the overall picture, where it is raining
                        and why, and what is moving (fronts). Finish with one practical sentence for a developer. Only state
                        what the tools return.""");
            }
            case "fallback_plan" -> {
                String model = args.path("model").asText("");
                if (model.isBlank()) throw new InvalidParams("fallback_plan requires argument 'model'");
                r.put("description", "Fallback plan for " + model);
                user(messages, """
                        I depend on the model "%s". Call get_conditions for its provider and pick_fallback for the model.
                        Tell me whether I should switch right now, and propose a concrete routing: primary, first fallback,
                        second fallback, with the price difference per million tokens and any context-length caveat.
                        Base every statement on the tool results.""".formatted(model));
            }
            default -> throw new InvalidParams("Unknown prompt: " + name);
        }
        return r;
    }

    static void user(ArrayNode messages, String text) {
        ObjectNode m = messages.addObject();
        m.put("role", "user");
        m.putObject("content").put("type", "text").put("text", text.strip());
    }

    static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode res = Json.object();
        res.put("jsonrpc", "2.0");
        if (id == null || id.isMissingNode()) res.putNull("id"); else res.set("id", id);
        ObjectNode err = res.putObject("error");
        err.put("code", code);
        err.put("message", message);
        return res;
    }

    static String pretty(JsonNode n) {
        try {
            return Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(n);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return n.toString();
        }
    }

    static final String SCHEMA_NOTES = """
            # forecast.json

            Conditions per provider: SUNNY, PARTLY_CLOUDY, CLOUDY, RAIN, STORM or UNKNOWN (no observation).
            Status page indicator none is SUNNY; minor or maintenance is PARTLY_CLOUDY (CLOUDY with two or more
            active incidents); major is RAIN; critical is STORM. A failed API probe makes the condition at least
            RAIN; a probe slower than 8 seconds worsens it one step.

            Pressure compares the six most recent observations with the rest of the last 24 hours: RISING means
            improving, FALLING means worsening, STEADY otherwise.

            Fronts are changes since the previous observation: price-drop and price-rise (input or output price
            moved 5% or more, magnitude is the relative change), new-model and deprecation (listing appeared or
            disappeared on OpenRouter).

            Prices are USD per million tokens as listed on OpenRouter and may differ from a provider's own list.
            Full contract: https://github.com/Cantara/LLMWeather/blob/main/docs/forecast-schema.md
            """;
}
