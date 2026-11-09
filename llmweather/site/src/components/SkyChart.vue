<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { conditionLabel, conditionStatus, fmtLatency } from '../format.js'

const props = defineProps({ stations: { type: Array, default: () => [] }, fronts: { type: Array, default: () => [] } })

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
    const c = { station: s, cx, cy, stars, lines, labelY: cy + 62 }
    c.weather = weatherFor(c)
    return c
  })
})

// A cloud is a flat base plus a row of overlapping puffs along the top. Every cloud is seeded, so the
// same provider gets the same cloud shape on every build, and two clouds never look identical.
function cloudShape(seed, cx, cy, w) {
  const r = rng(hash(seed))
  const h = w * 0.42
  const puffs = []
  const n = 4 + Math.floor(r() * 3)
  for (let i = 0; i < n; i++) {
    const t = (i + 0.5) / n
    const pr = h * (0.42 + r() * 0.3) * (1 - Math.abs(t - 0.5) * 0.9)
    puffs.push({ cx: cx - w / 2 + t * w, cy: cy - pr * 0.35 + (r() - 0.5) * h * 0.1, r: pr })
  }
  return { base: { x: cx - w / 2, y: cy - h * 0.1, w, h: h * 0.55, rx: h * 0.28 }, puffs }
}

// Weather plan per condition: which clouds, how heavy, how fast they drift.
function weatherFor(c) {
  const { cx, cy } = c
  const id = c.station.id
  switch (c.station.condition) {
    case 'PARTLY_CLOUDY':
      return { clouds: [{ shape: cloudShape(id + '-a', cx + 16, cy + 2, 74), kind: 'light', dur: 11, delay: -3 }] }
    case 'CLOUDY':
      return { clouds: [
        { shape: cloudShape(id + '-b', cx + 30, cy - 14, 70), kind: 'mid', dur: 17, delay: -6 },
        { shape: cloudShape(id + '-a', cx - 12, cy + 8, 96), kind: 'light', dur: 12, delay: -1 },
      ] }
    case 'RAIN':
      return { rain: 9, clouds: [
        { shape: cloudShape(id + '-b', cx + 22, cy - 18, 84), kind: 'mid', dur: 19, delay: -8 },
        { shape: cloudShape(id + '-a', cx - 4, cy - 4, 112), kind: 'heavy', dur: 14, delay: -2 },
      ] }
    case 'STORM':
      return { storm: true, rain: 7, clouds: [
        { shape: cloudShape(id + '-b', cx + 26, cy - 20, 92), kind: 'heavy', dur: 21, delay: -5 },
        { shape: cloudShape(id + '-a', cx - 2, cy - 6, 124), kind: 'storm', dur: 15, delay: -9 },
      ] }
    default:
      return { clouds: [] }
  }
}

// A shooting star for every newly listed model: the sky announces arrivals.
const meteors = computed(() => {
  const fresh = props.fronts.filter(f => f.type === 'new-model').slice(0, 4)
  return fresh.map((f, i) => {
    const r = rng(hash('meteor-' + f.model))
    return { key: f.model, x: W.value * (0.15 + r() * 0.7), y: H.value * (0.08 + r() * 0.35), len: 70 + r() * 50, dur: 7 + r() * 5, delay: i * 3.5 + r() * 2, label: f.model }
  })
})

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
        <linearGradient id="cloud-light" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f3f5fa" /><stop offset="1" stop-color="#b9c2d4" /></linearGradient>
        <linearGradient id="cloud-mid" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d6dce8" /><stop offset="1" stop-color="#8e98ad" /></linearGradient>
        <linearGradient id="cloud-heavy" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#a8b1c3" /><stop offset="1" stop-color="#5d6678" /></linearGradient>
        <linearGradient id="cloud-storm" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6a7287" /><stop offset="1" stop-color="#2b3040" /></linearGradient>
        <linearGradient id="meteor" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0" /><stop offset="1" stop-color="#fff" stop-opacity="0.95" /></linearGradient>
        <filter id="cloudsoft" x="-20%" y="-30%" width="140%" height="170%"><feGaussianBlur stdDeviation="0.9" /></filter>
        <filter id="cloudshadow" x="-20%" y="-30%" width="140%" height="190%"><feGaussianBlur stdDeviation="4" /></filter>
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

        <!-- weather overlay: layered clouds (back to front), then rain, then lightning -->
        <g v-for="(cl, k) in c.weather.clouds" :key="k" class="cloud" :class="cl.kind"
           :style="{ animationDuration: `${cl.dur}s`, animationDelay: `${cl.delay}s` }">
          <ellipse :cx="cl.shape.base.x + cl.shape.base.w / 2" :cy="cl.shape.base.y + cl.shape.base.h + 6" :rx="cl.shape.base.w * 0.42" :ry="cl.shape.base.h * 0.35" class="shadow" filter="url(#cloudshadow)" />
          <g :fill="`url(#cloud-${cl.kind})`" filter="url(#cloudsoft)">
            <rect :x="cl.shape.base.x" :y="cl.shape.base.y" :width="cl.shape.base.w" :height="cl.shape.base.h" :rx="cl.shape.base.rx" />
            <circle v-for="(p, j) in cl.shape.puffs" :key="j" :cx="p.cx" :cy="p.cy" :r="p.r" />
          </g>
          <g v-if="c.weather.storm && k === c.weather.clouds.length - 1" class="flash">
            <rect :x="cl.shape.base.x" :y="cl.shape.base.y - 20" :width="cl.shape.base.w" :height="cl.shape.base.h + 22" :rx="cl.shape.base.rx" fill="#ffe9a8" />
          </g>
        </g>
        <g v-if="c.weather.rain" class="rain" :class="{ heavy: c.weather.storm }">
          <line v-for="k in c.weather.rain" :key="k"
                :x1="c.cx - 50 + k * (100 / (c.weather.rain + 1))" :y1="c.cy + 16"
                :x2="c.cx - 53 + k * (100 / (c.weather.rain + 1))" :y2="c.cy + 30"
                :style="{ animationDelay: `${(k * 0.13) % 0.9}s` }" />
        </g>
        <polygon v-if="c.weather.storm" class="bolt"
                 :points="`${c.cx + 4},${c.cy + 8} ${c.cx - 10},${c.cy + 30} ${c.cx - 1},${c.cy + 30} ${c.cx - 7},${c.cy + 50} ${c.cx + 14},${c.cy + 22} ${c.cx + 4},${c.cy + 22} ${c.cx + 10},${c.cy + 8}`" />

        <text :x="c.cx" :y="c.labelY" class="name" text-anchor="middle">{{ c.station.name }}</text>
        <text :x="c.cx" :y="c.labelY + 15" class="cond" :class="`cond-text-${status(c)}`" text-anchor="middle">{{ conditionLabel(c.station.condition) }}</text>
      </g>
      <g v-for="m in meteors" :key="m.key" class="meteor" :style="{ animationDuration: `${m.dur}s`, animationDelay: `${m.delay}s` }">
        <title>New model listed: {{ m.label }}</title>
        <g :transform="`translate(${m.x} ${m.y}) rotate(28)`">
          <rect :x="-m.len" y="-0.9" :width="m.len" height="1.8" rx="0.9" fill="url(#meteor)" />
          <circle cx="0" cy="0" r="2.2" fill="#fff" />
        </g>
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
        <li v-if="meteors.length"><svg width="26" height="12" aria-hidden="true"><line x1="2" y1="10" x2="20" y2="3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" /><circle cx="21" cy="2.5" r="2" fill="currentColor" /></svg>new model</li>
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
.cloud { animation: drift 12s ease-in-out infinite alternate; }
.cloud .shadow { fill: #000; opacity: 0.35; }
.cloud.light { opacity: 0.92; }
.cloud.mid { opacity: 0.94; }
.cloud.heavy, .cloud.storm { opacity: 0.97; }
.flash rect { opacity: 0; animation: flash 3.2s steps(1) infinite; mix-blend-mode: screen; }
.rain line { stroke: #86b9ff; stroke-width: 1.5; stroke-linecap: round; animation: rainfall 0.9s linear infinite; }
.rain.heavy line { stroke: #a7c9ff; stroke-width: 1.8; animation-duration: 0.65s; }
.bolt { fill: #ffd34d; filter: drop-shadow(0 0 5px #ffd34d); animation: flicker 3.2s steps(1) infinite; }
.meteor { opacity: 0; animation-name: meteor; animation-timing-function: ease-out; animation-iteration-count: infinite; }

/* Pressure: rising constellations brighten slowly, falling ones fade. Steady holds. */
.press-RISING .star circle:last-child { animation: brighten 4s ease-in-out infinite alternate; }
.press-FALLING .star circle:last-child { animation: fade 4s ease-in-out infinite alternate; }
.twinkle { animation: twinkle 3s ease-in-out infinite alternate; }

@keyframes brighten { from { opacity: 0.6; } to { opacity: 1; } }
@keyframes fade { from { opacity: 1; } to { opacity: 0.45; } }
@keyframes drift { from { transform: translate(-7px, 0); } 50% { transform: translate(0, -1.5px); } to { transform: translate(7px, 0); } }
@keyframes rainfall { from { transform: translateY(-3px); opacity: 0; } 25% { opacity: 1; } to { transform: translateY(10px); opacity: 0; } }
@keyframes flicker { 0%, 87%, 90.5%, 93%, 100% { opacity: 0.18; } 88%, 91.5% { opacity: 1; } }
@keyframes flash { 0%, 87%, 90.5%, 93%, 100% { opacity: 0; } 88%, 91.5% { opacity: 0.55; } }
@keyframes twinkle { from { opacity: 0.2; } to { opacity: 0.9; } }
@keyframes meteor {
  0% { opacity: 0; transform: translate(-60px, -32px); }
  4% { opacity: 1; }
  16% { opacity: 0; transform: translate(70px, 37px); }
  100% { opacity: 0; transform: translate(70px, 37px); }
}

@media (prefers-reduced-motion: reduce) {
  .cloud, .rain line, .twinkle, .meteor, .flash rect, .press-RISING .star circle:last-child, .press-FALLING .star circle:last-child { animation: none; }
  .bolt { animation: none; opacity: 1; }
  .meteor { opacity: 0.8; }
}

.foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding: 10px 20px 12px; border-top: 1px solid var(--border); }
.tip { display: flex; flex-wrap: wrap; gap: 10px; font-size: 0.8rem; color: var(--text-secondary); min-height: 1.2em; }
.legend { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 0.75rem; color: var(--text-secondary); }
.legend li { display: flex; align-items: center; gap: 6px; }
</style>
