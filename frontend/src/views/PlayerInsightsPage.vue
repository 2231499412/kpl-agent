<template>
  <main class="player-insights" :class="`theme-${theme}`">
    <section class="topbar">
      <div class="title-block">
        <span>KPL PLAYER INTELLIGENCE</span>
        <h1>选手数据详情</h1>
      </div>
      <div class="controls">
        <el-select v-model="selectedLeagueId" filterable placeholder="选择赛事" @change="onLeagueChange">
          <el-option v-for="league in leagues" :key="league.leagueId" :label="league.leagueName" :value="league.leagueId" />
        </el-select>
        <el-select
          v-model="selectedPlayer"
          filterable
          remote
          reserve-keyword
          :remote-method="filterPlayers"
          :loading="playerLoading"
          placeholder="搜索选手"
          @change="loadDetail"
        >
          <el-option v-for="player in visiblePlayers" :key="playerKey(player)" :label="player.playerName" :value="player.playerName">
            <div class="player-option">
              <img v-if="player.playerIcon || player.teamIcon" :src="player.playerIcon || player.teamIcon" :alt="player.playerName">
              <span>{{ player.playerName }}</span>
              <em>{{ player.teamName }} · {{ player.positionDesc || '-' }}</em>
            </div>
          </el-option>
        </el-select>
        <el-button :icon="Refresh" class="refresh-btn" :loading="loading" @click="refreshPage">刷新</el-button>
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <section v-if="loading && !detail" class="state-panel">
      <div class="spinner" />
      <span>正在生成选手赛季档案...</span>
    </section>

    <section v-else-if="errorText" class="state-panel error">
      <strong>{{ errorText }}</strong>
      <span>请换一个选手或赛事再试。</span>
    </section>

    <template v-else-if="detail">
      <section class="profile-strip">
        <article class="profile-card">
          <img v-if="player.playerIcon" :src="player.playerIcon" :alt="player.playerName" @error="hideBroken">
          <div class="avatar-fallback" v-else>{{ shortName(player.playerName).slice(0, 1) }}</div>
          <div class="profile-main">
            <span>{{ currentLeagueName }}</span>
            <h2>{{ shortName(player.playerName) }}</h2>
            <p>{{ player.teamName || '-' }} · {{ player.positionDesc || '-' }}</p>
          </div>
          <div class="status-score">
            <strong>{{ statusScore }}</strong>
            <span>综合状态分</span>
          </div>
        </article>

        <aside class="metric-grid">
          <div class="metric-card"><span>本赛季场次</span><strong>{{ player.battleCount || 0 }}</strong><em>同位置 #{{ rankOf('avgKda') || '-' }}</em></div>
          <div class="metric-card win"><span>胜率</span><strong>{{ formatPercent(player.winRate) }}</strong><em>排名 #{{ rankOf('winRate') || '-' }}</em></div>
          <div class="metric-card"><span>KDA</span><strong>{{ fixed(player.avgKda, 2) }}</strong><em>{{ fixed(player.avgKill, 1) }}/{{ fixed(player.avgDeath, 1) }}/{{ fixed(player.avgAssist, 1) }}</em></div>
          <div class="metric-card accent"><span>场均经济</span><strong>{{ formatCompact(player.avgGold) }}</strong><em>排名 #{{ rankOf('avgGold') || '-' }}</em></div>
          <div class="metric-card"><span>参团率</span><strong>{{ formatPercent(player.avgParticipationRate) }}</strong><em>排名 #{{ rankOf('avgParticipationRate') || '-' }}</em></div>
          <div class="metric-card danger"><span>近期 5 场</span><strong>{{ formatPercent(recent5.winRate) }}</strong><em>KDA {{ fixed(recent5.avgKda, 2) }}</em></div>
        </aside>
      </section>

      <section class="analysis-grid">
        <article class="panel trend-panel">
          <PanelTitle tag="FORM" title="近期状态趋势" note="最近 5 / 10 / 赛季" />
          <div class="mini-stat-row">
            <div><span>最近5场</span><strong>{{ formatPercent(recent5.winRate) }}</strong><em>KDA {{ fixed(recent5.avgKda, 2) }}</em></div>
            <div><span>最近10场</span><strong>{{ formatPercent(recent10.winRate) }}</strong><em>KDA {{ fixed(recent10.avgKda, 2) }}</em></div>
            <div><span>赛季整体</span><strong>{{ formatPercent(player.winRate) }}</strong><em>KDA {{ fixed(player.avgKda, 2) }}</em></div>
          </div>
          <svg class="trend-chart" viewBox="0 0 420 150" role="img" aria-label="近期KDA趋势">
            <line v-for="y in [30, 70, 110]" :key="y" x1="10" :y1="y" x2="410" :y2="y" />
            <polyline :points="trendLine" />
            <circle v-for="point in trendDots" :key="point.key" :cx="point.x" :cy="point.y" :class="{ win: point.won }" r="4" />
          </svg>
        </article>

        <article class="panel hero-panel">
          <PanelTitle tag="HERO POOL" title="英雄池分析" note="胜率 / 熟练度 / 标签" />
          <div class="hero-list">
            <div v-for="hero in heroPool" :key="hero.heroId" class="hero-row" :class="{ signature: isSignatureHero(hero) }">
              <img :src="heroIcon(hero)" :alt="hero.heroName" @error="hideBroken">
              <div>
                <strong>{{ hero.heroName }}</strong>
                <span>{{ hero.games }} 局 · KDA {{ fixed(hero.avgKda, 2) }}</span>
              </div>
              <b>{{ formatPercent(hero.winRate) }}</b>
              <em v-if="isSignatureHero(hero)">招牌</em>
            </div>
          </div>
        </article>

        <article class="panel compare-panel">
          <PanelTitle tag="LANE BENCHMARK" title="同位置横向对比" :note="`${player.positionDesc || '-'} · ${comparison.peerCount || 0} 人样本`" />
          <div class="compare-bars">
            <CompareBar label="胜率" :value="percentNumber(player.winRate)" :avg="percentNumber(compareAvg.winRate)" suffix="%" />
            <CompareBar label="KDA" :value="num(player.avgKda)" :avg="num(compareAvg.avgKda)" />
            <CompareBar label="击杀" :value="num(player.avgKill)" :avg="num(compareAvg.avgKill)" />
            <CompareBar label="助攻" :value="num(player.avgAssist)" :avg="num(compareAvg.avgAssist)" />
            <CompareBar label="经济" :value="num(player.avgGold)" :avg="num(compareAvg.avgGold)" compact />
          </div>
        </article>
      </section>

      <section class="bottom-grid">
        <article class="panel stage-panel">
          <PanelTitle tag="PATCH / STAGE" title="版本 / 阶段变化" note="按赛段聚合" />
          <div class="stage-list">
            <div v-for="stage in stageStats" :key="stage.stage || stage.stageDesc" class="stage-row">
              <strong>{{ stage.stageDesc || stage.stage || '未知赛段' }}</strong>
              <span>{{ stage.games }} 局</span>
              <b>{{ formatPercent(stage.winRate) }}</b>
              <em>KDA {{ fixed(stage.avgKda, 2) }}</em>
              <small>{{ stage.mainHero || '-' }}</small>
            </div>
          </div>
        </article>

        <article class="panel featured-panel">
          <PanelTitle tag="FEATURED GAMES" title="推荐代表场次" note="高光 / 逆风尽力 / 招牌英雄" />
          <div class="featured-list">
            <div v-for="(battle, index) in featuredBattles" :key="battle.battleId" class="battle-row">
              <b class="battle-rank">{{ index + 1 }}</b>
              <div class="battle-main">
                <span>{{ battle.matchStageDesc || '赛段' }} · 第{{ battle.battleSeq || '-' }}局</span>
                <strong>{{ battle.camp1TeamName }} {{ battle.camp1Score ?? '-' }} : {{ battle.camp2Score ?? '-' }} {{ battle.camp2TeamName }}</strong>
                <em>{{ battle.heroName }} · {{ battle.killNum }}/{{ battle.deathNum }}/{{ battle.assistNum }} · KDA {{ fixed(battle.kda, 2) }}</em>
              </div>
              <div class="battle-score">
                <strong>{{ fixed(battle.score, 1) }}</strong>
                <span :class="{ won: Number(battle.won) === 1 }">{{ Number(battle.won) === 1 ? '胜' : '负' }}</span>
              </div>
            </div>
          </div>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTheme, setTheme } from '../utils/theme'

const leagues = ref(JSON.parse(localStorage.getItem('kpl_leagues') || '[]'))
const players = ref([])
const visiblePlayers = ref([])
const selectedLeagueId = ref('')
const selectedPlayer = ref('')
const detail = ref(null)
const loading = ref(false)
const playerLoading = ref(false)
const errorText = ref('')
const theme = ref(getTheme())
watch(theme, (value) => setTheme(value))

const player = computed(() => detail.value?.player || {})
const recentGames = computed(() => detail.value?.recentGames || [])
const heroPool = computed(() => detail.value?.heroPool || [])
const stageStats = computed(() => detail.value?.stageStats || [])
const featuredBattles = computed(() => detail.value?.featuredBattles || [])
const comparison = computed(() => detail.value?.positionComparison || {})
const compareAvg = computed(() => comparison.value?.avg || {})
const compareRank = computed(() => comparison.value?.rank || {})
const currentLeagueName = computed(() => leagues.value.find(item => item.leagueId === selectedLeagueId.value)?.leagueName || '当前赛事')
const recent5 = computed(() => summarizeRecent(recentGames.value.slice(0, 5)))
const recent10 = computed(() => summarizeRecent(recentGames.value.slice(0, 10)))
const statusScore = computed(() => {
  const score = percentNumber(player.value.winRate) * 0.35
    + num(player.value.avgKda) * 7
    + percentNumber(player.value.avgParticipationRate) * 0.18
    + recent5.value.avgKda * 3
  return Math.max(0, Math.min(100, Math.round(score)))
})
const trendDots = computed(() => {
  const rows = [...recentGames.value].reverse()
  const maxKda = Math.max(1, ...rows.map(row => num(row.kda)))
  return rows.map((row, index) => ({
    key: `${row.battleId}-${index}`,
    x: 18 + index * (384 / Math.max(1, rows.length - 1)),
    y: 126 - (num(row.kda) / maxKda) * 104,
    won: Number(row.won) === 1,
  }))
})
const trendLine = computed(() => trendDots.value.map(point => `${point.x},${point.y}`).join(' '))

const PanelTitle = defineComponent({
  props: { tag: String, title: String, note: String },
  setup(props) {
    return () => h('div', { class: 'section-title' }, [
      h('div', [h('span', props.tag), h('h3', props.title)]),
      props.note ? h('small', props.note) : null,
    ])
  },
})

const CompareBar = defineComponent({
  props: { label: String, value: Number, avg: Number, suffix: String, compact: Boolean },
  setup(props) {
    return () => {
      const max = Math.max(props.value || 0, props.avg || 0, 1)
      return h('div', { class: 'compare-row' }, [
        h('span', props.label),
        h('div', { class: 'compare-track' }, [
          h('i', { class: 'self', style: { width: `${Math.min(100, ((props.value || 0) / max) * 100)}%` } }),
          h('i', { class: 'avg', style: { width: `${Math.min(100, ((props.avg || 0) / max) * 100)}%` } }),
        ]),
        h('b', `${formatMetric(props.value, props.compact)}${props.suffix || ''}`),
        h('em', `均 ${formatMetric(props.avg, props.compact)}${props.suffix || ''}`),
      ])
    }
  },
})

async function request(path, options = {}) {
  const response = await fetch(path, { headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }, ...options })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const body = await response.json()
  if (body.success === false) throw new Error(body.message || '请求失败')
  return body.data ?? body
}

async function loadLeagues() {
  try {
    const data = await request('/api/leagues?limit=30')
    if (Array.isArray(data) && data.length) {
      leagues.value = data
      localStorage.setItem('kpl_leagues', JSON.stringify(data))
    }
  } catch {}
  if (!selectedLeagueId.value && leagues.value.length) selectedLeagueId.value = leagues.value[0].leagueId
}

async function loadPlayers() {
  if (!selectedLeagueId.value) return
  playerLoading.value = true
  try {
    const res = await request(`/api/query/player/top?sort=kda&leagueId=${selectedLeagueId.value}`)
    const rows = Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.data) ? res.data.data : []
    players.value = rows.filter(item => item.playerName)
    visiblePlayers.value = players.value.slice(0, 80)
    if (!selectedPlayer.value && players.value.length) selectedPlayer.value = players.value[0].playerName
  } catch (error) {
    ElMessage.error('选手列表加载失败: ' + error.message)
  } finally {
    playerLoading.value = false
  }
}

async function loadDetail() {
  if (!selectedLeagueId.value || !selectedPlayer.value) return
  loading.value = true
  errorText.value = ''
  try {
    const data = await request(`/api/query/player/detail?name=${encodeURIComponent(selectedPlayer.value)}&leagueId=${selectedLeagueId.value}&limit=10`)
    if (data?.error) throw new Error('当前赛事没有该选手数据')
    detail.value = data
  } catch (error) {
    errorText.value = error.message
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function onLeagueChange() {
  selectedPlayer.value = ''
  detail.value = null
  await loadPlayers()
  await loadDetail()
}

async function refreshPage() {
  await loadLeagues()
  await loadPlayers()
  await loadDetail()
}

function filterPlayers(keyword) {
  const key = String(keyword || '').trim().toLowerCase()
  visiblePlayers.value = !key
    ? players.value.slice(0, 80)
    : players.value.filter(item => `${item.playerName}${item.teamName || ''}`.toLowerCase().includes(key)).slice(0, 80)
}

function summarizeRecent(rows) {
  if (!rows.length) return { games: 0, wins: 0, winRate: 0, avgKda: 0 }
  const wins = rows.filter(row => Number(row.won) === 1).length
  const kda = rows.reduce((sum, row) => sum + num(row.kda), 0) / rows.length
  return { games: rows.length, wins, winRate: wins / rows.length, avgKda: kda }
}

function playerKey(item) {
  return `${item.playerName}-${item.teamName || ''}-${item.positionDesc || ''}`
}

function rankOf(key) {
  return compareRank.value?.[key] || 0
}

function isSignatureHero(hero) {
  return Number(hero.games || 0) >= 4 && percentNumber(hero.winRate) >= 55
}

function heroIcon(hero) {
  return hero?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${hero.heroId}.jpg` : ''
}

function hideBroken(event) {
  event.target.style.display = 'none'
}

function shortName(value) {
  return String(value || '-').split('.').pop()
}

function num(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function percentNumber(value) {
  const n = num(value)
  return Math.abs(n) <= 1 ? n * 100 : n
}

function formatPercent(value) {
  return `${percentNumber(value).toFixed(1)}%`
}

function fixed(value, digits = 1) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(digits) : '-'
}

function formatCompact(value) {
  const n = num(value)
  if (n >= 10000) return `${(n / 1000).toFixed(1)}k`
  return Math.round(n).toString()
}

function formatMetric(value, compact = false) {
  return compact ? formatCompact(value) : fixed(value, 1)
}

onMounted(async () => {
  await loadLeagues()
  await loadPlayers()
  await loadDetail()
})
</script>

<style scoped>
.player-insights {
  --page-bg: #101113;
  --panel-bg: rgba(24, 25, 27, .92);
  --panel-strong: rgba(15, 16, 18, .96);
  --line: rgba(255, 255, 255, .1);
  --line-strong: rgba(255, 255, 255, .2);
  --text: #f1f0e8;
  --soft: rgba(241, 240, 232, .68);
  --dim: rgba(241, 240, 232, .42);
  --blue: #3ba7ff;
  --green: #87df55;
  --red: #ff5e57;
  --gold: #d7b45a;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 10px 14px 24px;
  color: var(--text);
  background: linear-gradient(180deg, rgba(16, 17, 19, .96), rgba(10, 11, 12, .98)), var(--page-bg);
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.player-insights,
.player-insights * { box-sizing: border-box; }

.topbar,
.profile-strip,
.analysis-grid,
.bottom-grid,
.state-panel {
  width: min(1680px, calc(100vw - 112px));
  margin-inline: auto;
}

.topbar {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  background: var(--panel-strong);
}

.title-block span,
.section-title span {
  display: block;
  margin-bottom: 2px;
  color: var(--gold);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 2px;
}

.title-block h1,
.section-title h3 {
  margin: 0;
  font-size: 22px;
  font-weight: 950;
  letter-spacing: 0;
}

.section-title h3 { font-size: 16px; }

.controls {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.controls :deep(.el-select) { width: 190px; }
.controls :deep(.el-select__wrapper),
.controls :deep(.el-button) {
  min-height: 34px;
  border-radius: 6px !important;
  background: rgba(255, 255, 255, .06) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
}

.refresh-btn {
  --el-button-text-color: var(--text);
  --el-button-hover-text-color: #101113;
  --el-button-hover-bg-color: var(--gold);
  --el-button-hover-border-color: var(--gold);
}

.player-option {
  display: flex;
  align-items: center;
  gap: 8px;
}
.player-option img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}
.player-option span { min-width: 60px; }
.player-option em {
  color: var(--dim);
  font-style: normal;
  font-size: 12px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px;
  border: 0;
  background: none;
  color: var(--soft);
  cursor: pointer;
}
.toggle-track {
  position: relative;
  width: 34px;
  height: 18px;
  border-radius: 12px;
  background: rgba(255, 255, 255, .18);
}
.toggle-track.on { background: var(--gold); }
.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #101113;
  transition: transform .2s ease;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }
.theme-toggle small {
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 1px;
}

.state-panel {
  min-height: 420px;
  display: grid;
  place-items: center;
  margin-top: 8px;
  border: 1px solid var(--line);
  background: var(--panel-bg);
  color: var(--soft);
}
.state-panel.error strong { color: var(--red); }
.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(255, 255, 255, .12);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.profile-strip {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.profile-card,
.metric-card,
.panel {
  border: 1px solid var(--line);
  background: var(--panel-bg);
}

.profile-card {
  min-height: 158px;
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr) 72px;
  align-items: center;
  gap: 12px;
  padding: 14px;
}
.profile-card img,
.avatar-fallback {
  width: 74px;
  height: 74px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line-strong);
}
.avatar-fallback {
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, .06);
  color: var(--gold);
  font-size: 30px;
  font-weight: 950;
}
.profile-main { min-width: 0; }
.profile-main span {
  color: var(--gold);
  font-size: 11px;
  font-weight: 900;
}
.profile-main h2 {
  margin: 4px 0 2px;
  overflow: hidden;
  font-size: 34px;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-main p {
  margin: 0;
  color: var(--soft);
  font-size: 13px;
}
.status-score {
  text-align: right;
}
.status-score strong {
  display: block;
  color: var(--green);
  font-size: 36px;
  line-height: 1;
}
.status-score span {
  color: var(--dim);
  font-size: 11px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}
.metric-card {
  min-height: 158px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px;
}
.metric-card span,
.metric-card em {
  color: var(--dim);
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}
.metric-card strong {
  margin: 6px 0 5px;
  color: var(--text);
  font-size: clamp(24px, 2.4vw, 36px);
  line-height: 1;
  font-weight: 950;
}
.metric-card.win strong { color: var(--green); }
.metric-card.accent strong { color: var(--gold); }
.metric-card.danger strong { color: var(--red); }

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}
.bottom-grid {
  display: grid;
  grid-template-columns: .82fr 1.18fr;
  gap: 8px;
  margin-top: 8px;
}

.section-title {
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px 8px;
  border-bottom: 1px solid var(--line);
}
.section-title :deep(span) {
  display: block;
  margin-bottom: 2px;
  color: var(--gold);
  font-size: 9px;
  font-weight: 900;
  letter-spacing: 2px;
}
.section-title :deep(h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 950;
  letter-spacing: 0;
}
.section-title small {
  color: var(--dim);
  font-size: 11px;
}
.section-title :deep(small) {
  color: var(--dim);
  font-size: 11px;
}

.mini-stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 8px;
}
.mini-stat-row div {
  padding: 8px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .035);
}
.mini-stat-row span,
.mini-stat-row em {
  display: block;
  color: var(--dim);
  font-size: 10px;
  font-style: normal;
}
.mini-stat-row strong {
  display: block;
  margin: 3px 0;
  color: var(--green);
  font-size: 20px;
}
.trend-chart {
  width: 100%;
  height: 136px;
  padding: 4px 8px 10px;
}
.trend-chart line {
  stroke: var(--line);
  stroke-width: 1;
}
.trend-chart polyline {
  fill: none;
  stroke: var(--gold);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.trend-chart circle {
  fill: var(--red);
  stroke: var(--panel-bg);
  stroke-width: 2;
}
.trend-chart circle.win { fill: var(--green); }

.hero-list,
.stage-list,
.featured-list {
  max-height: 236px;
  overflow-y: auto;
  padding: 8px;
}
.hero-row,
.stage-row,
.battle-row {
  border-bottom: 1px solid var(--line);
}
.hero-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 58px 42px;
  align-items: center;
  gap: 8px;
  min-height: 46px;
  padding: 6px 4px;
}
.hero-row img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}
.hero-row strong,
.hero-row span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hero-row strong { font-size: 13px; }
.hero-row span {
  color: var(--dim);
  font-size: 11px;
}
.hero-row b {
  color: var(--green);
  text-align: right;
}
.hero-row em {
  color: var(--gold);
  font-size: 11px;
  font-style: normal;
  text-align: right;
}
.hero-row:not(.signature) em { visibility: hidden; }

.compare-bars {
  display: grid;
  gap: 10px;
  padding: 12px;
}
.compare-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 54px 58px;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}
.compare-row :deep(span),
.compare-row :deep(em) {
  color: var(--dim);
  font-style: normal;
}
.compare-row :deep(b) {
  color: var(--text);
  text-align: right;
}
.compare-row span,
.compare-row em {
  color: var(--dim);
  font-style: normal;
}
.compare-row b {
  color: var(--text);
  text-align: right;
}
.compare-track {
  position: relative;
  height: 12px;
  background: rgba(255, 255, 255, .06);
  overflow: hidden;
}
.compare-row :deep(.compare-track) {
  position: relative;
  height: 12px;
  background: rgba(255, 255, 255, .06);
  overflow: hidden;
}
.compare-track i {
  position: absolute;
  left: 0;
  height: 5px;
}
.compare-row :deep(.compare-track i) {
  position: absolute;
  left: 0;
  height: 5px;
}
.compare-track .self {
  top: 1px;
  background: var(--gold);
}
.compare-row :deep(.compare-track .self) {
  top: 1px;
  background: var(--gold);
}
.compare-track .avg {
  bottom: 1px;
  background: rgba(59, 167, 255, .6);
}
.compare-row :deep(.compare-track .avg) {
  bottom: 1px;
  background: rgba(59, 167, 255, .6);
}

.stage-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 54px 64px 72px 80px;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 6px 4px;
}
.stage-row strong,
.stage-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-row span,
.stage-row em,
.stage-row small {
  color: var(--dim);
  font-style: normal;
  font-size: 11px;
}
.stage-row b { color: var(--green); }

.battle-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 58px;
  align-items: center;
  gap: 8px;
  min-height: 58px;
  padding: 7px 4px;
}
.battle-rank {
  color: var(--gold);
  font-size: 16px;
}
.battle-main {
  min-width: 0;
}
.battle-main span,
.battle-main strong,
.battle-main em {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.battle-main span,
.battle-main em {
  color: var(--dim);
  font-size: 11px;
  font-style: normal;
}
.battle-main strong {
  margin: 2px 0;
  font-size: 13px;
}
.battle-score {
  text-align: right;
}
.battle-score strong {
  display: block;
  color: var(--gold);
}
.battle-score span {
  color: var(--red);
  font-size: 12px;
  font-weight: 900;
}
.battle-score span.won { color: var(--green); }

.player-insights.theme-light {
  --page-bg: #f5f1e7;
  --panel-bg: rgba(255, 255, 255, .92);
  --panel-strong: rgba(255, 255, 255, .96);
  --line: rgba(20, 20, 20, .14);
  --line-strong: rgba(20, 20, 20, .24);
  --text: #18191b;
  --soft: rgba(24, 25, 27, .68);
  --dim: rgba(24, 25, 27, .42);
  background: linear-gradient(180deg, #f5f1e7, #eee8dc);
}
.player-insights.theme-light .controls :deep(.el-select__wrapper),
.player-insights.theme-light .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .72) !important;
}
.player-insights.theme-light .mini-stat-row div,
.player-insights.theme-light .compare-track {
  background: rgba(24, 25, 27, .04);
}

@media (max-width: 1180px) {
  .profile-strip,
  .analysis-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .player-insights {
    margin-left: 0;
    padding: 10px 10px calc(80px + env(safe-area-inset-bottom));
  }
  .topbar,
  .profile-strip,
  .analysis-grid,
  .bottom-grid,
  .state-panel {
    width: 100%;
  }
  .topbar {
    display: grid;
  }
  .controls {
    display: grid;
    grid-template-columns: 1fr 1fr auto auto;
    gap: 6px;
  }
  .controls :deep(.el-select) {
    width: 100%;
  }
  .theme-toggle small,
  .refresh-btn :deep(span) {
    display: none;
  }
  .profile-card {
    grid-template-columns: 60px minmax(0, 1fr) 58px;
  }
  .profile-card img,
  .avatar-fallback {
    width: 60px;
    height: 60px;
  }
  .profile-main h2 {
    font-size: 26px;
  }
  .status-score strong {
    font-size: 28px;
  }
  .metric-grid,
  .mini-stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
