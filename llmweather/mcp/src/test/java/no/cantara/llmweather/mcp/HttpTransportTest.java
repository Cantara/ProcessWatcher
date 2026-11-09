package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportTest {
    static HttpTransport transport;
    static HttpClient client = HttpClient.newHttpClient();
    static String base;

    @BeforeAll
    static void start() throws Exception {
        transport = new HttpTransport(new McpServer(TestSource.fixtures()), 0);
        transport.start();
        base = "http://127.0.0.1:" + transport.port();
    }

    @AfterAll
    static void stop() {
        transport.stop();
    }

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void initializeOverHttp() throws Exception {
        HttpResponse<String> res = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"0\"}}}");
        assertEquals(200, res.statusCode());
        assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode body = Json.mapper().readTree(res.body());
        assertEquals("llmweather", body.path("result").path("serverInfo").path("name").asText());
    }

    @Test
    void notificationIsAccepted() throws Exception {
        assertEquals(202, post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode());
    }

    @Test
    void toolCallOverHttp() throws Exception {
        HttpResponse<String> res = post("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"get_alerts\",\"arguments\":{}}}");
        assertEquals(200, res.statusCode());
        JsonNode body = Json.mapper().readTree(res.body());
        assertEquals(7, body.path("id").asInt());
        assertEquals(2, body.path("result").path("structuredContent").path("alerts").size());
    }

    @Test
    void getIsNotAllowedAndBadJsonIsParseError() throws Exception {
        HttpResponse<String> get = client.send(HttpRequest.newBuilder(URI.create(base + "/mcp")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());
        HttpResponse<String> bad = post("{not json");
        assertEquals(400, bad.statusCode());
        assertEquals(-32700, Json.mapper().readTree(bad.body()).path("error").path("code").asInt());
        HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create(base + "/healthz")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
    }
}
