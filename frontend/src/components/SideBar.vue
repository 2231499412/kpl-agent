<template>
  <aside class="sidebar" @mouseenter="expanded = true" @mouseleave="expanded = false">
    <!-- 顶部 Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">K</div>
      <transition name="fade-text">
        <span v-if="expanded" class="logo-label">KPL Agent</span>
      </transition>
    </div>

    <!-- 主导航 -->
    <nav class="sidebar-nav">
      <div
        v-for="item in navItems"
        :key="item.id"
        class="nav-item"
        :class="{ active: activeNav === item.id }"
        @click="activeNav = item.id; $emit('navigate', item.id)"
      >
        <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
        <transition name="fade-text">
          <span v-if="expanded" class="nav-label">{{ item.label }}</span>
        </transition>
      </div>
    </nav>

    <!-- 底部工具 -->
    <div class="sidebar-bottom">
      <div v-for="item in bottomItems" :key="item.id" class="nav-item" @click="$emit('action', item.id)">
        <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
        <transition name="fade-text">
          <span v-if="expanded" class="nav-label">{{ item.label }}</span>
        </transition>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref } from 'vue'

defineEmits(['navigate', 'action'])

const expanded = ref(false)
const activeNav = ref('dashboard')

const navItems = [
  { id: 'dashboard', label: '数据总览', icon: 'Odometer' },
  { id: 'team', label: '战队榜', icon: 'Trophy' },
  { id: 'player', label: '选手榜', icon: 'User' },
  { id: 'hero', label: '英雄榜', icon: 'MagicStick' },
  { id: 'match', label: '赛程', icon: 'Calendar' },
  { id: 'equip', label: '装备', icon: 'Box' },
  { id: 'agent', label: 'Agent', icon: 'ChatDotRound' },
]

const bottomItems = [
  { id: 'user', label: '用户', icon: 'UserFilled' },
  { id: 'sound', label: '声音', icon: 'Microphone' },
  { id: 'share', label: '分享', icon: 'Share' },
  { id: 'lang', label: '语言', icon: 'Sunny' },
]
</script>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 67.5px;
  background: #161b22;
  border-right: 1px solid rgba(139, 148, 158, 0.15);
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width 0.3s ease-in-out;
  overflow: hidden;
}
.sidebar:hover {
  width: 202.5px;
}

/* Logo */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(139, 148, 158, 0.15);
  min-height: 64px;
}
.logo-icon {
  width: 32px;
  height: 32px;
  min-width: 32px;
  background: #FFFA00;
  color: #141414;
  font-size: 18px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}
.logo-label {
  font-size: 14px;
  font-weight: 700;
  color: #e6edf3;
  white-space: nowrap;
}

/* 导航区 */
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
  border-radius: 0;
}
.nav-item:hover {
  background: #21262d;
}
.nav-item.active {
  background: #1c2128;
}
.nav-item.active .nav-icon {
  color: #FFFA00;
}

.nav-icon {
  font-size: 20px;
  min-width: 20px;
  color: #8b949e;
  transition: color 0.15s;
}
.nav-item:hover .nav-icon,
.nav-item.active .nav-icon {
  color: #e6edf3;
}

.nav-label {
  font-size: 13px;
  color: #e6edf3;
  font-weight: 500;
}

/* 底部工具 */
.sidebar-bottom {
  border-top: 1px solid rgba(139, 148, 158, 0.15);
  padding: 8px 0;
}

/* 文字淡入动画 */
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
