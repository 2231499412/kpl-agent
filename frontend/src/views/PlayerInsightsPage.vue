<template>
  <main class="player-insights" :class="`theme-${theme}`">
    <section class="topbar">
      <div class="title-block">
        <span>KPL PLAYER INTELLIGENCE</span>
        <h1>选手数据详情</h1>
      </div>
      <div class="controls">
        <el-select v-model="selectedLeagueId" filterable placeholder="选择赛事" :suffix-icon="ArrowDown" @change="onLeagueChange">
          <el-option v-for="league in leagues" :key="league.leagueId" :label="league.leagueName" :value="league.leagueId" />
        </el-select>
        <el-select v-model="selectedPosition" class="lane-select" filterable placeholder="全部分路" :suffix-icon="ArrowDown" @change="onPositionFilterChange">
          <el-option label="全部分路" value="" />
          <el-option v-for="position in positionOptions" :key="position" :label="position" :value="position" />
        </el-select>
        <el-select
          ref="playerSelectRef"
          v-model="selectedPlayer"
          filterable
          remote
          reserve-keyword
          :remote-method="filterPlayers"
          :loading="playerLoading"
          placeholder="搜索选手"
          :suffix-icon="ArrowDown"
          @change="onPlayerChange"
          @focus="focusSelectedPlayerInput"
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
                  <div class="score-row"><span>赛段进程 + 出场数</span><span>基底分</span></div>
                  <div class="score-row"><span>个人表现微调</span><span>±8 分</span></div>
                  <div class="score-row"><span>队伍实力修正</span><span>±3 分</span></div>
                  <p class="score-note">走到越深、打的越多，基底分越高；个人数据和队伍实力在小范围内微调。</p>
                </div>
              </el-popover></strong>
            <span>综合状态分</span>
          </div>
        </article>

        <aside class="metric-grid">
          <div class="metric-card primary" role="button" tabindex="0" @click="openRankDialog('battleCount')" @keydown.enter="openRankDialog('battleCount')"><span>本赛季场次</span><strong>{{ player.battleCount || 0 }}</strong><em>排名 #{{ metricRank('battleCount') || '-' }}</em></div>
          <div class="metric-card win" role="button" tabindex="0" @click="openRankDialog('winRate')" @keydown.enter="openRankDialog('winRate')"><span>胜率</span><strong>{{ formatPercent(player.winRate) }}</strong><em>排名 #{{ metricRank('winRate') || '-' }}</em></div>
          <div class="metric-card light" role="button" tabindex="0" @click="openRankDialog('avgKda')" @keydown.enter="openRankDialog('avgKda')"><span>KDA</span><strong>{{ fixed(player.avgKda, 2) }}</strong><em>排名 #{{ metricRank('avgKda') || '-' }}</em></div>
          <div class="metric-card accent" role="button" tabindex="0" @click="openRankDialog('avgGold')" @keydown.enter="openRankDialog('avgGold')"><span>场均经济</span><strong>{{ formatCompact(player.avgGold) }}</strong><em>排名 #{{ metricRank('avgGold') || '-' }}</em></div>
          <div class="metric-card primary" role="button" tabindex="0" @click="openRankDialog('avgParticipationRate')" @keydown.enter="openRankDialog('avgParticipationRate')"><span>参团率</span><strong>{{ formatPercent(player.avgParticipationRate) }}</strong><em>排名 #{{ metricRank('avgParticipationRate') || '-' }}</em></div>
          <div class="metric-card danger" role="button" tabindex="0" @click="openRankDialog('recent5WinRate')" @keydown.enter="openRankDialog('recent5WinRate')"><span>近期 5 场胜率</span><strong>{{ formatPercent(recent5.winRate) }}</strong><em>排名 #{{ metricRank('recent5WinRate') || '-' }}</em></div>
        </aside>
      </section>

      <section class="analysis-grid">
        <article class="panel trend-panel" :key="`trend-${chartRenderKey}-${selectedTrendMetric}`">
          <div class="section-title trend-title">
            <div>
              <span>FORM</span>
              <h3>近期比赛走势</h3>
            </div>
            <div class="trend-tools">
              <small>{{ trendMetricMeta.note }}</small>
              <el-select v-model="selectedTrendMetric" class="trend-metric-select" size="small" :teleported="false">
                <el-option v-for="item in trendMetricOptions" :key="item.key" :label="item.label" :value="item.key" />
              </el-select>
            </div>
          </div>
          <div class="mini-stat-row">
            <div v-for="card in trendSummaryCards" :key="card.label">
              <span>{{ card.label }}</span>
              <strong>{{ card.value }}</strong>
              <em>{{ card.note }}</em>
            </div>
          </div>
          <div class="trend-explain">
            <span>折线取最近 {{ trendDots.length }} 场比赛，点上数字为{{ trendMetricMeta.label }}</span>
            <b>均值 {{ formatTrendValue(trendAverage) }}</b>
            <em>{{ trendMetricMeta.description }}</em>
          </div>
          <svg class="trend-chart" viewBox="0 0 420 150" role="img" :aria-label="`${trendMetricMeta.label}走势`">
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
            <text v-for="point in trendDots" :key="`${point.key}-value`" class="chart-point-value" :x="point.x" :y="point.labelY">{{ formatTrendValue(point.value) }}</text>
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
          <div v-if="heroPoolSummary" class="hero-pool-summary">
            <div><span>英雄数</span><strong>{{ heroPoolSummary.heroCount }}</strong></div>
            <div><span>招牌英雄</span><strong>{{ heroPoolSummary.signatureCount }}</strong></div>
            <div><span>池子胜率</span><strong>{{ fixed(heroPoolSummary.winRate, 1) }}%</strong></div>
            <div><span>平均 KDA</span><strong>{{ fixed(heroPoolSummary.avgKda, 2) }}</strong></div>
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
          <div class="compare-legend">
            <div class="legend-items">
              <span class="legend-item"><i class="swatch self" />选手数值</span>
              <span class="legend-item"><i class="swatch avg" />同分路均值</span>
            </div>
            <p class="legend-note">条形长度按选手与同分路均值的相对最大值归一化；选手条在上(紫色)，均值条在下(青色)。条越长表示该项越强。经济采用紧凑千分位(k)。</p>
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
                  <span>{{ activeTrendLeague.games }} 局 · 表现指数 {{ fixed(historyMetricValue(activeTrendLeague), 1) }} · {{ activeTrendLeague.teamResultLabel || '进程待识别' }} · KDA {{ fixed(activeTrendLeague.avgKda, 2) }}</span>
                  <em>{{ activeTrendLeague.teamName || player.teamName || '-' }} · {{ activeTrendLeague.performancePositionDesc || player.positionDesc || '-' }}</em>
                </div>
                <div class="history-score-grid" v-if="activeTrendLeague">
                  <div v-for="factor in historyScoreFactors(activeTrendLeague)" :key="factor.label">
                    <span>
                      {{ factor.label }}
                      <el-tooltip :content="factor.help" placement="top" effect="dark">
                        <QuestionFilled />
                      </el-tooltip>
                    </span>
                    <strong>{{ factor.value }}</strong>
                    <em>{{ factor.note }}</em>
                  </div>
                </div>
                <p class="history-formula" v-if="activeTrendLeague">
                  {{ activeTrendLeague.performanceFormula || '表现指数 = 赛事进程 65% + 队伍表现 20% + 同分路个人表现 15%' }}
                </p>
                <svg class="history-chart" viewBox="0 0 420 108" role="img" aria-label="历史赛事表现指数趋势">
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
                  <text v-for="point in historyTrendDots" :key="`${point.key}-x`" class="chart-x-label compact" :x="point.x" y="104">{{ point.label }}</text>
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
                      <span>{{ league.games }}局 · 大场{{ formatPercent(league.winRate) }}</span>
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
            <div
              v-for="(battle, index) in featuredBattles"
              :key="battle.battleId"
              class="battle-row"
              :class="{ clickable: Boolean(videoUrl(battle)) }"
              role="link"
              tabindex="0"
              @click="openBattleVideo(battle)"
              @keydown.enter.prevent="openBattleVideo(battle)"
            >
              <div class="battle-thumb" :style="battleThumbStyle(battle)">
                <img
                  :src="battleCover(battle)"
                  :alt="battle.heroName || '推荐代表场次'"
                  :class="{ loaded: isBattleCoverLoaded(battle) }"
                  :loading="index < 3 ? 'eager' : 'lazy'"
                  :fetchpriority="index < 3 ? 'high' : 'auto'"
                  decoding="async"
                  @load="markBattleCoverLoaded(battle)"
                  @error="fallbackBattleCover($event, battle)"
                >
                <span>{{ index + 1 }}</span>
                <i v-if="isBilibili(battle)">B站</i>
              </div>
              <div class="battle-main">
                <span>{{ battle.matchStageDesc || '赛段' }} · 第{{ battle.battleSeq || '-' }}局</span>
                <strong>{{ battle.camp1TeamName }} {{ battle.camp1Score ?? '-' }} : {{ battle.camp2Score ?? '-' }} {{ battle.camp2TeamName }}</strong>
                <em>{{ battle.heroName }} · {{ battle.killNum }}/{{ battle.deathNum }}/{{ battle.assistNum }} · KDA {{ fixed(battle.kda, 2) }}</em>
              </div>
              <div class="battle-score">
                <a v-if="videoUrl(battle)" :href="videoUrl(battle)" target="_blank" rel="noopener" :class="['video-link', isBilibili(battle) ? 'bilibili' : 'tencent']" :title="isBilibili(battle) ? '在B站观看' : '在腾讯视频观看'" @click.stop>
                  <svg v-if="isBilibili(battle)" viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M17.813 4.653h.854c1.51.054 2.769.578 3.773 1.574 1.004.995 1.524 2.249 1.56 3.76v7.36c-.036 1.51-.556 2.769-1.56 3.773s-2.262 1.524-3.773 1.56H5.333c-1.51-.036-2.769-.556-3.773-1.56S.036 18.858 0 17.347v-7.36c.036-1.511.556-2.765 1.56-3.76 1.004-.996 2.262-1.52 3.773-1.574h.774l-1.174-1.12a1.234 1.234 0 0 1-.373-.906c0-.356.124-.658.373-.907l.027-.027c.267-.249.573-.373.92-.373.347 0 .653.124.92.373L9.653 4.44c.071.071.134.142.187.213h4.267a.836.836 0 0 1 .16-.213l2.853-2.747c.267-.249.573-.373.92-.373.347 0 .662.124.929.373.267.249.391.551.391.907 0 .355-.124.657-.373.906zM5.333 7.24c-.746.018-1.373.276-1.88.773-.506.498-.769 1.13-.786 1.894v7.52c.017.764.28 1.395.786 1.893.507.498 1.134.756 1.88.773h13.334c.746-.017 1.373-.275 1.88-.773.506-.498.769-1.129.786-1.893v-7.52c-.017-.765-.28-1.396-.786-1.894-.507-.497-1.134-.755-1.88-.773zM8 11.107c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c0-.373.129-.689.386-.947.258-.257.574-.386.947-.386zm8 0c.373 0 .684.124.933.373.25.249.383.569.4.96v1.173c-.017.391-.15.711-.4.96-.249.25-.56.374-.933.374s-.684-.125-.933-.374c-.25-.249-.383-.569-.4-.96V12.44c.017-.391.15-.711.4-.96.249-.249.56-.373.933-.373z"/></svg>
                  <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </a>
                <strong>{{ fixed(battle.score, 1) }}</strong>
                <span :class="{ won: Number(battle.won) === 1 }">{{ Number(battle.won) === 1 ? '胜' : '负' }}</span>
              </div>
            </div>
          </div>
        </article>
      </section>
    </template>
    <Teleport to="body">
      <div v-if="rankDialogVisible" class="rank-overlay" :class="`theme-${theme}`" @click.self="rankDialogVisible = false">
        <div class="rank-dialog">
          <div class="rank-dialog-head">
            <strong>{{ rankDialogTitle }}</strong>
            <button class="rank-dialog-close" @click="rankDialogVisible = false">&times;</button>
          </div>
          <div class="rank-dialog-note">{{ rankDialogNote }}</div>
          <div class="rank-table-wrap">
            <table class="rank-table">
              <thead>
                <tr><th class="rank-col">#</th><th>选手</th><th class="data-col">数据</th></tr>
              </thead>
              <tbody>
                <tr v-for="row in rankDialogRows" :key="row.playerName" :class="{ active: isSelectedRankRow(row) }">
                  <td class="rank-col"><span class="rank-badge" :class="{ 'rank-top3': row.rank <= 3 }">{{ row.rank }}</span></td>
                  <td>
                    <div class="name-cell">
                      <img v-if="row.playerIcon" :src="row.playerIcon" class="cell-icon" @error="hideBroken">
                      <div class="name-text">
                        <strong>{{ shortName(row.playerName) }}</strong>
                        <span>{{ row.teamName || '-' }} · {{ row.positionDesc || '-' }}</span>
                      </div>
                    </div>
                  </td>
                  <td class="data-col">
                    <div class="rate-cell">
                      <span class="rate-num">{{ row.display }}</span>
                      <div class="rate-track"><i class="rate-bar" :style="{ width: rankBarWidth(row) }" /></div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </Teleport>
  </main>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowLeftBold, ArrowRightBold, Back, QuestionFilled, Refresh } from '@element-plus/icons-vue'
import { getTheme, setTheme } from '../utils/theme'

const route = useRoute()
const router = useRouter()
const leagues = ref(JSON.parse(localStorage.getItem('kpl_leagues') || '[]'))
const players = ref([])
const visiblePlayers = ref([])
const selectedLeagueId = ref('')
const selectedPlayer = ref('')
const playerSelectRef = ref(null)
const selectedPosition = ref('')
const playerSearchKeyword = ref('')
const detail = ref(null)
const loading = ref(false)
const playerLoading = ref(false)
const errorText = ref('')
const theme = ref(getTheme())
const stageMode = ref('stage')
const selectedLeagueTrendId = ref('')
const selectedTrendMetric = ref('winRate')
const rankDialogVisible = ref(false)
const activeRankMetric = ref('statusScore')
const leagueTabsRef = ref(null)
const routeReady = ref(false)
const loadedBattleCovers = ref({})
let detailRequestId = 0
watch(theme, (value) => setTheme(value))

const player = computed(() => detail.value?.player || {})
const recentGames = computed(() => detail.value?.recentGames || [])
const seasonTrendSummary = computed(() => detail.value?.seasonTrendSummary || {})
const heroPool = computed(() => {
  const pool = detail.value?.heroPool || []
  const signature = []
  const normal = []
  for (const hero of pool) {
    if (isSignatureHero(hero)) signature.push(hero)
    else normal.push(hero)
  }
  signature.sort((a, b) => percentNumber(b.winRate) - percentNumber(a.winRate))
  return [...signature, ...normal]
})
const heroPoolSummary = computed(() => {
  const pool = heroPool.value
  if (!pool.length) return null
  const games = pool.reduce((sum, hero) => sum + num(hero.games), 0)
  const wins = pool.reduce((sum, hero) => sum + num(hero.wins), 0)
  const signatureCount = pool.filter(hero => isSignatureHero(hero)).length
  const avgKda = pool.reduce((sum, hero) => sum + num(hero.avgKda), 0) / pool.length
  return {
    heroCount: pool.length,
    signatureCount,
    winRate: games > 0 ? (wins / games) * 100 : 0,
    avgKda,
  }
})
const stageStats = computed(() => detail.value?.stageStats || [])
const leagueTimeline = computed(() => detail.value?.leagueTimeline || [])
const leagueHeroMatrix = computed(() => detail.value?.leagueHeroMatrix || [])
const featuredBattles = computed(() => detail.value?.featuredBattles || [])
const comparison = computed(() => detail.value?.positionComparison || {})
const compareAvg = computed(() => comparison.value?.avg || {})
const compareRank = computed(() => comparison.value?.rank || {})
const selectedPlayerTop = computed(() => {
  const target = shortName(selectedPlayer.value)
  return players.value.find(item => shortName(item.playerName) === target) || {}
})
const rankMetricConfigs = {
  battleCount: { title: '本赛季场次排名', note: '按当前赛事选手小局出场数排序。', value: row => num(row.battleCount), format: value => Math.round(value).toString() },
  winRate: { title: '胜率排名', note: '按当前赛事官方选手胜率排序。', value: row => percentNumber(row.winRate), format: value => `${value.toFixed(1)}%` },
  avgKda: { title: 'KDA 排名', note: '按当前赛事选手平均 KDA 排序。', value: row => num(row.avgKda), format: value => fixed(value, 2) },
  avgGold: { title: '场均经济排名', note: '按当前赛事选手场均经济排序。', value: row => num(row.avgGold), format: value => formatCompact(value) },
  avgParticipationRate: { title: '参团率排名', note: '按当前赛事选手平均参团率排序。', value: row => percentNumber(row.avgParticipationRate), format: value => `${value.toFixed(1)}%` },
  recent5WinRate: { title: '近期 5 场胜率排名', note: '按最近 5 场大场胜率排序；不足 5 场按实际样本展示。', value: row => percentNumber(row.recent5WinRate), format: value => `${value.toFixed(1)}%` },
  statusScore: { title: '综合状态分排名', note: '基底分 = 赛段进程 + 出场数，个人 ±8、队伍 ±3 微调。', value: row => num(row.statusScore), format: value => fixed(value, 1) },
}
const activeRankConfig = computed(() => rankMetricConfigs[activeRankMetric.value] || rankMetricConfigs.statusScore)
const rankDialogTitle = computed(() => activeRankConfig.value.title)
const rankDialogNote = computed(() => activeRankConfig.value.note)
const rankDialogRows = computed(() => {
  const config = activeRankConfig.value
  return players.value
    .map(row => ({ row, value: config.value(row) }))
    .filter(item => Number.isFinite(item.value))
    .sort((a, b) => b.value - a.value || num(b.row.statusScore) - num(a.row.statusScore))
    .slice(0, 50)
    .map((item, index) => ({
      ...item.row,
      rank: index + 1,
      rawValue: item.value,
      display: config.format(item.value),
    }))
})
const trendMetricOptions = [
  {
    key: 'winRate',
    label: '胜率',
    note: '最近10场比赛',
    description: '按小局总胜率计算：本场小局胜场 / 本场小局总数，例如 5:0 为 100%。',
    suffix: '%',
    fixedMax: 100,
    decimals: 1,
    value: row => matchWinRateValue(row),
  },
  {
    key: 'kda',
    label: 'KDA',
    note: '最近10场比赛',
    description: '同一场 BO 的逐局 KDA 先求平均，再用于比赛走势和近期汇总。',
    decimals: 1,
    value: row => num(row.kda),
  },
  {
    key: 'participationRate',
    label: '参团率',
    note: '最近10场比赛',
    description: '同一场 BO 的逐局参团率先求平均，表示参与本队击杀的比例。',
    suffix: '%',
    fixedMax: 100,
    decimals: 1,
    value: row => percentNumber(row.participationRate),
  },
  {
    key: 'beHurtTotal',
    label: '承伤',
    note: '最近10场比赛',
    description: '同一场 BO 的逐局总承伤先求平均，再观察近期承压变化。',
    compact: true,
    value: row => num(row.beHurtTotal),
  },
  {
    key: 'killNum',
    label: '击杀',
    note: '最近10场比赛',
    description: '同一场 BO 的逐局击杀数先求平均，再作为这一场比赛的击杀表现。',
    decimals: 0,
    value: row => num(row.killNum),
  },
  {
    key: 'deathNum',
    label: '死亡',
    note: '最近10场比赛',
    description: '同一场 BO 的逐局死亡数先求平均，数值越低通常越好。',
    decimals: 0,
    lowerIsBetter: true,
    value: row => num(row.deathNum),
  },
  {
    key: 'hurtToHeroPerMinute',
    label: '分均伤害',
    note: '最近10场比赛',
    description: '逐局先计算“对英雄伤害 / 对局时长分钟”，同一场 BO 再求平均。',
    compact: true,
    value: row => num(row.hurtToHeroPerMinute),
  },
  {
    key: 'goldPerMinute',
    label: '分均经济',
    note: '最近10场比赛',
    description: '逐局先计算“经济 / 对局时长分钟”，同一场 BO 再求平均。',
    compact: true,
    value: row => num(row.goldPerMinute),
  },
]
const trendMetricMeta = computed(() => trendMetricOptions.find(item => item.key === selectedTrendMetric.value) || trendMetricOptions[0])

watch(featuredBattles, (items) => {
  preloadBattleCoverImages(items)
}, { flush: 'post' })

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
const seasonSummary = computed(() => summarizeSeason(seasonTrendSummary.value))
const trendSummaryCards = computed(() => [
  trendSummaryCard('最近5场比赛', recent5.value, trendMetricMeta.value.key === 'winRate' ? '小局汇总' : '5场均值'),
  trendSummaryCard('最近10场比赛', recent10.value, trendMetricMeta.value.key === 'winRate' ? '小局汇总' : '10场均值'),
  trendSummaryCard('赛季整体', seasonSummary.value, trendMetricMeta.value.key === 'winRate' ? '小局汇总' : '按比赛聚合'),
])
const chartRenderKey = computed(() => `${selectedLeagueId.value || 'auto'}-${selectedPlayer.value || 'player'}`)
const positionOptions = computed(() => {
  const order = ['对抗路', '打野', '中路', '发育路', '游走']
  return Array.from(new Set(players.value.map(item => item.positionDesc).filter(Boolean)))
    .sort((a, b) => {
      const ai = order.indexOf(a)
      const bi = order.indexOf(b)
      if (ai !== -1 || bi !== -1) return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
      return String(a).localeCompare(String(b), 'zh-Hans-CN')
    })
})
const statusScore = computed(() => {
  const backendScore = Number(selectedPlayerTop.value.statusScore)
  if (Number.isFinite(backendScore) && backendScore > 0) return Math.round(backendScore)
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
const trendRows = computed(() => [...recentGames.value.slice(0, 10)].reverse())
const trendValues = computed(() => trendRows.value.map(row => trendMetricMeta.value.value(row)))
const trendAverage = computed(() => {
  if (trendMetricMeta.value.key === 'winRate') return smallGameWinRate(trendRows.value)
  const values = trendValues.value.filter(value => Number.isFinite(value))
  if (!values.length) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
})
const trendDots = computed(() => {
  const rows = trendRows.value
  const maxValue = trendMaxValue.value
  return rows.map((row, index) => {
    const value = trendMetricMeta.value.value(row)
    const x = 18 + index * (384 / Math.max(1, rows.length - 1))
    const y = 126 - (value / maxValue) * 104
    return {
      key: `${row.matchId}-${index}`,
      x,
      y,
      labelY: Math.max(12, y - 10),
      value,
      label: `M${index + 1}`,
      won: Number(row.won) === 1,
    }
  })
})
const trendMaxValue = computed(() => {
  if (trendMetricMeta.value.fixedMax) return trendMetricMeta.value.fixedMax
  return Math.max(1, ...trendValues.value.map(value => Number.isFinite(value) ? value : 0))
})
const trendAxisTicks = computed(() => [trendMaxValue.value, trendMaxValue.value / 2, 0].map(value => ({
  value,
  label: formatTrendValue(value),
  y: 126 - (value / trendMaxValue.value) * 104,
})))
const trendLine = computed(() => trendDots.value.map(point => `${point.x},${point.y}`).join(' '))
const trendArea = computed(() => {
  if (!trendDots.value.length) return ''
  return `10,132 ${trendDots.value.map(point => `${point.x},${point.y}`).join(' ')} 410,132`
})
const historyTrendDots = computed(() => {
  const rows = [...leagueTimeline.value].reverse()
  const maxValue = historyMaxValue.value
  return rows.map((row, index) => {
    const value = historyMetricValue(row)
    const x = 18 + index * (384 / Math.max(1, rows.length - 1))
    const y = 82 - (value / maxValue) * 66
    return {
      key: row.leagueId || index,
      leagueId: row.leagueId,
      x,
      y,
      labelY: Math.max(10, y - 7),
      value,
      label: historyAxisLabel(row),
    }
  })
})
const historyUsesPerformanceIndex = computed(() => leagueTimeline.value.some(row => Number.isFinite(Number(row.performanceIndex))))
const historyMaxValue = computed(() => {
  if (historyUsesPerformanceIndex.value) return 100
  return Math.max(1, ...leagueTimeline.value.map(row => num(row.avgKda)))
})
const historyAxisTicks = computed(() => [historyMaxValue.value, historyMaxValue.value / 2, 0].map(value => ({
  value,
  label: fixed(value, 1),
  y: 82 - (value / historyMaxValue.value) * 66,
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
    const data = await request('/api/leagues?limit=100')
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
    const res = await request(`/api/query/player/top?sort=status&leagueId=${selectedLeagueId.value}`)
    const rows = Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.data) ? res.data.data : []
    players.value = rows.filter(item => item.playerName)
    applyPlayerFilter()
    const fallbackPlayers = visiblePlayers.value.length ? visiblePlayers.value : players.value
    if (!selectedPlayer.value && fallbackPlayers.length) selectedPlayer.value = fallbackPlayers[0].playerName
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
  selectedPosition.value = ''
  playerSearchKeyword.value = ''
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
  playerSearchKeyword.value = String(keyword || '').trim()
  applyPlayerFilter()
}

function onPositionFilterChange() {
  applyPlayerFilter()
}

function applyPlayerFilter() {
  const key = playerSearchKeyword.value.toLowerCase()
  visiblePlayers.value = players.value.filter(item => {
    const matchesPosition = !selectedPosition.value || item.positionDesc === selectedPosition.value
    const matchesKeyword = !key || `${item.playerName}${item.teamName || ''}${item.positionDesc || ''}`.toLowerCase().includes(key)
    return matchesPosition && matchesKeyword
  })
}

function focusSelectedPlayerInput() {
  if (!selectedPlayer.value) return
  nextTick(() => {
    window.requestAnimationFrame(() => {
      const input = playerSelectRef.value?.$el?.querySelector('.el-select__input')
      if (!input) return
      input.value = selectedPlayer.value
      input.setSelectionRange(input.value.length, input.value.length)
    })
  })
}

function summarizeRecent(rows) {
  if (!rows.length) return { games: 0, wins: 0, winRate: 0, avgKda: 0, value: 0 }
  const wins = rows.filter(row => Number(row.won) === 1).length
  const kda = rows.reduce((sum, row) => sum + num(row.kda), 0) / rows.length
  const value = trendMetricMeta.value.key === 'winRate'
    ? smallGameWinRate(rows)
    : averageMetric(rows)
  return { games: rows.length, wins, winRate: wins / rows.length, avgKda: kda, value }
}

function summarizeSeason(row) {
  const matches = Math.round(num(row?.matches))
  const wins = Math.round(num(row?.wins))
  return {
    games: matches,
    wins,
    winRate: percentNumber(row?.winRate) / 100,
    avgKda: num(row?.kda),
    value: trendMetricMeta.value.value(row || {}),
  }
}

function trendSummaryCard(label, summary, note) {
  const games = Math.round(num(summary.games))
  return {
    label,
    value: formatTrendValue(summary.value),
    note: games > 0 ? `${note} · 样本${games}场` : '暂无样本',
  }
}

function playerKey(item) {
  return `${item.playerName}-${item.teamName || ''}-${item.positionDesc || ''}`
}

function rankOf(key) {
  return compareRank.value?.[key] || 0
}

function openRankDialog(metric) {
  activeRankMetric.value = metric
  rankDialogVisible.value = true
}

function metricRank(metric) {
  const config = rankMetricConfigs[metric]
  if (!config) return 0
  const targetName = shortName(selectedPlayer.value)
  const sorted = players.value
    .map(row => ({ name: shortName(row.playerName), value: config.value(row) }))
    .filter(item => Number.isFinite(item.value))
    .sort((a, b) => b.value - a.value)
  const idx = sorted.findIndex(item => item.name === targetName)
  return idx >= 0 ? idx + 1 : 0
}

function rankBarWidth(row) {
  const rows = rankDialogRows.value
  if (!rows.length) return '0%'
  const max = rows[0].rawValue || 1
  return `${Math.min(100, (row.rawValue / max) * 100)}%`
}

function isSelectedRankRow(row) {
  return shortName(row.playerName) === shortName(selectedPlayer.value)
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

function historyAxisLabel(league) {
  const name = compactLeagueName(league)
    .replace(/^20(\d{2})年?/, '$1')
    .replace(/王者荣耀/g, '')
    .replace(/挑战者杯/g, '挑杯')
    .replace(/年度总决赛/g, '年总')
    .replace(/世界冠军杯/g, '世冠')
    .replace(/冬季冠军杯/g, '冬冠')
    .replace(/春季赛/g, '春')
    .replace(/夏季赛/g, '夏')
    .replace(/秋季赛/g, '秋')
    .replace(/正赛|选拔赛|季前赛/g, '')
  return name.length > 6 ? name.slice(0, 6) : name
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

function matchWinRateValue(row) {
  if (!row) return 0
  if (Object.prototype.hasOwnProperty.call(row, 'battleWinRate')) {
    return percentNumber(row.battleWinRate)
  }
  if (Object.prototype.hasOwnProperty.call(row, 'battleCount')) {
    const battles = num(row.battleCount)
    return battles > 0 ? (num(row.wins) / battles) * 100 : 0
  }
  if (Object.prototype.hasOwnProperty.call(row, 'won')) {
    return Number(row.won) === 1 ? 100 : 0
  }
  return percentNumber(row?.winRate)
}

function smallGameWinRate(rows) {
  const games = rows.reduce((sum, row) => sum + num(row.battleCount), 0)
  if (!games) return 0
  const wins = rows.reduce((sum, row) => sum + num(row.wins), 0)
  return (wins / games) * 100
}

function averageMetric(rows) {
  const values = rows
    .map(row => trendMetricMeta.value.value(row))
    .filter(value => Number.isFinite(value))
  return values.length ? values.reduce((sum, item) => sum + item, 0) / values.length : 0
}

function historyMetricValue(row) {
  const score = Number(row?.performanceIndex)
  return Number.isFinite(score) ? score : num(row?.avgKda)
}

function performancePeerLabel(row) {
  const count = Number(row?.performancePeerCount)
  return Number.isFinite(count) && count > 0 ? `同分路 ${count} 人` : '同分路样本待计算'
}

function historyScoreFactors(row) {
  const breakdown = row?.performanceBreakdown || {}
  return [
    {
      label: '赛事进程',
      value: fixed(breakdown.progressScore, 1),
      note: row?.teamLastStageDesc || row?.teamResultLabel || '根据最终阶段',
      help: '占总指数 65%。根据队伍该赛事最后一场阶段和输赢推断：冠军最高，其次亚军、四强、八强、淘汰赛、常规赛/小组赛。',
    },
    {
      label: '队伍表现',
      value: fixed(breakdown.teamScore, 1),
      note: `大场${formatMaybePercent(row?.teamMatchWinRate)} · 小局${formatMaybePercent(row?.teamBattleWinRate)}`,
      help: '占总指数 20%。在同赛事所有队伍中比较：大场胜率 40%、小局胜率 35%、场均小局净胜 25%。',
    },
    {
      label: '个人表现',
      value: fixed(breakdown.playerScore, 1),
      note: performancePeerLabel(row),
      help: '占总指数 15%。只和同赛事、同分路选手比较，按分路权重综合 KDA、分均经济、参团率、分均伤害、分均承伤和出场局数的百分位。',
    },
  ]
}

function formatMaybePercent(value) {
  const n = Number(value)
  return Number.isFinite(n) ? `${percentNumber(n).toFixed(1)}%` : '-'
}

function formatPercent(value) {
  return `${percentNumber(value).toFixed(1)}%`
}

function fixed(value, digits = 1) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '-'
}

function videoUrl(battle) {
  if (!battle.bvid) return null
  if (battle.bvid.startsWith('BV')) {
    const base = `https://www.bilibili.com/video/${battle.bvid}`
    // pageNum 优先，否则用 battleSeq（第几局=分P）
    const p = battle.pageNum || battle.battleSeq
    return p && p > 1 ? `${base}?p=${p}` : base
  }
  if (battle.bvid.startsWith('http')) return battle.bvid
  return null
}

function isBilibili(battle) {
  return battle.bvid && battle.bvid.startsWith('BV')
}

function heroPoster(hero) {
  const heroId = hero?.heroId
  return heroId
    ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${heroId}/${heroId}-bigskin-1.jpg`
    : heroIcon(hero)
}

function battleCover(battle) {
  if (isBilibili(battle)) {
    return `/api/query/video/cover-image?bvid=${encodeURIComponent(battle.bvid)}`
  }
  return heroPoster(battle)
}

function battleThumbStyle(battle) {
  const fallback = heroPoster(battle)
  return fallback ? { '--fallback-cover': `url("${fallback}")` } : {}
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

function fallbackBattleCover(event, battle) {
  const fallback = heroPoster(battle)
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

function openBattleVideo(battle) {
  const url = videoUrl(battle)
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

function formatCompact(value) {
  const n = num(value)
  if (n >= 10000) return `${(n / 1000).toFixed(1)}k`
  return Math.round(n).toString()
}

function formatMetric(value, compact = false) {
  return compact ? formatCompact(value) : fixed(value, 1)
}

function formatTrendValue(value) {
  const metric = trendMetricMeta.value
  const n = num(value)
  if (metric.compact) return formatCompact(n)
  const decimals = Number.isFinite(metric.decimals) ? metric.decimals : 1
  return `${fixed(n, decimals)}${metric.suffix || ''}`
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
  --metric-blue: #8fb7d2;
  --metric-green: #86c7a4;
  --metric-red: #d28a80;
  --metric-gold: #d3b36a;
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
.controls :deep(.lane-select) { width: 132px; }
.controls :deep(.el-select__wrapper),
.controls :deep(.el-button) {
  min-height: 34px;
  border-radius: 0 !important;
  background: rgba(255, 255, 255, .06) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
  color: var(--text) !important;
}
.controls :deep(.el-select__placeholder),
.controls :deep(.el-select__selected-item),
.controls :deep(.el-select__input) {
  color: var(--text) !important;
}
.controls :deep(.el-select__placeholder.is-transparent) {
  color: var(--soft) !important;
}

.controls :deep(.el-select__suffix) {
  color: var(--soft);
}
.controls :deep(.el-select__caret) {
  color: var(--soft);
}

.refresh-btn {
  --el-button-text-color: var(--text);
  --el-button-hover-text-color: var(--text);
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
  min-height: calc(100vh - 120px);
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
  box-shadow: 0 1px 4px rgba(0, 0, 0, .08);
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
  cursor: pointer;
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
.metric-card:focus-visible {
  outline: 2px solid var(--gold);
  outline-offset: 2px;
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
/* hero panel uses flex so the summary footer pins to the bottom,
   filling the dead space without resizing the card. */
.hero-panel,
.compare-panel {
  display: flex;
  flex-direction: column;
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

.trend-title {
  align-items: center;
}

.trend-tools {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.trend-tools small {
  color: var(--dim);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.trend-metric-select {
  width: 116px;
}

.trend-metric-select :deep(.el-select__wrapper) {
  min-height: 28px;
  border-radius: 0 !important;
  background: rgba(255, 255, 255, .05) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
}

.trend-explain {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px 10px;
  padding: 8px 12px 0;
  color: var(--dim);
  font-size: 11px;
  line-height: 1.35;
}

.trend-explain span,
.trend-explain em {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend-explain b {
  color: var(--text);
  font-size: 12px;
  font-weight: 950;
  white-space: nowrap;
}

.trend-explain em {
  grid-column: 1 / -1;
  font-style: normal;
}

.stage-panel,
.featured-panel {
  height: 460px;
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
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  min-height: 0;
  padding: 9px 12px 0;
  border-bottom: 1px solid var(--line);
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
.history-score-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
  margin-top: 6px;
}
.history-score-grid div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  align-items: center;
  min-width: 0;
  padding: 4px 7px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .035);
}
.history-score-grid span,
.history-score-grid em {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: var(--dim);
  font-size: 10px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-score-grid span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-weight: 900;
}
.history-score-grid span svg {
  width: 11px;
  height: 11px;
  flex: 0 0 11px;
  color: var(--gold);
}
.history-score-grid strong {
  display: block;
  grid-column: 2;
  grid-row: 1 / span 2;
  margin-left: 6px;
  color: var(--text);
  font-size: 15px;
  font-weight: 950;
}
.history-score-grid em {
  grid-column: 1;
}
.history-formula {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--dim);
  font-size: 10px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-chart {
  width: 100%;
  height: 92px;
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
  position: relative;
  z-index: 5;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 28px;
  align-items: stretch;
  gap: 5px;
  margin-top: 2px;
  padding-bottom: 6px;
  background: var(--panel-bg);
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
  z-index: 0;
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
/* hero pool footer summary — fills the bottom space under the hero list */
.hero-pool-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  padding: 10px 8px 12px;
  margin-top: auto;
  border-top: 1px solid var(--line);
}
.hero-pool-summary div {
  padding: 6px 4px;
  text-align: center;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, .035);
}
.hero-pool-summary span {
  display: block;
  color: var(--dim);
  font-size: 10px;
  font-weight: 800;
}
.hero-pool-summary strong {
  display: block;
  margin-top: 2px;
  color: var(--gold);
  font-size: 16px;
  font-weight: 950;
  line-height: 1.1;
}
.hero-row,
.stage-row,
.battle-row {
  border-bottom: 1px solid var(--line);
  transition: background .2s ease, padding-left .2s ease;
}

.hero-row,
.stage-row,
.battle-row.clickable {
  cursor: pointer;
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
  gap: 18px;
  padding: 16px 20px;
  align-content: start;
}
/* legend / chart-info footer — pins to the bottom, fills dead space */
.compare-legend {
  margin-top: auto;
  padding: 10px 20px 12px;
  border-top: 1px solid var(--line);
}
.compare-legend .legend-items {
  display: flex;
  flex-direction: row;
  gap: 14px;
}
.compare-legend .legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--soft);
  font-size: 11px;
  font-weight: 800;
}
.compare-legend .swatch {
  display: inline-block;
  width: 18px;
  height: 7px;
  border-radius: 3px;
}
.compare-legend .swatch.self {
  background: linear-gradient(90deg, #6752D7, #8E7CF3);
  box-shadow: 0 0 10px rgba(103, 82, 215, .22);
}
.compare-legend .swatch.avg {
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
}
.compare-legend .legend-note {
  margin: 8px 0 0;
  color: var(--dim);
  font-size: 10px;
  line-height: 1.5;
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
  background: linear-gradient(90deg, #6752D7, #8E7CF3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}
.compare-row :deep(.compare-track .self) {
  top: 1px;
  background: linear-gradient(90deg, #6752D7, #8E7CF3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}
.compare-track .avg {
  bottom: 1px;
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
  animation-delay: 120ms;
}
.compare-row :deep(.compare-track .avg) {
  bottom: 1px;
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
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
  grid-template-columns: 92px minmax(0, 1fr) 58px;
  align-items: center;
  gap: 10px;
  min-height: 74px;
  padding: 8px 4px;
  transition: background .2s ease, border-color .2s ease;
}

.battle-row.clickable:hover .battle-thumb img.loaded {
  transform: scale(1.08);
}

.battle-row:focus-visible {
  outline: 2px solid var(--gold);
  outline-offset: 2px;
}

.battle-thumb {
  position: relative;
  min-width: 0;
  height: 58px;
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
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
}
.battle-score strong {
  color: var(--gold);
  font-size: 18px;
}
.battle-score span {
  color: var(--red);
  font-size: 12px;
  font-weight: 900;
}
.battle-score span.won { color: var(--green); }

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

.theme-light {
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

.theme-light .topbar {
  background: var(--panel-strong);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .32);
  border-radius: 12px;
  padding: 10px 18px;
}

.theme-light .panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
}

.theme-light .metric-card {
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .70), rgba(235, 242, 252, .50)),
    var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 14px 36px rgba(49, 57, 92, .12), inset 0 1px 0 rgba(255, 255, 255, .72);
  border-radius: 12px;
  padding: 14px 18px;
}

.theme-light .profile-card {
  background:
    linear-gradient(135deg, rgba(255, 255, 255, .72), rgba(238, 245, 252, .52)),
    var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
  padding: 16px 18px;
}

.theme-light .state-panel {
  background: var(--panel-bg);
  border-color: var(--line);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .30);
  border-radius: 12px;
}

.theme-light .metric-card span {
  color: #6B7280;
}

.theme-light .metric-card em {
  color: #9CA3AF;
}

.theme-light .metric-card strong {
  color: #111827;
}

.theme-light .metric-card.win strong { color: #16A34A; }
.theme-light .metric-card.danger strong { color: #EF4444; }
.theme-light .metric-card.accent strong { color: #D97706; }

.theme-light .section-title {
  border-bottom-color: #8A9097;
  padding: 10px 18px 10px;
}

.theme-light .section-title :deep(small),
.theme-light .section-title small {
  color: #4B5563;
}

.theme-light .empty-line {
  color: #4B5563;
}

.theme-light .section-title :deep(h3),
.theme-light .section-title h3 {
  color: #111827;
}

.theme-light .title-block span,
.theme-light .section-title :deep(span) {
  color: #B88A2E;
}

.player-insights .toggle-track {
  border-radius: 999px;
  background: rgba(0, 0, 0, .35);
}

.player-insights .toggle-track.on {
  background: var(--text);
}

.theme-light .toggle-track {
  background: #8A9097;
}

.theme-light .toggle-track.on {
  background: #111827;
}

.theme-light .toggle-thumb {
  background: #FFFFFF;
}

.theme-light .theme-toggle small {
  color: #6B7280;
}

.theme-light .refresh-btn {
  --el-button-text-color: #4B5563;
  --el-button-hover-text-color: #FFFFFF;
  --el-button-hover-bg-color: #B88A2E;
  --el-button-hover-border-color: #B88A2E;
}

.theme-light .hero-list,
.theme-light .stage-list,
.theme-light .featured-list {
  padding: 10px 14px;
}

.theme-light .player-option em {
  color: #9CA3AF;
}

.theme-light .player-option b {
  color: #16A34A;
}

.theme-light .profile-main span {
  color: #B88A2E;
}

.theme-light .profile-main h2 {
  color: #111827;
}

.theme-light .profile-main p {
  color: #4B5563;
}

.theme-light .status-score strong {
  color: #16A34A;
}

.theme-light .status-score span {
  color: #9CA3AF;
}

.theme-light .score-hint {
  background: #F3F4F6;
  color: #9CA3AF;
}

.theme-light .score-hint:hover {
  background: #E5E7EB;
  color: #111827;
}

.theme-light .mini-stat-row div {
  background: #F6F7F9;
  border-color: #8A9097;
}

.theme-light .mini-stat-row span,
.theme-light .mini-stat-row em {
  color: #6B7280;
}

.theme-light .mini-stat-row strong {
  color: #16A34A;
}

.theme-light .hero-pool-summary div {
  border-color: #E3D6C4;
  background: rgba(0, 0, 0, .02);
}
.theme-light .hero-pool-summary span { color: #9A8B78; }
.theme-light .hero-pool-summary strong { color: #B88A2E; }

.theme-light .trend-chart line,
.theme-light .history-chart line {
  stroke: #8A9097;
}

.theme-light .trend-chart polyline {
  stroke: #C9972F;
}

.theme-light .trend-chart .trend-line {
  stroke: url(#trendLineGradient);
}

.theme-light .trend-chart .trend-area {
  fill: url(#trendAreaGradient);
}

.theme-light .trend-chart circle {
  fill: #EF4444;
  stroke: #FFFFFF;
}

.theme-light .trend-chart circle.win {
  fill: #16A34A;
}

.theme-light .history-chart polyline {
  stroke: #C9972F;
}

.theme-light .history-chart .history-line {
  stroke: url(#historyLineGradient);
}

.theme-light .history-chart .history-area {
  fill: url(#historyAreaGradient);
}

.theme-light .trend-chart .chart-axis,
.theme-light .history-chart .chart-axis {
  stroke: #9CA3AF;
}

.theme-light .trend-chart .chart-grid,
.theme-light .history-chart .chart-grid {
  stroke: #E5E7EB;
}

.theme-light .chart-y-label,
.theme-light .chart-x-label {
  fill: #6B7280;
}

.theme-light .chart-point-value {
  fill: #111827;
}

.theme-light .history-chart circle {
  fill: #B88A2E;
  stroke: #FFFFFF;
}

.theme-light .history-chart circle.active {
  fill: #16A34A;
}

.theme-light .compare-track {
  background: #F3F4F6;
}

.theme-light .compare-track .self {
  background: #C9972F;
}

.theme-light .compare-track .avg {
  background: #93C5FD;
}

.theme-light .compare-row span,
.theme-light .compare-row em,
.theme-light .compare-row :deep(span),
.theme-light .compare-row :deep(em) {
  color: #6B7280;
}

.theme-light .compare-row b,
.theme-light .compare-row :deep(b) {
  color: #111827;
}

.theme-light .compare-legend {
  border-top-color: #E3D6C4;
}
.theme-light .compare-legend .legend-item { color: #5F6670; }
.theme-light .compare-legend .legend-note { color: #9A8B78; }

.theme-light .hero-row strong {
  color: #111827;
}

.theme-light .hero-row span {
  color: #6B7280;
}

.theme-light .hero-row b {
  color: #16A34A;
}

.theme-light .hero-row em {
  color: #B88A2E;
}

.theme-light .stage-row strong {
  color: #111827;
}

.theme-light .stage-row span,
.theme-light .stage-row em,
.theme-light .stage-row small {
  color: #6B7280;
}

.theme-light .stage-row b {
  color: #16A34A;
}

.theme-light .battle-thumb {
  border-color: #E3D6C4;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, .18), rgba(255, 255, 255, .56), rgba(255, 255, 255, .18)),
    var(--fallback-cover),
    #F5EFE4;
}

.theme-light .battle-main span,
.theme-light .battle-main em {
  color: #6B7280;
}

.theme-light .battle-main strong {
  color: #111827;
}

.theme-light .battle-score strong {
  color: #B88A2E;
}

.theme-light .battle-score span {
  color: #EF4444;
}

.theme-light .battle-score span.won {
  color: #16A34A;
}

.theme-light .league-summary strong {
  color: #111827;
}

.theme-light .league-summary span {
  color: #6B7280;
}

.theme-light .league-summary em {
  color: #B88A2E;
}

.theme-light .league-tabs button {
  background: #F6F7F9;
  border-color: #8A9097;
  color: #4B5563;
}

.theme-light .league-tabs button.active {
  border-color: #B88A2E;
  background: rgba(184, 138, 46, .1);
}

.theme-light .league-tabs strong {
  color: #111827;
}

.theme-light .league-tabs span {
  color: #6B7280;
}

.theme-light .mode-switch {
  border-color: #8A9097;
  background: #F6F7F9;
}

.theme-light .mode-switch button {
  color: #6B7280;
}

.theme-light .mode-switch button.active {
  background: #B88A2E;
  color: #FFFFFF;
}

.theme-light .avatar-fallback {
  background: #F6F7F9;
  color: #B88A2E;
}

.theme-light .controls :deep(.el-select__wrapper),
.theme-light .controls :deep(.el-button) {
  background: #FFFFFF !important;
  box-shadow: 0 0 0 1px #8A9097 inset !important;
}

.theme-light .metric-card:hover {
  border-color: #8A9097;
  border-left-color: #B88A2E;
  background: #F3F4F6;
  box-shadow: 0 2px 12px rgba(15, 23, 42, .08);
}

.theme-light .hero-row:hover,
.theme-light .stage-row:hover,
.theme-light .battle-row:hover {
  background: #F3F4F6;
}

.theme-light .title-block span,
.theme-light .section-title :deep(span) {
  color: var(--blue);
}

.theme-light .section-title {
  border-bottom-color: rgba(255, 255, 255, .56);
}

.theme-light .metric-card.primary,
.theme-light .metric-card.win,
.theme-light .metric-card.danger,
.theme-light .metric-card.accent,
.theme-light .metric-card.light {
  background: #FFFFFF;
}

.theme-light .metric-card:hover {
  border-color: var(--line-strong);
  border-left-color: var(--blue);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .78), rgba(234, 241, 253, .62)),
    var(--panel-strong);
  box-shadow: 0 20px 48px rgba(49, 57, 92, .16), inset 0 1px 0 rgba(255, 255, 255, .84);
}

.theme-light .metric-card.primary strong { color: var(--blue); }
.theme-light .metric-card.win strong { color: var(--green); }
.theme-light .metric-card.danger strong { color: var(--red); }
.theme-light .metric-card.accent strong,
.theme-light .metric-card.light strong { color: var(--gold); }

.theme-light .mini-stat-row div,
.theme-light .league-tabs button,
.theme-light .league-scroll-btn {
  background: rgba(255, 255, 255, .42);
  border-color: rgba(255, 255, 255, .62);
  box-shadow: 0 8px 18px rgba(49, 57, 92, .07);
}

.theme-light .league-tabs button.active {
  border-color: rgba(103, 82, 215, .42);
  background: linear-gradient(135deg, rgba(103, 82, 215, .16), rgba(77, 224, 212, .12));
}

.theme-light .league-scroll-btn:hover {
  border-color: rgba(103, 82, 215, .42);
  color: var(--blue);
  background: rgba(255, 255, 255, .62);
}

.theme-light .hero-row,
.theme-light .stage-row,
.theme-light .battle-row {
  border-bottom-color: rgba(255, 255, 255, .52);
}

.theme-light .hero-row:hover,
.theme-light .stage-row:hover,
.theme-light .battle-row:hover {
  background: rgba(255, 255, 255, .48);
}

.theme-light .compare-track,
.theme-light .compare-row :deep(.compare-track) {
  background: rgba(255, 255, 255, .46);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .58);
}

.theme-light .compare-track .self,
.theme-light .compare-row :deep(.compare-track .self) {
  background: linear-gradient(90deg, #6752d7, #8e7cf3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}

.theme-light .compare-track .avg,
.theme-light .compare-row :deep(.compare-track .avg) {
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
}

.theme-light .trend-chart .chart-axis,
.theme-light .history-chart .chart-axis {
  stroke: rgba(22, 32, 44, .38);
}

.theme-light .trend-chart .chart-grid,
.theme-light .history-chart .chart-grid {
  stroke: rgba(22, 32, 44, .14);
}

.theme-light .trend-chart circle,
.theme-light .history-chart circle {
  stroke: rgba(255, 255, 255, .86);
}

.theme-light .history-chart circle {
  fill: var(--blue);
}

.theme-light .history-chart circle.active {
  fill: var(--red);
}

.theme-light .status-score strong,
.theme-light .mini-stat-row strong,
.theme-light .hero-row b,
.theme-light .stage-row b,
.theme-light .battle-score span.won {
  color: var(--green);
}

.theme-light .battle-score span {
  color: var(--red);
}

.theme-light .battle-score strong,
.theme-light .hero-row em,
.theme-light .league-summary em {
  color: var(--gold);
}

.theme-light .avatar-fallback {
  background: linear-gradient(135deg, rgba(103, 82, 215, .16), rgba(77, 224, 212, .14));
  color: var(--blue);
}

.theme-light .profile-card img,
.theme-light .avatar-fallback {
  border-color: rgba(255, 255, 255, .78);
  box-shadow: 0 12px 26px rgba(49, 57, 92, .15);
}

.theme-light .controls :deep(.el-select__wrapper),
.theme-light .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .62) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .74) inset, 0 10px 24px rgba(49, 57, 92, .08) !important;
}

.theme-light {
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
  --metric-blue: #2563EB;
  --metric-green: #16A34A;
  --metric-red: #EF4444;
  --metric-gold: #B88A2E;
  background: #F5EFE4;
}

.theme-light .topbar,
.theme-light .panel,
.theme-light .metric-card,
.theme-light .profile-card,
.theme-light .state-panel {
  background: #FFFBF3;
  border-color: #E3D6C4;
  backdrop-filter: none;
  box-shadow: 0 8px 22px rgba(88, 72, 50, .055);
}

.theme-light .topbar {
  box-shadow: 0 1px 4px rgba(15, 23, 42, .06);
}

.theme-light .title-block span,
.theme-light .section-title :deep(span) {
  color: #B88A2E;
}

.theme-light .section-title {
  border-bottom-color: #E3D6C4;
}

.theme-light .metric-card.primary strong { color: #2563EB; }
.theme-light .metric-card.win strong,
.theme-light .status-score strong,
.theme-light .mini-stat-row strong,
.theme-light .hero-row b,
.theme-light .stage-row b,
.theme-light .battle-score span.won { color: #16A34A; }
.theme-light .metric-card.danger strong,
.theme-light .battle-score span { color: #EF4444; }
.theme-light .metric-card.accent strong,
.theme-light .metric-card.light strong,
.theme-light .battle-score strong,
.theme-light .hero-row em,
.theme-light .league-summary em { color: #B88A2E; }

.theme-light .mini-stat-row div,
.theme-light .league-tabs button,
.theme-light .league-scroll-btn,
.theme-light .avatar-fallback {
  background: #F5EFE4;
  border-color: #E3D6C4;
  box-shadow: none;
}

.theme-light .league-tabs button.active {
  border-color: #B88A2E;
  background: rgba(184, 138, 46, .1);
}

.theme-light .metric-card:hover,
.theme-light .hero-row:hover,
.theme-light .stage-row:hover,
.theme-light .battle-row:hover {
  background: #F8F1E8;
  border-color: #C9B79F;
  box-shadow: 0 2px 12px rgba(88, 72, 50, .08);
}

.theme-light .controls :deep(.el-select__wrapper),
.theme-light .controls :deep(.el-button) {
  background: #FFFBF3 !important;
  box-shadow: 0 0 0 1px #E3D6C4 inset !important;
}

.theme-light .trend-chart .trend-line {
  stroke: url(#trendLineGradient);
  filter: drop-shadow(0 6px 10px rgba(103, 82, 215, .20));
}

.theme-light .trend-chart .trend-area {
  fill: url(#trendAreaGradient);
}

.theme-light .history-chart .history-line {
  stroke: url(#historyLineGradient);
  filter: drop-shadow(0 5px 8px rgba(210, 90, 120, .18));
}

.theme-light .history-chart .history-area {
  fill: url(#historyAreaGradient);
}

.theme-light .trend-chart circle {
  fill: #D25A78;
  stroke: #FFFBF3;
}

.theme-light .trend-chart circle.win {
  fill: #249E8F;
}

.theme-light .history-chart circle {
  fill: #6752D7;
  stroke: #FFFBF3;
}

.theme-light .history-chart circle.active {
  fill: #D25A78;
}

.theme-light .compare-track,
.theme-light .compare-row :deep(.compare-track) {
  background: rgba(255, 251, 243, .76);
  box-shadow: inset 0 0 0 1px rgba(227, 214, 196, .72);
}

.theme-light .compare-track .self,
.theme-light .compare-row :deep(.compare-track .self) {
  background: linear-gradient(90deg, #6752D7, #8E7CF3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}

.theme-light .compare-track .avg,
.theme-light .compare-row :deep(.compare-track .avg) {
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
}

.theme-light .trend-chart .chart-axis,
.theme-light .history-chart .chart-axis {
  stroke: rgba(22, 32, 44, .38);
}

.theme-light .trend-chart .chart-grid,
.theme-light .history-chart .chart-grid {
  stroke: rgba(22, 32, 44, .14);
}

.theme-light .profile-card img,
.theme-light .avatar-fallback {
  border-color: #E3D6C4;
  box-shadow: none;
}

.player-insights .trend-chart circle {
  fill: #D25A78;
  stroke: var(--panel-bg);
}

.player-insights .trend-chart circle.win {
  fill: #249E8F;
}

.player-insights .history-chart circle {
  fill: #6752D7;
  stroke: var(--panel-bg);
}

.player-insights .history-chart circle.active {
  fill: #D25A78;
}

.player-insights .compare-track .self,
.player-insights .compare-row :deep(.compare-track .self) {
  background: linear-gradient(90deg, #6752D7, #8E7CF3);
  box-shadow: 0 0 16px rgba(103, 82, 215, .22);
}

.player-insights .compare-track .avg,
.player-insights .compare-row :deep(.compare-track .avg) {
  background: linear-gradient(90deg, rgba(77, 224, 212, .70), rgba(36, 158, 143, .70));
}

.player-insights .metric-card.primary { border-left-color: var(--metric-blue); }
.player-insights .metric-card.win { border-left-color: var(--metric-green); }
.player-insights .metric-card.danger { border-left-color: var(--metric-red); }
.player-insights .metric-card.accent,
.player-insights .metric-card.light { border-left-color: var(--metric-gold); }

.player-insights .metric-card.primary strong { color: var(--metric-blue); }
.player-insights .metric-card.win strong { color: var(--metric-green); }
.player-insights .metric-card.danger strong { color: var(--metric-red); }
.player-insights .metric-card.accent strong,
.player-insights .metric-card.light strong { color: var(--metric-gold); }

.theme-light .mini-stat-row strong,
.theme-light .hero-row b,
.theme-light .stage-row b,
.theme-light .battle-score span,
.theme-light .battle-score span.won {
  color: var(--text);
}

.player-insights .video-link.bilibili {
  background: rgba(0, 174, 236, .12);
  color: #00aeec;
}

.player-insights .video-link.bilibili:hover {
  background: rgba(0, 174, 236, .25);
}

/* Rankings-style console parity with hero insights */
.player-insights.theme-dark {
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
  --metric-blue: #8fb7d2;
  --metric-green: #86c7a4;
  --metric-red: #d28a80;
  --metric-gold: #d3b36a;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.theme-light {
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
  --metric-blue: #2563EB;
  --metric-green: #16A34A;
  --metric-red: #EF4444;
  --metric-gold: #B88A2E;
  background:
    linear-gradient(180deg, rgba(250, 248, 240, .98), rgba(245, 242, 232, .99)),
    #f8f5ec;
}

.player-insights .topbar,
.player-insights .profile-card,
.player-insights .panel,
.player-insights .metric-card,
.player-insights .state-panel {
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.theme-light .topbar,
.theme-light .profile-card,
.theme-light .panel,
.theme-light .metric-card,
.theme-light .state-panel,
.player-insights.theme-dark .topbar,
.player-insights.theme-dark .profile-card,
.player-insights.theme-dark .panel,
.player-insights.theme-dark .metric-card,
.player-insights.theme-dark .state-panel {
  border-color: var(--line);
  border-radius: 0;
  background: var(--panel-bg);
  box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.player-insights .topbar,
.player-insights .profile-card,
.player-insights .panel,
.player-insights .metric-card,
.player-insights .state-panel {
  position: relative;
}

.player-insights .topbar::before,
.player-insights .profile-card::before,
.player-insights .panel::before,
.player-insights .metric-card::before,
.player-insights .state-panel::before {
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

.player-insights.theme-dark .topbar::before,
.player-insights.theme-dark .profile-card::before,
.player-insights.theme-dark .panel::before,
.player-insights.theme-dark .metric-card::before,
.player-insights.theme-dark .state-panel::before {
  border-color: rgba(255, 255, 255, .18);
}

.player-insights .topbar {
  min-height: 64px;
  padding: 10px 18px;
}

.player-insights .title-block span,
.player-insights .section-title :deep(span) {
  color: var(--soft);
  letter-spacing: 1.8px;
}

.player-insights .controls :deep(.el-select__wrapper),
.player-insights .controls :deep(.el-button) {
  min-height: 40px;
  border-radius: 0 !important;
  background: rgba(255, 255, 255, .60) !important;
  box-shadow: 0 0 0 1px var(--line) inset !important;
}

.player-insights.theme-dark .controls :deep(.el-select__wrapper),
.player-insights.theme-dark .controls :deep(.el-button) {
  background: rgba(255, 255, 255, .06) !important;
}

.player-insights .profile-card img,
.player-insights .avatar-fallback,
.player-insights .hero-row img,
.player-insights .battle-thumb,
.player-insights .mini-stat-row div,
.player-insights .league-tabs button,
.player-insights .league-scroll-btn,
.player-insights .mode-switch,
.player-insights .mode-switch button,
.player-insights .compare-track,
.player-insights .battle-row,
.player-insights .hero-row,
.player-insights .stage-row {
  border-radius: 0;
}

.theme-light .profile-card img,
.theme-light .avatar-fallback,
.theme-light .hero-row img,
.theme-light .battle-thumb,
.theme-light .mini-stat-row div,
.theme-light .league-tabs button,
.theme-light .league-scroll-btn,
.theme-light .battle-row,
.theme-light .hero-row,
.theme-light .stage-row,
.player-insights.theme-dark .profile-card img,
.player-insights.theme-dark .avatar-fallback,
.player-insights.theme-dark .hero-row img,
.player-insights.theme-dark .battle-thumb,
.player-insights.theme-dark .mini-stat-row div,
.player-insights.theme-dark .league-tabs button,
.player-insights.theme-dark .league-scroll-btn,
.player-insights.theme-dark .battle-row,
.player-insights.theme-dark .hero-row,
.player-insights.theme-dark .stage-row {
  border-radius: 0;
}

.player-insights .mini-stat-row div,
.player-insights .hero-row,
.player-insights .stage-row,
.player-insights .battle-row,
.player-insights .league-tabs button,
.player-insights .league-scroll-btn {
  border-color: var(--line);
  background: transparent;
  box-shadow: none;
}

.player-insights .metric-card {
  background: var(--panel-bg);
  box-shadow: none;
}

.player-insights .metric-card:hover,
.player-insights .hero-row:hover,
.player-insights .stage-row:hover,
.player-insights .battle-row:hover,
.player-insights .league-tabs button:hover {
  border-color: var(--line-strong);
  background: rgba(0, 0, 0, .04);
  box-shadow: none;
}

.player-insights.theme-dark .metric-card:hover,
.player-insights.theme-dark .hero-row:hover,
.player-insights.theme-dark .stage-row:hover,
.player-insights.theme-dark .battle-row:hover,
.player-insights.theme-dark .league-tabs button:hover {
  background: rgba(255, 255, 255, .05);
}

.player-insights .metric-card.primary { border-left-color: var(--metric-blue); }
.player-insights .metric-card.win { border-left-color: var(--metric-green); }
.player-insights .metric-card.danger { border-left-color: var(--metric-red); }
.player-insights .metric-card.accent,
.player-insights .metric-card.light { border-left-color: var(--metric-gold); }

.player-insights .metric-card.primary strong { color: var(--metric-blue); }
.player-insights .metric-card.win strong { color: var(--metric-green); }
.player-insights .metric-card.danger strong { color: var(--metric-red); }
.player-insights .metric-card.accent strong,
.player-insights .metric-card.light strong { color: var(--metric-gold); }

.theme-light .mini-stat-row strong,
.theme-light .hero-row b,
.theme-light .stage-row b,
.theme-light .battle-score span,
.theme-light .battle-score span.won {
  color: var(--text);
}

.player-insights .video-link.bilibili,
.player-insights.theme-dark .video-link.bilibili {
  background: rgba(0, 174, 236, .12);
  color: #00aeec;
}

.player-insights .video-link.bilibili:hover,
.player-insights.theme-dark .video-link.bilibili:hover {
  background: rgba(0, 174, 236, .25);
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
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .title-block {
    flex: 1;
    min-width: 0;
  }
  .theme-toggle {
    order: -1;
    flex-shrink: 0;
    align-self: center;
  }
  .controls {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .controls :deep(.el-select) {
    flex: 1 1 100px;
    min-width: 0;
  }
  .controls :deep(.el-select__wrapper) {
    display: flex !important;
    flex-wrap: nowrap !important;
  }
  .controls :deep(.el-select__selected-item),
  .controls :deep(.el-select__placeholder) {
    flex: 1 1 auto !important;
    min-width: 40px !important;
    max-width: none !important;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .controls :deep(.el-select__suffix),
  .controls :deep(.el-select__caret) {
    flex: 0 0 auto !important;
  }
  .title-block {
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

<!-- unscoped: Teleport to body -->
<style>
/* overlay — click-catch zone left of the dialog, always fully transparent
   (no veil in any theme). `!important` guards against any global theme
   background leaking onto the Teleported element. */
.rank-overlay {
  position: fixed;
  top: 0;
  right: 520px;
  bottom: 0;
  left: 0;
  z-index: 2000;
  background: transparent !important;
}
/* dialog box */
.rank-dialog {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 520px;
  max-width: 90vw;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255, 255, 255, .48);
  border-radius: 0;
  background: #1a1b1e;
  box-shadow: -4px 0 24px rgba(0, 0, 0, .18);
  overflow: hidden;
  color: #e8e8e8;
  animation: rank-slide-in .25s cubic-bezier(.2, .8, .2, 1) both;
}
@keyframes rank-slide-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}
.rank-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, .48);
}
.rank-dialog-head strong {
  font-size: 16px;
  font-weight: 800;
  color: #e8e8e8;
}
.rank-dialog-close {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  background: none;
  color: rgba(232, 232, 232, .5);
  font-size: 20px;
  cursor: pointer;
}
.rank-dialog-close:hover {
  color: #e8e8e8;
}
.rank-dialog-note {
  padding: 8px 20px 0;
  font-size: 12px;
  color: rgba(232, 232, 232, .5);
}
.rank-table-wrap {
  flex: 1;
  min-height: 0;
  max-height: 420px;
  margin: 10px 20px 16px;
  border: 1px solid rgba(255, 255, 255, .48);
  background: rgba(255, 255, 255, .04);
  overflow-y: auto;
}
.rank-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.rank-table thead {
  position: sticky;
  top: 0;
  z-index: 1;
}
.rank-table th {
  padding: 7px 10px;
  background: rgba(255, 255, 255, .05);
  border-bottom: 1px solid rgba(255, 255, 255, .48);
  color: rgba(232, 232, 232, .65);
  font-size: 11px;
  font-weight: 700;
  text-align: left;
}
.rank-table td {
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, .48);
  vertical-align: middle;
}
.rank-table tr:last-child td { border-bottom: none; }
.rank-table tr:hover { background: rgba(255, 255, 255, .04); }
.rank-table tr.active { background: rgba(103, 82, 215, .10); }
.rank-col { width: 48px; text-align: center !important; }
.data-col { width: 120px; text-align: right !important; }
.rank-badge {
  display: inline-block;
  min-width: 22px;
  font-size: 13px;
  font-weight: 900;
  color: rgba(232, 232, 232, .5);
  text-align: center;
}
.rank-badge.rank-top3 { color: #e8e8e8; }
.rank-table tr.active .rank-badge { color: #6752d7; }
.name-cell { display: flex; align-items: center; gap: 8px; }
.name-cell .cell-icon {
  width: 28px; height: 28px; border-radius: 0; object-fit: cover; flex-shrink: 0;
}
.name-text strong { display: block; font-size: 13px; font-weight: 800; color: #e8e8e8; }
.name-text span { display: block; font-size: 11px; color: rgba(232, 232, 232, .5); }
.rate-cell { text-align: right; }
.rate-num { font-size: 13px; font-weight: 900; color: #e8e8e8; }
.rate-track {
  height: 3px; margin-top: 4px; background: rgba(255, 255, 255, .1); border-radius: 0;
}
.rate-bar {
  display: block; height: 100%; background: #e8e8e8; border-radius: 0; transition: width .3s ease;
}

/* responsive overlay — on narrow screens dialog takes 90vw */
@media (max-width: 580px) {
  .rank-overlay {
    right: 90vw;
  }
}

/* light theme — `.rank-overlay` carries the `theme-light` class itself
   (Teleported to <body>), so we scope via compound selectors.
   The overlay itself stays transparent (no dim/veil), same as dark theme. */
.rank-overlay.theme-light .rank-dialog {
  border-color: #E3D6C4;
  background: #FFFBF3;
  box-shadow: -4px 0 24px rgba(88, 72, 50, .15);
}
.rank-overlay.theme-light .rank-dialog-head {
  border-color: #E3D6C4;
}
.rank-overlay.theme-light .rank-dialog-head strong { color: #1F2933; }
.rank-overlay.theme-light .rank-dialog-close { color: #9A8B78; }
.rank-overlay.theme-light .rank-dialog-close:hover { color: #1F2933; }
.rank-overlay.theme-light .rank-dialog-note { color: #5F6670; }
.rank-overlay.theme-light .rank-table-wrap { border-color: #E3D6C4; background: rgba(0, 0, 0, .02); }
.rank-overlay.theme-light .rank-table th {
  background: rgba(0, 0, 0, .04); border-color: #E3D6C4; color: #5F6670;
}
.rank-overlay.theme-light .rank-table td { border-color: #E3D6C4; }
.rank-overlay.theme-light .rank-table tr:hover { background: rgba(0, 0, 0, .04); }
.rank-overlay.theme-light .rank-table tr.active { background: rgba(0, 0, 0, .06); }
.rank-overlay.theme-light .rank-badge { color: #9A8B78; }
.rank-overlay.theme-light .rank-badge.rank-top3 { color: #1F2933; }
.rank-overlay.theme-light .rank-table tr.active .rank-badge { color: #1F2933; }
.rank-overlay.theme-light .name-text strong { color: #1F2933; }
.rank-overlay.theme-light .name-text span { color: #9A8B78; }
.rank-overlay.theme-light .rate-num { color: #1F2933; }
.rank-overlay.theme-light .rate-track { background: rgba(0, 0, 0, .08); }
.rank-overlay.theme-light .rate-bar { background: #1F2933; }
</style>
