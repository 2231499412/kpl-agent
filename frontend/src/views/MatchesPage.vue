<template>
  <main class="app-shell has-sidebar matches-console" :class="`theme-${theme}`">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>赛程中心</h1>
        </div>
      </div>
      <div class="status-line">
        <el-select v-model="selectedLeagueId" class="league-select" placeholder="选择赛事" @change="loadMatches">
          <el-option v-for="l in leagues" :key="l.leagueId" :label="l.leagueName" :value="l.leagueId" />
        </el-select>
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <section class="match-content">
      <div v-if="loading" class="loading-hint">
        <div class="spinner"></div>
        <span>加载赛程数据...</span>
      </div>

      <template v-else-if="groupedMatches.length">
        <div v-for="group in groupedMatches" :key="group.stage" class="stage-group">
          <div class="stage-header">
            <span class="stage-tag" :class="stageClass(group.stage)">{{ group.stage }}</span>
            <span class="stage-count">{{ group.matches.length }} 场</span>
          </div>
          <div class="match-list">
            <div v-for="match in group.matches" :key="match.matchId" class="match-card" @click="openMatch(match)">
              <div class="match-time">
                <span class="time-date">{{ formatDate(match.startTime) }}</span>
                <span class="time-hour">{{ formatTime(match.startTime) }}</span>
              </div>
              <div class="match-teams">
                <div class="team-side" :class="{ winner: match.winCamp === 1 }">
                  <span class="team-name">{{ match.camp1TeamName || '蓝方' }}</span>
                  <span class="team-score">{{ match.camp1Score ?? '-' }}</span>
                </div>
                <span class="vs-divider">VS</span>
                <div class="team-side" :class="{ winner: match.winCamp === 2 }">
                  <span class="team-score">{{ match.camp2Score ?? '-' }}</span>
                  <span class="team-name">{{ match.camp2TeamName || '红方' }}</span>
                </div>
              </div>
              <div class="match-meta">
                <span v-if="match.bo" class="bo-tag">BO{{ match.bo }}</span>
                <span v-if="match.matchAddress" class="addr-tag">{{ match.matchAddress }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="empty-hint">
        <span>暂无赛程数据</span>
        <small>请选择赛事或等待数据同步</small>
      </div>
    </section>

    <!-- 对局详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="800" class="detail-dialog" destroy-on-close>
      <template v-if="detailLoading">
        <div class="skeleton-wrap">
          <div class="skeleton-row" style="width:40%"></div>
          <div class="skeleton-row" style="width:100%;height:100px;margin-top:16px"></div>
          <div class="skeleton-row" style="width:100%;height:100px;margin-top:12px"></div>
        </div>
      </template>
      <template v-else-if="detailData">
        <div class="battle-list">
          <div v-for="(battle, bi) in detailData.battles" :key="bi" class="battle-card">
            <div class="battle-head">
              <span class="battle-num">第{{ bi + 1 }} 局</span>
              <span class="battle-winner" :class="battle.battle?.winCamp === 1 ? 'camp-blue' : 'camp-red'">
                {{ battle.battle?.winCamp === 1 ? currentMatch?.camp1TeamName : currentMatch?.camp2TeamName }} 获胜
              </span>
            </div>
            <div class="player-grid">
              <div v-for="pd in sortedPlayers(battle.players)" :key="pd.player?.playerName"
                :class="['player-card', pd.player?.camp === 1 ? 'camp-blue' : 'camp-red']">
                <div class="player-header">
                  <img :src="'https://res.edata.qq.com/sgame/static/images/hero/' + pd.player?.heroId + '.jpg'" class="hero-img" />
                  <div>
                    <b>{{ pd.player?.playerName }}</b>
                    <small>{{ pd.player?.heroName }} · {{ pd.player?.positionDesc || '' }}</small>
                  </div>
                </div>
                <div class="player-stats">
                  <span>{{ pd.player?.killNum }}/{{ pd.player?.deathNum }}/{{ pd.player?.assistNum }}</span>
                  <span>KDA {{ pd.player?.kda }}</span>
                  <span v-if="pd.player?.isMvp" class="mvp-tag">MVP</span>
                  <span v-if="pd.player?.isLoseMvp" class="mvp-tag lose">败方MVP</span>
                </div>
                <div class="equip-list">
                  <span v-for="eq in pd.equips" :key="eq.equipId" class="equip-chip" :title="eq.equipName">
                    <img v-if="eq.equipIcon" :src="eq.equipIcon" class="equip-mini-icon" />
                    {{ eq.equipName }}
                  </span>
                </div>
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

const theme = ref(localStorage.getItem('kpl-theme') || 'light')
watch(theme, (v) => localStorage.setItem('kpl-theme', v))

const leagues = ref([])
const selectedLeagueId = ref('')
const matches = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const currentMatch = ref(null)

const detailTitle = computed(() => {
  if (!currentMatch.value) return ''
  const m = currentMatch.value
  return `${m.camp1TeamName || '蓝方'} ${m.camp1Score ?? '-'} : ${m.camp2Score ?? '-'} ${m.camp2TeamName || '红方'}`
})

const STAGE_ORDER = { js: 1, bjs: 2, bf: 3, dbtts: 4, rgjs2: 5, rgjs1: 6, cgs2: 7, cgs1: 8 }

const groupedMatches = computed(() => {
  const groups = {}
  for (const m of matches.value) {
    const stage = m.matchStageDesc || '其他'
    if (!groups[stage]) groups[stage] = { stage, order: STAGE_ORDER[m.matchStage] ?? 99, matches: [] }
    groups[stage].matches.push(m)
  }
  return Object.values(groups).sort((a, b) => a.order - b.order)
})

async function init() {
  try {
    const res = await fetch('/api/leagues?limit=50')
    const data = await res.json()
    leagues.value = data?.data || []
    if (leagues.value.length) {
      selectedLeagueId.value = leagues.value[0].leagueId
      await loadMatches()
    }
  } catch { /* ignore */ }
}

async function loadMatches() {
  if (!selectedLeagueId.value) return
  loading.value = true
  try {
    const res = await fetch(`/api/query/match/schedule?leagueId=${selectedLeagueId.value}`)
    const data = await res.json()
    matches.value = data?.data?.data || []
  } catch { matches.value = [] }
  loading.value = false
}

async function openMatch(match) {
  currentMatch.value = match
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const res = await fetch(`/api/query/match/battle?matchId=${match.matchId}`)
    const data = await res.json()
    detailData.value = data?.data || null
  } catch { detailData.value = null }
  detailLoading.value = false
}

function sortedPlayers(players) {
  if (!players) return []
  const order = { 6: 1, 4: 2, 3: 3, 1: 4, 2: 5, 5: 6 }
  return [...players].sort((a, b) => (order[a.player?.position] ?? 9) - (order[b.player?.position] ?? 9))
}

function formatDate(v) {
  if (!v) return ''
  return String(v).slice(5, 10).replace('-', '/')
}

function formatTime(v) {
  if (!v) return ''
  return String(v).slice(11, 16)
}

function stageClass(stage) {
  if (stage.includes('决赛')) return 'stage-final'
  if (stage.includes('半决赛') || stage.includes('淘汰')) return 'stage-playoff'
  return 'stage-regular'
}

onMounted(init)
</script>

<style scoped>
.matches-console {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.12);
  --mono-line-strong: rgba(0, 0, 0, 0.22);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --mono-accent: #1a1a1a;
  --card-bg: #fff;
  --card-hover: rgba(0, 0, 0, 0.02);
  --stat-bg: rgba(0, 0, 0, 0.03);
  --input-bg: rgba(255, 255, 255, 0.7);
  --corner-border: rgba(0, 0, 0, 0.12);
  --winner-color: #1a1a1a;
  --dialog-bg: #fff;
  max-width: none;
  min-height: 100vh;
  padding: 28px 32px 36px 87px;
  color: var(--mono-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
}

.matches-console.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.12);
  --mono-line-strong: rgba(255, 255, 255, 0.25);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --mono-accent: #e8e8e8;
  --card-bg: rgba(255, 255, 255, 0.06);
  --card-hover: rgba(255, 255, 255, 0.03);
  --stat-bg: rgba(255, 255, 255, 0.04);
  --input-bg: rgba(255, 255, 255, 0.06);
  --corner-border: rgba(255, 255, 255, 0.18);
  --winner-color: #e8e8e8;
  --dialog-bg: #1a1a1a;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

/* ── command strip ── */
.command-strip {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border: 1px solid var(--mono-line);
  background: var(--mono-panel);
}
.command-strip::before {
  content: "";
  position: absolute;
  left: -1px; top: -1px;
  width: 36px; height: 36px;
  border-left: 2px solid var(--corner-border);
  border-top: 2px solid var(--corner-border);
  pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px;
  display: grid; place-items: center;
  border: 1px solid var(--mono-line-strong);
  color: var(--mono-ink);
  background: var(--stat-bg);
  font-size: 20px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--mono-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--mono-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }
.league-select { width: 220px; }

/* ── theme toggle ── */
.theme-toggle {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--mono-soft); font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--mono-ink); }
.toggle-track {
  position: relative; width: 32px; height: 16px; border-radius: 8px;
  background: var(--mono-line-strong); transition: background 0.2s;
}
.toggle-track.on { background: var(--mono-ink); }
.toggle-thumb {
  position: absolute; top: 2px; left: 2px;
  width: 12px; height: 12px; border-radius: 50%;
  background: var(--mono-bg); transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }

/* ── content ── */
.match-content {
  margin-top: 18px;
}

.loading-hint, .empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 0;
  color: var(--mono-dim);
  font-size: 14px;
}
.empty-hint small { font-size: 12px; color: var(--mono-dim); }
.spinner {
  width: 24px; height: 24px;
  border: 2px solid var(--mono-line);
  border-top-color: var(--mono-ink);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── stage groups ── */
.stage-group { margin-bottom: 28px; }
.stage-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--mono-line);
}
.stage-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
}
.stage-final { color: #1a1a1a; background: rgba(0,0,0,0.06); }
.stage-playoff { color: #1a1a1a; background: rgba(0,0,0,0.04); }
.stage-regular { color: var(--mono-soft); background: var(--stat-bg); }
.theme-dark .stage-final { color: #e8e8e8; background: rgba(255,255,255,0.08); }
.theme-dark .stage-playoff { color: #e8e8e8; background: rgba(255,255,255,0.05); }
.stage-count { color: var(--mono-dim); font-size: 12px; }

/* ── match cards ── */
.match-list { display: flex; flex-direction: column; gap: 8px; }
.match-card {
  display: grid;
  grid-template-columns: 100px 1fr 100px;
  align-items: center;
  gap: 18px;
  padding: 14px 18px;
  border: 1px solid var(--mono-line);
  background: var(--card-bg);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.match-card:hover {
  background: var(--card-hover);
  border-color: var(--mono-line-strong);
}

.match-time { display: flex; flex-direction: column; }
.time-date { font-size: 13px; font-weight: 700; color: var(--mono-ink); }
.time-hour { font-size: 12px; color: var(--mono-dim); margin-top: 2px; }

.match-teams {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}
.team-side {
  display: flex;
  align-items: center;
  gap: 10px;
}
.team-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--mono-soft);
  transition: color 0.15s;
}
.team-side.winner .team-name {
  color: var(--winner-color);
  font-weight: 800;
}
.team-score {
  font-size: 20px;
  font-weight: 900;
  color: var(--mono-dim);
  min-width: 24px;
  text-align: center;
}
.team-side.winner .team-score {
  color: var(--mono-ink);
}
.vs-divider {
  color: var(--mono-dim);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 2px;
}

.match-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.bo-tag, .addr-tag {
  font-size: 11px;
  color: var(--mono-dim);
}

/* ── detail dialog ── */
.battle-list { display: flex; flex-direction: column; gap: 16px; }
.battle-card {
  border: 1px solid var(--mono-line);
  background: var(--stat-bg);
  padding: 14px;
}
.battle-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--mono-line);
}
.battle-num { font-weight: 800; font-size: 14px; color: var(--mono-ink); }
.battle-winner { font-size: 13px; font-weight: 700; }
.battle-winner.camp-blue { color: var(--mono-ink); }
.battle-winner.camp-red { color: var(--mono-dim); }

.player-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px;
}
.player-card {
  padding: 10px 12px;
  border: 1px solid var(--mono-line);
  background: var(--card-bg);
}
.player-card.camp-blue { border-left: 3px solid var(--mono-ink); }
.player-card.camp-red { border-left: 3px solid var(--mono-dim); }

.player-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.hero-img { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.player-header b { font-size: 13px; color: var(--mono-ink); }
.player-header small { display: block; font-size: 11px; color: var(--mono-dim); }

.player-stats {
  display: flex; gap: 8px; font-size: 12px; color: var(--mono-ink); margin-bottom: 4px;
}
.mvp-tag { font-size: 11px; font-weight: 700; color: #b8860b; }
.mvp-tag.lose { color: var(--mono-dim); }

.equip-list { display: flex; flex-wrap: wrap; gap: 3px; }
.equip-chip {
  display: inline-flex; align-items: center; gap: 2px;
  padding: 1px 5px; font-size: 10px;
  background: var(--stat-bg); border: 1px solid var(--mono-line);
  color: var(--mono-soft);
}
.equip-mini-icon { width: 14px; height: 14px; border-radius: 2px; object-fit: cover; }

.skeleton-wrap { padding: 8px 0; }
.skeleton-row {
  height: 16px; border-radius: 6px;
  background: linear-gradient(90deg, var(--stat-bg) 25%, var(--card-hover) 50%, var(--stat-bg) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* ── Element Plus overrides ── */
:deep(.el-dialog) {
  border: 1px solid var(--mono-line-strong) !important;
  border-radius: 0 !important;
  background: var(--dialog-bg) !important;
}
:deep(.el-dialog__title) { color: var(--mono-ink) !important; font-weight: 900 !important; }
:deep(.el-dialog__header) { border-bottom: 1px solid var(--mono-line); padding-bottom: 12px; margin: 0; }
:deep(.el-dialog__body) { height: 60vh; min-height: 300px; overflow-y: auto; padding: 16px 20px; }
:deep(.el-input__wrapper), :deep(.el-select__wrapper) {
  border-radius: 0 !important;
  background: var(--input-bg) !important;
  box-shadow: 0 0 0 1px var(--mono-line) inset !important;
}
:deep(.el-button) { border-radius: 0; }
</style>
