<template>
  <canvas ref="canvasRef" class="particle-canvas" />
</template>

<script setup>
import { ref } from 'vue'
import { useParticles } from '../composables/useParticles'

const canvasRef = ref(null)
useParticles(canvasRef)
</script>

<style scoped>
.particle-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
</style>
