package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {
    private final McpServer server = new McpServer(TestSource.fixtures());

    private JsonNode call(String method, ObjectNode params) {
        ObjectNode req = Json.object();
        req.put("jsonrpc", "2.0").put("id", 1).put("method", method);
        if (params != null) req.set("params", params);
        return server.handle(req).orElseThrow();
    }

    private JsonNode tool(String name, ObjectNode args) {
        ObjectNode p = Json.object();
        p.put("name", name);
        p.set("arguments", args);
        return call("tools/call", p).path("result");
    }

    @Test
    void initializeNegotiatesProtocolAndAdvertisesCapabilities() {
        JsonNode r = call("initialize", Json.object().put("protocolVersion", "2025-03-26")).path("result");
        assertEquals("2025-03-26", r.path("protocolVersion").asText());
        assertTrue(r.path("capabilities").has("tools"));
        assertTrue(r.path("capabilities").has("resources"));
        assertTrue(r.path("capabilities").has("prompts"));
        assertEquals("llmweather", r.path("serverInfo").path("name").asText());
        assertTrue(r.path("instructions").asText().contains("pick_fallback"));

        JsonNode unknown = call("initialize", Json.object().put("protocolVersion", "1999-01-01")).path("result");
        assertEquals("2025-06-18", unknown.path("protocolVersion").asText(), "falls back to the newest supported version");
    }

    @Test
    void notificationsGetNoResponse() {
        ObjectNode n = Json.object();
        n.put("jsonrpc", "2.0").put("method", "notifications/initialized");
        assertEquals(Optional.empty(), server.handle(n));
    }

    @Test
    void listsSixReadOnlyTools() {
        JsonNode tools = call("tools/list", null).path("result").path("tools");
        assertEquals(6, tools.size());
        for (JsonNode t : tools) {
            assertTrue(t.path("annotations").path("readOnlyHint").asBoolean(), t.path("name").asText());
            assertEquals("object", t.path("inputSchema").path("type").asText());
        }
    }

    @Test
    void bulletinAndAlerts() {
        JsonNode b = tool("get_bulletin", Json.object());
        assertFalse(b.path("isError").asBoolean());
        assertEquals("stormy", b.path("structuredContent").path("severity").asText());
        assertTrue(b.path("structuredContent").path("headline").asText().startsWith("Storms over Mistral"));
        assertEquals(2, b.path("structuredContent").path("outlook").size());

        JsonNode a = tool("get_alerts", Json.object()).path("structuredContent");
        assertEquals(2, a.path("alerts").size());
        assertEquals("severe", a.path("alerts").get(0).path("level").asText());
        assertFalse(a.path("meta").path("stale").asBoolean());
    }

    @Test
    void conditionsFilterByProviderAndRejectUnknown() {
        JsonNode all = tool("get_conditions", Json.object()).path("structuredContent");
        assertEquals(8, all.path("stations").size());

        JsonNode one = tool("get_conditions", Json.object().put("provider", "Mistral")).path("structuredContent");
        assertEquals(1, one.path("stations").size());
        assertEquals("STORM", one.path("stations").get(0).path("condition").asText());
        assertEquals(1, one.path("stations").get(0).path("incidents").size());

        JsonNode bad = tool("get_conditions", Json.object().put("provider", "nonexistent"));
        assertTrue(bad.path("isError").asBoolean());
        assertTrue(bad.path("content").get(0).path("text").asText().contains("Known providers"));
    }

    @Test
    void frontsFilterByType() {
        JsonNode drops = tool("get_fronts", Json.object().put("type", "price-drop")).path("structuredContent").path("fronts");
        assertEquals(3, drops.size());
        assertTrue(drops.get(0).path("model").asText().contains("GPT-5.2"));
        JsonNode bad = tool("get_fronts", Json.object().put("type", "hurricane"));
        assertTrue(bad.path("isError").asBoolean());
    }

    @Test
    void pricesMatchQueryAndCarryRecentChange() {
        JsonNode rows = tool("get_model_prices", Json.object().put("query", "gpt-5")).path("structuredContent").path("models");
        assertEquals(2, rows.size());
        JsonNode gpt52 = rows.get(0).path("id").asText().equals("openai/gpt-5.2") ? rows.get(0) : rows.get(1);
        assertEquals(1.75, gpt52.path("inputPerMTok").asDouble());
        assertTrue(gpt52.path("recentPriceChange").asText().contains("down 30%"));
    }

    @Test
    void fallbackPrefersSunnyOtherProvidersInPriceBand() {
        JsonNode r = tool("pick_fallback", Json.object().put("model", "mistralai/mistral-large-3")).path("structuredContent");
        assertEquals("STORM", r.path("current").path("condition").asText());
        assertTrue(r.path("advice").asText().contains("Route to one of these now"));
        JsonNode cands = r.path("candidates");
        assertTrue(cands.size() >= 1 && cands.size() <= 3);
        for (JsonNode c : cands) {
            assertFalse(c.path("provider").asText().equals("Mistral"), "never the stormy provider itself");
            assertTrue(c.path("condition").asText().equals("SUNNY") || c.path("condition").asText().equals("PARTLY_CLOUDY"));
            assertTrue(c.path("inputPerMTok").asDouble() <= 2.0 * 1.5, "within 1.5x of $2.00");
        }
        assertEquals("SUNNY", cands.get(0).path("condition").asText(), "sunny stations rank before partly cloudy ones");
    }

    @Test
    void fallbackOnSunnyProviderSaysNoNeedToSwitch() {
        JsonNode r = tool("pick_fallback", Json.object().put("model", "Claude Sonnet 5.5")).path("structuredContent");
        assertEquals("anthropic/claude-sonnet-5.5", r.path("current").path("id").asText());
        assertTrue(r.path("advice").asText().startsWith("No need to switch"));
    }

    @Test
    void resourcesAndPrompts() {
        JsonNode list = call("resources/list", null).path("result").path("resources");
        assertEquals(4, list.size());
        JsonNode read = call("resources/read", Json.object().put("uri", "llmweather://bulletin")).path("result");
        assertTrue(read.path("contents").get(0).path("text").asText().startsWith("Storms over Mistral"));
        JsonNode hist = call("resources/read", Json.object().put("uri", "llmweather://history")).path("result");
        assertTrue(hist.path("contents").get(0).path("text").asText().contains("\"providers\""));

        JsonNode prompts = call("prompts/list", null).path("result").path("prompts");
        assertEquals(2, prompts.size());
        ObjectNode p = Json.object();
        p.put("name", "fallback_plan");
        p.putObject("arguments").put("model", "gpt-5.2");
        JsonNode got = call("prompts/get", p).path("result");
        assertTrue(got.path("messages").get(0).path("content").path("text").asText().contains("gpt-5.2"));
    }

    @Test
    void protocolErrors() {
        assertEquals(-32601, call("nope/method", null).path("error").path("code").asInt());
        assertEquals(-32602, call("resources/read", Json.object()).path("error").path("code").asInt());
        ObjectNode garbage = Json.object();
        garbage.put("foo", "bar");
        assertEquals(-32600, server.handle(garbage).orElseThrow().path("error").path("code").asInt());
    }
}
