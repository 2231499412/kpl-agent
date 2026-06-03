<template>
  <aside class="sidebar" :class="{ expanded }" @mouseenter="expanded = true" @mouseleave="expanded = false">
    <div class="sidebar-logo" @click="router.push('/')">
      <div class="logo-icon">K</div>
      <span class="logo-label">KPL Agent</span>
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
        <span class="nav-label">{{ item.label }}</span>
      </div>
    </nav>

    <div class="sidebar-bottom">
      <div class="nav-item" @click="router.push('/')">
        <el-icon class="nav-icon"><HomeFilled /></el-icon>
        <span class="nav-label">返回首页</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const expanded = ref(false)

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
  background:
    linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)),
    #f8f5ec;
  border-right: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.06);
}
.sidebar.expanded {
  width: 202.5px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  min-height: 64px;
  cursor: pointer;
  transition: background 0.15s;
}
.sidebar-logo:hover {
  background: rgba(0, 0, 0, 0.04);
}

.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border: 1px solid rgba(0, 0, 0, 0.15);
  background: rgba(0, 0, 0, 0.06);
  color: #1a1a1a;
  font-size: 18px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0;
}
.logo-label {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a1a;
  white-space: nowrap;
  visibility: hidden;
  transition: visibility 0s 0.2s;
}
.expanded .logo-label {
  visibility: visible;
  transition: visibility 0s 0.08s;
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
  border-left: 2px solid transparent;
}
.nav-item:hover {
  background: rgba(0, 0, 0, 0.05);
}
.nav-item.active {
  border-left-color: #1a1a1a;
  background: rgba(0, 0, 0, 0.07);
}
.nav-item.active .nav-icon {
  color: #1a1a1a;
}
.nav-item.active .nav-label {
  color: #1a1a1a;
  font-weight: 600;
}

.nav-icon {
  font-size: 20px;
  min-width: 20px;
  color: rgba(0, 0, 0, 0.4);
  transition: color 0.15s;
}
.nav-item:hover .nav-icon {
  color: #1a1a1a;
}

.nav-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.55);
  font-weight: 500;
  white-space: nowrap;
  visibility: hidden;
  transition: visibility 0s 0.2s;
}
.expanded .nav-label {
  visibility: visible;
  transition: visibility 0s 0.08s;
}
.nav-item:hover .nav-label {
  color: #1a1a1a;
}

.sidebar-bottom {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding: 8px 0;
}
</style>
