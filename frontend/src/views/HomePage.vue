<template>
  <main
    class="home-stage"
    :style="{ '--home-bg-image': `url(${homeBg})` }"
    tabindex="0"
    @wheel.prevent="handleWheel"
    @keydown="handleKeydown"
    @touchstart="handleTouchStart"
    @touchend="handleTouchEnd"
  >
    <div class="home-backdrop" />
    <div class="home-noise" />
    <div class="content-edge-lines" :style="contentEdgeStyle" aria-hidden="true">
      <span class="content-edge-y" />
      <span class="content-edge-x content-edge-bottom" />
      <span class="content-edge-x content-edge-top" />
      <span class="content-edge-corner" />
      <span :key="contentEdgeIndex" class="content-edge-index">{{ contentEdgeIndex }}</span>
    </div>

    <header class="home-topbar">
      <router-link to="/" class="home-brand" aria-label="KPL 数据中枢">
        <span class="brand-sigil">K</span>
        <span>KPL DATA AGENT</span>
      </router-link>
      <nav class="home-nav" aria-label="主页功能导航">
        <button
          v-for="(panel, index) in panels"
          :key="panel.key"
          :class="{ active: activeIndex === index }"
          type="button"
          @click="goToPanel(index)"
        >
          {{ panel.nav }}
        </button>
      </nav>
    </header>

    <section class="panel-rail" :style="{ transform: `translate3d(${-activeIndex * viewportWidth}px, 0, 0)` }">
      <article v-for="panel in panels" :key="panel.key" class="home-panel">
        <div class="panel-inner">
          <p class="panel-kicker">{{ panel.kicker }}</p>
          <h1 v-if="panel.key === 'hero'" class="panel-title hero-title">{{ panel.title }}</h1>
          <h2 v-else class="panel-title">{{ panel.title }}</h2>
          <p class="panel-copy">{{ panel.copy }}</p>

          <div v-if="panel.key === 'hero'" class="hero-actions">
            <router-link class="primary-link" to="/rankings">进入数据排名</router-link>
            <router-link class="ghost-link" to="/agent">AI 复盘</router-link>
          </div>

          <div v-if="panel.key === 'overview'" class="metric-grid">
            <div v-for="stat in stats" :key="stat.label" class="metric-card">
              <strong>{{ stat.value }}</strong>
              <span>{{ stat.label }}</span>
            </div>
          </div>

          <div v-if="panel.key === 'tools'" class="tool-grid">
            <router-link v-for="tool in tools" :key="tool.route" class="tool-card" :to="tool.route">
              <component :is="tool.icon" class="tool-icon" />
              <span>{{ tool.title }}</span>
              <small>{{ tool.desc }}</small>
            </router-link>
          </div>

          <div v-if="panel.key === 'matches'" class="match-board">
            <div v-for="match in recentMatches" :key="match.id" class="match-row">
              <span>{{ match.stage }}</span>
              <strong>{{ match.teams }}</strong>
              <small>{{ match.time }}</small>
            </div>
          </div>

          <div v-if="panel.key === 'agent'" class="agent-console">
            <div class="console-line">读取近期赛程、阵容和英雄热度...</div>
            <div class="console-line accent">生成 BP 风险、节奏点和复盘摘要</div>
            <router-link class="primary-link compact" to="/agent">开始分析</router-link>
          </div>
        </div>
      </article>
    </section>

    <aside class="slide-meter" aria-label="章节进度">
      <button
        v-for="(panel, index) in panels"
        :key="panel.key"
        :aria-label="`切换到${panel.nav}`"
        :class="{ active: activeIndex === index }"
        type="button"
        @click="goToPanel(index)"
      />
    </aside>

    <div class="scroll-cue">
      <span>SCROLL</span>
      <i />
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Box, Calendar, ChatDotRound, DataAnalysis, Trophy } from '@element-plus/icons-vue'
import homeBg from '../assets/home-bg.jpg'

const activeIndex = ref(0)
const locked = ref(false)
const touchStartX = ref(0)
const touchStartY = ref(0)
const viewportWidth = ref(1120)
const edgeSettleTimer = ref(null)
const contentEdgeX = ref(0)
const contentEdgeY = ref(0)
const contentEdgeTopY = ref(0)
const contentIndexOffsetX = ref(14)
const contentIndexOffsetY = ref(12)
const teamCount = ref(0)
const matchCount = ref(0)
const heroCount = ref(0)
const recentMatches = ref([
  { id: 'placeholder-1', stage: '数据待同步', teams: 'KPL 赛事数据', time: '等待接口返回' },
  { id: 'placeholder-2', stage: '分析模块', teams: '阵容 / 英雄 / 装备', time: '可进入功能页查看' },
])

const panels = [
  {
    key: 'hero',
    nav: '首页',
    kicker: 'TACTICAL DATA TERMINAL',
    title: 'KPL 数据中枢',
    copy: '把赛程、阵容、英雄、装备和 AI 复盘压进同一块战术面板。滚动一次，切换一个功能。',
  },
  {
    key: 'overview',
    nav: '概览',
    kicker: 'LIVE INDEX',
    title: '数据概览',
    copy: '快速扫过赛事规模和当前数据沉淀，不需要在长页面里慢慢找入口。',
  },
  {
    key: 'tools',
    nav: '工具',
    kicker: 'MODULE SELECT',
    title: '赛事工具',
    copy: '每个功能都是一块独立屏幕，滚轮像切换作战模块一样向右推进。',
  },
  {
    key: 'matches',
    nav: '赛程',
    kicker: 'MATCH FEED',
    title: '近期赛事',
    copy: '赛程、比分和阶段信息保持横向浏览节奏，适合赛前快速定位。',
  },
  {
    key: 'agent',
    nav: 'AI',
    kicker: 'REVIEW AGENT',
    title: 'AI 复盘',
    copy: '从比赛数据里抽取节奏节点、英雄价值和装备倾向，输出可读的复盘线索。',
  },
]

const tools = [
  { title: '数据排名', desc: '战队 / 选手 / 英雄', route: '/rankings', icon: Trophy },
  { title: '赛程查询', desc: '赛程与对局详情', route: '/matches', icon: Calendar },
  { title: '装备分析', desc: '出场率与分路偏好', route: '/equipment', icon: Box },
  { title: 'AI 复盘', desc: '智能生成比赛分析', route: '/agent', icon: ChatDotRound },
  { title: 'BP 分析', desc: 'Ban/Pick 策略辅助', route: '/bp-analysis', icon: DataAnalysis },
]

const stats = computed(() => [
  { label: '战队样本', value: teamCount.value || '--' },
  { label: '比赛记录', value: matchCount.value || '--' },
  { label: '英雄池', value: heroCount.value || '--' },
  { label: '功能模块', value: tools.length },
])

const contentEdgeStyle = computed(() => ({
  '--content-edge-x': `${contentEdgeX.value}px`,
  '--content-edge-y': `${contentEdgeY.value}px`,
  '--content-edge-top-y': `${contentEdgeTopY.value}px`,
  '--content-edge-active-y': `${activeIndex.value === 1 ? contentEdgeTopY.value : contentEdgeY.value}px`,
  '--content-edge-bottom-y': `${activeIndex.value === 1 ? contentEdgeY.value + 140 : contentEdgeY.value}px`,
  '--content-edge-top-line-y': `${activeIndex.value === 1 ? contentEdgeTopY.value : contentEdgeTopY.value - 140}px`,
  '--content-edge-bottom-opacity': activeIndex.value === 1 ? 0 : 1,
  '--content-edge-top-opacity': activeIndex.value === 1 ? 1 : 0,
  '--content-index-offset-x': `${contentIndexOffsetX.value}px`,
  '--content-index-offset-y': `${contentIndexOffsetY.value}px`,
}))

const contentEdgeIndex = computed(() => String(activeIndex.value + 1).padStart(2, '0'))

async function updateContentEdges() {
  await nextTick()
  const panel = document.querySelectorAll('.home-panel')[activeIndex.value]
  const inner = panel?.querySelector('.panel-inner')
  if (!inner) return

  const kicker = inner.querySelector('.panel-kicker')
  const title = inner.querySelector('.panel-title')
  const kickerRect = kicker?.getBoundingClientRect()

  contentEdgeX.value = Math.round(inner.offsetLeft)
  contentEdgeY.value = Math.round(inner.offsetTop + inner.offsetHeight)
  contentEdgeTopY.value = activeIndex.value === 1 && kickerRect
    ? Math.round(kickerRect.top)
    : Math.round(inner.offsetTop + (kicker?.offsetTop ?? title?.offsetTop ?? 0))

  const offsets = [
    [18, 14],
    [18, -58],
    [18, 14],
    [-78, 14],
    [-78, -58],
  ]
  const [offsetX, offsetY] = offsets[activeIndex.value] || offsets[2]
  contentIndexOffsetX.value = offsetX
  contentIndexOffsetY.value = offsetY
}

watch(activeIndex, updateContentEdges)

function goToPanel(index) {
  activeIndex.value = Math.max(0, Math.min(index, panels.length - 1))
  clearTimeout(edgeSettleTimer.value)
  edgeSettleTimer.value = window.setTimeout(updateContentEdges, 820)
}

function stepPanel(direction) {
  if (locked.value) return
  const next = activeIndex.value + direction
  if (next < 0 || next >= panels.length) return
  locked.value = true
  goToPanel(next)
  window.setTimeout(() => {
    locked.value = false
  }, 780)
}

function handleWheel(event) {
  event?.preventDefault()
  const direction = event.deltaY > 0 || event.deltaX > 0 ? 1 : -1
  stepPanel(direction)
}

function handleKeydown(event) {
  if (['ArrowDown', 'ArrowRight', 'PageDown', ' '].includes(event.key)) {
    event.preventDefault()
    stepPanel(1)
  }
  if (['ArrowUp', 'ArrowLeft', 'PageUp'].includes(event.key)) {
    event.preventDefault()
    stepPanel(-1)
  }
}

function handleTouchStart(event) {
  const touch = event.changedTouches[0]
  touchStartX.value = touch.clientX
  touchStartY.value = touch.clientY
}

function handleTouchEnd(event) {
  const touch = event.changedTouches[0]
  const diffX = touchStartX.value - touch.clientX
  const diffY = touchStartY.value - touch.clientY
  const direction = Math.abs(diffX) > Math.abs(diffY) ? diffX : diffY
  if (Math.abs(direction) > 42) stepPanel(direction > 0 ? 1 : -1)
}

function formatTime(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(5, 16)
}

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
  updateContentEdges()
}

onMounted(async () => {
  document.body.classList.add('home-lock')
  syncViewportWidth()
  updateContentEdges()
  window.addEventListener('wheel', handleWheel, { passive: false })
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', syncViewportWidth)
  try {
    const [teamRes, heroRes, matchRes] = await Promise.allSettled([
      fetch('/api/query/team/ranking').then((r) => r.json()),
      fetch('/api/query/hero/top?sort=pick').then((r) => r.json()),
      fetch('/api/query/match/schedule').then((r) => r.json()),
    ])

    const teams = teamRes.status === 'fulfilled' ? teamRes.value?.data : []
    const heroes = heroRes.status === 'fulfilled' ? heroRes.value?.data : []
    const matches = matchRes.status === 'fulfilled' ? matchRes.value?.data : []

    if (Array.isArray(teams)) {
      teamCount.value = teams.length
      matchCount.value = teams.reduce((sum, team) => sum + (team.battleCount || 0), 0)
    }
    if (Array.isArray(heroes)) heroCount.value = heroes.length
    if (Array.isArray(matches) && matches.length) {
      recentMatches.value = matches
        .sort((a, b) => (b.startTime || '').localeCompare(a.startTime || ''))
        .slice(0, 5)
        .map((match, index) => ({
          id: match.matchId || index,
          stage: match.matchStageDesc || 'KPL',
          teams: `${match.camp1TeamName || '蓝方'} ${match.camp1Score ?? '-'} : ${match.camp2Score ?? '-'} ${match.camp2TeamName || '红方'}`,
          time: formatTime(match.startTime),
        }))
    }
    updateContentEdges()
  } catch {
    // Keep the designed fallback state if local API is unavailable.
  }
})

onBeforeUnmount(() => {
  document.body.classList.remove('home-lock')
  clearTimeout(edgeSettleTimer.value)
  window.removeEventListener('wheel', handleWheel)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<style scoped>
.home-stage {
  --ink: #eef7f4;
  --soft: rgba(238, 247, 244, 0.68);
  --dim: rgba(238, 247, 244, 0.42);
  --cyan: #88f7ee;
  --mint: #39d8c2;
  --gold: #d7be72;
  --panel: rgba(8, 18, 20, 0.62);
  --panel-line: rgba(155, 245, 229, 0.18);
  position: fixed;
  inset: 0;
  min-width: 1120px;
  overflow: hidden;
  color: var(--ink);
  background: #05090a;
  outline: none;
  isolation: isolate;
}

:global(body.home-lock) {
  overflow: hidden;
}

.home-backdrop {
  position: absolute;
  inset: 0;
  z-index: -3;
  background-image:
    linear-gradient(90deg, rgba(2, 5, 6, 0.92) 0%, rgba(2, 5, 6, 0.58) 42%, rgba(2, 5, 6, 0.74) 100%),
    linear-gradient(180deg, rgba(7, 32, 34, 0.1), rgba(3, 7, 8, 0.88)),
    var(--home-bg-image);
  background-size: cover;
  background-position: center;
  transform: scale(1.02);
}

.home-backdrop::after {
  content: "";
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(90deg, rgba(136, 247, 238, 0.05) 0 1px, transparent 1px 92px),
    linear-gradient(115deg, transparent 0 54%, rgba(136, 247, 238, 0.12) 54% 54.3%, transparent 54.3% 100%);
  mask-image: linear-gradient(90deg, #000, transparent 78%);
}

.home-noise {
  position: absolute;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  background-image: radial-gradient(rgba(255, 255, 255, 0.12) 0.7px, transparent 0.7px);
  background-size: 5px 5px;
  opacity: 0.11;
  mix-blend-mode: screen;
}

.content-edge-lines {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

.content-edge-y,
.content-edge-x,
.content-edge-corner,
.content-edge-index {
  position: absolute;
  display: block;
  transition:
    transform 0.78s cubic-bezier(0.78, 0, 0.16, 1),
    opacity 0.48s ease;
}

.content-edge-y {
  top: 0;
  bottom: 0;
  left: 0;
  width: 1px;
  background: rgba(238, 247, 244, 0.28);
  transform: translateX(var(--content-edge-x));
  box-shadow: 0 0 12px rgba(238, 247, 244, 0.08);
}

.content-edge-x {
  left: 0;
  right: 0;
  top: 0;
  height: 1px;
  background: rgba(238, 247, 244, 0.28);
  box-shadow: 0 0 12px rgba(238, 247, 244, 0.08);
}

.content-edge-bottom {
  opacity: var(--content-edge-bottom-opacity);
  transform: translateY(var(--content-edge-bottom-y));
}

.content-edge-top {
  opacity: var(--content-edge-top-opacity);
  transform: translateY(var(--content-edge-top-line-y));
}

.content-edge-corner {
  left: 0;
  top: 0;
  width: 18px;
  height: 18px;
  background: transparent;
  border-left: 1px solid rgba(215, 190, 114, 0.55);
  border-bottom: 1px solid rgba(215, 190, 114, 0.55);
  transform: translate(
    calc(var(--content-edge-x) - 1px),
    calc(var(--content-edge-active-y) - 17px)
  );
}

.content-edge-index {
  left: 0;
  top: 0;
  min-width: 58px;
  padding: 0;
  color: rgba(238, 247, 244, 0.52);
  background: transparent;
  border: 0;
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 38px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 1px;
  text-shadow: 0 0 18px rgba(238, 247, 244, 0.11);
  animation: edge-index-fade 1.15s ease both;
  transform: translate(
    calc(var(--content-edge-x) + var(--content-index-offset-x)),
    calc(var(--content-edge-active-y) + var(--content-index-offset-y))
  );
}

@keyframes edge-index-fade {
  from {
    opacity: 0;
    filter: blur(8px);
  }
  45% {
    opacity: 0;
  }
  to {
    opacity: 1;
    filter: blur(0);
  }
}

.home-topbar {
  position: absolute;
  top: 28px;
  left: 42px;
  right: 42px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.home-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--ink);
  text-decoration: none;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 2px;
}

.brand-sigil {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid rgba(136, 247, 238, 0.32);
  background: rgba(3, 12, 14, 0.72);
  color: var(--cyan);
  font-size: 21px;
  box-shadow: inset 0 0 18px rgba(136, 247, 238, 0.14);
}

.home-nav {
  position: relative;
  display: flex;
  gap: 6px;
  padding: 6px;
  border: 1px solid rgba(136, 247, 238, 0.14);
  background: rgba(2, 8, 10, 0.58);
  backdrop-filter: blur(18px);
  overflow: visible;
}

.home-nav button {
  position: relative;
  z-index: 2;
  min-width: 74px;
  height: 34px;
  border: 0;
  color: var(--dim);
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.home-nav button.active,
.home-nav button:hover {
  color: #031010;
  background: linear-gradient(135deg, var(--cyan), var(--gold));
}

.panel-rail {
  display: flex;
  width: max-content;
  height: 100vh;
  transition: transform 0.78s cubic-bezier(0.78, 0, 0.16, 1);
  will-change: transform;
}

.home-panel {
  position: relative;
  width: 100vw;
  height: 100vh;
  flex: 0 0 100vw;
  display: grid;
  align-items: center;
  padding: 120px 9vw 90px;
}

.home-panel::after {
  content: "";
  position: absolute;
  right: 8vw;
  bottom: 12vh;
  width: 32vw;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(136, 247, 238, 0.8), transparent);
}

.panel-inner {
  width: min(720px, 52vw);
}

.panel-kicker {
  margin: 0 0 18px;
  color: var(--gold);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 4px;
}

.panel-title {
  margin: 0;
  color: var(--ink);
  font-size: 64px;
  font-weight: 900;
  line-height: 0.95;
  letter-spacing: 0;
  text-shadow: 0 0 34px rgba(136, 247, 238, 0.13);
}

.hero-title {
  font-size: 92px;
}

.panel-copy {
  max-width: 620px;
  margin: 28px 0 0;
  color: var(--soft);
  font-size: 18px;
  line-height: 1.85;
}

.hero-actions,
.agent-console {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 38px;
}

.primary-link,
.ghost-link {
  display: inline-flex;
  min-width: 154px;
  height: 46px;
  align-items: center;
  justify-content: center;
  padding: 0 22px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 800;
}

.primary-link {
  color: #061313;
  background: linear-gradient(135deg, var(--cyan), var(--gold));
  box-shadow: 0 0 26px rgba(57, 216, 194, 0.22);
}

.primary-link.compact {
  min-width: 128px;
  height: 38px;
}

.ghost-link {
  color: var(--cyan);
  border: 1px solid rgba(136, 247, 238, 0.28);
  background: rgba(2, 10, 12, 0.48);
}

.metric-grid,
.tool-grid,
.match-board {
  display: grid;
  gap: 12px;
  margin-top: 36px;
}

.metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-card,
.tool-card,
.match-row,
.agent-console {
  border: 1px solid var(--panel-line);
  background: var(--panel);
  backdrop-filter: blur(18px);
}

.metric-card {
  min-height: 116px;
  padding: 22px;
}

.metric-card strong {
  display: block;
  color: var(--cyan);
  font-size: 36px;
  line-height: 1;
}

.metric-card span {
  display: block;
  margin-top: 14px;
  color: var(--dim);
  font-size: 13px;
}

.tool-grid {
  grid-template-columns: repeat(5, 128px);
}

.tool-card {
  min-height: 148px;
  padding: 18px 14px;
  color: var(--ink);
  text-decoration: none;
  transition: transform 0.22s ease, border-color 0.22s ease, background 0.22s ease;
}

.tool-card:hover {
  transform: translateY(-6px);
  border-color: rgba(136, 247, 238, 0.52);
  background: rgba(18, 46, 48, 0.72);
}

.tool-icon {
  width: 28px;
  height: 28px;
  color: var(--cyan);
}

.tool-card span,
.tool-card small {
  display: block;
}

.tool-card span {
  margin-top: 28px;
  font-size: 15px;
  font-weight: 800;
}

.tool-card small {
  margin-top: 8px;
  color: var(--dim);
  font-size: 12px;
  line-height: 1.5;
}

.match-board {
  width: min(660px, 48vw);
}

.match-row {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr) 92px;
  gap: 18px;
  align-items: center;
  min-height: 62px;
  padding: 0 18px;
}

.match-row span {
  color: var(--gold);
  font-size: 12px;
}

.match-row strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.match-row small {
  color: var(--dim);
  text-align: right;
}

.agent-console {
  width: min(620px, 46vw);
  flex-direction: column;
  align-items: flex-start;
  padding: 24px;
}

.console-line {
  width: 100%;
  color: var(--soft);
  font-family: "Cascadia Mono", "Consolas", monospace;
  font-size: 13px;
}

.console-line::before {
  content: "> ";
  color: var(--gold);
}

.console-line.accent {
  color: var(--cyan);
}

.slide-meter {
  position: absolute;
  right: 42px;
  top: 50%;
  z-index: 6;
  display: grid;
  gap: 14px;
  transform: translateY(-50%);
}

.slide-meter button {
  width: 36px;
  height: 3px;
  padding: 0;
  border: 0;
  background: rgba(238, 247, 244, 0.24);
  cursor: pointer;
  transition: width 0.22s ease, background 0.22s ease;
}

.slide-meter button.active {
  width: 54px;
  background: var(--cyan);
}

.scroll-cue {
  position: absolute;
  left: 42px;
  bottom: 34px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--dim);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 3px;
}

.scroll-cue i {
  display: block;
  width: 82px;
  height: 1px;
  background: linear-gradient(90deg, var(--cyan), transparent);
}

@media (max-width: 1180px) {
  .home-stage {
    min-width: 0;
  }

  .home-nav {
    display: none;
  }

  .home-panel {
    padding-inline: 32px;
  }

  .panel-inner {
    width: min(680px, 78vw);
  }

  .hero-title,
  .panel-title {
    font-size: 56px;
  }

  .metric-grid,
  .tool-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .match-board,
  .agent-console {
    width: min(620px, 78vw);
  }
}
</style>
