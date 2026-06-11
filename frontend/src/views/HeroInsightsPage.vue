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
                  <div class="battle-head-right">
                    <a v-if="bilibiliUrl(battle)" :href="bilibiliUrl(battle)" target="_blank" rel="noopener" class="bilibili-link" title="在B站观看">
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                        <path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/>
                      </svg>
                    </a>
                    <b :class="{ won: Number(battle.won) === 1 }">{{ Number(battle.won) === 1 ? '胜利' : '失利' }}</b>
                  </div>
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

function bilibiliUrl(battle) {
  if (!battle.bvid) return null
  const base = `https://www.bilibili.com/video/${battle.bvid}`
  // 使用存储的实际分P编号
  return battle.pageNum ? `${base}?p=${battle.pageNum}` : base
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
  --line: rgba(255, 255, 255, .34);
  --line-strong: rgba(255, 255, 255, .45);
  --text: #e8e8e8;
  --soft: rgba(232, 232, 232, .65);
  --dim: rgba(232, 232, 232, .4);
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
  border-radius: 12px;
  background: var(--panel-strong);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
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
  gap: 16px;
  margin-top: 16px;
}

.hero-visual {
  position: relative;
  min-height: 220px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
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
  transition: transform .4s ease;
}

.hero-visual:hover .hero-bg {
  transform: scale(1.06);
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
  border-left: 3px solid transparent;
  border-radius: 12px;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
  transition: border-color .25s ease, background .25s ease, box-shadow .25s ease;
}

.metric-card strong {
  margin: 6px 0 5px;
  color: var(--text);
  font-size: clamp(26px, 2.6vw, 38px);
  line-height: 1;
  font-weight: 950;
}

.metric-card {
  transition: border-color .2s ease, background .2s ease, box-shadow .2s ease, transform .2s ease;
}

.metric-card:hover {
  border-color: var(--line-strong);
  border-left-color: var(--gold);
  background: var(--panel-strong);
  box-shadow: 0 2px 8px rgba(0, 0, 0, .06);
  transform: translateY(-3px);
}

.metric-card span,
.metric-card em {
  color: var(--dim);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.metric-card.primary strong { color: var(--blue); }
.metric-card.win strong { color: var(--green); }
.metric-card.danger strong { color: var(--red); }
.metric-card.accent strong { color: var(--gold); }

.content-grid {
  display: grid;
  grid-template-columns: minmax(460px, .82fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
}

.relationship-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.panel {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
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
  cursor: pointer;
  transition: background .2s ease, padding-left .2s ease;
}

.player-row:hover {
  background: rgba(255, 255, 255, .06);
  padding-left: 12px;
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
  cursor: pointer;
  transition: border-color .2s ease, background .2s ease;
}

.battle-card:hover {
  border-color: var(--line-strong);
  background: rgba(255, 255, 255, .08);
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

.battle-head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bilibili-link {
  display: inline-flex;
  align-items: center;
  color: #00a1d6;
  transition: color .2s, transform .2s;
}

.bilibili-link:hover {
  color: #23c9ed;
  transform: scale(1.15);
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
  cursor: pointer;
  transition: background .2s ease, padding-left .2s ease;
}

.hero-chip:hover {
  background: rgba(255, 255, 255, .06);
  padding-left: 12px;
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
  --page-bg: #F6F7F9;
  --panel-bg: #FFFFFF;
  --panel-strong: #FFFFFF;
  --line: #8A9097;
  --line-strong: #72787E;
  --text: #111827;
  --soft: #4B5563;
  --dim: #9CA3AF;
  --green: #16A34A;
  --red: #EF4444;
  --gold: #B88A2E;
  --blue: #2563EB;
  background: #F6F7F9;
}

.hero-insights.theme-light .topbar {
  background: #FFFFFF;
  border-color: #8A9097;
  box-shadow: 0 1px 4px rgba(15, 23, 42, .06);
  border-radius: 12px;
  padding: 10px 18px;
}

.hero-insights.theme-light .panel {
  background: #FFFFFF;
  border-color: #8A9097;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
  border-radius: 12px;
}

.hero-insights.theme-light .metric-card {
  background: #FFFFFF;
  border-color: #8A9097;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
  border-radius: 12px;
  padding: 14px 18px;
}

.hero-insights.theme-light .state-panel {
  background: #FFFFFF;
  border-color: #8A9097;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
  border-radius: 12px;
}

.hero-insights.theme-light .metric-card span {
  color: #6B7280;
}

.hero-insights.theme-light .metric-card em {
  color: #9CA3AF;
}

.hero-insights.theme-light .metric-card strong {
  color: #111827;
}

.hero-insights.theme-light .metric-card.primary strong { color: #2563EB; }
.hero-insights.theme-light .metric-card.win strong { color: #16A34A; }
.hero-insights.theme-light .metric-card.danger strong { color: #EF4444; }
.hero-insights.theme-light .metric-card.accent strong { color: #D97706; }

.hero-insights.theme-light .section-title {
  border-bottom-color: #8A9097;
  padding: 10px 18px 10px;
}

.hero-insights.theme-light .section-title small {
  color: #4B5563;
}

.hero-insights.theme-light .empty-line {
  color: #4B5563;
}

.hero-insights.theme-light .section-title h3 {
  color: #111827;
}

.hero-insights.theme-light .title-block span,
.hero-insights.theme-light .section-title span {
  color: #B88A2E;
}

.hero-insights.theme-light .toggle-track {
  background: #8A9097;
}

.hero-insights.theme-light .toggle-track.on {
  background: #111827;
}

.hero-insights.theme-light .toggle-thumb {
  background: #FFFFFF;
}

.hero-insights.theme-light .theme-toggle small {
  color: #6B7280;
}

.hero-insights.theme-light .refresh-btn {
  --el-button-text-color: #4B5563;
  --el-button-hover-text-color: #FFFFFF;
  --el-button-hover-bg-color: #B88A2E;
  --el-button-hover-border-color: #B88A2E;
}

.hero-insights.theme-light .player-list,
.hero-insights.theme-light .featured-list,
.hero-insights.theme-light .hero-chip-list {
  padding: 10px 14px;
}

.hero-insights.theme-light .hero-option em {
  color: #9CA3AF;
}

.hero-insights.theme-light .hero-visual {
  border-radius: 12px;
  overflow: hidden;
}

.hero-insights.theme-light .player-row.leader {
  background: linear-gradient(90deg, rgba(184, 138, 46, .1), transparent);
}

.hero-insights.theme-light .rank {
  color: #B88A2E;
}

.hero-insights.theme-light .player-main strong {
  color: #111827;
}

.hero-insights.theme-light .player-main span,
.hero-insights.theme-light .player-metrics span,
.hero-insights.theme-light .mini-kda {
  color: #6B7280;
}

.hero-insights.theme-light .player-metrics strong {
  color: #16A34A;
}

.hero-insights.theme-light .battle-card {
  background: #FFFFFF;
  border-color: #8A9097;
  box-shadow: 0 1px 2px rgba(15, 23, 42, .04);
  border-radius: 10px;
}

.hero-insights.theme-light .battle-rank {
  background: #F6F7F9;
  border-color: #8A9097;
  color: #B88A2E;
}

.hero-insights.theme-light .battle-head {
  color: #6B7280;
}

.hero-insights.theme-light .battle-head b {
  color: #EF4444;
}

.hero-insights.theme-light .battle-head b.won {
  color: #16A34A;
}

.hero-insights.theme-light .battle-body > strong {
  color: #111827;
}

.hero-insights.theme-light .battle-meta span {
  color: #4B5563;
  background: #F6F7F9;
  border-color: #8A9097;
}

.hero-insights.theme-light .hero-chip {
  background: #FFFFFF;
}

.hero-insights.theme-light .hero-chip-main strong {
  color: #111827;
}

.hero-insights.theme-light .hero-chip-main span {
  color: #6B7280;
}

.hero-insights.theme-light .hero-chip b {
  color: #16A34A;
}

.hero-insights.theme-light .hero-chip.warn b {
  color: #EF4444;
}

.hero-insights.theme-light .hero-visual {
  background: #18191b;
  border-radius: 12px;
}

.hero-insights.theme-light .metric-card:hover {
  border-color: #8A9097;
  border-left-color: #B88A2E;
  background: #F3F4F6;
  box-shadow: 0 2px 12px rgba(15, 23, 42, .08);
}

.hero-insights.theme-light .battle-card:hover {
  border-color: #8A9097;
  background: #F3F4F6;
}

.hero-insights.theme-light .player-row:hover,
.hero-insights.theme-light .hero-chip:hover {
  background: #F3F4F6;
}

.hero-insights.theme-light .hero-chip:hover {
  background: #F6F7F9;
}

.hero-insights.theme-light .player-row:hover {
  background: #F6F7F9;
}

.hero-insights.theme-light .controls :deep(.el-select__wrapper),
.hero-insights.theme-light .controls :deep(.el-button) {
  background: #FFFFFF !important;
  box-shadow: 0 0 0 1px #8A9097 inset !important;
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

<style>
@import '../styles/select-dropdown.css';
</style>
