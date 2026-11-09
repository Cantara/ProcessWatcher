<script setup>
import { ALERT_STATUS, fmtShort } from '../format.js'
defineProps({ alerts: { type: Array, default: () => [] } })
</script>

<template>
  <section v-if="alerts.length" class="alerts" aria-label="Weather alerts">
    <div v-for="(a, i) in alerts" :key="i" class="alert" :class="`alert-${ALERT_STATUS[a.level] || 'warning'}`">
      <span class="badge" :class="`badge-${ALERT_STATUS[a.level] || 'warning'}`">
        <svg width="12" height="12" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2 1 21h22L12 2zm0 6 7 12H5l7-12zm-1 4v4h2v-4h-2zm0 5v2h2v-2h-2z" fill="currentColor"/></svg>
        {{ a.level }}
      </span>
      <span class="title"><strong>{{ a.provider }}</strong>: {{ a.title }}</span>
      <span class="when muted">since {{ fmtShort(a.since) }}</span>
      <a v-if="a.url" :href="a.url" target="_blank" rel="noopener">details</a>
    </div>
  </section>
</template>

<style scoped>
.alerts { display: grid; gap: 8px; margin-top: 16px; }
.alert { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 14px; padding: 10px 16px; border-radius: 10px; border: 1px solid var(--border); background: var(--surface-1); border-left-width: 4px; }
.alert-critical { border-left-color: var(--status-critical); }
.alert-serious { border-left-color: var(--status-serious); }
.alert-warning { border-left-color: var(--status-warning); }
.title { flex: 1 1 240px; }
.when { font-size: 0.85rem; }
</style>
