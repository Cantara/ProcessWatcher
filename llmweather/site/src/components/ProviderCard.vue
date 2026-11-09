<script setup>
import { computed } from 'vue'
import WeatherIcon from './WeatherIcon.vue'
import Sparkline from './Sparkline.vue'
import { conditionLabel, conditionStatus, fmtLatency, fmtShort } from '../format.js'

const props = defineProps({ station: { type: Object, required: true } })

const pressure = computed(() => ({
  RISING: { glyph: '▲', text: 'Rising', cls: 'good' },
  FALLING: { glyph: '▼', text: 'Falling', cls: 'serious' },
  STEADY: { glyph: '■', text: 'Steady', cls: 'none' },
}[props.station.pressure] || { glyph: '■', text: 'Steady', cls: 'none' }))

const modelCount = computed(() => (props.station.models || []).length)
</script>

<template>
  <article class="card station">
    <header class="head">
      <WeatherIcon :condition="station.condition" :size="52" />
      <div class="titles">
        <h3>{{ station.name }}</h3>
        <span class="badge" :class="`badge-${conditionStatus(station.condition)}`">{{ conditionLabel(station.condition) }}</span>
      </div>
      <a v-if="station.statusUrl" class="status-link" :href="station.statusUrl" target="_blank" rel="noopener" title="Open status page">status</a>
    </header>

    <p class="desc secondary">{{ station.statusDescription || 'No status page for this provider' }}</p>

    <dl class="stats">
      <div>
        <dt>Pressure</dt>
        <dd :class="`press-${pressure.cls}`" :title="station.pressureNote"><span aria-hidden="true">{{ pressure.glyph }}</span> {{ pressure.text }}</dd>
      </div>
      <div>
        <dt>Probe</dt>
        <dd v-if="station.probe" class="mono" :class="{ 'press-serious': !station.probe.ok }">
          {{ station.probe.ok ? fmtLatency(station.probe.latencyMs) : 'failed' }}
        </dd>
        <dd v-else class="muted">none</dd>
      </div>
      <div>
        <dt>Models</dt>
        <dd class="mono">{{ modelCount || '–' }}</dd>
      </div>
    </dl>

    <Sparkline :points="station.history || []" />

    <ul v-if="station.incidents && station.incidents.length" class="incidents">
      <li v-for="i in station.incidents" :key="i.id">
        <span class="badge" :class="`badge-${i.impact === 'critical' ? 'critical' : i.impact === 'major' ? 'serious' : 'warning'}`">{{ i.impact }}</span>
        <a v-if="i.url" :href="i.url" target="_blank" rel="noopener">{{ i.name }}</a>
        <span v-else>{{ i.name }}</span>
        <span class="muted small">{{ i.status }} · {{ fmtShort(i.updatedAt || i.createdAt) }}</span>
      </li>
    </ul>
    <ul v-if="station.maintenances && station.maintenances.length" class="incidents">
      <li v-for="m in station.maintenances" :key="m.id">
        <span class="badge badge-none">maintenance</span>
        <a v-if="m.url" :href="m.url" target="_blank" rel="noopener">{{ m.name }}</a>
        <span v-else>{{ m.name }}</span>
        <span class="muted small">{{ fmtShort(m.scheduledFor) }}</span>
      </li>
    </ul>
  </article>
</template>

<style scoped>
.station { display: grid; gap: 12px; align-content: start; }
.head { display: flex; align-items: center; gap: 12px; }
.titles { display: grid; gap: 4px; flex: 1; }
h3 { font-size: 1.15rem; font-weight: 700; letter-spacing: -0.01em; }
.titles .badge { justify-self: start; }
.status-link { font-size: 0.8rem; align-self: start; }
.desc { font-size: 0.9rem; min-height: 1.4em; }
.stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 0; padding: 10px 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
dt { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
dd { margin: 0; font-weight: 600; font-size: 0.95rem; }
.press-good { color: var(--status-good); }
.press-serious { color: var(--status-serious); }
.press-none { color: var(--text-secondary); }
.incidents { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; font-size: 0.88rem; }
.incidents li { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.small { font-size: 0.78rem; }
</style>
