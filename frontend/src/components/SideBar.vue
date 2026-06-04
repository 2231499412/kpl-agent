<template>
  <aside class="sidebar" :class="{ expanded, 'theme-dark': theme === 'dark' }" @mouseenter="expanded = true" @mouseleave="expanded = false">
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const expanded = ref(false)
const theme = ref(localStorage.getItem('kpl-theme') || 'light')

function onThemeChanged(e) {
  theme.value = e.detail || 'light'
}
onMounted(() => window.addEventListener('theme-changed', onThemeChanged))
onUnmounted(() => window.removeEventListener('theme-changed', onThemeChanged))

const navItems = [
  { id: 'rankings', label: '数据排行', icon: 'Trophy', route: '/rankings' },
  { id: 'matches', label: '赛程', icon: 'Calendar', route: '/matches' },
  { id: 'equipment', label: '装备分析', icon: 'Box', route: '/equipment' },
  { id: 'agent', label: 'AI 复盘', icon: 'ChatDotRound', route: '/agent' },
  { id: 'bp-analysis', label: 'BP 分析', icon: 'DataAnalysis', route: '/bp-analysis' },
  { id: 'tier-list', label: '梯度榜', icon: 'Histogram', route: '/tier-list' },
  { id: 'bp-simulator', label: 'BP模拟器', icon: 'Operation', route: '/bp-simulator' },
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
  background: #1a1a1a;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
  overflow: hidden;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.2);
}
.sidebar.expanded {
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
  background: rgba(255, 255, 255, 0.06);
}

.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.1);
  color: #e8e8e8;
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
  color: #e8e8e8;
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
  background: rgba(255, 255, 255, 0.06);
}
.nav-item.active {
  border-left-color: #e8e8e8;
  background: rgba(255, 255, 255, 0.1);
}
.nav-item.active .nav-icon {
  color: #e8e8e8;
}
.nav-item.active .nav-label {
  color: #e8e8e8;
  font-weight: 600;
}

.nav-icon {
  font-size: 20px;
  min-width: 20px;
  color: rgba(255, 255, 255, 0.45);
  transition: color 0.15s;
}
.nav-item:hover .nav-icon {
  color: #e8e8e8;
}

.nav-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
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
  color: #e8e8e8;
}

.sidebar-bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 8px 0;
}

/* 暗色主题 → 白色侧边栏 */
.sidebar.theme-dark {
  background: #ffffff;
  border-right-color: rgba(0, 0, 0, 0.1);
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.08);
}
.sidebar.theme-dark .logo-icon {
  border-color: rgba(0, 0, 0, 0.2);
  background: rgba(0, 0, 0, 0.06);
  color: #1a1a1a;
}
.sidebar.theme-dark .logo-label {
  color: #1a1a1a;
}
.sidebar.theme-dark .sidebar-logo {
  border-bottom-color: rgba(0, 0, 0, 0.08);
}
.sidebar.theme-dark .sidebar-logo:hover {
  background: rgba(0, 0, 0, 0.04);
}
.sidebar.theme-dark .nav-icon {
  color: rgba(0, 0, 0, 0.4);
}
.sidebar.theme-dark .nav-item:hover .nav-icon {
  color: #1a1a1a;
}
.sidebar.theme-dark .nav-item:hover {
  background: rgba(0, 0, 0, 0.04);
}
.sidebar.theme-dark .nav-item.active {
  border-left-color: #1a1a1a;
  background: rgba(0, 0, 0, 0.06);
}
.sidebar.theme-dark .nav-item.active .nav-icon {
  color: #1a1a1a;
}
.sidebar.theme-dark .nav-item.active .nav-label {
  color: #1a1a1a;
}
.sidebar.theme-dark .nav-label {
  color: rgba(0, 0, 0, 0.55);
}
.sidebar.theme-dark .nav-item:hover .nav-label {
  color: #1a1a1a;
}
.sidebar.theme-dark .sidebar-bottom {
  border-top-color: rgba(0, 0, 0, 0.08);
}

@media (max-width: 767px) {
  .sidebar,
  .sidebar.expanded {
    left: 8px;
    right: 8px;
    top: auto;
    bottom: calc(8px + env(safe-area-inset-bottom));
    width: auto;
    height: 58px;
    flex-direction: row;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    background: rgba(26, 26, 26, 0.96);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(18px);
  }

  .sidebar-logo,
  .sidebar-bottom {
    display: none;
  }

  .sidebar-nav {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    width: 100%;
    padding: 0;
    overflow: visible;
  }

  .nav-item {
    min-width: 0;
    height: 58px;
    padding: 5px 2px 4px;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    border-left: 0;
    border-top: 2px solid transparent;
  }

  .nav-item.active {
    border-left-color: transparent;
    border-top-color: #1a1a1a;
  }

  /* 移动端暗色主题 → 白色底栏 */
  .sidebar.theme-dark,
  .sidebar.theme-dark.expanded {
    background: rgba(255, 255, 255, 0.96);
    border-color: rgba(0, 0, 0, 0.1);
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
  }
  .sidebar.theme-dark .nav-item.active {
    border-top-color: #ffffff;
  }
  .sidebar.theme-dark .nav-icon {
    color: rgba(0, 0, 0, 0.4);
  }
  .sidebar.theme-dark .nav-label {
    color: rgba(0, 0, 0, 0.55);
  }
  .sidebar.theme-dark .nav-item.active .nav-icon {
    color: #1a1a1a;
  }
  .sidebar.theme-dark .nav-item.active .nav-label {
    color: #1a1a1a;
  }

  .nav-icon {
    min-width: 18px;
    font-size: 18px;
  }

  .nav-label,
  .expanded .nav-label {
    display: block;
    max-width: 100%;
    overflow: hidden;
    font-size: 9px;
    line-height: 1.1;
    text-overflow: ellipsis;
    visibility: visible;
  }
}
</style>
