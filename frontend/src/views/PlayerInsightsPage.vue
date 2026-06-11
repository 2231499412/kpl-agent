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
          @change="onPlayerChange"
        >
          <el-option v-for="player in visiblePlayers" :key="playerKey(player)" :label="player.playerName" :value="player.playerName">
            <div class="player-option">
              <img v-if="player.playerIcon || player.teamIcon" :src="player.playerIcon || player.teamIcon" :alt="player.playerName">
              <span>{{ player.playerName }}</span>
              <em>{{ player.teamName }} · {{ player.positionDesc || '-' }}</em>
              <b>{{ formatPercent(player.winRate) }}</b>
            </div>
          </el-option>
        </el-select>
        <el-button v-if="returnTarget" :icon="Back" class="refresh-btn back-btn" @click="returnToHero">返回英雄</el-button>
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
            <strong>{{ statusScore }}<el-popover placement="bottom" :width="260" trigger="click">
                <template #reference>
                  <i class="score-hint">?</i>
                </template>
                <div class="score-breakdown">
                  <b>综合状态分怎么算？</b>
                  <div class="score-row"><span>胜率表现</span><span>35%</span></div>
                  <div class="score-row"><span>KDA表现</span><span>25%</span></div>
                  <div class="score-row"><span>参团表现</span><span>20%</span></div>
                  <div class="score-row"><span>近期状态</span><span>10%</span></div>
                  <div class="score-row"><span>出场样本</span><span>10%</span></div>
                  <p class="score-note">系统会对小样本数据进行修正，避免少量比赛导致分数虚高。</p>
                </div>
              </el-popover></strong>
            <span>综合状态分</span>
          </div>
        </article>

        <aside class="metric-grid">
          <div class="metric-card primary"><span>本赛季场次</span><strong>{{ player.battleCount || 0 }}</strong><em>同位置 #{{ rankOf('avgKda') || '-' }}</em></div>
          <div class="metric-card win"><span>胜率</span><strong>{{ formatPercent(player.winRate) }}</strong><em>排名 #{{ rankOf('winRate') || '-' }}</em></div>
          <div class="metric-card light"><span>KDA</span><strong>{{ fixed(player.avgKda, 2) }}</strong><em>{{ fixed(player.avgKill, 1) }}/{{ fixed(player.avgDeath, 1) }}/{{ fixed(player.avgAssist, 1) }}</em></div>
          <div class="metric-card accent"><span>场均经济</span><strong>{{ formatCompact(player.avgGold) }}</strong><em>排名 #{{ rankOf('avgGold') || '-' }}</em></div>
          <div class="metric-card primary"><span>参团率</span><strong>{{ formatPercent(player.avgParticipationRate) }}</strong><em>排名 #{{ rankOf('avgParticipationRate') || '-' }}</em></div>
          <div class="metric-card danger"><span>近期 5 场胜率</span><strong>{{ formatPercent(recent5.winRate) }}</strong><em>KDA {{ fixed(recent5.avgKda, 2) }}</em></div>
        </aside>
      </section>

      <section class="analysis-grid">
        <article class="panel trend-panel" :key="`trend-${chartRenderKey}`">
          <PanelTitle tag="FORM" title="近期状态趋势" note="最近 5 / 10 / 赛季" />
          <div class="mini-stat-row">
            <div><span>最近5场</span><strong>{{ formatPercent(recent5.winRate) }}</strong><em>KDA {{ fixed(recent5.avgKda, 2) }}</em></div>
            <div><span>最近10场</span><strong>{{ formatPercent(recent10.winRate) }}</strong><em>KDA {{ fixed(recent10.avgKda, 2) }}</em></div>
            <div><span>赛季整体</span><strong>{{ formatPercent(player.winRate) }}</strong><em>KDA {{ fixed(player.avgKda, 2) }}</em></div>
          </div>
          <svg class="trend-chart" viewBox="0 0 420 150" role="img" aria-label="近期KDA趋势">
            <defs>
              <linearGradient id="trendLineGradient" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stop-color="#6752D7" />
                <stop offset="100%" stop-color="#4DE0D4" />
              </linearGradient>
              <linearGradient id="trendAreaGradient" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stop-color="#6752D7" stop-opacity=".24" />
                <stop offset="100%" stop-color="#4DE0D4" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line class="chart-axis" x1="18" y1="22" x2="18" y2="132" />
            <line class="chart-axis" x1="18" y1="132" x2="410" y2="132" />
            <g v-for="tick in trendAxisTicks" :key="tick.label">
              <line class="chart-grid" x1="18" :y1="tick.y" x2="410" :y2="tick.y" />
              <text class="chart-y-label" x="3" :y="tick.y + 4">{{ tick.label }}</text>
            </g>
            <polygon v-if="trendArea" class="trend-area" :points="trendArea" />
            <polyline class="trend-line" :points="trendLine" />
            <text v-for="point in trendDots" :key="`${point.key}-value`" class="chart-point-value" :x="point.x" :y="point.labelY">{{ fixed(point.value, 1) }}</text>
            <text v-for="point in trendDots" :key="`${point.key}-x`" class="chart-x-label" :x="point.x" y="146">{{ point.label }}</text>
            <circle v-for="(point, index) in trendDots" :key="point.key" :cx="point.x" :cy="point.y" :class="['trend-dot', { win: point.won }]" :style="{ animationDelay: `${index * 70 + 280}ms` }" r="4" />
          </svg>
        </article>

        <article class="panel hero-panel">
          <PanelTitle tag="HERO POOL" title="英雄池分析" note="胜率 / 熟练度 / 标签" />
          <div class="hero-list">
            <div v-for="hero in heroPool" :key="hero.heroId" class="hero-row" :class="{ signature: isSignatureHero(hero) }" @click="openHeroInsight(hero)">
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

        <article class="panel compare-panel" :key="`compare-${chartRenderKey}`">
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
        <article class="panel stage-panel" :key="`history-${chartRenderKey}`">
          <div class="section-title stage-title">
            <div>
              <span>LEAGUE HISTORY</span>
              <h3>历史赛事</h3>
            </div>
          </div>

            <div class="history-mode">
              <div class="league-trend">
                <div class="league-summary" v-if="activeTrendLeague">
                  <strong>{{ trendLeagueLabel(activeTrendLeague) }}</strong>
                  <span>{{ activeTrendLeague.games }} 局 · 胜率 {{ formatPercent(activeTrendLeague.winRate) }} · KDA {{ fixed(activeTrendLeague.avgKda, 2) }}</span>
                  <em>{{ activeTrendLeague.heroCount || 0 }} 个英雄</em>
                </div>
                <svg class="history-chart" viewBox="0 0 420 96" role="img" aria-label="历史赛事KDA趋势">
                  <defs>
                    <linearGradient id="historyLineGradient" x1="0" x2="1" y1="0" y2="0">
                      <stop offset="0%" stop-color="#D25A78" />
                      <stop offset="100%" stop-color="#6752D7" />
                    </linearGradient>
                    <linearGradient id="historyAreaGradient" x1="0" x2="0" y1="0" y2="1">
                      <stop offset="0%" stop-color="#D25A78" stop-opacity=".20" />
                      <stop offset="100%" stop-color="#6752D7" stop-opacity="0" />
                    </linearGradient>
                  </defs>
                  <line class="chart-axis" x1="18" y1="16" x2="18" y2="88" />
                  <line class="chart-axis" x1="18" y1="88" x2="410" y2="88" />
                  <g v-for="tick in historyAxisTicks" :key="tick.label">
                    <line class="chart-grid" x1="18" :y1="tick.y" x2="410" :y2="tick.y" />
                    <text class="chart-y-label compact" x="3" :y="tick.y + 3">{{ tick.label }}</text>
                  </g>
                  <polygon v-if="historyArea" class="history-area" :points="historyArea" />
                  <polyline class="history-line" :points="historyTrendLine" />
                  <text v-for="point in historyTrendDots" :key="`${point.key}-value`" class="chart-point-value compact" :x="point.x" :y="point.labelY">{{ fixed(point.value, 1) }}</text>
                  <circle
                    v-for="(point, index) in historyTrendDots"
                    :key="point.key"
                    :cx="point.x"
                    :cy="point.y"
                    :class="['history-dot', { active: point.leagueId === selectedLeagueTrendId }]"
                    :style="{ animationDelay: `${index * 55 + 240}ms` }"
                    r="4"
                    @click="selectedLeagueTrendId = point.leagueId"
                  />
                </svg>
                <div class="league-tabs-shell">
                  <button class="league-scroll-btn" type="button" aria-label="向左切换赛事" @click="scrollLeagueTabs(-1)">
                    <ArrowLeftBold />
                  </button>
                  <div ref="leagueTabsRef" class="league-tabs" @wheel.prevent="onLeagueTabsWheel">
                    <button
                      v-for="league in leagueTimeline"
                      :key="league.leagueId"
                      :class="{ active: league.leagueId === selectedLeagueTrendId }"
                      @click="selectedLeagueTrendId = league.leagueId"
                    >
                      <strong>{{ compactLeagueName(league) }}</strong>
                      <span>{{ league.games }}局 · {{ formatPercent(league.winRate) }}</span>
                    </button>
                  </div>
                  <button class="league-scroll-btn" type="button" aria-label="向右切换赛事" @click="scrollLeagueTabs(1)">
                    <ArrowRightBold />
                  </button>
                </div>
              </div>
              <div class="hero-list history-hero-list">
                <div v-for="hero in activeLeagueHeroes" :key="`${hero.leagueId}-${hero.heroId}`" class="hero-row" @click="openHeroInsight(hero)">
                  <img :src="heroIcon(hero)" :alt="hero.heroName" @error="hideBroken">
                  <div>
                    <strong>{{ hero.heroName }}</strong>
                    <span>{{ hero.games }} 局 · {{ hero.wins || 0 }} 胜 · KDA {{ fixed(hero.avgKda, 2) }}</span>
                  </div>
                  <b>{{ formatPercent(hero.winRate) }}</b>
                  <em>{{ fixed(hero.avgKill, 1) }}/{{ fixed(hero.avgDeath, 1) }}/{{ fixed(hero.avgAssist, 1) }}</em>
                </div>
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
                <a v-if="bilibiliUrl(battle)" :href="bilibiliUrl(battle)" target="_blank" rel="noopener" class="bilibili-link" title="在B站观看">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.124.929.373.267.249.391.551.391.907 0 .355-.124.657-.373.906zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/></svg>
                </a>
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeftBold, ArrowRightBold, Back, Refresh } from '@element-plus/icons-vue'
import { getTheme, setTheme } from '../utils/theme'

const route = useRoute()
const router = useRouter()
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
const stageMode = ref('stage')
const selectedLeagueTrendId = ref('')
const leagueTabsRef = ref(null)
const routeReady = ref(false)
let detailRequestId = 0
watch(theme, (value) => setTheme(value))

const player = computed(() => detail.value?.player || {})
const recentGames = computed(() => detail.value?.recentGames || [])
const heroPool = computed(() => detail.value?.heroPool || [])
const stageStats = computed(() => detail.value?.stageStats || [])
const leagueTimeline = computed(() => detail.value?.leagueTimeline || [])
const leagueHeroMatrix = computed(() => detail.value?.leagueHeroMatrix || [])
const featuredBattles = computed(() => detail.value?.featuredBattles || [])
const comparison = computed(() => detail.value?.positionComparison || {})
const compareAvg = computed(() => comparison.value?.avg || {})
const compareRank = computed(() => comparison.value?.rank || {})
const currentLeagueName = computed(() => leagues.value.find(item => item.leagueId === selectedLeagueId.value)?.leagueName || '当前赛事')
const returnTarget = computed(() => {
  if (!route.query.returnHeroId) return null
  return {
    leagueId: String(route.query.returnLeagueId || selectedLeagueId.value),
    heroId: route.query.returnHeroId,
  }
})
const activeTrendLeague = computed(() => {
  if (!leagueTimeline.value.length) return null
  return leagueTimeline.value.find(item => item.leagueId === selectedLeagueTrendId.value) || leagueTimeline.value[0]
})
const activeLeagueHeroes = computed(() => {
  const leagueId = activeTrendLeague.value?.leagueId
  if (!leagueId) return []
  return leagueHeroMatrix.value
    .filter(item => item.leagueId === leagueId)
    .sort((a, b) => num(b.games) - num(a.games) || percentNumber(b.winRate) - percentNumber(a.winRate))
    .slice(0, 8)
})
const recent5 = computed(() => summarizeRecent(recentGames.value.slice(0, 5)))
const recent10 = computed(() => summarizeRecent(recentGames.value.slice(0, 10)))
const chartRenderKey = computed(() => `${selectedLeagueId.value || 'auto'}-${selectedPlayer.value || 'player'}`)
const statusScore = computed(() => {
  const games = num(player.value.battleCount)
  const wins = Math.round(percentNumber(player.value.winRate) / 100 * games)
  const avgKda = num(player.value.avgKda)
  const participation = percentNumber(player.value.avgParticipationRate)
  const r5Kda = recent5.value.avgKda

  // 1. 修正胜率（贝叶斯平滑，先验 10 场 50%）
  const adjustedWinRate = (wins + 0.5 * 10) / (games + 10)
  const winRateScore = adjustedWinRate * 100

  // 2. KDA 得分（上限 8 归一化）
  const kdaScore = Math.min(avgKda / 8, 1) * 100

  // 3. 参团率得分（已是百分比）
  const participationScore = participation

  // 4. 近期状态得分（相对赛季偏差）
  const recentFormScore = Math.max(0, Math.min(100, 50 + (r5Kda - avgKda) * 8))

  // 5. 样本量得分（30 场满分）
  const sampleSizeScore = Math.min(games / 30, 1) * 100

  // 综合状态分（用于排名）
  const rankScore = winRateScore * 0.35
    + kdaScore * 0.25
    + participationScore * 0.20
    + recentFormScore * 0.10
    + sampleSizeScore * 0.10

  // 展示分（居中到 50 附近，范围更柔和）
  const displayScore = 50 + rankScore * 0.5
  return Math.max(0, Math.min(100, Math.round(displayScore)))
})
const trendDots = computed(() => {
  const rows = [...recentGames.value].reverse()
  const maxKda = trendMaxKda.value
  return rows.map((row, index) => {
    const value = num(row.kda)
    const x = 18 + index * (384 / Math.max(1, rows.length - 1))
    const y = 126 - (value / maxKda) * 104
    return {
      key: `${row.battleId}-${index}`,
      x,
      y,
      labelY: Math.max(12, y - 10),
      value,
      label: `${index + 1}`,
      won: Number(row.won) === 1,
    }
  })
})
const trendMaxKda = computed(() => Math.max(1, ...recentGames.value.map(row => num(row.kda))))
const trendAxisTicks = computed(() => [trendMaxKda.value, trendMaxKda.value / 2, 0].map(value => ({
  value,
  label: fixed(value, 1),
  y: 126 - (value / trendMaxKda.value) * 104,
})))
const trendLine = computed(() => trendDots.value.map(point => `${point.x},${point.y}`).join(' '))
const trendArea = computed(() => {
  if (!trendDots.value.length) return ''
  return `10,132 ${trendDots.value.map(point => `${point.x},${point.y}`).join(' ')} 410,132`
})
const historyTrendDots = computed(() => {
  const rows = [...leagueTimeline.value].reverse()
  const maxKda = historyMaxKda.value
  return rows.map((row, index) => {
    const value = num(row.avgKda)
    const x = 18 + index * (384 / Math.max(1, rows.length - 1))
    const y = 82 - (value / maxKda) * 66
    return {
      key: row.leagueId || index,
      leagueId: row.leagueId,
      x,
      y,
      labelY: Math.max(10, y - 7),
      value,
    }
  })
})
const historyMaxKda = computed(() => Math.max(1, ...leagueTimeline.value.map(row => num(row.avgKda))))
const historyAxisTicks = computed(() => [historyMaxKda.value, historyMaxKda.value / 2, 0].map(value => ({
  value,
  label: fixed(value, 1),
  y: 82 - (value / historyMaxKda.value) * 66,
})))
const historyTrendLine = computed(() => historyTrendDots.value.map(point => `${point.x},${point.y}`).join(' '))
const historyArea = computed(() => {
  if (!historyTrendDots.value.length) return ''
  return `10,88 ${historyTrendDots.value.map(point => `${point.x},${point.y}`).join(' ')} 410,88`
})

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
    const res = await request(`/api/query/player/top?sort=win&leagueId=${selectedLeagueId.value}`)
    const rows = Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.data) ? res.data.data : []
    players.value = rows.filter(item => item.playerName)
    visiblePlayers.value = players.value
    if (!selectedPlayer.value && players.value.length) selectedPlayer.value = players.value[0].playerName
  } catch (error) {
    ElMessage.error('选手列表加载失败: ' + error.message)
  } finally {
    playerLoading.value = false
  }
}

async function loadDetail(options = {}) {
  if (!selectedLeagueId.value || !selectedPlayer.value) return
  const requestId = ++detailRequestId
  if (options.clearCurrent) detail.value = null
  loading.value = true
  errorText.value = ''
  try {
    const data = await request(`/api/query/player/detail?name=${encodeURIComponent(selectedPlayer.value)}&leagueId=${selectedLeagueId.value}&limit=10`)
    if (requestId !== detailRequestId) return
    if (data?.error) throw new Error('当前赛事没有该选手数据')
    detail.value = data
    syncTrendLeague()
  } catch (error) {
    if (requestId !== detailRequestId) return
    errorText.value = error.message
    detail.value = null
  } finally {
    if (requestId === detailRequestId) loading.value = false
  }
}

async function onLeagueChange() {
  selectedPlayer.value = ''
  detail.value = null
  await loadPlayers()
  await pushPlayerRoute(true)
}

async function onPlayerChange() {
  detail.value = null
  errorText.value = ''
  loading.value = true
  await pushPlayerRoute()
}

async function refreshPage() {
  await loadLeagues()
  await loadPlayers()
  await loadDetail()
}

function returnToHero() {
  if (!returnTarget.value) return
  router.push({
    path: '/hero-insights',
    query: {
      leagueId: returnTarget.value.leagueId,
      heroId: returnTarget.value.heroId,
    },
  })
}

function openHeroInsight(hero) {
  if (!hero?.heroId) return
  router.push({
    path: '/hero-insights',
    query: {
      leagueId: selectedLeagueId.value,
      heroId: hero.heroId,
      returnLeagueId: selectedLeagueId.value,
      returnPlayer: selectedPlayer.value,
      ...(returnTarget.value
        ? {
            playerReturnLeagueId: returnTarget.value.leagueId,
            playerReturnHeroId: returnTarget.value.heroId,
          }
        : {}),
    },
  })
}

async function pushPlayerRoute(replace = false) {
  if (!selectedLeagueId.value || !selectedPlayer.value) return
  const target = {
    path: '/player-insights',
    query: {
      leagueId: selectedLeagueId.value,
      player: selectedPlayer.value,
    },
  }
  if (replace) await router.replace(target)
  else await router.push(target)
}

watch(
  () => [route.query.leagueId, route.query.player],
  async ([leagueId, playerName]) => {
    if (!routeReady.value || route.name !== 'player-insights') return
    const nextLeagueId = leagueId ? String(leagueId) : selectedLeagueId.value
    const nextPlayer = playerName ? String(playerName) : selectedPlayer.value
    const leagueChanged = nextLeagueId && nextLeagueId !== selectedLeagueId.value
    const playerChanged = nextPlayer && nextPlayer !== selectedPlayer.value

    if (leagueChanged) {
      selectedLeagueId.value = nextLeagueId
      selectedPlayer.value = nextPlayer
      detail.value = null
      await loadPlayers()
    } else if (playerChanged) {
      selectedPlayer.value = nextPlayer
      detail.value = null
    }

    await loadDetail({ clearCurrent: leagueChanged || playerChanged })
  }
)

function filterPlayers(keyword) {
  const key = String(keyword || '').trim().toLowerCase()
  visiblePlayers.value = !key
    ? players.value
    : players.value.filter(item => `${item.playerName}${item.teamName || ''}`.toLowerCase().includes(key))
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

function syncTrendLeague() {
  const rows = detail.value?.leagueTimeline || []
  if (!rows.length) {
    selectedLeagueTrendId.value = ''
    return
  }
  selectedLeagueTrendId.value = rows.some(item => item.leagueId === selectedLeagueId.value)
    ? selectedLeagueId.value
    : rows[0].leagueId
}

function scrollLeagueTabs(direction) {
  const el = leagueTabsRef.value
  if (!el) return
  el.scrollBy({
    left: direction * Math.max(220, el.clientWidth * 0.72),
    behavior: 'smooth',
  })
}

function onLeagueTabsWheel(event) {
  const el = leagueTabsRef.value
  if (!el) return
  const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX) ? event.deltaY : event.deltaX
  el.scrollLeft += delta
}

function isSignatureHero(hero) {
  return Number(hero.games || 0) >= 4 && percentNumber(hero.winRate) >= 55
}

function trendLeagueLabel(league) {
  return league?.leagueName || league?.leagueId || '历史赛事'
}

function compactLeagueName(league) {
  return String(trendLeagueLabel(league))
    .replace(/王者荣耀职业联赛/g, 'KPL')
    .replace(/KPL/g, 'KPL')
    .replace(/\s+/g, '')
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
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '-'
}

function bilibiliUrl(battle) {
  if (!battle.bvid) return null
  const base = `https://www.bilibili.com/video/${battle.bvid}`
  return battle.pageNum ? `${base}?p=${battle.pageNum}` : base
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
  if (route.query.leagueId) selectedLeagueId.value = String(route.query.leagueId)
  if (route.query.player) selectedPlayer.value = String(route.query.player)
  await loadPlayers()
  if (route.query.player) selectedPlayer.value = String(route.query.player)
  await loadDetail()
  routeReady.value = true
})
</script>

<style scoped>
.player-insights {
  --page-bg: #dce6f4;
  --panel-bg: rgba(238, 245, 252, .70);
  --panel-strong: rgba(248, 251, 255, .82);
  --line: rgba(255, 255, 255, .62);
  --line-strong: rgba(255, 255, 255, .82);
  --text: #16202c;
  --soft: rgba(22, 32, 44, .64);
  --dim: rgba(22, 32, 44, .44);
  --blue: #6752d7;
  --green: #249e8f;
  --red: #d25a78;
  --gold: #b88a2e;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 10px 14px 24px;
  position: relative;
  isolation: isolate;
  overflow-x: hidden;
  color: var(--text);
  background:
    radial-gradient(ellipse at 10% 82%, rgba(138, 101, 236, .34), transparent 34%),
    radial-gradient(ellipse at 90% 12%, rgba(77, 224, 212, .34), transparent 30%),
    linear-gradient(120deg, rgba(133, 110, 255, .18), transparent 28%),
    linear-gradient(240deg, rgba(53, 225, 211, .22), transparent 30%),
    linear-gradient(180deg, #dce6f4 0%, #f4f0fa 58%, #d5c7ee 100%);
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.player-insights,
.player-insights * { box-sizing: border-box; }

.player-insights::before {
  content: "";
  position: fixed;
  inset: 0;
  z-index: -2;
  pointer-events: none;
  background:
    repeating-linear-gradient(90deg, transparent 0 159px, rgba(73, 89, 121, .11) 160px),
    linear-gradient(180deg, rgba(255, 255, 255, .56), transparent 28%);
  mix-blend-mode: multiply;
}

.player-insights::after {
  content: "";
  position: fixed;
  inset: auto -12vw 6vh -12vw;
  z-index: -1;
  height: 28vw;
  min-height: 260px;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 60%, rgba(138, 101, 236, .38), transparent 28%),
    radial-gradient(circle at 82% 34%, rgba(77, 224, 212, .36), transparent 30%);
  filter: blur(64px);
  opacity: .62;
}

.player-insights > * {
  position: relative;
  z-index: 1;
}

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
  border-radius: 12px;
  background: var(--panel-strong);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .32);
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
  background: rgba(255, 255, 255, .58) !important;
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
  flex: 1;
}
.player-option b {
  color: #111827;
  font-style: normal;
  font-size: 12px;
  font-weight: 900;
  margin-left: auto;
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
  gap: 16px;
  margin-top: 16px;
}

.profile-card,
.metric-card,
.panel {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel-bg);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
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
.status-score{text-align:left;align-self:center;margin-top:-2px}
.status-score strong{display:block;position:relative;color:var(--green);font-size:36px;line-height:1;padding-right:30px;margin-top:20px}
.status-score span{display:block;color:var(--dim);font-size:11px;margin-top:2px}
.score-hint{position:absolute;top:-10px;right:9px;display:flex;align-items:center;justify-content:center;width:14px;height:14px;border-radius:50%;background:rgba(255,255,255,.1);color:var(--dim);font-size:9px;font-style:normal;font-weight:700;cursor:pointer;transition:background .2s}
.score-hint:hover{background:rgba(255,255,255,.2);color:var(--text)}
.score-breakdown b {
  display: block;
  font-size: 13px;
  color: #1a1a1a;
  margin-bottom: 10px;
}
.score-breakdown .score-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 12px;
  color: #374151;
  border-bottom: 1px solid #f3f4f6;
}
.score-breakdown .score-row:last-of-type {
  border-bottom: 0;
}
.score-breakdown .score-note {
  margin: 10px 0 0;
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.5;
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
  border-left: 3px solid transparent;
  box-shadow: 0 14px 36px rgba(49, 57, 92, .12), inset 0 1px 0 rgba(255, 255, 255, .72);
  transition: border-color .25s ease, background .25s ease, box-shadow .25s ease;
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
.metric-card.primary { border-left-color: var(--blue); }
.metric-card.primary strong { color: var(--blue); }
.metric-card.light { border-left-color: #fff; }
.metric-card.win { border-left-color: var(--green); }
.metric-card.win strong { color: var(--green); }
.metric-card.accent { border-left-color: var(--gold); }
.metric-card.accent strong { color: var(--gold); }
.metric-card.danger { border-left-color: var(--red); }
.metric-card.danger strong { color: var(--red); }

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 16px;
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

.stage-panel,
.featured-panel {
  height: 360px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.stage-panel > .stage-list,
.featured-panel > .featured-list {
  flex: 1;
  max-height: none;
}
.stage-title {
  min-height: 48px;
  flex: 0 0 48px;
}
.mode-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .045);
}
.mode-switch button {
  min-width: 62px;
  height: 26px;
  padding: 0 9px;
  border: 0;
  background: transparent;
  color: var(--dim);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}
.mode-switch button.active {
  background: var(--gold);
  color: #101113;
}

.history-mode {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.league-trend {
  flex: 0 0 166px;
  min-height: 0;
  padding: 7px 8px 0;
  border-bottom: 1px solid var(--line);
  overflow: hidden;
}
.league-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 10px;
  align-items: end;
}
.league-summary strong,
.league-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.league-summary strong {
  color: var(--text);
  font-size: 14px;
}
.league-summary span,
.league-summary em {
  color: var(--dim);
  font-size: 11px;
  font-style: normal;
}
.league-summary em {
  grid-column: 2;
  grid-row: 1 / span 2;
  color: var(--gold);
  font-weight: 900;
}
.history-chart {
  width: 100%;
  height: 60px;
  margin-top: 2px;
  overflow: visible;
}
.history-chart line {
  stroke: var(--line);
  stroke-width: 1;
}
.history-chart polyline {
  fill: none;
  stroke: var(--blue);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.history-chart .history-area {
  fill: url(#historyAreaGradient);
  opacity: 0;
  animation: chart-area-in .7s ease .16s forwards;
}
.history-chart .history-line {
  stroke: url(#historyLineGradient);
  stroke-width: 4;
  stroke-dasharray: 760;
  stroke-dashoffset: 760;
  filter: drop-shadow(0 5px 8px rgba(59, 167, 255, .16));
  animation: chart-line-draw .9s cubic-bezier(.22, 1, .36, 1) forwards;
}
.history-chart circle {
  fill: var(--gold);
  stroke: var(--panel-bg);
  stroke-width: 2;
  cursor: pointer;
}
.history-chart .history-dot {
  opacity: 0;
  transform: scale(.35);
  transform-box: fill-box;
  transform-origin: center;
  animation: chart-dot-in .34s cubic-bezier(.2, 1.3, .4, 1) forwards;
}
.history-chart circle.active {
  fill: var(--green);
  stroke-width: 3;
}
.league-tabs-shell {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 28px;
  align-items: stretch;
  gap: 5px;
  padding-bottom: 6px;
}
.league-scroll-btn {
  display: grid;
  place-items: center;
  width: 28px;
  min-width: 28px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .045);
  color: var(--soft);
  cursor: pointer;
}
.league-scroll-btn:hover {
  border-color: var(--gold);
  color: var(--gold);
  background: rgba(215, 180, 90, .12);
}
.league-scroll-btn svg {
  width: 14px;
  height: 14px;
}
.league-tabs {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  overscroll-behavior-x: contain;
}
.league-tabs button {
  flex: 0 0 128px;
  min-width: 0;
  padding: 5px 8px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .035);
  color: var(--soft);
  text-align: left;
  cursor: pointer;
}
.league-tabs button.active {
  border-color: var(--gold);
  background: rgba(215, 180, 90, .16);
}
.league-tabs strong,
.league-tabs span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.league-tabs strong {
  color: var(--text);
  font-size: 12px;
}
.league-tabs span {
  margin-top: 2px;
  color: var(--dim);
  font-size: 10px;
}
.history-hero-list {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}
.history-hero-list .hero-row {
  grid-template-columns: 34px minmax(0, 1fr) 58px 82px;
}
.history-hero-list .hero-row em {
  visibility: visible;
  color: var(--dim);
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
  animation: chart-card-in .46s cubic-bezier(.2, .8, .2, 1) both;
}
.mini-stat-row div:nth-child(2) {
  animation-delay: 80ms;
}
.mini-stat-row div:nth-child(3) {
  animation-delay: 160ms;
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
  overflow: visible;
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
.trend-chart .trend-area {
  fill: url(#trendAreaGradient);
  opacity: 0;
  animation: chart-area-in .72s ease .18s forwards;
}
.trend-chart .trend-line {
  stroke: url(#trendLineGradient);
  stroke-width: 4;
  stroke-dasharray: 760;
  stroke-dashoffset: 760;
  filter: drop-shadow(0 6px 10px rgba(215, 180, 90, .18));
  animation: chart-line-draw .96s cubic-bezier(.22, 1, .36, 1) forwards;
}
.trend-chart circle {
  fill: var(--red);
  stroke: var(--panel-bg);
  stroke-width: 2;
}
.trend-chart .trend-dot {
  opacity: 0;
  transform: scale(.35);
  transform-box: fill-box;
  transform-origin: center;
  animation: chart-dot-in .38s cubic-bezier(.2, 1.3, .4, 1) forwards;
}
.trend-chart circle.win { fill: var(--green); }

.trend-chart .chart-axis,
.history-chart .chart-axis {
  stroke: rgba(232, 232, 232, .32);
  stroke-width: 1.2;
}
.trend-chart .chart-grid,
.history-chart .chart-grid {
  stroke: var(--line);
  stroke-width: 1;
  stroke-dasharray: 4 6;
}
.chart-y-label,
.chart-x-label,
.chart-point-value {
  fill: var(--dim);
  font-size: 9px;
  font-weight: 800;
  text-anchor: middle;
  pointer-events: none;
}
.chart-y-label {
  text-anchor: start;
}
.chart-y-label.compact,
.chart-point-value.compact {
  font-size: 8px;
}
.chart-point-value {
  fill: var(--text);
  opacity: 0;
  animation: chart-label-in .36s ease .42s forwards;
}
.chart-x-label {
  fill: var(--dim);
}

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
  cursor: pointer;
  transition: background .2s ease, padding-left .2s ease;
}

.hero-row:hover,
.stage-row:hover,
.battle-row:hover {
  background: rgba(255, 255, 255, .06);
  padding-left: 8px;
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
  gap: 24px;
  padding: 20px 20px;
  align-content: start;
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
  height: 20px;
  background: rgba(255, 255, 255, .06);
  overflow: hidden;
  border-radius: 4px;
}
.compare-row :deep(.compare-track) {
  position: relative;
  height: 20px;
  background: rgba(255, 255, 255, .06);
  overflow: hidden;
  border-radius: 4px;
}
.compare-track i {
  position: absolute;
  left: 0;
  height: 9px;
  border-radius: 3px;
  transform: scaleX(0);
  transform-origin: left center;
  animation: compare-bar-in .72s cubic-bezier(.22, 1, .36, 1) forwards;
}
.compare-row :deep(.compare-track i) {
  position: absolute;
  left: 0;
  height: 9px;
  border-radius: 3px;
  transform: scaleX(0);
  transform-origin: left center;
  animation: compare-bar-in .72s cubic-bezier(.22, 1, .36, 1) forwards;
}
.compare-track .self {
  top: 1px;
  background: var(--gold);
  box-shadow: 0 0 14px rgba(215, 180, 90, .18);
}
.compare-row :deep(.compare-track .self) {
  top: 1px;
  background: var(--gold);
  box-shadow: 0 0 14px rgba(215, 180, 90, .18);
}
.compare-track .avg {
  bottom: 1px;
  background: rgba(59, 167, 255, .6);
  animation-delay: 120ms;
}
.compare-row :deep(.compare-track .avg) {
  bottom: 1px;
  background: rgba(59, 167, 255, .6);
  animation-delay: 120ms;
}

@keyframes chart-line-draw {
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes chart-area-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes chart-dot-in {
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes chart-label-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: .86;
    transform: translateY(0);
  }
}

@keyframes compare-bar-in {
  to {
    transform: scaleX(1);
  }
}

@keyframes chart-card-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-chart .trend-area,
  .trend-chart .trend-line,
  .trend-chart .trend-dot,
  .trend-chart .chart-point-value,
  .history-chart .history-area,
  .history-chart .history-line,
  .history-chart .history-dot,
  .history-chart .chart-point-value,
  .compare-track i,
  .mini-stat-row div {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
    stroke-dashoffset: 0 !important;
  }
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

.bilibili-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: rgba(0, 174, 236, 0.12);
  color: #00aeec;
  transition: background 0.2s;
  cursor: pointer;
}
.bilibili-link:hover {
  background: rgba(0, 174, 236, 0.25);
}

.player-insights.theme-light {
  --page-bg: #dce6f4;
  --panel-bg: rgba(238, 245, 252, .70);
  --panel-strong: rgba(248, 251, 255, .82);
  --line: rgba(255, 255, 255, .62);
  --line-strong: rgba(255, 255, 255, .82);
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

.player-insights.theme-light .topbar {
  background: var(--panel-strong);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .32);
  border-radius: 12px;
  padding: 10px 18px;
}

.player-insights.theme-light .panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
}

.player-insights.theme-light .metric-card {
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .70), rgba(235, 242, 252, .50)),
    var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 14px 36px rgba(49, 57, 92, .12), inset 0 1px 0 rgba(255, 255, 255, .72);
  border-radius: 12px;
  padding: 14px 18px;
}

.player-insights.theme-light .profile-card {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, .72), rgba(238, 245, 252, .52)),
    var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
  padding: 16px 18px;
}

.player-insights.theme-light .state-panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
}

.player-insights.theme-light .metric-card span {
  color: #6B7280;
}

.player-insights.theme-light .metric-card em {
  color: #9CA3AF;
}

.player-insights.theme-light .metric-card strong {
  color: #111827;
}

.player-insights.theme-light .metric-card.win strong { color: #16A34A; }
.player-insights.theme-light .metric-card.danger strong { color: #EF4444; }
.player-insights.theme-light .metric-card.accent strong { color: #D97706; }

.player-insights.theme-light .section-title {
  border-bottom-color: #8A9097;
  padding: 10px 18px 10px;
}

.player-insights.theme-light .section-title :deep(small),
.player-insights.theme-light .section-title small {
  color: #4B5563;
}

.player-insights.theme-light .empty-line {
  color: #4B5563;
}

.player-insights.theme-light .section-title :deep(h3),
.player-insights.theme-light .section-title h3 {
  color: #111827;
}

.player-insights.theme-light .title-block span,
.player-insights.theme-light .section-title :deep(span) {
  color: #B88A2E;
}

.player-insights.theme-light .toggle-track {
  background: #8A9097;
}

.player-insights.theme-light .toggle-track.on {
  background: #111827;
}

.player-insights.theme-light .toggle-thumb {
  background: #FFFFFF;
}

.player-insights.theme-light .theme-toggle small {
  color: #6B7280;
}

.player-insights.theme-light .refresh-btn {
  --el-button-text-color: #4B5563;
  --el-button-hover-text-color: #FFFFFF;
  --el-button-hover-bg-color: #B88A2E;
  --el-button-hover-border-color: #B88A2E;
}

.player-insights.theme-light .hero-list,
.player-insights.theme-light .stage-list,
.player-insights.theme-light .featured-list {
  padding: 10px 14px;
}

.player-insights.theme-light .player-option em {
  color: #9CA3AF;
}

.player-insights.theme-light .player-option b {
  color: #16A34A;
}

.player-insights.theme-light .profile-main span {
  color: #B88A2E;
}

.player-insights.theme-light .profile-main h2 {
  color: #111827;
}

.player-insights.theme-light .profile-main p {
  color: #4B5563;
}

.player-insights.theme-light .status-score strong {
  color: #16A34A;
}

.player-insights.theme-light .status-score span {
  color: #9CA3AF;
}

.player-insights.theme-light .score-hint {
  background: #F3F4F6;
  color: #9CA3AF;
}

.player-insights.theme-light .score-hint:hover {
  background: #E5E7EB;
  color: #111827;
}

.player-insights.theme-light .mini-stat-row div {
  background: #F6F7F9;
  border-color: #8A9097;
}

.player-insights.theme-light .mini-stat-row span,
.player-insights.theme-light .mini-stat-row em {
  color: #6B7280;
}

.player-insights.theme-light .mini-stat-row strong {
  color: #16A34A;
}

.player-insights.theme-light .trend-chart line,
.player-insights.theme-light .history-chart line {
  stroke: #8A9097;
}

.player-insights.theme-light .trend-chart polyline {
  stroke: #C9972F;
}

.player-insights.theme-light .trend-chart .trend-line {
  stroke: url(#trendLineGradient);
}

.player-insights.theme-light .trend-chart .trend-area {
  fill: url(#trendAreaGradient);
}

.player-insights.theme-light .trend-chart circle {
  fill: #EF4444;
  stroke: #FFFFFF;
}

.player-insights.theme-light .trend-chart circle.win {
  fill: #16A34A;
}

.player-insights.theme-light .history-chart polyline {
  stroke: #C9972F;
}

.player-insights.theme-light .history-chart .history-line {
  stroke: url(#historyLineGradient);
}

.player-insights.theme-light .history-chart .history-area {
  fill: url(#historyAreaGradient);
}

.player-insights.theme-light .trend-chart .chart-axis,
.player-insights.theme-light .history-chart .chart-axis {
  stroke: #9CA3AF;
}

.player-insights.theme-light .trend-chart .chart-grid,
.player-insights.theme-light .history-chart .chart-grid {
  stroke: #E5E7EB;
}

.player-insights.theme-light .chart-y-label,
.player-insights.theme-light .chart-x-label {
  fill: #6B7280;
}

.player-insights.theme-light .chart-point-value {
  fill: #111827;
}

.player-insights.theme-light .history-chart circle {
  fill: #B88A2E;
  stroke: #FFFFFF;
}

.player-insights.theme-light .history-chart circle.active {
  fill: #16A34A;
}

.player-insights.theme-light .compare-track {
  background: #F3F4F6;
}

.player-insights.theme-light .compare-track .self {
  background: #C9972F;
}

.player-insights.theme-light .compare-track .avg {
  background: #93C5FD;
}

.player-insights.theme-light .compare-row span,
.player-insights.theme-light .compare-row em,
.player-insights.theme-light .compare-row :deep(span),
.player-insights.theme-light .compare-row :deep(em) {
  color: #6B7280;
}

.player-insights.theme-light .compare-row b,
.player-insights.theme-light .compare-row :deep(b) {
  color: #111827;
}

.player-insights.theme-light .hero-row strong {
  color: #111827;
}

.player-insights.theme-light .hero-row span {
  color: #6B7280;
}

.player-insights.theme-light .hero-row b {
  color: #16A34A;
}

.player-insights.theme-light .hero-row em {
  color: #B88A2E;
}

.player-insights.theme-light .stage-row strong {
  color: #111827;
}

.player-insights.theme-light .stage-row span,
.player-insights.theme-light .stage-row em,
.player-insights.theme-light .stage-row small {
  color: #6B7280;
}

.player-insights.theme-light .stage-row b {
  color: #16A34A;
}

.player-insights.theme-light .battle-row .battle-rank {
  color: #B88A2E;
}

.player-insights.theme-light .battle-main span,
.player-insights.theme-light .battle-main em {
  color: #6B7280;
}

.player-insights.theme-light .battle-main strong {
  color: #111827;
}

.player-insights.theme-light .battle-score strong {
  color: #B88A2E;
}

.player-insights.theme-light .battle-score span {
  color: #EF4444;
}

.player-insights.theme-light .battle-score span.won {
  color: #16A34A;
}

.player-insights.theme-light .league-summary strong {
  color: #111827;
}

.player-insights.theme-light .league-summary span {
  color: #6B7280;
}

.player-insights.theme-light .league-summary em {
  color: #B88A2E;
}

.player-insights.theme-light .league-tabs button {
  background: #F6F7F9;
  border-color: #8A9097;
  color: #4B5563;
}

.player-insights.theme-light .league-tabs button.active {
  border-color: #B88A2E;
  background: rgba(184, 138, 46, .1);
}

.player-insights.theme-light .league-tabs strong {
  color: #111827;
}

.player-insights.theme-light .league-tabs span {
  color: #6B7280;
}

.player-insights.theme-light .mode-switch {
  border-color: #8A9097;
  background: #F6F7F9;
}

.player-insights.theme-light .mode-switch button {
  color: #6B7280;
}

.player-insights.theme-light .mode-switch button.active {
  background: #B88A2E;
  color: #FFFFFF;
}

.player-insights.theme-light .avatar-fallback {
  background: #F6F7F9;
  color: #B88A2E;
}

.player-insights.theme-light .controls :deep(.el-select__wrapper),
.player-insights.theme-light .controls :deep(.el-button) {
  background: #FFFFFF !important;
  box-shadow: 0 0 0 1px #8A9097 inset !important;
}

.player-insights.theme-light .metric-card:hover {
  border-color: #8A9097;
  border-left-color: #B88A2E;
  background: #F3F4F6;
  box-shadow: 0 2px 12px rgba(15, 23, 42, .08);
}

.player-insights.theme-light .hero-row:hover,
.player-insights.theme-light .stage-row:hover,
.player-insights.theme-light .battle-row:hover {
  background: #F3F4F6;
}

.player-insights.theme-light .title-block span,
.player-insights.theme-light .section-title :deep(span) {
  color: var(--blue);
}

.player-insights.theme-light .section-title {
  border-bottom-color: rgba(255, 255, 255, .56);
}

.player-insights.theme-light .metric-card.primary {
  background:
    linear-gradient(145deg, rgba(103, 82, 215, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.player-insights.theme-light .metric-card.win {
  background:
    linear-gradient(145deg, rgba(36, 158, 143, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.player-insights.theme-light .metric-card.danger {
  background:
    linear-gradient(145deg, rgba(210, 90, 120, .15), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.player-insights.theme-light .metric-card.accent,
.player-insights.theme-light .metric-card.light {
  background:
    linear-gradient(145deg, rgba(184, 138, 46, .14), rgba(255, 255, 255, .58)),
    var(--panel-bg);
}

.player-insights.theme-light .metric-card:hover {
  border-color: var(--line-strong);
  border-left-color: var(--blue);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .78), rgba(234, 241, 253, .62)),
    var(--panel-strong);
  box-shadow: 0 20px 48px rgba(49, 57, 92, .16), inset 0 1px 0 rgba(255, 255, 255, .84);
}

.player-insights.theme-light .metric-card.primary strong { color: var(--blue); }
.player-insights.theme-light .metric-card.win strong { color: var(--green); }
.player-insights.theme-light .metric-card.danger strong { color: var(--red); }
.player-insights.theme-light .metric-card.accent strong,
.player-insights.theme-light .metric-card.light strong { color: var(--gold); }

.player-insights.theme-light .mini-stat-row div,
.player-insights.theme-light .league-tabs button,
.player-insights.theme-light .league-scroll-btn {
  background: rgba(255, 255, 255, .42);
  border-color: rgba(255, 255, 255, .62);
  box-shadow: 0 8px 18px rgba(49, 57, 92, .07);
}

.player-insights.theme-light .league-tabs button.active {
  border-color: rgba(103, 82, 215, .42);
  background: linear-gradient(135deg, rgba(103, 82, 215, .16), rgba(77, 224, 212, .12));
}

.player-insights.theme-light .league-scroll-btn:hover {
  border-color: rgba(103, 82, 215, .42);
  color: var(--blue);
  background: rgba(255, 255, 255, .62);
}

.player-insights.theme-light .hero-row,
.player-insights.theme-light .stage-row,
.player-insights.theme-light .battle-row {
  border-bottom-color: rgba(255, 255, 255, .52);
}

.player-insights.theme-light .hero-row:hover,
.player-insights.theme-light .stage-row:hover,
.player-insights.theme-light .battle-row:hover {
  background: rgba(255, 255, 255, .48);
}

.player-insights.theme-light .compare-track,
.player-insights.theme-light .compare-row :deep(.compare-track) {
  background: rgba(255, 255, 255, .46);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .58);
}

.player-insights.theme-light .compare-track .self,
.player-insights.theme-light .compare-row :deep(.compare-track .self) {
  background: linear-gradient(90deg, #6752d7, #8e7cf3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}

.player-insights.theme-light .compare-track .avg,
.player-insights.theme-light .compare-row :deep(.compare-track .avg) {
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
}

.player-insights.theme-light .trend-chart .chart-axis,
.player-insights.theme-light .history-chart .chart-axis {
  stroke: rgba(22, 32, 44, .38);
}

.player-insights.theme-light .trend-chart .chart-grid,
.player-insights.theme-light .history-chart .chart-grid {
  stroke: rgba(22, 32, 44, .14);
}

.player-insights.theme-light .trend-chart circle,
.player-insights.theme-light .history-chart circle {
  stroke: rgba(255, 255, 255, .86);
}

.player-insights.theme-light .history-chart circle {
  fill: var(--blue);
}

.player-insights.theme-light .history-chart circle.active {
  fill: var(--red);
}

.player-insights.theme-light .status-score strong,
.player-insights.theme-light .mini-stat-row strong,
.player-insights.theme-light .hero-row b,
.player-insights.theme-light .stage-row b,
.player-insights.theme-light .battle-score span.won {
  color: var(--green);
}

.player-insights.theme-light .battle-score span {
  color: var(--red);
}

.player-insights.theme-light .battle-score strong,
.player-insights.theme-light .hero-row em,
.player-insights.theme-light .league-summary em {
  color: var(--gold);
}

.player-insights.theme-light .avatar-fallback {
  background: linear-gradient(135deg, rgba(103, 82, 215, .16), rgba(77, 224, 212, .14));
  color: var(--blue);
}

.player-insights.theme-light .profile-card img,
.player-insights.theme-light .avatar-fallback {
  border-color: rgba(255, 255, 255, .78);
  box-shadow: 0 12px 26px rgba(49, 57, 92, .15);
}

.player-insights.theme-light .controls :deep(.el-select__wrapper),
.player-insights.theme-light .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .62) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .74) inset, 0 10px 24px rgba(49, 57, 92, .08) !important;
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
    grid-template-columns: 1fr 1fr auto auto auto;
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

<style>
@import '../styles/select-dropdown.css';

/* 状态分弹窗 — 与下拉框同风格 */
.el-popover.el-popper {
  border: 1px solid rgba(0, 0, 0, .3) !important;
  border-radius: 0 !important;
  box-shadow: 4px 4px 0 rgba(0, 0, 0, .08) !important;
  background: #fff !important;
  padding: 14px 16px !important;
}
.el-popover.el-popper .el-popper__arrow::before {
  background: #fff !important;
  border: 1px solid rgba(0, 0, 0, .3) !important;
  border-bottom: 0 !important;
  border-right: 0 !important;
}
.score-breakdown b {
  display: block;
  font-size: 13px;
  font-weight: 800;
  color: #1a1a1a;
  margin-bottom: 10px;
}
.score-breakdown .score-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 0;
  font-size: 12px;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}
.score-breakdown .score-row:last-of-type {
  border-bottom: none;
}
.score-breakdown .score-note {
  margin: 10px 0 0;
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.5;
}
</style>
