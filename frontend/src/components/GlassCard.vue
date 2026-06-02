<template>
  <div class="glass-card" :class="{ hoverable }">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  hoverable: { type: Boolean, default: false }
})
</script>

<style scoped>
.glass-card {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 24px;
  transition: transform 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
}
.glass-card.hoverable {
  cursor: pointer;
}
.glass-card.hoverable:hover {
  transform: translateY(-4px);
  border-color: rgba(255, 68, 68, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3), 0 0 20px var(--accent-glow);
}
</style>
