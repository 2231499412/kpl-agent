<template>
  <main class="app-shell has-sidebar bp-console" :class="`theme-${theme}`">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">BP</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>BP 分析</h1>
        </div>
      </div>
      <div class="status-line">
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <section class="bp-body">
      <!-- 控制栏 -->
      <div class="ctrl-bar">
        <el-select v-model="selectedLeagueId" class="league-select" placeholder="选择赛事" @change="loadData">
          <el-option v-for="l in leagues" :key="l.leagueId" :label="l.leagueName" :value="l.leagueId" />
        </el-select>

        <div class="sort-group">
          <button v-for="s in sortOptions" :key="s.value" :class="['sort-btn', { active: sortBy === s.value }]" @click="changeSort(s.value)">{{ s.label }}</button>
        </div>

        <div class="tier-legend">
          <span class="tier-tag tier-s">S</span>
          <span class="tier-tag tier-a">A</span>
          <span class="tier-tag tier-b">B</span>
          <span class="tier-tag tier-c">C</span>
        </div>
      </div>

      <!-- 统计摘要 -->
      <div class="summary-row" v-if="heroList.length">
        <div class="summary-card">
          <span class="s-val">{{ heroList.length }}</span>
          <span class="s-label">英雄总数</span>
        </div>
        <div class="summary-card">
          <span class="s-val">{{ sTierCount }}</span>
          <span class="s-label">S 级 (BP>80%)</span>
        </div>
        <div class="summary-card">
          <span class="s-val">{{ aTierCount }}</span>
          <span class="s-label">A 级 (BP>50%)</span>
        </div>
        <div class="summary-card">
          <span class="s-val">{{ avgWinRate }}</span>
          <span class="s-label">平均胜率</span>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading-wrap">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>

      <!-- 英雄 BP 列表 -->
      <div v-else-if="heroList.length" class="hero-grid">
        <TransitionGroup name="hero-sort">
        <div v-for="(hero, idx) in sortedList" :key="hero.heroId" class="hero-row" @click="openDetail(hero)">
          <div class="row-rank">
            <span class="rank-num">{{ idx + 1 }}</span>
            <span :class="['tier-badge', 'tier-' + hero._tier]">{{ hero._tier }}</span>
          </div>
          <div class="row-hero">
            <img :src="hero.heroIcon || ('https://res.edata.qq.com/sgame/static/images/hero/' + hero.heroId + '.jpg')" class="hero-img" />
            <span class="hero-name">{{ hero.heroName }}</span>
          </div>
          <div class="row-bp-bar">
            <div class="bp-track">
              <div class="bp-fill pick" :style="{ width: pct(hero.pickRate) + '%' }"></div>
              <div class="bp-fill ban" :style="{ width: pct(hero.banRate) + '%' }"></div>
            </div>
            <span class="bp-pct">{{ pct(hero.pickRate + hero.banRate) }}%</span>
          </div>
          <div class="row-stats">
            <div class="stat-cell">
              <span class="stat-val">{{ pct(hero.pickRate) }}%</span>
              <span class="stat-label">Pick</span>
            </div>
            <div class="stat-cell">
              <span class="stat-val">{{ pct(hero.banRate) }}%</span>
              <span class="stat-label">Ban</span>
            </div>
            <div class="stat-cell">
              <span class="stat-val" :class="{ 'wr-high': hero.winRate >= 0.55, 'wr-low': hero.winRate < 0.45 }">{{ pct(hero.winRate) }}%</span>
              <span class="stat-label">胜率</span>
            </div>
            <div class="stat-cell">
              <span class="stat-val">{{ hero.battleCount }}</span>
              <span class="stat-label">出场</span>
            </div>
          </div>
        </div>
        </TransitionGroup>
      </div>

      <div v-else-if="!loading" class="empty-state">
        <span>请选择赛事查看 BP 数据</span>
      </div>
    </section>

    <!-- 英雄详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailHero?.heroName + ' - BP 详情'" width="640" class="detail-dialog" destroy-on-close>
      <template v-if="detailHero">
        <div class="detail-profile">
          <div class="profile-card">
            <div class="profile-header">
              <img :src="detailHero.heroIcon || ('https://res.edata.qq.com/sgame/static/images/hero/' + detailHero.heroId + '.jpg')" class="profile-avatar" />
              <div>
                <b>{{ detailHero.heroName }}</b>
                <small>出场 {{ detailHero.battleCount }} 局</small>
              </div>
            </div>
            <div class="stat-grid">
              <div class="stat-item"><span class="sv">{{ pct(detailHero.pickRate) }}%</span><span class="sl">Pick 率</span></div>
              <div class="stat-item"><span class="sv">{{ pct(detailHero.banRate) }}%</span><span class="sl">Ban 率</span></div>
              <div class="stat-item"><span class="sv">{{ pct(detailHero.winRate) }}%</span><span class="sl">胜率</span></div>
              <div class="stat-item"><span class="sv">{{ detailHero.avgKda?.toFixed(2) }}</span><span class="sl">KDA</span></div>
              <div class="stat-item"><span class="sv">{{ detailHero.avgKill?.toFixed(1) }}</span><span class="sl">场均击杀</span></div>
              <div class="stat-item"><span class="sv">{{ detailHero.avgDeath?.toFixed(1) }}</span><span class="sl">场均死亡</span></div>
            </div>
          </div>
        </div>

        <div class="equip-section" v-if="detailPlayers.length">
          <h4>高胜率选手</h4>
          <div class="player-list">
            <div v-for="p in detailPlayers" :key="p.playerName" class="player-chip">
              <span class="p-name">{{ p.playerName }}</span>
              <span class="p-team">{{ p.teamName }}</span>
              <span class="p-wr" :class="{ 'wr-high': p.winRate >= 60, 'wr-low': p.winRate < 40 }">{{ p.winRate }}%</span>
              <span class="p-games">{{ p.games }}场</span>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTheme, setTheme } from '../utils/theme'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const leagues = ref([])
const selectedLeagueId = ref('')
const heroList = ref([])
const loading = ref(false)
const sortBy = ref('bp')
const detailVisible = ref(false)
const detailHero = ref(null)
const detailPlayers = ref([])

const sortOptions = [
  { label: 'BP 率', value: 'bp' },
  { label: 'Pick 率', value: 'pick' },
  { label: 'Ban 率', value: 'ban' },
  { label: '胜率', value: 'win' },
  { label: '出场', value: 'count' },
]

function pct(v) {
  if (v == null) return '0.0'
  return (Number(v) * 100).toFixed(1)
}

function getTier(hero) {
  const bp = (hero.pickRate || 0) + (hero.banRate || 0)
  if (bp >= 0.8) return 's'
  if (bp >= 0.5) return 'a'
  if (bp >= 0.2) return 'b'
  return 'c'
}

const sortedList = computed(() => {
  const list = heroList.value.map(h => ({ ...h, _tier: getTier(h) }))
  const key = sortBy.value
  return list.sort((a, b) => {
    if (key === 'bp') return ((b.pickRate || 0) + (b.banRate || 0)) - ((a.pickRate || 0) + (a.banRate || 0))
    if (key === 'pick') return (b.pickRate || 0) - (a.pickRate || 0)
    if (key === 'ban') return (b.banRate || 0) - (a.banRate || 0)
    if (key === 'win') return (b.winRate || 0) - (a.winRate || 0)
    return (b.battleCount || 0) - (a.battleCount || 0)
  })
})

const sTierCount = computed(() => sortedList.value.filter(h => h._tier === 's').length)
const aTierCount = computed(() => sortedList.value.filter(h => h._tier === 'a').length)
const avgWinRate = computed(() => {
  if (!heroList.value.length) return '0.0'
  const sum = heroList.value.reduce((s, h) => s + (h.winRate || 0), 0)
  return (sum / heroList.value.length * 100).toFixed(1) + '%'
})

function changeSort(val) {
  sortBy.value = val
}

async function request(path) {
  const res = await fetch(path, { headers: { 'Content-Type': 'application/json' } })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = await res.json()
  if (body.success === false) throw new Error(body.message || '请求失败')
  return body.data ?? body
}

async function loadLeagues() {
  try {
    leagues.value = await request('/api/leagues?limit=30')
    if (leagues.value.length && !selectedLeagueId.value) {
      selectedLeagueId.value = leagues.value[0].leagueId
    }
  } catch (e) {
    ElMessage.error('加载赛事列表失败')
  }
}

async function loadData() {
  if (!selectedLeagueId.value) return
  loading.value = true
  try {
    const result = await request(`/api/query/hero/top?sort=pick&leagueId=${selectedLeagueId.value}`)
    const data = Array.isArray(result?.data) ? result.data : Array.isArray(result) ? result : []
    heroList.value = data
  } catch (e) {
    ElMessage.error('加载英雄数据失败: ' + e.message)
    heroList.value = []
  } finally {
    loading.value = false
  }
}

async function openDetail(hero) {
  detailHero.value = hero
  detailPlayers.value = []
  detailVisible.value = true
  try {
    const result = await request(`/api/query/hero/players?name=${encodeURIComponent(hero.heroName)}&leagueId=${selectedLeagueId.value}`)
    const data = Array.isArray(result?.data) ? result.data : Array.isArray(result) ? result : []
    detailPlayers.value = data.slice(0, 10)
  } catch {
    // ignore
  }
}

onMounted(async () => {
  await loadLeagues()
  if (selectedLeagueId.value) await loadData()
})
</script>

<style scoped>
.bp-console {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.18);
  --mono-line-strong: rgba(0, 0, 0, 0.32);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --card-bg: #fff;
  --stat-bg: rgba(0, 0, 0, 0.03);
  --corner-border: rgba(0, 0, 0, 0.18);
  --accent: #1a1a1a;
  --tier-s: #c0392b;
  --tier-a: #e67e22;
  --tier-b: #2980b9;
  --tier-c: #7f8c8d;
  --pick-color: #27ae60;
  --ban-color: #c0392b;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 28px 32px 0 87px;
  color: var(--mono-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
}
.bp-console.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.18);
  --mono-line-strong: rgba(255, 255, 255, 0.32);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --card-bg: rgba(255, 255, 255, 0.06);
  --stat-bg: rgba(255, 255, 255, 0.04);
  --corner-border: rgba(255, 255, 255, 0.18);
  --accent: #e8e8e8;
  --tier-s: #e74c3c;
  --tier-a: #f39c12;
  --tier-b: #3498db;
  --tier-c: #95a5a6;
  --pick-color: #2ecc71;
  --ban-color: #e74c3c;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.command-strip {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border: 1px solid var(--mono-line); background: var(--mono-panel);
  flex-shrink: 0;
}
.command-strip::before {
  content: ""; position: absolute; left: -1px; top: -1px; width: 36px; height: 36px;
  border-left: 2px solid var(--corner-border); border-top: 2px solid var(--corner-border); pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px; display: grid; place-items: center;
  border: 1px solid var(--mono-line-strong); color: var(--mono-ink); background: var(--stat-bg);
  font-size: 16px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--mono-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--mono-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }
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

/* body */
.bp-body {
  flex: 1; overflow-y: auto; padding: 20px 0 32px;
}

/* 控制栏 */
.ctrl-bar {
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  padding: 14px 18px; border: 1px solid var(--mono-line); background: var(--mono-panel);
  margin-bottom: 16px;
}
.league-select { width: 200px; }
.sort-group { display: flex; gap: 0; }
.sort-btn {
  padding: 6px 14px; border: 1px solid var(--mono-line); background: var(--card-bg);
  color: var(--mono-soft); font-size: 12px; font-weight: 600; cursor: pointer;
  transition: all 0.15s; margin-left: -1px;
}
.sort-btn:first-child { margin-left: 0; }
.sort-btn:hover { color: var(--mono-ink); background: var(--stat-bg); }
.sort-btn.active {
  background: var(--mono-ink); color: var(--mono-bg); border-color: var(--mono-ink);
  z-index: 1; position: relative;
}
.tier-legend { display: flex; gap: 6px; margin-left: auto; }
.tier-tag {
  width: 22px; height: 22px; display: grid; place-items: center;
  font-size: 11px; font-weight: 900; color: #fff; border-radius: 2px;
}
.tier-tag.tier-s { background: var(--tier-s); }
.tier-tag.tier-a { background: var(--tier-a); }
.tier-tag.tier-b { background: var(--tier-b); }
.tier-tag.tier-c { background: var(--tier-c); }

/* 摘要 */
.summary-row {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px;
}
.summary-card {
  padding: 14px 16px; border: 1px solid var(--mono-line); background: var(--mono-panel);
  display: flex; flex-direction: column; gap: 2px;
}
.s-val { font-size: 22px; font-weight: 900; color: var(--mono-ink); }
.s-label { font-size: 11px; color: var(--mono-dim); font-weight: 600; letter-spacing: 0.5px; }

/* loading */
.loading-wrap {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; padding: 60px 0; color: var(--mono-dim);
}
.loading-spinner {
  width: 28px; height: 28px; border: 3px solid var(--mono-line);
  border-top-color: var(--mono-ink); border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex; align-items: center; justify-content: center;
  padding: 60px 0; color: var(--mono-dim); font-size: 14px;
}

/* hero grid */
.hero-grid {
  display: flex; flex-direction: column; gap: 2px;
}

/* 切换排序动画 */
.hero-sort-move { transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1); }
.hero-sort-enter-active { transition: all 0.35s ease-out; }
.hero-sort-leave-active { transition: all 0.25s ease-in; position: absolute; width: calc(100% - 2px); }
.hero-sort-enter-from { opacity: 0; transform: translateY(-8px); }
.hero-sort-leave-to { opacity: 0; transform: translateY(8px); }

.hero-row {
  display: grid; grid-template-columns: 56px 140px 1fr 240px;
  align-items: center; gap: 12px; padding: 10px 14px;
  border: 1px solid var(--mono-line); background: var(--card-bg);
  cursor: pointer; transition: all 0.15s;
}
.hero-row:hover {
  border-color: var(--mono-ink); box-shadow: inset 3px 0 0 var(--mono-ink);
}
.row-rank { display: flex; align-items: center; gap: 6px; }
.rank-num { font-size: 14px; font-weight: 800; color: var(--mono-dim); min-width: 20px; text-align: right; }
.tier-badge {
  width: 20px; height: 20px; display: grid; place-items: center;
  font-size: 10px; font-weight: 900; color: #fff; border-radius: 2px;
}
.tier-badge.tier-s { background: var(--tier-s); }
.tier-badge.tier-a { background: var(--tier-a); }
.tier-badge.tier-b { background: var(--tier-b); }
.tier-badge.tier-c { background: var(--tier-c); }

.row-hero { display: flex; align-items: center; gap: 8px; }
.hero-img { width: 32px; height: 32px; border: 1px solid var(--mono-line); object-fit: cover; }
.hero-name { font-size: 13px; font-weight: 700; color: var(--mono-ink); }

.row-bp-bar { display: flex; align-items: center; gap: 8px; }
.bp-track {
  flex: 1; height: 18px; background: var(--stat-bg); border: 1px solid var(--mono-line);
  display: flex; overflow: hidden;
}
.bp-fill {
  height: 100%;
  transition: width 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.bp-fill.pick { background: var(--pick-color); opacity: 0.7; }
.bp-fill.ban { background: var(--ban-color); opacity: 0.7; }
.bp-pct { font-size: 13px; font-weight: 800; color: var(--mono-ink); min-width: 42px; text-align: right; }

.row-stats { display: flex; gap: 0; }
.stat-cell {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 1px;
  padding: 2px 0; border-left: 1px solid var(--mono-line);
}
.stat-cell:first-child { border-left: none; }
.stat-val { font-size: 13px; font-weight: 700; color: var(--mono-ink); }
.stat-label { font-size: 9px; color: var(--mono-dim); font-weight: 600; letter-spacing: 0.5px; text-transform: uppercase; }
.wr-high { color: #27ae60; }
.wr-low { color: #c0392b; }

/* detail dialog */
.detail-dialog { border-radius: 0; }
.detail-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--mono-line); padding: 14px 20px; margin: 0;
}
.detail-dialog :deep(.el-dialog__body) { padding: 20px; }
.detail-profile { margin-bottom: 16px; }
.profile-card { padding: 16px; border: 1px solid var(--mono-line); background: var(--stat-bg); }
.profile-header { display: flex; align-items: center; gap: 14px; margin-bottom: 14px; }
.profile-avatar { width: 48px; height: 48px; border: 1px solid var(--mono-line); object-fit: cover; }
.profile-header b { font-size: 16px; display: block; }
.profile-header small { color: var(--mono-dim); font-size: 12px; }
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.stat-item {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 8px; border: 1px solid var(--mono-line); background: var(--card-bg);
}
.sv { font-size: 16px; font-weight: 800; color: var(--mono-ink); }
.sl { font-size: 10px; color: var(--mono-dim); font-weight: 600; }

.equip-section { margin-top: 16px; }
.equip-section h4 { font-size: 12px; font-weight: 800; color: var(--mono-dim); letter-spacing: 1px; text-transform: uppercase; margin: 0 0 10px; }
.player-list { display: flex; flex-direction: column; gap: 4px; }
.player-chip {
  display: grid; grid-template-columns: 1fr 1fr 60px 60px;
  align-items: center; gap: 8px; padding: 8px 10px;
  border: 1px solid var(--mono-line); background: var(--card-bg);
  font-size: 12px;
}
.p-name { font-weight: 700; color: var(--mono-ink); }
.p-team { color: var(--mono-soft); }
.p-wr { font-weight: 700; text-align: center; }
.p-games { color: var(--mono-dim); text-align: center; }

/* element plus overrides */
.bp-console :deep(.el-select__wrapper) {
  background: var(--card-bg) !important; border: 1px solid var(--mono-line-strong) !important;
  box-shadow: none !important; color: var(--mono-ink) !important; border-radius: 0;
}
.bp-console :deep(.el-select__wrapper.is-focused) { border-color: var(--mono-ink) !important; }
.bp-console :deep(.el-select__placeholder) { color: var(--mono-soft) !important; }
.bp-console :deep(.el-select__caret) { color: var(--mono-soft) !important; }

@media (max-width: 767px) {
  .bp-console {
    width: 100%;
    min-width: 0;
    height: 100dvh;
    padding: 10px 8px calc(82px + env(safe-area-inset-bottom));
    overflow: hidden;
  }

  .command-strip {
    min-width: 0;
    padding: 10px 12px;
  }

  .brand-block,
  .status-line {
    min-width: 0;
  }

  .brand-mark {
    width: 34px;
    height: 34px;
    min-width: 34px;
    font-size: 14px;
  }

  h1 {
    font-size: 18px;
  }

  .theme-toggle small {
    display: none;
  }

  .bp-body {
    min-width: 0;
    padding: 10px 0 0;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .ctrl-bar {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 12px;
    margin-bottom: 10px;
  }

  .league-select {
    width: 100%;
  }

  .sort-group {
    width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .sort-btn {
    flex: 0 0 auto;
    padding: 8px 12px;
    white-space: nowrap;
  }

  .tier-legend {
    margin-left: 0;
    justify-content: space-between;
  }

  .tier-tag {
    flex: 1;
    height: 24px;
  }

  .summary-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin-bottom: 10px;
  }

  .summary-card {
    min-width: 0;
    padding: 12px;
  }

  .s-val {
    font-size: 20px;
  }

  .s-label {
    overflow: hidden;
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .hero-grid {
    gap: 8px;
  }

  .hero-sort-leave-active {
    width: 100%;
  }

  .hero-row {
    grid-template-columns: 48px minmax(0, 1fr);
    grid-template-areas:
      "rank hero"
      "bar bar"
      "stats stats";
    gap: 10px;
    min-width: 0;
    padding: 12px;
    overflow: hidden;
    border-color: var(--mono-line);
  }

  .hero-row:hover {
    box-shadow: none;
  }

  .row-rank {
    grid-area: rank;
    min-width: 0;
  }

  .rank-num {
    min-width: 18px;
    text-align: left;
  }

  .row-hero {
    grid-area: hero;
    min-width: 0;
  }

  .hero-img {
    width: 36px;
    height: 36px;
    flex: 0 0 auto;
  }

  .hero-name {
    min-width: 0;
    overflow: hidden;
    font-size: 15px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-bp-bar {
    grid-area: bar;
    min-width: 0;
  }

  .bp-track {
    min-width: 0;
  }

  .bp-pct {
    min-width: 46px;
  }

  .row-stats {
    grid-area: stats;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    width: 100%;
    min-width: 0;
    border-top: 1px solid var(--mono-line);
    padding-top: 8px;
  }

  .stat-cell {
    min-width: 0;
    padding: 0 4px;
  }

  .stat-val {
    font-size: 12px;
  }

  .stat-label {
    font-size: 8px;
  }

  .detail-dialog :deep(.el-dialog__body) {
    max-height: calc(82dvh - 56px);
    overflow-y: auto;
    padding: 12px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .player-chip {
    grid-template-columns: 1fr auto;
    gap: 6px;
  }

  .p-team,
  .p-games {
    text-align: left;
  }
}
</style>

<style>
/* 下拉面板全局样式（Teleport 到 body，scoped 覆盖不到） */
.el-select__popper {
  border: 1px solid rgba(0,0,0,0.3) !important;
  border-radius: 0 !important;
  box-shadow: 4px 4px 0 rgba(0,0,0,0.08) !important;
  background: #fff !important;
}
.el-select-dropdown__list { padding: 4px 0 !important; }
.el-select-dropdown__item {
  padding: 8px 16px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  color: #1a1a1a !important;
  border-radius: 0 !important;
  transition: background 0.1s, padding-left 0.15s;
}
.el-select-dropdown__item.is-hovering {
  background: rgba(0,0,0,0.04) !important;
  padding-left: 20px !important;
}
.el-select-dropdown__item.is-selected {
  font-weight: 800 !important;
  color: #1a1a1a !important;
  position: relative;
}
.el-select-dropdown__item.is-selected::after {
  content: '';
  position: absolute;
  left: 6px; top: 50%; transform: translateY(-50%);
  width: 3px; height: 14px;
  background: #1a1a1a;
}

/* 暗色主题下拉 */
.theme-dark .el-select__popper {
  border-color: rgba(255,255,255,0.25) !important;
  background: #1a1a1a !important;
  box-shadow: 4px 4px 0 rgba(0,0,0,0.3) !important;
}
.theme-dark .el-select-dropdown__item {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-hovering {
  background: rgba(255,255,255,0.06) !important;
}
.theme-dark .el-select-dropdown__item.is-selected {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-selected::after {
  background: #e8e8e8;
}
</style>
