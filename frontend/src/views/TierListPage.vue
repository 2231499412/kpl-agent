<template>
  <main class="app-shell has-sidebar tier-console" :class="`theme-${theme}`">
    <div class="grid-overlay"></div>
    <div class="noise-overlay"></div>

    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">T</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>英雄梯度榜</h1>
        </div>
      </div>
      <div class="status-line">
        <span class="algo-badge">算法测试中</span>
        <span class="date-tag">{{ currentDate }}</span>
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }"><span class="toggle-thumb" /></span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <!-- 分路筛选 -->
    <nav ref="roleNavRef" class="role-tabs">
      <span class="role-pill" :style="pillStyle"></span>
      <button
        v-for="r in roleOptions"
        :key="r.value"
        :ref="el => roleBtnRefs[r.value] = el"
        :class="{ active: activeRole === r.value }"
        @click="selectRole(r.value)"
      >{{ r.label }}</button>
    </nav>

    <!-- 图例 + 评分说明 -->
    <div class="info-bar" v-if="!loading && filteredHeroes.length">
      <div class="legend-items">
        <div class="legend-item">
          <span class="legend-dot high-ban"></span>
          <span>高禁用率 (&gt;30%)</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot low-pick"></span>
          <span>低出场 (&lt;10场)</span>
        </div>
      </div>
      <div class="score-formula">
        <span class="formula-label">评分</span>
        <span class="formula-eq">= BP率 &times; 50 + 禁用率 &times; 30 + 胜率 &times; 20</span>
        <span class="formula-note">BP率 = 出场率 + 禁用率，禁用率单独加权因被 ban 代表对手认为更强，胜率代表实际表现。梯度按百分位分档，反映相对优先级。</span>
      </div>
    </div>

    <!-- 加载 -->
    <div v-if="loading" class="loading-wrap">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>

    <!-- 梯度内容 -->
    <div v-else class="tier-content">
      <TransitionGroup name="tier-sort" tag="div" class="hero-flat-grid">
        <template v-for="(item, idx) in flatHeroes" :key="item._key">
          <div v-if="item._type === 'header'" class="tier-header tier-header-row">
            <span :class="['tier-badge', 'tier-' + item.tier.key]">{{ item.tier.label }}</span>
            <span class="tier-name">{{ item.tier.name }}</span>
            <span class="tier-criteria">{{ item.tier.desc }}{{ item.tier.scoreRange ? ' · 评分 ' + item.tier.scoreRange : '' }}</span>
            <span class="tier-count">{{ item.tier.heroes.length }} 英雄</span>
          </div>
          <div v-else class="hero-card"
            :class="{ 'high-ban': item.banRate > 0.3, 'low-pick': item.battleCount < 10 }">
            <img
              :src="item.heroIcon || ('https://res.edata.qq.com/sgame/static/images/hero/' + item.heroId + '.jpg')"
              class="hero-avatar"
              :alt="item.heroName"
              @load="$event.target.style.opacity = 1"
            />
            <span class="hero-score">{{ item._score }}</span>
            <span class="hero-name">{{ item.heroName }}</span>
          </div>
        </template>
      </TransitionGroup>

      <div v-if="!filteredHeroes.length && !loading" class="empty-state">
        <span>暂无数据，请先选择赛事</span>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const loading = ref(false)
const heroList = ref([])
const activeRole = ref('all')
const roleNavRef = ref(null)
const roleBtnRefs = {}
const pillStyle = ref({})

function selectRole(value) {
  activeRole.value = value
  nextTick(updatePill)
}

function updatePill() {
  const nav = roleNavRef.value
  const btn = roleBtnRefs[activeRole.value]
  if (!nav || !btn) return
  const navRect = nav.getBoundingClientRect()
  const btnRect = btn.getBoundingClientRect()
  pillStyle.value = {
    width: btnRect.width + 'px',
    transform: `translateX(${btnRect.left - navRect.left}px)`,
  }
}

const currentDate = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })

const roleOptions = [
  { label: '全部分路', value: 'all' },
  { label: '对抗路', value: '对抗路' },
  { label: '打野', value: '打野' },
  { label: '中路', value: '中路' },
  { label: '发育路', value: '发育路' },
  { label: '游走', value: '游走' },
]

// 英雄-分路映射（硬编码）
const heroRoleMap = {
  // 对抗路
  '关羽': '对抗路', '夏侯惇': '对抗路', '吕布': '对抗路', '曹操': '对抗路',
  '花木兰': '对抗路', '狂铁': '对抗路', '马超': '对抗路', '蒙恬': '对抗路',
  '白起': '对抗路', '猪八戒': '对抗路', '夏洛特': '对抗路', '司空震': '对抗路',
  '李信': '对抗路', '芈月': '对抗路', '刘邦': '对抗路', '哪吒': '对抗路',
  '孙策': '对抗路', '关羽': '对抗路', '暗信': '对抗路', '亚连': '对抗路',
  '姬小满': '对抗路', '海诺': '对抗路',

  // 打野
  '赵云': '打野', '韩信': '打野', '露娜': '打野', '镜': '打野',
  '澜': '打野', '云缨': '打野', '兰陵王': '打野', '李白': '打野',
  '阿轲': '打野', '裴擒虎': '打野', '百里玄策': '打野', '橘右京': '打野',
  '娜可露露': '打野', '曜': '打野', '盘古': '打野', '刘备': '打野',
  '雅典娜': '打野', '曹操': '打野', '宫本武藏': '打野', '铠': '打野',
  '猪八戒': '打野', '梦奇': '打野', '司马懿': '打野', '暃': '打野',
  '大司命': '打野', '云中君': '打野',

  // 中路
  '诸葛亮': '中路', '干将莫邪': '中路', '貂蝉': '中路', '西施': '中路',
  '王昭君': '中路', '张良': '中路', '嬴政': '中路', '小乔': '中路',
  '甄姬': '中路', '安琪拉': '中路', '妲己': '中路', '高渐离': '中路',
  '杨玉环': '中路', '女娲': '中路', '沈梦溪': '中路', '上官婉儿': '中路',
  '海月': '中路', '金蝉': '中路', '姜子牙': '中路', '周瑜': '中路',
  '不知火舞': '中路', '扁鹊': '中路', '武则天': '中路', '弈星': '中路',
  '米莱狄': '中路', '嫦娥': '中路',

  // 发育路
  '公孙离': '发育路', '马可波罗': '发育路', '伽罗': '发育路', '后羿': '发育路',
  '鲁班七号': '发育路', '黄忠': '发育路', '孙尚香': '发育路', '李元芳': '发育路',
  '狄仁杰': '发育路', '虞姬': '发育路', '百里守约': '发育路', '成吉思汗': '发育路',
  '艾琳': '发育路', '戈娅': '发育路', '莱西奥': '发育路', '蒙犽': '发育路',
  '敖隐': '发育路',

  // 游走
  '张飞': '游走', '牛魔': '游走', '鬼谷子': '游走', '太乙真人': '游走',
  '瑶': '游走', '蔡文姬': '游走', '大乔': '游走', '刘禅': '游走',
  '庄周': '游走', '廉颇': '游走', '苏烈': '游走', '盾山': '游走',
  '鲁班大师': '游走', '孙膑': '游走', '明世隐': '游走', '东皇太一': '游走',
  '钟馗': '游走', '鬼谷子': '游走', '桑启': '游走', '少司缘': '游走',
  '朵莉亚': '游走',
}

function getRole(heroName) {
  return heroRoleMap[heroName] || ''
}

// 评分 = BP率权重 + 禁用率权重 + 胜率权重（满分100）
function getScore(hero) {
  const bp = (hero.pickRate || 0) + (hero.banRate || 0)
  const ban = hero.banRate || 0
  const wr = hero.winRate || 0
  return Math.round(bp * 50 + ban * 30 + wr * 20)
}

const tierDefs = [
  { key: 't0',  label: 'T0',   name: '绝对核心',  desc: 'BP率极高，版本答案' },
  { key: 't05', label: 'T0.5', name: '最高优先',  desc: 'BP率很高，优先锁定' },
  { key: 't1',  label: 'T1',   name: '主力梯队',  desc: '高BP率，主流选择' },
  { key: 't2',  label: 'T2',   name: '常规出场',  desc: '稳定出场，体系常用' },
  { key: 't3',  label: 'T3',   name: '战术选择',  desc: '针对性出场 / counter位' },
  { key: 't4',  label: 'T4',   name: '冷门登场',  desc: '偶尔亮相，特定阵容' },
]

// 百分位分档：按分数排序，按比例切分
const tierPercentiles = [0.10, 0.25, 0.50, 0.75, 0.90] // T0: 前10%, T0.5: 10-25%, T1: 25-50% ...

function assignTiers(heroes) {
  if (!heroes.length) return heroes
  const sorted = [...heroes].sort((a, b) => b._score - a._score)
  const n = sorted.length
  const thresholds = tierPercentiles.map(p => Math.ceil(n * p))
  return sorted.map((h, i) => {
    let tier
    if (i < thresholds[0]) tier = 't0'
    else if (i < thresholds[1]) tier = 't05'
    else if (i < thresholds[2]) tier = 't1'
    else if (i < thresholds[3]) tier = 't2'
    else if (i < thresholds[4]) tier = 't3'
    else tier = 't4'
    return { ...h, _tier: tier }
  })
}

const processedHeroes = computed(() => {
  const withScore = heroList.value.map(h => ({
    ...h,
    _role: getRole(h.heroName),
    _score: getScore(h),
  }))
  return assignTiers(withScore)
})

const filteredHeroes = computed(() => {
  if (activeRole.value === 'all') return processedHeroes.value
  return processedHeroes.value.filter(h => h._role === activeRole.value)
})

const visibleTiers = computed(() => {
  return tierDefs.map(t => ({
    ...t,
    heroes: filteredHeroes.value
      .filter(h => h._tier === t.key)
      .sort((a, b) => b._score - a._score),
    // 动态计算该梯度的分数范围
    scoreRange: (() => {
      const tierHeroes = filteredHeroes.value.filter(h => h._tier === t.key)
      if (!tierHeroes.length) return ''
      const scores = tierHeroes.map(h => h._score)
      return `${Math.min(...scores)}-${Math.max(...scores)}`
    })(),
  }))
})

const flatHeroes = computed(() => {
  const result = []
  for (const tier of visibleTiers.value) {
    if (!tier.heroes.length) continue
    result.push({ _type: 'header', _key: `tier-${tier.key}`, tier })
    for (const hero of tier.heroes) {
      result.push({ ...hero, _type: 'item', _key: `hero-${hero.heroId}` })
    }
  }
  return result
})

async function loadData() {
  loading.value = true
  try {
    const res = await fetch('/api/query/hero/top?sort=pick&limit=200')
    const body = await res.json()
    const data = body?.data
    heroList.value = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : []
  } catch {
    heroList.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
  nextTick(updatePill)
  window.addEventListener('resize', updatePill)
})
onUnmounted(() => {
  window.removeEventListener('resize', updatePill)
})
</script>

<style scoped>
.tier-console {
  --c-bg: #f8f5ec;
  --c-panel: rgba(255, 255, 255, 0.92);
  --c-line: rgba(0, 0, 0, 0.38);
  --c-ink: #1a1a1a;
  --c-soft: rgba(26, 26, 26, 0.6);
  --c-dim: rgba(26, 26, 26, 0.35);
  --c-card: rgba(0, 0, 0, 0.03);
  --c-corner: rgba(0, 0, 0, 0.18);
  --c-hover: rgba(0, 0, 0, 0.06);
  --c-grid: rgba(0, 0, 0, 0.04);
  --tier-t0: #c0392b;
  --tier-t05: #e67e22;
  --tier-t1: #f39c12;
  --tier-t2: #2980b9;
  --tier-t3: #7f8c8d;
  --tier-t4: #bdc3c7;

  position: relative;
  min-height: 100vh;
  padding: 28px 32px calc(78px + env(safe-area-inset-bottom)) 87px;
  color: var(--c-ink);
  background: linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)), #f8f5ec;
  overflow-x: hidden;
}
.tier-console.theme-dark {
  --c-bg: #0a0a0a;
  --c-panel: rgba(18, 18, 18, 0.92);
  --c-line: rgba(255, 255, 255, 0.2);
  --c-ink: #e8e8e8;
  --c-soft: rgba(232, 232, 232, 0.6);
  --c-dim: rgba(232, 232, 232, 0.35);
  --c-card: rgba(255, 255, 255, 0.05);
  --c-corner: rgba(255, 255, 255, 0.18);
  --c-hover: rgba(255, 255, 255, 0.08);
  --c-grid: rgba(255, 255, 255, 0.03);
  --tier-t0: #e74c3c;
  --tier-t05: #f39c12;
  --tier-t1: #f1c40f;
  --tier-t2: #3498db;
  --tier-t3: #95a5a6;
  --tier-t4: #7f8c8d;
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

/* 网格纹理 */
.grid-overlay {
  position: fixed; inset: 0; z-index: -3;
  background:
    linear-gradient(var(--c-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--c-grid) 1px, transparent 1px);
  background-size: 80px 80px;
  pointer-events: none;
}
.noise-overlay {
  position: fixed; inset: 0; z-index: -2;
  background: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.03'/%3E%3C/svg%3E");
  pointer-events: none;
}

/* 顶栏 */
.command-strip {
  position: relative; display: flex; align-items: center; justify-content: space-between;
  padding: 18px 22px; border: 1px solid var(--c-line); background: var(--c-panel);
  margin-bottom: 16px; flex-shrink: 0;
}
.command-strip::before {
  content: ""; position: absolute; left: -1px; top: -1px; width: 36px; height: 36px;
  border-left: 2px solid var(--c-corner); border-top: 2px solid var(--c-corner);
  pointer-events: none;
}
.brand-block { display: flex; align-items: center; gap: 14px; }
.brand-mark {
  width: 42px; height: 42px; display: grid; place-items: center;
  border: 1px solid var(--c-line); color: var(--c-ink); background: var(--c-card);
  font-size: 18px; font-weight: 900;
}
.eyebrow { margin: 0; color: var(--c-dim); font-size: 10px; font-weight: 800; letter-spacing: 2px; text-transform: uppercase; }
h1 { margin: 0; color: var(--c-ink); font-size: 20px; font-weight: 900; }
.status-line { display: flex; align-items: center; gap: 12px; }
.algo-badge {
  padding: 4px 10px; font-size: 11px; font-weight: 700; letter-spacing: 1px;
  border: 1px solid rgba(243, 156, 18, 0.4); color: #f39c12; background: rgba(243, 156, 18, 0.08);
}
.date-tag { font-size: 12px; color: var(--c-dim); font-weight: 600; }
.theme-toggle {
  display: inline-flex; align-items: center; gap: 8px; padding: 0; border: 0; background: none; cursor: pointer;
  color: var(--c-dim); font-size: 11px; font-weight: 700; letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--c-ink); }
.toggle-track {
  position: relative; width: 32px; height: 16px; border-radius: 8px;
  background: var(--c-line); transition: background 0.2s;
}
.toggle-track.on { background: var(--c-ink); }
.toggle-thumb {
  position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%;
  background: var(--c-bg); transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }

/* 分路筛选 */
.role-tabs {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 5px;
  padding: 4px;
  overflow-x: auto;
}
.role-pill {
  position: absolute;
  top: 4px;
  left: 0;
  height: calc(100% - 8px);
  background: #1a1a1a;
  border-radius: 10px;
  transition: transform .3s cubic-bezier(.4,0,.2,1);
  z-index: 0;
  pointer-events: none;
}
.role-tabs button {
  position: relative;
  z-index: 1;
  flex: 1 0 auto;
  min-width: 86px;
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--c-line);
  border-radius: 10px;
  color: var(--c-soft);
  background: transparent;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  transition: color .2s ease;
}
.role-tabs button:hover { color: var(--c-ink); }
.role-tabs button.active { color: #f8f5ec; }

/* 扁平网格 + 排序动画 */
.hero-flat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
}
.tier-header-row {
  grid-column: 1 / -1;
}
.tier-sort-move {
  transition: transform 0.9s cubic-bezier(0.22, 1, 0.36, 1);
}
.tier-sort-leave-active {
  transition: opacity 0.36s cubic-bezier(0.4, 0, 1, 1),
              transform 0.36s cubic-bezier(0.4, 0, 1, 1);
  position: absolute;
}
.tier-sort-leave-to {
  opacity: 0;
  transform: scale(0.86);
}
.tier-sort-enter-active {
  transition: all 0.35s cubic-bezier(0, 0, 0.2, 1) 0.15s;
}
.tier-sort-enter-from {
  opacity: 0;
  transform: scale(0.86);
}

/* 加载 */
.loading-wrap {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; padding: 80px 0; color: var(--c-dim);
}
.loading-spinner {
  width: 28px; height: 28px; border: 3px solid var(--c-line);
  border-top-color: var(--c-ink); border-radius: 50%; animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty-state {
  display: flex; align-items: center; justify-content: center;
  padding: 80px 0; color: var(--c-dim); font-size: 14px;
}

/* 梯度区块 */
.tier-content { display: flex; flex-direction: column; gap: 0; }

.tier-section { padding: 0 0 8px; }

.tier-header {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 12px; border-bottom: 1px solid var(--c-line);
  margin-bottom: 14px;
}
.tier-badge {
  min-width: 36px; height: 26px; display: inline-grid; place-items: center;
  font-size: 13px; font-weight: 900; color: #fff; border-radius: 3px; padding: 0 6px;
}
.tier-badge.tier-t0 { background: var(--tier-t0); }
.tier-badge.tier-t05 { background: var(--tier-t05); }
.tier-badge.tier-t1 { background: var(--tier-t1); color: #1a1a1a; }
.tier-badge.tier-t2 { background: var(--tier-t2); }
.tier-badge.tier-t3 { background: var(--tier-t3); }
.tier-badge.tier-t4 { background: var(--tier-t4); }
.tier-name { font-size: 14px; font-weight: 700; color: var(--c-ink); }
.tier-criteria { font-size: 12px; color: var(--c-dim); margin-left: 4px; }
.tier-count { font-size: 12px; color: var(--c-dim); margin-left: auto; }

.tier-divider {
  height: 0; border-top: 1px dashed var(--c-line);
  margin: 6px 0;
}

/* 英雄卡片网格 */
.hero-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
}

.hero-card {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 10px 6px 8px; background: var(--c-card); border: 1px solid var(--c-line);
  transition: all 0.15s; position: relative;
}
.hero-card:hover {
  background: var(--c-hover); border-color: var(--c-ink);
  transform: translateY(-2px);
}
.hero-card.high-ban { border-color: #e74c3c; border-width: 2px; box-shadow: 0 0 8px rgba(231, 76, 60, 0.25); }
.hero-card.low-pick { border-color: #3498db; border-width: 2px; border-style: dashed; box-shadow: 0 0 8px rgba(52, 152, 219, 0.2); }

.hero-avatar {
  width: 48px; height: 48px; border-radius: 4px; object-fit: cover;
  border: 1px solid var(--c-line);
  opacity: 0; transition: opacity .15s ease;
}
.hero-score {
  font-size: 18px; font-weight: 900; color: var(--c-ink);
  line-height: 1;
}
.hero-name {
  font-size: 11px; color: var(--c-soft); font-weight: 600;
  text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 100%;
}

/* 图例 + 评分说明 */
.info-bar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 16px;
  padding: 12px 16px; margin-bottom: 16px;
  border: 1px solid var(--c-line); background: var(--c-panel);
}
.legend-items { display: flex; gap: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--c-soft); }
.legend-dot {
  width: 10px; height: 10px; border-radius: 2px; flex-shrink: 0;
}
.legend-dot.high-ban { background: #e74c3c; }
.legend-dot.low-pick { background: #3498db; }
.score-formula {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-size: 12px; color: var(--c-soft); margin-left: auto;
}
.formula-label { font-weight: 800; color: var(--c-ink); }
.formula-eq { color: var(--c-ink); font-weight: 600; white-space: nowrap; }
.formula-note {
  font-size: 11px; color: var(--c-dim); line-height: 1.5;
  flex-basis: 100%;
}

/* 移动端 */
@media (max-width: 767px) {
  .tier-console {
    padding: 12px 12px calc(82px + env(safe-area-inset-bottom));
  }

  .command-strip {
    min-height: 56px; padding: 10px 12px;
  }
  .brand-mark { width: 34px; height: 34px; font-size: 16px; }
  h1 { font-size: 17px; }
  .algo-badge { font-size: 10px; padding: 3px 8px; }
  .date-tag { display: none; }

  .role-tabs { gap: 4px; padding: 3px; }
  .role-tabs button { flex: 0 0 76px; min-width: 76px; height: 42px; padding: 0 10px; }

  .tier-header { padding: 10px 0 8px; margin-bottom: 10px; }
  .tier-badge { min-width: 32px; height: 22px; font-size: 12px; }
  .tier-name { font-size: 13px; }
  .tier-criteria { font-size: 11px; display: none; }
  .tier-count { font-size: 11px; }

  .hero-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }
  .hero-flat-grid {
    gap: 8px;
  }
  .hero-flat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }

  .hero-card { padding: 8px 4px 6px; }
  .hero-avatar { width: 40px; height: 40px; }
  .hero-score { font-size: 15px; }
  .hero-name { font-size: 10px; }

  .info-bar { flex-direction: column; align-items: flex-start; gap: 10px; padding: 10px 12px; }
  .legend-items { gap: 12px; }
  .legend-item { font-size: 11px; }
  .score-formula { margin-left: 0; }
  .formula-note { font-size: 10px; }
}
</style>
