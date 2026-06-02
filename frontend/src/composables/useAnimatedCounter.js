import { ref, watch } from 'vue'

export function useAnimatedCounter(targetRef, duration = 1500) {
  const displayValue = ref(0)
  let animationFrame = null

  function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3)
  }

  function animate(target) {
    const start = performance.now()
    const startValue = displayValue.value

    function step(now) {
      const elapsed = now - start
      const progress = Math.min(elapsed / duration, 1)
      displayValue.value = Math.round(startValue + (target - startValue) * easeOutCubic(progress))

      if (progress < 1) {
        animationFrame = requestAnimationFrame(step)
      }
    }

    cancelAnimationFrame(animationFrame)
    animationFrame = requestAnimationFrame(step)
  }

  watch(targetRef, (newVal) => {
    if (typeof newVal === 'number') animate(newVal)
  }, { immediate: true })

  return displayValue
}
