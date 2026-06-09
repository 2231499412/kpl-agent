<template>
  <main class="bp-broadcast">
    <div class="aurora aurora-left" />
    <div class="aurora aurora-right" />

    <header class="match-header">
      <section class="team-head team-blue">
        <div class="ban-strip" v-if="!isBlindPick">
          <button
            v-for="(hero, i) in currentGameObj.blueBans"
            :key="`blue-ban-${i}`"
            :class="['ban-card', { active: isActiveBan('blue', i), filled: hero }]"
            @click="onSlotClick('ban', 'blue', i, hero)"
          >
            <img v-if="hero" :src="getHeroImg(hero)" :alt="hero.heroName">
            <span v-else>禁</span>
          </button>
        </div>
        <div class="team-identity">
          <div class="team-logo"><img :src="blueSideTeamObj.logo" :alt="blueSideTeam"></div>
          <div>
            <small>蓝方</small>
            <strong>{{ blueSideTeamObj.name }}</strong>
          </div>
        </div>
      </section>

      <section class="score-head">
        <small>2026 KPL · BO{{ boFormat }}</small>
        <div class="score-line">
          <strong>{{ teamWins(blueSideTeam) }}</strong>
          <span>-</span>
          <strong>{{ teamWins(redSideTeam) }}</strong>
        </div>
        <em>第 {{ currentGame + 1 }} 局</em>
      </section>

      <section class="team-head team-red">
        <div class="team-identity">
          <div>
            <small>红方</small>
            <strong>{{ redSideTeamObj.name }}</strong>
          </div>
          <div class="team-logo team-logo-red"><img :src="redSideTeamObj.logo" :alt="redSideTeam"></div>
        </div>
        <div class="ban-strip" v-if="!isBlindPick">
          <button
            v-for="(hero, i) in currentGameObj.redBans"
            :key="`red-ban-${i}`"
            :class="['ban-card', { active: isActiveBan('red', i), filled: hero }]"
            @click="onSlotClick('ban', 'red', i, hero)"
          >
            <img v-if="hero" :src="getHeroImg(hero)" :alt="hero.heroName">
            <span v-else>禁</span>
          </button>
        </div>
      </section>
    </header>

    <section class="draft-stage">
      <div class="pick-team blue-picks">
        <button
          v-for="(hero, i) in currentGameObj.bluePicks"
          :key="`blue-pick-${i}`"
          :class="['pick-card', { active: isActivePick('blue', i), filled: hero }]"
          @click="onSlotClick('pick', 'blue', i, hero)"
        >
          <img
            v-if="hero"
            :src="getHeroPoster(hero)"
            :alt="hero.heroName"
            :style="getHeroPosterStyle(hero)"
            @error="onPosterError($event, hero)"
          >
          <div v-else class="pick-placeholder">
            <span>0{{ i + 1 }}</span>
            <b>{{ isActivePick('blue', i) ? '选择英雄' : '待选' }}</b>
          </div>
          <footer>
            <span>{{ hero?._role || roleLabels[i] }}</span>
            <strong>{{ hero?.heroName || bluePlayers[i] }}</strong>
          </footer>
        </button>
      </div>

      <div class="center-console">
        <div class="phase-kicker">{{ isBlindPick ? '巅峰对决 · ' : '' }}{{ bpDone ? 'BP 完成' : currentStepObj.type === 'ban' ? '禁用阶段' : '选择阶段' }}</div>
        <div class="turn-side" :class="currentStepObj.side">
          {{ bpDone ? '阵容锁定' : currentStepObj.side === 'blue' ? '蓝方操作' : '红方操作' }}
        </div>
        <div class="timer">{{ bpDone ? 'OK' : timerEnabled ? timerLeft : currentActionNum }}</div>
        <div class="timer-track" v-if="timerEnabled">
          <span :class="currentStepObj.side" :style="{ width: `${(timerLeft / 30) * 100}%` }" />
        </div>
        <strong class="phase-name">{{ bpDone ? '本局 BP 已完成' : currentStepObj.label }}</strong>
        <p v-if="isBlindPick && !bpDone">无 Ban · 双方可选相同英雄</p>
        <p v-else>{{ bpDone ? '可标记胜负或切换下一局' : '点击高亮槽位选择英雄' }}</p>
        <div class="final-mark"><span />决赛<span /></div>
      </div>

      <div class="pick-team red-picks">
        <button
          v-for="(hero, i) in currentGameObj.redPicks"
          :key="`red-pick-${i}`"
          :class="['pick-card', { active: isActivePick('red', i), filled: hero }]"
          @click="onSlotClick('pick', 'red', i, hero)"
        >
          <img
            v-if="hero"
            :src="getHeroPoster(hero)"
            :alt="hero.heroName"
            :style="getHeroPosterStyle(hero)"
            @error="onPosterError($event, hero)"
          >
          <div v-else class="pick-placeholder">
            <span>0{{ i + 1 }}</span>
            <b>{{ isActivePick('red', i) ? '选择英雄' : '待选' }}</b>
          </div>
          <footer>
            <strong>{{ hero?.heroName || redPlayers[i] }}</strong>
            <span>{{ hero?._role || roleLabels[i] }}</span>
          </footer>
        </button>
      </div>
    </section>

    <section class="game-strip">
      <div class="bo-dropdown" @click.stop>
        <button class="bo-trigger" @click="boOpen = !boOpen">
          <span>BO{{ boFormat }}</span>
          <svg :class="['bo-arrow', { open: boOpen }]" viewBox="0 0 10 6" fill="none"><path d="M1 1l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <div class="bo-menu" v-if="boOpen">
          <button
            v-for="n in boOptions"
            :key="n"
            :class="{ active: boFormat === n }"
            @click="changeBo(n)"
          >BO{{ n }}</button>
        </div>
      </div>
      <nav class="game-nav" aria-label="局数切换">
        <button
          v-for="(_, i) in games"
          :key="i"
          :class="{ active: currentGame === i, done: isGameDone(i) }"
          @click="switchGame(i)"
        >
          <span>第 {{ i + 1 }} 局</span>
          <b>{{ getGameWinnerLabel(i) }}</b>
        </button>
      </nav>
      <div class="action-bar">
        <button @click="undo" :disabled="currentGameObj.currentStep === 0">撤销</button>
        <button @click="swapSides">交换阵营</button>
        <button :class="timerEnabled ? 'timer-on' : ''" @click="toggleTimer">{{ timerEnabled ? '关闭计时' : '开启计时' }}</button>
        <button class="blue-action" @click="markWin('blue')">{{ blueSideTeamObj.short }}胜</button>
        <button class="red-action" @click="markWin('red')">{{ redSideTeamObj.short }}胜</button>
        <button class="danger" @click="resetGame">重置本局</button>
      </div>
    </section>

    <section class="info-board">
      <article class="info-card hero-stats-card">
        <div class="info-title">
          <span>英雄数据</span>
          <b>{{ lastPickedHero ? lastPickedHero.heroName : '—' }}</b>
        </div>
        <template v-if="lastPickedHero">
          <div class="stats-hero-row">
            <img :src="getHeroImg(lastPickedHero)" class="stats-avatar" :alt="lastPickedHero.heroName">
            <div class="stats-hero-info">
              <strong>{{ lastPickedHero.heroName }}</strong>
              <div class="stats-roles">
                <i :class="['tier-tag', getTier(lastPickedHero)]">{{ getTier(lastPickedHero) }} {{ tierDefs.find(t => t.key === getTier(lastPickedHero))?.name }}</i>
                <span v-if="lastPickedHero._role" class="role-tag main">{{ lastPickedHero._role }}</span>
                <template v-if="lastPickedHero.positions && lastPickedHero.positions.length">
                  <span v-for="p in lastPickedHero.positions" :key="p.position" class="role-tag" :class="{ main: p.position === lastPickedHero._role }">
                    {{ p.position }} {{ p.rate != null ? pct(p.rate) + '%' : '' }}
                  </span>
                </template>
              </div>
            </div>
          </div>
          <div class="stats-grid">
            <div class="stat-item"><small>出场</small><b>{{ lastPickedHero.battleCount || 0 }}</b></div>
            <div class="stat-item"><small>胜率</small><b>{{ lastPickedHero.winRate != null ? pct(lastPickedHero.winRate) + '%' : '—' }}</b></div>
            <div class="stat-item"><small>Pick</small><b>{{ lastPickedHero.pickRate != null ? pct(lastPickedHero.pickRate) + '%' : '—' }}</b></div>
            <div class="stat-item"><small>Ban</small><b>{{ lastPickedHero.banRate != null ? pct(lastPickedHero.banRate) + '%' : '—' }}</b></div>
            <div class="stat-item"><small>KDA</small><b>{{ lastPickedHero.avgKda != null ? lastPickedHero.avgKda.toFixed(1) : '—' }}</b></div>
            <div class="stat-item"><small>评分</small><b>{{ heroScore(lastPickedHero) }}</b></div>
          </div>
        </template>
        <div v-else class="stats-empty">
          <svg viewBox="0 0 48 48" fill="none" class="empty-icon"><rect x="6" y="10" width="36" height="28" rx="3" stroke="currentColor" stroke-width="2"/><path d="M6 18h36" stroke="currentColor" stroke-width="2"/><circle cx="14" cy="28" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M22 26h14M22 30h10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
          <span>选择英雄后查看数据</span>
        </div>
      </article>

      <article class="info-card guide-card">
        <div class="info-title">
          <span>当前操作</span>
          <b>{{ bpDone ? '已完成' : currentStepObj.type === 'ban' ? '禁用英雄' : '选择英雄' }}</b>
        </div>
        <div class="guide-panel" :class="currentStepObj.side">
          <div class="guide-side">
            <small>{{ bpDone ? '已完成' : currentStepObj.side === 'blue' ? '蓝方' : '红方' }}</small>
            <strong>{{ bpDone ? '阵容锁定' : currentStepObj.label }}</strong>
          </div>
          <div class="guide-copy">
            <span>{{ bpDone ? '本局 BP 已完成，可以标记胜负或切换下一局。' : activeSlotText }}</span>
            <b>{{ remainingActionText }}</b>
          </div>
        </div>
      </article>

      <article class="info-card lineup-score-card">
        <div class="info-title">
          <span>阵容评分</span>
          <b>第 {{ currentGame + 1 }} 局</b>
        </div>
        <template v-if="blueTeamScore + redTeamScore > 0">
          <div class="score-compare">
            <div class="score-side blue">
              <small>蓝方</small>
              <strong>{{ blueTeamScore }}</strong>
            </div>
            <div class="score-bar-wrap">
              <div class="score-bar-track">
                <span class="score-bar-blue" :style="{ width: `${(blueTeamScore / (blueTeamScore + redTeamScore)) * 100}%` }" />
              </div>
            </div>
            <div class="score-side red">
              <small>红方</small>
              <strong>{{ redTeamScore }}</strong>
            </div>
          </div>
          <div class="score-hero-list">
            <div class="score-hero-col blue">
              <div v-for="(hero, i) in currentGameObj.bluePicks" :key="`bs-${i}`" class="score-hero-row">
                <template v-if="hero">
                  <img :src="getHeroImg(hero)" class="score-hero-avatar" :alt="hero.heroName">
                  <span>{{ hero.heroName }}</span>
                  <b>{{ heroScore(hero) }}</b>
                </template>
                <template v-else>
                  <div class="score-hero-empty" />
                  <span class="score-placeholder">待选</span>
                </template>
              </div>
            </div>
            <div class="score-hero-col red">
              <div v-for="(hero, i) in currentGameObj.redPicks" :key="`rs-${i}`" class="score-hero-row">
                <template v-if="hero">
                  <b>{{ heroScore(hero) }}</b>
                  <span>{{ hero.heroName }}</span>
                  <img :src="getHeroImg(hero)" class="score-hero-avatar" :alt="hero.heroName">
                </template>
                <template v-else>
                  <span class="score-placeholder">待选</span>
                  <div class="score-hero-empty" />
                </template>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="score-empty">
          <div class="score-vs">
            <span class="side-blue">蓝方</span>
            <span class="vs-q">? — ?</span>
            <span class="side-red">红方</span>
          </div>
          <div class="score-bar-track empty-track">
            <span class="score-bar-half" />
          </div>
          <span class="empty-hint">完成 BP 后查看评分</span>
        </div>
      </article>
    </section>

    <el-dialog
      v-model="pickerOpen"
      :title="pickerTitle"
      width="780px"
      destroy-on-close
      class="hero-picker-dialog"
    >
      <div class="picker-toolbar">
        <input ref="searchInputRef" v-model="pickerSearch" type="text" placeholder="搜索英雄">
        <div class="picker-filters">
          <button
            v-for="role in roleOptions"
            :key="role.value"
            :class="{ active: pickerRole === role.value }"
            @click="pickerRole = role.value"
          >{{ role.label }}</button>
          <span class="filter-sep" />
          <button
            v-for="tier in tierOptions"
            :key="tier.value"
            :class="['tier-btn', tier.value, { active: pickerTier === tier.value }]"
            :title="tier.name"
            @click="pickerTier = tier.value"
          >{{ tier.label }}</button>
        </div>
      </div>
      <div class="picker-tier-list">
        <div v-for="group in tierGroups" :key="group.key" class="tier-group">
          <div :class="['tier-header', group.key]">
            <span class="tier-label">{{ group.label }}</span>
            <span class="tier-name">{{ group.name }}</span>
            <span class="tier-desc">{{ group.desc }} · 评分 {{ group.minScore }}–{{ group.maxScore }}</span>
          </div>
          <div class="picker-grid">
            <button
              v-for="hero in group.heroes"
              :key="`${hero.heroId}-${hero.heroName}`"
              :disabled="isHeroUsed(hero)"
              @click="pickHero(hero)"
            >
              <img :src="getHeroImg(hero)" :alt="hero.heroName">
              <strong>{{ hero.heroName }}</strong>
              <span>{{ hero._role || '英雄' }}</span>
            </button>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="sideChooseOpen"
      :title="sideChooseLabel"
      width="420px"
      class="side-choose-dialog"
    >
      <p class="side-choose-desc">选择下一局的阵营，决定先 Ban/Pick 还是后 Ban/Pick</p>
      <div class="side-choose-options">
        <button class="side-option side-option-blue" @click="chooseSide('blueSide')">
          <div class="side-option-indicator blue" />
          <div>
            <strong>蓝方</strong>
            <small>先 Ban · 先 Pick</small>
          </div>
        </button>
        <button class="side-option side-option-red" @click="chooseSide('redSide')">
          <div class="side-option-indicator red" />
          <div>
            <strong>红方</strong>
            <small>后 Ban · 后 Pick</small>
          </div>
        </button>
      </div>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { HERO_CATALOG } from '../data/heroData'
import mixueLogo from '../assets/mixue-logo.png'
import luckinLogo from '../assets/luckin-logo.png'

const bluePlayers = ['摩天脆脆', '雪王', '蜜桃四季春', '大圣代', '薄荷奶绿']
const redPlayers = ['生椰拿铁', '冰块', '小黄油美式', '陨石拿铁', '苹果冰茶']
const roleLabels = ['对抗路', '打野', '中路', '发育路', '游走']
const roleOptions = [
  { label: '全部', value: 'all' },
  ...roleLabels.map(role => ({ label: role, value: role })),
]

const TEAMS = {
  mixue: { key: 'mixue', name: '蜜雪冰城队', short: '蜜雪冰城', logo: mixueLogo },
  luckin: { key: 'luckin', name: '瑞幸咖啡队', short: '瑞幸咖啡', logo: luckinLogo },
}
const blueSideTeam = ref('mixue')
const blueSideTeamObj = computed(() => TEAMS[blueSideTeam.value])
const redSideTeamObj = computed(() => TEAMS[blueSideTeam.value === 'mixue' ? 'luckin' : 'mixue'])
const redSideTeam = computed(() => redSideTeamObj.value.key)

function teamWins(teamKey) {
  return games.value.filter(g => g.winner === teamKey).length
}

function getGameWinnerLabel(i) {
  const w = games.value[i].winner
  if (!w) return '未开始'
  return TEAMS[w]?.short || '未知'
}

function getHeroImg(hero) {
  return hero?.heroIcon || (hero?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${hero.heroId}.jpg` : '')
}

function getHeroPoster(hero) {
  return hero?.heroId ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-mobileskin-1.jpg` : getHeroImg(hero)
}

function getHeroBigSkin(hero) {
  return hero?.heroId ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-bigskin-1.jpg` : getHeroImg(hero)
}

function onPosterError(event, hero) {
  const img = event.target
  if (img.dataset.fallback === 'bigskin') {
    img.dataset.fallback = 'avatar'
    img.src = getHeroImg(hero)
    return
  }
  img.dataset.fallback = 'bigskin'
  img.src = getHeroBigSkin(hero)
}

const rolePosterFocus = {
  '对抗路': { x: 50, y: 16, scale: 1.04 },
  '打野': { x: 50, y: 17, scale: 1.05 },
  '中路': { x: 50, y: 15, scale: 1.03 },
  '发育路': { x: 50, y: 14, scale: 1.03 },
  '游走': { x: 50, y: 17, scale: 1.05 },
}

// 原画是横版构图，竖版 BP 卡需要按英雄微调视觉焦点。
// x 越小画面越向左取景，y 越小越靠上，scale 越大裁切越近。
const heroPosterFocus = {
  105: { x: 48, y: 17, scale: 1.06 }, // 廉颇
  107: { x: 49, y: 15, scale: 1.05 }, // 赵云
  111: { x: 51, y: 13, scale: 1.03 }, // 孙尚香
  123: { x: 48, y: 16, scale: 1.05 }, // 吕布
  131: { x: 49, y: 13, scale: 1.03 }, // 李白
  132: { x: 50, y: 14, scale: 1.04 }, // 马可波罗
  140: { x: 48, y: 16, scale: 1.05 }, // 关羽
  141: { x: 50, y: 13, scale: 1.03 }, // 貂蝉
  146: { x: 51, y: 13, scale: 1.03 }, // 露娜
  150: { x: 49, y: 15, scale: 1.04 }, // 韩信
  157: { x: 50, y: 13, scale: 1.03 }, // 不知火舞
  190: { x: 49, y: 15, scale: 1.05 }, // 诸葛亮
  199: { x: 50, y: 12, scale: 1.02 }, // 公孙离
  502: { x: 49, y: 16, scale: 1.05 }, // 裴擒虎
  508: { x: 51, y: 13, scale: 1.03 }, // 伽罗
  523: { x: 50, y: 12, scale: 1.02 }, // 西施
  531: { x: 50, y: 14, scale: 1.03 }, // 镜
  542: { x: 50, y: 15, scale: 1.05 }, // 暃
}

function getHeroPosterStyle(hero) {
  const focus = heroPosterFocus[hero?.heroId] || rolePosterFocus[hero?._role] || { x: 50, y: 15, scale: 1.04 }
  return {
    '--poster-x': `${focus.x}%`,
    '--poster-y': `${focus.y}%`,
    '--poster-scale': focus.scale,
  }
}

function mergeHeroes(apiList) {
  const catalogMap = new Map()
  HERO_CATALOG.forEach(h => catalogMap.set(h.heroId, h.role))

  const seen = new Set()
  return [...apiList, ...HERO_CATALOG].filter(hero => {
    const key = hero.heroId || hero.heroName
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).map(hero => ({
    ...hero,
    _role: hero._role || hero.role || hero.mainPosition || hero.position || catalogMap.get(hero.heroId) || '',
  }))
}

const allHeroes = ref([])
const pickerOpen = ref(false)
const pickerSearch = ref('')
const pickerRole = ref('all')
const pickerTier = ref('all')
const pickerTarget = ref(null)
const searchInputRef = ref(null)

const tierDefs = [
  { key: 'T0', label: 'T0', name: '绝对核心', desc: 'BP率极高，版本答案' },
  { key: 'T0.5', label: 'T0.5', name: '最高优先', desc: 'BP率很高，优先锁定' },
  { key: 'T1', label: 'T1', name: '主力梯队', desc: '高BP率，主流选择' },
  { key: 'T2', label: 'T2', name: '常规出场', desc: '稳定出场，体系常用' },
  { key: 'T3', label: 'T3', name: '战术选择', desc: '针对性出场 / counter位' },
  { key: 'T4', label: 'T4', name: '冷门登场', desc: '偶尔亮相，特定阵容' },
]

const tierMap = computed(() => {
  const withData = allHeroes.value.filter(h => h.battleCount > 0 || h.pickRate > 0)
  const sorted = [...withData].sort((a, b) => heroScore(b) - heroScore(a))
  const n = sorted.length
  const breaks = [0.10, 0.25, 0.50, 0.75, 0.90].map(p => Math.ceil(n * p))
  const map = new Map()
  sorted.forEach((h, i) => {
    let tier
    if (i < breaks[0]) tier = 'T0'
    else if (i < breaks[1]) tier = 'T0.5'
    else if (i < breaks[2]) tier = 'T1'
    else if (i < breaks[3]) tier = 'T2'
    else if (i < breaks[4]) tier = 'T3'
    else tier = 'T4'
    map.set(h.heroId, tier)
  })
  return map
})

function getTier(hero) {
  return tierMap.value.get(hero?.heroId) || 'T4'
}

const tierOptions = [
  { label: '全部', value: 'all' },
  ...tierDefs.map(t => ({ label: t.label, value: t.key, name: t.name })),
]

const filteredHeroes = computed(() => {
  const query = pickerSearch.value.trim().toLowerCase()
  return allHeroes.value
    .filter(hero => {
      const matchesRole = pickerRole.value === 'all' || hero._role === pickerRole.value
      const matchesTier = pickerTier.value === 'all' || getTier(hero) === pickerTier.value
      const matchesSearch = !query || hero.heroName.toLowerCase().includes(query)
      return matchesRole && matchesTier && matchesSearch
    })
    .sort((a, b) => heroScore(b) - heroScore(a))
})

const tierGroups = computed(() => {
  const heroes = filteredHeroes.value
  const groups = []
  for (const def of tierDefs) {
    const list = heroes.filter(h => getTier(h) === def.key)
    if (!list.length) continue
    const scores = list.map(heroScore)
    groups.push({
      ...def,
      heroes: list,
      minScore: Math.min(...scores),
      maxScore: Math.max(...scores),
    })
  }
  return groups
})

const pickerTitle = computed(() => {
  if (!pickerTarget.value) return '选择英雄'
  const side = pickerTarget.value.side === 'blue' ? '蓝方' : '红方'
  const type = pickerTarget.value.type === 'ban' ? '禁用' : '选择'
  return `${side} · ${type}英雄`
})

function createBpSequence(firstPickSide = 'blue') {
  const fp = firstPickSide       // 先 pick 方
  const sp = fp === 'blue' ? 'red' : 'blue'
  const fpL = fp === 'blue' ? '蓝方' : '红方'
  const spL = sp === 'blue' ? '蓝方' : '红方'
  return [
    { type: 'ban', side: fp, label: `${fpL} Ban 1` },
    { type: 'ban', side: sp, label: `${spL} Ban 1` },
    { type: 'ban', side: fp, label: `${fpL} Ban 2` },
    { type: 'ban', side: sp, label: `${spL} Ban 2` },
    { type: 'pick', side: fp, label: `${fpL} Pick 1` },
    { type: 'pick', side: sp, label: `${spL} Pick 1` },
    { type: 'pick', side: sp, label: `${spL} Pick 2` },
    { type: 'pick', side: fp, label: `${fpL} Pick 2` },
    { type: 'pick', side: fp, label: `${fpL} Pick 3` },
    { type: 'pick', side: sp, label: `${spL} Pick 3` },
    { type: 'ban', side: sp, label: `${spL} Ban 3` },
    { type: 'ban', side: fp, label: `${fpL} Ban 3` },
    { type: 'ban', side: sp, label: `${spL} Ban 4` },
    { type: 'ban', side: fp, label: `${fpL} Ban 4` },
    { type: 'pick', side: sp, label: `${spL} Pick 4` },
    { type: 'pick', side: fp, label: `${fpL} Pick 4` },
    { type: 'pick', side: sp, label: `${spL} Pick 5` },
    { type: 'pick', side: fp, label: `${fpL} Pick 5` },
  ]
}

const blindPickSequence = [
  { type: 'pick', side: 'blue', label: '蓝方 Pick 1' },
  { type: 'pick', side: 'red', label: '红方 Pick 1' },
  { type: 'pick', side: 'blue', label: '蓝方 Pick 2' },
  { type: 'pick', side: 'red', label: '红方 Pick 2' },
  { type: 'pick', side: 'blue', label: '蓝方 Pick 3' },
  { type: 'pick', side: 'red', label: '红方 Pick 3' },
  { type: 'pick', side: 'blue', label: '蓝方 Pick 4' },
  { type: 'pick', side: 'red', label: '红方 Pick 4' },
  { type: 'pick', side: 'blue', label: '蓝方 Pick 5' },
  { type: 'pick', side: 'red', label: '红方 Pick 5' },
]

function createGameState(blindPick = false, firstPickSide = 'blue') {
  return {
    blueBans: blindPick ? [] : [null, null, null, null],
    redBans: blindPick ? [] : [null, null, null, null],
    bluePicks: [null, null, null, null, null],
    redPicks: [null, null, null, null, null],
    currentStep: 0,
    blueIsFirst: firstPickSide === 'blue',
    firstPickSide,
    blueSideTeam: 'mixue',
    winner: null,
    blindPick,
  }
}

const boOptions = [3, 5, 7, 9]
const boFormat = ref(5)
const boOpen = ref(false)
const games = ref(Array.from({ length: 5 }, () => createGameState()))
const currentGame = ref(0)
const currentGameObj = computed(() => games.value[currentGame.value])
const isBlindPick = computed(() => (boFormat.value === 7 && currentGame.value === 6) || (boFormat.value === 9 && currentGame.value === 8))
const activeSequence = computed(() => isBlindPick.value ? blindPickSequence : createBpSequence(currentGameObj.value.firstPickSide))

function changeBo(n) {
  stopTimer()
  boFormat.value = n
  boOpen.value = false
  blueSideTeam.value = 'mixue'
  games.value = Array.from({ length: n }, (_, i) => {
    const isLast = (n === 7 && i === 6) || (n === 9 && i === 8)
    return createGameState(isLast)
  })
  currentGame.value = 0
  lastPickedHero.value = null
  startTimer()
}
const currentStepObj = computed(() => activeSequence.value[currentGameObj.value.currentStep] || { type: 'done', side: '', label: '完成' })
const currentActionNum = computed(() => {
  if (bpDone.value) return ''
  const seq = activeSequence.value
  const step = currentGameObj.value.currentStep
  const cur = seq[step]
  if (!cur) return ''
  let count = 0
  for (let i = 0; i <= step; i++) {
    if (seq[i].type === cur.type && seq[i].side === cur.side) count++
  }
  return count
})
const bpDone = computed(() => currentGameObj.value.currentStep >= activeSequence.value.length)
const activeSlotText = computed(() => {
  if (bpDone.value) return ''
  const side = currentStepObj.value.side === 'blue' ? '左侧蓝方' : '右侧红方'
  const action = currentStepObj.value.type === 'ban' ? '顶部高亮禁用位' : '阵容区高亮 Pick 位'
  return `请点击${side}${action}，打开英雄选择器。`
})
const remainingActionText = computed(() => {
  if (bpDone.value) return '本局操作结束'
  const game = currentGameObj.value
  const pickLeft = game.bluePicks.concat(game.redPicks).filter(item => !item).length
  if (isBlindPick.value) return `剩余 ${pickLeft} 个选择位`
  const banLeft = game.blueBans.concat(game.redBans).filter(item => !item).length
  return `剩余 ${banLeft} 个禁用位 · ${pickLeft} 个选择位`
})

function pct(v) { return (Number(v) * 100).toFixed(1) }
function heroScore(hero) {
  if (!hero) return 0
  const bp = (hero.pickRate || 0) + (hero.banRate || 0)
  return Math.round(bp * 50 + (hero.banRate || 0) * 30 + (hero.winRate || 0) * 20)
}
const lastPickedHero = ref(null)
const blueTeamScore = computed(() => currentGameObj.value.bluePicks.reduce((s, h) => s + heroScore(h), 0))
const redTeamScore = computed(() => currentGameObj.value.redPicks.reduce((s, h) => s + heroScore(h), 0))

const timerLeft = ref(30)
const timerEnabled = ref(false)
let timerInterval

function stopTimer() {
  clearInterval(timerInterval)
  timerInterval = undefined
}

function startTimer() {
  stopTimer()
  timerLeft.value = 30
  if (!timerEnabled.value || bpDone.value) return
  timerInterval = setInterval(() => {
    timerLeft.value -= 1
    if (timerLeft.value <= 0) advanceStep()
  }, 1000)
}

function toggleTimer() {
  timerEnabled.value = !timerEnabled.value
  if (timerEnabled.value) {
    startTimer()
  } else {
    stopTimer()
  }
}

function getSlotInfo(step) {
  const sequence = activeSequence.value[step]
  if (!sequence) return null
  const key = `${sequence.side}${sequence.type === 'ban' ? 'Bans' : 'Picks'}`
  const arr = currentGameObj.value[key]
  // 红方 pick 从右往左填充，与蓝方对称
  const idx = (sequence.type === 'pick' && sequence.side === 'red')
    ? arr.lastIndexOf(null)
    : arr.indexOf(null)
  return { ...sequence, arr, idx }
}

function isActiveBan(side, idx) {
  const info = getSlotInfo(currentGameObj.value.currentStep)
  return info?.type === 'ban' && info.side === side && info.idx === idx
}

function isActivePick(side, idx) {
  const info = getSlotInfo(currentGameObj.value.currentStep)
  return info?.type === 'pick' && info.side === side && info.idx === idx
}

function openPicker(type, side, idx) {
  pickerTarget.value = { type, side, idx }
  pickerSearch.value = ''
  pickerRole.value = 'all'
  pickerTier.value = 'all'
  pickerOpen.value = true
  nextTick(() => searchInputRef.value?.focus())
}

function onSlotClick(type, side, idx, hero) {
  const key = `${side}${type === 'ban' ? 'Bans' : 'Picks'}`
  if (hero) {
    // 只允许移除最近一步放置的英雄，并回退步骤
    const game = currentGameObj.value
    const prevStep = game.currentStep - 1
    const seq = activeSequence.value
    const prevSeq = seq[prevStep]
    if (prevSeq && prevSeq.type === type && prevSeq.side === side) {
      // 计算上一步对应的槽位索引
      const stepsOfSame = seq.filter(s => s.type === type && s.side === side)
      const expectedIdx = stepsOfSame.indexOf(prevSeq)
      if (expectedIdx === idx) {
        game[key][idx] = null
        game.currentStep = prevStep
        startTimer()
      }
    }
    return
  }
  if ((type === 'ban' && isActiveBan(side, idx)) || (type === 'pick' && isActivePick(side, idx))) {
    openPicker(type, side, idx)
  }
}

function isHeroUsed(hero) {
  const game = currentGameObj.value
  if (isBlindPick.value) {
    // 巅峰对决：双方可选相同英雄，只检查当前操作方
    const side = pickerTarget.value?.side
    if (!side) return false
    const picks = side === 'blue' ? game.bluePicks : game.redPicks
    return picks.some(item => item?.heroName === hero.heroName)
  }
  return [...game.blueBans, ...game.redBans, ...game.bluePicks, ...game.redPicks]
    .some(item => item?.heroName === hero.heroName)
}

function pickHero(hero) {
  if (!pickerTarget.value || isHeroUsed(hero)) return
  const { type, side, idx } = pickerTarget.value
  const key = `${side}${type === 'ban' ? 'Bans' : 'Picks'}`
  const merged = { ...hero, _role: hero._role || hero.role || hero.mainPosition || hero.position || HERO_CATALOG.find(h => h.heroId === hero.heroId)?.role || '' }
  currentGameObj.value[key][idx] = merged
  lastPickedHero.value = { ...merged }
  pickerOpen.value = false
  pickerTarget.value = null
  advanceStep()
}

function advanceStep() {
  if (currentGameObj.value.currentStep < activeSequence.value.length) currentGameObj.value.currentStep += 1
  startTimer()
}

function undo() {
  const game = currentGameObj.value
  if (!game.currentStep) return
  game.currentStep -= 1
  const seq = activeSequence.value
  const step = seq[game.currentStep]
  if (!step) return
  if (step.type === 'ban') {
    // ban index = floor(step / 2) for both sides in regular sequence
    const banSteps = seq.filter(s => s.type === 'ban' && s.side === step.side)
    const idx = banSteps.indexOf(step)
    const arr = step.side === 'blue' ? game.blueBans : game.redBans
    if (idx >= 0 && idx < arr.length) arr[idx] = null
  } else {
    const pickSteps = seq.filter(s => s.type === 'pick' && s.side === step.side)
    let idx = pickSteps.indexOf(step)
    // 红方 pick 从右往左填充，撤销时索引也要反转
    if (step.side === 'red') idx = game.redPicks.length - 1 - idx
    const arr = step.side === 'blue' ? game.bluePicks : game.redPicks
    if (idx >= 0 && idx < arr.length) arr[idx] = null
  }
  startTimer()
}

function resetGame() {
  const gi = currentGame.value
  const isLast = (boFormat.value === 7 && gi === 6) || (boFormat.value === 9 && gi === 8)
  games.value[gi] = createGameState(isLast, games.value[gi].firstPickSide)
  lastPickedHero.value = null
  startTimer()
}

function switchGame(index) {
  currentGame.value = index
  if (games.value[index].blueSideTeam) {
    blueSideTeam.value = games.value[index].blueSideTeam
  }
  lastPickedHero.value = null
  startTimer()
}

function swapSides() {
  const game = currentGameObj.value
  ;[game.blueBans, game.redBans] = [game.redBans, game.blueBans]
  ;[game.bluePicks, game.redPicks] = [game.redPicks, game.bluePicks]
  game.blueIsFirst = !game.blueIsFirst
  game.firstPickSide = game.firstPickSide === 'blue' ? 'red' : 'blue'
  blueSideTeam.value = blueSideTeam.value === 'mixue' ? 'luckin' : 'mixue'
}

function firstToMatchPoint() {
  const wins = { mixue: 0, luckin: 0 }
  const mp = boFormat.value === 7 ? 3 : 4
  let first = null
  for (const g of games.value) {
    if (!g.winner) break
    wins[g.winner]++
    if (wins[g.winner] >= mp && !first) first = g.winner
  }
  // 双方同时到赛点（如3:3），不算谁先到
  if (wins.mixue >= mp && wins.luckin >= mp) return null
  return first
}

function markWin(side) {
  const game = currentGameObj.value
  game.winner = side === 'blue' ? blueSideTeam.value : redSideTeam.value
  game.blueSideTeam = blueSideTeam.value
  const gi = currentGame.value
  const isBlindPickGame = (boFormat.value === 7 && gi === 6) || (boFormat.value === 9 && gi === 8)
  const nextGi = gi + 1
  if (nextGi >= games.value.length) return
  const nextIsBlindPick = (boFormat.value === 7 && nextGi === 6) || (boFormat.value === 9 && nextGi === 8)
  if (nextIsBlindPick) {
    // 巅峰对决：先拿到赛点的队伍优先选边
    const chooser = firstToMatchPoint()
    if (chooser) {
      sideChooseLoserTeam.value = chooser
      sideChooseOpen.value = true
    } else {
      // 双方同时到赛点（如3:3），不弹窗，败方坐蓝方
      const loserKey = side === 'blue' ? redSideTeam.value : blueSideTeam.value
      blueSideTeam.value = loserKey
      const isLast = (boFormat.value === 7 && nextGi === 6) || (boFormat.value === 9 && nextGi === 8)
      Object.assign(games.value[nextGi], createGameState(isLast))
      games.value[nextGi].blueSideTeam = loserKey
    }
  } else if (!isBlindPickGame) {
    // 普通局：败方选边
    const loserKey = side === 'blue' ? redSideTeam.value : blueSideTeam.value
    sideChooseLoserTeam.value = loserKey
    sideChooseOpen.value = true
  }
}

// === 败方选边 ===
const sideChooseOpen = ref(false)
const sideChooseLoserTeam = ref(null)
const sideChooseLabel = computed(() => {
  const t = TEAMS[sideChooseLoserTeam.value]?.short || '败方'
  const gi = currentGame.value + 1
  const isBlindPickNext = (boFormat.value === 7 && gi === 6) || (boFormat.value === 9 && gi === 8)
  if (isBlindPickNext) return `${t}（先拿到赛点）优先选边`
  return `${t}（败方）选择阵营`
})

function chooseSide(sideChoice) {
  const gi = currentGame.value + 1
  if (gi >= games.value.length) return
  // sideChoice: 'blueSide' = 败方选蓝方, 'redSide' = 败方选红方
  const loserTeam = sideChooseLoserTeam.value
  const nextBlueTeam = sideChoice === 'blueSide' ? loserTeam : (loserTeam === 'mixue' ? 'luckin' : 'mixue')
  blueSideTeam.value = nextBlueTeam
  const isLast = (boFormat.value === 7 && gi === 6) || (boFormat.value === 9 && gi === 8)
  // 蓝方始终先 BP，败方选阵营只是决定谁坐哪边
  const fps = 'blue'
  Object.assign(games.value[gi], createGameState(isLast, fps))
  games.value[gi].blueSideTeam = nextBlueTeam
  sideChooseOpen.value = false
  currentGame.value = gi
  lastPickedHero.value = null
  startTimer()
}

function isGameDone(index) {
  const game = games.value[index]
  const seq = game.blindPick ? blindPickSequence : createBpSequence(game.firstPickSide)
  return game.currentStep >= seq.length
}

async function loadHeroes() {
  let apiList = []
  try {
    const response = await fetch('/api/query/hero/top?sort=pick&limit=200')
    const body = await response.json()
    apiList = Array.isArray(body?.data?.data) ? body.data.data : Array.isArray(body?.data) ? body.data : []
  } catch {
    apiList = []
  }
  allHeroes.value = mergeHeroes(apiList)
}

function closeBoMenu(e) {
  if (!e.target.closest('.bo-dropdown')) boOpen.value = false
}

onMounted(() => {
  loadHeroes()
  startTimer()
  document.addEventListener('click', closeBoMenu)
})
onUnmounted(() => {
  stopTimer()
  document.removeEventListener('click', closeBoMenu)
})
</script>

<style scoped>
.bp-broadcast {
  --blue: #6752d7;
  --blue-bright: #8e7cf3;
  --red: #d25a78;
  --red-bright: #f287a0;
  --ink: #16202c;
  position: relative;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 14px 18px 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--ink);
  background:
    linear-gradient(120deg, rgba(133, 110, 255, .18), transparent 28%),
    linear-gradient(240deg, rgba(53, 225, 211, .22), transparent 30%),
    linear-gradient(180deg, #dce6f4 0%, #f4f0fa 58%, #d5c7ee 100%);
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.bp-broadcast::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    repeating-linear-gradient(90deg, transparent 0 159px, rgba(73, 89, 121, .12) 160px),
    linear-gradient(180deg, rgba(255, 255, 255, .56), transparent 28%);
  mix-blend-mode: multiply;
}

.aurora {
  position: absolute;
  width: 42vw;
  height: 20vw;
  border-radius: 50%;
  filter: blur(70px);
  opacity: .42;
  pointer-events: none;
}
.aurora-left { left: 0; bottom: 8vh; background: #8a65ec; transform: translateX(-45%); }
.aurora-right { right: 0; top: 10vh; background: #4de0d4; transform: translateX(45%); }

.match-header,
.draft-stage,
.game-strip,
.info-board {
  position: relative;
  z-index: 1;
  width: min(1680px, calc(100vw - 104px));
  min-width: 0;
  margin-inline: auto;
}

.match-header {
  min-height: 104px;
  flex-shrink: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 210px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid rgba(255, 255, 255, .62);
  background: rgba(238, 245, 252, .7);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .38);
}

.team-head,
.team-identity,
.ban-strip {
  display: flex;
  align-items: center;
}
.team-head { min-width: 0; gap: 10px; }
.team-red { justify-content: flex-end; }
.team-identity { min-width: 0; gap: 10px; }
.team-red .team-identity { text-align: right; justify-content: flex-end; }
.team-red .ban-strip { flex-direction: row-reverse; }
.team-red .pick-team { direction: rtl; }
.team-red .pick-team > * { direction: ltr; }
.team-identity small { display: block; margin-bottom: 2px; color: rgba(22, 32, 44, .46); font-size: 9px; font-weight: 900; letter-spacing: 2px; }
.team-identity strong { display: block; font-size: clamp(17px, 1.8vw, 28px); font-weight: 950; white-space: nowrap; }
.team-logo {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
}
.team-logo img { width: 100%; height: 100%; object-fit: contain; }
.team-logo-red { border-color: #d09920; }

.ban-strip { gap: 5px; }
.ban-card {
  position: relative;
  width: clamp(36px, 3.8vw, 48px);
  flex: 0 0 auto;
  aspect-ratio: 1;
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(26, 34, 48, .16);
  border-radius: 10px;
  color: rgba(22, 32, 44, .28);
  background: rgba(255, 255, 255, .38);
  cursor: default;
}
.ban-card img { width: 100%; height: 100%; object-fit: cover; filter: grayscale(1) contrast(1.12); }
.ban-card.filled::after {
  content: "";
  position: absolute;
  inset: 48% -8px auto;
  height: 2px;
  background: #fff;
  transform: rotate(-38deg);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, .55);
}
.ban-card.active { border-color: var(--blue); cursor: pointer; animation: breathe 1.3s infinite; }
.team-red .ban-card.active { border-color: var(--red); }

.score-head { text-align: center; }
.score-head small { color: rgba(22, 32, 44, .46); font-size: 10px; font-weight: 900; letter-spacing: 2px; }
.score-line { display: flex; justify-content: center; align-items: center; gap: 16px; font-size: 36px; line-height: 1; }
.score-line strong { font-size: 50px; font-weight: 950; }
.score-line span { color: rgba(22, 32, 44, .44); font-weight: 400; }
.score-head em { font-style: normal; font-size: 12px; font-weight: 800; letter-spacing: 4px; }

.draft-stage {
  --draft-stage-height: clamp(270px, 40vh, 380px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(170px, 17vw, 230px) minmax(0, 1fr);
  flex: 0 0 var(--draft-stage-height);
  height: var(--draft-stage-height);
  min-height: var(--draft-stage-height);
  max-height: var(--draft-stage-height);
  margin-top: 8px;
  border: 1px solid rgba(255, 255, 255, .68);
  background:
    linear-gradient(90deg, rgba(103, 82, 215, .12), transparent 32%, transparent 68%, rgba(210, 90, 120, .12)),
    rgba(235, 239, 250, .58);
  box-shadow: 0 28px 88px rgba(72, 63, 121, .18), inset 0 0 90px rgba(255, 255, 255, .32);
  backdrop-filter: blur(14px);
}

.draft-stage::before,
.draft-stage::after {
  content: "PICK PHASE";
  position: absolute;
  top: 12px;
  z-index: 3;
  color: rgba(22, 32, 44, .32);
  font-size: 11px;
  font-weight: 950;
  letter-spacing: 4px;
  pointer-events: none;
}

.draft-stage::before { left: 16px; }
.draft-stage::after { right: 16px; }

.pick-team {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  align-items: stretch;
  min-width: 0;
  padding-top: 0;
  height: 100%;
}
.pick-card {
  position: relative;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-right: 1px solid rgba(68, 74, 115, .2);
  color: var(--ink);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .25), rgba(103, 82, 215, .18)),
    linear-gradient(135deg, transparent 50%, rgba(255, 255, 255, .3));
  cursor: default;
  min-height: 0;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .22);
}
.red-picks .pick-card {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .25), rgba(210, 90, 120, .18)),
    linear-gradient(225deg, transparent 50%, rgba(255, 255, 255, .3));
}
.pick-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: var(--poster-x, 50%) var(--poster-y, 18%);
  filter: saturate(.92) contrast(1.05);
  transform: scale(var(--poster-scale, 1.14));
  transform-origin: var(--poster-x, 50%) var(--poster-y, 18%);
  animation: hero-in .35s ease both;
}
.pick-card::after {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, transparent 46%, rgba(70, 48, 132, .78) 100%);
}
.pick-card::before {
  content: "KPL";
  position: absolute;
  z-index: 2;
  top: 12px;
  left: 10px;
  padding: 2px 6px;
  border: 1px solid rgba(255, 255, 255, .46);
  color: rgba(255, 255, 255, .78);
  background: rgba(38, 26, 92, .22);
  font-size: 9px;
  font-weight: 950;
  letter-spacing: 2px;
}
.red-picks .pick-card::before { background: rgba(115, 33, 65, .22); }
.red-picks .pick-card::after { background: linear-gradient(180deg, transparent 46%, rgba(129, 48, 76, .78) 100%); }
.pick-card.active { cursor: pointer; box-shadow: inset 0 0 0 3px var(--blue), inset 0 0 50px rgba(103, 82, 215, .32); animation: breathe 1.3s infinite; }
.red-picks .pick-card.active { box-shadow: inset 0 0 0 3px var(--red), inset 0 0 50px rgba(210, 90, 120, .32); }
.pick-placeholder {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 8px;
  color: rgba(22, 32, 44, .35);
  text-align: center;
  background:
    radial-gradient(circle at 50% 36%, rgba(255, 255, 255, .48), transparent 28%),
    repeating-linear-gradient(180deg, transparent 0 34px, rgba(255, 255, 255, .18) 35px);
}
.pick-placeholder span { font-size: clamp(42px, 5vw, 72px); font-weight: 200; line-height: .9; }
.pick-placeholder b { font-size: 10px; letter-spacing: 2px; }
.pick-card footer {
  position: absolute;
  z-index: 2;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 54px;
  padding: 9px 10px;
  color: white;
  text-align: left;
}
.pick-card footer span { opacity: .74; font-size: 10px; white-space: nowrap; }
.pick-card footer strong { font-size: clamp(12px, 1.25vw, 18px); white-space: normal; line-height: 1.25; word-break: keep-all; }

.center-console {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18px 16px 48px;
  height: 100%;
  border-inline: 1px solid rgba(62, 72, 106, .16);
  text-align: center;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .52), rgba(230, 235, 249, .84)),
    rgba(239, 243, 251, .82);
}
.center-console::before {
  content: "";
  position: absolute;
  inset: 12px;
  border: 1px solid rgba(22, 32, 44, .08);
  pointer-events: none;
}
.phase-kicker { font-size: 11px; font-weight: 950; letter-spacing: 4px; }
.turn-side { margin-top: 14px; color: var(--blue); font-size: 12px; font-weight: 900; letter-spacing: 2px; }
.turn-side.red { color: var(--red); }
.timer { margin-top: 2px; color: var(--blue); font-family: Impact, Haettenschweiler, sans-serif; font-size: clamp(64px, 8vw, 96px); font-style: italic; line-height: 1; letter-spacing: -5px; text-shadow: 7px 7px 0 rgba(103, 82, 215, .12); }
.turn-side.red + .timer { color: var(--red); text-shadow: 7px 7px 0 rgba(210, 90, 120, .12); }
.timer-track { width: min(138px, 70%); height: 4px; margin: 8px 0 14px; overflow: hidden; background: rgba(22, 32, 44, .12); }
.timer-track span { display: block; height: 100%; background: var(--blue); transition: width 1s linear; }
.timer-track span.red { background: var(--red); }
.phase-name { font-size: 16px; }
.center-console p { margin: 6px 0 0; color: rgba(22, 32, 44, .48); font-size: 10px; }
.final-mark { position: absolute; bottom: 22px; display: flex; align-items: center; gap: 8px; font-size: 12px; font-weight: 950; letter-spacing: 4px; }
.final-mark span { width: 34px; height: 1px; background: rgba(22, 32, 44, .42); }

.info-board {
  display: grid;
  grid-template-columns: 1fr 1.2fr 1fr;
  grid-auto-rows: minmax(0, 1fr);
  align-items: stretch;
  gap: 8px;
  margin-top: 8px;
  margin-bottom: 0;
  flex: 1 1 0;
  min-height: 0;
}

.info-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, .62);
  background: rgba(245, 247, 252, .66);
  backdrop-filter: blur(14px);
  box-shadow: inset 0 0 34px rgba(255, 255, 255, .22);
}

.info-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: rgba(22, 32, 44, .48);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
}

.info-title b {
  color: var(--ink);
  letter-spacing: 1px;
}

.guide-panel {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
  margin-top: 10px;
  flex: 1;
}

.guide-side {
  display: grid;
  align-content: center;
  min-height: 56px;
  padding: 8px 10px;
  border-left: 4px solid var(--blue);
  background: rgba(103, 82, 215, .1);
}

.guide-panel.red .guide-side {
  border-left-color: var(--red);
  background: rgba(210, 90, 120, .1);
}

.guide-side small,
.guide-side strong,
.guide-copy span,
.guide-copy b {
  display: block;
}

.guide-side small {
  color: rgba(22, 32, 44, .42);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
}

.guide-side strong {
  margin-top: 4px;
  color: var(--ink);
  font-size: 15px;
}

.guide-copy {
  display: grid;
  align-content: center;
  min-width: 0;
  min-height: 56px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, .38);
}

.guide-copy span {
  color: var(--ink);
  font-size: 13px;
  font-weight: 800;
}

.guide-copy b {
  margin-top: 5px;
  color: rgba(22, 32, 44, .48);
  font-size: 11px;
}


.stats-hero-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}
.stats-avatar { width: 52px; height: 52px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
.stats-hero-info { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.stats-hero-info strong { font-size: 17px; line-height: 1.2; }
.stats-roles { display: flex; flex-wrap: wrap; gap: 4px; }
.role-tag {
  padding: 2px 7px;
  border-radius: 3px;
  background: rgba(22, 32, 44, .07);
  color: rgba(22, 32, 44, .55);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}
.role-tag.main { background: rgba(103, 82, 215, .12); color: var(--blue); }
.tier-tag {
  padding: 2px 7px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 800;
  font-style: normal;
  white-space: nowrap;
  color: #fff;
}
.tier-tag.T0 { background: #e74c3c; }
.tier-tag.T0\.5 { background: #e84393; }
.tier-tag.T1 { background: #e67e22; }
.tier-tag.T2 { background: #3498db; }
.tier-tag.T3 { background: #6c5ce7; }
.tier-tag.T4 { background: #95a5a6; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  margin-top: 6px;
  flex: 1;
  align-content: start;
}
.stat-item {
  padding: 6px 8px;
  background: rgba(255, 255, 255, .38);
  text-align: center;
}
.stat-item small { display: block; color: rgba(22, 32, 44, .42); font-size: 9px; font-weight: 900; letter-spacing: 1px; }
.stat-item b { display: block; margin-top: 2px; color: var(--ink); font-size: 15px; }

.stats-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
  color: rgba(22, 32, 44, .28);
}
.stats-empty .empty-icon { width: 40px; height: 40px; }
.stats-empty span { font-size: 12px; font-weight: 600; letter-spacing: .5px; }

.score-compare {
  display: grid;
  grid-template-columns: 70px 1fr 70px;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.score-side { text-align: center; }
.score-side small { display: block; color: rgba(22, 32, 44, .42); font-size: 9px; font-weight: 950; letter-spacing: 2px; }
.score-side strong { display: block; font-size: 28px; line-height: 1.2; }
.score-side.blue strong { color: var(--blue); }
.score-side.red strong { color: var(--red); }

.score-bar-track {
  height: 8px;
  background: rgba(210, 90, 120, .3);
  overflow: hidden;
}
.score-bar-blue {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--blue), rgba(103, 82, 215, .6));
  transition: width .3s ease;
}

.score-hero-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 8px;
  flex: 1;
  align-content: center;
}
.score-hero-col { display: flex; flex-direction: column; gap: 3px; }
.score-hero-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 8px;
  font-size: 11px;
  background: rgba(255, 255, 255, .3);
  border-radius: 4px;
}
.score-hero-col.blue .score-hero-row { border-left: 2px solid var(--blue); }
.score-hero-col.red .score-hero-row { justify-content: flex-end; text-align: right; border-right: 2px solid var(--red); }
.score-hero-avatar { width: 24px; height: 24px; border-radius: 4px; object-fit: cover; flex-shrink: 0; }
.score-hero-empty { width: 24px; height: 24px; border-radius: 4px; background: rgba(22, 32, 44, .06); flex-shrink: 0; }
.score-hero-row span { color: var(--ink); font-weight: 600; }
.score-hero-row b { color: var(--blue); font-size: 12px; font-weight: 900; min-width: 22px; }
.score-hero-col.red .score-hero-row b { color: var(--red); }
.score-placeholder { color: rgba(22, 32, 44, .22) !important; font-weight: 400 !important; }

.score-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex: 1;
}
.score-vs {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  font-weight: 800;
}
.score-vs .side-blue { color: var(--blue); }
.score-vs .side-red { color: var(--red); }
.score-vs .vs-q { color: rgba(22, 32, 44, .3); font-size: 18px; font-weight: 200; letter-spacing: 2px; }
.empty-track { width: 70%; margin: 0 auto; }
.score-bar-half {
  display: block;
  width: 50%;
  height: 100%;
  background: rgba(103, 82, 215, .2);
}
.empty-hint { color: rgba(22, 32, 44, .3); font-size: 11px; font-weight: 600; }

.game-strip {
  display: flex;
  align-items: stretch;
  gap: 0;
  margin-top: 8px;
  background: rgba(245, 247, 252, .7);
  backdrop-filter: blur(14px);
  z-index: 10;
  flex-shrink: 0;
}
.bo-dropdown {
  position: static;
  display: flex;
  align-items: stretch;
  padding: 0;
  border-right: 1px solid rgba(61, 70, 100, .14);
  user-select: none;
}
.bo-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--ink);
  font-size: 13px;
  font-weight: 900;
  letter-spacing: 1px;
  cursor: pointer;
  white-space: nowrap;
}
.bo-trigger:hover { background: rgba(103, 82, 215, .06); }
.bo-arrow {
  width: 10px;
  height: 6px;
  color: rgba(22, 32, 44, .4);
  transition: transform .2s;
}
.bo-arrow.open { transform: rotate(180deg); }
.bo-menu {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 20;
  margin-top: 2px;
  border: 1px solid rgba(255, 255, 255, .62);
  background: rgba(232, 238, 249, .97);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 28px rgba(49, 57, 92, .16);
}
.bo-menu button {
  display: block;
  width: 100%;
  padding: 9px 14px;
  border: none;
  background: transparent;
  color: rgba(22, 32, 44, .62);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1px;
  text-align: left;
  cursor: pointer;
  transition: background .15s, color .15s;
}
.bo-menu button:hover { background: rgba(103, 82, 215, .08); color: var(--ink); }
.bo-menu button.active { color: var(--blue); background: rgba(103, 82, 215, .1); font-weight: 900; }

.game-nav {
  display: flex;
  flex: 1;
  overflow-x: auto;
}
.game-nav button {
  flex: 1;
  min-width: 0;
  padding: 9px 14px;
  border: none;
  border-right: 1px solid rgba(61, 70, 100, .1);
  background: transparent;
  color: rgba(22, 32, 44, .62);
  text-align: left;
  cursor: pointer;
}
.game-nav button:last-child { border-right: none; }
.game-nav span,
.game-nav b { display: block; }
.game-nav span { font-size: 10px; font-weight: 950; letter-spacing: 1.2px; }
.game-nav b { margin-top: 2px; font-size: 11px; }
.game-nav button.active { color: #fff; background: var(--blue); }
.game-nav button.done:not(.active) { color: var(--blue); }
.game-nav button:hover:not(.active) { background: rgba(103, 82, 215, .08); }

.action-bar {
  display: flex;
  align-items: stretch;
  margin-left: auto;
  border-left: 1px solid rgba(61, 70, 100, .14);
}
.action-bar button {
  padding: 0 13px;
  border: none;
  border-right: 1px solid rgba(61, 70, 100, .1);
  color: rgba(22, 32, 44, .62);
  background: transparent;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  transition: background .15s, color .15s;
}
.action-bar button:last-child { border-right: none; }
.action-bar button:hover { color: var(--ink); background: rgba(255, 255, 255, .45); }
.action-bar button:disabled { opacity: .35; cursor: not-allowed; }
.action-bar button.timer-on { color: #fff; background: var(--blue); }
.action-bar .blue-action { color: var(--blue); }
.action-bar .red-action { color: var(--red); }
.action-bar .danger { color: #b93e5d; }

:global(.hero-picker-dialog .el-dialog) {
  border: 1px solid rgba(255, 255, 255, .7) !important;
  border-radius: 0 !important;
  background: rgba(232, 238, 249, .96) !important;
  backdrop-filter: blur(22px);
}
:global(.el-dialog.hero-picker-dialog) {
  border: 1px solid rgba(255, 255, 255, .7) !important;
  border-radius: 0 !important;
  background: rgba(232, 238, 249, .96) !important;
  backdrop-filter: blur(22px);
}
:global(.hero-picker-dialog .el-dialog__title),
:global(.el-dialog.hero-picker-dialog .el-dialog__title) {
  color: #16202c !important;
  font-weight: 900 !important;
}
:global(.el-dialog.hero-picker-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: rgba(22, 32, 44, .48) !important;
}
.picker-toolbar { display: flex; gap: 8px; margin-bottom: 14px; align-items: center; }
.picker-toolbar input { flex: 0 0 160px; padding: 9px 12px; border: 1px solid rgba(22, 32, 44, .13); color: var(--ink); background: rgba(255, 255, 255, .5); outline: none; }
.picker-filters { display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
.picker-filters button { padding: 6px 9px; border: 1px solid rgba(22, 32, 44, .13); color: rgba(22, 32, 44, .6); background: rgba(255, 255, 255, .5); font-size: 11px; font-weight: 700; cursor: pointer; transition: all .15s; }
.picker-filters button:hover { color: var(--ink); border-color: rgba(22, 32, 44, .25); }
.picker-filters button.active { color: #fff; background: var(--blue); border-color: var(--blue); }
.filter-sep { width: 1px; height: 18px; background: rgba(22, 32, 44, .12); margin: 0 4px; }
.tier-btn.T0.active { background: #e74c3c; border-color: #e74c3c; }
.tier-btn.T0\.5.active { background: #e84393; border-color: #e84393; }
.tier-btn.T1.active { background: #e67e22; border-color: #e67e22; }
.tier-btn.T2.active { background: #3498db; border-color: #3498db; }
.tier-btn.T3.active { background: #6c5ce7; border-color: #6c5ce7; }
.tier-btn.T4.active { background: #95a5a6; border-color: #95a5a6; }
.picker-tier-list { max-height: 520px; overflow-y: auto; }
.tier-group { margin-bottom: 12px; }
.tier-group:last-child { margin-bottom: 0; }
.tier-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  margin-bottom: 6px;
  border-radius: 4px;
  background: rgba(22, 32, 44, .05);
}
.tier-label {
  padding: 2px 8px;
  border-radius: 3px;
  color: #fff;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .5px;
  white-space: nowrap;
}
.tier-name { font-size: 13px; font-weight: 900; color: var(--ink); white-space: nowrap; }
.tier-desc { font-size: 11px; color: rgba(22, 32, 44, .45); white-space: nowrap; }
.tier-header.T0 .tier-label { background: #e74c3c; }
.tier-header.T0\.5 .tier-label { background: #e84393; }
.tier-header.T1 .tier-label { background: #e67e22; }
.tier-header.T2 .tier-label { background: #3498db; }
.tier-header.T3 .tier-label { background: #6c5ce7; }
.tier-header.T4 .tier-label { background: #95a5a6; }
.picker-grid { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 6px; }
.picker-grid button { min-width: 0; padding: 5px; border: 1px solid rgba(22, 32, 44, .12); background: rgba(255, 255, 255, .5); cursor: pointer; }
.picker-grid button:hover { border-color: var(--blue); transform: translateY(-2px); }
.picker-grid button:disabled { opacity: .25; cursor: not-allowed; transform: none; }
.picker-grid img { width: 100%; aspect-ratio: 1; object-fit: cover; }
.picker-grid strong,
.picker-grid span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-grid strong { margin-top: 4px; color: var(--ink); font-size: 11px; }
.picker-grid span { color: rgba(22, 32, 44, .46); font-size: 9px; }

@keyframes breathe {
  50% { filter: brightness(1.15); box-shadow: inset 0 0 0 3px currentColor, 0 0 22px currentColor; }
}
@keyframes hero-in {
  from { opacity: 0; filter: saturate(.7) contrast(1.02); }
}

@media (max-width: 1200px) {
  .match-header { grid-template-columns: minmax(0, 1fr) 160px minmax(0, 1fr); }
  .team-identity { min-width: 0; }
  .team-identity strong { font-size: 16px; }
  .team-logo { display: none; }
  .draft-stage { grid-template-columns: minmax(0, 1fr) 190px minmax(0, 1fr); }
  .timer { font-size: 78px; }
  .action-bar button { min-height: 38px; }
}

:global(.side-choose-dialog .el-dialog),
:global(.el-dialog.side-choose-dialog) {
  border: 1px solid rgba(255, 255, 255, .7) !important;
  border-radius: 0 !important;
  background: rgba(232, 238, 249, .96) !important;
  backdrop-filter: blur(22px);
}
:global(.side-choose-dialog .el-dialog__title),
:global(.el-dialog.side-choose-dialog .el-dialog__title) {
  color: #16202c !important;
  font-weight: 900 !important;
}
:global(.el-dialog.side-choose-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: rgba(22, 32, 44, .48) !important;
}
.side-choose-desc { color: rgba(22, 32, 44, .58); font-size: 13px; margin-bottom: 20px; }
.side-choose-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.side-option {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 18px;
  border: 2px solid rgba(22, 32, 44, .1);
  border-radius: 10px;
  background: rgba(255, 255, 255, .5);
  cursor: pointer;
  transition: all .2s;
}
.side-option:hover { border-color: var(--blue); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(103, 82, 215, .15); }
.side-option-red:hover { border-color: var(--red); box-shadow: 0 8px 24px rgba(210, 90, 120, .15); }
.side-option-indicator {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  flex-shrink: 0;
}
.side-option-indicator.blue { background: linear-gradient(135deg, var(--blue), var(--blue-bright)); }
.side-option-indicator.red { background: linear-gradient(135deg, var(--red), var(--red-bright)); }
.side-option strong { display: block; font-size: 18px; font-weight: 950; color: var(--ink); }
.side-option small { display: block; margin-top: 3px; font-size: 11px; color: rgba(22, 32, 44, .48); }

@media (max-width: 767px) {
  .bp-broadcast {
    min-height: 100vh;
    margin-left: 0;
    padding: 8px 8px calc(72px + env(safe-area-inset-bottom));
    overflow-x: hidden;
    overflow-y: auto;
  }

  .match-header,
  .draft-stage,
  .game-strip,
  .info-board {
    width: 100%;
    min-width: 0;
  }

  .match-header {
    min-height: auto;
    grid-template-columns: 1fr auto 1fr;
    gap: 8px;
    padding: 8px;
  }

  .team-head {
    flex-direction: column-reverse;
    align-items: flex-start;
    gap: 6px;
  }

  .team-red {
    flex-direction: column;
    align-items: flex-end;
  }

  .team-identity {
    gap: 6px;
  }

  .team-red .team-identity {
    flex-direction: row-reverse;
  }

  .team-logo {
    display: grid;
    width: 34px;
    height: 34px;
    font-size: 13px;
    border-width: 2px;
  }

  .team-identity small {
    display: none;
  }

  .team-identity strong {
    max-width: 30vw;
    overflow: hidden;
    font-size: 13px;
    text-overflow: ellipsis;
  }

  .ban-strip {
    gap: 4px;
  }

  .ban-card {
    width: 30px;
    border-radius: 7px;
  }

  .ban-card span {
    font-size: 10px;
  }

  .score-head small {
    display: none;
  }

  .score-line {
    gap: 8px;
  }

  .score-line strong {
    font-size: 34px;
  }

  .score-line span {
    font-size: 24px;
  }

  .score-head em {
    font-size: 10px;
    letter-spacing: 2px;
  }

  .draft-stage {
    --draft-stage-height: auto;
    display: grid;
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
    max-height: none;
    margin-top: 8px;
    overflow: hidden;
  }

  .draft-stage::before,
  .draft-stage::after {
    display: none;
  }

  .center-console {
    order: -1;
    min-height: 118px;
    padding: 12px 14px 32px;
    border-inline: none;
    border-bottom: 1px solid rgba(62, 72, 106, .16);
  }

  .center-console::before {
    inset: 8px;
  }

  .phase-kicker {
    font-size: 10px;
  }

  .turn-side {
    margin-top: 8px;
    font-size: 11px;
  }

  .timer {
    font-size: 58px;
    letter-spacing: -3px;
  }

  .timer-track {
    width: 124px;
    margin: 6px 0 8px;
  }

  .phase-name {
    font-size: 14px;
  }

  .center-console p {
    display: none;
  }

  .final-mark {
    bottom: 12px;
    font-size: 10px;
    letter-spacing: 3px;
  }

  .final-mark span {
    width: 24px;
  }

  .pick-team {
    display: grid;
    grid-template-columns: repeat(5, minmax(88px, 1fr));
    height: auto;
    min-width: 0;
    overflow-x: auto;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
  }

  .blue-picks {
    border-bottom: 1px solid rgba(62, 72, 106, .12);
  }

  .pick-card {
    min-height: 156px;
    aspect-ratio: 9 / 16;
    scroll-snap-align: start;
  }

  .pick-placeholder span {
    font-size: 40px;
  }

  .pick-placeholder b {
    font-size: 9px;
  }

  .pick-card::before {
    top: 8px;
    left: 8px;
    font-size: 8px;
  }

  .pick-card footer {
    min-height: 42px;
    padding: 7px 8px;
  }

  .pick-card footer span {
    font-size: 9px;
  }

  .pick-card footer strong {
    font-size: 12px;
  }

  .game-strip {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    margin-top: 8px;
  }

  .bo-dropdown {
    min-width: 72px;
  }

  .bo-trigger {
    height: 42px;
    padding: 0 10px;
    font-size: 12px;
  }

  .game-nav {
    min-width: 0;
    overflow-x: auto;
  }

  .game-nav button {
    flex: 0 0 82px;
    padding: 7px 10px;
  }

  .game-nav span {
    font-size: 9px;
  }

  .game-nav b {
    font-size: 10px;
  }

  .action-bar {
    grid-column: 1 / -1;
    min-width: 0;
    margin-left: 0;
    border-top: 1px solid rgba(61, 70, 100, .1);
    border-left: none;
    overflow-x: auto;
  }

  .action-bar button {
    flex: 1 0 auto;
    min-height: 36px;
    padding: 0 10px;
    font-size: 10px;
  }

  .info-board {
    grid-template-columns: 1fr;
    grid-auto-rows: auto;
    gap: 8px;
    margin-top: 8px;
    flex: none;
  }

  .info-card {
    min-height: 132px;
    height: auto;
    overflow: visible;
    padding: 10px;
  }

  .hero-stats-card {
    min-height: 150px;
  }

  .guide-panel {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .guide-side,
  .guide-copy {
    min-height: 48px;
  }

  .guide-copy span {
    font-size: 12px;
  }

  .stats-avatar {
    width: 44px;
    height: 44px;
  }

  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .score-compare {
    grid-template-columns: 54px 1fr 54px;
  }

  .score-side strong {
    font-size: 22px;
  }

  .score-hero-list {
    gap: 6px;
  }

  .score-hero-row {
    padding: 4px 6px;
    font-size: 10px;
  }

  :global(.el-dialog.hero-picker-dialog),
  :global(.hero-picker-dialog .el-dialog) {
    width: calc(100vw - 14px) !important;
    margin-top: 2vh !important;
  }

  .picker-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .picker-toolbar input {
    flex: none;
    width: 100%;
  }

  .picker-filters {
    max-height: 84px;
    overflow-y: auto;
  }

  .picker-tier-list {
    max-height: 66vh;
  }

  .tier-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .tier-desc {
    white-space: normal;
  }

  .picker-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .picker-toolbar { flex-wrap: wrap; }
}
</style>
