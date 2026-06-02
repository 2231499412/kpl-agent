<template>
  <aside class="sidebar" @mouseenter="expanded = true" @mouseleave="expanded = false">
    <div class="sidebar-logo" @click="router.push('/')">
      <div class="logo-icon">K</div>
      <transition name="fade-text">
        <span v-if="expanded" class="logo-label">KPL Agent</span>
      </transition>
    </div>

    <nav class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNav === item.id }"
        @click="router.push(item.route)"
      >
        <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
        <transition name="fade-text">
          <span v-if="expanded" class="nav-label">{{ item.label }}</span>
        </transition>
      </div>
    </nav>

    <div class="sidebar-bottom">
      <div class="nav-item" @click="router.push('/')">
        <el-icon class="nav-icon"><HomeFilled /></el-icon>
        <transition name="fade-text">
          <span v-if="expanded" class="nav-label">返回首页</span>
        </transition>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const navItems = [
  { id: 'rankings', label: '数据排行', icon: 'Trophy', route: '/rankings' },
  { id: 'matches', label: '赛程', icon: 'Calendar', route: '/matches' },
  { id: 'equipment', label: '装备分析', icon: 'Box', route: '/equipment' },
  { id: 'agent', label: 'AI 复盘', icon: 'ChatDotRound', route: '/agent' },
  { id: 'bp-analysis', label: 'BP 分析', icon: 'DataAnalysis', route: '/bp-analysis' },
]

const activeNav = computed(() => {
  const match = navItems.find(item => item.route === route.path)
  return match?.id || ''
})
</script>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 67.5px;
  background: #0d0d14;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width 0.3s ease-in-out;
  overflow: hidden;
}
.sidebar:hover {
  width: 202.5px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  min-height: 64px;
  cursor: pointer;
  transition: background 0.15s;
}
.sidebar-logo:hover {
  background: rgba(255, 255, 255, 0.03);
}

.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  background: linear-gradient(135deg, var(--accent), var(--accent-secondary));
  color: #fff;
  font-size: 18px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}
.logo-label {
  font-size: 14px;
  font-weight: 700;
  color: #ffffff;
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 18px;
  height: 44px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.04);
}
.nav-item.active {
  background: rgba(255, 68, 68, 0.08);
}
.nav-item.active .nav-icon {
  color: var(--accent);
}
.nav-item.active .nav-label {
  color: var(--accent);
}

.nav-icon {
  font-size: 20px;
  min-width: 20px;
  color: #666;
  transition: color 0.15s;
}
.nav-item:hover .nav-icon {
  color: #ccc;
}

.nav-label {
  font-size: 13px;
  color: #aaa;
  font-weight: 500;
  transition: color 0.15s;
}
.nav-item:hover .nav-label {
  color: #e0e0e0;
}

.sidebar-bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 8px 0;
}

.fade-text-enter-active {
  transition: opacity 0.2s ease 0.1s;
}
.fade-text-leave-active {
  transition: opacity 0.1s ease;
}
.fade-text-enter-from,
.fade-text-leave-to {
  opacity: 0;
}
</style>
