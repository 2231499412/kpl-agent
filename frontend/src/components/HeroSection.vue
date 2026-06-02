<template>
  <section class="hero-section">
    <div class="hero-bg" />
    <div class="hero-content">
      <div class="hero-brand animate-float">
        <span class="hero-mark">K</span>
      </div>
      <h1 class="hero-title">
        <span class="gradient-text">KPL 赛事工具站</span>
      </h1>
      <p class="hero-subtitle">
        <span class="typing-text">{{ displayedText }}</span>
        <span class="typing-cursor">|</span>
      </p>
      <div class="hero-tags">
        <span class="hero-tag">数据排行</span>
        <span class="hero-tag">赛程查询</span>
        <span class="hero-tag">AI 复盘</span>
        <span class="hero-tag">装备分析</span>
      </div>
    </div>
    <div class="hero-scroll-hint animate-bounce-down">
      <el-icon :size="24"><ArrowDownBold /></el-icon>
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowDownBold } from '@element-plus/icons-vue'

const fullText = '数据驱动，洞察赛场每一个细节'
const displayedText = ref('')
let charIndex = 0
let typeTimer = null

function typeNext() {
  if (charIndex < fullText.length) {
    displayedText.value += fullText[charIndex]
    charIndex++
    typeTimer = setTimeout(typeNext, 80 + Math.random() * 40)
  } else {
    typeTimer = setTimeout(() => {
      displayedText.value = ''
      charIndex = 0
      typeNext()
    }, 4000)
  }
}

onMounted(() => {
  typeTimer = setTimeout(typeNext, 800)
})

onUnmounted(() => {
  clearTimeout(typeTimer)
})
</script>

<style scoped>
.hero-section {
  position: relative;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 70% 50% at 25% 25%, rgba(255, 68, 68, 0.15) 0%, transparent 70%),
    radial-gradient(ellipse 50% 60% at 75% 20%, rgba(100, 60, 255, 0.1) 0%, transparent 70%),
    radial-gradient(ellipse 60% 40% at 50% 85%, rgba(255, 136, 0, 0.08) 0%, transparent 70%),
    radial-gradient(circle at 50% 50%, rgba(255, 68, 68, 0.03) 0%, transparent 50%);
  z-index: 0;
}

.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 0 24px;
}

.hero-brand {
  margin-bottom: 32px;
}

.hero-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  font-size: 48px;
  font-weight: 900;
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-secondary));
  border-radius: 20px;
  box-shadow: 0 0 40px var(--accent-glow), 0 8px 32px rgba(0, 0, 0, 0.4);
}

.hero-title {
  margin: 0 0 16px;
  font-size: clamp(40px, 7vw, 80px);
  font-weight: 900;
  letter-spacing: -2px;
  line-height: 1.1;
}

.hero-subtitle {
  margin: 0 0 32px;
  font-size: clamp(16px, 2.5vw, 22px);
  color: var(--text-secondary);
  min-height: 1.5em;
}

.typing-cursor {
  color: var(--accent);
  animation: typing-cursor 0.8s step-end infinite;
  margin-left: 2px;
}

.hero-tags {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.hero-tag {
  padding: 8px 20px;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  backdrop-filter: blur(8px);
  transition: all 0.3s ease;
}

.hero-tag:hover {
  color: var(--accent);
  border-color: rgba(255, 68, 68, 0.3);
  background: var(--accent-muted);
}

.hero-scroll-hint {
  position: absolute;
  bottom: 40px;
  color: var(--muted);
  z-index: 1;
}
</style>
