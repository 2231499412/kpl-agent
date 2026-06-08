<template>
  <main class="matchup-page" :class="`theme-${theme}`">
    <section class="select-strip">
      <div class="select-left">
        <label>
          <span>赛事</span>
          <el-select v-model="selectedLeagueId" filterable placeholder="选择赛事" @change="onLeagueChange">
            <el-option v-for="league in leagues" :key="league.leagueId" :label="league.leagueName" :value="league.leagueId" />
          </el-select>
        </label>
      </div>

      <div class="select-center">
        <label class="match-select">
          <span>比赛</span>
          <el-select v-model="selectedMatchId" filterable placeholder="选择比赛" :disabled="!matches.length" @change="onMatchChange">
            <el-option v-for="match in matches" :key="match.matchId" :label="matchLabel(match)" :value="match.matchId" />
          </el-select>
        </label>
        <label class="battle-select">
          <span>小局</span>
          <el-select v-model="selectedBattleId" placeholder="选择小局" :disabled="!battles.length" @change="onBattleChange">
            <el-option v-for="item in battles" :key="item.battle?.battleId" :label="battleLabel(item)" :value="item.battle?.battleId" />
          </el-select>
        </label>
        <nav ref="roleNavRef" class="role-tabs" aria-label="分路选择">
          <span class="role-pill" :style="pillStyle"></span>
          <button v-for="role in roleOptions" :key="role.value" :ref="el => roleBtnRefs[role.value] = el" :class="{ active: selectedRole === role.value }" @click="selectRole(role.value, $event)">
            {{ role.label }}
          </button>
        </nav>
      </div>

      <aside class="select-right">
        <div class="select-text">
          <span>当前基准</span>
          <strong>{{ radarData?.basis?.sampleCount || '-' }} 样本</strong>
        </div>
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }">
            <span class="toggle-thumb" />
          </span>
          <span class="toggle-label">{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</span>
        </button>
      </aside>
    </section>

    <section v-if="loading" class="state-card">
      <div class="spinner" />
      <span>正在生成对位海报...</span>
    </section>

    <section v-else-if="errorText" class="state-card error">
      <strong>{{ errorText }}</strong>
      <span>换一个小局或分路试试，可能该局缺少完整选手分路数据。</span>
    </section>

    <section v-else-if="radarData" :key="radarKey" class="poster-shell">
      <aside class="hero-panel blue">
        <SideVisual :side="radarData.blue" :result="sideResult(radarData.blue)" />
      </aside>

      <article class="radar-poster">
        <header class="scoreboard">
          <img v-if="radarData.blue.teamIcon" class="team-logo blue-logo" :src="radarData.blue.teamIcon" :alt="radarData.blue.teamName">
          <div class="team-name left">{{ currentMatch?.camp1TeamName || radarData.blue.teamName }}</div>
          <div class="score-center">
            <strong>{{ currentGameTitle }}</strong>
            <span>{{ currentScoreLabel }}</span>
          </div>
          <div class="win-badge" :class="{ blue: winnerCamp === 1, red: winnerCamp === 2 }">胜</div>
          <div class="team-name right">{{ currentMatch?.camp2TeamName || radarData.red.teamName }}</div>
          <img v-if="radarData.red.teamIcon" class="team-logo red-logo" :src="radarData.red.teamIcon" :alt="radarData.red.teamName">
        </header>

        <div class="poster-subtitle">
          <em v-if="radarData.battle?.gameDuration" class="game-duration">{{ gameDurationText(radarData.battle.gameDuration) }}</em>
          <b>{{ selectedRole }}对位</b>
        </div>

        <section ref="arenaRef" class="radar-arena" @mouseleave="hoverMetric = null">
          <svg class="radar-svg" viewBox="0 0 460 460" role="img" aria-label="红蓝双方对位雷达图">
            <polygon :points="gridPolygon(100)" class="outer-grid" />
            <polygon v-for="level in [80, 60, 40, 20]" :key="level" :points="gridPolygon(level)" class="inner-grid" />
            <g v-for="(_, index) in axes" :key="index">
              <line :x1="center" :y1="center" :x2="axisPoint(index, 100).x" :y2="axisPoint(index, 100).y" class="axis-line" />
            </g>
            <polygon :points="radarPolygon(loserScores)" class="loser-poly" />
            <polygon :points="radarPolygon(winnerScores)" class="winner-poly" />
            <polygon :points="radarPolygon(loserScores)" class="loser-line" />
            <polygon :points="radarPolygon(winnerScores)" class="winner-line" />
            <g class="mobile-radar-labels">
              <g v-for="(metric, index) in metricRows" :key="`mobile-label-${metric.key || index}`">
                <template v-if="isHorizontalMetric(index)">
                  <text
                    :x="mobileLabelPoint(index, 'blue').x"
                    :y="mobileLabelPoint(index, 'blue').y"
                    :text-anchor="mobileLabelPoint(index, 'blue').anchor"
                    :class="mobileMetricClass(metric.blue, 1)"
                  >{{ formatRaw(metric.blue) }}</text>
                  <text
                    :x="mobileLabelPoint(index, 'label').x"
                    :y="mobileLabelPoint(index, 'label').y"
                    text-anchor="middle"
                    class="mobile-label"
                  >{{ metric.name }}</text>
                  <text
                    :x="mobileLabelPoint(index, 'red').x"
                    :y="mobileLabelPoint(index, 'red').y"
                    :text-anchor="mobileLabelPoint(index, 'red').anchor"
                    :class="mobileMetricClass(metric.red, 2)"
                  >{{ formatRaw(metric.red) }}</text>
                </template>
                <template v-else>
                  <text
                    :x="mobileLabelPoint(index, 'winner').x"
                    :y="mobileLabelPoint(index, 'winner').y"
                    text-anchor="middle"
                    :class="mobileMetricClass(metric.winner, winnerCamp)"
                  >{{ formatRaw(metric.winner) }}</text>
                  <text
                    :x="mobileLabelPoint(index, 'label').x"
                    :y="mobileLabelPoint(index, 'label').y"
                    text-anchor="middle"
                    class="mobile-label"
                  >{{ metric.name }}</text>
                  <text
                    :x="mobileLabelPoint(index, 'loser').x"
                    :y="mobileLabelPoint(index, 'loser').y"
                    text-anchor="middle"
                    :class="mobileMetricClass(metric.loser, winnerCamp === 2 ? 1 : 2)"
                  >{{ formatRaw(metric.loser) }}</text>
                </template>
              </g>
            </g>
          </svg>

          <div v-if="isMatchSummary" class="summary-hero-orbit">
            <div class="orbit-side blue">
              <span class="orbit-label">{{ shortPlayer(radarData.blue.playerName) }}</span>
              <div class="orbit-icons">
                <span v-for="game in radarData.blue.heroGames || []" :key="`blue-${game.battleId}`" class="orbit-hero" :class="{ win: game.result === '胜' }" :title="heroGameTitle(game)">
                  <img :src="heroIcon(game)" :alt="game.heroName">
                  <b>G{{ game.battleSeq }}</b>
                </span>
              </div>
            </div>
            <div class="orbit-side red">
              <span class="orbit-label">{{ shortPlayer(radarData.red.playerName) }}</span>
              <div class="orbit-icons">
                <span v-for="game in radarData.red.heroGames || []" :key="`red-${game.battleId}`" class="orbit-hero" :class="{ win: game.result === '胜' }" :title="heroGameTitle(game)">
                  <img :src="heroIcon(game)" :alt="game.heroName">
                  <b>G{{ game.battleSeq }}</b>
                </span>
              </div>
            </div>
          </div>

          <div v-if="radarData.highlights?.length" class="radar-highlights">
            <div class="highlight-column left">
              <div v-for="item in sideHighlights(1)" :key="highlightKey(item)" class="highlight-card" :class="`camp-${item.camp}`">
                <span>{{ item.label }}</span>
                <strong>{{ highlightValue(item) }}</strong>
                <em>{{ shortPlayer(item.playerName) }} · {{ item.heroName || '-' }}</em>
              </div>
            </div>
            <div class="highlight-column right">
              <div v-for="item in sideHighlights(2)" :key="highlightKey(item)" class="highlight-card" :class="`camp-${item.camp}`">
                <span>{{ item.label }}</span>
                <strong>{{ highlightValue(item) }}</strong>
                <em>{{ shortPlayer(item.playerName) }} · {{ item.heroName || '-' }}</em>
              </div>
            </div>
          </div>
        </section>

        <footer class="poster-footer">
          <div class="footer-player left">
            <span v-if="!isMatchSummary && radarData.blue.isMvp" class="mvp-tag">MVP</span>
            <span v-else-if="!isMatchSummary && radarData.blue.isLoseMvp" class="mvp-tag lose">败方MVP</span>
            <strong>{{ shortPlayer(radarData.blue.playerName) }}</strong>
            <span>{{ sideFooterText(radarData.blue) }}</span>
          </div>
          <div class="legend">
            <span class="blue-mark" />胜方表现分
            <span class="red-mark" />败方表现分
          </div>
          <div class="footer-player right">
            <span v-if="!isMatchSummary && radarData.red.isMvp" class="mvp-tag">MVP</span>
            <span v-else-if="!isMatchSummary && radarData.red.isLoseMvp" class="mvp-tag lose">败方MVP</span>
            <strong>{{ shortPlayer(radarData.red.playerName) }}</strong>
            <span>{{ sideFooterText(radarData.red) }}</span>
          </div>
        </footer>
      </article>

      <aside class="hero-panel red">
        <SideVisual :side="radarData.red" :result="sideResult(radarData.red)" />
      </aside>
    </section>

    <Transition name="fade-hint">
      <div v-if="wheelHintVisible && radarData" class="wheel-hint">鼠标滚轮切换分路</div>
    </Transition>

  </main>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const roleOptions = [
  { label: '对抗', value: '对抗路' },
  { label: '打野', value: '打野' },
  { label: '中路', value: '中路' },
  { label: '发育', value: '发育路' },
  { label: '游走', value: '游走' },
]
const leagues = ref([])
const matches = ref([])
const battles = ref([])
const selectedLeagueId = ref('')
const selectedMatchId = ref('')
const selectedBattleId = ref('')
const selectedRole = ref('对抗路')
const radarData = ref(null)
const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))
const loading = ref(false)
const errorText = ref('')
const hoverMetric = ref(null)
const arenaRef = ref(null)
const roleNavRef = ref(null)
const roleBtnRefs = {}
const pillStyle = ref({})
const wheelHintVisible = ref(true)
let wheelHintShown = false

let wheelTimer = null
function onWheel(e) {
  if (!radarData.value || loading.value) return
  e.preventDefault()
  if (wheelTimer) return
  const idx = roleOptions.findIndex(r => r.value === selectedRole.value)
  const next = e.deltaY > 0 ? Math.min(idx + 1, roleOptions.length - 1) : Math.max(idx - 1, 0)
  if (next === idx) return
  selectedRole.value = roleOptions[next].value
  loadRadar()
  nextTick(() => updatePill())
  wheelTimer = setTimeout(() => { wheelTimer = null }, 600)
}

function selectRole(value) {
  selectedRole.value = value
  loadRadar()
  nextTick(() => updatePill())
}

function updatePill() {
  const nav = roleNavRef.value
  const btn = roleBtnRefs[selectedRole.value]
  if (!nav || !btn) return
  const navRect = nav.getBoundingClientRect()
  const btnRect = btn.getBoundingClientRect()
  pillStyle.value = {
    left: (btnRect.left - navRect.left) + 'px',
    width: btnRect.width + 'px',
  }
}

const center = 230
const radius = 162

const mobileLabelLayout = reactive([
  { label: [230, 45], blue: [184, 45, 'end'], red: [276, 45, 'start'] },
  { label: [380, 108], winner: [380, 90], loser: [380, 126] },
  { label: [430, 230], winner: [430, 212], loser: [430, 248] },
  { label: [380, 352], winner: [380, 334], loser: [380, 370] },
  { label: [230, 410], blue: [184, 408, 'end'], red: [276, 408, 'start'] },
  { label: [70, 352], winner: [70, 334], loser: [70, 370] },
  { label: [40, 230], winner: [40, 212], loser: [40, 248] },
  { label: [70, 108], winner: [70, 90], loser: [70, 126] },
])

function mobileLabelPoint(index, slot) {
  const item = mobileLabelLayout[index] || mobileLabelLayout[0]
  const point = item[slot] || item.label
  return {
    x: point[0],
    y: point[1],
    anchor: point[2] || 'middle',
  }
}

function mobileMetricClass(metric, camp) {
  return [
    'mobile-value',
    camp === winnerCamp.value ? 'winner' : 'loser',
    { hot: isMetricHighlighted(metric, camp) },
  ]
}

const currentLeagueName = computed(() => leagues.value.find(l => l.leagueId === selectedLeagueId.value)?.leagueName || 'KPL')
const currentMatch = computed(() => matches.value.find(m => m.matchId === selectedMatchId.value))
const currentBattle = computed(() => battles.value.find(b => b.battle?.battleId === selectedBattleId.value)?.battle)
const isMatchSummary = computed(() => selectedBattleId.value === 'all' || radarData.value?.battle?.summary)
const currentBattleTitle = computed(() => {
  if (isMatchSummary.value) return '全场总结'
  return currentBattle.value ? `第${currentBattle.value.battleSeq || '-'}局` : '请选择小局'
})
const currentGameTitle = computed(() => currentBattleTitle.value === '请选择小局' ? '第-局' : currentBattleTitle.value)
const winnerCamp = computed(() => Number(radarData.value?.battle?.winCamp ?? currentBattle.value?.winCamp) || 0)
const currentScoreText = computed(() => {
  if (isMatchSummary.value) {
    return {
      blue: Number(currentMatch.value?.camp1Score) || 0,
      red: Number(currentMatch.value?.camp2Score) || 0,
    }
  }
  const seq = Number(currentBattle.value?.battleSeq) || 0
  if (!seq) return { blue: 0, red: 0 }
  return battles.value
    .map(item => item.battle)
    .filter(battle => battle && Number(battle.battleSeq) <= seq)
    .reduce((score, battle) => {
      if (Number(battle.winCamp) === 1) score.blue += 1
      if (Number(battle.winCamp) === 2) score.red += 1
      return score
    }, { blue: 0, red: 0 })
})
const currentScoreLabel = computed(() => `${currentScoreText.value.blue} : ${currentScoreText.value.red}`)
const radarKey = computed(() => radarData.value ? `${selectedBattleId.value}-${selectedRole.value}-${radarData.value.type}-${radarData.value.blue?.playerName}-${radarData.value.red?.playerName}` : 'empty')
const axes = computed(() => radarData.value?.indicators || [])
const blueScores = computed(() => radarData.value?.blue?.metrics?.map(m => Number(m.score) || 0) || [])
const redScores = computed(() => radarData.value?.red?.metrics?.map(m => Number(m.score) || 0) || [])
const winnerSide = computed(() => winnerCamp.value === 2 ? radarData.value?.red : radarData.value?.blue)
const loserSide = computed(() => winnerCamp.value === 2 ? radarData.value?.blue : radarData.value?.red)
const winnerScores = computed(() => winnerSide.value?.metrics?.map(m => Number(m.score) || 0) || [])
const loserScores = computed(() => loserSide.value?.metrics?.map(m => Number(m.score) || 0) || [])
const metricRows = computed(() => {
  if (!radarData.value) return []
  return radarData.value.blue.metrics.map((blue, index) => ({
    ...blue,
    blue,
    red: radarData.value.red.metrics[index],
    winner: winnerCamp.value === 2 ? radarData.value.red.metrics[index] : blue,
    loser: winnerCamp.value === 2 ? blue : radarData.value.red.metrics[index],
  }))
})

const SideVisual = defineComponent({
  name: 'SideVisual',
  props: {
    side: { type: Object, required: true },
    result: { type: String, required: true },
  },
  setup(props) {
    return () => {
      const heroGames = Array.isArray(props.side.heroGames) ? props.side.heroGames : []
      const mainHero = latestHeroGame(props.side) || props.side
      const summaryMode = heroGames.length > 1
      return [
      h('img', { class: 'matchup-hero-bg', src: heroPoster(mainHero), alt: mainHero.heroName || props.side.heroName }),
      h('div', { class: 'hero-vignette' }),
      props.side.teamIcon ? h('img', { class: 'corner-logo', src: props.side.teamIcon, alt: props.side.teamName }) : null,
      h('div', { class: ['result-chip', props.result === '胜' ? 'win' : 'lose'] }, props.result === '胜' ? '胜利' : '失败'),
      mainHero.summonerAbilityIcon
        ? h('img', {
            class: 'summoner-corner',
            src: mainHero.summonerAbilityIcon,
            alt: mainHero.summonerAbilityName || 'summoner',
            title: mainHero.summonerAbilityName,
            onError: event => { event.target.style.display = 'none' },
          })
        : null,
      props.side.symbolIds ? h('div', { class: 'symbol-strip' },
        [...new Set(String(props.side.symbolIds).split(/[+,]/).filter(Boolean))].map(id =>
          h('img', {
            key: id,
            src: `https://game.gtimg.cn/images/yxzj/img201606/mingwen/${id.trim()}.png`,
            alt: id.trim(),
            class: 'symbol-icon',
            onError: event => { event.target.style.display = 'none' },
          })
        )
      ) : null,
      h('div', { class: 'portrait-block' }, [
        h('div', { class: 'player-portrait-wrap' }, [
          props.side.playerIcon
            ? h('img', { class: 'player-portrait', src: props.side.playerIcon, alt: shortPlayer(props.side.playerName) })
            : h('img', { class: 'player-portrait hero-fallback', src: heroIcon(props.side), alt: props.side.heroName }),
        ]),
        h('div', { class: 'kda-side' }, kdaText(props.side)),
      ]),
      h('div', { class: 'equip-block' }, [
        h('div', { class: 'equip-stack' }, (props.side.equips || []).slice(0, 6).map(eq =>
          h('img', { key: eq.equipId || eq.equipName, src: eq.equipIcon, alt: eq.equipName, title: eq.equipName })
        )),
        h('div', { class: 'gold-line' }, [
          h('span', { class: 'coin' }, 'S'),
          h('strong', formatInt(props.side.gold)),
        ]),
      ]),
      h('div', { class: 'nameplate' }, [
        h('strong', shortPlayer(props.side.playerName)),
        h('span', summaryMode ? `全场 ${heroGames.length} 局 · ${kdaText(props.side)}` : `${props.side.heroName || '-'} · ${props.side.positionDesc || ''}`),
      ]),
    ]
    }
  }
})

async function request(url) {
  const response = await fetch(url)
  const body = await response.json()
  if (!body?.success) throw new Error(body?.message || '请求失败')
  return body.data
}

async function init() {
  leagues.value = await request('/api/leagues?limit=50').catch(() => [])
  if (leagues.value.length) {
    selectedLeagueId.value = leagues.value[0].leagueId
    await loadMatches()
  }
}

async function onLeagueChange() {
  selectedMatchId.value = ''
  selectedBattleId.value = ''
  radarData.value = null
  wheelHintShown = false
  await loadMatches()
}

async function loadMatches() {
  if (!selectedLeagueId.value) return
  matches.value = []
  battles.value = []
  const data = await request(`/api/query/match/schedule?leagueId=${selectedLeagueId.value}`).catch(() => ({ data: [] }))
  matches.value = data?.data || []
  if (matches.value.length) {
    selectedMatchId.value = matches.value[0].matchId
    await loadBattles()
  }
}

async function onMatchChange() {
  selectedBattleId.value = ''
  radarData.value = null
  wheelHintShown = false
  await loadBattles()
}

function onBattleChange() {
  wheelHintShown = false
  loadRadar()
}

async function loadBattles() {
  if (!selectedMatchId.value) return
  battles.value = []
  const data = await request(`/api/query/match/battle?matchId=${selectedMatchId.value}`).catch(() => ({ battles: [] }))
  const battleItems = data?.battles || []
  const match = currentMatch.value
  battles.value = [
    ...battleItems,
    {
      battle: {
        battleId: 'all',
        matchId: selectedMatchId.value,
        title: '全场总结',
        summary: true,
        winCamp: Number(match?.winCamp) || ((Number(match?.camp1Score) || 0) > (Number(match?.camp2Score) || 0) ? 1 : 2),
        gameDuration: battleItems.reduce((sum, item) => sum + (Number(item.battle?.gameDuration) || 0), 0),
        battleCount: battleItems.length,
      },
    },
  ]
  if (battles.value.length) {
    selectedBattleId.value = battles.value[0].battle?.battleId
    await loadRadar()
  }
}

async function loadRadar() {
  if (!selectedLeagueId.value || !selectedMatchId.value || !selectedBattleId.value) return
  loading.value = true
  errorText.value = ''
  try {
    const params = new URLSearchParams({
      leagueId: selectedLeagueId.value,
      matchId: selectedMatchId.value,
      battleId: selectedBattleId.value,
      role: selectedRole.value,
    })
    const data = await request(`/api/query/radar/lane?${params.toString()}`)
    if (data?.error) {
      errorText.value = data.error
      radarData.value = null
    } else {
      radarData.value = data
    }
  } catch (error) {
    errorText.value = error?.message || '雷达图数据加载失败'
    radarData.value = null
  } finally {
    loading.value = false
  }
}

function axisPoint(index, score) {
  const count = Math.max(axes.value.length, 1)
  const angle = -Math.PI / 2 + (Math.PI * 2 * index) / count
  const r = radius * (score / 100)
  return {
    x: center + Math.cos(angle) * r,
    y: center + Math.sin(angle) * r,
  }
}

function gridPolygon(level) {
  return axes.value.map((_, index) => {
    const point = axisPoint(index, level)
    return `${point.x},${point.y}`
  }).join(' ')
}

function isHorizontalMetric(index) {
  return index === 0 || index === 4
}

function radarPolygon(scores) {
  return scores.map((score, index) => {
    const point = axisPoint(index, score)
    return `${point.x},${point.y}`
  }).join(' ')
}

function sideResult(side) {
  const winCamp = radarData.value?.battle?.winCamp ?? currentBattle.value?.winCamp
  if (!winCamp || !side?.camp) return ''
  return winCamp === side.camp ? '胜' : '败'
}

function matchLabel(match) {
  const score = `${match.camp1Score ?? '-'}:${match.camp2Score ?? '-'}`
  const stage = match.matchStageDesc ? ` · ${match.matchStageDesc}` : ''
  return `${match.camp1TeamName || '蓝方'} ${score} ${match.camp2TeamName || '红方'}${stage}`
}

function battleLabel(item) {
  const battle = item.battle
  if (!battle) return '小局'
  if (battle.summary) return `全场总结 · ${currentMatch.value?.camp1Score ?? '-'}:${currentMatch.value?.camp2Score ?? '-'}`
  const winner = battle.winCamp === 1 ? currentMatch.value?.camp1TeamName : battle.winCamp === 2 ? currentMatch.value?.camp2TeamName : ''
  return `第 ${battle.battleSeq || '-'} 局${winner ? ` · ${winner}胜` : ''}`
}

function heroIcon(side) {
  return side?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${side.heroId}.jpg` : ''
}

function heroPoster(side) {
  return side?.heroId ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${side.heroId}/${side.heroId}-mobileskin-1.jpg` : heroIcon(side)
}

function latestHeroGame(side) {
  const games = Array.isArray(side?.heroGames) ? side.heroGames : []
  return games.length ? games[games.length - 1] : null
}

function heroGameTitle(game) {
  return `第${game?.battleSeq || '-'}局 · ${game?.heroName || '-'} · ${kdaText(game)} · ${game?.result || '-'}`
}

function shortPlayer(name) {
  return String(name || '').split('.').pop() || '-'
}

function kdaText(side) {
  return `${side?.kills ?? 0}/${side?.deaths ?? 0}/${side?.assists ?? 0}`
}

function sideFooterText(side) {
  const games = Array.isArray(side?.heroGames) ? side.heroGames : []
  return games.length > 1 ? `全场 ${games.length} 局 · ${kdaText(side)}` : `${side?.heroName || '-'} · ${kdaText(side)}`
}

function highlightValue(item) {
  const value = Number(item?.displayValue ?? item?.value ?? 0)
  const unit = item?.unit || ''
  const text = unit === '%'
    ? value.toFixed(1)
    : unit === '次' || value >= 100
      ? Math.round(value).toLocaleString()
      : value.toFixed(1)
  return `${text}${unit}`
}

function sideHighlights(camp) {
  return (radarData.value?.highlights || []).filter(item => Number(item.camp) === Number(camp))
}

function highlightKey(item) {
  return `${item?.key || 'highlight'}-${item?.camp || '-'}-${item?.playerName || '-'}-${item?.heroName || '-'}`
}

function isMetricHighlighted(metric, camp) {
  if (!metric || !radarData.value?.highlights?.length) return false
  const keys = highlightKeysForMetric(metric.key)
  if (!keys.length) return false
  return radarData.value.highlights.some(item => Number(item.camp) === Number(camp) && keys.includes(item.key))
}

function highlightKeysForMetric(metricKey) {
  const map = {
    damagePerMinute: ['damageTotal'],
    goldPerMinute: ['goldTotal'],
    killParticipation: ['kills'],
    participationRate: ['participationRate'],
    kda: ['kda'],
    beHurtShare: ['beHurtTotal'],
    beHurtPerDeath: ['beHurtTotal'],
  }
  return map[metricKey] || []
}

function formatInt(value) {
  return Math.round(Number(value) || 0).toLocaleString()
}

function gameDurationText(duration) {
  if (!duration) return ''
  const totalSec = duration > 10000 ? Math.round(duration / 1000) : Math.round(duration)
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function formatRaw(metric) {
  return formatAnimatedRaw(metric, Number(metric?.displayRaw ?? metric?.raw ?? 0))
}

function formatAnimatedRaw(metric, animatedValue) {
  const unit = metric.displayUnit ?? metric.unit ?? ''
  const value = Number(animatedValue) || 0
  if (unit === '%') return `${(value * 100).toFixed(1)}%`
  if (unit.includes('死亡')) return `${value.toFixed(0)}`
  if (unit === '次') return `${value.toFixed(0)}`
  if (value >= 1000) return Math.round(value).toLocaleString()
  return value.toFixed(value >= 10 ? 1 : 2)
}


function tooltipText(metric) {
  return `${metric.name}\n蓝方：${formatRaw(metric.blue)}，${metric.blue.score}分\n红方：${formatRaw(metric.red)}，${metric.red.score}分\n${metric.blue.sampleCount}样本`
}

watch(radarData, (v) => {
  if (v && !wheelHintShown) {
    wheelHintShown = true
    wheelHintVisible.value = true
    setTimeout(() => { wheelHintVisible.value = false }, 3000)
  }
})

onMounted(async () => {
  await init()
  nextTick(updatePill)
  window.addEventListener('resize', updatePill)
})
onUnmounted(() => {
  window.removeEventListener('resize', updatePill)
})
</script>

<style scoped>
:global(html:has(.matchup-page)),
:global(body:has(.matchup-page)) {
  overflow: hidden;
}

.matchup-page {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.18);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --purple: #1a1a1a;
  --accent-deep: #1a1a1a;
  --winner: #1a1a1a;
  --loser: rgba(26, 26, 26, 0.45);
  --ink: #1a1a1a;
  box-sizing: border-box;
  width: calc(100vw - 67.5px);
  height: 100dvh;
  margin-left: 67.5px;
  padding: 8px 10px 10px;
  overflow: hidden;
  color: var(--ink);
  background:
    linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)),
    #f8f5ec;
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.matchup-page,
.matchup-page * { box-sizing: border-box; }

/* ── dark theme ── */
.matchup-page.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.12);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --purple: #e8e8e8;
  --accent-deep: #e8e8e8;
  --winner: #e8e8e8;
  --loser: rgba(232, 232, 232, 0.45);
  --ink: #e8e8e8;
  color: var(--ink);
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.theme-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border: 1px solid var(--mono-line);
  background: var(--mono-panel);
  cursor: pointer;
  height: 100%;
}

.toggle-track {
  width: 28px;
  height: 14px;
  border-radius: 999px;
  background: rgba(0, 0, 0, .15);
  position: relative;
  transition: background .2s;
}
.toggle-track.on { background: rgba(255, 255, 255, .25); }

.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  transition: transform .2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(14px); }

.select-strip {
  position: relative;
  z-index: 5;
  width: calc(100% - 36px);
  max-width: 1640px;
  height: 58px;
  display: grid;
  grid-template-columns: minmax(168px, 252px) minmax(560px, 1fr) minmax(168px, 252px);
  gap: 8px;
  margin: 6px auto 8px;
}

.select-right,
.state-card {
  border: 1px solid var(--mono-line);
  background: var(--mono-panel);
}

.select-left,
.select-strip label {
  border: 0;
  background: transparent;
}

.select-left {
  display: grid;
  gap: 6px;
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
  height: 100%;
  overflow: hidden;
}

.select-center {
  display: flex;
  align-items: stretch;
  gap: 6px;
  height: 100%;
  min-width: 0;
}

.select-center .match-select {
  flex: 4 1 0;
  min-width: 0;
}

.select-center .battle-select {
  flex: 3 1 0;
  min-width: 0;
}

.select-center .role-tabs {
  flex: 4 1 0;
  min-width: 0;
}

.select-strip label :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

.select-right {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 72px 7px 10px;
  overflow: hidden;
  height: 100%;
}

.select-right .select-text {
  display: grid;
  align-content: center;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.select-right .theme-toggle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  border: 0;
  padding: 0;
  flex-shrink: 0;
  margin-top: 18px;
  margin-left: -6px;
}

.toggle-label {
  font-size: 8px;
  font-weight: 950;
  letter-spacing: 2px;
  color: var(--mono-dim);
}

.select-right span {
  color: var(--mono-dim);
  font-size: 9px;
  font-weight: 950;
  letter-spacing: 2px;
}

.select-right strong {
  color: var(--mono-ink);
  font-size: 16px;
  font-weight: 950;
  line-height: 1;
}

.select-right em {
  overflow: hidden;
  color: var(--mono-soft);
  font-size: 10px;
  font-style: normal;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-strip label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 100%;
  padding: 4px 8px;
  overflow: hidden;
}

.select-strip label span {
  color: var(--mono-dim);
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 1px;
  white-space: nowrap;
  flex-shrink: 0;
}

:deep(.el-select__wrapper) {
  min-height: 27px;
  min-width: 0 !important;
  background: transparent !important;
  border: 1px solid var(--mono-line) !important;
  box-shadow: none !important;
  color: var(--mono-ink) !important;
}
:deep(.el-select__wrapper.is-focused) {
  border-color: var(--mono-ink) !important;
}

:deep(.el-select__selected-item),
:deep(.el-select__placeholder),
:deep(.el-select__input) {
  color: rgba(17, 22, 31, .88) !important;
  font-weight: 850;
}

:deep(.el-select__placeholder.is-transparent) {
  color: rgba(17, 22, 31, .48) !important;
}

.role-tabs {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 5px;
  padding: 4px;
  height: 100%;
  align-items: stretch;
}
.role-pill {
  position: absolute;
  top: 4px;
  height: calc(100% - 8px);
  background: #1a1a1a;
  border-radius: 10px;
  transition: left .5s cubic-bezier(.34,1.56,.64,1), width .5s cubic-bezier(.34,1.56,.64,1);
  z-index: 0;
  pointer-events: none;
}
.role-tabs button {
  position: relative;
  z-index: 1;
  border: 1px solid var(--mono-line);
  border-radius: 10px;
  color: var(--mono-soft);
  background: transparent;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  transition: color .2s ease;
}
.role-tabs button:hover {
  color: var(--mono-ink);
}
.role-tabs button.active {
  color: #f8f5ec;
}

.state-card {
  width: calc(100% - 36px);
  max-width: 1640px;
  height: calc(100dvh - 98px);
  display: grid;
  place-items: center;
  gap: 12px;
  margin: 0 auto;
  color: var(--mono-soft);
}
.state-card.error strong { color: var(--ink); font-size: 20px; }
.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(0, 0, 0, .1);
  border-top-color: var(--purple);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}

.poster-shell {
  width: calc(100% - 36px);
  max-width: 1640px;
  height: calc(100dvh - 98px);
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(168px, 252px) minmax(560px, 1fr) minmax(168px, 252px);
  gap: 8px;
  margin: 0 auto;
  overflow: hidden;
}

.wheel-hint {
  position: fixed;
  bottom: 64px;
  left: 52%;
  transform: translateX(-50%);
  z-index: 20;
  padding: 6px 18px;
  background: rgba(26, 26, 26, 0.85);
  color: #e8e8e8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  border-radius: 20px;
  pointer-events: none;
  backdrop-filter: blur(6px);
}
.fade-hint-enter-active { transition: opacity 0.3s ease; }
.fade-hint-leave-active { transition: opacity 0.8s ease; }
.fade-hint-enter-from,
.fade-hint-leave-to { opacity: 0; }

.hero-panel,
.radar-poster {
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--mono-line);
  box-shadow: 0 8px 24px rgba(0, 0, 0, .06);
}

.hero-panel {
  position: relative;
  background: #101623;
}

.matchup-hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 12%;
  filter: saturate(1.04) contrast(1.04);
}
.hero-panel.blue .matchup-hero-bg { object-position: 48% 12%; }
.hero-panel.red .matchup-hero-bg { object-position: 52% 12%; }

.hero-vignette {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(7, 10, 18, .05), transparent 36%, rgba(7, 10, 18, .72) 100%),
    linear-gradient(90deg, rgba(7, 10, 18, .2), transparent 40%, rgba(7, 10, 18, .18));
}

.corner-logo {
  position: absolute;
  top: 12px;
  width: 38px;
  height: 38px;
  object-fit: contain;
  border-radius: 50%;
  background: rgba(255, 255, 255, .72);
}
.hero-panel.blue .corner-logo { left: 12px; }
.hero-panel.red .corner-logo { right: 12px; }

.result-chip {
  position: absolute;
  top: 14px;
  color: rgba(255, 255, 255, .7);
  font-size: clamp(26px, 3vw, 42px);
  font-weight: 950;
  letter-spacing: -2px;
  text-shadow: 0 8px 22px rgba(0,0,0,.28);
}
.hero-panel.blue .result-chip { right: 12px; }
.hero-panel.red .result-chip { left: 12px; }
.result-chip.win { color: rgba(255, 255, 255, .92); }

.equip-stack {
  position: absolute;
  z-index: 2;
  bottom: 120px;
  display: grid;
  grid-template-columns: repeat(3, 32px);
  gap: 6px;
}
.hero-panel.blue .equip-stack { right: 12px; }
.hero-panel.red .equip-stack { left: 12px; }
.equip-stack img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  background: rgba(255,255,255,.74);
  box-shadow: 0 5px 14px rgba(0,0,0,.22);
}

.player-portrait-wrap {
  position: absolute;
  z-index: 2;
  bottom: 82px;
  width: 58px;
  height: 58px;
}
.hero-panel.blue .player-portrait-wrap { left: 14px; }
.hero-panel.red .player-portrait-wrap { right: 14px; }
.player-portrait {
  width: 58px;
  height: 58px;
  border: 2px solid rgba(255,255,255,.84);
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 9px 22px rgba(0,0,0,.24);
}

.side-stats {
  position: absolute;
  z-index: 2;
  left: 14px;
  right: 14px;
  bottom: 62px;
  display: flex;
  justify-content: space-between;
  color: #fff;
  font-family: Impact, Haettenschweiler, sans-serif;
  text-shadow: 0 3px 12px rgba(0,0,0,.35);
}
.gold-line { display: flex; align-items: center; gap: 5px; font-size: 21px; }
.coin {
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #5f4706;
  background: radial-gradient(circle, #f7d36b, #b9861b);
  font-family: Georgia, serif;
  font-size: 11px;
  font-weight: 950;
}
.kda-side { font-size: 21px; letter-spacing: 1px; }

.nameplate {
  position: absolute;
  z-index: 2;
  left: 14px;
  right: 14px;
  bottom: 16px;
  color: #fff;
}
.hero-panel.red .nameplate { text-align: right; }
.nameplate strong {
  display: block;
  font-size: clamp(28px, 3vw, 42px);
  font-weight: 950;
  line-height: .95;
  letter-spacing: -2px;
  text-shadow: 0 8px 22px rgba(0,0,0,.38);
}
.nameplate span {
  display: block;
  margin-top: 5px;
  color: rgba(255,255,255,.78);
  font-size: 13px;
  font-weight: 900;
}

.hero-panel :deep(.symbol-strip) {
  position: absolute;
  z-index: 5;
  bottom: 56px;
  display: flex;
  gap: 2px;
  flex-wrap: wrap;
}
.hero-panel.blue :deep(.symbol-strip) { right: 14px; }
.hero-panel.red :deep(.symbol-strip) { left: 14px; }
.hero-panel :deep(.symbol-icon) {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, .3);
  object-fit: cover;
}

.radar-poster {
  position: relative;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  padding: 18px 26px 16px;
  background:
    linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)),
    #f8f5ec;
}
.radar-poster::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(90deg, transparent 0 119px, rgba(0, 0, 0, .02) 120px);
  pointer-events: none;
}

.scoreboard,
.poster-subtitle,
.radar-arena,
.poster-footer { position: relative; z-index: 1; }

.scoreboard {
  display: grid;
  grid-template-columns: 84px minmax(92px, 1fr) 48px auto 48px minmax(92px, 1fr) 84px;
  align-items: center;
  gap: 8px;
}

.result-word {
  color: var(--mono-dim);
  font-size: clamp(28px, 3.4vw, 48px);
  font-weight: 950;
  letter-spacing: -3px;
  line-height: 1;
}
.result-word.win { color: var(--mono-ink); text-align: right; }
.team-name {
  min-width: 0;
  overflow: hidden;
  color: var(--mono-ink);
  font-size: 17px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-name.left { text-align: right; }
.team-logo { width: 48px; height: 48px; object-fit: contain; }
.score-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #1a2029;
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: clamp(42px, 5vw, 68px);
  line-height: .95;
}
.score-line .red-score { color: var(--purple); }
.poster-subtitle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  margin-top: 16px;
  color: var(--mono-soft);
  font-size: 14px;
  font-weight: 900;
}
.poster-subtitle b {
  color: var(--mono-ink);
  font-size: 22px;
}

.radar-arena {
  position: relative;
  min-height: 0;
}

.radar-svg {
  position: absolute;
  left: 50%;
  top: 50%;
  width: min(410px, 43vw);
  max-width: 100%;
  transform: translate(-50%, -48%);
  overflow: visible;
}

.summary-hero-orbit {
  position: absolute;
  z-index: 3;
  inset: 52px 0 126px;
  display: grid;
  grid-template-columns: 46px 1fr 46px;
  align-items: center;
  pointer-events: none;
}

.orbit-side {
  min-width: 0;
  display: grid;
  justify-items: center;
  gap: 7px;
}

.orbit-side.blue {
  grid-column: 1;
}

.orbit-side.red {
  grid-column: 3;
}

.orbit-label {
  display: block;
  color: var(--mono-soft);
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 1px;
}

.orbit-icons {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.orbit-hero {
  position: relative;
  width: 30px;
  height: 30px;
  padding: 2px;
  border: 1px solid rgba(26, 26, 26, .12);
  border-radius: 50%;
  background: rgba(255, 255, 255, .42);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 18px rgba(28, 41, 68, .1);
}

.orbit-hero.win {
  border-color: rgba(92, 78, 190, .42);
  background: rgba(92, 78, 190, .12);
}

.orbit-hero img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.orbit-hero b {
  position: absolute;
  right: -5px;
  bottom: -3px;
  min-width: 15px;
  height: 15px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255,255,255,.7);
  border-radius: 999px;
  color: #fff;
  background: var(--mono-ink);
  font-size: 7px;
  font-weight: 950;
}

.radar-highlights {
  position: absolute;
  z-index: 2;
  inset: 96px 56px 118px;
  display: grid;
  grid-template-columns: 132px 1fr 132px;
  align-items: center;
  pointer-events: none;
}

.highlight-column {
  display: grid;
  gap: 10px;
}

.highlight-column.left { grid-column: 1; }
.highlight-column.right { grid-column: 3; }

.highlight-card {
  padding: 9px 10px;
  border: 1px solid rgba(26, 26, 26, .08);
  border-radius: 14px;
  background: rgba(255, 255, 255, .46);
  box-shadow: 0 12px 26px rgba(28, 41, 68, .08);
  backdrop-filter: blur(14px);
}

.highlight-card.camp-1 {
  border-color: rgba(28, 164, 190, .22);
}

.highlight-card.camp-2 {
  border-color: rgba(92, 78, 190, .22);
}

.highlight-card span,
.highlight-card em {
  display: block;
  overflow: hidden;
  color: var(--mono-soft);
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.highlight-card strong {
  display: block;
  margin: 2px 0 1px;
  color: var(--mono-ink);
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: 24px;
  line-height: 1;
}

.outer-grid {
  fill: rgba(0, 0, 0, .03);
  stroke: rgba(0, 0, 0, .18);
  stroke-width: 2;
}
.inner-grid {
  fill: none;
  stroke: rgba(0, 0, 0, .1);
  stroke-width: 1.4;
}
.axis-line {
  stroke: rgba(0, 0, 0, .08);
  stroke-width: 1.2;
}
.blue-poly,
.winner-poly {
  fill: rgba(26, 26, 26, .15);
}
.red-poly,
.loser-poly {
  fill: rgba(26, 26, 26, .08);
}
.blue-line,
.red-line,
.winner-line,
.loser-line {
  fill: none;
  stroke-width: 3;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.blue-line,
.winner-line { stroke: var(--winner); }
.red-line,
.loser-line { stroke: var(--loser); }
.point {
  cursor: pointer;
  stroke: #fff;
  stroke-width: 2;
}
.blue-point { fill: var(--purple); }
.red-point { fill: var(--mono-soft); }

.metric-callout.active {
  filter: drop-shadow(0 8px 16px rgba(78, 73, 135, .18));
}
.metric-values {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  color: #0f141c;
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: 27px;
  letter-spacing: 1px;
}
.metric-values .winner-num,
.metric-values .loser-num,
.metric-values .hot,
.metric-callout span {
  display: inline-block;
  margin-top: 2px;
  padding: 3px 14px 4px;
  color: #fff;
  background: var(--mono-ink);
  border-radius: 2px;
  font-size: 14px;
  font-weight: 950;
  line-height: 1.05;
}

.pos-0 { left: 50%; top: 42px; transform: translateX(-50%); }
.pos-1 { right: 18%; top: 118px; }
.pos-2 { right: 14%; top: 218px; }
.pos-3 { right: 18%; bottom: 112px; }
.pos-4 { left: 50%; bottom: 36px; transform: translateX(-50%); }
.pos-5 { left: 18%; bottom: 112px; }
.pos-6 { left: 14%; top: 218px; }
.pos-7 { left: 18%; top: 118px; }

.poster-footer {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: end;
  gap: 16px;
  margin-top: -8px;
}
.footer-player {
  display: flex;
  flex-direction: column;
}
.footer-player strong {
  color: var(--mono-ink);
  font-size: 28px;
  font-weight: 950;
}
.footer-player span {
  display: block;
  color: var(--mono-soft);
  font-size: 13px;
  font-weight: 900;
}
.footer-player.right {
  text-align: right;
  align-items: flex-end;
}

.legend {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mono-soft);
  font-size: 12px;
  font-weight: 900;
}
.legend span {
  width: 11px;
  height: 11px;
  display: inline-block;
}
.blue-mark { background: var(--mono-ink); }
.red-mark { background: rgba(26, 26, 26, .35); }

.mvp-tag {
  background: linear-gradient(135deg, #f7d36b, #e6a817, #f7d36b);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-size: 14px;
  font-weight: 950;
  letter-spacing: 1px;
  animation: mvp-shimmer 3s ease-in-out infinite;
}
.footer-player .mvp-tag { margin-bottom: 2px; }
.mvp-tag.lose {
  background: linear-gradient(135deg, #c0c0c0, #a0a0a0, #c0c0c0);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-size: 12px;
  animation: none;
}
@keyframes mvp-shimmer {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.hero-panel :deep(.matchup-hero-bg) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 12%;
  filter: saturate(1.04) contrast(1.04);
}

.hero-panel.blue :deep(.matchup-hero-bg) { object-position: 48% 12%; }
.hero-panel.red :deep(.matchup-hero-bg) { object-position: 52% 12%; }

.hero-panel :deep(.hero-vignette) {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(7, 10, 18, .04), transparent 34%, rgba(7, 10, 18, .76) 100%),
    linear-gradient(90deg, rgba(7, 10, 18, .2), transparent 40%, rgba(7, 10, 18, .18));
}

.hero-panel :deep(.corner-logo) {
  position: absolute;
  top: 12px;
  width: 38px;
  height: 38px;
  object-fit: contain;
  border-radius: 50%;
  background: rgba(255, 255, 255, .72);
}

.hero-panel.blue :deep(.corner-logo) { left: 12px; }
.hero-panel.red :deep(.corner-logo) { right: 12px; }

.hero-panel :deep(.result-chip) {
  position: absolute;
  top: 14px;
  color: rgba(255, 255, 255, .7);
  font-size: clamp(26px, 3vw, 42px);
  font-weight: 950;
  letter-spacing: -2px;
  text-shadow: 0 8px 22px rgba(0,0,0,.28);
}

.hero-panel.blue :deep(.result-chip) { right: 12px; }
.hero-panel.red :deep(.result-chip) { left: 12px; }
.hero-panel :deep(.result-chip.win) { color: rgba(255, 255, 255, .92); }

.hero-panel :deep(.equip-stack) {
  position: absolute;
  z-index: 2;
  bottom: 120px;
  display: grid;
  grid-template-columns: repeat(3, 32px);
  gap: 6px;
}

.hero-panel.blue :deep(.equip-stack) { right: 12px; }
.hero-panel.red :deep(.equip-stack) { left: 12px; }

.hero-panel :deep(.equip-stack img) {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
  background: rgba(255,255,255,.74);
  box-shadow: 0 5px 14px rgba(0,0,0,.22);
}

.hero-panel :deep(.player-portrait-wrap) {
  position: absolute;
  z-index: 3;
  bottom: 82px;
  width: 58px;
  height: 58px;
}

.hero-panel.blue :deep(.player-portrait-wrap) { left: 14px; }
.hero-panel.red :deep(.player-portrait-wrap) { right: 14px; }

.hero-panel :deep(.player-portrait) {
  width: 38px;
  height: 38px;
  border: 2px solid rgba(255,255,255,.84);
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 9px 22px rgba(0,0,0,.24);
}

.hero-panel :deep(.side-stats) {
  position: absolute;
  z-index: 2;
  left: 14px;
  right: 14px;
  bottom: 62px;
  display: flex;
  justify-content: space-between;
  color: #fff;
  font-family: Impact, Haettenschweiler, sans-serif;
  text-shadow: 0 3px 12px rgba(0,0,0,.35);
}

.hero-panel :deep(.gold-line) {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 21px;
}

.hero-panel :deep(.coin) {
  width: 12px;
  height: 12px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #5f4706;
  background: radial-gradient(circle, #f7d36b, #b9861b);
  font-family: Georgia, serif;
  font-size: 8px;
  font-weight: 950;
}

.hero-panel :deep(.kda-side) {
  font-size: 21px;
  letter-spacing: 1px;
}

.hero-panel :deep(.nameplate) {
  position: absolute;
  z-index: 2;
  left: 14px;
  right: 14px;
  bottom: 16px;
  color: #fff;
}

.hero-panel.red :deep(.nameplate) { text-align: right; }

.hero-panel :deep(.nameplate strong) {
  display: block;
  font-size: clamp(28px, 3vw, 42px);
  font-weight: 950;
  line-height: .95;
  letter-spacing: -2px;
  text-shadow: 0 8px 22px rgba(0,0,0,.38);
}

.hero-panel :deep(.nameplate span) {
  display: block;
  margin-top: 5px;
  color: rgba(255,255,255,.78);
  font-size: 13px;
  font-weight: 900;
}

.poster-shell {
  animation: poster-rise .42s cubic-bezier(.2, .8, .2, 1) both;
}

.scoreboard {
  position: relative;
  min-height: 72px;
  margin: -10px -12px 10px;
  padding: 8px 16px;
  display: grid;
  grid-template-columns: 52px minmax(120px, 1fr) 120px minmax(90px, auto) 120px minmax(120px, 1fr) 52px;
  align-items: center;
  gap: 12px;
  color: var(--mono-ink);
  background:
    linear-gradient(90deg, rgba(0, 0, 0, .04), transparent 27%, transparent 73%, rgba(0, 0, 0, .03)),
    linear-gradient(120deg, rgba(255,255,255,.78), rgba(239, 247, 250, .62));
  border: 1px solid rgba(255, 255, 255, .78);
  border-radius: 18px;
  box-shadow: inset 0 0 28px rgba(255,255,255,.42), 0 12px 28px rgba(28, 41, 68, .1);
  backdrop-filter: blur(16px);
}

.scoreboard .team-logo {
  width: 48px;
  height: 48px;
  padding: 3px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .74);
  box-shadow: 0 8px 18px rgba(66, 78, 115, .14);
}

.scoreboard .team-name {
  color: var(--mono-ink);
  font-size: clamp(18px, 1.9vw, 27px);
  text-shadow: 0 1px 0 rgba(255,255,255,.65);
}

.scoreboard .team-name.left { grid-column: 2; }
.scoreboard .team-name.right { grid-column: 6; text-align: left; }
.scoreboard .blue-logo { grid-column: 1; }
.scoreboard .red-logo { grid-column: 7; }

.score-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: grid;
  justify-items: center;
  gap: 2px;
  color: var(--mono-ink);
  line-height: 1;
  z-index: 2;
}

.score-center strong {
  font-size: clamp(18px, 2vw, 27px);
  font-weight: 950;
  letter-spacing: 1px;
}

.score-center span {
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: clamp(22px, 2.6vw, 36px);
  letter-spacing: 4px;
  color: var(--accent-deep);
}
.game-duration {
  font-size: 12px;
  font-weight: 800;
  color: var(--mono-dim);
  font-style: normal;
  letter-spacing: 1px;
}

.win-badge {
  position: absolute;
  left: calc(50% + 74px);
  top: 50%;
  transform: translateY(-50%);
  min-width: 34px;
  height: 30px;
  padding: 0 10px;
  display: inline-grid;
  place-items: center;
  justify-self: center;
  border: 1px solid var(--mono-line);
  border-radius: 999px;
  color: var(--mono-ink);
  background: var(--mono-panel);
  box-shadow: 0 4px 12px rgba(0, 0, 0, .06);
  font-size: 13px;
  font-weight: 950;
  letter-spacing: 1px;
}

.win-badge.blue {
  color: var(--accent-deep);
}

.radar-svg {
  width: min(390px, 40vw);
  animation: radar-focus .48s cubic-bezier(.2, .8, .2, 1) both;
}

.blue-poly,
.red-poly,
.winner-poly,
.loser-poly,
.blue-line,
.red-line,
.winner-line,
.loser-line {
  transform-box: fill-box;
  transform-origin: center;
  animation: radar-grow .62s cubic-bezier(.19, 1, .22, 1) both;
}

.red-poly,
.red-line,
.loser-poly,
.loser-line { animation-delay: .08s; }

.metric-callout.horizontal {
  min-width: 210px;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  justify-items: center;
  gap: 8px;
}


.metric-values,
.metric-values b,
.metric-callout .metric-label {
  display: inline-block;
  color: #1a1a1a;
  background: rgba(255, 255, 255, 0.7);
  padding: 2px 6px;
  border-radius: 5px;
  font-size: 15px;
  font-weight: 950;
  line-height: 1.1;
  white-space: nowrap;
}

.hero-panel :deep(.portrait-block),
.hero-panel :deep(.equip-block) {
  position: absolute;
  z-index: 4;
  display: grid;
  justify-items: center;
  gap: 5px;
  color: #fff;
  text-shadow: 0 3px 12px rgba(0,0,0,.36);
}
.hero-panel :deep(.portrait-block) { bottom: 85px; }
.hero-panel :deep(.equip-block) { bottom: 98px; }

.hero-panel.blue :deep(.portrait-block) { left: 14px; }
.hero-panel.red :deep(.portrait-block) { right: 14px; }
.hero-panel.blue :deep(.equip-block) { right: 12px; }
.hero-panel.red :deep(.equip-block) { left: 12px; }

.hero-panel :deep(.summoner-icon),
.hero-panel :deep(.summoner-corner) {
  width: 30px;
  height: 30px;
  border: 2px solid rgba(255,255,255,.84);
  border-radius: 50%;
  object-fit: cover;
  background: rgba(255,255,255,.7);
  box-shadow: 0 6px 16px rgba(0,0,0,.24);
}

.hero-panel :deep(.summoner-corner) {
  position: absolute;
  z-index: 5;
  bottom: 18px;
  width: 28px;
  height: 28px;
}
.hero-panel.blue :deep(.summoner-corner) { right: 14px; }
.hero-panel.red :deep(.summoner-corner) { left: 14px; }

.hero-panel :deep(.player-portrait-wrap) {
  position: static;
  width: 38px;
  height: 38px;
}

.hero-panel :deep(.equip-stack) {
  position: static;
  display: grid;
  grid-template-columns: repeat(3, 20px);
  gap: 5px;
}

.hero-panel :deep(.side-stats) {
  display: none;
}

.hero-panel :deep(.gold-line),
.hero-panel :deep(.kda-side) {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #fff;
  font-family: Impact, Haettenschweiler, sans-serif;
  line-height: 1;
  letter-spacing: .5px;
}
.hero-panel :deep(.gold-line) { font-size: 17px; }
.hero-panel :deep(.kda-side) { font-size: 14px; }

.hero-panel :deep(.nameplate) {
  bottom: 16px;
}

.hero-panel :deep(.nameplate strong),
.hero-panel :deep(.nameplate span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes poster-rise {
  from { opacity: 0; transform: translateY(10px) scale(.992); filter: blur(6px); }
  to { opacity: 1; transform: translateY(0) scale(1); filter: blur(0); }
}

@keyframes radar-focus {
  from { opacity: 0; transform: translate(-50%, -48%) scale(.92); }
  to { opacity: 1; transform: translate(-50%, -48%) scale(1); }
}

@keyframes radar-grow {
  from { opacity: 0; transform: scale(.25); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes point-pop {
  from { opacity: 0; transform: scale(.2); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes callout-in {
  from { opacity: 0; filter: blur(4px); }
  to { opacity: 1; filter: blur(0); }
}

@keyframes value-pop {
  0% { opacity: 0; transform: translateY(4px) scale(.92); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1280px) {
  .poster-shell {
    grid-template-columns: minmax(160px, 220px) minmax(520px, 1fr) minmax(160px, 220px);
  }
  .scoreboard {
    min-height: 66px;
    margin: -8px -10px 8px;
    padding: 7px 14px;
    grid-template-columns: 46px minmax(116px, 1fr) minmax(96px, auto) 64px minmax(116px, 1fr) 46px;
    gap: 10px;
  }
  .scoreboard .team-logo { width: 46px; height: 46px; }
  .team-name { font-size: 15px; }
  .scoreboard .team-name { font-size: clamp(16px, 2vw, 24px); }
  .score-center strong { font-size: 18px; }
  .score-center span { font-size: 28px; }
  .win-badge { min-width: 58px; height: 28px; font-size: 12px; }
  .radar-poster { padding: 14px 18px 12px; }
  .radar-svg { width: min(360px, 41vw); }
.metric-values { font-size: 20px; }
.result-word { font-size: 34px; }
  .nameplate strong { font-size: 32px; }
}

@media (max-width: 980px) {
  .matchup-page {
    height: auto;
    min-height: 100dvh;
    overflow-y: auto;
  }
  .select-strip {
    grid-template-columns: 1fr 1fr;
    height: auto;
  }
  .poster-shell {
    height: auto;
    grid-template-columns: 1fr;
    overflow: hidden;
  }
  .hero-panel {
    min-height: 260px;
    height: 260px;
  }
  .matchup-hero-bg {
    height: 100%;
  }
  .player-portrait-wrap,
  .equip-stack {
    display: none;
  }
  .side-stats,
  .nameplate {
    left: 18px;
    right: 18px;
  }
  .side-stats { bottom: 78px; }
  .nameplate { bottom: 22px; }
  .radar-poster {
    height: auto;
    min-height: 620px;
    order: -1;
  }
}

@media (max-width: 767px) {
  .matchup-page {
    margin-left: 0;
    padding: 8px 8px calc(74px + env(safe-area-inset-bottom));
  }
  .select-strip,
  .state-card,
  .poster-shell {
    width: 100%;
  }
  .select-strip {
    grid-template-columns: 1fr;
  }
  .role-tabs {
    height: 42px;
    overflow-x: auto;
  }
  .role-tabs button {
    min-width: 74px;
  }
  .radar-poster {
    min-height: 620px;
    padding: 16px 10px;
  }
  .scoreboard {
    min-height: 58px;
    margin: -8px -6px 8px;
    padding: 7px 8px;
    grid-template-columns: minmax(0, 1fr) minmax(72px, auto) 54px minmax(0, 1fr);
    gap: 7px;
  }
  .scoreboard .team-logo {
    display: none;
  }
  .team-name {
    font-size: 13px;
  }
  .score-center strong { font-size: 13px; }
  .score-center span {
    font-size: 21px;
    letter-spacing: 2px;
  }
  .win-badge {
    min-width: 50px;
    height: 26px;
    padding-inline: 8px;
    font-size: 11px;
  }
  .poster-subtitle {
    flex-direction: column;
    align-items: center;
    gap: 3px;
  }
  .radar-svg {
    width: 330px;
  }
.metric-values {
    gap: 5px;
    font-size: 17px;
  }
.pos-1,
  .pos-2,
  .pos-3 { right: 0; }
  .pos-5,
  .pos-6,
  .pos-7 { left: 0; }
  .poster-footer {
    grid-template-columns: 1fr;
    gap: 8px;
    text-align: center;
  }
  .footer-player.right {
    text-align: center;
  }
  .legend {
    justify-content: center;
    order: -1;
  }
  .hero-panel {
    min-height: 210px;
    height: 210px;
  }
  .gold-line,
  .kda-side {
    font-size: 22px;
  }
  .nameplate strong {
    font-size: 30px;
  }
}

/* ── dark theme overrides ── */
.theme-dark .scoreboard {
  background:
    linear-gradient(90deg, rgba(255, 255, 255, .03), transparent 27%, transparent 73%, rgba(255, 255, 255, .02)),
    linear-gradient(120deg, rgba(30, 30, 30, .78), rgba(20, 20, 20, .62));
  border-color: rgba(255, 255, 255, .08);
  box-shadow: inset 0 0 28px rgba(255, 255, 255, .04), 0 12px 28px rgba(0, 0, 0, .3);
}

.theme-dark .scoreboard .team-logo {
  background: rgba(255, 255, 255, .12);
  box-shadow: 0 8px 18px rgba(0, 0, 0, .3);
}

.theme-dark .win-badge {
  border-color: rgba(255, 255, 255, .12);
  background: rgba(30, 30, 30, .92);
}

.theme-dark .metric-callout .metric-label {
  background: rgba(255, 255, 255, .08);
}

.theme-dark .poster-footer {
  border-color: rgba(255, 255, 255, .08);
}

.theme-dark .legend {
  color: var(--mono-soft);
}

.theme-dark .role-tabs button {
  border-color: rgba(255, 255, 255, .12);
  color: var(--mono-soft);
}

.theme-dark .role-tabs button:hover {
  color: var(--mono-ink);
}

.theme-dark .role-tabs button.active {
  color: #0a0a0a;
}

.theme-dark .role-pill {
  background: var(--mono-ink);
}

.theme-dark .hero-panel {
  background: #0a0a0a;
}

.theme-dark .radar-poster {
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.theme-dark .radar-poster::before {
  background: repeating-linear-gradient(90deg, transparent 0 119px, rgba(255, 255, 255, .03) 120px);
}

.theme-dark .outer-grid {
  fill: rgba(255, 255, 255, .04);
  stroke: rgba(255, 255, 255, .15);
}

.theme-dark .inner-grid {
  stroke: rgba(255, 255, 255, .08);
}

.theme-dark .axis-line {
  stroke: rgba(255, 255, 255, .06);
}

.theme-dark .metric-values {
  color: #e8e8e8;
}
.theme-dark .metric-values .winner-num,
.theme-dark .metric-values .loser-num,
.theme-dark .score-line { color: #e8e8e8; }

.theme-dark .winner-poly { fill: rgba(232, 232, 232, .15); }
.theme-dark .loser-poly { fill: rgba(232, 232, 232, .08); }
.theme-dark .winner-line { stroke: #e8e8e8; }
.theme-dark .loser-line { stroke: rgba(232, 232, 232, .45); }

.theme-dark :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, .06) !important;
  border-color: rgba(255, 255, 255, .12) !important;
  box-shadow: none !important;
}
.theme-dark :deep(.el-select__wrapper.is-focused) {
  border-color: var(--mono-ink) !important;
}

.theme-dark :deep(.el-select__selected-item),
.theme-dark :deep(.el-select__placeholder),
.theme-dark :deep(.el-select__input) {
  color: rgba(232, 232, 232, .88) !important;
}

.theme-dark :deep(.el-select__placeholder.is-transparent) {
  color: rgba(232, 232, 232, .48) !important;
}

.theme-dark .spinner {
  border-color: rgba(255, 255, 255, .1);
  border-top-color: var(--mono-ink);
}

</style>

<style>
/* 下拉面板全局样式（Teleport 到 body，scoped 覆盖不到） */
.el-select__popper {
  border: 1px solid rgba(0, 0, 0, 0.3) !important;
  box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.08) !important;
  background: #fff !important;
}
.el-select-dropdown__list { padding: 4px 0 !important; }
.el-select-dropdown__item {
  padding: 8px 16px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  color: #1a1a1a !important;
  transition: background 0.1s, padding-left 0.15s;
}
.el-select-dropdown__item.is-hovering {
  background: rgba(0, 0, 0, 0.04) !important;
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
  border-color: rgba(255, 255, 255, 0.25) !important;
  background: #1a1a1a !important;
  box-shadow: 4px 4px 0 rgba(0, 0, 0, 0.3) !important;
}
.theme-dark .el-select-dropdown__item {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-hovering {
  background: rgba(255, 255, 255, 0.06) !important;
}
.theme-dark .el-select-dropdown__item.is-selected {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-selected::after {
  background: #e8e8e8;
}
</style>

<style>
/* ── 竖屏：侧边栏置顶 ── */
@media (max-width: 1120px) and (orientation: portrait), (pointer: coarse) and (orientation: portrait) {
  .sidebar {
    z-index: 100001 !important;
  }

  .matchup-page::after {
    content: "请横屏观看对位雷达图";
    position: fixed;
    z-index: 99999;
    inset: 0;
    display: grid;
    place-items: center;
    padding: 24px;
    color: #1a1a1a;
    background: radial-gradient(circle at 50% 34%, rgba(255,255,255,.9), rgba(248,245,236,.98) 54%, #f8f5ec);
    font-size: 22px;
    font-weight: 950;
    letter-spacing: 1px;
    text-align: center;
  }

  .matchup-page::before {
    content: "旋转手机后可获得和 PC 端一致的海报布局";
    position: fixed;
    z-index: 100000;
    left: 24px;
    right: 24px;
    top: calc(50% + 42px);
    color: rgba(26, 26, 26, .58);
    font-size: 13px;
    font-weight: 800;
    text-align: center;
  }
}

/* ── 横屏布局 ── */
@media (max-width: 1120px) and (orientation: landscape), (pointer: coarse) and (orientation: landscape) {
  .sidebar {
    display: none !important;
  }

  html,
  body,
  #app {
    width: 100% !important;
    height: 100% !important;
    min-height: 100% !important;
    overflow: hidden !important;
  }

  .matchup-page {
    width: 100vw !important;
    height: 100svh !important;
    min-height: 0 !important;
    margin-left: 0 !important;
    padding: 6px 8px 7px !important;
    overflow: hidden !important;
    touch-action: manipulation !important;
  }

  .select-strip {
    position: relative !important;
    top: auto !important;
    width: 100% !important;
    max-width: none !important;
    height: 42px !important;
    display: grid !important;
    grid-template-columns: minmax(150px, 1fr) minmax(280px, 2.55fr) !important;
    gap: 6px !important;
    margin: 0 0 6px !important;
    padding: 0 !important;
    backdrop-filter: none !important;
  }

  .select-left,
  .select-strip label {
    min-height: 0 !important;
    height: 42px !important;
    border-radius: 0 !important;
    border: 1px solid var(--mono-line) !important;
    background: transparent !important;
  }

  .select-center :deep(.el-select__wrapper) {
    background: transparent !important;
    border: 1px solid var(--mono-line) !important;
  }

  .select-right {
    display: none !important;
  }

  .select-center {
    display: grid !important;
    grid-template-columns: minmax(100px, 1.1fr) minmax(72px, .6fr) minmax(180px, 1.8fr) !important;
    gap: 6px !important;
    height: 42px !important;
  }

  .select-center .match-select,
  .select-center .battle-select,
  .select-center .role-tabs {
    grid-column: auto !important;
    width: auto !important;
  }

  .select-strip label {
    padding: 4px 7px !important;
  }

  .select-strip label span {
    font-size: 10px !important;
  }

  .role-tabs {
    height: 42px !important;
    display: grid !important;
    grid-template-columns: repeat(5, minmax(0, 1fr)) !important;
    gap: 4px !important;
    padding: 3px !important;
    border: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    overflow: visible !important;
  }

  .role-tabs button {
    min-width: 0 !important;
    border-radius: 9px !important;
    font-size: 11px !important;
    writing-mode: horizontal-tb !important;
    white-space: nowrap !important;
  }

  .role-pill {
    top: 3px !important;
    height: calc(100% - 6px) !important;
    border-radius: 9px !important;
  }

  .poster-subtitle {
    visibility: hidden !important;
    height: 0 !important;
    min-height: 0 !important;
    margin: 0 !important;
    padding: 0 !important;
    overflow: hidden !important;
  }

  .hero-panel :deep(.symbol-strip) {
    display: none !important;
  }

}

/* SVG ?? */
.mobile-radar-labels {
  display: block;
}
.mobile-radar-labels text {
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: 14px;
  font-weight: 900;
  paint-order: stroke;
  stroke: rgba(255, 255, 255, .78);
  stroke-width: 3px;
  stroke-linejoin: round;
}
.mobile-radar-labels .mobile-label {
  font-size: 11px;
  fill: var(--mono-ink);
  stroke: rgba(255, 255, 255, .92);
  letter-spacing: .5px;
}
.mobile-radar-labels .mobile-value {
  font-size: 14px;
  fill: var(--mono-ink);
}
.mobile-radar-labels .mobile-value.winner {
  fill: var(--mono-ink);
}
.mobile-radar-labels .mobile-value.loser {
  fill: var(--loser);
}
.mobile-radar-labels .mobile-value.hot { fill: #ff5b5b; }
@media (max-width: 1120px) and (orientation: landscape), (pointer: coarse) and (orientation: landscape) {
  .poster-shell {
    width: 100% !important;
    max-width: none !important;
    height: calc(100svh - 55px) !important;
    min-height: 0 !important;
    display: grid !important;
    grid-template-columns: minmax(118px, 17vw) minmax(0, 1fr) minmax(118px, 17vw) !important;
    gap: 6px !important;
    overflow: hidden !important;
  }
  .hero-panel {
    display: block !important;
    min-width: 0 !important;
    min-height: 0 !important;
    height: 100% !important;
    order: 0 !important;
  }
  .radar-poster {
    order: 0 !important;
    height: 100% !important;
    min-height: 0 !important;
    width: 100% !important;
    padding: 6px 8px 6px !important;
  }
  .scoreboard {
    min-height: 48px !important;
    margin: -4px -4px 3px !important;
    padding: 4px 8px !important;
    display: grid !important;
    grid-template-columns: 32px minmax(0, 1fr) 96px minmax(0, 1fr) 32px !important;
    gap: 4px !important;
    overflow: hidden !important;
  }
  .scoreboard .blue-logo {
    grid-column: 1 !important;
  }
  .scoreboard .team-name.left {
    grid-column: 2 !important;
    text-align: left !important;
  }
  .scoreboard .team-name.right {
    grid-column: 4 !important;
    text-align: right !important;
  }
  .scoreboard .red-logo {
    grid-column: 5 !important;
  }
  .scoreboard .team-logo {
    display: block !important;
    width: 28px !important;
    height: 28px !important;
  }
  .score-center {
    left: 50% !important;
    top: 50% !important;
  }
  .score-center strong {
    font-size: 13px !important;
  }
  .score-center span {
    font-size: 24px !important;
    letter-spacing: 1px !important;
  }
  .win-badge {
    left: calc(50% + 64px) !important;
    min-width: 32px !important;
    height: 22px !important;
    padding: 0 6px !important;
    font-size: 11px !important;
  }
  .poster-footer {
    margin-top: -8px !important;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr) !important;
    align-items: end !important;
  }
  .radar-arena {
    min-height: 0 !important;
  }
  .radar-svg {
    width: min(235px, 29vw) !important;
    transform: translate(-50%, -50%) !important;
  }
  .hero-panel :deep(.result-chip) {
    font-size: clamp(20px, 3vw, 28px) !important;
  }
  .hero-panel :deep(.corner-logo) {
    width: 30px !important;
    height: 30px !important;
  }
  .hero-panel :deep(.portrait-block),
  .hero-panel :deep(.equip-block),
  .hero-panel :deep(.nameplate) {
    display: grid !important;
  }
  .hero-panel :deep(.portrait-block) {
    bottom: 50px !important;
  }
  .hero-panel :deep(.equip-block) {
    bottom: 54px !important;
  }
  .hero-panel :deep(.player-portrait-wrap) {
    width: 34px !important;
    height: 34px !important;
  }
  .hero-panel :deep(.player-portrait) {
    width: 34px !important;
    height: 34px !important;
  }
  .hero-panel :deep(.equip-stack) {
    grid-template-columns: repeat(3, 18px) !important;
    gap: 3px !important;
  }
  .hero-panel :deep(.equip-stack img) {
    width: 18px !important;
    height: 18px !important;
  }
  .hero-panel :deep(.gold-line) {
    font-size: 12px !important;
  }
  .hero-panel :deep(.kda-side) {
    font-size: 10px !important;
  }
  .hero-panel :deep(.nameplate) {
    left: 8px !important;
    right: 8px !important;
    bottom: 8px !important;
  }
  .hero-panel :deep(.nameplate strong) {
    overflow: hidden !important;
    font-size: clamp(16px, 2.1vw, 22px) !important;
    letter-spacing: -1px !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
  }
  .hero-panel :deep(.nameplate span) {
    overflow: hidden !important;
    font-size: 9px !important;
    line-height: 1.12 !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
  }
  .radar-highlights {
    inset: 74px 10px 54px !important;
    display: grid !important;
    grid-template-columns: 86px 1fr 86px !important;
    align-items: center !important;
    pointer-events: none !important;
  }
  .highlight-column {
    gap: 6px !important;
  }
  .highlight-card {
    padding: 5px 6px !important;
    border-radius: 9px !important;
    background: rgba(255, 255, 255, .58) !important;
    box-shadow: 0 8px 18px rgba(28, 41, 68, .08) !important;
  }
  .highlight-card:nth-child(n + 3) {
    display: none !important;
  }
  .highlight-card span,
  .highlight-card em {
    font-size: 8px !important;
    line-height: 1.05 !important;
  }
  .highlight-card strong {
    margin: 1px 0 !important;
    font-size: 15px !important;
  }
  .mobile-radar-labels {
    display: block;
  }
  .mobile-radar-labels text {
    font-size: 14px;
    font-weight: 950;
    text-rendering: geometricPrecision;
    shape-rendering: geometricPrecision;
    -webkit-font-smoothing: antialiased;
  }
  .mobile-radar-labels .mobile-label {
    font-size: 10px;
    font-weight: 850;
    letter-spacing: .2px;
  }
  .scoreboard .team-name {
    min-width: 0 !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
    word-break: keep-all !important;
    line-height: 1.08 !important;
    font-size: 12px !important;
  }
  .footer-player strong {
    overflow: hidden !important;
    font-size: 20px !important;
    line-height: 1 !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
  }
  .footer-player span {
    overflow: hidden !important;
    font-size: 12px !important;
    line-height: 1.15 !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
  }
  .wheel-hint {
    display: none !important;
  }

  .select-strip {
    height: 42px !important;
    margin: 3px auto 5px !important;
  }
  .select-left {
    min-width: 92px !important;
  }
  .select-strip label {
    padding: 2px 5px !important;
    gap: 4px !important;
  }
  .select-strip label span {
    font-size: 9px !important;
  }
  .select-center {
    gap: 4px !important;
  }
  .select-center .match-select {
    flex: 2.7 1 0 !important;
  }
  .select-center .battle-select {
    flex: 3.8 1 0 !important;
  }
  .select-center .role-tabs {
    flex: 3 1 0 !important;
  }
  .role-tabs {
    gap: 3px !important;
    padding: 3px !important;
  }
  .role-tabs button {
    font-size: 10px !important;
    padding-inline: 4px !important;
  }
  :deep(.el-select__wrapper) {
    min-height: 24px !important;
  }
}
</style>
