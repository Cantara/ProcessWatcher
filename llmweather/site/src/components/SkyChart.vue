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
const reduced = ref(false)
let mq
const update = () => { narrow.value = window.innerWidth < 640 }
onMounted(() => {
  update(); window.addEventListener('resize', update)
  mq = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduced.value = mq.matches
  mq.addEventListener('change', e => { reduced.value = e.matches })
})
onBeforeUnmount(() => window.removeEventListener('resize', update))

const W = computed(() => (narrow.value ? 600 : 1200))
const H = computed(() => (narrow.value ? 760 : 430))
const cols = computed(() => (narrow.value ? 2 : 4))
const moon = computed(() => (narrow.value ? { x: 300, y: 30, r: 13 } : { x: 600, y: 34, r: 16 }))

const background = computed(() => {
  const r = rng(hash('llmweather-sky'))
  return Array.from({ length: narrow.value ? 170 : 280 }, (_, i) => ({
    x: r() * W.value, y: r() * H.value, r: 0.35 + r() * 1.15, o: 0.2 + r() * 0.65, twinkle: i % 7 === 0, dur: 2.2 + r() * 3,
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

// A cloud is a flat base plus a row of overlapping puffs along the top. Every cloud is seeded, so the
// same provider gets the same cloud shape on every build, and two clouds never look identical.
function cloudShape(seed, cx, cy, w) {
  const r = rng(hash(seed))
  const h = w * 0.42
  const puffs = []
  const n = 5 + Math.floor(r() * 3)
  for (let i = 0; i < n; i++) {
    const t = (i + 0.5) / n
    const pr = h * (0.42 + r() * 0.3) * (1 - Math.abs(t - 0.5) * 0.85)
    puffs.push({ cx: cx - w / 2 + t * w, cy: cy - pr * 0.38 + (r() - 0.5) * h * 0.12, r: pr })
  }
  const base = { x: cx - w / 2, y: cy - h * 0.1, w, h: h * 0.55, rx: h * 0.28 }
  const top = Math.min(...puffs.map(p => p.cy - p.r))
  const left = Math.min(base.x, ...puffs.map(p => p.cx - p.r))
  const right = Math.max(base.x + w, ...puffs.map(p => p.cx + p.r))
  const bottom = base.y + base.h
  return { cx, cy, w, h, base, puffs, bbox: { x: left, y: top, w: right - left, h: bottom - top }, seed: Math.floor(r() * 1000), freq: 0.011 + r() * 0.006 }
}

// Weather plan per condition: which clouds, how heavy, how they move. Back layers drift slower (parallax).
function weatherFor(c) {
  const { cx, cy } = c
  const id = c.station.id
  const r = rng(hash(id + '-weather'))
  const boltDelay = -(r() * 6)
  switch (c.station.condition) {
    case 'PARTLY_CLOUDY':
      return { clouds: [{ shape: cloudShape(id + '-a', cx + 16, cy + 2, 78), kind: 'light', dur: 13, delay: -r() * 10 }] }
    case 'CLOUDY':
      return { clouds: [
        { shape: cloudShape(id + '-b', cx + 32, cy - 16, 74), kind: 'mid', dur: 24, delay: -r() * 20, back: true },
        { shape: cloudShape(id + '-a', cx - 12, cy + 8, 100), kind: 'light', dur: 14, delay: -r() * 10 },
      ] }
    case 'RAIN':
      return { rain: 14, clouds: [
        { shape: cloudShape(id + '-b', cx + 24, cy - 20, 90), kind: 'mid', dur: 26, delay: -r() * 20, back: true },
        { shape: cloudShape(id + '-a', cx - 4, cy - 4, 118), kind: 'heavy', dur: 15, delay: -r() * 10 },
      ] }
    case 'STORM':
      return { storm: true, boltDelay, rain: 12, clouds: [
        { shape: cloudShape(id + '-b', cx + 28, cy - 22, 98), kind: 'heavy', dur: 28, delay: -r() * 20, back: true },
        { shape: cloudShape(id + '-a', cx - 2, cy - 6, 130), kind: 'storm', dur: 16, delay: -r() * 10 },
      ] }
    default:
      return { clouds: [] }
  }
}

function rainDrops(c) {
  const r = rng(hash(c.station.id + '-rain'))
  const n = c.weather.rain
  const spread = c.weather.clouds[c.weather.clouds.length - 1].shape.w * 0.78
  return Array.from({ length: n }, (_, k) => {
    const x = c.cx - spread / 2 + ((k + 0.5) / n) * spread + (r() - 0.5) * 6
    return { x, y: c.cy + 14 + r() * 6, len: 9 + r() * 9, delay: r() * 0.9, o: 0.5 + r() * 0.5 }
  })
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
    c.drops = c.weather.rain ? rainDrops(c) : []
    return c
  })
})

// A shooting star for every newly listed model: the sky announces arrivals.
const meteors = computed(() => {
  const fresh = props.fronts.filter(f => f.type === 'new-model').slice(0, 4)
  return fresh.map((f, i) => {
    const r = rng(hash('meteor-' + f.model))
    return { key: f.model, x: W.value * (0.15 + r() * 0.7), y: H.value * (0.08 + r() * 0.35), len: 80 + r() * 60, dur: 8 + r() * 5, delay: i * 3.5 + r() * 2, label: f.model }
  })
})

const bolt = c => {
  const { cx, cy } = c
  return {
    main: `${cx + 6},${cy + 4} ${cx - 6},${cy + 22} ${cx + 2},${cy + 22} ${cx - 10},${cy + 48} ${cx + 12},${cy + 18} ${cx + 4},${cy + 18} ${cx + 14},${cy + 4}`,
    branch: `${cx - 2},${cy + 28} ${cx - 16},${cy + 36} ${cx - 10},${cy + 37} ${cx - 22},${cy + 46}`,
  }
}

const hover = ref(null)
const status = c => conditionStatus(c.station.condition)
function focusStation(id) {
  const el = document.getElementById(`station-${id}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
</script>

<template>
  <div class="sky card" :class="{ still: reduced }">
    <svg :viewBox="`0 0 ${W} ${H}`" class="canvas" role="img" aria-label="Sky chart: one constellation per provider, one star per model, with tonight's weather drawn over it">
      <defs>
        <radialGradient id="skyglow" cx="50%" cy="115%" r="95%">
          <stop offset="0" stop-color="#22305a" />
          <stop offset="0.5" stop-color="#101a38" />
          <stop offset="1" stop-color="#05081a" />
        </radialGradient>
        <radialGradient id="moonhalo"><stop offset="0" stop-color="#fff6d8" stop-opacity="0.55" /><stop offset="0.35" stop-color="#fff6d8" stop-opacity="0.12" /><stop offset="1" stop-color="#fff6d8" stop-opacity="0" /></radialGradient>
        <radialGradient id="moonface" cx="38%" cy="35%" r="75%"><stop offset="0" stop-color="#fffdf4" /><stop offset="1" stop-color="#e6dcbc" /></radialGradient>
        <radialGradient id="starglow"><stop offset="0" stop-color="#fff" stop-opacity="0.9" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></radialGradient>
        <linearGradient id="milky" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8fa4ff" stop-opacity="0" /><stop offset="0.5" stop-color="#b9c6ff" stop-opacity="0.16" /><stop offset="1" stop-color="#8fa4ff" stop-opacity="0" /></linearGradient>

        <!-- cloud materials: each cloud is shaded as one silhouette, lit from the moon above -->
        <linearGradient id="body-light" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" /><stop offset="0.55" stop-color="#dfe6f1" /><stop offset="1" stop-color="#9aa6bd" /></linearGradient>
        <linearGradient id="body-mid" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f1f4fa" /><stop offset="0.55" stop-color="#b9c2d4" /><stop offset="1" stop-color="#6b768c" /></linearGradient>
        <linearGradient id="body-heavy" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#c9d1df" /><stop offset="0.5" stop-color="#828ca1" /><stop offset="1" stop-color="#3b4253" /></linearGradient>
        <linearGradient id="body-storm" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#838b9f" /><stop offset="0.5" stop-color="#4a5266" /><stop offset="1" stop-color="#141822" /></linearGradient>
        <linearGradient id="base-light" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#dfe5f0" /><stop offset="1" stop-color="#a4aec2" /></linearGradient>
        <linearGradient id="base-mid" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#bcc5d6" /><stop offset="1" stop-color="#7a8499" /></linearGradient>
        <linearGradient id="base-heavy" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#8a94a9" /><stop offset="1" stop-color="#4a5264" /></linearGradient>
        <linearGradient id="base-storm" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#545c70" /><stop offset="1" stop-color="#1c2130" /></linearGradient>
        <radialGradient id="hilite"><stop offset="0" stop-color="#ffffff" stop-opacity="0.75" /><stop offset="1" stop-color="#ffffff" stop-opacity="0" /></radialGradient>
        <radialGradient id="shade"><stop offset="0" stop-color="#000" stop-opacity="0.35" /><stop offset="1" stop-color="#000" stop-opacity="0" /></radialGradient>
        <radialGradient id="flashglow"><stop offset="0" stop-color="#fff6cf" stop-opacity="1" /><stop offset="0.6" stop-color="#ffe9a0" stop-opacity="0.45" /><stop offset="1" stop-color="#ffe9a0" stop-opacity="0" /></radialGradient>
        <linearGradient id="drop" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#9ec5ff" stop-opacity="0" /><stop offset="1" stop-color="#cfe2ff" stop-opacity="1" /></linearGradient>
        <linearGradient id="meteor" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0" /><stop offset="1" stop-color="#fff" stop-opacity="0.95" /></linearGradient>

        <filter id="haze" x="-30%" y="-50%" width="160%" height="220%"><feGaussianBlur stdDeviation="7" /></filter>
        <filter id="cloudshadow" x="-20%" y="-30%" width="140%" height="190%"><feGaussianBlur stdDeviation="4" /></filter>
        <filter id="boltglow" x="-60%" y="-30%" width="220%" height="160%"><feGaussianBlur stdDeviation="2.4" result="b" /><feMerge><feMergeNode in="b" /><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge></filter>

        <!-- one wobble filter per cloud: fractal noise displaces the edges and slowly morphs them -->
        <template v-for="c in constellations" :key="'f' + c.station.id">
          <filter v-for="(cl, k) in c.weather.clouds" :key="k" :id="`wobble-${c.station.id}-${k}`" x="-15%" y="-25%" width="130%" height="160%">
            <feTurbulence type="fractalNoise" :baseFrequency="cl.shape.freq" numOctaves="2" :seed="cl.shape.seed" result="noise">
              <animate v-if="!reduced" attributeName="baseFrequency" :values="`${cl.shape.freq};${cl.shape.freq * 1.45};${cl.shape.freq}`" :dur="`${22 + k * 7}s`" repeatCount="indefinite" />
            </feTurbulence>
            <feDisplacementMap in="SourceGraphic" in2="noise" :scale="cl.kind === 'light' ? 9 : 12" xChannelSelector="R" yChannelSelector="G" />
            <feGaussianBlur stdDeviation="0.45" />
          </filter>
          <clipPath v-for="(cl, k) in c.weather.clouds" :key="'c' + k" :id="`cshape-${c.station.id}-${k}`">
            <rect :x="cl.shape.base.x" :y="cl.shape.base.y" :width="cl.shape.base.w" :height="cl.shape.base.h" :rx="cl.shape.base.rx" />
            <circle v-for="(p, j) in cl.shape.puffs" :key="j" :cx="p.cx" :cy="p.cy" :r="p.r" />
          </clipPath>
        </template>
      </defs>

      <rect width="100%" height="100%" fill="url(#skyglow)" />
      <ellipse :cx="W * 0.55" :cy="H * 0.5" :rx="W * 0.7" :ry="H * 0.16" fill="url(#milky)" :transform="`rotate(-12 ${W * 0.55} ${H * 0.5})`" />
      <g class="bg">
        <circle v-for="(s, i) in background" :key="i" :cx="s.x" :cy="s.y" :r="s.r" fill="#dfe6ff" :opacity="s.o" :class="{ twinkle: s.twinkle }" :style="s.twinkle ? { animationDuration: `${s.dur}s` } : null" />
      </g>
      <g class="moon">
        <mask id="crescent">
          <rect :x="moon.x - moon.r * 2" :y="moon.y - moon.r * 2" :width="moon.r * 4" :height="moon.r * 4" fill="#fff" />
          <circle :cx="moon.x + moon.r * 0.48" :cy="moon.y - moon.r * 0.12" :r="moon.r * 0.88" fill="#000" />
        </mask>
        <circle :cx="moon.x" :cy="moon.y" :r="moon.r * 4.5" fill="url(#moonhalo)" />
        <circle :cx="moon.x" :cy="moon.y" :r="moon.r" fill="url(#moonface)" mask="url(#crescent)" />
      </g>

      <g v-for="c in constellations" :key="c.station.id"
         class="constellation"
         :class="[`cond-${c.station.condition}`, `press-${c.station.pressure}`, { hot: hover && hover.station.id === c.station.id, dim: hover && hover.station.id !== c.station.id }]"
         @mouseenter="hover = c" @mouseleave="hover = null" @click="focusStation(c.station.id)"
         tabindex="0" role="button" :aria-label="`${c.station.name}: ${conditionLabel(c.station.condition)}`"
         @focus="hover = c" @blur="hover = null" @keydown.enter="focusStation(c.station.id)">
        <rect :x="c.cx - 85" :y="c.cy - 65" width="170" height="150" fill="transparent" />
        <line v-for="(l, i) in c.lines" :key="i" :x1="l.x1" :y1="l.y1" :x2="l.x2" :y2="l.y2" class="link" />
        <g v-for="st in c.stars" :key="st.id" class="star">
          <circle v-if="c.station.condition !== 'UNKNOWN'" :cx="st.x" :cy="st.y" :r="st.r * 3" fill="url(#starglow)" opacity="0.35" />
          <circle :cx="st.x" :cy="st.y" :r="st.r" :class="{ ghost: c.station.condition === 'UNKNOWN' }" />
        </g>

        <!-- weather: haze and clouds back to front, then rain, then lightning -->
        <g v-for="(cl, k) in c.weather.clouds" :key="k" class="cloud" :class="[cl.kind, { back: cl.back }]"
           :style="{ animationDuration: `${cl.dur}s`, animationDelay: `${cl.delay}s` }">
          <g class="haze" filter="url(#haze)" :transform="`translate(${cl.shape.cx} ${cl.shape.cy}) scale(1.35 1.15) translate(${-cl.shape.cx} ${-cl.shape.cy})`">
            <circle v-for="(p, j) in cl.shape.puffs" :key="'h' + j" :cx="p.cx" :cy="p.cy + 4" :r="p.r" :fill="`url(#base-${cl.kind})`" />
          </g>
          <ellipse :cx="cl.shape.cx" :cy="cl.shape.base.y + cl.shape.base.h + 7" :rx="cl.shape.w * 0.42" :ry="cl.shape.h * 0.2" class="shadow" filter="url(#cloudshadow)" />
          <g :filter="`url(#wobble-${c.station.id}-${k})`">
            <g :clip-path="`url(#cshape-${c.station.id}-${k})`">
              <rect :x="cl.shape.bbox.x - 4" :y="cl.shape.bbox.y - 4" :width="cl.shape.bbox.w + 8" :height="cl.shape.bbox.h + 8" :fill="`url(#body-${cl.kind})`" />
              <ellipse class="hilite" :cx="cl.shape.bbox.x + cl.shape.bbox.w * 0.42" :cy="cl.shape.bbox.y + cl.shape.bbox.h * 0.18" :rx="cl.shape.bbox.w * 0.36" :ry="cl.shape.bbox.h * 0.34" fill="url(#hilite)" />
              <ellipse :cx="cl.shape.cx" :cy="cl.shape.bbox.y + cl.shape.bbox.h * 1.02" :rx="cl.shape.bbox.w * 0.52" :ry="cl.shape.bbox.h * 0.45" fill="url(#shade)" />
              <g v-if="c.weather.storm && !cl.back" class="flash" :style="{ animationDelay: `${c.weather.boltDelay}s` }">
                <ellipse :cx="cl.shape.cx + cl.shape.w * 0.05" :cy="cl.shape.bbox.y + cl.shape.bbox.h * 0.7" :rx="cl.shape.bbox.w * 0.45" :ry="cl.shape.bbox.h * 0.7" fill="url(#flashglow)" />
              </g>
            </g>
          </g>
        </g>
        <g v-if="c.weather.rain" class="rain" :class="{ heavy: c.weather.storm }">
          <line v-for="(d, k) in c.drops" :key="k" :x1="d.x" :y1="d.y" :x2="d.x - d.len * 0.22" :y2="d.y + d.len" :opacity="d.o"
                :style="{ animationDelay: `${d.delay}s` }" />
        </g>
        <g v-if="c.weather.storm" class="bolt" filter="url(#boltglow)" :style="{ animationDelay: `${c.weather.boltDelay}s` }">
          <polyline :points="bolt(c).main" class="bolt-core" />
          <polyline :points="bolt(c).branch" class="bolt-branch" />
        </g>

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

.cloud { animation: drift 14s ease-in-out infinite alternate; }
.cloud.back { animation-name: drift-back; }
.cloud .haze { opacity: 0.38; }
.cloud.light .haze { opacity: 0.3; }
.cloud .shadow { fill: #000; opacity: 0.4; }
.cloud .hilite { opacity: 1; }
.cloud.heavy .hilite { opacity: 0.55; }
.cloud.storm .hilite { opacity: 0.35; }
.flash { opacity: 0; animation: flash 4.8s steps(1) infinite; }
.rain line { stroke: url(#drop); stroke-width: 1.5; stroke-linecap: round; animation: rainfall 1s linear infinite; }
.rain.heavy line { stroke-width: 1.9; animation-duration: 0.7s; }
.bolt { animation: flicker 4.8s steps(1) infinite; }
.bolt-core { fill: none; stroke: #fff2b0; stroke-width: 2.2; stroke-linejoin: round; stroke-linecap: round; }
.bolt-branch { fill: none; stroke: #fff2b0; stroke-width: 1.3; stroke-linejoin: round; stroke-linecap: round; }
.meteor { opacity: 0; animation-name: meteor; animation-timing-function: ease-out; animation-iteration-count: infinite; }

/* Pressure: rising constellations brighten slowly, falling ones fade. Steady holds. */
.press-RISING .star circle:last-child { animation: brighten 4s ease-in-out infinite alternate; }
.press-FALLING .star circle:last-child { animation: fade 4s ease-in-out infinite alternate; }
.twinkle { animation: twinkle 3s ease-in-out infinite alternate; }

@keyframes brighten { from { opacity: 0.6; } to { opacity: 1; } }
@keyframes fade { from { opacity: 1; } to { opacity: 0.45; } }
@keyframes drift { from { transform: translate(-9px, 0); } 50% { transform: translate(0, -2px); } to { transform: translate(9px, 0); } }
@keyframes drift-back { from { transform: translate(-4px, 0); } 50% { transform: translate(0, 1px); } to { transform: translate(4px, 0); } }
@keyframes rainfall { from { transform: translateY(-4px); opacity: 0; } 20% { opacity: 1; } to { transform: translateY(14px); opacity: 0; } }
@keyframes flicker { 0%, 84%, 86.5%, 88%, 92%, 100% { opacity: 0.12; } 85%, 89.5% { opacity: 1; } 90.5% { opacity: 0.6; } }
@keyframes flash { 0%, 84%, 86.5%, 88%, 92%, 100% { opacity: 0; } 85%, 89.5% { opacity: 0.9; } 90.5% { opacity: 0.45; } }
@keyframes twinkle { from { opacity: 0.2; } to { opacity: 0.9; } }
@keyframes meteor {
  0% { opacity: 0; transform: translate(-70px, -37px); }
  4% { opacity: 1; }
  16% { opacity: 0; transform: translate(80px, 43px); }
  100% { opacity: 0; transform: translate(80px, 43px); }
}

.sky.still .cloud, .sky.still .rain line, .sky.still .twinkle, .sky.still .meteor, .sky.still .flash, .sky.still .bolt,
.sky.still .press-RISING .star circle:last-child, .sky.still .press-FALLING .star circle:last-child { animation: none; }
.sky.still .bolt { opacity: 1; }
.sky.still .meteor { opacity: 0.8; }

.foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 16px; padding: 10px 20px 12px; border-top: 1px solid var(--border); }
.tip { display: flex; flex-wrap: wrap; gap: 10px; font-size: 0.8rem; color: var(--text-secondary); min-height: 1.2em; }
.legend { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 0.75rem; color: var(--text-secondary); }
.legend li { display: flex; align-items: center; gap: 6px; }
</style>
