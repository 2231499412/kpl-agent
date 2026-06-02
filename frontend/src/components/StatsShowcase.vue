<template>
  <section class="stats-section">
    <ScrollReveal v-for="(stat, i) in stats" :key="stat.label" :delay="i * 100">
      <GlassCard>
        <div class="stat-content">
          <span class="stat-number counter-value">{{ stat.display }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
      </GlassCard>
    </ScrollReveal>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ScrollReveal from './ScrollReveal.vue'
import GlassCard from './GlassCard.vue'
import { useAnimatedCounter } from '../composables/useAnimatedCounter'

const teamCount = ref(0)
const matchCount = ref(0)
const playerCount = ref(0)
const heroCount = ref(0)

const teamDisplay = useAnimatedCounter(teamCount)
const matchDisplay = useAnimatedCounter(matchCount)
const playerDisplay = useAnimatedCounter(playerCount)
const heroDisplay = useAnimatedCounter(heroCount)

const stats = ref([
  { label: '战队', display: teamDisplay, key: 'team' },
  { label: '比赛', display: matchDisplay, key: 'match' },
  { label: '选手', display: playerDisplay, key: 'player' },
  { label: '英雄', display: heroDisplay, key: 'hero' },
])

onMounted(async () => {
  try {
    const [teamRes, heroRes] = await Promise.allSettled([
      fetch('/api/query/team/ranking').then(r => r.json()),
      fetch('/api/query/hero/top?sort=pick').then(r => r.json()),
    ])

    if (teamRes.status === 'fulfilled') {
      const data = teamRes.value?.data
      if (Array.isArray(data)) {
        teamCount.value = data.length
        matchCount.value = data.reduce((sum, t) => sum + (t.battleCount || 0), 0)
        playerCount.value = data.length * 5
      }
    }
    if (heroRes.status === 'fulfilled') {
      const data = heroRes.value?.data
      if (Array.isArray(data)) heroCount.value = data.length
    }
  } catch {
    // silent fail - stats will show 0
  }
})
</script>

<style scoped>
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 24px;
}

.stat-content {
  text-align: center;
}

.stat-number {
  display: block;
  font-size: 36px;
  font-weight: 800;
  color: var(--text);
  letter-spacing: -1px;
}

.stat-label {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}
</style>
