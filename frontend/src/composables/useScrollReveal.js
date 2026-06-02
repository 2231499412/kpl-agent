import { ref, onMounted, onUnmounted } from 'vue'

export function useScrollReveal(options = {}) {
  const elementRef = ref(null)
  const isRevealed = ref(false)
  let observer = null

  const { threshold = 0.15, rootMargin = '0px 0px -50px 0px' } = options

  onMounted(() => {
    if (!elementRef.value) return
    observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          isRevealed.value = true
          observer?.disconnect()
        }
      },
      { threshold, rootMargin }
    )
    observer.observe(elementRef.value)
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { elementRef, isRevealed }
}
