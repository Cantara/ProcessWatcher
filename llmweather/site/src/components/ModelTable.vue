<script setup>
import { ref, computed } from 'vue'
import { fmtMoney, fmtContext, fmtReleased } from '../format.js'

const props = defineProps({ models: { type: Array, default: () => [] } })

const providers = computed(() => [...new Set(props.models.map(m => m.provider))])
const selected = ref('All')
const sortKey = ref('created')
const sortDir = ref(-1)

function sortBy(key) {
  if (sortKey.value === key) sortDir.value = -sortDir.value
  else { sortKey.value = key; sortDir.value = key === 'name' || key === 'provider' ? 1 : -1 }
}

const rows = computed(() => {
  const list = selected.value === 'All' ? props.models : props.models.filter(m => m.provider === selected.value)
  const k = sortKey.value
  return [...list].sort((a, b) => {
    const av = a[k], bv = b[k]
    if (av === null || av === undefined) return 1
    if (bv === null || bv === undefined) return -1
    if (typeof av === 'string') return av.localeCompare(bv) * sortDir.value
    return (av - bv) * sortDir.value
  })
})

const arrow = key => (sortKey.value === key ? (sortDir.value > 0 ? ' ↑' : ' ↓') : '')
</script>

<template>
  <div class="card table-card">
    <div class="chips" role="tablist" aria-label="Filter by provider">
      <button type="button" class="chip" :class="{ on: selected === 'All' }" @click="selected = 'All'">All</button>
      <button v-for="p in providers" :key="p" type="button" class="chip" :class="{ on: selected === p }" @click="selected = p">{{ p }}</button>
    </div>
    <p v-if="!models.length" class="muted">No model listings in this snapshot.</p>
    <div v-else class="scroll">
      <table>
        <thead>
          <tr>
            <th @click="sortBy('name')">Model{{ arrow('name') }}</th>
            <th @click="sortBy('provider')">Provider{{ arrow('provider') }}</th>
            <th class="num" @click="sortBy('contextLength')">Context{{ arrow('contextLength') }}</th>
            <th class="num" @click="sortBy('promptPerMTok')">Input $/M{{ arrow('promptPerMTok') }}</th>
            <th class="num" @click="sortBy('completionPerMTok')">Output $/M{{ arrow('completionPerMTok') }}</th>
            <th class="num" @click="sortBy('created')">Listed{{ arrow('created') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in rows" :key="m.id">
            <td><span class="name">{{ m.name.replace(/^[^:]+:\s*/, '') }}</span><br /><span class="id mono muted">{{ m.id }}</span></td>
            <td>{{ m.provider }}</td>
            <td class="num mono">{{ fmtContext(m.contextLength) }}</td>
            <td class="num mono">{{ fmtMoney(m.promptPerMTok) }}</td>
            <td class="num mono">{{ fmtMoney(m.completionPerMTok) }}</td>
            <td class="num muted">{{ fmtReleased(m.created) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.chip { border: 1px solid var(--border); background: var(--surface-1); color: var(--text-secondary); border-radius: 999px; padding: 3px 12px; font-size: 0.82rem; cursor: pointer; }
.chip.on { background: var(--accent); border-color: var(--accent); color: #fff; }
.scroll { overflow-x: auto; }
th { text-align: left; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); padding: 8px 10px; border-bottom: 1px solid var(--border); cursor: pointer; white-space: nowrap; user-select: none; }
td { padding: 9px 10px; border-bottom: 1px solid var(--border); vertical-align: top; font-size: 0.92rem; }
tr:last-child td { border-bottom: none; }
.num { text-align: right; }
.name { font-weight: 600; }
.id { font-size: 0.75rem; }
</style>
