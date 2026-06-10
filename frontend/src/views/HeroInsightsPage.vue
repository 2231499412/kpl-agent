<template>
  <main class="hero-insights" :class="`theme-${theme}`">
    <section class="topbar">
      <div class="title-block">
        <span>KPL HERO INTELLIGENCE</span>
        <h1>英雄数据详情</h1>
      </div>

      <div class="controls">
        <el-select v-model="selectedLeagueId" filterable placeholder="选择赛事" @change="onLeagueChange">
          <el-option v-for="league in leagues" :key="league.leagueId" :label="league.leagueName" :value="league.leagueId" />
        </el-select>
        <el-select
          v-model="selectedHeroId"
          filterable
          remote
          reserve-keyword
          :remote-method="filterHero"
          :loading="heroLoading"
          placeholder="搜索英雄"
          @change="loadDetail"
        >
          <el-option v-for="hero in visibleHeroes" :key="hero.heroId" :label="hero.heroName" :value="hero.heroId">
            <div class="hero-option">
              <img :src="heroIcon(hero)" :alt="hero.heroName">
              <span>{{ hero.heroName }}</span>
              <em>{{ formatPercent(hero.pickRate) }}</em>
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
      <span>正在生成英雄赛季档案...</span>
    </section>

    <section v-else-if="errorText" class="state-panel error">
      <strong>{{ errorText }}</strong>
      <span>请换一个英雄或赛事再试。</span>
    </section>

    <template v-else-if="detail">
      <section class="hero-stage">
        <article class="hero-visual">
          <img class="hero-bg" :src="heroPoster(activeHero)" :alt="activeHero?.heroName" @error="hideBroken">
          <div class="visual-shade" />
          <div class="hero-copy">
            <span>{{ currentLeagueName }}</span>
            <h2>{{ activeHero?.heroName || '-' }}</h2>
            <p>本赛季出场 {{ metricBattleCount }} 局，{{ detail.userCount || 0 }} 位选手使用，胜率 {{ formatPercent(activeHero?.winRate) }}。</p>
          </div>
        </article>

        <aside class="metric-grid">
          <div class="metric-card primary">
            <span>本赛季使用人数</span>
            <strong>{{ detail.userCount || 0 }}</strong>
            <em>按选手去重</em>
          </div>
          <div class="metric-card">
            <span>出场次数</span>
            <strong>{{ metricBattleCount }}</strong>
            <em>Pick {{ formatPercent(activeHero?.pickRate) }}</em>
          </div>
          <div class="metric-card win">
            <span>胜率</span>
            <strong>{{ formatPercent(activeHero?.winRate) }}</strong>
            <em>{{ winRankHint }}</em>
          </div>
          <div class="metric-card danger">
            <span>Ban 率</span>
            <strong>{{ formatPercent(activeHero?.banRate) }}</strong>
            <em>Ban {{ activeHero?.banNum || 0 }} 次</em>
          </div>
          <div class="metric-card">
            <span>场均 KDA</span>
            <strong>{{ fixed(activeHero?.avgKda, 2) }}</strong>
            <em>{{ fixed(activeHero?.avgKill, 1) }}/{{ fixed(activeHero?.avgDeath, 1) }}/{{ fixed(activeHero?.avgAssist, 1) }}</em>
          </div>
          <div class="metric-card accent">
            <span>场均经济</span>
            <strong>{{ formatCompact(activeHero?.avgGold) }}</strong>
            <em>样本 {{ metricBattleCount }} 局</em>
          </div>
        </aside>
      </section>

      <section class="content-grid">
        <article class="panel player-board">
          <div class="section-title">
            <div>
              <span>PLAYER LEADERBOARD</span>
              <h3>谁用它胜率最高</h3>
            </div>
            <small>至少 3 局</small>
          </div>

          <div class="player-list" v-if="topPlayers.length">
            <div v-for="(player, index) in topPlayers" :key="player.playerName" class="player-row" :class="{ leader: index === 0 }">
              <b class="rank">{{ index + 1 }}</b>
              <img v-if="player.playerIcon || player.teamIcon" :src="player.playerIcon || player.teamIcon" :alt="player.playerName">
              <div class="player-main">
                <strong>{{ shortName(player.playerName) }}</strong>
                <span>{{ player.teamName || '未知战队' }}</span>
              </div>
              <div class="player-metrics">
                <strong>{{ formatPercent(player.winRate) }}</strong>
                <span>{{ player.wins || 0 }}/{{ player.games || 0 }} 局</span>
              </div>
              <div class="mini-kda">KDA {{ fixed(player.avgKda, 2) }}</div>
            </div>
          </div>
          <div v-else class="empty-line">暂无满足场次阈值的选手。</div>
        </article>

        <article class="panel match-board">
          <div class="section-title">
            <div>
              <span>FEATURED GAMES</span>
              <h3>推荐对局</h3>
            </div>
            <small>按 KDA、MVP、胜负和伤害评分</small>
          </div>

          <div class="featured-list" v-if="featuredBattles.length">
            <div v-for="(battle, index) in featuredBattles" :key="battle.battleId" class="battle-card">
              <div class="battle-rank">{{ index + 1 }}</div>
              <div class="battle-body">
                <div class="battle-head">
                  <span>{{ battle.matchStageDesc || '赛段' }} · 第{{ battle.battleSeq || '-' }}局</span>
                  <b :class="{ won: Number(battle.won) === 1 }">{{ Number(battle.won) === 1 ? '胜利' : '失利' }}</b>
                </div>
                <strong>{{ battle.camp1TeamName }} {{ battle.camp1Score ?? '-' }} : {{ battle.camp2Score ?? '-' }} {{ battle.camp2TeamName }}</strong>
                <div class="battle-meta">
                  <span>{{ shortName(battle.playerName) }} · {{ battle.teamName }}</span>
                  <span>{{ battle.killNum }}/{{ battle.deathNum }}/{{ battle.assistNum }}</span>
                  <span>KDA {{ fixed(battle.kda, 2) }}</span>
                  <span>评分 {{ fixed(battle.score, 1) }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-line">暂无可推荐对局。</div>
        </article>
      </section>

      <section class="relationship-grid">
        <article class="panel relation-panel">
          <div class="section-title compact">
            <div>
              <span>SYNERGY</span>
              <h3>推荐搭配</h3>
            </div>
          </div>
          <div class="hero-chip-list" v-if="synergyHeroes.length">
            <div v-for="hero in synergyHeroes" :key="hero.heroId" class="hero-chip good">
              <img :src="heroIcon(hero)" :alt="hero.heroName" @error="hideBroken">
              <div class="hero-chip-main">
                <strong>{{ hero.heroName || '-' }}</strong>
                <span>{{ hero.games || 0 }} 局 · {{ hero.positionDesc || '全位置' }}</span>
              </div>
              <b>{{ formatPercent(hero.winRate) }}</b>
            </div>
          </div>
          <div v-else class="empty-line">暂无稳定共现样本。</div>
        </article>

        <article class="panel relation-panel">
          <div class="section-title compact">
            <div>
              <span>FAVORED</span>
              <h3>优势对抗</h3>
            </div>
          </div>
          <div class="hero-chip-list" v-if="favoredCounters.length">
            <div v-for="hero in favoredCounters" :key="hero.heroId" class="hero-chip good">
              <img :src="heroIcon(hero)" :alt="hero.heroName" @error="hideBroken">
              <div class="hero-chip-main">
                <strong>{{ hero.heroName || '-' }}</strong>
                <span>{{ hero.games || 0 }} 局 · {{ hero.positionDesc || '全位置' }}</span>
              </div>
              <b>{{ formatPercent(hero.winRate) }}</b>
            </div>
          </div>
          <div v-else class="empty-line">暂无优势样本。</div>
        </article>

        <article class="panel relation-panel">
          <div class="section-title compact">
            <div>
              <span>WATCHLIST</span>
              <h3>需要注意</h3>
            </div>
          </div>
          <div class="hero-chip-list" v-if="toughCounters.length">
            <div v-for="hero in toughCounters" :key="hero.heroId" class="hero-chip warn">
              <img :src="heroIcon(hero)" :alt="hero.heroName" @error="hideBroken">
              <div class="hero-chip-main">
                <strong>{{ hero.heroName || '-' }}</strong>
                <span>{{ hero.games || 0 }} 局 · {{ hero.positionDesc || '全位置' }}</span>
              </div>
              <b>{{ formatPercent(hero.winRate) }}</b>
            </div>
          </div>
          <div v-else class="empty-line">暂无劣势样本。</div>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTheme, setTheme } from '../utils/theme'

const leagues = ref(JSON.parse(localStorage.getItem('kpl_leagues') || '[]'))
const heroes = ref([])
const visibleHeroes = ref([])
const selectedLeagueId = ref('')
const selectedHeroId = ref(null)
const detail = ref(null)
const loading = ref(false)
const heroLoading = ref(false)
const errorText = ref('')
const theme = ref(getTheme())
watch(theme, (value) => setTheme(value))

const activeHero = computed(() => detail.value?.hero || heroes.value.find(hero => Number(hero.heroId) === Number(selectedHeroId.value)))
const topPlayers = computed(() => detail.value?.topPlayers || [])
const featuredBattles = computed(() => detail.value?.featuredBattles || [])
const synergyHeroes = computed(() => detail.value?.synergyHeroes || [])
const favoredCounters = computed(() => detail.value?.favoredCounters || [])
const toughCounters = computed(() => detail.value?.toughCounters || [])
const currentLeagueName = computed(() => leagues.value.find(item => item.leagueId === selectedLeagueId.value)?.leagueName || '当前赛事')
const metricBattleCount = computed(() => activeHero.value?.battleCount || activeHero.value?.pickNum || 0)
const winRankHint = computed(() => {
  const value = percentNumber(activeHero.value?.winRate)
  if (value >= 60) return '高胜率样本'
  if (value >= 50) return '胜率稳定'
  return '需要结合阵容'
})

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  })
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
  } catch {
    // keep local cache
  }
  if (!selectedLeagueId.value && leagues.value.length) selectedLeagueId.value = leagues.value[0].leagueId
}

async function loadHeroes() {
  if (!selectedLeagueId.value) return
  heroLoading.value = true
  try {
    const res = await request(`/api/query/hero/top?sort=pick&leagueId=${selectedLeagueId.value}`)
    const rows = Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.data) ? res.data.data : []
    heroes.value = rows.filter(hero => hero.heroId && hero.heroName)
    visibleHeroes.value = heroes.value.slice(0, 80)
    if (!selectedHeroId.value && heroes.value.length) selectedHeroId.value = heroes.value[0].heroId
  } catch (error) {
    ElMessage.error('英雄列表加载失败: ' + error.message)
  } finally {
    heroLoading.value = false
  }
}

async function loadDetail() {
  if (!selectedLeagueId.value || !selectedHeroId.value) return
  loading.value = true
  errorText.value = ''
  try {
    const data = await request(`/api/query/hero/detail?heroId=${selectedHeroId.value}&leagueId=${selectedLeagueId.value}&limit=8`)
    if (data?.error) throw new Error('当前赛事没有该英雄数据')
    detail.value = data
  } catch (error) {
    errorText.value = error.message
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function onLeagueChange() {
  selectedHeroId.value = null
  detail.value = null
  await loadHeroes()
  await loadDetail()
}

async function refreshPage() {
  await loadLeagues()
  await loadHeroes()
  await loadDetail()
}

function filterHero(keyword) {
  const key = String(keyword || '').trim().toLowerCase()
  visibleHeroes.value = !key
    ? heroes.value.slice(0, 80)
    : heroes.value.filter(hero => String(hero.heroName || '').toLowerCase().includes(key)).slice(0, 80)
}

function heroIcon(hero) {
  return hero?.heroIcon || (hero?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${hero.heroId}.jpg` : '')
}

function heroPoster(hero) {
  return hero?.heroId
    ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-bigskin-1.jpg`
    : heroIcon(hero)
}

function hideBroken(event) {
  event.target.style.display = 'none'
}

function percentNumber(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.abs(num) <= 1 ? num * 100 : num
}

function formatPercent(value) {
  return `${percentNumber(value).toFixed(1)}%`
}

function fixed(value, digits = 1) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '-'
}

function formatCompact(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '-'
  if (num >= 10000) return `${(num / 1000).toFixed(1)}k`
  return Math.round(num).toString()
}

function shortName(value) {
  return String(value || '-').split('.').pop()
}

onMounted(async () => {
  await loadLeagues()
  await loadHeroes()
  await loadDetail()
})
</script>

<style scoped>
.hero-insights {
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
  --violet: #b783ff;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 10px 14px 24px;
  color: var(--text);
  background:
    linear-gradient(180deg, rgba(16, 17, 19, .96), rgba(10, 11, 12, .98)),
    var(--page-bg);
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.hero-insights,
.hero-insights * {
  box-sizing: border-box;
}

.topbar,
.hero-stage,
.content-grid,
.relationship-grid,
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
  margin-bottom: 3px;
  color: var(--gold);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 2px;
}

.title-block h1,
.section-title h3,
.hero-copy h2 {
  margin: 0;
  letter-spacing: 0;
}

.title-block h1 {
  font-size: 22px;
  font-weight: 950;
}

.controls {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.controls :deep(.el-select) {
  width: 190px;
}

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

.hero-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hero-option img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.hero-option span {
  flex: 1;
}

.hero-option em {
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

.toggle-track.on {
  background: var(--gold);
}

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

.toggle-track.on .toggle-thumb {
  transform: translateX(16px);
}

.theme-toggle small {
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 1px;
}

.state-panel {
  min-height: 420px;
  display: grid;
  place-items: center;
  gap: 12px;
  margin-top: 12px;
  border: 1px solid var(--line);
  background: var(--panel-bg);
  color: var(--soft);
}

.state-panel.error strong {
  color: var(--red);
}

.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(255, 255, 255, .12);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.hero-stage {
  display: grid;
  grid-template-columns: minmax(420px, .82fr) minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.hero-visual {
  position: relative;
  min-height: 220px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #08090a;
}

.hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 58% 16%;
  filter: saturate(1.05) contrast(1.05);
}

.visual-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 9, 10, .9), rgba(8, 9, 10, .48) 48%, rgba(8, 9, 10, .12)),
    linear-gradient(180deg, transparent 44%, rgba(8, 9, 10, .88));
}

.hero-copy {
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 16px;
}

.hero-copy span {
  color: var(--gold);
  font-size: 12px;
  font-weight: 900;
}

.hero-copy h2 {
  margin-top: 4px;
  color: #fff;
  font-size: clamp(34px, 4vw, 56px);
  line-height: .96;
  font-weight: 950;
  text-shadow: 0 10px 26px rgba(0, 0, 0, .45);
}

.hero-copy p {
  max-width: 520px;
  margin: 6px 0 0;
  color: rgba(255, 255, 255, .82);
  font-size: 13px;
  line-height: 1.45;
  text-shadow: 0 3px 12px rgba(0, 0, 0, .55);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.metric-card {
  min-height: 106px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px;
  border: 1px solid var(--line);
  background: var(--panel-bg);
}

.metric-card span,
.metric-card em {
  color: var(--dim);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.metric-card strong {
  margin: 6px 0 5px;
  color: var(--text);
  font-size: clamp(26px, 2.6vw, 38px);
  line-height: 1;
  font-weight: 950;
}

.metric-card.primary strong { color: var(--blue); }
.metric-card.win strong { color: var(--green); }
.metric-card.danger strong { color: var(--red); }
.metric-card.accent strong { color: var(--gold); }

.content-grid {
  display: grid;
  grid-template-columns: minmax(460px, .82fr) minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.relationship-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.panel {
  min-width: 0;
  border: 1px solid var(--line);
  background: var(--panel-bg);
}

.section-title {
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px 8px;
  border-bottom: 1px solid var(--line);
}

.section-title.compact {
  min-height: 46px;
}

.section-title h3 {
  font-size: 16px;
  font-weight: 950;
}

.section-title small {
  color: var(--dim);
  font-size: 11px;
}

.player-list,
.featured-list,
.hero-chip-list {
  padding: 8px;
}

.player-list,
.featured-list {
  max-height: 224px;
  overflow-y: auto;
}

.featured-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.player-row {
  display: grid;
  grid-template-columns: 24px 30px minmax(0, 1fr) 76px 64px;
  align-items: center;
  gap: 8px;
  min-height: 46px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--line);
}

.player-row:last-child {
  border-bottom: 0;
}

.player-row.leader {
  background: linear-gradient(90deg, rgba(215, 180, 90, .18), transparent);
}

.rank {
  color: var(--gold);
  font-size: 15px;
}

.player-row img {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
}

.player-main,
.player-metrics {
  min-width: 0;
}

.player-main strong,
.player-main span,
.player-metrics strong,
.player-metrics span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-main strong {
  font-size: 13px;
}

.player-main span,
.player-metrics span,
.mini-kda {
  color: var(--dim);
  font-size: 11px;
}

.player-metrics strong {
  color: var(--green);
  font-size: 14px;
}

.mini-kda {
  text-align: right;
}

.battle-card {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(255, 255, 255, .045);
}

.battle-card:last-child {
  border-bottom: 1px solid var(--line);
}

.battle-rank {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  color: var(--gold);
  font-weight: 950;
}

.battle-body {
  min-width: 0;
}

.battle-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--dim);
  font-size: 11px;
}

.battle-head b {
  color: var(--red);
}

.battle-head b.won {
  color: var(--green);
}

.battle-body > strong {
  display: block;
  margin: 3px 0 6px;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.battle-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.battle-meta span {
  padding: 2px 6px;
  border: 1px solid var(--line);
  color: var(--soft);
  background: rgba(255, 255, 255, .04);
  font-size: 10px;
}

.relation-panel {
  min-height: 214px;
}

.hero-chip-list {
  max-height: 168px;
  overflow-y: auto;
}

.hero-chip {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 62px;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--line);
}

.hero-chip:last-child {
  border-bottom: 0;
}

.hero-chip img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line-strong);
}

.hero-chip-main {
  min-width: 0;
}

.hero-chip-main strong,
.hero-chip-main span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-chip-main strong {
  color: var(--text);
  font-size: 13px;
}

.hero-chip-main span {
  color: var(--dim);
  font-size: 11px;
}

.hero-chip b {
  color: var(--green);
  font-size: 14px;
  text-align: right;
}

.hero-chip.warn b {
  color: var(--red);
}

.empty-line {
  padding: 28px 18px;
  color: var(--dim);
  font-size: 13px;
  text-align: center;
}

.hero-insights.theme-light {
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

.hero-insights.theme-light .battle-card {
  background: #fff;
  box-shadow: 0 8px 22px rgba(24, 25, 27, .04);
}

.hero-insights.theme-light .battle-rank {
  background: #f6f1e6;
}

.hero-insights.theme-light .battle-meta span {
  color: rgba(24, 25, 27, .68);
  background: #f7f7f5;
}

.hero-insights.theme-light .hero-chip {
  background: #fff;
}

.hero-insights.theme-light .hero-visual {
  background: #18191b;
}

.hero-insights.theme-light .controls :deep(.el-select__wrapper),
.hero-insights.theme-light .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .72) !important;
}

@media (max-width: 1180px) {
  .hero-stage,
  .content-grid,
  .relationship-grid {
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .hero-insights {
    margin-left: 0;
    padding: 10px 10px calc(80px + env(safe-area-inset-bottom));
  }

  .topbar,
  .hero-stage,
  .content-grid,
  .relationship-grid,
  .state-panel {
    width: 100%;
  }

  .topbar {
    display: grid;
    grid-template-columns: 1fr;
  }

  .controls {
    display: grid;
    grid-template-columns: 1fr 1fr auto auto;
    gap: 6px;
  }

  .controls :deep(.el-select) {
    width: 100%;
  }

  .refresh-btn :deep(span) {
    display: none;
  }

  .theme-toggle small {
    display: none;
  }

  .hero-stage {
    margin-top: 10px;
  }

  .hero-visual {
    min-height: 300px;
  }

  .hero-copy {
    left: 16px;
    right: 16px;
    bottom: 16px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .metric-card {
    min-height: 118px;
    padding: 12px;
  }

  .metric-card strong {
    font-size: 27px;
  }

  .player-row {
    grid-template-columns: 24px 32px minmax(0, 1fr) 74px;
  }

  .mini-kda {
    display: none;
  }

  .battle-card {
    grid-template-columns: 34px minmax(0, 1fr);
  }
}
</style>
