package no.cantara.llmweather.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Newline-delimited JSON-RPC on stdin/stdout, for local clients such as Claude Desktop and Claude Code. */
public final class StdioTransport {
    private final McpServer server;

    public StdioTransport(McpServer server) {
        this.server = server;
    }

    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            JsonNode msg;
            try {
                msg = Json.mapper().readTree(line);
            } catch (IOException e) {
                out.println(McpServer.error(null, -32700, "Parse error").toString());
                continue;
            }
            server.handle(msg).ifPresent(res -> out.println(res.toString()));
        }
    }
}
