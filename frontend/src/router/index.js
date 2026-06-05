import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomePage.vue'),
    meta: { title: 'KPL 赛事工具站' }
  },
  {
    path: '/rankings',
    name: 'rankings',
    component: () => import('../views/RankingsPage.vue'),
    meta: { title: 'KPL - 数据排行', showSidebar: true }
  },
  {
    path: '/matches',
    name: 'matches',
    component: () => import('../views/MatchesPage.vue'),
    meta: { title: 'KPL - 赛程', showSidebar: true }
  },
  {
    path: '/equipment',
    name: 'equipment',
    component: () => import('../views/EquipmentPage.vue'),
    meta: { title: 'KPL - 装备分析', showSidebar: true }
  },
  {
    path: '/agent',
    name: 'agent',
    component: () => import('../views/AgentPage.vue'),
    meta: { title: 'KPL - AI 复盘', showSidebar: true }
  },
  {
    path: '/bp-analysis',
    name: 'bp-analysis',
    component: () => import('../views/BpAnalysisPage.vue'),
    meta: { title: 'KPL - BP 分析', showSidebar: true }
  },
  {
    path: '/tier-list',
    name: 'tier-list',
    component: () => import('../views/TierListPage.vue'),
    meta: { title: 'KPL - 英雄梯度榜', showSidebar: true }
  },
  {
    path: '/hero-gallery',
    name: 'hero-gallery',
    component: () => import('../views/HeroGalleryPage.vue'),
    meta: { title: 'KPL - 英雄原画画廊', showSidebar: true }
  },
  {
    path: '/bp-simulator',
    name: 'bp-simulator',
    component: () => import('../views/BpSimulatorPage.vue'),
    meta: { title: 'KPL - BP 模拟器', showSidebar: true }
  },
  {
    path: '/lane-radar',
    name: 'lane-radar',
    component: () => import('../views/LaneRadarPage.vue'),
    meta: { title: 'KPL - 对位雷达图', showSidebar: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title || 'KPL 赛事工具站'
})

export default router
