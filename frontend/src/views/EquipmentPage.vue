<template>
  <main class="app-shell has-sidebar equip-tier-console" :class="`theme-${theme}`">
    <div class="grid-overlay"></div>
    <div class="noise-overlay"></div>

    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">E</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>装备梯度榜</h1>
        </div>
      </div>
      <div class="status-line">
        <el-select v-model="selectedLeagueId" class="league-select" placeholder="选择赛事" @change="onLeagueChange">
          <el-option v-for="l in leagues" :key="l.leagueId" :label="l.leagueName" :value="l.leagueId" />
        </el-select>
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <!-- 分路筛选 -->
    <nav ref="roleNavRef" class="role-tabs">
      <span class="role-pill" :style="pillStyle"></span>
      <button
        v-for="r in roleOptions"
        :key="r.value"
        :ref="el => roleBtnRefs[r.value] = el"
        :class="{ active: activeRole === r.value }"
        @click="selectRole(r.value)"
      >{{ r.label }}</button>
    </nav>

    <!-- 图例 + 评分说明 -->
    <div class="info-bar" v-if="!loading && filteredEquips.length">
      <div class="legend-items">
        <div class="legend-item">
          <span class="legend-dot high-pick"></span>
          <span>高出场率 (&gt;50%)</span>
        </div>
      </div>
      <div class="score-formula">
        <span class="formula-label">评分</span>
        <span class="formula-eq">= 该分路出场次数</span>
        <span class="formula-note">梯度按百分位分档，反映装备在选定分路的出场优先级。切换分路后评分会重新计算。</span>
      </div>
    </div>

    <!-- 加载 -->
    <div v-if="loading" class="loading-wrap">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>

    <!-- 梯度内容 -->
    <div v-else class="tier-content">
      <TransitionGroup name="tier-sort" tag="div" class="equip-flat-grid">
        <template v-for="(item, idx) in flatEquips" :key="item._key">
          <div v-if="item._type === 'header'" class="tier-header tier-header-row">
            <span :class="['tier-badge', 'tier-' + item.tier.key]">{{ item.tier.label }}</span>
            <span class="tier-name">{{ item.tier.name }}</span>
            <span class="tier-criteria">{{ item.tier.desc }}{{ item.tier.scoreRange ? ' · 评分 ' + item.tier.scoreRange : '' }}</span>
            <span class="tier-count">{{ item.tier.equips.length }} 装备</span>
          </div>
          <div v-else class="equip-card"
            :class="{ 'high-pick': item.pickRate > 0.5, 'low-pick': item.pickCount < 5 }"
            @click="openDetail(item)">
            <img v-if="item.equipIcon" :src="item.equipIcon" class="equip-icon" />
            <div v-else class="equip-icon-placeholder">?</div>
            <span class="equip-score">{{ item._score }}</span>
            <span class="equip-name">{{ item.equipName }}</span>
            <span class="equip-meta">{{ item._lanePickCount != null ? item._lanePickCount + '次' : item.pickCount + '次' }} / {{ item.heroCount }}英雄</span>
            <span class="equip-price" v-if="item.totalPrice">{{ item.totalPrice }} 金币</span>
          </div>
        </template>
      </TransitionGroup>

      <div v-if="!filteredEquips.length && !loading" class="empty-state">
        <span>暂无数据，请先选择赛事</span>
      </div>
    </div>

    <!-- 装备详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="640" class="detail-dialog" destroy-on-close>
      <template v-if="detailLoading">
        <div class="skeleton-wrap">
          <div class="skeleton-row" style="width:40%"></div>
          <div class="skeleton-row" style="width:100%;height:80px;margin-top:16px"></div>
        </div>
      </template>
      <template v-else-if="detailData">
        <div class="detail-body">
          <div class="detail-info">
            <img v-if="detailData.equipIcon" :src="detailData.equipIcon" class="detail-icon" />
            <div>
              <h3>{{ detailData.equipName }}</h3>
              <p v-if="detailData.totalPrice" class="detail-price">
                总价: {{ detailData.totalPrice }} 金币
                <template v-if="detailData.price && detailData.price !== detailData.totalPrice">
                  (组件: {{ detailData.price }} 金币)
                </template>
              </p>
              <p v-if="detailData.equipDescGain" class="detail-desc" v-html="detailData.equipDescGain"></p>
              <p v-if="detailData.equipDescFunction" class="detail-passive" v-html="detailData.equipDescFunction"></p>
            </div>
          </div>
          <div class="detail-section" v-if="detailData.positions?.length">
            <h4>分路分布</h4>
            <div class="position-grid">
              <div v-for="p in detailData.positions" :key="p.positionDesc || p.positionNum" class="position-card">
                <span class="pos-name">{{ posName(p) }}</span>
                <span class="pos-count">{{ p.cnt }} 次</span>
              </div>
            </div>
          </div>
          <div class="detail-section" v-if="detailData.heroes?.length">
            <h4>常用英雄</h4>
            <div class="hero-grid-detail">
              <div v-for="h in detailData.heroes" :key="h.heroId" class="hero-card-detail">
                <img :src="'https://res.edata.qq.com/sgame/static/images/hero/' + h.heroId + '.jpg'" class="hero-img" />
                <span class="hero-name-detail">{{ h.heroName }}</span>
                <span class="hero-count">{{ h.cnt }} 次</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const leagues = ref([])
const selectedLeagueId = ref('')
const equipList = ref([])
const loading = ref(false)
const activeRole = ref('all')
const roleNavRef = ref(null)
const roleBtnRefs = {}
const pillStyle = ref({})

function selectRole(value) {
  activeRole.value = value
  nextTick(updatePill)
}

function updatePill() {
  const nav = roleNavRef.value
  const btn = roleBtnRefs[activeRole.value]
  if (!nav || !btn) return
  const navRect = nav.getBoundingClientRect()
  const btnRect = btn.getBoundingClientRect()
  pillStyle.value = {
    width: btnRect.width + 'px',
    transform: `translateX(${btnRect.left - navRect.left}px)`,
  }
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

const roleOptions = [
  { label: '全部分路', value: 'all' },
  { label: '对抗路', value: '5' },
  { label: '打野', value: '7' },
  { label: '中路', value: '2' },
  { label: '发育路', value: '4' },
  { label: '游走', value: '6' },
]

const POS_MAP = { 2: '中路', 4: '发育路', 5: '对抗路', 6: '游走', 7: '打野' }

function posName(p) {
  const fixed = BUGGY_POS_FIX[p.positionNum] ?? p.positionNum
  return POS_MAP[fixed] || p.positionDesc || '-'
}

// 修正历史数据中 inferPosition 映射错误: 对抗路↔游走, 打野↔发育路
const BUGGY_POS_FIX = { 6: 5, 5: 7, 7: 4, 4: 6 }

function getLanePickCount(item, posNum) {
  const positions = item.positions
  if (!positions) return 0
  for (const p of positions) {
    const fixed = BUGGY_POS_FIX[p.positionNum] ?? p.positionNum
    if (fixed === posNum) return p.cnt || 0
  }
  return 0
}

const tierDefs = [
  { key: 't0',  label: 'T0',   name: '绝对核心',  desc: '出场率极高，几乎必出' },
  { key: 't05', label: 'T0.5', name: '最高优先',  desc: '出场率很高，优先合成' },
  { key: 't1',  label: 'T1',   name: '主力装备',  desc: '高出场率，主流选择' },
  { key: 't2',  label: 'T2',   name: '常规装备',  desc: '稳定出场，常用搭配' },
  { key: 't3',  label: 'T3',   name: '情景装备',  desc: '针对性出装 / 特定英雄' },
  { key: 't4',  label: 'T4',   name: '冷门装备',  desc: '偶尔出现，特定阵容' },
]

const tierPercentiles = [0.10, 0.25, 0.50, 0.75, 0.90]

function assignTiers(equips) {
  if (!equips.length) return equips
  const sorted = [...equips].sort((a, b) => b._score - a._score)
  const n = sorted.length
  const thresholds = tierPercentiles.map(p => Math.ceil(n * p))
  return sorted.map((item, i) => {
    let tier
    if (i < thresholds[0]) tier = 't0'
    else if (i < thresholds[1]) tier = 't05'
    else if (i < thresholds[2]) tier = 't1'
    else if (i < thresholds[3]) tier = 't2'
    else if (i < thresholds[4]) tier = 't3'
    else tier = 't4'
    return { ...item, _tier: tier }
  })
}

function isVisibleEquip(item) {
  if (item.equipName && item.equipName.includes('之靴')) return true
  if (item.totalPrice == null) return true
  return item.totalPrice >= 1000
}

const filteredEquips = computed(() => {
  if (activeRole.value === 'all') {
    return assignTiers(equipList.value.filter(isVisibleEquip).map(item => ({
      ...item,
      _score: Math.round((item.pickRate || 0) * 100),
    })))
  }
  const posNum = Number(activeRole.value)
  const laneItems = equipList.value
    .map(item => ({
      ...item,
      _lanePickCount: getLanePickCount(item, posNum),
    }))
    .filter(item => item._lanePickCount > 0 && isVisibleEquip(item))
    .map(item => ({
      ...item,
      _score: item._lanePickCount,
    }))
  return assignTiers(laneItems)
})

const visibleTiers = computed(() => {
  return tierDefs.map(t => ({
    ...t,
    equips: filteredEquips.value
      .filter(item => item._tier === t.key)
      .sort((a, b) => b._score - a._score),
    scoreRange: (() => {
      const tierEquips = filteredEquips.value.filter(item => item._tier === t.key)
      if (!tierEquips.length) return ''
      const scores = tierEquips.map(item => item._score)
      return `${Math.min(...scores)}-${Math.max(...scores)}`
    })(),
  }))
})

const flatEquips = computed(() => {
  const result = []
  for (const tier of visibleTiers.value) {
    if (!tier.equips.length) continue
    result.push({ _type: 'header', _key: `tier-${tier.key}`, tier })
    for (const item of tier.equips) {
      result.push({ ...item, _type: 'item', _key: `eq-${item.equipId}` })
    }
  }
  return result
})

const detailTitle = computed(() => detailData.value?.equipName || '装备详情')

async function init() {
  try {
    const res = await fetch('/api/leagues?limit=50')
    const data = await res.json()
    leagues.value = data?.data || []
    if (leagues.value.length) {
      selectedLeagueId.value = leagues.value[0].leagueId
      await loadEquipRanking()
    }
  } catch { /* ignore */ }
}

async function loadEquipRanking() {
  if (!selectedLeagueId.value) return
  loading.value = true
  try {
    const res = await fetch(`/api/query/equip/top?leagueId=${selectedLeagueId.value}&limit=100`)
    const data = await res.json()
    equipList.value = data?.data?.data || []
  } catch { equipList.value = [] }
  loading.value = false
}

function onLeagueChange() {
  loadEquipRanking()
}

async function openDetail(item) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const res = await fetch(`/api/query/equip/detail?equipId=${item.equipId}&leagueId=${selectedLeagueId.value}`)
    const data = await res.json()
    detailData.value = data?.data || null
  } catch { detailData.value = null }
  detailLoading.value = false
}

onMounted(() => {
  init()
  nextTick(updatePill)
  window.addEventListener('resize', updatePill)
})
onUnmounted(() => {
  window.removeEventListener('resize', updatePill)
})
</script>

<style scoped>
.equip-tier-console {
  --c-bg: #f8f5ec;
  --c-panel: rgba(255, 255, 255, 0.92);
  --c-line: rgba(0, 0, 0, 0.1);
  --c-ink: #1a1a1a;
  --c-soft: rgba(26, 26, 26, 0.6);
  --c-dim: rgba(26, 26, 26, 0.35);
  --c-card: rgba(0, 0, 0, 0.03);
  --c-corner: rgba(0, 0, 0, 0.18);
  --c-hover: rgba(0, 0, 0, 0.06);
  --c-grid: rgba(0, 0, 0, 0.04);
  --tier-t0: #c0392b;
  --tier-t05: #e67e22;
  --tier-t1: #f39c12;
  --tier-t2: #2980b9;
  --tier-t3: #7f8c8d;
  --tier-t4: #bdc3c7;

  position: relative;
  min-height: 100vh;
  padding: 28px 32px calc(78px + env(safe-area-inset-bottom)) 87px;
  color: var(--c-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
  overflow-x: hidden;
}
.equip-tier-console.theme-dark {
  --c-bg: #0a0a0a;
  --c-panel: rgba(18, 18, 18, 0.92);
  --c-line: rgba(255, 255, 255, 0.1);
  --c-ink: #e8e8e8;
  --c-soft: rgba(232, 232, 232, 0.6);
  --c-dim: rgba(232, 232, 232, 0.35);
  --c-card: rgba(255, 255, 255, 0.05);
  --c-corner: rgba(255, 255, 255, 0.18);
  --c-hover: rgba(255, 255, 255, 0.08);
  --c-grid: rgba(255, 255, 255, 0.03);
  --tier-t0: #e74c3c;
  --tier-t05: #f39c12;
  --tier-t1: #f1c40f;
  --tier-t2: #3498db;
  --tier-t3: #95a5a6;
  --tier-t4: #7f8c8d;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

/* 网格纹理 */
.grid-overlay {
  position: fixed; inset: 0; z-index: -3;
  background:
    linear-gradient(var(--c-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--c-grid) 1px, transparent 1px);
  background-size: 80px 80px;
  pointer-events: none;
}
.noise-overlay {
  position: fixed; inset: 0; z-index: -2;
  background: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");
  pointer-events: none;
}

/* 顶栏 */
.command-strip {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border: 1px solid var(--c-line); background: var(--c-panel);
  margin-bottom: 16px; flex-shrink: 0;
}
.command-strip::before {
  content: ""; position: absolute; left: -1px; top: -1px; width: 36px; height: 36px;
  border-left: 2px solid var(--c-corner); border-top: 2px solid var(--c-corner);
  pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px; display: grid; place-items: center;
  border: 1px solid var(--c-line); color: var(--c-ink); background: var(--c-card);
  font-size: 18px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--c-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--c-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }
.league-select { width: 220px; }
.theme-toggle {
  display: inline-flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--c-dim); font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--c-ink); }
.toggle-track {
  position: relative; width: 32px; height: 16px; border-radius: 8px;
  background: var(--c-line); transition: background 0.2s;
}
.toggle-track.on { background: var(--c-ink); }
.toggle-thumb {
  position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%;
  background: var(--c-bg); transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }

/* 分路筛选 */
.role-tabs {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 5px;
  padding: 4px;
  overflow-x: auto;
}
.role-pill {
  position: absolute;
  top: 4px;
  left: 0;
  height: calc(100% - 8px);
  background: #1a1a1a;
  border-radius: 10px;
  transition: transform .3s cubic-bezier(.4,0,.2,1);
  z-index: 0;
  pointer-events: none;
}
.role-tabs button {
  position: relative;
  z-index: 1;
  flex: 1 0 auto;
  min-width: 86px;
  padding: 0 14px;
  border: 1px solid var(--c-line);
  border-radius: 10px;
  color: var(--c-soft);
  background: transparent;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  transition: color .2s ease;
}
.role-tabs button:hover { color: var(--c-ink); }
.role-tabs button.active { color: #f8f5ec; }

/* 扁平网格 + 排序动画 */
.equip-flat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.tier-header-row {
  grid-column: 1 / -1;
}
.tier-sort-move {
  transition: transform 0.9s cubic-bezier(0.22, 1, 0.36, 1);
}
.tier-sort-leave-active {
  transition: opacity 0.36s cubic-bezier(0.4, 0, 1, 1),
              transform 0.36s cubic-bezier(0.4, 0, 1, 1);
  position: absolute;
}
.tier-sort-leave-to {
  opacity: 0;
  transform: scale(0.86);
}
.tier-sort-enter-active {
  transition: all 0.35s cubic-bezier(0, 0, 0.2, 1) 0.15s;
}
.tier-sort-enter-from {
  opacity: 0;
  transform: scale(0.86);
}

/* 加载 */
.loading-wrap {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; padding: 80px 0; color: var(--c-dim);
}
.loading-spinner {
  width: 28px; height: 28px; border: 3px solid var(--c-line);
  border-top-color: var(--c-ink); border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex; align-items: center; justify-content: center;
  padding: 80px 0; color: var(--c-dim); font-size: 14px;
}

/* 梯度区块 */
.tier-content { display: flex; flex-direction: column; gap: 0; }
.tier-section { padding: 0 0 8px; }
.tier-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 12px; border-bottom: 1px solid var(--c-line);
  margin-bottom: 14px;
}
.tier-badge {
  min-width: 36px; height: 26px; display: inline-grid; place-items: center;
  font-size: 13px; font-weight: 900; color: #fff; border-radius: 3px; padding: 0 6px;
}
.tier-badge.tier-t0 { background: var(--tier-t0); }
.tier-badge.tier-t05 { background: var(--tier-t05); }
.tier-badge.tier-t1 { background: var(--tier-t1); color: #1a1a1a; }
.tier-badge.tier-t2 { background: var(--tier-t2); }
.tier-badge.tier-t3 { background: var(--tier-t3); }
.tier-badge.tier-t4 { background: var(--tier-t4); }
.tier-name { font-size: 14px; font-weight: 700; color: var(--c-ink); }
.tier-criteria { font-size: 12px; color: var(--c-dim); margin-left: 4px; }
.tier-count { font-size: 12px; color: var(--c-dim); margin-left: auto; }

.tier-divider {
  height: 0; border-top: 1px dashed var(--c-line);
  margin: 6px 0;
}

/* 装备卡片网格 */
.equip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}
.equip-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 12px 8px 10px; background: var(--c-card); border: 1px solid var(--c-line);
  transition: all 0.15s; position: relative; cursor: pointer;
}
.equip-card:hover {
  background: var(--c-hover); border-color: var(--c-ink);
  transform: translateY(-2px);
}
.equip-card.high-pick { border-color: #e74c3c; border-width: 2px; box-shadow: 0 0 8px rgba(231, 76, 60, 0.25); }

.equip-icon {
  width: 48px; height: 48px; border-radius: 4px; object-fit: cover;
  border: 1px solid var(--c-line);
}
.equip-icon-placeholder {
  width: 48px; height: 48px; border-radius: 4px; display: grid; place-items: center;
  background: var(--c-card); border: 1px solid var(--c-line); font-size: 20px; color: var(--c-dim);
}
.equip-score {
  font-size: 18px; font-weight: 900; color: var(--c-ink);
  line-height: 1;
}
.equip-name {
  font-size: 11px; color: var(--c-soft); font-weight: 600;
  text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 100%;
}
.equip-meta {
  font-size: 10px; color: var(--c-dim); text-align: center;
}
.equip-price {
  font-size: 10px; font-weight: 700; color: #f39c12; text-align: center;
}

/* 图例 + 评分说明 */
.info-bar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 16px;
  padding: 12px 16px; margin-bottom: 16px;
  border: 1px solid var(--c-line); background: var(--c-panel);
}
.legend-items { display: flex; gap: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--c-soft); }
.legend-dot {
  width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0;
}
.legend-dot.high-pick { background: #e74c3c; }
.score-formula {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-size: 12px; color: var(--c-soft); margin-left: auto;
}
.formula-label { font-weight: 800; color: var(--c-ink); }
.formula-eq { color: var(--c-ink); font-weight: 600; white-space: nowrap; }
.formula-note {
  font-size: 11px; color: var(--c-dim); line-height: 1.5;
  flex-basis: 100%;
}

/* 详情弹窗 */
.detail-body { display: flex; flex-direction: column; gap: 20px; }
.detail-info {
  display: flex; align-items: flex-start; gap: 14px;
  padding-bottom: 16px; border-bottom: 1px solid var(--c-line);
}
.detail-icon {
  width: 48px; height: 48px; border: 1px solid var(--c-line);
  border-radius: 6px; object-fit: cover;
}
.detail-info h3 { margin: 0; font-size: 18px; font-weight: 900; color: var(--c-ink); }
.detail-price { margin: 4px 0 0; font-size: 13px; font-weight: 700; color: #f39c12; }
.detail-desc { margin: 4px 0 0; font-size: 12px; color: var(--c-ink); line-height: 1.6; opacity: 0.75; }
.detail-passive { margin: 6px 0 0; font-size: 12px; color: #27ae60; line-height: 1.6; font-weight: 600; }
.detail-section h4 { margin: 0 0 10px; font-size: 13px; font-weight: 800; color: var(--c-ink); }
.position-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.position-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 16px; border: 1px solid var(--c-line); background: var(--c-card); min-width: 72px;
}
.pos-name { font-size: 12px; font-weight: 700; color: var(--c-ink); }
.pos-count { font-size: 11px; color: var(--c-dim); }
.hero-grid-detail { display: flex; flex-wrap: wrap; gap: 8px; }
.hero-card-detail {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border: 1px solid var(--c-line); background: var(--c-card);
}
.hero-img { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.hero-name-detail { font-size: 13px; font-weight: 600; color: var(--c-ink); }
.hero-count { font-size: 11px; color: var(--c-dim); }

.skeleton-wrap { padding: 8px 0; }
.skeleton-row {
  height: 16px; border-radius: 6px;
  background: linear-gradient(90deg, var(--c-card) 25%, var(--c-hover) 50%, var(--c-card) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s ease-in-out infinite;
}
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* Element Plus overrides */
:deep(.el-dialog) {
  border: 1px solid var(--c-line) !important;
  border-radius: 0 !important; background: var(--c-panel) !important;
}
:deep(.el-dialog__title) { color: var(--c-ink) !important; font-weight: 900 !important; }
:deep(.el-dialog__header) { border-bottom: 1px solid var(--c-line); padding-bottom: 12px; margin: 0; }
:deep(.el-dialog__body) { max-height: 60vh; overflow-y: auto; padding: 16px 20px; }
:deep(.el-select__wrapper) {
  border-radius: 0 !important; background: var(--c-card) !important;
  box-shadow: 0 0 0 1px var(--c-line) inset !important; min-height: 36px;
}
:deep(.el-select__placeholder), :deep(.el-select__selected-item .el-select__tags-text) {
  color: var(--c-ink) !important; font-weight: 600;
}
:deep(.el-select__caret) { color: var(--c-soft) !important; }

/* 移动端 */
@media (max-width: 767px) {
  .equip-tier-console {
    padding: 12px 12px calc(82px + env(safe-area-inset-bottom));
  }

  .command-strip {
    min-height: 56px; padding: 10px 12px;
  }
  .brand-mark { width: 34px; height: 34px; font-size: 16px; }
  h1 { font-size: 17px; }
  .league-select { width: 132px; }
  .theme-toggle small { display: none; }

  .role-tabs { gap: 4px; padding: 3px; }
  .role-tabs button { flex: 0 0 76px; min-width: 76px; height: 42px; padding: 0 10px; }

  .tier-header { padding: 10px 0 8px; margin-bottom: 10px; }
  .tier-badge { min-width: 32px; height: 22px; font-size: 12px; }
  .tier-name { font-size: 13px; }
  .tier-criteria { font-size: 11px; display: none; }
  .tier-count { font-size: 11px; }

  .equip-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }
  .equip-flat-grid {
    gap: 8px;
  }
  .equip-flat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .equip-card { padding: 8px 4px 6px; }
  .equip-icon { width: 40px; height: 40px; }
  .equip-score { font-size: 15px; }
  .equip-name { font-size: 10px; }
  .equip-meta { font-size: 9px; }
  .equip-price { font-size: 9px; }

  .info-bar { flex-direction: column; align-items: flex-start; gap: 10px; padding: 10px 12px; }
  .legend-items { gap: 12px; }
  .legend-item { font-size: 11px; }
  .score-formula { margin-left: 0; }
  .formula-note { font-size: 10px; }

  .detail-info { gap: 10px; }
  .position-card {
    flex: 1 1 calc(50% - 4px);
    min-width: 0;
  }
  :deep(.el-dialog) { width: calc(100vw - 16px) !important; }
  :deep(.el-dialog__body) {
    max-height: calc(86dvh - 60px);
    padding: 12px;
  }
}
</style>
