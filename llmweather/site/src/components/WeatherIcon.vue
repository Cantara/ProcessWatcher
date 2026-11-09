<script setup>
defineProps({ condition: { type: String, default: 'UNKNOWN' }, size: { type: Number, default: 56 } })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 64 64" aria-hidden="true" class="icon">
    <template v-if="condition === 'SUNNY'">
      <circle cx="32" cy="32" r="13" fill="var(--sun)" />
      <g stroke="var(--sun)" stroke-width="3.5" stroke-linecap="round">
        <line x1="32" y1="6" x2="32" y2="13" /><line x1="32" y1="51" x2="32" y2="58" />
        <line x1="6" y1="32" x2="13" y2="32" /><line x1="51" y1="32" x2="58" y2="32" />
        <line x1="13.6" y1="13.6" x2="18.6" y2="18.6" /><line x1="45.4" y1="45.4" x2="50.4" y2="50.4" />
        <line x1="13.6" y1="50.4" x2="18.6" y2="45.4" /><line x1="45.4" y1="18.6" x2="50.4" y2="13.6" />
      </g>
    </template>
    <template v-else-if="condition === 'PARTLY_CLOUDY'">
      <circle cx="24" cy="24" r="11" fill="var(--sun)" />
      <g stroke="var(--sun)" stroke-width="3" stroke-linecap="round">
        <line x1="24" y1="5" x2="24" y2="9" /><line x1="5" y1="24" x2="9" y2="24" />
        <line x1="10.6" y1="10.6" x2="13.4" y2="13.4" /><line x1="34.6" y1="13.4" x2="37.4" y2="10.6" />
      </g>
      <path d="M22 50h26a8 8 0 0 0 1-16A12 12 0 0 0 26 32a7 7 0 0 0-4 18z" fill="var(--cloud)" />
    </template>
    <template v-else-if="condition === 'CLOUDY'">
      <path d="M16 50h30a9 9 0 0 0 1-18A13 13 0 0 0 22 30a8 8 0 0 0-6 20z" fill="var(--cloud)" />
      <path d="M30 40h20a6 6 0 0 0 .7-12A9 9 0 0 0 34 26a5.5 5.5 0 0 0-4 14z" fill="var(--cloud-dark)" opacity="0.55" />
    </template>
    <template v-else-if="condition === 'RAIN'">
      <path d="M16 42h30a9 9 0 0 0 1-18A13 13 0 0 0 22 22a8 8 0 0 0-6 20z" fill="var(--cloud-dark)" />
      <g stroke="var(--rain)" stroke-width="3" stroke-linecap="round">
        <line x1="22" y1="48" x2="19" y2="56" /><line x1="32" y1="48" x2="29" y2="56" /><line x1="42" y1="48" x2="39" y2="56" />
      </g>
    </template>
    <template v-else-if="condition === 'STORM'">
      <path d="M16 40h30a9 9 0 0 0 1-18A13 13 0 0 0 22 20a8 8 0 0 0-6 20z" fill="var(--cloud-dark)" />
      <polygon points="34,38 24,52 32,52 29,62 41,46 33,46 36,38" fill="var(--bolt)" />
    </template>
    <template v-else>
      <path d="M16 46h30a9 9 0 0 0 1-18A13 13 0 0 0 22 26a8 8 0 0 0-6 20z" fill="none" stroke="var(--cloud)" stroke-width="3" stroke-dasharray="5 4" />
      <text x="32" y="41" text-anchor="middle" font-size="16" font-weight="700" fill="var(--text-muted)" font-family="var(--font)">?</text>
    </template>
  </svg>
</template>

<style scoped>
.icon { flex: none; }
</style>
