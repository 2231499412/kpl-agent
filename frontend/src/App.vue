<template>
  <LoadingScreen />
  <transition name="sidebar-enter">
    <SideBar v-if="route.meta.showSidebar && !(route.path === '/lane-radar' && isMobileLandscape)" />
  </transition>
  <router-view v-slot="{ Component, route: r }">
    <transition name="page-zoom">
      <component :is="Component" :key="r.path" />
    </transition>
  </router-view>

  <!-- 全局背景音乐 -->
  <div class="bgm-control" :class="{ playing: isPlaying, dark: isDark, 'bgm-top': route.path === '/lane-radar', 'bgm-agent': route.path === '/agent' }" @click="toggleBgm" :title="isPlaying ? '暂停音乐' : '播放音乐'">
    <span class="bgm-bars"><i /><i /><i /><i /></span>
  </div>
  <audio ref="bgmAudio" src="https://music.163.com/song/media/outer/url?id=1361800752.mp3" loop preload="auto" @error="onBgmError"></audio>

  <!-- 回到顶部 -->
  <div v-if="route.path !== '/lane-radar'" class="back-to-top" :class="{ dark: isDark, 'back-top-agent': route.path === '/agent' }" @click="scrollToTop" title="回到顶部">
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15" /></svg>
  </div>

  <!-- 手机端回到首页 -->
  <div v-if="route.meta.showSidebar && !(route.path === '/lane-radar' && isMobileLandscape)" class="mobile-home-btn" :class="{ dark: isDark }" @click="router.push('/')" title="回到首页">
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12l9-9 9 9"/><path d="M5 10v10a1 1 0 001 1h3a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1h3a1 1 0 001-1V10"/></svg>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LoadingScreen from './components/LoadingScreen.vue'
import SideBar from './components/SideBar.vue'

const route = useRoute()
const router = useRouter()

const bgmAudio = ref(null)
const isPlaying = ref(false)
const showBackToTop = ref(false)
const isDark = ref(false)
const bgmMuted = ref(localStorage.getItem('bgm-muted') === 'true')
const isMobileLandscape = ref(false)

function checkScroll() {
  const sy = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0
  showBackToTop.value = sy > 300
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  document.documentElement.scrollTo({ top: 0, behavior: 'smooth' })
}

function toggleBgm() {
  if (!bgmAudio.value) return
  if (isPlaying.value) {
    bgmAudio.value.pause()
    isPlaying.value = false
    bgmMuted.value = true
    localStorage.setItem('bgm-muted', 'true')
  } else {
    bgmAudio.value.play().then(() => {
      isPlaying.value = true
      bgmMuted.value = false
      localStorage.setItem('bgm-muted', 'false')
    }).catch(() => {})
  }
}

function detectTheme() {
  const main = document.querySelector('.theme-dark')
  isDark.value = !!main
}

function checkMobileLandscape() {
  isMobileLandscape.value = window.innerWidth <= 767 && window.innerWidth > window.innerHeight
}

function onBgmError() {
  if (!bgmAudio.value || bgmAudio.value.src.includes('/bgm.mp3')) return
  bgmAudio.value.src = '/bgm.mp3'
}

function tryPlayBgm() {
  if (!bgmAudio.value || isPlaying.value || bgmMuted.value) return
  bgmAudio.value.play().then(() => {
    isPlaying.value = true
  }).catch(() => {})
}

function onFirstInteraction() {
  if (!bgmMuted.value) tryPlayBgm()
  document.removeEventListener('touchstart', onFirstInteraction)
  document.removeEventListener('click', onFirstInteraction)
  document.removeEventListener('keydown', onFirstInteraction)
}

onMounted(() => {
  tryPlayBgm()
  // 移动端需要用户交互才能播放音频
  if (!bgmMuted.value) {
    document.addEventListener('touchstart', onFirstInteraction, { once: false })
    document.addEventListener('click', onFirstInteraction, { once: false })
    document.addEventListener('keydown', onFirstInteraction, { once: false })
  }
  document.addEventListener('scroll', checkScroll, { passive: true })
  setInterval(checkScroll, 500)
  detectTheme()
  checkMobileLandscape()
  window.addEventListener('resize', checkMobileLandscape)
  window.addEventListener('orientationchange', () => setTimeout(checkMobileLandscape, 100))
  const observer = new MutationObserver(detectTheme)
  observer.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'] })
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

@media (max-width: 767px) {
  .page-zoom-enter-active,
  .page-zoom-leave-active {
    transition: opacity 0.25s ease;
  }
  .page-zoom-enter-from,
  .page-zoom-leave-to {
    opacity: 0;
    transform: none;
  }
  .page-zoom-leave-active {
    position: absolute;
    inset: 0;
  }
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
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s;
}
.bgm-control.dark {
  background: #e8e8e8;
  border-color: rgba(0, 0, 0, 0.12);
}
.bgm-control.dark .bgm-bars i {
  background: #1a1a1a;
}
.bgm-control:hover {
  background: rgba(255, 255, 255, 0.06);
}
.bgm-control.dark:hover {
  background: rgba(232, 232, 232, 0.85);
}

.bgm-control.bgm-top {
  top: 26px;
  bottom: auto;
  right: calc((100% - 1640px) / 2 + 18px);
}

.bgm-control.bgm-agent {
  bottom: 80px;
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
  background: #e8e8e8;
  border-radius: 1px;
}
.bgm-bars i:nth-child(1) { height: 6px; }
.bgm-bars i:nth-child(2) { height: 12px; }
.bgm-bars i:nth-child(3) { height: 8px; }
.bgm-bars i:nth-child(4) { height: 14px; }

.bgm-control.playing .bgm-bars i {
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
  .back-to-top {
    right: 56px !important;
    bottom: calc(78px + env(safe-area-inset-bottom)) !important;
    width: 34px !important;
    height: 34px !important;
  }
}

/* 回到顶部 */
.back-to-top {
  position: fixed;
  bottom: 24px;
  right: 72px;
  z-index: 9999;
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  color: #e8e8e8;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
}
.back-to-top.dark {
  background: #e8e8e8;
  border-color: rgba(0, 0, 0, 0.12);
  color: #1a1a1a;
}
.back-to-top:hover {
  background: rgba(26, 26, 26, 0.8);
  transform: translateY(-2px);
}
.back-to-top.dark:hover {
  background: rgba(232, 232, 232, 0.85);
}
.back-to-top:hover {
  background: rgba(255, 255, 255, 0.06);
  transform: translateY(-2px);
}
.back-to-top.back-top-agent {
  bottom: 80px;
}

/* 手机端回到首页按钮 */
.mobile-home-btn {
  display: none;
}
@media (max-width: 767px) {
  .mobile-home-btn {
    position: fixed;
    bottom: calc(78px + env(safe-area-inset-bottom));
    left: 12px;
    z-index: 9999;
    width: 36px;
    height: 36px;
    display: grid;
    place-items: center;
    background: rgba(26, 26, 26, 0.88);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 50%;
    color: #e8e8e8;
    cursor: pointer;
    backdrop-filter: blur(10px);
    transition: background 0.2s, transform 0.2s;
  }
  .mobile-home-btn.dark {
    background: rgba(255, 255, 255, 0.9);
    border-color: rgba(0, 0, 0, 0.1);
    color: #1a1a1a;
  }
  .mobile-home-btn:active {
    transform: scale(0.92);
  }
}

.back-top-fade-enter-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.back-top-fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.back-top-fade-enter-from,
.back-top-fade-leave-to { opacity: 0; transform: translateY(8px); }
</style>
