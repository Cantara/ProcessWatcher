# forecast.json

The collector writes one `forecast.json` per run. The site renders it directly, and it is a stable,
public contract: anything (a bot, a dashboard, another site) may consume it from
`https://cantara.github.io/LLMWeather/data/forecast.json`.

## Top level

| Field | Type | Meaning |
|---|---|---|
| `generatedAt` | ISO-8601 instant | When the observations were taken |
| `headline` | string | One-line summary, e.g. "Mostly sunny, showers over OpenAI" |
| `bulletin` | string | Prose bulletin, paragraphs separated by blank lines |
| `bulletinAuthor` | string | `rules` for the deterministic writer, otherwise the model id that rewrote it |
| `severity` | `calm` / `unsettled` / `stormy` | Worst condition across stations |
| `alerts[]` | Alert | Major and critical incidents, failed probes. Severest first |
| `providers[]` | ProviderReport | One "weather station" per configured provider |
| `fronts[]` | Front | Changes since the previous observation, largest first |
| `outlook[]` | Outlook | "Next 24 hours" and "The week ahead" |
| `sources[]` | SourceStatus | Which instruments answered, so readers can judge the forecast |

## ProviderReport

| Field | Meaning |
|---|---|
| `id`, `name`, `statusUrl` | From `providers.json` |
| `condition` | `SUNNY`, `PARTLY_CLOUDY`, `CLOUDY`, `RAIN`, `STORM` or `UNKNOWN` (no observation) |
| `statusIndicator`, `statusDescription` | Raw Statuspage.io values (`none`, `minor`, `major`, `critical`, `maintenance`) |
| `incidents[]` | Active incidents: `name`, `status`, `impact`, `createdAt`, `updatedAt`, `url`, `latestUpdate` |
| `maintenances[]` | Scheduled maintenance windows |
| `pressure`, `pressureNote` | `RISING` (improving), `STEADY`, `FALLING` (worsening) over the last 24 hours |
| `probe` | `{ok, latencyMs, model, error}` from the live API probe, or null |
| `models[]` | The provider's models on OpenRouter: `id`, `name`, `created`, `contextLength`, `promptPerMTok`, `completionPerMTok` (USD per million tokens) |
| `history[]` | Last 48 hours of `{ts, condition, latencyMs}` for the sparkline |

## How conditions are derived

Deterministic rules in `Forecaster.java`:

1. Status page indicator: `none` is SUNNY, `minor` or `maintenance` is PARTLY_CLOUDY (CLOUDY with two or more
   active incidents), `major` is RAIN, `critical` is STORM.
2. A failed API probe makes the condition at least RAIN. A successful probe slower than 8 seconds worsens it one step.
3. A station with no status page and no probe is UNKNOWN. A station with only a healthy probe is SUNNY.

Pressure compares the mean severity of the six most recent observations with the rest of the last 24 hours.
A difference of more than half a step either way is RISING or FALLING; fewer than four observations is STEADY.

## Fronts

Computed against the model list remembered from the previous run (`history.json`):

| `type` | Trigger | `magnitude` |
|---|---|---|
| `price-drop` / `price-rise` | Input or output price moved 5% or more | Relative change, negative for drops |
| `new-model` | Model id not seen before | 0 |
| `deprecation` | Model id no longer listed | 0 |

The first run has no previous list and therefore no fronts.
