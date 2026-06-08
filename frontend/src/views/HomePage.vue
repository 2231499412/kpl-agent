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
        <span class="brand-text">KPL<span class="brand-dot">·</span>DATA</span>
      </router-link>
      <nav class="home-nav" aria-label="主页功能导航">
        <a
          v-for="(panel, index) in panels"
          :key="panel.key"
          :class="{ active: activeIndex === index }"
          @click.prevent="goToPanel(index)"
          href="#"
        >
          {{ panel.nav }}
        </a>
      </nav>
    </header>

    <section class="panel-rail" :style="{ transform: `translate3d(${-activeIndex * viewportWidth}px, 0, 0)` }">
      <article v-for="(panel, index) in panels" :key="panel.key" class="home-panel" :class="{ active: activeIndex === index }">
        <div class="panel-inner">
          <p class="panel-kicker">{{ panel.kicker }}</p>
          <h1 v-if="panel.key === 'hero'" class="panel-title hero-title">{{ panel.title }}</h1>
          <h2 v-else class="panel-title">{{ panel.title }}</h2>
          <p class="panel-copy">{{ panel.copy }}</p>

          <div v-if="panel.key === 'hero'" class="hero-actions">
            <router-link class="primary-link" to="/rankings">进入数据排名</router-link>
            <router-link :class="['ghost-link', { disabled: isAiDevMode }]" to="/agent">AI 复盘 <span v-if="isAiDevMode" class="dev-tag">开发中</span></router-link>
          </div>
          <div v-if="panel.key === 'hero'" class="hero-credit">
            <span>by <b>flylegends</b></span>
            <span class="credit-sep">·</span>
            <span>交流群 791050795</span>
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
            <p class="match-league" v-if="recentMatches.length">{{ recentMatches[0].league }}</p>
            <div v-for="match in recentMatches" :key="match.id" class="match-row">
              <span>{{ match.stage }}</span>
              <strong>{{ match.teams }}</strong>
              <small>{{ match.winner ? match.winner + ' 胜' : match.time }}</small>
            </div>
          </div>

          <div v-if="panel.key === 'agent'" class="agent-console">
            <div class="console-line">加载英雄数据、Ban/Pick 规则...</div>
            <div class="console-line accent">模拟双方 BP 博弈，生成推荐阵容</div>
            <router-link class="primary-link compact" to="/bp-simulator">开始模拟</router-link>
          </div>
        </div>
      </article>
    </section>

    <footer class="home-footer" :class="{ open: footerOpen }">
      <div class="footer-inner">
        <div class="footer-grid">
          <div class="footer-block">
            <h4 class="footer-label">联系作者</h4>
            <p class="footer-info"><b>flylegends</b></p>
            <p class="footer-info">邮箱：2231499412@qq.com</p>
            <p class="footer-info">玩家交流群：791050795</p>
          </div>
          <div class="footer-block">
            <h4 class="footer-label">开源项目</h4>
            <a class="footer-link" href="https://github.com/2231499412/kpl-agent" target="_blank" rel="noopener">
              github.com/2231499412/kpl-agent
            </a>
            <p class="footer-desc">KPL 赛事数据分析平台，数据来源于腾讯官方接口。欢迎 Star & PR。</p>
          </div>
          <div class="footer-block">
            <h4 class="footer-label">技术栈</h4>
            <div class="footer-tags">
              <span>Vue 3</span><span>Element Plus</span><span>Spring Boot</span><span>MyBatis-Plus</span><span>MySQL</span>
            </div>
          </div>
          <div class="footer-block">
            <h4 class="footer-label">免责声明</h4>
            <p class="footer-disclaimer">本项目仅供学习与研究使用，所有赛事数据版权归腾讯及 KPL 联赛所有。不得将数据用于任何商业用途。使用本项目即表示您同意本声明。</p>
          </div>
        </div>
      </div>
    </footer>

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

    <div class="scroll-cue" :class="{ hide: footerOpen }">
      <span>SCROLL</span>
      <svg class="scroll-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="6 9 12 15 18 9" />
      </svg>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Aim, Box, Calendar, ChatDotRound, DataAnalysis, List, Operation, Picture, Trophy } from '@element-plus/icons-vue'
import homeBg from '../assets/home-bg.webp'

const isAiDevMode = import.meta.env.VITE_AI_DEV_MODE === 'true'

const activeIndex = ref(0)
const locked = ref(false)
const footerOpen = ref(false)
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
    copy: '把赛程、阵容、英雄、装备和 BP 模拟压进同一块战术面板。滚动一次，切换一个功能。',
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
    nav: 'BP',
    kicker: 'BP SIMULATOR',
    title: 'BP 模拟',
    copy: '自由选择蓝红双方 Ban/Pick，模拟完整对局阵容博弈。',
  },
]

const tools = [
  { title: '数据排名', desc: '战队 / 选手 / 英雄', route: '/rankings', icon: Trophy },
  { title: '赛程查询', desc: '赛程与对局详情', route: '/matches', icon: Calendar },
  { title: '装备分析', desc: '出场率与分路偏好', route: '/equipment', icon: Box },
  { title: '英雄梯度', desc: '版本强势英雄分档', route: '/tier-list', icon: List },
  { title: '英雄图鉴', desc: '全英雄资料与皮肤', route: '/hero-gallery', icon: Picture },
  { title: '对位雷达', desc: '选手对位数据对比', route: '/lane-radar', icon: Aim },
  { title: 'BP 模拟', desc: '自由模拟 Ban/Pick 博弈', route: '/bp-simulator', icon: Operation },
  { title: 'BP 分析', desc: 'Ban/Pick 策略辅助', route: '/bp-analysis', icon: DataAnalysis },
  { title: 'AI 复盘', desc: '智能对局分析', route: '/agent', icon: ChatDotRound },
]

const moduleCount = 9

const stats = computed(() => [
  { label: '战队样本', value: teamCount.value || '--' },
  { label: '比赛记录', value: matchCount.value || '--' },
  { label: '英雄池', value: heroCount.value || '--' },
  { label: '功能模块', value: moduleCount },
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
  if (direction === 1 && activeIndex.value === panels.length - 1) {
    footerOpen.value = true
    locked.value = true
    window.setTimeout(() => { locked.value = false }, 600)
    return
  }
  if (direction === -1 && footerOpen.value) {
    footerOpen.value = false
    locked.value = true
    window.setTimeout(() => { locked.value = false }, 600)
    return
  }
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
  if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 42) {
    stepPanel(diffX > 0 ? 1 : -1)
  }
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
    const [statsRes, matchRes, leagueRes] = await Promise.allSettled([
      fetch('/api/leagues/stats').then((r) => r.json()),
      fetch('/api/query/match/schedule').then((r) => r.json()),
      fetch('/api/leagues?limit=50').then((r) => r.json()),
    ])

    const statsData = statsRes.status === 'fulfilled' ? statsRes.value?.data : {}
    teamCount.value = statsData?.teamCount || 34
    matchCount.value = statsData?.battleCount || statsData?.matchCount || 10420
    heroCount.value = statsData?.heroCount || 116

    const matchData = matchRes.status === 'fulfilled' ? matchRes.value?.data : {}
    const matches = Array.isArray(matchData?.data) ? matchData.data : Array.isArray(matchData) ? matchData : []
    const leagueData = leagueRes.status === 'fulfilled' ? leagueRes.value?.data : []
    const leagues = Array.isArray(leagueData) ? leagueData : []
    const leagueMap = Object.fromEntries((leagues || []).map(l => [l.leagueId, l.leagueName]))

    if (Array.isArray(matches) && matches.length) {
      const lid = matches[0]?.leagueId || ''
      const leagueName = leagueMap[lid] || 'KPL'

      recentMatches.value = matches
        .sort((a, b) => (b.startTime || '').localeCompare(a.startTime || ''))
        .slice(0, 5)
        .map((match, index) => {
          const c1 = match.camp1TeamName || '蓝方'
          const c2 = match.camp2TeamName || '红方'
          const s1 = match.camp1Score ?? '-'
          const s2 = match.camp2Score ?? '-'
          const winner = match.winCamp === 1 ? c1 : match.winCamp === 2 ? c2 : ''
          return {
            id: match.matchId || index,
            league: leagueName,
            stage: match.matchStageDesc || '',
            teams: `${c1}  ${s1} : ${s2}  ${c2}`,
            winner,
            time: formatTime(match.startTime),
          }
        })
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
  --ink: var(--kpl-ink);
  --soft: var(--kpl-soft);
  --dim: var(--kpl-dim);
  --cyan: var(--kpl-cyan);
  --mint: var(--kpl-mint);
  --gold: var(--kpl-gold);
  --panel: rgba(8, 18, 20, 0.62);
  --panel-line: rgba(155, 245, 229, 0.18);
  position: fixed;
  inset: 0;
  min-width: 1120px;
  overflow: hidden;
  color: var(--ink);
  background: var(--kpl-bg);
  outline: none;
  isolation: isolate;
}

:global(body.home-lock) {
  overflow: hidden;
}

@media (max-width: 767px) {
  :global(body.home-lock) {
    overflow-x: hidden;
    overflow-y: auto;
  }
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
  top: 0;
  left: 0;
  right: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72px;
  padding: 0 52px;
  background: linear-gradient(180deg, rgba(2, 5, 6, 0.6) 0%, rgba(2, 5, 6, 0.15) 70%, transparent 100%);
}

.home-brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  color: var(--ink);
  text-decoration: none;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 2px;
}

.brand-sigil {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgba(136, 247, 238, 0.28);
  background: rgba(3, 12, 14, 0.6);
  color: var(--cyan);
  font-size: 18px;
  font-weight: 900;
  transition: border-color 0.3s, box-shadow 0.3s;
}

.home-brand:hover .brand-sigil {
  border-color: var(--cyan);
  box-shadow: 0 0 20px rgba(136, 247, 238, 0.2);
}

.brand-text {
  font-size: 14px;
  letter-spacing: 4px;
  font-weight: 800;
}

.brand-dot {
  color: var(--cyan);
  margin: 0 1px;
}

.home-nav {
  display: flex;
  align-items: center;
  gap: 0;
}

.home-nav a {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 72px;
  padding: 0 24px;
  color: rgba(238, 247, 244, 0.5);
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1.5px;
  transition: color 0.3s;
}

.home-nav a::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 0;
  height: 2px;
  background: var(--cyan);
  transform: translateX(-50%);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 0 8px rgba(136, 247, 238, 0.4);
}

.home-nav a:hover {
  color: var(--ink);
}

.home-nav a:hover::after {
  width: 100%;
}

.home-nav a.active {
  color: var(--cyan);
}

.home-nav a.active::after {
  width: 100%;
  background: var(--cyan);
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
  opacity: 0;
  transform: translateX(60px);
  transition:
    opacity 0.55s cubic-bezier(0.4, 0, 0.2, 1) 0.18s,
    transform 0.55s cubic-bezier(0.4, 0, 0.2, 1) 0.18s;
}

.home-panel.active .panel-inner {
  opacity: 1;
  transform: translateX(0);
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

.hero-credit {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 20px 0 0;
  font-size: 12px;
  color: var(--soft);
  font-weight: 500;
}
.hero-credit b {
  color: var(--ink);
  font-weight: 700;
}
.credit-sep {
  color: var(--dim);
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
.ghost-link.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}
.dev-tag {
  display: inline-block;
  padding: 1px 6px;
  margin-left: 4px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.15);
  vertical-align: middle;
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

.match-league {
  margin: 0 0 4px;
  color: var(--gold);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 2px;
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

/* 页脚 */
.home-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  transform: translateY(100%);
  transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}
.home-footer.open {
  transform: translateY(0);
}
.footer-inner {
  background: var(--panel);
  border-top: 1px solid var(--panel-line);
  padding: 40px 60px 48px;
  backdrop-filter: blur(12px);
}
.footer-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
  max-width: 1080px;
  margin: 0 auto;
}
.footer-info {
  margin: 0;
  font-size: 13px;
  color: var(--soft);
  line-height: 1.8;
}
.footer-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.footer-label {
  margin: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--cyan);
}
.footer-link {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink);
  text-decoration: none;
  transition: color 0.15s;
}
.footer-link:hover {
  color: var(--cyan);
}
.footer-desc {
  margin: 0;
  font-size: 13px;
  color: var(--soft);
  line-height: 1.6;
}
.footer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.footer-tags span {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
  background: rgba(155, 245, 229, 0.08);
  border: 1px solid var(--panel-line);
  border-radius: 3px;
}
.footer-disclaimer {
  margin: 0;
  font-size: 12px;
  color: var(--dim);
  line-height: 1.8;
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
  left: 50%;
  bottom: 32px;
  z-index: 6;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: var(--dim);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 3px;
  transition: opacity 0.4s;
  pointer-events: none;
}
.scroll-cue.hide {
  opacity: 0;
}
.scroll-arrow {
  width: 22px;
  height: 22px;
  color: var(--cyan);
  animation: scroll-bounce 1.6s ease-in-out infinite;
}
@keyframes scroll-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.6; }
  50% { transform: translateY(6px); opacity: 1; }
}

@media (max-width: 1180px) {
  .home-stage {
    min-width: 0;
  }

  .home-topbar {
    padding: 0 28px;
  }

  .home-nav a {
    padding: 0 16px;
    font-size: 12px;
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

@media (max-width: 767px) {
  .home-stage {
    min-width: 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .home-topbar {
    height: 58px;
    padding: 0 12px;
  }

  .home-brand {
    min-width: 0;
  }

  .home-nav {
    display: none;
  }

  .panel-rail {
    height: auto;
  }

  .home-panel {
    min-height: 100vh;
    padding: 86px 16px 54px;
  }

  .panel-inner,
  .match-board,
  .agent-console {
    width: 100%;
    max-width: none;
  }

  .hero-title,
  .panel-title {
    max-width: 100%;
    font-size: 38px;
    line-height: 1.08;
  }

  .hero-copy,
  .panel-copy {
    max-width: 100%;
    font-size: 13px;
  }

  .hero-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
    gap: 8px;
  }

  .hero-actions button,
  .hero-actions a {
    min-width: 0;
    padding: 0 10px;
    font-size: 12px;
  }

  .metric-grid,
  .tool-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .metric-card,
  .tool-card {
    min-height: 112px;
    padding: 14px 12px;
  }

  .match-board {
    overflow-x: auto;
  }

  .match-row {
    min-width: 520px;
  }

  .agent-console {
    padding: 16px;
  }

  .slide-meter,
  .scroll-cue {
    display: none;
  }

  .footer-inner {
    padding: 28px 20px 36px;
  }
  .footer-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 20px;
  }
  .footer-link {
    font-size: 15px;
  }
}
</style>
