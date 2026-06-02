import { onMounted, onUnmounted } from 'vue'

export function useParticles(canvasRef) {
  let animationFrame = null
  let particles = []

  const PARTICLE_COUNT = 70
  const COLORS = [
    'rgba(255, 255, 255, 0.06)',
    'rgba(255, 255, 255, 0.04)',
    'rgba(255, 255, 255, 0.08)',
    'rgba(255, 68, 68, 0.1)',
    'rgba(255, 136, 0, 0.08)',
    'rgba(100, 60, 255, 0.06)',
  ]

  function createParticle(width, height) {
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      size: Math.random() * 2.5 + 0.5,
      speedY: -(Math.random() * 0.3 + 0.1),
      speedX: (Math.random() - 0.5) * 0.2,
      oscillation: Math.random() * Math.PI * 2,
      oscillationSpeed: Math.random() * 0.01 + 0.005,
      oscillationAmplitude: Math.random() * 0.5 + 0.2,
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
    }
  }

  function init(canvas) {
    const ctx = canvas.getContext('2d')
    const dpr = window.devicePixelRatio || 1

    function resize() {
      canvas.width = window.innerWidth * dpr
      canvas.height = window.innerHeight * dpr
      canvas.style.width = window.innerWidth + 'px'
      canvas.style.height = window.innerHeight + 'px'
      ctx.scale(dpr, dpr)
    }

    resize()
    window.addEventListener('resize', resize)

    particles = Array.from({ length: PARTICLE_COUNT }, () =>
      createParticle(window.innerWidth, window.innerHeight)
    )

    function draw() {
      if (document.hidden) {
        animationFrame = requestAnimationFrame(draw)
        return
      }

      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight)

      for (const p of particles) {
        p.y += p.speedY
        p.oscillation += p.oscillationSpeed
        p.x += p.speedX + Math.sin(p.oscillation) * p.oscillationAmplitude

        if (p.y < -10) {
          p.y = window.innerHeight + 10
          p.x = Math.random() * window.innerWidth
        }
        if (p.x < -10) p.x = window.innerWidth + 10
        if (p.x > window.innerWidth + 10) p.x = -10

        ctx.beginPath()
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
        ctx.fillStyle = p.color
        ctx.fill()
      }

      animationFrame = requestAnimationFrame(draw)
    }

    draw()

    return () => {
      cancelAnimationFrame(animationFrame)
      window.removeEventListener('resize', resize)
    }
  }

  let cleanup = null

  onMounted(() => {
    if (canvasRef.value) {
      cleanup = init(canvasRef.value)
    }
  })

  onUnmounted(() => {
    cleanup?.()
  })
}
