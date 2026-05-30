<template>
  <main class="app-shell">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>赛场数据中控</h1>
        </div>
      </div>

      <div class="status-line">
        <span class="status-pill" :class="{ online: system.database === 'UP' }">
          <i></i>DB {{ system.database || '...' }}
        </span>
        <span class="status-pill" :class="{ online: system.redis === 'UP' }">
          <i></i>Redis {{ system.redis || '...' }}
        </span>
        <span class="status-pill" :class="{ online: system.aiConfigured }">
          <i></i>AI {{ system.aiConfigured ? system.aiModel : 'Fallback' }}
        </span>
      </div>
    </section>

    <section class="layout-grid">
      <aside class="side-rail panel">
        <div class="section-head">
          <span>赛事信号</span>
          <el-button :icon="Refresh" circle class="icon-ghost" @click="refreshAll" />
        </div>

        <el-select v-model="selectedLeagueId" class="league-select" placeholder="选择赛事" @change="loadDashboard">
          <el-option
            v-for="league in leagues"
            :key="league.leagueId"
            :label="league.leagueName"
            :value="league.leagueId"
          />
        </el-select>

        <div class="league-plate" v-if="currentLeague">
          <span>{{ currentLeague.leagueType }}</span>
          <strong>{{ currentLeague.leagueName }}</strong>
          <small>{{ formatDate(currentLeague.startTime) }}</small>
        </div>
        <div class="league-plate muted" v-else>
          <span>NO DATA</span>
          <strong>等待同步</strong>
          <small>先拉取赛事列表</small>
        </div>

        <div class="sync-grid">
          <el-button :icon="Download" :loading="loading.sync" class="sync-all-btn" @click="runSync('/api/sync/all')">同步全部历史</el-button>
          <el-button :icon="Lightning" :loading="loading.sync" @click="runSync('/api/sync/latest')">最新赛季</el-button>
          <el-button :icon="RefreshRight" :loading="loading.sync" @click="runSync('/api/sync/latest/incremental')">增量刷新</el-button>
          <el-button :icon="Aim" :loading="loading.sync" @click="runSync('/api/sync/latest/deep-incremental?matchLimit=10')">对局详情</el-button>
        </div>

        <div class="arena-visual" aria-hidden="true">
          <div class="lane lane-a"></div>
          <div class="lane lane-b"></div>
          <div class="lane lane-c"></div>
          <span class="node n1"></span>
          <span class="node n2"></span>
          <span class="node n3"></span>
          <span class="node n4"></span>
        </div>

        <div class="job-feed">
          <div class="mini-title">同步队列</div>
          <div v-for="job in jobs" :key="job.id" class="job-row">
            <span :class="['job-state', job.status?.toLowerCase()]">{{ job.status }}</span>
            <b>{{ job.jobType }}</b>
            <small>{{ formatTime(job.startedAt) }}</small>
          </div>
        </div>
      </aside>

      <section class="main-board">
        <div class="panel query-panel">
          <div class="section-head">
            <span>数据查询</span>
            <el-segmented v-model="queryMode" :options="queryModes" />
          </div>

          <div class="query-bar">
            <el-select v-if="queryMode === 'playerTop'" v-model="playerSort" class="sort-select" @change="runQuery">
              <el-option label="KDA 排行" value="kda" />
              <el-option label="胜率排行" value="win" />
            </el-select>
            <el-select v-if="queryMode === 'heroTop'" v-model="heroSort" class="sort-select" @change="runQuery">
              <el-option label="Pick 率" value="pick" />
              <el-option label="Ban 率" value="ban" />
              <el-option label="胜率" value="win" />
            </el-select>
            <el-input
              v-if="queryMode !== 'honors'"
              v-model="queryKeyword"
              :prefix-icon="Search"
              :placeholder="placeholder"
              clearable
              @keyup.enter="runQuery"
            />
            <el-button v-if="queryMode !== 'honors'" :icon="DataAnalysis" :loading="loading.query" class="primary-action" @click="runQuery">
              查询
            </el-button>
          </div>

          <div class="metric-row">
            <div class="metric-tile">
              <span>结果</span>
              <strong>{{ resultCount }}</strong>
            </div>
            <div class="metric-tile">
              <span>模式</span>
              <strong>{{ modeLabel }}</strong>
            </div>
            <div class="metric-tile">
              <span>{{ queryMode === 'honors' ? '范围' : '赛事' }}</span>
              <strong>{{ queryMode === 'honors' ? '全历史' : (selectedLeagueId || 'AUTO') }}</strong>
            </div>
          </div>

          <el-table class="data-table" :data="tableRows" height="360" empty-text="暂无数据">
            <el-table-column label="#" width="70" align="center" type="index" />
            <el-table-column v-for="col in tableColumns" :key="col.prop" :prop="col.prop" :label="col.label" min-width="110" />
          </el-table>
        </div>

        <div class="bottom-grid">
          <div class="panel chart-panel">
            <div class="section-head">
              <span>战队强度</span>
              <el-button :icon="TrendCharts" circle class="icon-ghost" @click="loadTeamRanking" />
            </div>
            <div ref="teamChartRef" class="chart-canvas"></div>
          </div>

          <div class="panel agent-panel">
            <div class="section-head">
              <span>Agent 复盘</span>
              <el-button :icon="ChatLineRound" circle class="icon-ghost" @click="askAgent" />
            </div>
            <div class="agent-log">
              <div v-for="item in agentMessages" :key="item.id" :class="['bubble', item.role]">
                {{ item.text }}
              </div>
            </div>
            <div class="agent-input">
              <el-input v-model="agentQuestion" placeholder="AG 最近比赛表现怎么样" @keyup.enter="askAgent" />
              <el-button :icon="Position" :loading="loading.agent" class="primary-action" @click="askAgent">发送</el-button>
            </div>
          </div>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import {
  Aim,
  ChatLineRound,
  DataAnalysis,
  Download,
  Lightning,
  Position,
  Refresh,
  RefreshRight,
  Search,
  TrendCharts
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const leagues = ref([])
const jobs = ref([])
const selectedLeagueId = ref('')
const system = ref({})
const queryMode = ref('ranking')
const queryKeyword = ref('')
const heroSort = ref('pick')
const playerSort = ref('kda')
const queryResult = ref(null)
const teamChartRef = ref(null)
const chart = ref(null)
const agentQuestion = ref('AG 最近比赛表现怎么样')
const agentMessages = ref([
  { id: 1, role: 'assistant', text: '数据链路已待命。' }
])

const loading = ref({
  sync: false,
  query: false,
  agent: false
})

watch(queryMode, () => {
  queryResult.value = null
  queryKeyword.value = ''
  if (queryMode.value === 'ranking') {
    loadTeamRanking()
  } else if (queryMode.value === 'honors') {
    loadHonors()
  } else if (queryMode.value === 'playerTop' || queryMode.value === 'heroTop') {
    runQuery()
  }
})

const queryModes = [
  { label: '战队榜', value: 'ranking' },
  { label: '选手榜', value: 'playerTop' },
  { label: '英雄榜', value: 'heroTop' },
  { label: '荣誉榜', value: 'honors' },
  { label: '比赛', value: 'match' }
]

const currentLeague = computed(() => leagues.value.find((item) => item.leagueId === selectedLeagueId.value))
const modeLabel = computed(() => queryModes.find((item) => item.value === queryMode.value)?.label || '-')
const placeholder = computed(() => {
  const map = {
    ranking: '搜索战队，例如 AG（留空显示榜单）',
    playerTop: '搜索选手，例如 一诺（留空显示榜单）',
    heroTop: '搜索英雄，例如 公孙离（留空显示榜单）',
    match: '输入战队名，例如 狼队',
    honors: '跨赛事荣誉总榜（无需输入）'
  }
  return map[queryMode.value] || '输入关键词'
})

const autoLoadModes = ['ranking', 'playerTop', 'heroTop', 'honors']

const rawData = computed(() => {
  const data = queryResult.value?.data
  if (Array.isArray(data)) return data
  return Array.isArray(queryResult.value) ? queryResult.value : []
})

const tableRows = computed(() => rawData.value.map(normalizeRow))
const resultCount = computed(() => queryResult.value?.count ?? tableRows.value.length)
const tableColumns = computed(() => {
  const columns = {
    ranking: [
      ['teamName', '战队'],
      ['battleCount', '局数'],
      ['winRate', '胜率'],
      ['avgKill', '场均击杀'],
      ['avgKda', 'KDA']
    ],
    playerTop: [
      ['playerName', '选手'],
      ['teamName', '战队'],
      ['positionDesc', '位置'],
      ['battleCount', '局数'],
      ['winRate', '胜率'],
      ['avgKda', 'KDA']
    ],
    heroTop: [
      ['heroName', '英雄'],
      ['battleCount', '出场'],
      ['pickRate', 'Pick'],
      ['banRate', 'Ban'],
      ['winRate', '胜率']
    ],
    match: [
      ['camp1TeamName', '蓝方'],
      ['camp1Score', '比分'],
      ['camp2TeamName', '红方'],
      ['camp2Score', '比分'],
      ['matchStageDesc', '赛段'],
      ['startTime', '时间']
    ],
    honors: [
      ['teamName', '战队'],
      ['champion', '冠军'],
      ['runnerUp', '亚军'],
      ['total', '总计']
    ]
  }
  return (columns[queryMode.value] || columns.ranking).map(([prop, label]) => ({ prop, label }))
})

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const body = await response.json()
  if (body.success === false) throw new Error(body.message || '请求失败')
  return body.data ?? body
}

async function refreshAll() {
  await Promise.allSettled([loadStatus(), loadLeagues(), loadJobs()])
  await loadDashboard()
}

async function loadStatus() {
  system.value = await request('/api/system/status')
}

async function loadLeagues() {
  leagues.value = await request('/api/leagues?limit=30')
  if (!selectedLeagueId.value && leagues.value.length) {
    selectedLeagueId.value = leagues.value[0].leagueId
  }
}

async function loadJobs() {
  jobs.value = await request('/api/sync/jobs?limit=6')
}

async function loadDashboard() {
  if (selectedLeagueId.value) {
    await runQuery()
  }
}

async function loadTeamRanking() {
  queryMode.value = 'ranking'
  queryResult.value = await request(`/api/query/team/ranking${leagueParam()}`)
  renderTeamChart()
}

async function loadHonors() {
  queryResult.value = await request('/api/query/team/honors')
}

async function runSync(path) {
  loading.value.sync = true
  try {
    const data = await request(path, { method: 'POST' })
    ElMessage.success(data.result || '同步完成')
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value.sync = false
  }
}

async function runQuery() {
  loading.value.query = true
  try {
    const keyword = encodeURIComponent(queryKeyword.value.trim())
    const hasKeyword = !!queryKeyword.value.trim()
    let url
    if (queryMode.value === 'ranking') {
      url = hasKeyword
        ? `/api/query/team?name=${keyword}${leagueJoin()}`
        : `/api/query/team/ranking${leagueParam()}`
    } else if (queryMode.value === 'playerTop') {
      url = hasKeyword
        ? `/api/query/player?name=${keyword}${leagueJoin()}`
        : `/api/query/player/top?sort=${playerSort.value}${leagueJoin()}`
    } else if (queryMode.value === 'heroTop') {
      url = hasKeyword
        ? `/api/query/hero?name=${keyword}${leagueJoin()}`
        : `/api/query/hero/top?sort=${heroSort.value}${leagueJoin()}`
    } else if (queryMode.value === 'match') {
      if (!hasKeyword) {
        ElMessage.warning('请输入战队名')
        return
      }
      url = `/api/query/match/recent?team=${keyword}${leagueJoin()}`
    } else if (queryMode.value === 'honors') {
      await loadHonors()
      return
    }
    queryResult.value = await request(url)
    if (queryMode.value === 'ranking' && !hasKeyword) renderTeamChart()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value.query = false
  }
}

async function askAgent() {
  const text = agentQuestion.value.trim()
  if (!text) return
  loading.value.agent = true
  const id = Date.now()
  agentMessages.value.push({ id, role: 'user', text })
  try {
    const data = await request('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({ message: text })
    })
    agentMessages.value.push({ id: id + 1, role: 'assistant', text: data.reply || '没有返回内容' })
    agentQuestion.value = ''
  } catch (error) {
    agentMessages.value.push({ id: id + 1, role: 'assistant', text: error.message })
  } finally {
    loading.value.agent = false
  }
}

function leagueParam() {
  return selectedLeagueId.value ? `?leagueId=${selectedLeagueId.value}` : ''
}

function leagueJoin() {
  return selectedLeagueId.value ? `&leagueId=${selectedLeagueId.value}` : ''
}

function normalizeRow(row) {
  const clone = { ...row }
  for (const key of ['winRate', 'pickRate', 'banRate', 'avgKda', 'avgKill']) {
    if (typeof clone[key] === 'number') clone[key] = Number(clone[key]).toFixed(2)
  }
  if (clone.startTime) clone.startTime = formatTime(clone.startTime)
  return clone
}

function renderTeamChart() {
  nextTick(() => {
    if (!teamChartRef.value) return
    if (!chart.value) chart.value = echarts.init(teamChartRef.value)
    const rows = rawData.value.slice(0, 8)
    chart.value.setOption({
      backgroundColor: 'transparent',
      grid: { left: 28, right: 12, top: 18, bottom: 36 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: rows.map((item) => item.teamName || item.keyword),
        axisLabel: { color: '#9aa7b4', interval: 0, rotate: 24 },
        axisLine: { lineStyle: { color: '#2d3a45' } }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#9aa7b4' },
        splitLine: { lineStyle: { color: 'rgba(116, 139, 158, .14)' } }
      },
      series: [
        {
          type: 'bar',
          data: rows.map((item) => Math.round((item.winRate || 0) * 1000) / 10),
          barWidth: 18,
          itemStyle: {
            borderRadius: [3, 3, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#18e0c2' },
              { offset: 0.55, color: '#d6ae3a' },
              { offset: 1, color: '#bc3c2f' }
            ])
          }
        }
      ]
    })
  })
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  await refreshAll()
})
</script>
