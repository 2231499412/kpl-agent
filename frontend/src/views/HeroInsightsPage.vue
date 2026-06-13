<template>
  <main class="hero-insights" :class="`theme-${theme}`">
    <section class="topbar">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div class="title-block">
          <span>KPL HERO INTELLIGENCE</span>
          <h1>英雄数据详情</h1>
        </div>
      </div>

      <div class="controls">
        <el-select v-model="selectedLeagueId" filterable placeholder="选择赛事" :suffix-icon="ArrowDown" @change="onLeagueChange">
          <el-option v-for="league in leagues" :key="league.leagueId" :label="league.leagueName" :value="league.leagueId" />
        </el-select>
        <el-select
          v-model="selectedHeroId"
          filterable
          remote
          reserve-keyword
          :remote-method="filterHero"
          :loading="heroLoading"
          popper-class="hero-insights-select-popper"
          placeholder="搜索英雄"
          :suffix-icon="ArrowDown"
          @change="onHeroChange"
        >
          <el-option v-for="hero in visibleHeroes" :key="hero.heroId" :label="hero.heroName" :value="hero.heroId">
            <div class="hero-option">
              <img :src="heroIcon(hero)" :alt="hero.heroName">
              <span>{{ hero.heroName }}</span>
              <em>
                <b>{{ heroOptionMetricValue(hero) }}</b>
                <small>{{ heroOptionMetricLabel(hero) }}</small>
              </em>
            </div>
          </el-option>
        </el-select>

        <el-button v-if="returnPlayerTarget" :icon="Back" class="back-btn" @click="returnToPlayer">返回选手</el-button>
        <el-button v-if="heroTrail.length" :icon="Back" class="back-btn" @click="goBackHero">返回</el-button>
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
      <section class="hero-stage" :class="{ switching: isSwitchingHero }">
        <article class="hero-visual">
          <img :key="`poster-${heroViewKey}`" class="hero-bg" :src="heroPoster(activeHero)" :alt="activeHero?.heroName" @error="hideBroken">
          <div class="visual-shade" />
          <div :key="`copy-${heroViewKey}`" class="hero-copy">
            <span>{{ currentLeagueName }}</span>
            <h2>{{ activeHero?.heroName || '-' }}</h2>
            <p>本赛季出场 {{ metricBattleCount }} 局，{{ detail.userCount || 0 }} 位选手使用，胜率 {{ formatPercent(activeHero?.winRate) }}。</p>
          </div>
          <div v-if="isSwitchingHero" class="hero-switch-mask">
            <div class="switch-spinner" />
            <span>正在加载新英雄档案</span>
          </div>
        </article>

        <aside class="metric-grid">
          <div class="metric-card primary">
            <span>本赛季使用人数</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.userCount">{{ detail.userCount || 0 }}</strong>
              </Transition>
            </div>
            <em>按选手去重</em>
          </div>
          <div class="metric-card accent">
            <span>出场次数</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.battleCount">{{ metricBattleCount }}</strong>
              </Transition>
            </div>
            <em>Pick {{ formatBpPercent(activeHero, 'pick') }}</em>
          </div>
          <div class="metric-card win">
            <span>胜率</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.winRate">{{ formatPercent(activeHero?.winRate) }}</strong>
              </Transition>
            </div>
            <em>{{ winRankHint }}</em>
          </div>
          <div class="metric-card danger">
            <span>Ban 率</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.banRate">{{ formatBpPercent(activeHero, 'ban') }}</strong>
              </Transition>
            </div>
            <em>Ban {{ formatBpCount(activeHero, 'ban') }}</em>
          </div>
          <div class="metric-card light">
            <span>场均 KDA</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.avgKda">{{ fixed(activeHero?.avgKda, 2) }}</strong>
              </Transition>
            </div>
            <em>{{ fixed(activeHero?.avgKill, 1) }}/{{ fixed(activeHero?.avgDeath, 1) }}/{{ fixed(activeHero?.avgAssist, 1) }}</em>
          </div>
          <div class="metric-card accent">
            <span>场均经济</span>
            <div class="metric-value-wrap">
              <Transition name="metric-value">
                <strong :key="metricKeys.avgGold">{{ formatCompact(activeHero?.avgGold) }}</strong>
              </Transition>
            </div>
            <em>样本 {{ metricBattleCount }} 局</em>
          </div>
        </aside>
      </section>

      <section class="content-grid">
        <article class="panel player-board">
          <div class="section-title">
            <div>
              <span>PLAYER LEADERBOARD</span>
              <h3>高胜率选手</h3>
            </div>
            <small>至少 3 局</small>
          </div>

          <div class="player-list" v-if="topPlayers.length">
            <div v-for="(player, index) in topPlayers" :key="player.playerName" class="player-row" :class="{ leader: index === 0 }" @click="openPlayer(player)">
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
            <div
              v-for="(battle, index) in featuredBattles"
              :key="battle.battleId"
              class="battle-card"
              :class="{ clickable: Boolean(videoUrl(battle)) }"
              role="link"
              tabindex="0"
              @click="openBattleVideo(battle)"
              @keydown.enter.prevent="openBattleVideo(battle)"
            >
              <div class="battle-thumb" :style="battleThumbStyle(battle)">
                <img
                  :src="battleCover(battle)"
                  :alt="videoCoverTitle(battle) || activeHero?.heroName || '推荐对局'"
                  :class="{ loaded: isBattleCoverLoaded(battle) }"
                  :loading="index < 3 ? 'eager' : 'lazy'"
                  :fetchpriority="index < 3 ? 'high' : 'auto'"
                  decoding="async"
                  @load="markBattleCoverLoaded(battle)"
                  @error="fallbackBattleCover"
                >
                <span>{{ index + 1 }}</span>
                <i v-if="isBilibili(battle)">B站</i>
              </div>
              <div class="battle-body">
                <div class="battle-head">
                  <span>{{ battle.matchStageDesc || '赛段' }} · 第{{ battle.battleSeq || '-' }}局</span>
                  <div class="battle-head-right">
                    <a v-if="videoUrl(battle)" :href="videoUrl(battle)" target="_blank" rel="noopener" :class="['video-link', isBilibili(battle) ? 'bilibili' : 'tencent']" :title="isBilibili(battle) ? '在B站观看' : '在腾讯视频观看'" @click.stop>
                      <svg v-if="isBilibili(battle)" viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.151.929.4.267.249.391.551.391.907 0 .355-.124.657-.373.906zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/></svg>
                      <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
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
            <div v-for="hero in synergyHeroes" :key="hero.heroId" class="hero-chip good" @click="openRelatedHero(hero)">
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
            <div v-for="hero in favoredCounters" :key="hero.heroId" class="hero-chip good" @click="openRelatedHero(hero)">
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
            <div v-for="hero in toughCounters" :key="hero.heroId" class="hero-chip warn" @click="openRelatedHero(hero)">
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Back, Refresh } from '@element-plus/icons-vue'
import { getTheme, setTheme } from '../utils/theme'

const HERO_TRAIL_KEY = 'kpl_hero_insights_trail'
const route = useRoute()
const router = useRouter()
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
const routeReady = ref(false)
const heroTrail = ref([])
const isSwitchingHero = ref(false)
const videoCovers = ref({})
const loadedBattleCovers = ref({})
let detailRequestId = 0
watch(theme, (value) => setTheme(value))

const activeHero = computed(() => detail.value?.hero || heroes.value.find(hero => Number(hero.heroId) === Number(selectedHeroId.value)))
const topPlayers = computed(() => detail.value?.topPlayers || [])
const featuredBattles = computed(() => detail.value?.featuredBattles || [])
const synergyHeroes = computed(() => detail.value?.synergyHeroes || [])
const favoredCounters = computed(() => detail.value?.favoredCounters || [])
const toughCounters = computed(() => detail.value?.toughCounters || [])
const currentLeagueName = computed(() => leagues.value.find(item => item.leagueId === selectedLeagueId.value)?.leagueName || '当前赛事')
const returnPlayerTarget = computed(() => {
  if (!route.query.returnPlayer) return null
  return {
    leagueId: String(route.query.returnLeagueId || selectedLeagueId.value),
    player: String(route.query.returnPlayer),
  }
})
const metricBattleCount = computed(() => activeHero.value?.battleCount || activeHero.value?.pickNum || 0)
const heroViewKey = computed(() => {
  const hero = detail.value?.hero
  return `${selectedLeagueId.value || 'league'}-${hero?.heroId || selectedHeroId.value || 'hero'}`
})
const metricKeys = computed(() => ({
  userCount: `${heroViewKey.value}-users-${detail.value?.userCount || 0}`,
  battleCount: `${heroViewKey.value}-battle-${metricBattleCount.value}`,
  winRate: `${heroViewKey.value}-win-${formatPercent(activeHero.value?.winRate)}`,
  banRate: `${heroViewKey.value}-ban-${formatBpPercent(activeHero.value, 'ban')}`,
  avgKda: `${heroViewKey.value}-kda-${fixed(activeHero.value?.avgKda, 2)}`,
  avgGold: `${heroViewKey.value}-gold-${formatCompact(activeHero.value?.avgGold)}`,
}))
const winRankHint = computed(() => {
  const value = percentNumber(activeHero.value?.winRate)
  if (value >= 60) return '高胜率样本'
  if (value >= 50) return '胜率稳定'
  return '需要结合阵容'
})
watch(featuredBattles, (items) => {
  preloadBattleCoverImages(items)
  loadVideoCovers(items)
}, { flush: 'post' })

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
    const data = await request('/api/leagues?limit=100')
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
    visibleHeroes.value = heroes.value
    if (!selectedHeroId.value && heroes.value.length) selectedHeroId.value = heroes.value[0].heroId
  } catch (error) {
    ElMessage.error('英雄列表加载失败: ' + error.message)
  } finally {
    heroLoading.value = false
  }
}

async function loadDetail() {
  if (!selectedLeagueId.value || !selectedHeroId.value) return
  const requestId = ++detailRequestId
  const leagueId = selectedLeagueId.value
  const heroId = selectedHeroId.value
  const hadDetail = Boolean(detail.value)
  loading.value = true
  isSwitchingHero.value = hadDetail
  errorText.value = ''
  try {
    const data = await request(`/api/query/hero/detail?heroId=${heroId}&leagueId=${leagueId}&limit=8`)
    if (data?.error) throw new Error('当前赛事没有该英雄数据')
    const posterHero = data?.hero || heroes.value.find(hero => Number(hero.heroId) === Number(heroId)) || { heroId }
    await preloadImage(heroPoster(posterHero))
    if (requestId !== detailRequestId) return
    detail.value = data
  } catch (error) {
    if (requestId !== detailRequestId) return
    if (hadDetail) {
      ElMessage.error(error.message)
    } else {
      errorText.value = error.message
      detail.value = null
    }
  } finally {
    if (requestId === detailRequestId) {
      loading.value = false
      isSwitchingHero.value = false
    }
  }
}

async function onLeagueChange() {
  selectedHeroId.value = null
  detail.value = null
  await loadHeroes()
  await pushHeroRoute(true)
}

async function onHeroChange() {
  await pushHeroRoute()
}

async function refreshPage() {
  await loadLeagues()
  await loadHeroes()
  await loadDetail()
}

function normalizeQueryId(value) {
  if (value == null || value === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : value
}

async function pushHeroRoute(replace = false) {
  if (!selectedLeagueId.value || !selectedHeroId.value) return
  const target = {
    path: '/hero-insights',
    query: {
      leagueId: selectedLeagueId.value,
      heroId: selectedHeroId.value,
      ...returnPlayerQuery(),
    },
  }
  if (replace) await router.replace(target)
  else await router.push(target)
}

function openPlayer(player) {
  if (!player?.playerName) return
  router.push({
    path: '/player-insights',
    query: {
      leagueId: selectedLeagueId.value,
      player: player.playerName,
      returnLeagueId: selectedLeagueId.value,
      returnHeroId: selectedHeroId.value,
    },
  })
}

function openRelatedHero(hero) {
  if (!hero?.heroId) return
  pushHeroTrail()
  router.push({
    path: '/hero-insights',
    query: {
      leagueId: selectedLeagueId.value,
      heroId: hero.heroId,
      ...returnPlayerQuery(),
    },
  })
}

function returnPlayerQuery() {
  if (!route.query.returnPlayer) return {}
  return {
    returnLeagueId: route.query.returnLeagueId || selectedLeagueId.value,
    returnPlayer: route.query.returnPlayer,
    ...(route.query.playerReturnHeroId
      ? {
          playerReturnLeagueId: route.query.playerReturnLeagueId || route.query.returnLeagueId || selectedLeagueId.value,
          playerReturnHeroId: route.query.playerReturnHeroId,
        }
      : {}),
  }
}

function returnToPlayer() {
  if (!returnPlayerTarget.value) return
  router.push({
    path: '/player-insights',
    query: {
      leagueId: returnPlayerTarget.value.leagueId,
      player: returnPlayerTarget.value.player,
      ...(route.query.playerReturnHeroId
        ? {
            returnLeagueId: route.query.playerReturnLeagueId || returnPlayerTarget.value.leagueId,
            returnHeroId: route.query.playerReturnHeroId,
          }
        : {}),
    },
  })
}

function loadHeroTrail() {
  try {
    const rows = JSON.parse(sessionStorage.getItem(HERO_TRAIL_KEY) || '[]')
    heroTrail.value = Array.isArray(rows) ? rows : []
  } catch {
    heroTrail.value = []
  }
}

function saveHeroTrail() {
  sessionStorage.setItem(HERO_TRAIL_KEY, JSON.stringify(heroTrail.value.slice(-10)))
}

function pushHeroTrail() {
  if (!selectedLeagueId.value || !selectedHeroId.value) return
  const item = {
    leagueId: selectedLeagueId.value,
    heroId: selectedHeroId.value,
    heroName: activeHero.value?.heroName || '',
  }
  const last = heroTrail.value[heroTrail.value.length - 1]
  if (last && String(last.heroId) === String(item.heroId) && String(last.leagueId) === String(item.leagueId)) return
  heroTrail.value = [...heroTrail.value, item].slice(-10)
  saveHeroTrail()
}

function goBackHero() {
  const previous = heroTrail.value.pop()
  saveHeroTrail()
  if (!previous) return
  router.push({
    path: '/hero-insights',
    query: {
      leagueId: previous.leagueId,
      heroId: previous.heroId,
      ...returnPlayerQuery(),
    },
  })
}

watch(
  () => [route.query.leagueId, route.query.heroId],
  async ([leagueId, heroId]) => {
    if (!routeReady.value || route.name !== 'hero-insights') return
    const nextLeagueId = leagueId ? String(leagueId) : selectedLeagueId.value
    const nextHeroId = normalizeQueryId(heroId)
    const leagueChanged = nextLeagueId && nextLeagueId !== selectedLeagueId.value

    if (leagueChanged) {
      selectedLeagueId.value = nextLeagueId
      selectedHeroId.value = nextHeroId
      detail.value = null
      await loadHeroes()
    } else if (nextHeroId && Number(nextHeroId) !== Number(selectedHeroId.value)) {
      selectedHeroId.value = nextHeroId
    }

    await loadDetail()
  }
)

function filterHero(keyword) {
  const key = String(keyword || '').trim().toLowerCase()
  visibleHeroes.value = !key
    ? heroes.value
    : heroes.value.filter(hero => String(hero.heroName || '').toLowerCase().includes(key))
}

function heroIcon(hero) {
  return hero?.heroIcon || (hero?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${hero.heroId}.jpg` : '')
}

function heroPoster(hero) {
  return hero?.heroId
    ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-bigskin-1.jpg`
    : heroIcon(hero)
}

function preloadImage(src) {
  if (!src || typeof Image === 'undefined') return Promise.resolve(false)
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve(true)
    img.onerror = () => resolve(false)
    img.src = src
  })
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

function bpValue(hero, type) {
  if (!hero) return { count: 0, rate: 0 }
  return type === 'ban'
    ? { count: Number(hero.banNum), rate: Number(hero.banRate) }
    : { count: Number(hero.pickNum), rate: Number(hero.pickRate) }
}

function hasBpData(hero, type) {
  const { count, rate } = bpValue(hero, type)
  return (Number.isFinite(count) && count > 0) || (Number.isFinite(rate) && rate > 0)
}

function formatBpPercent(hero, type) {
  return hasBpData(hero, type) ? formatPercent(bpValue(hero, type).rate) : '暂无'
}

function formatBpCount(hero, type) {
  return hasBpData(hero, type) ? `${Math.round(bpValue(hero, type).count || 0)} 次` : '暂无'
}

function heroOptionMetricLabel(hero) {
  return hasBpData(hero, 'pick') ? 'pick\u7387' : '\u51fa\u573a'
}

function heroOptionMetricValue(hero) {
  return hasBpData(hero, 'pick') ? formatPercent(hero.pickRate) : `${hero?.battleCount || 0}次`
}

function battleCover(battle) {
  if (isBilibili(battle)) {
    return `/api/query/video/cover-image?bvid=${encodeURIComponent(battle.bvid)}`
  }
  return heroPoster(activeHero.value)
}

function battleThumbStyle(battle) {
  const fallback = heroPoster(activeHero.value)
  return fallback ? { '--fallback-cover': `url("${fallback}")` } : {}
}

function videoCoverTitle(battle) {
  return videoCovers.value[battle?.bvid]?.title || ''
}

function battleCoverKey(battle) {
  return battle?.battleId || battle?.bvid || ''
}

function isBattleCoverLoaded(battle) {
  return Boolean(loadedBattleCovers.value[battleCoverKey(battle)])
}

function markBattleCoverLoaded(battle) {
  const key = battleCoverKey(battle)
  if (!key) return
  loadedBattleCovers.value = {
    ...loadedBattleCovers.value,
    [key]: true,
  }
}

function fallbackBattleCover(event) {
  const fallback = heroPoster(activeHero.value)
  if (fallback && event.target.src !== fallback) {
    event.target.src = fallback
    return
  }
  hideBroken(event)
}

function preloadBattleCoverImages(items = [], limit = 5) {
  const urls = [...new Set(items
    .filter(item => isBilibili(item))
    .map(item => battleCover(item)))]
    .slice(0, limit)
  urls.forEach((url) => {
    const image = new Image()
    image.decoding = 'async'
    image.src = url
  })
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

function videoUrl(battle) {
  if (!battle.bvid) return null
  if (battle.bvid.startsWith('BV')) {
    const base = `https://www.bilibili.com/video/${battle.bvid}`
    const p = battle.pageNum || battle.battleSeq
    return p && p > 1 ? `${base}?p=${p}` : base
  }
  if (battle.bvid.startsWith('http')) return battle.bvid
  return null
}

function isBilibili(battle) {
  return battle.bvid && battle.bvid.startsWith('BV')
}

function openBattleVideo(battle) {
  const url = videoUrl(battle)
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

async function loadVideoCovers(items = []) {
  const bvids = [...new Set(items.map(item => item?.bvid).filter(bvid => bvid && bvid.startsWith('BV')))]
    .filter(bvid => !videoCovers.value[bvid])
    .slice(0, 8)
  if (!bvids.length) return
  await Promise.allSettled(bvids.map(async (bvid) => {
    const data = await request(`/api/query/video/cover?bvid=${encodeURIComponent(bvid)}`)
    videoCovers.value = {
      ...videoCovers.value,
      [bvid]: data,
    }
  }))
}

onMounted(async () => {
  loadHeroTrail()
  await loadLeagues()
  if (route.query.leagueId) selectedLeagueId.value = String(route.query.leagueId)
  if (route.query.heroId) selectedHeroId.value = normalizeQueryId(route.query.heroId)
  await loadHeroes()
  if (route.query.heroId) selectedHeroId.value = normalizeQueryId(route.query.heroId)
  await loadDetail()
  routeReady.value = true
})
</script>

<style scoped>
.hero-insights {
  --page-bg: #101113;
  --panel-bg: rgba(24, 25, 27, .92);
  --panel-strong: rgba(15, 16, 18, .96);
  --line: rgba(255, 255, 255, .48);
  --line-strong: rgba(255, 255, 255, .62);
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
  padding: 14px 18px 28px;
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
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel-strong);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.hero-insights .brand-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  background: rgba(255, 255, 255, .04);
  color: var(--text);
  font-size: 30px;
  font-weight: 950;
  line-height: 1;
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

.controls :deep(.el-select__suffix) {
  color: var(--soft);
}
.controls :deep(.el-select__caret) {
  color: var(--soft);
}

.refresh-btn,
.back-btn {
  --el-button-text-color: var(--text);
  --el-button-hover-text-color: var(--text);
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
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 3px;
  min-width: 94px;
  color: #6b5f50;
  font-style: normal;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.hero-option em small {
  color: #111827;
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.hero-option em b {
  color: #111827;
  font-size: 13px;
  font-weight: 950;
  text-align: right;
  white-space: nowrap;
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
  grid-template-columns: minmax(440px, .84fr) minmax(0, 1fr);
  grid-auto-rows: 1fr;
  gap: 16px;
  margin-top: 16px;
}

.hero-visual {
  position: relative;
  min-height: 246px;
  height: 100%;
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
  transform: scale(1) translateX(0);
  opacity: 1;
  animation: heroPosterIn .56s cubic-bezier(.2, .72, .18, 1) none;
  transition: transform .4s ease, filter .4s ease, opacity .4s ease;
}

.hero-visual:hover .hero-bg {
  transform: scale(1.06);
}

.visual-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(8, 9, 10, .5), rgba(8, 9, 10, .25) 48%, rgba(8, 9, 10, .05)),
    linear-gradient(180deg, transparent 44%, rgba(8, 9, 10, .6));
}

.hero-copy {
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 16px;
  animation: heroCopyIn .48s cubic-bezier(.2, .72, .18, 1) .04s both;
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

.hero-switch-mask {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 8px;
  padding: 18px;
  color: rgba(255, 255, 255, .86);
  background:
    linear-gradient(90deg, rgba(8, 9, 10, .12), rgba(8, 9, 10, .38)),
    linear-gradient(180deg, transparent 38%, rgba(8, 9, 10, .58));
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
  pointer-events: none;
  animation: switchMaskIn .2s ease both;
}

.switch-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, .24);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: spin .7s linear infinite;
}

.hero-stage.switching .hero-bg {
  filter: saturate(.92) contrast(.98) brightness(.84);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  gap: 8px;
  height: 100%;
}

.metric-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 14px;
  border: 1px solid var(--line);
  border-left: 3px solid transparent;
  border-radius: 12px;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
  transition: border-color .25s ease, background .25s ease, box-shadow .25s ease;
}

.metric-card strong {
  display: block;
  margin: 0;
  color: var(--text);
  font-size: clamp(26px, 2.6vw, 38px);
  line-height: 1;
  font-weight: 950;
}

.metric-value-wrap {
  position: relative;
  min-height: clamp(26px, 2.6vw, 38px);
  margin: 6px 0 5px;
  display: flex;
  align-items: center;
  overflow: hidden;
}

.metric-value-enter-active,
.metric-value-leave-active {
  transition: opacity .24s ease, transform .24s ease, filter .24s ease;
}

.metric-value-leave-active {
  position: absolute;
  inset: 0 auto auto 0;
}

.metric-value-enter-from {
  opacity: 0;
  filter: blur(4px);
  transform: translateY(10px);
}

.metric-value-leave-to {
  opacity: 0;
  filter: blur(3px);
  transform: translateY(-8px);
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

.metric-card.primary { border-left-color: var(--blue); }
.metric-card.primary strong { color: var(--blue); }
.metric-card.win { border-left-color: var(--green); }
.metric-card.win strong { color: var(--green); }
.metric-card.danger { border-left-color: var(--red); }
.metric-card.danger strong { color: var(--red); }
.metric-card.accent { border-left-color: var(--gold); }
.metric-card.accent strong { color: var(--gold); }
.metric-card.light { border-left-color: #fff; }

@keyframes heroPosterIn {
  from {
    opacity: 0;
    filter: saturate(.9) contrast(.96) blur(8px);
    transform: scale(1.035) translateX(10px);
  }
  to {
    opacity: 1;
    filter: saturate(1.05) contrast(1.05) blur(0);
    transform: scale(1) translateX(0);
  }
}

@keyframes heroCopyIn {
  from {
    opacity: 0;
    filter: blur(5px);
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    filter: blur(0);
    transform: translateY(0);
  }
}

@keyframes switchMaskIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(440px, .84fr) minmax(0, 1fr);
  grid-auto-rows: 1fr;
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
  height: 100%;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
  display: flex;
  flex-direction: column;
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
  padding: 10px;
}

.player-list,
.featured-list {
  max-height: 244px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
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
  grid-template-columns: 94px minmax(0, 1fr);
  gap: 10px;
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

.battle-card.clickable {
  cursor: pointer;
}

.battle-card.clickable:hover .battle-thumb img.loaded {
  transform: scale(1.08);
}

.battle-card:focus-visible {
  outline: 2px solid var(--gold);
  outline-offset: 2px;
}

.battle-card:last-child {
  border-bottom: 1px solid var(--line);
}

.battle-thumb {
  position: relative;
  min-width: 0;
  height: 62px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, .02), rgba(255, 255, 255, .18), rgba(255, 255, 255, .02)),
    var(--fallback-cover),
    rgba(255, 255, 255, .06);
  background-size: 220% 100%, cover, auto;
  background-position: -120% 0, center, center;
  animation: cover-skeleton 1.15s ease-in-out infinite;
}

.battle-thumb img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  opacity: 0;
  filter: saturate(.92) contrast(.96);
  transform: scale(1.04);
  transition: opacity .42s ease, filter .42s ease, transform .56s cubic-bezier(.2, .72, .18, 1);
}

.battle-thumb img.loaded {
  opacity: 1;
  filter: saturate(1.04) contrast(1.02);
  transform: scale(1.02);
}

.battle-thumb:has(img.loaded) {
  animation: none;
}

.battle-thumb::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 28%, rgba(0, 0, 0, .58));
}

.battle-thumb span,
.battle-thumb i {
  position: absolute;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.battle-thumb span {
  left: 6px;
  top: 6px;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 5px;
  background: rgba(0, 0, 0, .58);
  color: #fff;
  font-size: 12px;
  font-weight: 950;
}

.battle-thumb i {
  right: 6px;
  bottom: 6px;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  background: rgba(0, 174, 236, .88);
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 900;
}

@keyframes cover-skeleton {
  0% { background-position: -120% 0, center, center; }
  100% { background-position: 120% 0, center, center; }
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

.video-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  border-radius: 4px;
  transition: background 0.2s;
  cursor: pointer;
}
.video-link.bilibili {
  background: rgba(0, 174, 236, 0.12);
  color: #00aeec;
}
.video-link.bilibili:hover {
  background: rgba(0, 174, 236, 0.25);
}
.video-link.tencent {
  background: rgba(255, 76, 76, 0.1);
  color: #ff4c4c;
}
.video-link.tencent:hover {
  background: rgba(255, 76, 76, 0.2);
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
  min-height: 300px;
  height: 100%;
}

.hero-chip-list {
  max-height: 250px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
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
  --page-bg: #dce6f4;
  --panel-bg: rgba(238, 245, 252, .70);
  --panel-strong: rgba(248, 251, 255, .82);
  --line: rgba(80, 90, 120, .55);
  --line-strong: rgba(50, 60, 90, .65);
  --text: #16202c;
  --soft: rgba(22, 32, 44, .64);
  --dim: rgba(22, 32, 44, .44);
  --green: #249e8f;
  --red: #d25a78;
  --gold: #b88a2e;
  --blue: #6752d7;
  background:
    radial-gradient(ellipse at 10% 82%, rgba(138, 101, 236, .34), transparent 34%),
    radial-gradient(ellipse at 90% 12%, rgba(77, 224, 212, .34), transparent 30%),
    linear-gradient(120deg, rgba(133, 110, 255, .18), transparent 28%),
    linear-gradient(240deg, rgba(53, 225, 211, .22), transparent 30%),
    linear-gradient(180deg, #dce6f4 0%, #f4f0fa 58%, #d5c7ee 100%);
}

.hero-insights.theme-light .topbar {
  background: var(--panel-strong);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .32);
  border-radius: 12px;
  padding: 10px 18px;
}

.hero-insights.theme-light .panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
}

.hero-insights.theme-light .metric-card {
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .70), rgba(235, 242, 252, .50)),
    var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 14px 36px rgba(49, 57, 92, .12), inset 0 1px 0 rgba(255, 255, 255, .72);
  border-radius: 12px;
  padding: 14px 18px;
}

.hero-insights.theme-light .state-panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
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
  border-bottom-color: #D1D5DB;
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
  background: #D1D5DB;
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

.hero-insights.theme-light .refresh-btn,
.hero-insights.theme-light .back-btn {
  --el-button-text-color: #4B5563;
  --el-button-hover-text-color: #4B5563;
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
  border-color: #D1D5DB;
  box-shadow: 0 4px 14px rgba(15, 23, 42, .04);
  border-radius: 10px;
}

.hero-insights.theme-light .battle-thumb {
  background: #F6F7F9;
  border-color: #D1D5DB;
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
  border-color: #D1D5DB;
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
  border-color: #9CA3AF;
  border-left-color: #B88A2E;
  background: #F3F4F6;
  box-shadow: 0 2px 12px rgba(15, 23, 42, .08);
}

.hero-insights.theme-light .battle-card:hover {
  border-color: #9CA3AF;
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
  background: rgba(255, 255, 255, .62) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .74) inset, 0 10px 24px rgba(49, 57, 92, .08) !important;
}

.hero-insights.theme-light .controls :deep(.el-select__suffix) {
  color: #4B5563;
}
.hero-insights.theme-light .controls :deep(.el-select__caret) {
  color: #4B5563;
}

.hero-insights.theme-light .title-block span,
.hero-insights.theme-light .section-title span {
  color: var(--blue);
}

.hero-insights.theme-light .title-block h1,
.hero-insights.theme-light .section-title h3 {
  color: var(--text);
}

.hero-insights.theme-light .section-title {
  border-bottom-color: rgba(255, 255, 255, .56);
}

.hero-insights.theme-light .metric-card.primary {
  background:
    linear-gradient(145deg, rgba(103, 82, 215, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.hero-insights.theme-light .metric-card.win {
  background:
    linear-gradient(145deg, rgba(36, 158, 143, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.hero-insights.theme-light .metric-card.danger {
  background:
    linear-gradient(145deg, rgba(210, 90, 120, .15), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.hero-insights.theme-light .metric-card.accent,
.hero-insights.theme-light .metric-card.light {
  background:
    linear-gradient(145deg, rgba(184, 138, 46, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.hero-insights.theme-light .metric-card:hover {
  border-color: var(--line-strong);
  border-left-color: var(--blue);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .78), rgba(234, 241, 253, .62)),
    var(--panel-strong);
  box-shadow: 0 20px 48px rgba(49, 57, 92, .16), inset 0 1px 0 rgba(255, 255, 255, .84);
}

.hero-insights.theme-light .metric-card.primary strong { color: var(--blue); }
.hero-insights.theme-light .metric-card.win strong { color: var(--green); }
.hero-insights.theme-light .metric-card.danger strong { color: var(--red); }
.hero-insights.theme-light .metric-card.accent strong,
.hero-insights.theme-light .metric-card.light strong { color: var(--gold); }

.hero-insights.theme-light .battle-card {
  background: rgba(255, 255, 255, .42);
  border-color: rgba(255, 255, 255, .62);
  box-shadow: 0 10px 24px rgba(49, 57, 92, .08);
}

.hero-insights.theme-light .battle-card:hover {
  border-color: rgba(103, 82, 215, .34);
  background: rgba(255, 255, 255, .64);
  box-shadow: 0 14px 30px rgba(49, 57, 92, .12);
}

.hero-insights.theme-light .battle-thumb {
  background: linear-gradient(135deg, rgba(103, 82, 215, .14), rgba(77, 224, 212, .18));
  border-color: rgba(255, 255, 255, .72);
}

.hero-insights.theme-light .battle-meta span {
  background: rgba(255, 255, 255, .46);
  border-color: rgba(255, 255, 255, .62);
}

.hero-insights.theme-light .player-row,
.hero-insights.theme-light .hero-chip {
  border-bottom-color: rgba(255, 255, 255, .52);
}

.hero-insights.theme-light .player-row.leader {
  background: linear-gradient(90deg, rgba(103, 82, 215, .14), rgba(77, 224, 212, .08), transparent);
}

.hero-insights.theme-light .player-row:hover,
.hero-insights.theme-light .hero-chip:hover {
  background: rgba(255, 255, 255, .48);
}

.hero-insights.theme-light .hero-chip {
  background: transparent;
}

.hero-insights.theme-light .hero-chip img {
  border-color: rgba(255, 255, 255, .78);
  box-shadow: 0 8px 18px rgba(49, 57, 92, .13);
}

.hero-insights.theme-light .battle-head b,
.hero-insights.theme-light .hero-chip.warn b {
  color: var(--red);
}

.hero-insights.theme-light .battle-head b.won,
.hero-insights.theme-light .player-metrics strong,
.hero-insights.theme-light .hero-chip b {
  color: var(--green);
}

.hero-insights.theme-light {
  --page-bg: #F5EFE4;
  --panel-bg: #FFFBF3;
  --panel-strong: #FFFBF3;
  --line: #E3D6C4;
  --line-strong: #C9B79F;
  --text: #1F2933;
  --soft: #5F6670;
  --dim: #9A8B78;
  --green: #16A34A;
  --red: #EF4444;
  --gold: #B88A2E;
  --blue: #2563EB;
  background: #F5EFE4;
}

.hero-insights.theme-light .topbar,
.hero-insights.theme-light .panel,
.hero-insights.theme-light .metric-card,
.hero-insights.theme-light .state-panel {
  background: #FFFBF3;
  border-color: #E3D6C4;
  backdrop-filter: none;
  box-shadow: 0 8px 22px rgba(88, 72, 50, .055);
}

.hero-insights.theme-light .topbar {
  box-shadow: 0 8px 24px rgba(15, 23, 42, .05);
}

.hero-insights.theme-light .title-block span,
.hero-insights.theme-light .section-title span {
  color: #B88A2E;
}

.hero-insights.theme-light .section-title {
  border-bottom-color: #E3D6C4;
}

.hero-insights.theme-light .metric-card.primary,
.hero-insights.theme-light .metric-card.win,
.hero-insights.theme-light .metric-card.danger,
.hero-insights.theme-light .metric-card.accent,
.hero-insights.theme-light .metric-card.light {
  background: #FFFBF3;
}

.hero-insights.theme-light .metric-card.primary strong { color: #2563EB; }
.hero-insights.theme-light .metric-card.win strong,
.hero-insights.theme-light .player-metrics strong,
.hero-insights.theme-light .battle-head b.won,
.hero-insights.theme-light .hero-chip b { color: #16A34A; }
.hero-insights.theme-light .metric-card.danger strong,
.hero-insights.theme-light .battle-head b,
.hero-insights.theme-light .hero-chip.warn b { color: #EF4444; }
.hero-insights.theme-light .metric-card.accent strong,
.hero-insights.theme-light .metric-card.light strong,
.hero-insights.theme-light .rank { color: #B88A2E; }

.hero-insights.theme-light .battle-card,
.hero-insights.theme-light .hero-chip {
  background: #FFFBF3;
  border-color: #E3D6C4;
  box-shadow: 0 4px 14px rgba(88, 72, 50, .04);
}

.hero-insights.theme-light .battle-thumb,
.hero-insights.theme-light .battle-meta span {
  background: #F5EFE4;
  border-color: #E3D6C4;
}

.hero-insights.theme-light .player-row.leader {
  background: linear-gradient(90deg, rgba(184, 138, 46, .1), transparent);
}

.hero-insights.theme-light .metric-card:hover,
.hero-insights.theme-light .battle-card:hover,
.hero-insights.theme-light .player-row:hover,
.hero-insights.theme-light .hero-chip:hover {
  background: #F8F1E8;
  border-color: #C9B79F;
  box-shadow: 0 2px 12px rgba(88, 72, 50, .08);
}

.hero-insights.theme-light .controls :deep(.el-select__wrapper),
.hero-insights.theme-light .controls :deep(.el-button) {
  background: #FFFBF3 !important;
  box-shadow: 0 0 0 1px #E3D6C4 inset !important;
}

.hero-insights.theme-light .hero-chip img {
  border-color: #E3D6C4;
  box-shadow: none;
}

/* Rankings-style monochrome console */
.hero-insights {
  --page-bg: #0a0a0a;
  --panel-bg: rgba(18, 18, 18, .92);
  --panel-strong: rgba(18, 18, 18, .96);
  --line: rgba(255, 255, 255, .20);
  --line-strong: rgba(255, 255, 255, .35);
  --text: #e8e8e8;
  --soft: rgba(232, 232, 232, .65);
  --dim: rgba(232, 232, 232, .38);
  --blue: #e8e8e8;
  --green: #e8e8e8;
  --red: #e8e8e8;
  --gold: #e8e8e8;
  --violet: #e8e8e8;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.hero-insights.theme-light {
  --page-bg: #f8f5ec;
  --panel-bg: rgba(255, 255, 255, .92);
  --panel-strong: rgba(255, 255, 255, .92);
  --line: rgba(0, 0, 0, .38);
  --line-strong: rgba(0, 0, 0, .45);
  --text: #1a1a1a;
  --soft: rgba(26, 26, 26, .65);
  --dim: rgba(26, 26, 26, .40);
  --blue: #1a1a1a;
  --green: #1a1a1a;
  --red: #1a1a1a;
  --gold: #1a1a1a;
  --violet: #1a1a1a;
  background:
    linear-gradient(180deg, rgba(250, 248, 240, .98), rgba(245, 242, 232, .99)),
    #f8f5ec;
}

.topbar,
.panel,
.metric-card,
.hero-visual,
.state-panel {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.topbar::before,
.panel::before,
.metric-card::before,
.hero-visual::before,
.state-panel::before {
  content: "";
  position: absolute;
  left: -1px;
  top: -1px;
  z-index: 2;
  width: 36px;
  height: 36px;
  border-left: 2px solid rgba(0, 0, 0, .12);
  border-top: 2px solid rgba(0, 0, 0, .12);
  pointer-events: none;
}

.hero-insights.theme-dark .topbar::before,
.hero-insights.theme-dark .panel::before,
.hero-insights.theme-dark .metric-card::before,
.hero-insights.theme-dark .hero-visual::before,
.hero-insights.theme-dark .state-panel::before {
  border-color: rgba(255, 255, 255, .18);
}

.topbar {
  min-height: 88px;
  padding: 14px 24px;
}

.hero-insights .brand-mark {
  border-radius: 0;
  background: rgba(0, 0, 0, .03);
  color: var(--text);
}

.title-block span,
.section-title span,
.hero-copy span {
  color: var(--soft);
  letter-spacing: 1.8px;
}

.title-block h1,
.section-title h3,
.hero-copy h2,
.metric-card strong,
.player-main strong,
.battle-body > strong,
.hero-chip-main strong {
  color: var(--text);
}

.controls :deep(.el-select__wrapper),
.controls :deep(.el-button) {
  min-height: 40px;
  border-radius: 0 !important;
  background: rgba(255, 255, 255, .60) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
}

.hero-insights.theme-dark .controls :deep(.el-select__wrapper),
.hero-insights.theme-dark .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .06) !important;
}

.refresh-btn,
.back-btn {
  --el-button-text-color: var(--text);
  --el-button-hover-text-color: var(--panel-bg);
  --el-button-hover-bg-color: var(--text);
  --el-button-hover-border-color: var(--text);
}

.toggle-track {
  border-radius: 999px;
  background: rgba(0, 0, 0, .35);
}

.toggle-track.on {
  background: var(--text);
}

.hero-insights.theme-light .toggle-thumb {
  background: #fff;
}

.hero-stage,
.content-grid,
.relationship-grid {
  gap: 18px;
  margin-top: 18px;
}

.hero-visual {
  min-height: 282px;
  background: #111;
}

.hero-bg {
  filter: saturate(.72) contrast(1.06) brightness(.94);
}

.hero-visual:hover .hero-bg {
  transform: scale(1.035);
}

.visual-shade {
  background:
    linear-gradient(90deg, rgba(0, 0, 0, .70), rgba(0, 0, 0, .38) 42%, rgba(0, 0, 0, .08)),
    linear-gradient(180deg, transparent 42%, rgba(0, 0, 0, .76));
}

.hero-copy h2 {
  font-size: clamp(38px, 4vw, 62px);
  text-shadow: 0 10px 22px rgba(0, 0, 0, .50);
}

.hero-copy p {
  color: rgba(255, 255, 255, .78);
}

.metric-grid {
  gap: 8px;
}

.metric-card {
  border-left: 1px solid var(--line);
  border-radius: 0;
  padding: 16px 18px;
  background: var(--panel-bg);
  transition: background .18s ease, border-color .18s ease, transform .18s ease;
}

.metric-card:hover {
  border-color: var(--line-strong);
  border-left-color: var(--line-strong);
  background: rgba(0, 0, 0, .035);
  box-shadow: none;
  transform: translateY(-2px);
}

.hero-insights.theme-dark .metric-card:hover {
  background: rgba(255, 255, 255, .05);
}

.metric-card.primary,
.metric-card.win,
.metric-card.danger,
.metric-card.accent,
.metric-card.light {
  border-left-color: var(--line);
}

.metric-card.primary strong,
.metric-card.win strong,
.metric-card.danger strong,
.metric-card.accent strong,
.metric-card.light strong {
  color: var(--text);
}

.panel {
  border-radius: 0;
  background: var(--panel-bg);
}

.section-title {
  min-height: 52px;
  padding: 10px 24px;
  border-bottom: 1px solid var(--line);
}

.player-list,
.featured-list,
.hero-chip-list {
  padding: 10px 24px;
}

.player-row,
.hero-chip {
  min-height: 48px;
  border-bottom-color: var(--line);
  transition: background .18s ease, padding-left .18s ease;
}

.player-row:hover,
.hero-chip:hover {
  background: rgba(0, 0, 0, .04);
}

.hero-insights.theme-dark .player-row:hover,
.hero-insights.theme-dark .hero-chip:hover {
  background: rgba(255, 255, 255, .05);
}

.player-row.leader {
  background: rgba(0, 0, 0, .035);
}

.rank,
.player-metrics strong,
.hero-chip b,
.hero-chip.warn b,
.battle-head b,
.battle-head b.won {
  color: var(--text);
}

.player-row img,
.hero-chip img {
  border-radius: 0;
  border: 1px solid var(--line);
  box-shadow: none;
}

.battle-card {
  border-radius: 0;
  border-color: var(--line);
  background: rgba(0, 0, 0, .025);
  box-shadow: none;
}

.hero-insights.theme-dark .battle-card {
  background: rgba(255, 255, 255, .035);
}

.battle-card:hover {
  border-color: var(--line-strong);
  background: rgba(0, 0, 0, .045);
}

.hero-insights.theme-dark .battle-card:hover {
  background: rgba(255, 255, 255, .06);
}

.battle-thumb {
  border-radius: 0;
  border-color: var(--line);
  background:
    linear-gradient(90deg, rgba(0, 0, 0, .02), rgba(0, 0, 0, .10), rgba(0, 0, 0, .02)),
    var(--fallback-cover),
    rgba(0, 0, 0, .04);
}

.battle-thumb span,
.battle-thumb i {
  border-radius: 0;
  background: rgba(0, 0, 0, .72);
}

.battle-thumb i {
  background: rgba(26, 26, 26, .84);
}

.battle-meta span {
  border-color: var(--line);
  background: rgba(0, 0, 0, .03);
  color: var(--soft);
}

.hero-insights.theme-dark .battle-meta span {
  background: rgba(255, 255, 255, .04);
}

.hero-chip {
  background: transparent;
}

.state-panel {
  border-radius: 0;
}

.spinner,
.switch-spinner {
  border-top-color: var(--text);
}

.hero-insights.theme-light .topbar,
.hero-insights.theme-light .panel,
.hero-insights.theme-light .metric-card,
.hero-insights.theme-light .state-panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.hero-insights.theme-light .hero-visual,
.hero-insights.theme-light .battle-card,
.hero-insights.theme-light .hero-chip,
.hero-insights.theme-light .battle-thumb,
.hero-insights.theme-light .battle-meta span {
  border-color: var(--line);
  border-radius: 0;
}

.hero-insights.theme-light .metric-card.primary,
.hero-insights.theme-light .metric-card.win,
.hero-insights.theme-light .metric-card.danger,
.hero-insights.theme-light .metric-card.accent,
.hero-insights.theme-light .metric-card.light,
.hero-insights.theme-light .battle-card,
.hero-insights.theme-light .hero-chip {
  background: var(--panel-bg);
  box-shadow: none;
}

.hero-insights.theme-light .metric-card.primary strong,
.hero-insights.theme-light .metric-card.win strong,
.hero-insights.theme-light .metric-card.danger strong,
.hero-insights.theme-light .metric-card.accent strong,
.hero-insights.theme-light .metric-card.light strong,
.hero-insights.theme-light .player-metrics strong,
.hero-insights.theme-light .battle-head b,
.hero-insights.theme-light .battle-head b.won,
.hero-insights.theme-light .hero-chip b,
.hero-insights.theme-light .hero-chip.warn b,
.hero-insights.theme-light .rank {
  color: var(--text);
}

.hero-insights.theme-light .metric-card:hover,
.hero-insights.theme-light .battle-card:hover,
.hero-insights.theme-light .player-row:hover,
.hero-insights.theme-light .hero-chip:hover {
  background: rgba(0, 0, 0, .04);
  border-color: var(--line-strong);
  box-shadow: none;
}

.hero-insights.theme-light .controls :deep(.el-select__wrapper),
.hero-insights.theme-light .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .70) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
}

.hero-insights.theme-light .topbar,
.hero-insights.theme-light .panel,
.hero-insights.theme-light .metric-card,
.hero-insights.theme-light .hero-visual,
.hero-insights.theme-light .state-panel,
.hero-insights.theme-light .battle-card,
.hero-insights.theme-light .battle-thumb,
.hero-insights.theme-light .hero-chip,
.hero-insights.theme-light .player-row img,
.hero-insights.theme-light .hero-chip img,
.hero-insights.theme-dark .topbar,
.hero-insights.theme-dark .panel,
.hero-insights.theme-dark .metric-card,
.hero-insights.theme-dark .hero-visual,
.hero-insights.theme-dark .state-panel,
.hero-insights.theme-dark .battle-card,
.hero-insights.theme-dark .battle-thumb,
.hero-insights.theme-dark .hero-chip,
.hero-insights.theme-dark .player-row img,
.hero-insights.theme-dark .hero-chip img {
  border-radius: 0;
}

.hero-insights.theme-light .brand-mark,
.hero-insights.theme-dark .brand-mark {
  border-radius: 0;
  color: var(--text);
}

.hero-insights.theme-light .title-block span,
.hero-insights.theme-light .section-title span,
.hero-insights.theme-dark .title-block span,
.hero-insights.theme-dark .section-title span {
  color: var(--soft);
}

.hero-insights .hero-copy span,
.hero-insights .hero-copy h2 {
  color: #fff;
}

.hero-insights .hero-copy p {
  color: rgba(255, 255, 255, .82);
}

.hero-insights .video-link.bilibili,
.hero-insights .video-link.tencent {
  background: rgba(0, 0, 0, .08);
  color: var(--text);
}

.hero-insights.theme-dark .video-link.bilibili,
.hero-insights.theme-dark .video-link.tencent {
  background: rgba(255, 255, 255, .08);
  color: var(--text);
}

.hero-insights .video-link.bilibili:hover,
.hero-insights .video-link.tencent:hover {
  background: rgba(0, 0, 0, .14);
}

.hero-insights.theme-dark .video-link.bilibili:hover,
.hero-insights.theme-dark .video-link.tencent:hover {
  background: rgba(255, 255, 255, .16);
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

@media (prefers-reduced-motion: reduce) {
  .hero-copy,
  .hero-switch-mask,
  .switch-spinner,
  .spinner,
  .metric-value-enter-active,
  .metric-value-leave-active {
    animation: none !important;
    transition: none !important;
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
    grid-template-columns: 1fr 1fr repeat(4, auto);
    gap: 6px;
  }

  .controls :deep(.el-select) {
    width: 100%;
  }

  .refresh-btn :deep(span),
  .back-btn :deep(span) {
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
    grid-template-columns: 78px minmax(0, 1fr);
  }
}
</style>

<style>
@import '../styles/select-dropdown.css';

.hero-insights-select-popper .hero-option {
  color: #1F2933;
}

.hero-insights-select-popper .hero-option em {
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 3px;
  min-width: 94px;
  color: #6b5f50;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.hero-insights-select-popper .hero-option em b {
  color: #111827;
  font-size: 13px;
  font-weight: 950;
}

.hero-insights-select-popper .hero-option em small {
  color: #111827;
  font-size: 11px;
  font-weight: 900;
}

.hero-insights-select-popper .el-select-dropdown__item.is-selected .hero-option em b,
.hero-insights-select-popper .el-select-dropdown__item.is-hovering .hero-option em b,
.hero-insights-select-popper .el-select-dropdown__item.is-selected .hero-option em small,
.hero-insights-select-popper .el-select-dropdown__item.is-hovering .hero-option em small {
  color: #000;
}
</style>
