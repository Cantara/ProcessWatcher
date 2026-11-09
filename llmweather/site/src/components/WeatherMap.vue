<script setup>
import { computed, ref } from 'vue'
import WeatherIcon from './WeatherIcon.vue'
import { CONDITIONS, conditionLabel, conditionStatus, fmtShort, fmtLatency } from '../format.js'

const props = defineProps({
  stations: { type: Array, default: () => [] },
  generatedAt: { type: String, required: true },
  hours: { type: Number, default: 48 },
})

const HOUR = 3600_000
const SEVERITY = { SUNNY: 0, PARTLY_CLOUDY: 1, CLOUDY: 2, RAIN: 3, STORM: 4, UNKNOWN: -1 }

const end = computed(() => Math.ceil(new Date(props.generatedAt).getTime() / HOUR) * HOUR)
const start = computed(() => end.value - props.hours * HOUR)
const buckets = computed(() => Array.from({ length: props.hours }, (_, i) => start.value + i * HOUR))

// One row per station, one cell per hour. A cell with observations takes the worst condition seen in
// that hour; an hour without observations carries the last known condition forward, drawn faint.
const rows = computed(() => props.stations.map(station => {
  const cells = buckets.value.map(t => ({ t, observed: false, condition: null, carried: null, latency: null }))
  for (const p of station.history || []) {
    const idx = Math.floor((new Date(p.ts).getTime() - start.value) / HOUR)
    if (idx < 0 || idx >= cells.length) continue
    const c = cells[idx]
    c.observed = true
    if (c.condition === null || (SEVERITY[p.condition] ?? -1) > (SEVERITY[c.condition] ?? -1)) c.condition = p.condition
    if (p.latencyMs !== null && p.latencyMs !== undefined) c.latency = Math.max(c.latency ?? 0, p.latencyMs)
  }
  let last = null
  for (const c of cells) {
    if (c.observed) last = c.condition
    else c.carried = last
  }
  return { station, cells }
}))

const ticks = computed(() => buckets.value
  .map((t, i) => ({ t, i }))
  .filter(({ t }) => new Date(t).getHours() % 6 === 0))

const tickLabel = t => new Date(t).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
const dayLabel = t => new Date(t).toLocaleDateString(undefined, { weekday: 'short' })

const hover = ref(null)
function cellStyle(c) {
  const cond = c.observed ? c.condition : c.carried
  if (cond === null) return { background: 'transparent', boxShadow: 'inset 0 0 0 1px var(--border)' }
  return { background: `var(--status-${conditionStatus(cond)})`, opacity: c.observed ? 1 : 0.32 }
}
const legend = Object.entries(CONDITIONS).filter(([k]) => k !== 'UNKNOWN')
</script>

<template>
  <div class="card map">
    <div class="rows" @mouseleave="hover = null">
      <div v-for="row in rows" :key="row.station.id" class="row">
        <div class="label">
          <WeatherIcon :condition="row.station.condition" :size="22" />
          <span class="name">{{ row.station.name }}</span>
        </div>
        <div class="cells" role="img" :aria-label="`${row.station.name}: hourly conditions for the last ${hours} hours`">
          <div v-for="c in row.cells" :key="c.t" class="cell"
               :class="{ hot: hover && hover.c === c }"
               :style="cellStyle(c)"
               @mouseenter="hover = { row, c }" />
        </div>
      </div>
      <div class="row axis">
        <div class="label"></div>
        <div class="cells ticks">
          <span v-for="tk in ticks" :key="tk.t" class="tick" :style="{ left: `${(tk.i / hours) * 100}%` }">
            <span class="tick-time">{{ tickLabel(tk.t) }}</span>
            <span v-if="new Date(tk.t).getHours() === 0" class="tick-day">{{ dayLabel(tk.t) }}</span>
          </span>
        </div>
      </div>
    </div>

    <div class="foot">
      <div class="tip" aria-live="polite">
        <template v-if="hover">
          <strong>{{ hover.row.station.name }}</strong>
          <span class="mono">{{ fmtShort(hover.c.t) }}</span>
          <template v-if="hover.c.observed">
            <span>{{ conditionLabel(hover.c.condition) }}</span>
            <span v-if="hover.c.latency !== null" class="mono">{{ fmtLatency(hover.c.latency) }}</span>
          </template>
          <span v-else-if="hover.c.carried !== null" class="muted">no observation this hour, last known {{ conditionLabel(hover.c.carried).toLowerCase() }}</span>
          <span v-else class="muted">no observation</span>
        </template>
        <span v-else class="muted">Each cell is one hour. Faint cells carry the last observation forward. Hover for detail.</span>
      </div>
      <ul class="legend">
        <li v-for="[key, c] in legend" :key="key"><span class="swatch" :style="{ background: `var(--status-${c.status})` }"></span>{{ c.label }}</li>
        <li><span class="swatch" style="background: var(--status-none)"></span>No report</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.map { padding: 16px 20px 14px; }
.rows { display: grid; gap: 5px; }
.row { display: grid; grid-template-columns: 150px 1fr; gap: 12px; align-items: center; }
.label { display: flex; align-items: center; gap: 8px; min-width: 0; }
.name { font-weight: 600; font-size: 0.92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cells { display: flex; gap: 2px; height: 22px; }
.cell { flex: 1 1 0; min-width: 0; border-radius: 3px; transition: transform 80ms ease; }
.cell.hot { transform: scaleY(1.25); outline: 2px solid var(--text-primary); outline-offset: -1px; }
.axis { margin-top: 2px; }
.ticks { position: relative; height: 30px; }
.tick { position: absolute; top: 0; transform: translateX(-50%); display: grid; justify-items: center; font-size: 0.7rem; color: var(--text-muted); line-height: 1.2; }
.tick::before { content: ''; width: 1px; height: 5px; background: var(--border); margin-bottom: 2px; }
.tick-day { font-weight: 600; color: var(--text-secondary); }
.foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; margin-top: 8px; border-top: 1px solid var(--border); padding-top: 10px; }
.tip { display: flex; flex-wrap: wrap; gap: 10px; font-size: 0.8rem; color: var(--text-secondary); min-height: 1.2em; }
.legend { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 14px; font-size: 0.75rem; color: var(--text-secondary); }
.legend li { display: flex; align-items: center; gap: 6px; }
.swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
@media (max-width: 640px) {
  .row { grid-template-columns: 1fr; gap: 4px; }
  .cells { gap: 1px; height: 16px; }
  .cell { border-radius: 2px; }
  .ticks { height: 28px; }
  .axis .label { display: none; }
}
</style>
