<script setup>
import { computed, ref } from 'vue'
import { fmtShort, fmtLatency, conditionLabel, conditionStatus } from '../format.js'

const props = defineProps({ points: { type: Array, default: () => [] }, width: { type: Number, default: 280 }, height: { type: Number, default: 56 } })

const pad = { top: 6, bottom: 4, left: 2, right: 2 }
const latencies = computed(() => props.points.filter(p => p.latencyMs !== null && p.latencyMs !== undefined))
const hasLatency = computed(() => latencies.value.length >= 2)

const xOf = (i) => pad.left + (props.points.length === 1 ? (props.width - pad.left - pad.right) / 2 : (i * (props.width - pad.left - pad.right)) / (props.points.length - 1))
const yScale = computed(() => {
  const vals = latencies.value.map(p => p.latencyMs)
  const max = Math.max(...vals, 1)
  const min = Math.min(...vals, 0)
  const span = Math.max(max - min, 1)
  return v => pad.top + (1 - (v - min) / span) * (props.height - pad.top - pad.bottom)
})
const path = computed(() => {
  if (!hasLatency.value) return ''
  return props.points
    .map((p, i) => (p.latencyMs === null || p.latencyMs === undefined ? null : `${xOf(i).toFixed(1)},${yScale.value(p.latencyMs).toFixed(1)}`))
    .filter(Boolean)
    .map((xy, i) => (i === 0 ? 'M' : 'L') + xy)
    .join(' ')
})

const hover = ref(null)
function onMove(ev) {
  const rect = ev.currentTarget.getBoundingClientRect()
  const x = ((ev.clientX - rect.left) / rect.width) * props.width
  let best = 0
  let bestDist = Infinity
  props.points.forEach((_, i) => {
    const d = Math.abs(xOf(i) - x)
    if (d < bestDist) { bestDist = d; best = i }
  })
  hover.value = best
}
const hovered = computed(() => (hover.value === null ? null : props.points[hover.value]))
const cellW = computed(() => Math.max(2, (props.width - pad.left - pad.right) / Math.max(props.points.length, 1) - 1.5))
</script>

<template>
  <div class="spark">
    <svg :viewBox="`0 0 ${width} ${height + 12}`" preserveAspectRatio="none" class="plot" role="img"
         :aria-label="hasLatency ? 'Latency over the last 48 hours' : 'Conditions over the last 48 hours'"
         @mousemove="onMove" @mouseleave="hover = null">
      <!-- condition strip along the bottom: identity by colour AND by hover label -->
      <rect v-for="(p, i) in points" :key="'c' + i"
            :x="xOf(i) - cellW / 2" :y="height + 3" :width="cellW" height="7" rx="1.5"
            :fill="`var(--status-${conditionStatus(p.condition)})`" :opacity="hover === i ? 1 : 0.8" />
      <template v-if="hasLatency">
        <path :d="path" fill="none" stroke="var(--series-1)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
        <line v-if="hovered" :x1="xOf(hover)" :x2="xOf(hover)" :y1="0" :y2="height" stroke="var(--text-muted)" stroke-width="1" stroke-dasharray="2 3" vector-effect="non-scaling-stroke" />
        <circle v-if="hovered && hovered.latencyMs !== null && hovered.latencyMs !== undefined"
                :cx="xOf(hover)" :cy="yScale(hovered.latencyMs)" r="3.5" fill="var(--series-1)" stroke="var(--surface-1)" stroke-width="2" />
      </template>
      <text v-else :x="width / 2" :y="height / 2 + 4" text-anchor="middle" font-size="11" fill="var(--text-muted)" font-family="var(--font)">
        No latency probe configured
      </text>
    </svg>
    <div class="tip" :class="{ show: hovered }" aria-live="polite">
      <template v-if="hovered">
        <span class="mono">{{ fmtShort(hovered.ts) }}</span>
        <span>{{ conditionLabel(hovered.condition) }}</span>
        <span v-if="hovered.latencyMs !== null && hovered.latencyMs !== undefined" class="mono">{{ fmtLatency(hovered.latencyMs) }}</span>
      </template>
      <span v-else class="muted">Last 48 hours. Hover for detail.</span>
    </div>
  </div>
</template>

<style scoped>
.spark { display: grid; gap: 4px; }
.plot { width: 100%; height: 68px; display: block; cursor: crosshair; }
.tip { display: flex; gap: 12px; font-size: 0.78rem; color: var(--text-secondary); min-height: 1.2em; }
</style>
