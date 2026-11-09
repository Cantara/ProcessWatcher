<script setup>
import { computed } from 'vue'
import { fmtDateTime, fmtAgo } from '../format.js'

const props = defineProps({ forecast: { type: Object, required: true }, reporting: Number, total: Number })

const severityStatus = computed(() => ({ calm: 'good', unsettled: 'serious', stormy: 'critical' }[props.forecast.severity] || 'none'))
const severityLabel = computed(() => ({ calm: 'Calm', unsettled: 'Unsettled', stormy: 'Stormy' }[props.forecast.severity] || props.forecast.severity))
const paragraphs = computed(() => (props.forecast.bulletin || '').split(/\n\s*\n/).filter(Boolean))
const author = computed(() => props.forecast.bulletinAuthor && props.forecast.bulletinAuthor !== 'rules'
  ? `Bulletin written by ${props.forecast.bulletinAuthor} from the recorded observations`
  : 'Bulletin generated from the recorded observations')
</script>

<template>
  <section class="hero card">
    <div class="meta">
      <span class="badge" :class="`badge-${severityStatus}`"><span class="dot"></span>{{ severityLabel }}</span>
      <span class="muted">Observed {{ fmtDateTime(forecast.generatedAt) }} <span class="ago">({{ fmtAgo(forecast.generatedAt) }})</span></span>
      <span class="muted">{{ reporting }} of {{ total }} stations reporting</span>
    </div>
    <h1>{{ forecast.headline }}</h1>
    <div class="bulletin">
      <p v-for="(p, i) in paragraphs" :key="i">{{ p }}</p>
    </div>
    <p class="author muted">{{ author }}</p>
  </section>
</template>

<style scoped>
.hero { margin-top: 24px; padding: 26px 28px; background: linear-gradient(140deg, color-mix(in srgb, var(--accent-soft) 45%, var(--surface-1)) 0%, var(--surface-1) 55%); }
.meta { display: flex; flex-wrap: wrap; gap: 8px 18px; align-items: center; font-size: 0.85rem; margin-bottom: 12px; }
.ago { opacity: 0.8; }
h1 { font-size: clamp(1.7rem, 4vw, 2.6rem); font-weight: 750; letter-spacing: -0.02em; margin-bottom: 14px; }
.bulletin { display: grid; gap: 10px; max-width: 72ch; font-size: 1.02rem; color: var(--text-primary); }
.author { margin-top: 14px; font-size: 0.8rem; }
</style>
