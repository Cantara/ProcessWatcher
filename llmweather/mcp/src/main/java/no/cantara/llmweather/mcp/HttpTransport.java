package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streamable HTTP transport (MCP 2025-03-26 and later), stateless flavour: every JSON-RPC request is a POST to
 * /mcp answered with application/json. No sessions, no server-initiated streams, so GET is 405. Also serves
 * /healthz. A small per-IP rate limit protects the public deployment.
 */
public final class HttpTransport {
    static final int MAX_BODY = 64 * 1024;
    static final int REQUESTS_PER_MINUTE = 120;

    private final McpServer server;
    private final HttpServer http;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    record Window(long minute, AtomicInteger count) {}

    public HttpTransport(McpServer server, int port) throws IOException {
        this.server = server;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/mcp", this::mcp);
        http.createContext("/healthz", ex -> respond(ex, 200, "text/plain", "ok"));
        http.createContext("/", ex -> respond(ex, 200, "text/plain",
                "LLMWeather MCP server. POST JSON-RPC to /mcp (Streamable HTTP). https://github.com/Cantara/LLMWeather"));
        http.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(0);
    }

    public int port() {
        return http.getAddress().getPort();
    }

    void mcp(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        if ("OPTIONS".equals(method)) { respond(ex, 204, null, ""); return; }
        if ("GET".equals(method)) { respond(ex, 405, "text/plain", "This server does not open server-to-client streams. POST JSON-RPC to /mcp."); return; }
        if ("DELETE".equals(method)) { respond(ex, 200, null, ""); return; }
        if (!"POST".equals(method)) { respond(ex, 405, "text/plain", "Method not allowed"); return; }
        if (!allow(ex.getRemoteAddress().getAddress().getHostAddress())) {
            ex.getResponseHeaders().add("Retry-After", "60");
            respond(ex, 429, "application/json", McpServer.error(null, -32000, "Rate limit exceeded, try again in a minute").toString());
            return;
        }
        byte[] body;
        try (InputStream in = ex.getRequestBody()) {
            body = in.readNBytes(MAX_BODY + 1);
        }
        if (body.length > MAX_BODY) { respond(ex, 413, "application/json", McpServer.error(null, -32600, "Request too large").toString()); return; }
        JsonNode msg;
        try {
            msg = Json.mapper().readTree(body);
        } catch (IOException e) {
            respond(ex, 400, "application/json", McpServer.error(null, -32700, "Parse error").toString());
            return;
        }
        var response = server.handle(msg);
        if (response.isEmpty()) {
            respond(ex, 202, null, "");
        } else {
            respond(ex, 200, "application/json", response.get().toString());
        }
    }

    boolean allow(String ip) {
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(ip, (k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        if (windows.size() > 10_000) windows.clear();
        return w.count().incrementAndGet() <= REQUESTS_PER_MINUTE;
    }

    static void cors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Accept, Mcp-Session-Id, MCP-Protocol-Version, Authorization");
        ex.getResponseHeaders().add("Access-Control-Expose-Headers", "Mcp-Session-Id, MCP-Protocol-Version");
    }

    static void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) ex.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        if (bytes.length == 0) {
            ex.sendResponseHeaders(status, -1);
        } else {
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
        ex.close();
    }
}
