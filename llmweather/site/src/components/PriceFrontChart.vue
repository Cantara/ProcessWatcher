<script setup>
import { computed, ref } from 'vue'
import { FRONT_LABEL } from '../format.js'

const props = defineProps({ fronts: { type: Array, default: () => [] } })

const price = computed(() => props.fronts
  .filter(f => f.type === 'price-drop' || f.type === 'price-rise')
  .sort((a, b) => a.magnitude - b.magnitude))
const other = computed(() => props.fronts.filter(f => f.type !== 'price-drop' && f.type !== 'price-rise'))
const maxAbs = computed(() => Math.max(0.1, ...price.value.map(f => Math.abs(f.magnitude))))
const half = computed(() => Math.ceil(maxAbs.value * 100 / 10) * 10) // axis extent, rounded up to a multiple of 10%

const barStyle = f => {
  const w = (Math.abs(f.magnitude) * 100) / half.value * 50
  return f.magnitude < 0
    ? { right: '50%', width: `${w}%`, background: 'var(--status-good)' }
    : { left: '50%', width: `${w}%`, background: 'var(--status-serious)' }
}
const pct = f => `${f.magnitude < 0 ? '−' : '+'}${Math.round(Math.abs(f.magnitude) * 100)}%`
const hover = ref(null)
const status = { 'new-model': 'warning', deprecation: 'none' }
</script>

<template>
  <div class="card chart">
    <template v-if="price.length">
      <div class="rows" @mouseleave="hover = null">
        <div v-for="(f, i) in price" :key="i" class="row" :class="{ dim: hover !== null && hover !== i }" @mouseenter="hover = i">
          <div class="who">
            <div class="model">{{ f.model }}</div>
            <div class="prov muted">{{ f.provider }}</div>
          </div>
          <div class="track">
            <div class="mid"></div>
            <div class="bar" :style="barStyle(f)"></div>
          </div>
          <div class="val mono" :class="f.magnitude < 0 ? 'down' : 'up'">{{ pct(f) }}</div>
        </div>
      </div>
      <div class="axis">
        <span class="who"></span>
        <span class="track-labels"><span>−{{ half }}%</span><span>cheaper · 0 · dearer</span><span>+{{ half }}%</span></span>
        <span></span>
      </div>
      <p class="detail" aria-live="polite">
        <template v-if="hover !== null">{{ price[hover].model }}: {{ price[hover].detail }}.</template>
        <span v-else class="muted">Largest price move per model since the previous observation. Hover a bar for the before-and-after prices.</span>
      </p>
    </template>
    <p v-else class="muted">No price fronts. Every listed price is within 5% of the previous observation.</p>

    <ul v-if="other.length" class="other">
      <li v-for="(f, i) in other" :key="i">
        <span class="badge" :class="`badge-${status[f.type] || 'none'}`">{{ FRONT_LABEL[f.type] || f.type }}</span>
        <strong>{{ f.model }}</strong> <span class="muted">· {{ f.provider }} · {{ f.detail }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.rows { display: grid; gap: 10px; }
.row, .axis { display: grid; grid-template-columns: minmax(120px, 1.1fr) 2fr 56px; gap: 12px; align-items: center; }
.row { transition: opacity 120ms ease; }
.row.dim { opacity: 0.45; }
.model { font-weight: 600; font-size: 0.92rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.prov { font-size: 0.78rem; }
.track { position: relative; height: 14px; }
.mid { position: absolute; left: 50%; top: -3px; bottom: -3px; width: 1px; background: var(--text-muted); opacity: 0.5; }
.bar { position: absolute; top: 2px; height: 10px; border-radius: 5px; }
.val { text-align: right; font-weight: 700; }
.down { color: var(--status-good); }
.up { color: var(--status-serious); }
.axis { margin-top: 6px; font-size: 0.7rem; color: var(--text-muted); }
.track-labels { display: flex; justify-content: space-between; }
.detail { margin-top: 10px; font-size: 0.82rem; color: var(--text-secondary); min-height: 1.3em; }
.other { list-style: none; margin: 14px 0 0; padding: 12px 0 0; border-top: 1px solid var(--border); display: grid; gap: 8px; font-size: 0.88rem; }
.other li { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
@media (max-width: 480px) { .row, .axis { grid-template-columns: 1fr 48px; } .row .track { grid-column: 1 / -1; order: 3; } .axis .who { display: none; } }
</style>
