---
name: llmweather-check
description: Check today's weather across LLM providers before deploying, starting a batch job, or choosing a model. Use when the user asks whether a provider is up or degraded, what the LLM market looks like right now, whether prices changed, or which model to use today. Requires the LLMWeather MCP server.
---

# LLMWeather check

LLMWeather is a weather report for the LLM market: provider status pages, API probes and OpenRouter
listings, refreshed hourly and read through the `llmweather` MCP server.

## When to use

- Before a deploy, migration or long batch job that depends on a hosted model.
- When a user reports errors or slowness from a provider and asks "is it them or us".
- When the user asks which model to pick today, or whether prices moved.

## Procedure

1. Call `get_bulletin`. Read `severity`: `calm` means proceed, `unsettled` means plan a fallback,
   `stormy` means at least one major provider is down.
2. If the user named a provider or model, call `get_conditions` with that provider. Report the
   `condition`, the `statusDescription`, open `incidents` with their `status` (investigating,
   identified, monitoring) and the `pressure` trend. `RISING` means improving.
3. If severity is not calm, call `get_alerts` and relay each alert with its `since` time and `url`.
4. If the question involves cost or model choice, call `get_fronts` (price moves, new models,
   delistings) and `get_model_prices` for the specific models.

## Reporting

- State the observation time from `meta.observedAt`. If `meta.stale` is true, say the data could
  not be refreshed and give the age.
- Quote conditions in plain words: sunny, partly cloudy, cloudy, rain, storm, no report. A station with
  `UNKNOWN` has no public status page and no probe; say so rather than guessing.
- Prices are USD per million tokens as listed on OpenRouter and may differ from the provider's own list.
- Never present the forecast as monitoring. It is an indication from public sources; recommend the
  team's own probes for anything automated.
