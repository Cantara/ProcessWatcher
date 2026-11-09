<script setup>
import { fmtDateTime } from '../format.js'
defineProps({ sources: { type: Array, default: () => [] }, generatedAt: String })
</script>

<template>
  <footer class="foot">
    <details class="card">
      <summary>Instruments <span class="muted">({{ sources.filter(s => s.ok).length }} of {{ sources.length }} answered)</span></summary>
      <ul>
        <li v-for="(s, i) in sources" :key="i">
          <span class="badge" :class="s.ok ? 'badge-good' : 'badge-critical'">{{ s.ok ? 'ok' : 'failed' }}</span>
          <span>{{ s.source }}</span>
          <span class="muted detail">{{ s.detail }}</span>
        </li>
      </ul>
    </details>
    <p class="muted credits">
      Observed {{ fmtDateTime(generatedAt) }}. Conditions come from provider status pages and optional API probes; prices and model listings from OpenRouter.
      The forecast is an indication, not a monitoring tool. An open source project by <a href="https://wiki.cantara.no" target="_blank" rel="noopener">Cantara</a>,
      source on <a href="https://github.com/Cantara/LLMWeather" target="_blank" rel="noopener">GitHub</a>.
    </p>
  </footer>
</template>

<style scoped>
.foot { margin-top: 32px; display: grid; gap: 14px; }
summary { cursor: pointer; font-weight: 600; }
ul { list-style: none; margin: 12px 0 0; padding: 0; display: grid; gap: 6px; font-size: 0.88rem; }
li { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.detail { font-size: 0.8rem; overflow-wrap: anywhere; }
.credits { font-size: 0.8rem; max-width: 80ch; }
</style>
