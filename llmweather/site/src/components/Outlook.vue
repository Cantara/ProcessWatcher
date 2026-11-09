<script setup>
defineProps({ items: { type: Array, default: () => [] } })
</script>

<template>
  <div class="outlook">
    <div v-for="(o, i) in items" :key="i" class="card item">
      <div class="label">{{ o.label }}</div>
      <p>{{ o.text }}</p>
    </div>
  </div>
</template>

<style scoped>
.outlook { display: grid; gap: 12px; }
.label { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--text-muted); margin-bottom: 6px; font-weight: 600; }
p { font-size: 0.95rem; }
</style>
