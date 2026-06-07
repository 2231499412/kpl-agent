<template>
  <main class="app-shell has-sidebar equip-console" :class="`theme-${theme}`">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>装备分析</h1>
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

    <section class="query-bar">
      <div class="mode-tabs">
        <button v-for="m in modes" :key="m.key" :class="{ active: mode === m.key }" @click="switchMode(m.key)">{{ m.label }}</button>
      </div>
      <div class="breadcrumb" v-if="selectedHero || selectedPlayer">
        <span class="crumb-back" @click="goBack">← 返回列表</span>
        <span class="crumb-sep">/</span>
        <span class="crumb-current">{{ selectedHero?.heroName || selectedPlayer?.playerName }}</span>
      </div>
    </section>

    <section class="equip-content">
      <!-- 全局装备排行 / 英雄装备 / 选手装备 -->
      <template v-if="mode === 'global' || (mode !== 'global' && (selectedHero || selectedPlayer))">
        <div v-if="loading" class="loading-hint">
          <div class="spinner"></div>
          <span>加载装备数据...</span>
        </div>
        <template v-else-if="equipList.length">
          <p class="result-title">{{ resultTitle }}</p>
          <div class="equip-table">
            <div class="equip-row header-row">
              <span class="col col-rank">#</span>
              <span class="col col-equip">装备</span>
              <span class="col col-num">出场次数</span>
              <span class="col col-num col-secondary" v-if="mode === 'global'">涉及英雄</span>
              <span class="col col-bar">出场率</span>
            </div>
            <div v-for="(item, idx) in equipList" :key="item.equipId" class="equip-row" @click="openDetail(item)">
              <span class="col col-rank">{{ idx + 1 }}</span>
              <span class="col col-equip">
                <img v-if="item.equipIcon" :src="item.equipIcon" class="equip-icon" />
                <span class="equip-name">{{ item.equipName }}</span>
              </span>
              <span class="col col-num col-primary">{{ item.pickCount }}</span>
              <span class="col col-num col-secondary" v-if="mode === 'global'">{{ item.heroCount }}</span>
              <span class="col col-bar">
                <span class="rate-track"><span class="rate-fill" :style="{ width: ratePercent(item) + '%' }" /></span>
                <span class="rate-text">{{ ratePercent(item) }}%</span>
              </span>
            </div>
          </div>
        </template>
        <div v-else class="empty-hint">
          <span>暂无装备数据</span>
        </div>
      </template>

      <!-- 英雄列表（二级） -->
      <template v-else-if="mode === 'hero' && !selectedHero">
        <div v-if="heroLoading" class="loading-hint">
          <div class="spinner"></div>
          <span>加载英雄列表...</span>
        </div>
        <div v-else class="entity-grid">
          <div v-for="h in heroList" :key="h.heroId" class="entity-card" @click="selectHero(h)">
            <img :src="h.heroIcon || ('https://res.edata.qq.com/sgame/static/images/hero/' + h.heroId + '.jpg')" class="entity-img" />
            <span class="entity-name">{{ h.heroName }}</span>
            <span class="entity-meta">出场 {{ h.pickNum || h.battleCount }}</span>
          </div>
        </div>
      </template>

      <!-- 选手列表（二级） -->
      <template v-else-if="mode === 'player' && !selectedPlayer">
        <div v-if="heroLoading" class="loading-hint">
          <div class="spinner"></div>
          <span>加载选手列表...</span>
        </div>
        <div v-else class="entity-grid">
          <div v-for="p in playerList" :key="p.playerName" class="entity-card" @click="selectPlayer(p)">
            <img v-if="p.playerIcon" :src="p.playerIcon" class="entity-img" />
            <div v-else class="entity-img entity-placeholder">{{ p.playerName?.[0] }}</div>
            <span class="entity-name">{{ p.playerName }}</span>
            <span class="entity-meta">{{ p.teamName }} · {{ p.positionDesc }}</span>
          </div>
        </div>
      </template>
    </section>

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
              <p v-if="detailData.equipDescGain" class="detail-desc" v-html="detailData.equipDescGain"></p>
              <p v-if="detailData.equipDescFunction" class="detail-desc" v-html="detailData.equipDescFunction"></p>
            </div>
          </div>
          <div class="detail-section" v-if="detailData.positions?.length">
            <h4>分路分布</h4>
            <div class="position-grid">
              <div v-for="p in detailData.positions" :key="p.positionDesc" class="position-card">
                <span class="pos-name">{{ p.positionDesc }}</span>
                <span class="pos-count">{{ p.cnt }} 次</span>
              </div>
            </div>
          </div>
          <div class="detail-section" v-if="detailData.heroes?.length">
            <h4>常用英雄</h4>
            <div class="hero-grid">
              <div v-for="h in detailData.heroes" :key="h.heroId" class="hero-card">
                <img :src="'https://res.edata.qq.com/sgame/static/images/hero/' + h.heroId + '.jpg'" class="hero-img" />
                <span class="hero-name">{{ h.heroName }}</span>
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
import { computed, onMounted, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const leagues = ref([])
const selectedLeagueId = ref('')
const mode = ref('global')
const equipList = ref([])
const loading = ref(false)
const heroLoading = ref(false)
const heroList = ref([])
const playerList = ref([])
const selectedHero = ref(null)
const selectedPlayer = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

const modes = [
  { key: 'global', label: '全局排行' },
  { key: 'hero', label: '英雄装备' },
  { key: 'player', label: '选手装备' },
]

const detailTitle = computed(() => detailData.value?.equipName || '装备详情')

const resultTitle = computed(() => {
  if (mode.value === 'global') return '装备出场排行'
  if (selectedHero.value) return `${selectedHero.value.heroName} · 常用装备`
  if (selectedPlayer.value) return `${selectedPlayer.value.playerName} · 常用装备`
  return '装备出场排行'
})

function ratePercent(item) {
  if (item.pickRate != null) return Math.round(item.pickRate * 100)
  return 0
}

function switchMode(key) {
  mode.value = key
  selectedHero.value = null
  selectedPlayer.value = null
  equipList.value = []
  if (key === 'global') {
    loadEquipRanking()
  } else if (key === 'hero') {
    loadHeroList()
  } else if (key === 'player') {
    loadPlayerList()
  }
}

function goBack() {
  selectedHero.value = null
  selectedPlayer.value = null
  equipList.value = []
  if (mode.value === 'hero') loadHeroList()
  else if (mode.value === 'player') loadPlayerList()
}

function onLeagueChange() {
  if (mode.value === 'global') loadEquipRanking()
  else if (mode.value === 'hero') loadHeroList()
  else if (mode.value === 'player') loadPlayerList()
}

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
    const res = await fetch(`/api/query/equip/top?leagueId=${selectedLeagueId.value}&limit=30`)
    const data = await res.json()
    equipList.value = data?.data?.data || []
  } catch { equipList.value = [] }
  loading.value = false
}

async function loadHeroList() {
  if (!selectedLeagueId.value) return
  heroLoading.value = true
  try {
    const res = await fetch(`/api/query/hero/top?leagueId=${selectedLeagueId.value}&limit=50`)
    const data = await res.json()
    heroList.value = data?.data?.data || []
  } catch { heroList.value = [] }
  heroLoading.value = false
}

async function selectHero(hero) {
  selectedHero.value = hero
  loading.value = true
  try {
    const res = await fetch(`/api/query/equip/hero?heroId=${hero.heroId}&leagueId=${selectedLeagueId.value}&limit=20`)
    const data = await res.json()
    equipList.value = data?.data?.data || []
  } catch { equipList.value = [] }
  loading.value = false
}

async function loadPlayerList() {
  if (!selectedLeagueId.value) return
  heroLoading.value = true
  try {
    const res = await fetch(`/api/query/player/top?leagueId=${selectedLeagueId.value}&limit=50`)
    const data = await res.json()
    playerList.value = data?.data?.data || []
  } catch { playerList.value = [] }
  heroLoading.value = false
}

async function selectPlayer(player) {
  selectedPlayer.value = player
  loading.value = true
  try {
    const res = await fetch(`/api/query/equip/player?name=${encodeURIComponent(player.playerName)}&leagueId=${selectedLeagueId.value}&limit=20`)
    const data = await res.json()
    equipList.value = data?.data?.data || []
  } catch { equipList.value = [] }
  loading.value = false
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

onMounted(init)
</script>

<style scoped>
.equip-console {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.18);
  --mono-line-strong: rgba(0, 0, 0, 0.32);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --card-bg: #fff;
  --card-hover: rgba(0, 0, 0, 0.02);
  --stat-bg: rgba(0, 0, 0, 0.03);
  --input-bg: rgba(255, 255, 255, 0.7);
  --corner-border: rgba(0, 0, 0, 0.18);
  --dialog-bg: #fff;
  --rate-fill: #1a1a1a;
  max-width: none;
  min-height: 100vh;
  padding: 28px 32px 36px 87px;
  color: var(--mono-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
}
.equip-console.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.18);
  --mono-line-strong: rgba(255, 255, 255, 0.32);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --card-bg: rgba(255, 255, 255, 0.06);
  --card-hover: rgba(255, 255, 255, 0.03);
  --stat-bg: rgba(255, 255, 255, 0.04);
  --input-bg: rgba(255, 255, 255, 0.06);
  --corner-border: rgba(255, 255, 255, 0.18);
  --dialog-bg: #1a1a1a;
  --rate-fill: #e8e8e8;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

/* command strip */
.command-strip {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border: 1px solid var(--mono-line); background: var(--mono-panel);
}
.command-strip::before {
  content: ""; position: absolute; left: -1px; top: -1px; width: 36px; height: 36px;
  border-left: 2px solid var(--corner-border); border-top: 2px solid var(--corner-border); pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px; display: grid; place-items: center;
  border: 1px solid var(--mono-line-strong); color: var(--mono-ink); background: var(--stat-bg);
  font-size: 20px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--mono-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--mono-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }
.league-select { width: 220px; }

/* theme toggle */
.theme-toggle {
  display: inline-flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--mono-soft); font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--mono-ink); }
.toggle-track {
  position: relative; width: 32px; height: 16px; border-radius: 8px;
  background: var(--mono-line-strong); transition: background 0.2s;
}
.toggle-track.on { background: var(--mono-ink); }
.toggle-thumb {
  position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%;
  background: var(--mono-bg); transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }

/* query bar */
.query-bar {
  margin-top: 18px; display: flex; align-items: center; gap: 16px;
}
.mode-tabs { display: flex; border: 1px solid var(--mono-line); background: var(--stat-bg); }
.mode-tabs button {
  padding: 8px 18px; border: none; background: transparent;
  color: var(--mono-soft); font-size: 13px; font-weight: 600; cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.mode-tabs button:hover { color: var(--mono-ink); }
.mode-tabs button.active { background: var(--mono-ink); color: var(--mono-bg); }
.breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.crumb-back {
  padding: 4px 12px;
  border: 1px solid var(--mono-line);
  background: var(--stat-bg);
  color: var(--mono-soft);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.crumb-back:hover { background: var(--mono-ink); color: var(--mono-bg); }
.crumb-sep { color: var(--mono-dim); }
.crumb-current { font-weight: 700; color: var(--mono-ink); }

.search-hint { display: flex; gap: 8px; max-width: 360px; margin-top: 16px; }
.run-btn {
  padding: 0 18px; border: 1px solid var(--mono-line-strong);
  background: var(--mono-ink); color: var(--mono-bg);
  font-size: 13px; font-weight: 700; cursor: pointer;
}
.run-btn:hover { opacity: 0.85; }

/* content */
.equip-content { margin-top: 18px; }

.loading-hint, .empty-hint {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; padding: 80px 0; color: var(--mono-dim); font-size: 14px;
}
.spinner {
  width: 24px; height: 24px; border: 2px solid var(--mono-line);
  border-top-color: var(--mono-ink); border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.result-title {
  margin: 0 0 12px; font-size: 14px; font-weight: 700; color: var(--mono-ink);
  padding-bottom: 8px; border-bottom: 1px solid var(--mono-line);
}

/* equip table — single container, all columns aligned */
.equip-table {
  --equip-table-columns: 48px minmax(240px, 1fr) 112px 112px 240px;
  border: 1px solid var(--mono-line-strong);
  background: var(--card-bg);
}
.equip-row {
  display: grid;
  grid-template-columns: var(--equip-table-columns);
  align-items: center;
  padding: 0 14px;
  height: 44px;
  border-bottom: 1px solid var(--mono-line);
  cursor: pointer;
  transition: background 0.15s;
}
.equip-row:last-child { border-bottom: none; }
.equip-row:hover { background: var(--card-hover); }
.header-row {
  height: 36px;
  font-size: 11px; font-weight: 700; color: var(--mono-dim);
  letter-spacing: 1px; text-transform: uppercase;
  background: var(--stat-bg);
  cursor: default;
  border-bottom: 1px solid var(--mono-line-strong);
}
.header-row:hover { background: var(--stat-bg); }
.col { min-width: 0; box-sizing: border-box; }
.col-rank { text-align: center; }
.col-equip { display: flex; align-items: center; gap: 10px; min-width: 0; padding-right: 16px; }
.col-num { text-align: right; padding: 0 12px; font-variant-numeric: tabular-nums; }
.header-row .col-secondary { transform: translateX(-2ch); }
.equip-row:not(.header-row) .col-primary { transform: translateX(-3ch); }
.equip-row:not(.header-row) .col-secondary { transform: translateX(-5.5ch); }
.col-bar { width: 220px; display: flex; align-items: center; gap: 12px; padding-left: 12px; }
.header-row .col-num { text-align: center; }
.header-row .col-bar { justify-content: center; padding-left: 0; transform: translateX(-8ch); }

.equip-icon {
  width: 26px; height: 26px; border: 1px solid var(--mono-line);
  border-radius: 4px; object-fit: cover; flex-shrink: 0;
}
.equip-name { font-weight: 600; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rate-track {
  display: block; width: 100px; height: 6px;
  background: var(--stat-bg); border: 1px solid var(--mono-line); flex-shrink: 0;
}
.rate-fill { display: block; height: 100%; background: var(--rate-fill); }
.rate-text { font-size: 12px; font-weight: 700; color: var(--mono-soft); font-variant-numeric: tabular-nums; flex-shrink: 0; min-width: 36px; }

/* entity grid (hero list) */
.entity-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 8px;
  margin-top: 16px;
}
.entity-card {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 14px 8px; border: 1px solid var(--mono-line); background: var(--card-bg);
  cursor: pointer; transition: border-color 0.15s, background 0.15s;
}
.entity-card:hover { border-color: var(--mono-ink); background: var(--card-hover); }
.entity-img { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.entity-placeholder {
  display: grid; place-items: center;
  background: var(--stat-bg); border: 1px solid var(--mono-line);
  font-size: 16px; font-weight: 900; color: var(--mono-dim);
}
.entity-name { font-size: 12px; font-weight: 700; color: var(--mono-ink); text-align: center; }
.entity-meta { font-size: 10px; color: var(--mono-dim); }

/* detail dialog */
.detail-body { display: flex; flex-direction: column; gap: 20px; }
.detail-info {
  display: flex; align-items: flex-start; gap: 14px;
  padding-bottom: 16px; border-bottom: 1px solid var(--mono-line);
}
.detail-icon {
  width: 48px; height: 48px; border: 1px solid var(--mono-line);
  border-radius: 6px; object-fit: cover;
}
.detail-info h3 { margin: 0; font-size: 18px; font-weight: 900; color: var(--mono-ink); }
.detail-desc { margin: 4px 0 0; font-size: 12px; color: var(--mono-dim); line-height: 1.6; }
.detail-section h4 { margin: 0 0 10px; font-size: 13px; font-weight: 800; color: var(--mono-ink); }
.position-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.position-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 16px; border: 1px solid var(--mono-line); background: var(--stat-bg); min-width: 72px;
}
.pos-name { font-size: 12px; font-weight: 700; color: var(--mono-ink); }
.pos-count { font-size: 11px; color: var(--mono-dim); }
.hero-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.hero-card {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border: 1px solid var(--mono-line); background: var(--stat-bg);
}
.hero-img { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.hero-name { font-size: 13px; font-weight: 600; color: var(--mono-ink); }
.hero-count { font-size: 11px; color: var(--mono-dim); }

.skeleton-wrap { padding: 8px 0; }
.skeleton-row {
  height: 16px; border-radius: 6px;
  background: linear-gradient(90deg, var(--stat-bg) 25%, var(--card-hover) 50%, var(--stat-bg) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s ease-in-out infinite;
}
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* Element Plus overrides */
:deep(.el-dialog) {
  border: 1px solid var(--mono-line-strong) !important;
  border-radius: 0 !important; background: var(--dialog-bg) !important;
}
:deep(.el-dialog__title) { color: var(--mono-ink) !important; font-weight: 900 !important; }
:deep(.el-dialog__header) { border-bottom: 1px solid var(--mono-line); padding-bottom: 12px; margin: 0; }
:deep(.el-dialog__body) { max-height: 60vh; overflow-y: auto; padding: 16px 20px; }
:deep(.el-select__wrapper) {
  border-radius: 0 !important; background: var(--input-bg) !important;
  box-shadow: 0 0 0 1px var(--mono-line) inset !important; min-height: 36px;
}
:deep(.el-select__placeholder), :deep(.el-select__selected-item .el-select__tags-text) {
  color: var(--mono-ink) !important; font-weight: 600;
}
:deep(.el-select__caret) { color: var(--mono-soft) !important; }
:deep(.el-input__wrapper) {
  border-radius: 0 !important; background: var(--input-bg) !important;
  box-shadow: 0 0 0 1px var(--mono-line) inset !important;
}

@media (max-width: 767px) {
  .equip-console {
    min-height: 100dvh;
    padding: 12px 12px calc(78px + env(safe-area-inset-bottom));
  }
  .command-strip {
    min-height: 56px;
    padding: 10px 12px;
    gap: 8px;
  }
  .brand-mark { width: 34px; height: 34px; font-size: 16px; }
  h1 { font-size: 17px; }
  .league-select { width: 132px; }
  .theme-toggle small { display: none; }
  .query-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    padding: 12px 0;
  }
  .mode-tabs {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .mode-tabs button {
    min-height: 38px;
    padding: 6px 4px;
    font-size: 12px;
  }
  .search-hint {
    max-width: none;
    width: 100%;
    margin-top: 0;
  }
  .equip-table {
    --equip-table-columns: 34px minmax(92px, 1fr) 68px 46px;
    overflow-x: hidden;
  }
  .equip-row {
    min-width: 0;
    padding: 0 6px;
  }
  .col-secondary {
    display: none;
  }
  .header-row .col-secondary,
  .equip-row:not(.header-row) .col-primary,
  .equip-row:not(.header-row) .col-secondary,
  .header-row .col-bar {
    transform: none;
  }
  .col-num {
    padding: 0 4px;
    text-align: center;
  }
  .col-bar {
    width: auto;
    justify-content: flex-end;
    gap: 0;
    padding-left: 0;
  }
  .rate-track {
    display: none;
  }
  .rate-text {
    display: inline-block;
    min-width: 34px;
    text-align: right;
    font-size: 11px;
  }
  .equip-icon {
    width: 24px;
    height: 24px;
  }
  .equip-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .entity-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .entity-card {
    min-height: 92px;
  }
  .detail-info {
    gap: 10px;
  }
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
