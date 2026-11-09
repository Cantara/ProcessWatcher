# LLMWeather MCP server

A read-only [Model Context Protocol](https://modelcontextprotocol.io) server that gives agents the
forecast as tools, resources and prompts. It reads the published `forecast.json` and `history.json`
(cached for 60 seconds, served stale if the publisher is unreachable) and holds no other state.

## Tools

| Tool | Arguments | Returns |
|---|---|---|
| `get_bulletin` | none | headline, severity, bulletin prose, outlook, alert count |
| `get_conditions` | `provider?` | per-station condition, status description, pressure, probe, incidents, maintenance |
| `get_alerts` | none | severe weather alerts, severest first |
| `get_fronts` | `type?` (`price-drop`, `price-rise`, `new-model`, `deprecation`) | changes since the previous observation |
| `get_model_prices` | `query?` | models with context length and USD per million tokens, plus recent price change |
| `pick_fallback` | `model`, `max_price_ratio?` (1.5), `same_provider_ok?` (false) | current weather over the model's provider, advice, up to three sunny candidates in the price band |

Every result carries `meta.observedAt`, `meta.fetchedAt` and `meta.stale`. All tools are annotated
read-only and idempotent.

## Resources

| URI | Content |
|---|---|
| `llmweather://forecast` | the full forecast snapshot (JSON) |
| `llmweather://bulletin` | headline and bulletin (text) |
| `llmweather://history` | seven days of per-provider observations and the previous model list (JSON) |
| `llmweather://schema` | how conditions, pressure and fronts are derived (Markdown) |

## Prompts

`weather_briefing` (no arguments) and `fallback_plan` (`model`).

## Running

Local, over stdio (Claude Desktop, Claude Code, any stdio client):

```bash
cd mcp && mvn package
java -jar target/llmweather-mcp.jar --stdio
```

Claude Code:

```bash
claude mcp add llmweather -- java -jar /path/to/llmweather-mcp.jar --stdio
```

Public, over Streamable HTTP:

```bash
java -jar target/llmweather-mcp.jar --http 8080
# or
docker build -t llmweather-mcp mcp && docker run -p 8080:8080 llmweather-mcp
```

Clients connect to `http://host:8080/mcp`. The server is stateless: every JSON-RPC request is a POST
answered with JSON, GET returns 405, and there are no sessions to manage, which makes it trivial to run
behind any reverse proxy or on `mcp.cantara.no`. `/healthz` answers `ok`. Requests are limited to 120 per
minute per IP.

Options: `--forecast URL|PATH` and `--history URL|PATH` override the data location (default
`https://cantara.github.io/LLMWeather/data/...`), `--ttl SECONDS` the cache. Environment variables
`LLMWEATHER_FORECAST_URL`, `LLMWEATHER_HISTORY_URL` and `PORT` set the defaults.

## Skills

The `skills/` folder holds two Agent Skills that teach a client when and how to use the server:
`llmweather-check` (read the weather before acting) and `llmweather-fallback` (propose a routing when a
provider is stormy). Copy them into a project's `.claude/skills/` or point the client at the folder.

## Protocol notes

Implements MCP protocol versions 2025-06-18, 2025-03-26 and 2024-11-05 over JSON-RPC 2.0, without
framework dependencies. Supported methods: `initialize`, `ping`, `tools/list`, `tools/call`,
`resources/list`, `resources/templates/list`, `resources/read`, `prompts/list`, `prompts/get`,
`logging/setLevel`. Notifications are accepted and ignored. Tool failures come back as results with
`isError: true`; protocol failures as JSON-RPC errors.
