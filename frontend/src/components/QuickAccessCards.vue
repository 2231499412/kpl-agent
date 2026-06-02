<template>
  <section class="cards-section">
    <ScrollReveal v-for="(card, i) in cards" :key="card.route" :delay="i * 120">
      <GlassCard hoverable>
        <router-link :to="card.route" class="card-link">
          <div class="card-icon" :style="{ background: card.gradient }">
            <el-icon :size="28"><component :is="card.icon" /></el-icon>
          </div>
          <h3 class="card-title">{{ card.title }}</h3>
          <p class="card-desc">{{ card.desc }}</p>
          <span v-if="card.soon" class="card-badge">即将推出</span>
        </router-link>
      </GlassCard>
    </ScrollReveal>
  </section>
</template>

<script setup>
import ScrollReveal from './ScrollReveal.vue'
import GlassCard from './GlassCard.vue'
import {
  Trophy,
  Calendar,
  Box,
  ChatDotRound,
  DataAnalysis
} from '@element-plus/icons-vue'

const cards = [
  {
    title: '数据排行',
    desc: '战队、选手、英雄多维度排行',
    icon: Trophy,
    route: '/rankings',
    gradient: 'linear-gradient(135deg, rgba(255,68,68,0.15), rgba(255,136,0,0.1))',
  },
  {
    title: '赛程查询',
    desc: '实时赛程、对局详情一网打尽',
    icon: Calendar,
    route: '/matches',
    gradient: 'linear-gradient(135deg, rgba(100,60,255,0.15), rgba(52,199,89,0.1))',
  },
  {
    title: '装备分析',
    desc: '装备出场率、分路偏好、热门英雄',
    icon: Box,
    route: '/equipment',
    gradient: 'linear-gradient(135deg, rgba(52,199,89,0.15), rgba(90,200,250,0.1))',
  },
  {
    title: 'AI 复盘',
    desc: '智能分析比赛数据，生成复盘报告',
    icon: ChatDotRound,
    route: '/agent',
    gradient: 'linear-gradient(135deg, rgba(255,149,0,0.15), rgba(255,68,68,0.1))',
  },
  {
    title: 'BP 分析',
    desc: 'Ban/Pick 策略分析与推荐',
    icon: DataAnalysis,
    route: '/bp-analysis',
    gradient: 'linear-gradient(135deg, rgba(90,200,250,0.15), rgba(100,60,255,0.1))',
    soon: true,
  },
]
</script>

<style scoped>
.cards-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
}

.card-link {
  display: block;
  text-decoration: none;
  color: inherit;
  position: relative;
}

.card-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text);
  margin-bottom: 16px;
}

.card-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.card-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.card-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-muted);
  border: 1px solid rgba(255, 68, 68, 0.2);
}
</style>
