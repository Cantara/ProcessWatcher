---
name: llmweather-fallback
description: Propose a fallback model and routing when a provider the user depends on is degraded or down, or when the user wants a standby before a critical job. Use when the user says a model is failing, asks what to switch to, or asks for a fallback plan. Requires the LLMWeather MCP server.
---

# LLMWeather fallback plan

## Procedure

1. Identify the model the user depends on. If only a provider is named, call `get_model_prices` with the
   provider name and ask which model, or pick their most likely flagship and say so.
2. Call `pick_fallback` with the model id or name. Defaults: candidates up to 1.5x the current input
   price, other providers only. Pass `max_price_ratio` or `same_provider_ok: true` if the user has
   constraints.
3. Read `current.condition` and `advice`. If the provider is sunny, say no switch is needed and offer
   the candidates as a standby.
4. For each candidate, report provider, condition, input and output price per million tokens, context
   length and the `reason` string. Flag a smaller context window explicitly.
5. Propose a routing: primary, first fallback, second fallback. Mention the price difference per million
   tokens and any prompt-format differences the user will have to handle (tool calling, system prompt
   conventions, max output).

## Guardrails

- Only recommend models returned by the tool. Do not invent availability or prices.
- `pick_fallback` only considers providers that are currently sunny or partly cloudy. If it returns no
   candidates, relay its `note` and suggest relaxing the constraints.
- The weather is an indication. Recommend the user keep their own health check in front of any
  automatic failover.
