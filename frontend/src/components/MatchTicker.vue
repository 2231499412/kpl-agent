<template>
  <section class="ticker-section" v-if="matches.length">
    <ScrollReveal>
      <div class="ticker-header">
        <span class="ticker-title">近期赛事</span>
      </div>
      <div class="ticker-track" ref="trackRef">
        <div class="ticker-scroll">
          <div v-for="(match, i) in matches" :key="match.matchId || i" class="ticker-item glass">
            <span :class="['stage-tag', stageClass(match.matchStage)]">{{ match.matchStageDesc }}</span>
            <div class="ticker-teams">
              <span class="ticker-team">{{ match.camp1TeamName }}</span>
              <span class="ticker-score">{{ match.camp1Score }} : {{ match.camp2Score }}</span>
              <span class="ticker-team">{{ match.camp2TeamName }}</span>
            </div>
            <span class="ticker-time">{{ formatTime(match.startTime) }}</span>
          </div>
        </div>
      </div>
    </ScrollReveal>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ScrollReveal from './ScrollReveal.vue'

const matches = ref([])

function stageClass(stage) {
  if (stage === 'js' || stage === 'zjs') return 'stage-final'
  if (stage === 'jhs') return 'stage-playoff'
  return 'stage-regular'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(5, 16)
}

onMounted(async () => {
  try {
    const resp = await fetch('/api/query/match/schedule')
    const body = await resp.json()
    const data = body?.data
    if (Array.isArray(data)) {
      matches.value = data
        .sort((a, b) => (b.startTime || '').localeCompare(a.startTime || ''))
        .slice(0, 12)
    }
  } catch {
    // silent fail
  }
})
</script>

<style scoped>
.ticker-section {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
}

.ticker-header {
  margin-bottom: 16px;
}

.ticker-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.ticker-track {
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.ticker-track::-webkit-scrollbar {
  display: none;
}

.ticker-scroll {
  display: flex;
  gap: 12px;
  padding-bottom: 4px;
}

.ticker-item {
  flex-shrink: 0;
  padding: 14px 20px;
  min-width: 260px;
}

.ticker-teams {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 4px;
}

.ticker-team {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
}

.ticker-score {
  font-size: 16px;
  font-weight: 800;
  color: var(--accent);
  min-width: 44px;
  text-align: center;
}

.ticker-time {
  font-size: 11px;
  color: var(--muted);
}

@media (max-width: 767px) {
  .ticker-section {
    padding: 0 12px;
  }

  .ticker-header {
    margin-bottom: 10px;
  }

  .ticker-scroll {
    gap: 8px;
  }

  .ticker-item {
    min-width: 220px;
    padding: 12px 14px;
  }

  .ticker-teams {
    gap: 6px;
  }

  .ticker-team {
    max-width: 72px;
    overflow: hidden;
    font-size: 12px;
    text-overflow: ellipsis;
  }

  .ticker-score {
    min-width: 38px;
    font-size: 14px;
  }
}
</style>
