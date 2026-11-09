# LLMWeather

**A weather report for the LLM market.** Every hour LLMWeather reads the public status pages of the
major model providers, optionally probes their APIs, pulls prices and model listings from OpenRouter,
and publishes a forecast: today's conditions per provider, pressure trends, price fronts moving through,
severe weather alerts and the outlook.

Status pages are instruments. This is the forecaster standing in front of the map.

> Sideloaded in the ProcessWatcher repository for now; it is laid out as a self-contained repository
> root (`collector/`, `site/`, `.github/`) so it can be moved to `Cantara/LLMWeather` unchanged.

## What it shows

* **Sky chart**: the opening shot. One constellation per provider, one star per model sized by price
  tier, with tonight's weather drawn over it: drifting volumetric clouds, rain, lightning that lights the
  cloud from inside, and a shooting star for every newly listed model. All motion is disabled when the
  visitor prefers reduced motion.
* **Weather map**: one row per provider, one cell per hour for the last 48 hours.
* **Headline and bulletin**: a plain-language report generated from the observations. With an Anthropic
  key configured, Claude rewrites the bulletin as a proper weather presenter would; the facts always come
  from the recorded data, never from the model.
* **Weather stations**: one card per provider with its condition (sunny to storm), status page summary,
  pressure trend, probe latency, active incidents and a 48-hour history strip.
* **Fronts**: price cuts, price increases, new models and delistings since the previous observation.
* **Outlook**: scheduled maintenance ahead and what to expect from open incidents.
* **Model chart**: every listed model with context length and prices per million tokens, filterable
  and sortable.
* **Instruments**: which data sources answered, so a quiet map is never mistaken for a sunny one.

The site is an installable PWA: add it to a phone home screen and the last forecast opens offline.

The whole forecast is also available as machine-readable JSON, see [docs/forecast-schema.md](docs/forecast-schema.md),
and to agents through the **MCP server** in `mcp/`: six read-only tools (`get_bulletin`, `get_conditions`,
`get_alerts`, `get_fronts`, `get_model_prices`, `pick_fallback`), four resources and two prompts, over stdio or
Streamable HTTP. See [docs/mcp.md](docs/mcp.md). Two Agent Skills in `skills/` teach a client when to use it.

## Data sources

| Source | Used for | Needs a key |
|---|---|---|
| Provider status pages (Statuspage.io `api/v2/summary.json`) | Conditions, incidents, maintenance | No |
| [OpenRouter model list](https://openrouter.ai/api/v1/models) | Prices, context lengths, new and removed models | No |
| Live API probes (one tiny request per provider) | Latency and real reachability | Yes, one per provider, optional |
| Anthropic API | Rewriting the bulletin as prose | Yes, optional |

Providers are configured in `collector/src/main/resources/providers.json`. Providers without a public
Statuspage.io site (Google, Meta) report only through probes and show as "no report" without one.
Verify each `statusPage` URL still serves the Statuspage.io API before relying on it.

## Layout

```
collector/   Java 17+ Maven project. Collects, forecasts, writes forecast.json and history.json
site/        Vite + Vue 3 static site that renders forecast.json
mcp/         Java 17+ Maven project. Read-only MCP server over the published forecast (stdio or HTTP)
skills/      Agent Skills that teach a client when and how to use the MCP server
.github/     Hourly collect-build-deploy workflow for GitHub Pages, plus CI
docs/        Data contract and MCP reference
```

## Running locally

Build and test the collector (Java 17 or newer, Maven):

```bash
cd collector
mvn package
```

Produce a forecast from the recorded fixtures (no network needed):

```bash
java -jar target/llmweather-collector.jar \
  --fixtures src/test/resources/fixtures \
  --history /tmp/llmweather-history \
  --out ../site/public/data
```

Or from the live sources:

```bash
java -jar target/llmweather-collector.jar --history .history --out ../site/public/data
```

Flags: `--no-probes` skips API probes even when keys are present, `--llm` lets Claude rewrite the
bulletin when `ANTHROPIC_API_KEY` is set, `--providers FILE` swaps the provider list.

Then run the site:

```bash
cd ../site
npm install
npm run dev
```

Run the MCP server locally against the sample data:

```bash
cd ../mcp
mvn package
java -jar target/llmweather-mcp.jar --stdio --forecast ../site/public/data/forecast.json
```

## Deploying

The `Forecast` workflow runs hourly and on every push to the default branch. It fetches the previous
`history.json` from the live site, runs the collector, builds the site and deploys to GitHub Pages.
No data is committed to git; the history lives on Pages.

Setup in the new repository:

1. Settings, Pages, Source: GitHub Actions.
2. Optionally add repository secrets `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`,
   `MISTRAL_API_KEY`, `XAI_API_KEY`, `DEEPSEEK_API_KEY`, `COHERE_API_KEY`. Each one switches on the
   probe for that provider. The Anthropic key also switches on the LLM-written bulletin.
3. If the repository is not `Cantara/LLMWeather`, change `SITE_URL` and `BASE_PATH` in
   `.github/workflows/forecast.yml`.

Probes cost a few tokens per provider per hour. Everything else is free.

## Limitations

* Status pages understate incidents. Probes are the honest instrument; configure keys if you can.
* Pricing comes from OpenRouter listings, which can differ from a provider's own price list.
* Benchmarks and model quality are not part of the forecast yet.
* The condition mapping is a simple rule set, documented in [docs/forecast-schema.md](docs/forecast-schema.md).
  Treat it as an indication, not as monitoring.

## License

Apache-2.0, Cantara.
