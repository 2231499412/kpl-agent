<template>
  <LoadingScreen />
  <transition name="sidebar-enter">
    <SideBar v-if="route.meta.showSidebar" />
  </transition>
  <router-view v-slot="{ Component, route: r }">
    <transition name="page-zoom">
      <component :is="Component" :key="r.path" />
    </transition>
  </router-view>

  <!-- 全局背景音乐 -->
  <div class="bgm-control" :class="{ playing: isPlaying, 'bgm-top': route.path === '/lane-radar' }" @click="toggleBgm" :title="isPlaying ? '暂停音乐' : '播放音乐'">
    <span class="bgm-bars"><i /><i /><i /><i /></span>
  </div>
  <audio ref="bgmAudio" src="/bgm.mp3" loop preload="auto"></audio>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import LoadingScreen from './components/LoadingScreen.vue'
import SideBar from './components/SideBar.vue'

const route = useRoute()

const bgmAudio = ref(null)
const isPlaying = ref(false)

function toggleBgm() {
  if (!bgmAudio.value) return
  if (isPlaying.value) {
    bgmAudio.value.pause()
    isPlaying.value = false
  } else {
    bgmAudio.value.play().then(() => { isPlaying.value = true }).catch(() => {})
  }
}

onMounted(() => {
  // 尝试自动播放，大部分浏览器会拦截
  if (bgmAudio.value) {
    bgmAudio.value.play().then(() => { isPlaying.value = true }).catch(() => {})
  }
})

function onPageEnter(el) {
  const targets = el.querySelectorAll('.command-strip, .side-rail, .main-board, .panel')
  targets.forEach((child, i) => {
    child.style.opacity = '0'
    child.style.transform = 'translateY(18px)'
    child.style.transition = `opacity 0.4s ease ${i * 0.1}s, transform 0.4s ease ${i * 0.1}s`
    requestAnimationFrame(() => {
      child.style.opacity = '1'
      child.style.transform = 'translateY(0)'
    })
  })
}
</script>

<style>
.page-zoom-enter-active,
.page-zoom-leave-active {
  transition: opacity 0.4s ease, transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.page-zoom-enter-from {
  opacity: 0;
  transform: scale(1.04);
}
.page-zoom-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
.page-zoom-leave-active {
  position: absolute;
  inset: 0;
}

.sidebar-enter-active {
  transition: opacity 0.3s ease 0.2s;
}
.sidebar-enter-from {
  opacity: 0;
}
.sidebar-leave-active {
  transition: none;
}

/* 背景音乐控制 */
.bgm-control {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: rgba(26, 26, 26, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
  backdrop-filter: blur(8px);
}
.bgm-control:hover {
  background: rgba(26, 26, 26, 0.9);
  transform: scale(1.1);
}
.bgm-control.bgm-top {
  top: 26px;
  bottom: auto;
  right: calc((100% - 1640px) / 2 + 18px);
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.2);
}
.bgm-control.bgm-top:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: none;
}
.bgm-control.bgm-top .bgm-bars i,
.bgm-control.bgm-top.playing .bgm-bars i {
  background: #e8e8e8;
}

.bgm-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 16px;
}
.bgm-bars i {
  display: block;
  width: 3px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 1px;
  transition: background 0.2s;
}
.bgm-bars i:nth-child(1) { height: 6px; }
.bgm-bars i:nth-child(2) { height: 12px; }
.bgm-bars i:nth-child(3) { height: 8px; }
.bgm-bars i:nth-child(4) { height: 14px; }

.bgm-control.playing .bgm-bars i {
  background: #39d8c2;
  animation: bgm-bounce 0.8s ease-in-out infinite alternate;
}
.bgm-control.playing .bgm-bars i:nth-child(1) { animation-delay: 0s; }
.bgm-control.playing .bgm-bars i:nth-child(2) { animation-delay: 0.15s; }
.bgm-control.playing .bgm-bars i:nth-child(3) { animation-delay: 0.3s; }
.bgm-control.playing .bgm-bars i:nth-child(4) { animation-delay: 0.45s; }

@keyframes bgm-bounce {
  0% { height: 4px; }
  100% { height: 16px; }
}

@media (max-width: 767px) {
  .bgm-control {
    right: 12px;
    bottom: calc(78px + env(safe-area-inset-bottom));
    width: 34px;
    height: 34px;
  }
}
</style>
