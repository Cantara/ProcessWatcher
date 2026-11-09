package no.cantara.llmweather.mcp;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * Entry point.
 *
 * <pre>
 * java -jar llmweather-mcp.jar --stdio
 * java -jar llmweather-mcp.jar --http 8080
 *   [--forecast URL-or-path] [--history URL-or-path] [--ttl SECONDS]
 * </pre>
 *
 * Environment variables LLMWEATHER_FORECAST_URL, LLMWEATHER_HISTORY_URL and PORT are honoured as defaults.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        boolean stdio = false;
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        URI forecast = URI.create(System.getenv().getOrDefault("LLMWEATHER_FORECAST_URL", ForecastSource.DEFAULT_FORECAST.toString()));
        URI history = URI.create(System.getenv().getOrDefault("LLMWEATHER_HISTORY_URL", ForecastSource.DEFAULT_HISTORY.toString()));
        Duration ttl = Duration.ofSeconds(60);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--stdio" -> stdio = true;
                case "--http" -> port = Integer.parseInt(args[++i]);
                case "--forecast" -> forecast = ForecastSource.toUri(args[++i]);
                case "--history" -> history = ForecastSource.toUri(args[++i]);
                case "--ttl" -> ttl = Duration.ofSeconds(Long.parseLong(args[++i]));
                case "--help", "-h" -> {
                    System.out.println("usage: llmweather-mcp (--stdio | --http PORT) [--forecast URL|PATH] [--history URL|PATH] [--ttl SECONDS]");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        McpServer server = new McpServer(new ForecastSource(forecast, history, ttl, Clock.systemUTC()));
        if (stdio) {
            new StdioTransport(server).run();
        } else {
            HttpTransport http = new HttpTransport(server, port);
            http.start();
            System.err.println("[llmweather-mcp] listening on http://0.0.0.0:" + http.port() + "/mcp, forecast " + forecast);
            Thread.currentThread().join();
        }
    }
}
