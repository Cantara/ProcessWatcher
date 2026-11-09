<script setup>
import { FRONT_LABEL } from '../format.js'
defineProps({ fronts: { type: Array, default: () => [] } })

const status = { 'price-drop': 'good', 'price-rise': 'serious', 'new-model': 'warning', deprecation: 'none' }
</script>

<template>
  <div class="card fronts">
    <p v-if="!fronts.length" class="muted">No fronts on the map. Prices and the model line-up are unchanged since the previous observation.</p>
    <ul v-else>
      <li v-for="(f, i) in fronts" :key="i">
        <span class="badge" :class="`badge-${status[f.type] || 'none'}`">{{ FRONT_LABEL[f.type] || f.type }}</span>
        <div class="body">
          <div><strong>{{ f.model }}</strong> <span class="muted">· {{ f.provider }}</span></div>
          <div class="secondary detail">{{ f.detail }}</div>
        </div>
        <span v-if="f.magnitude" class="mag mono" :class="f.magnitude < 0 ? 'down' : 'up'">{{ f.magnitude < 0 ? '−' : '+' }}{{ Math.round(Math.abs(f.magnitude) * 100) }}%</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
li { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: start; }
.badge { margin-top: 2px; }
.detail { font-size: 0.88rem; }
.mag { font-weight: 700; font-size: 1rem; }
.down { color: var(--status-good); }
.up { color: var(--status-serious); }
</style>
