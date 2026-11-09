<script setup>
import { ref, computed, onMounted } from 'vue'
import Hero from './components/Hero.vue'
import AlertBanner from './components/AlertBanner.vue'
import ProviderCard from './components/ProviderCard.vue'
import WeatherMap from './components/WeatherMap.vue'
import PriceFrontChart from './components/PriceFrontChart.vue'
import Outlook from './components/Outlook.vue'
import ModelTable from './components/ModelTable.vue'
import SourcesFooter from './components/SourcesFooter.vue'

const forecast = ref(null)
const error = ref(null)
const theme = ref(readTheme())

function readTheme() {
  try { return localStorage.getItem('llmweather-theme') || 'auto' } catch { return 'auto' }
}
function applyTheme(value) {
  theme.value = value
  const root = document.documentElement
  if (value === 'auto') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', value)
  try { localStorage.setItem('llmweather-theme', value) } catch { /* private window */ }
}
function cycleTheme() {
  applyTheme(theme.value === 'auto' ? 'dark' : theme.value === 'dark' ? 'light' : 'auto')
}

const stations = computed(() => forecast.value?.providers ?? [])
const reporting = computed(() => stations.value.filter(p => p.condition !== 'UNKNOWN'))
const allModels = computed(() => stations.value.flatMap(p => (p.models || []).map(m => ({ ...m, provider: p.name }))))

onMounted(async () => {
  applyTheme(theme.value)
  try {
    const res = await fetch(`${import.meta.env.BASE_URL}data/forecast.json`, { cache: 'no-store' })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    forecast.value = await res.json()
  } catch (e) {
    error.value = `Could not load the forecast (${e.message}).`
  }
})
</script>

<template>
  <header class="topbar">
    <div class="container topbar-inner">
      <a class="brand" href="#top">
        <svg viewBox="0 0 32 32" width="28" height="28" aria-hidden="true">
          <circle cx="12" cy="12" r="7" fill="var(--sun)" />
          <path d="M9 26h15a5 5 0 0 0 .6-10A7 7 0 0 0 11.3 17 4.5 4.5 0 0 0 9 26z" fill="var(--rain)" opacity="0.85" />
        </svg>
        <span>LLMWeather</span>
      </a>
      <nav class="links">
        <a href="#map">Map</a>
        <a href="#stations">Stations</a>
        <a href="#fronts">Fronts</a>
        <a href="#models">Models</a>
        <button class="theme" type="button" @click="cycleTheme" :title="`Theme: ${theme}`">
          <span v-if="theme === 'auto'">Auto</span>
          <span v-else-if="theme === 'dark'">Dark</span>
          <span v-else>Light</span>
        </button>
      </nav>
    </div>
  </header>

  <main id="top" class="container">
    <p v-if="error" class="card error">{{ error }}</p>
    <p v-else-if="!forecast" class="card muted">Reading the instruments…</p>

    <template v-else>
      <Hero :forecast="forecast" :reporting="reporting.length" :total="stations.length" />
      <AlertBanner :alerts="forecast.alerts" />

      <section id="map" class="section">
        <div class="section-head">
          <h2>Weather map</h2>
          <span class="hint">Hourly conditions across the market, last 48 hours</span>
        </div>
        <WeatherMap :stations="stations" :generated-at="forecast.generatedAt" />
      </section>

      <section id="stations" class="section">
        <div class="section-head">
          <h2>Weather stations</h2>
          <span class="hint">Status page, probe and last 48 hours per provider</span>
        </div>
        <div class="grid">
          <ProviderCard v-for="p in stations" :key="p.id" :station="p" />
        </div>
      </section>

      <section class="section two-col">
        <div id="fronts">
          <div class="section-head">
            <h2>Fronts</h2>
            <span class="hint">Changes since the previous observation</span>
          </div>
          <PriceFrontChart :fronts="forecast.fronts" />
        </div>
        <div>
          <div class="section-head">
            <h2>Outlook</h2>
          </div>
          <Outlook :items="forecast.outlook" />
        </div>
      </section>

      <section id="models" class="section">
        <div class="section-head">
          <h2>Model chart</h2>
          <span class="hint">Prices per million tokens, as listed on OpenRouter</span>
        </div>
        <ModelTable :models="allModels" />
      </section>

      <SourcesFooter :sources="forecast.sources" :generated-at="forecast.generatedAt" />
    </template>
  </main>
</template>

<style scoped>
.topbar { position: sticky; top: 0; z-index: 5; backdrop-filter: blur(10px); background: color-mix(in srgb, var(--surface-0) 85%, transparent); border-bottom: 1px solid var(--border); }
.topbar-inner { display: flex; align-items: center; justify-content: space-between; height: 56px; }
.brand { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--text-primary); letter-spacing: -0.01em; }
.links { display: flex; align-items: center; gap: 18px; font-size: 0.9rem; }
.links a { color: var(--text-secondary); }
.theme { border: 1px solid var(--border); background: var(--surface-1); color: var(--text-secondary); border-radius: 999px; padding: 3px 12px; font-size: 0.8rem; cursor: pointer; }
main { padding-bottom: 48px; }
.error { color: var(--status-critical); margin-top: 24px; }
.two-col { display: grid; grid-template-columns: 3fr 2fr; gap: 24px; }
@media (max-width: 760px) { .two-col { grid-template-columns: 1fr; } .links a { display: none; } }
</style>
