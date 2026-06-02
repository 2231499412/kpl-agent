<template>
  <div v-if="visible" class="loading-screen" :class="{ 'fade-out': phase === 'fade' }">
    <!-- 黄色全屏展开遮罩 -->
    <div class="yellow-wipe" :class="{ active: phase === 'wipe' }"></div>

    <!-- 主内容 -->
    <div class="loading-content" :class="{ hidden: phase === 'wipe' || phase === 'fade' }">
      <!-- 左侧进度条，紧贴屏幕左边缘，深色底 -->
      <div class="progress-track">
        <div class="progress-fill" :style="{ height: progress + '%' }"></div>
      </div>

      <!-- 左下角进度数字 -->
      <div class="progress-number">{{ Math.floor(progress) }}%</div>

      <!-- 右下角 Logo 区域 -->
      <div class="logo-area">
        <div class="logo-mark">K</div>
        <div class="logo-text">
          <p class="logo-sub">KPL DATA AGENT</p>
          <p class="logo-main">赛场数据中控</p>
        </div>
        <div class="logo-tagline">
          <span>OVER THE FRONTIER</span>
          <span class="tagline-divider">/</span>
          <span>INTO THE FRONT</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const visible = ref(true)
const progress = ref(0)
const phase = ref('loading')

onMounted(() => {
  const duration = 600
  const interval = 16
  const step = 100 / (duration / interval)

  const timer = setInterval(() => {
    progress.value = Math.min(progress.value + step, 100)
    if (progress.value >= 100) {
      clearInterval(timer)
      phase.value = 'wipe'
      setTimeout(() => {
        phase.value = 'fade'
        setTimeout(() => {
          visible.value = false
          phase.value = 'done'
        }, 500)
      }, 600)
    }
  }, interval)
})
</script>

<style scoped>
.loading-screen {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: #111111;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  transition: opacity 0.5s ease;
}

.loading-screen.fade-out {
  opacity: 0;
  pointer-events: none;
}

/* 黄色从左往右展开 */
.yellow-wipe {
  position: absolute;
  inset: 0;
  background: #FFFA00;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.6s cubic-bezier(0.65, 0, 0.35, 1);
  z-index: 2;
}
.yellow-wipe.active {
  transform: scaleX(1);
}

/* 主内容 */
.loading-content {
  position: relative;
  width: 100%;
  height: 100%;
  transition: opacity 0.3s;
}
.loading-content.hidden {
  opacity: 0;
}

/* 左侧进度条，紧贴左边缘，深色底，细条 */
.progress-track {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 15px;
  background: #222222;
  overflow: hidden;
}
.progress-fill {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  background: #FFFA00;
  transition: height 0.016s linear;
}

/* 左下角进度数字，紧挨进度条右侧 */
.progress-number {
  position: absolute;
  left: 32px;
  bottom: 40px;
  font-size: 80px;
  font-weight: 800;
  color: #ffffff;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -3px;
  font-family: 'Arial Black', 'Helvetica Neue', sans-serif;
}

/* 右下角 Logo 区域 */
.logo-area {
  position: absolute;
  right: 60px;
  bottom: 60px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 16px;
}

.logo-mark {
  width: 48px;
  height: 48px;
  background: #FFFA00;
  color: #111111;
  font-size: 28px;
  font-weight: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.logo-text {
  text-align: right;
}
.logo-sub {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 4px;
  text-transform: uppercase;
  margin: 0 0 4px;
}
.logo-main {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
}

.logo-tagline {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  letter-spacing: 2px;
  text-transform: uppercase;
}
.tagline-divider {
  color: #FFFA00;
}
</style>
