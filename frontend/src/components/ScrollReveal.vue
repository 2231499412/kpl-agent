<template>
  <div ref="elementRef" class="scroll-reveal" :class="{ revealed: isRevealed }" :style="{ transitionDelay: delay + 'ms' }">
    <slot />
  </div>
</template>

<script setup>
import { useScrollReveal } from '../composables/useScrollReveal'

const props = defineProps({
  delay: { type: Number, default: 0 },
  threshold: { type: Number, default: 0.15 }
})

const { elementRef, isRevealed } = useScrollReveal({ threshold: props.threshold })
</script>
