<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { conditionLabel, conditionStatus, fmtLatency } from '../format.js'

const props = defineProps({ stations: { type: Array, default: () => [] } })

// Deterministic pseudo-randomness: the sky must look the same on every build and every reload.
function hash(str) {
  let h = 2166136261
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619) }
  return h >>> 0
}
function rng(seed) {
  let a = seed >>> 0
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
}

const narrow = ref(false)
const update = () => { narrow.value = window.innerWidth < 640 }
onMounted(() => { update(); window.addEventListener('resize', update) })
onBeforeUnmount(() => window.removeEventListener('resize', update))

const W = computed(() => (narrow.value ? 600 : 1200))
const H = computed(() => (narrow.value ? 760 : 430))
const cols = computed(() => (narrow.value ? 2 : 4))

const background = computed(() => {
  const r = rng(hash('llmweather-sky'))
  return Array.from({ length: narrow.value ? 160 : 260 }, (_, i) => ({
    x: r() * W.value, y: r() * H.value, r: 0.4 + r() * 1.1, o: 0.25 + r() * 0.6, twinkle: i % 9 === 0,
  }))
})

// Star size follows the input price tier, so flagships shine and small models are faint.
function starRadius(m) {
  const p = m.promptPerMTok
  if (p === null || p === undefined) return 2.4
  if (p >= 2) return 4.6
  if (p >= 0.5) return 3.3
  return 2.3
}

const constellations = computed(() => {
  const n = props.stations.length
  const rows = Math.max(1, Math.ceil(n / cols.value))
  const cellW = W.value / cols.value
  const cellH = (H.value - 24) / rows
  return props.stations.map((s, i) => {
    const r = rng(hash(s.id))
    const cx = (i % cols.value) * cellW + cellW / 2 + (r() - 0.5) * 24
    const cy = Math.floor(i / cols.value) * cellH + cellH / 2 - 14 + (r() - 0.5) * 16
    const models = [...(s.models || [])].sort((a, b) => b.created - a.created).slice(0, 8)
    const stars = models.map(m => {
      const mr = rng(hash(m.id))
      const angle = mr() * Math.PI * 2
      const dist = 18 + mr() * 46
      return { id: m.id, name: m.name.replace(/^[^:]+:\s*/, ''), x: cx + Math.cos(angle) * dist, y: cy + Math.sin(angle) * dist * 0.72, r: starRadius(m), price: m.promptPerMTok }
    })
    if (!stars.length) stars.push({ id: s.id, name: s.name, x: cx, y: cy, r: 3, price: null })
    const lines = stars.slice(1).map((st, k) => ({ x1: stars[k].x, y1: stars[k].y, x2: st.x, y2: st.y }))
    return { station: s, cx, cy, stars, lines, labelY: cy + 62 }
  })
})

const cloudPath = (cx, cy, w) => {
  const h = w * 0.42
  return `M${cx - w / 2} ${cy + h / 2}
    a${h * 0.45} ${h * 0.45} 0 0 1 ${h * 0.4} -${h * 0.75}
    a${h * 0.6} ${h * 0.6} 0 0 1 ${w * 0.36} -${h * 0.25}
    a${h * 0.5} ${h * 0.5} 0 0 1 ${w * 0.3} ${h * 0.3}
    a${h * 0.4} ${h * 0.4} 0 0 1 ${w * 0.04} ${h * 0.7} z`
}

const hover = ref(null)
const status = c => conditionStatus(c.station.condition)
function focusStation(id) {
  const el = document.getElementById(`station-${id}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
</script>

<template>
  <div class="sky card">
    <svg :viewBox="`0 0 ${W} ${H}`" class="canvas" role="img" aria-label="Sky chart: one constellation per provider, one star per model, with tonight's weather drawn over it">
      <defs>
        <radialGradient id="skyglow" cx="50%" cy="110%" r="90%">
          <stop offset="0" stop-color="#1d2a4d" />
          <stop offset="1" stop-color="#070b18" />
        </radialGradient>
        <radialGradient id="starglow">
          <stop offset="0" stop-color="#fff" stop-opacity="0.9" />
          <stop offset="1" stop-color="#fff" stop-opacity="0" />
        </radialGradient>
        <filter id="soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.2" /></filter>
      </defs>
      <rect width="100%" height="100%" fill="url(#skyglow)" />
      <g class="bg">
        <circle v-for="(s, i) in background" :key="i" :cx="s.x" :cy="s.y" :r="s.r" fill="#dfe6ff" :opacity="s.o" :class="{ twinkle: s.twinkle }" />
      </g>

      <g v-for="c in constellations" :key="c.station.id"
         class="constellation"
         :class="[`cond-${c.station.condition}`, `press-${c.station.pressure}`, { hot: hover && hover.station.id === c.station.id, dim: hover && hover.station.id !== c.station.id }]"
         @mouseenter="hover = c" @mouseleave="hover = null" @click="focusStation(c.station.id)"
         tabindex="0" role="button" :aria-label="`${c.station.name}: ${conditionLabel(c.station.condition)}`"
         @focus="hover = c" @blur="hover = null" @keydown.enter="focusStation(c.station.id)">
        <rect :x="c.cx - 80" :y="c.cy - 60" width="160" height="140" fill="transparent" />
        <line v-for="(l, i) in c.lines" :key="i" :x1="l.x1" :y1="l.y1" :x2="l.x2" :y2="l.y2" class="link" />
        <g v-for="st in c.stars" :key="st.id" class="star">
          <circle v-if="c.station.condition !== 'UNKNOWN'" :cx="st.x" :cy="st.y" :r="st.r * 3" fill="url(#starglow)" opacity="0.35" />
          <circle :cx="st.x" :cy="st.y" :r="st.r" :class="{ ghost: c.station.condition === 'UNKNOWN' }" />
        </g>

        <!-- weather overlay -->
        <template v-if="c.station.condition === 'PARTLY_CLOUDY'">
          <path :d="cloudPath(c.cx + 18, c.cy + 4, 70)" class="cloud drift" />
        </template>
        <template v-else-if="c.station.condition === 'CLOUDY'">
          <path :d="cloudPath(c.cx - 14, c.cy + 10, 86)" class="cloud drift" />
          <path :d="cloudPath(c.cx + 30, c.cy - 12, 64)" class="cloud drift slow" />
        </template>
        <template v-else-if="c.station.condition === 'RAIN'">
          <path :d="cloudPath(c.cx, c.cy - 6, 104)" class="cloud heavy drift slow" />
          <g class="rain">
            <line v-for="k in 6" :key="k" :x1="c.cx - 40 + k * 13" :y1="c.cy + 14" :x2="c.cx - 44 + k * 13" :y2="c.cy + 30" />
          </g>
        </template>
        <template v-else-if="c.station.condition === 'STORM'">
          <path :d="cloudPath(c.cx, c.cy - 8, 116)" class="cloud storm drift slow" />
          <polygon :points="`${c.cx + 2},${c.cy + 6} ${c.cx - 10},${c.cy + 26} ${c.cx - 1},${c.cy + 26} ${c.cx - 6},${c.cy + 44} ${c.cx + 12},${c.cy + 18} ${c.cx + 3},${c.cy + 18} ${c.cx + 8},${c.cy + 6}`" class="bolt" />
        </template>

        <text :x="c.cx" :y="c.labelY" class="name" text-anchor="middle">{{ c.station.name }}</text>
        <text :x="c.cx" :y="c.labelY + 15" class="cond" :class="`cond-text-${status(c)}`" text-anchor="middle">{{ conditionLabel(c.station.condition) }}</text>
      </g>
    </svg>

    <div class="foot">
      <div class="tip" aria-live="polite">
        <template v-if="hover">
          <strong>{{ hover.station.name }}</strong>
          <span>{{ conditionLabel(hover.station.condition) }}</span>
          <span v-if="hover.station.statusDescription" class="muted">{{ hover.station.statusDescription }}</span>
          <span class="muted">{{ hover.stars.length }} {{ hover.stars.length === 1 ? 'star' : 'stars' }} · pressure {{ (hover.station.pressure || 'steady').toLowerCase() }}</span>
          <span v-if="hover.station.probe && hover.station.probe.ok" class="mono">{{ fmtLatency(hover.station.probe.latencyMs) }}</span>
        </template>
        <span v-else class="muted">One constellation per provider, one star per model. Bright stars are the expensive flagships. Click a constellation for its station report.</span>
      </div>
      <ul class="legend">
        <li><svg width="30" height="12" aria-hidden="true"><circle cx="5" cy="6" r="2.2" fill="currentColor" /><circle cx="14" cy="6" r="3.2" fill="currentColor" /><circle cx="25" cy="6" r="4.4" fill="currentColor" /></svg>price tier</li>
        <li><svg width="22" height="12" aria-hidden="true"><path d="M2 10a3 3 0 0 1 3-5 5 5 0 0 1 9-1 3 3 0 0 1 4 5z" fill="currentColor" opacity="0.7" /></svg>clouds, rain or storm</li>
        <li><svg width="14" height="12" aria-hidden="true"><circle cx="7" cy="6" r="3.5" fill="none" stroke="currentColor" stroke-dasharray="2 2" /></svg>no report</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.sky { padding: 0; overflow: hidden; margin-top: 24px; }
.canvas { display: block; width: 100%; height: auto; }
.constellation { cursor: pointer; transition: opacity 160ms ease; outline: none; }
.constellation.dim { opacity: 0.45; }
.link { stroke: #9fb2ff; stroke-opacity: 0.28; stroke-width: 1; }
.constellation.hot .link { stroke-opacity: 0.7; }
.star circle:last-child { fill: #fff7e0; }
.star circle.ghost { fill: none; stroke: #9fb2ff; stroke-width: 1; stroke-dasharray: 2 2; }
.name { fill: #eef2ff; font-size: 14px; font-weight: 600; font-family: var(--font); letter-spacing: 0.01em; }
.cond { fill: #aab6d6; font-size: 11px; font-family: var(--font); text-transform: uppercase; letter-spacing: 0.06em; }
.cond-text-good { fill: #7fe08a; } .cond-text-warning { fill: #f6cf6a; } .cond-text-serious { fill: #f6a37f; } .cond-text-critical { fill: #ff8585; }
.cloud { fill: #c9d0de; opacity: 0.78; }
.cloud.heavy { fill: #99a2b4; opacity: 0.9; }
.cloud.storm { fill: #6c7386; opacity: 0.95; }
.rain line { stroke: #7fb3ff; stroke-width: 1.6; stroke-linecap: round; }
.bolt { fill: #ffd34d; filter: drop-shadow(0 0 4px #ffd34d); }

/* Pressure: rising constellations brighten slowly, falling ones fade. Steady holds. */
.press-RISING .star circle:last-child { animation: brighten 4s ease-in-out infinite alternate; }
.press-FALLING .star circle:last-child { animation: fade 4s ease-in-out infinite alternate; }
.drift { animation: drift 9s ease-in-out infinite alternate; }
.drift.slow { animation-duration: 14s; }
.rain { animation: rainfall 0.9s linear infinite; }
.bolt { animation: flicker 3.2s steps(1) infinite; }
.twinkle { animation: twinkle 3s ease-in-out infinite alternate; }

@keyframes brighten { from { opacity: 0.6; } to { opacity: 1; } }
@keyframes fade { from { opacity: 1; } to { opacity: 0.45; } }
@keyframes drift { from { transform: translateX(-6px); } to { transform: translateX(6px); } }
@keyframes rainfall { from { transform: translateY(-4px); opacity: 0.2; } 30% { opacity: 1; } to { transform: translateY(8px); opacity: 0; } }
@keyframes flicker { 0%, 88%, 93%, 100% { opacity: 0.22; } 90%, 95% { opacity: 1; } }
@keyframes twinkle { from { opacity: 0.2; } to { opacity: 0.9; } }

@media (prefers-reduced-motion: reduce) {
  .drift, .rain, .twinkle, .press-RISING .star circle:last-child, .press-FALLING .star circle:last-child { animation: none; }
  .bolt { animation: none; opacity: 1; }
}

.foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding: 10px 20px 12px; border-top: 1px solid var(--border); }
.tip { display: flex; flex-wrap: wrap; gap: 10px; font-size: 0.8rem; color: var(--text-secondary); min-height: 1.2em; }
.legend { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 0.75rem; color: var(--text-secondary); }
.legend li { display: flex; align-items: center; gap: 6px; }
</style>
