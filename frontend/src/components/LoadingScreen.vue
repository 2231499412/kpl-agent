<template>
  <div v-if="visible" class="loading-screen" :class="[`phase-${phase}`]" :style="{ '--loading-bg': `url(${loadingBg})` }">
    <div class="terrain-bg" />
    <div class="scan-field" />
    <div class="switch-lines" aria-hidden="true">
      <i v-for="line in 9" :key="line" :style="{ '--line-index': line }" />
    </div>

    <div class="loading-content" :class="{ hidden: phase !== 'loading' }">
      <div class="terminal-frame">
        <span class="corner corner-tl" />
        <span class="corner corner-tr" />
        <span class="corner corner-bl" />
        <span class="corner corner-br" />

        <div class="brand-block">
          <div class="brand-mark">K</div>
          <div>
            <p class="brand-kicker">KPL DATA AGENT</p>
            <h1>赛事数据中枢</h1>
          </div>
        </div>

        <div ref="locatorStage" class="locator-stage" :style="locatorStyle">
          <span class="locator-line line-x" />
          <span class="locator-line line-y" />
          <span class="locator-bracket" />
          <div
            v-for="(module, index) in modules"
            :key="module.code"
            class="locator-node"
            :class="{ active: activeModuleIndex === index }"
          >
            <small>{{ module.code }}</small>
            <strong>{{ module.title }}</strong>
            <span>{{ module.meta }}</span>
          </div>
          <div class="locator-readout">
            <span>LOCK</span>
            <strong>{{ modules[activeModuleIndex].code }}</strong>
            <small>{{ Math.floor(progress).toString().padStart(2, '0') }}%</small>
          </div>
        </div>

        <div class="status-row">
          <span>TACTICAL MAP INITIALIZING</span>
          <span>{{ Math.floor(progress) }}%</span>
        </div>

        <div class="progress-track">
          <div class="progress-fill" :style="{ width: progress + '%' }" />
          <span v-for="tick in 18" :key="tick" />
        </div>
      </div>
    </div>

    <div class="exit-panel" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import loadingBg from '../assets/loading-bg.jpg'

const visible = ref(true)
const progress = ref(0)
const phase = ref('loading')
const locatorStage = ref(null)
const locatorX = ref(0)
const locatorY = ref(0)
let resizeObserver = null

const modules = [
  { code: 'RANK', title: '数据排名', meta: 'team / player / hero' },
  { code: 'MATCH', title: '赛程情报', meta: 'schedule / battle' },
  { code: 'EQUIP', title: '装备分析', meta: 'pick / route' },
  { code: 'AGENT', title: 'AI 复盘', meta: 'review / insight' },
]

const activeModuleIndex = computed(() => {
  const step = 100 / modules.length
  return Math.min(modules.length - 1, Math.floor(progress.value / step))
})

const locatorStyle = computed(() => ({
  '--locator-x': `${locatorX.value}px`,
  '--locator-y': `${locatorY.value}px`,
}))

async function updateLocatorPosition() {
  await nextTick()
  const stage = locatorStage.value
  const node = stage?.querySelector('.locator-node.active')
  if (!stage || !node) return

  const stageRect = stage.getBoundingClientRect()
  const nodeRect = node.getBoundingClientRect()
  locatorX.value = nodeRect.left - stageRect.left + nodeRect.width / 2
  locatorY.value = nodeRect.top - stageRect.top + nodeRect.height / 2
}

watch(activeModuleIndex, updateLocatorPosition)

onMounted(() => {
  const duration = 980
  const interval = 16
  const step = 100 / (duration / interval)

  updateLocatorPosition()
  resizeObserver = new ResizeObserver(updateLocatorPosition)
  if (locatorStage.value) resizeObserver.observe(locatorStage.value)
  window.addEventListener('resize', updateLocatorPosition)

  const timer = setInterval(() => {
    progress.value = Math.min(progress.value + step, 100)
    if (progress.value >= 100) {
      clearInterval(timer)
      phase.value = 'switch'
      window.setTimeout(() => {
        phase.value = 'fade'
        window.setTimeout(() => {
          visible.value = false
        }, 520)
      }, 760)
    }
  }, interval)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', updateLocatorPosition)
})
</script>

<style scoped>
.loading-screen {
  --ink: #eef7f4;
  --soft: rgba(238, 247, 244, 0.7);
  --dim: rgba(238, 247, 244, 0.38);
  --cyan: #88f7ee;
  --gold: #d7be72;
  position: fixed;
  inset: 0;
  z-index: 100;
  overflow: hidden;
  color: var(--ink);
  background: #030809;
  transition: opacity 0.52s ease;
  isolation: isolate;
}

.loading-screen.phase-fade {
  opacity: 0;
  pointer-events: none;
}

.terrain-bg {
  position: absolute;
  inset: 0;
  z-index: -4;
  background-image:
    linear-gradient(90deg, rgba(3, 8, 9, 0.95) 0%, rgba(5, 19, 20, 0.72) 48%, rgba(3, 8, 9, 0.9) 100%),
    linear-gradient(180deg, rgba(9, 48, 48, 0.22), rgba(3, 8, 9, 0.9)),
    var(--loading-bg);
  background-size: cover;
  background-position: center;
  filter: saturate(0.92) contrast(1.08);
  transform: scale(1.03);
  animation: terrain-drift 9s ease-in-out infinite alternate;
}

.terrain-bg::after {
  content: "";
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(0deg, rgba(136, 247, 238, 0.05) 0 1px, transparent 1px 42px),
    repeating-linear-gradient(90deg, rgba(136, 247, 238, 0.045) 0 1px, transparent 1px 96px),
    linear-gradient(116deg, transparent 0 42%, rgba(136, 247, 238, 0.16) 42% 42.25%, transparent 42.25% 100%);
  mask-image: linear-gradient(90deg, #000 0 70%, transparent 96%);
}

.scan-field {
  position: absolute;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  background:
    linear-gradient(180deg, transparent 0%, rgba(136, 247, 238, 0.12) 48%, transparent 52%),
    radial-gradient(circle at 66% 42%, rgba(136, 247, 238, 0.16), transparent 24%);
  background-size: 100% 18px, auto;
  mix-blend-mode: screen;
  opacity: 0.34;
  animation: scan-roll 3.2s linear infinite;
}

.loading-content {
  position: relative;
  z-index: 2;
  display: grid;
  min-height: 100%;
  place-items: center;
  transition: opacity 0.22s ease, transform 0.34s ease;
}

.loading-content.hidden {
  opacity: 0;
  transform: translateX(-18px);
}

.terminal-frame {
  position: relative;
  width: min(780px, calc(100vw - 80px));
  min-height: 438px;
  padding: 42px;
  border: 1px solid rgba(136, 247, 238, 0.2);
  background:
    linear-gradient(135deg, rgba(7, 19, 21, 0.76), rgba(7, 18, 20, 0.5)),
    linear-gradient(90deg, rgba(136, 247, 238, 0.12), transparent 28% 72%, rgba(215, 190, 114, 0.1));
  backdrop-filter: blur(18px);
  box-shadow:
    inset 0 0 48px rgba(136, 247, 238, 0.07),
    0 28px 90px rgba(0, 0, 0, 0.42);
  animation: frame-enter 0.72s cubic-bezier(0.2, 0.9, 0.15, 1) both;
}

.terminal-frame::before,
.terminal-frame::after {
  content: "";
  position: absolute;
  left: -56px;
  right: -56px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(136, 247, 238, 0.65), transparent);
}

.terminal-frame::before {
  top: 22px;
}

.terminal-frame::after {
  bottom: 22px;
}

.corner {
  position: absolute;
  width: 42px;
  height: 42px;
  border-color: var(--cyan);
  opacity: 0.86;
}

.corner-tl {
  top: -1px;
  left: -1px;
  border-top: 2px solid;
  border-left: 2px solid;
}

.corner-tr {
  top: -1px;
  right: -1px;
  border-top: 2px solid;
  border-right: 2px solid;
}

.corner-bl {
  bottom: -1px;
  left: -1px;
  border-bottom: 2px solid;
  border-left: 2px solid;
}

.corner-br {
  right: -1px;
  bottom: -1px;
  border-right: 2px solid;
  border-bottom: 2px solid;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid rgba(136, 247, 238, 0.36);
  color: var(--cyan);
  background: rgba(3, 12, 14, 0.78);
  font-size: 28px;
  font-weight: 900;
  box-shadow: inset 0 0 20px rgba(136, 247, 238, 0.18);
}

.brand-kicker {
  margin: 0 0 6px;
  color: var(--gold);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 3px;
}

h1 {
  margin: 0;
  color: var(--ink);
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0;
}

.locator-stage {
  position: relative;
  min-height: 208px;
  margin: 34px 0 26px;
  padding: 18px 148px 18px 18px;
  border: 1px solid rgba(136, 247, 238, 0.16);
  background:
    linear-gradient(90deg, rgba(136, 247, 238, 0.06) 0 1px, transparent 1px 100%),
    linear-gradient(0deg, rgba(136, 247, 238, 0.05) 0 1px, transparent 1px 100%),
    rgba(2, 8, 10, 0.48);
  background-size: 46px 46px;
  overflow: hidden;
}

.locator-stage::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 0 58%, rgba(136, 247, 238, 0.1) 58% 58.35%, transparent 58.35% 100%);
  pointer-events: none;
}

.locator-line {
  position: absolute;
  z-index: 2;
  pointer-events: none;
  background: rgba(136, 247, 238, 0.72);
  box-shadow: 0 0 18px rgba(136, 247, 238, 0.32);
  transition: transform 0.34s cubic-bezier(0.78, 0, 0.16, 1);
}

.line-x {
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  transform: translateY(var(--locator-y));
}

.line-y {
  top: 0;
  bottom: 0;
  left: 0;
  width: 1px;
  transform: translateX(var(--locator-x));
}

.locator-bracket {
  position: absolute;
  z-index: 3;
  left: 0;
  top: 0;
  width: 112px;
  height: 62px;
  border: 1px solid rgba(136, 247, 238, 0.76);
  transform: translate(calc(var(--locator-x) - 56px), calc(var(--locator-y) - 31px));
  transition: transform 0.34s cubic-bezier(0.78, 0, 0.16, 1);
  box-shadow: inset 0 0 24px rgba(136, 247, 238, 0.08), 0 0 18px rgba(136, 247, 238, 0.2);
}

.locator-bracket::before,
.locator-bracket::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 24px;
  height: 1px;
  background: var(--gold);
}

.locator-bracket::before {
  left: -32px;
}

.locator-bracket::after {
  right: -32px;
}

.locator-node {
  position: absolute;
  z-index: 1;
  width: 232px;
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid rgba(136, 247, 238, 0.14);
  background: rgba(6, 20, 22, 0.62);
  transition: border-color 0.24s ease, background 0.24s ease, color 0.24s ease;
}

.locator-node:nth-of-type(1) {
  left: 18px;
  top: 18px;
}

.locator-node:nth-of-type(2) {
  right: 166px;
  top: 34px;
}

.locator-node:nth-of-type(3) {
  left: 72px;
  bottom: 22px;
}

.locator-node:nth-of-type(4) {
  right: 196px;
  bottom: 12px;
}

.locator-node.active {
  border-color: rgba(136, 247, 238, 0.62);
  background: rgba(19, 55, 55, 0.74);
}

.locator-node small,
.locator-node strong,
.locator-node span {
  display: block;
}

.locator-node small {
  color: var(--gold);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 2px;
}

.locator-node strong {
  margin-top: 8px;
  color: var(--ink);
  font-size: 16px;
  font-weight: 900;
}

.locator-node span {
  margin-top: 4px;
  color: var(--dim);
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 11px;
}

.locator-readout {
  position: absolute;
  z-index: 4;
  top: 18px;
  right: 18px;
  bottom: 18px;
  width: 112px;
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 16px;
  border-left: 1px solid rgba(136, 247, 238, 0.18);
  background: linear-gradient(90deg, rgba(2, 8, 10, 0.12), rgba(2, 8, 10, 0.72));
}

.locator-readout span,
.locator-readout small {
  color: var(--dim);
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 11px;
}

.locator-readout strong {
  color: var(--cyan);
  font-size: 24px;
  font-weight: 900;
}

.status-row {
  display: flex;
  justify-content: space-between;
  color: var(--soft);
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 12px;
  letter-spacing: 1px;
}

.progress-track {
  position: relative;
  height: 3px;
  margin-top: 14px;
  border: 0;
  background: rgba(136, 247, 238, 0.13);
  box-shadow:
    0 -8px 0 rgba(136, 247, 238, 0.035),
    0 8px 0 rgba(136, 247, 238, 0.035);
  overflow: visible;
}

.progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: linear-gradient(90deg, var(--cyan), var(--gold));
  box-shadow: 0 0 16px rgba(136, 247, 238, 0.46);
  transition: width 0.016s linear;
}

.progress-fill::after {
  content: "";
  position: absolute;
  top: -4px;
  right: -1px;
  width: 1px;
  height: 11px;
  background: var(--gold);
  box-shadow: 0 0 14px rgba(215, 190, 114, 0.65);
}

.progress-track span {
  position: relative;
  z-index: 1;
  display: inline-block;
  width: calc(100% / 18);
  height: 11px;
  top: -4px;
  border-right: 1px solid rgba(136, 247, 238, 0.18);
}

.switch-lines {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
  opacity: 0;
}

.switch-lines i {
  position: absolute;
  left: -14vw;
  right: -14vw;
  top: calc(var(--line-index) * 10.5vh);
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(136, 247, 238, 0.95), rgba(215, 190, 114, 0.76), transparent);
  transform: translateX(-110%);
}

.phase-switch .switch-lines {
  opacity: 1;
}

.phase-switch .switch-lines i {
  animation: line-cut 0.72s cubic-bezier(0.72, 0, 0.18, 1) forwards;
  animation-delay: calc((var(--line-index) - 1) * 34ms);
}

.exit-panel {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background:
    linear-gradient(90deg, transparent 0 24%, rgba(136, 247, 238, 0.08) 24% 24.3%, transparent 24.3% 100%),
    linear-gradient(105deg, rgba(3, 8, 9, 0.96), rgba(6, 24, 25, 0.92));
  clip-path: polygon(0 0, 0 0, 0 100%, 0 100%);
}

.phase-switch .exit-panel {
  animation: panel-cut 0.76s cubic-bezier(0.78, 0, 0.16, 1) forwards;
}

@keyframes terrain-drift {
  from { transform: scale(1.03) translate3d(-8px, 0, 0); }
  to { transform: scale(1.06) translate3d(10px, -8px, 0); }
}

@keyframes scan-roll {
  from { background-position: 0 -18px, center; }
  to { background-position: 0 18px, center; }
}

@keyframes frame-enter {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes line-cut {
  0% { transform: translateX(-110%); opacity: 0; }
  16% { opacity: 1; }
  100% { transform: translateX(110%); opacity: 0.2; }
}

@keyframes panel-cut {
  0% { clip-path: polygon(0 0, 0 0, 0 100%, 0 100%); }
  58% { clip-path: polygon(0 0, 118% 0, 104% 100%, 0 100%); }
  100% { clip-path: polygon(100% 0, 118% 0, 104% 100%, 100% 100%); }
}

@media (max-width: 760px) {
  .terminal-frame {
    width: calc(100vw - 32px);
    min-height: 430px;
    padding: 28px;
  }

  h1 {
    font-size: 26px;
  }

  .locator-stage {
    grid-template-columns: 1fr;
    min-height: 360px;
    padding-right: 18px;
  }

  .locator-readout {
    display: none;
  }
}
</style>
