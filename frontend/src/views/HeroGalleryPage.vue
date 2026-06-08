<template>
  <main class="gallery-broadcast" :class="`theme-${theme}`">
    <header class="gallery-header">
      <section class="brand-block">
        <div>
          <small>KPL HERO ARCHIVE</small>
          <h1>原画画廊</h1>
        </div>
      </section>
      <div class="header-right">
      <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
        <span class="toggle-track" :class="{ on: theme === 'dark' }">
          <span class="toggle-thumb" />
        </span>
        <span class="toggle-label">{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</span>
      </button>
      <section class="hero-count">
        <span>{{ showAllSkins ? totalSkins : filteredHeroes.length }}</span>
        <b>{{ showAllSkins ? 'SKINS' : 'HEROES' }}</b>
      </section>
      </div>
    </header>

    <section class="gallery-tools">
      <div class="search-box">
        <span>SEARCH</span>
        <input v-model.trim="keyword" type="text" placeholder="搜索英雄名称">
      </div>
      <nav ref="roleNavRef" class="role-tabs" aria-label="英雄分路筛选">
        <span class="role-pill" :style="pillStyle"></span>
        <button
          v-for="role in roleOptions"
          :key="role.value"
          :ref="el => roleBtnRefs[role.value] = el"
          :class="{ active: activeRole === role.value }"
          @click="selectRole(role.value)"
        >
          {{ role.label }}
        </button>
      </nav>
      <button class="skin-toggle" :class="{ active: showAllSkins }" @click="showAllSkins = !showAllSkins">
        {{ showAllSkins ? '全部皮肤' : '仅原皮' }}
      </button>
    </section>

    <section v-if="loading" class="loading-panel">
      <div class="loading-spinner" />
      <span>正在整理英雄立绘...</span>
    </section>

    <section v-else :key="`${activeRole}-${keyword}-${showAllSkins}`" class="gallery-grid">
      <template v-if="showAllSkins">
        <template v-for="hero in filteredHeroes" :key="hero.heroId">
          <button
            v-for="skin in hero._skins"
            :key="`${hero.heroId}-${skin}`"
            class="poster-card"
            @click="openHero(hero); selectedSkin = skin"
          >
            <img
              :src="getHeroPoster(hero, skin)"
              :alt="`${hero.heroName} 皮肤${skin}`"
              :style="getPosterStyle(hero)"
              @error="onPosterGridError($event, hero, skin - 1)"
            >
            <div class="poster-shade" />
            <div class="poster-meta">
              <span>{{ hero.heroName }} · {{ hero._role || '英雄' }}</span>
              <strong>{{ hero._skinNames[skin - 1] || hero.heroName }}</strong>
              <div class="poster-sub">
                <b class="skin-num">{{ skin }}</b>
                <b v-if="hero._skinQualities[skin - 1]" class="skin-quality">{{ hero._skinQualities[skin - 1] }}</b>
                <em v-if="hero._skinDates[skin - 1]">{{ hero._skinDates[skin - 1] }}</em>
                <em v-else-if="hero._releaseTime">{{ hero._releaseTime }}</em>
              </div>
            </div>
          </button>
        </template>
      </template>
      <template v-else>
        <button
          v-for="hero in filteredHeroes"
          :key="`${hero.heroId}-${hero.heroName}`"
          class="poster-card"
          @click="openHero(hero)"
        >
          <img
            :src="getHeroPoster(hero, 1)"
            :alt="hero.heroName"
            :style="getPosterStyle(hero)"
            @load="onPosterLoad($event)"
            @error="onPosterError($event, hero)"
          >
          <div class="poster-shade" />
          <div class="poster-meta">
            <span>{{ hero._role || '英雄' }}</span>
            <strong>{{ hero._skinNames[0] || hero.heroName }}</strong>
            <div class="poster-sub">
              <em v-if="hero._skinDates[0]">{{ hero._skinDates[0] }}</em>
              <em v-else-if="hero._releaseTime">{{ hero._releaseTime }}</em>
            </div>
          </div>
        </button>
      </template>
    </section>

    <el-dialog
      v-model="detailOpen"
      width="min(1180px, calc(100vw - 28px))"
      destroy-on-close
      class="gallery-dialog"
      :show-close="false"
    >
      <template v-if="selectedHero">
        <section class="detail-shell">
          <div class="art-stage">
            <img
              :key="currentArtUrl"
              :src="currentArtUrl"
              :alt="`${selectedHero.heroName} 原画`"
              @load="artMissing = false"
              @error="onArtError"
            >
            <div v-if="artMissing" class="art-missing-state">
              <strong>暂无原画资源</strong>
              <span>{{ selectedHero.heroName }} 的 bigskin / mobileskin 官方静态图暂未返回有效图片</span>
            </div>
            <div class="art-gradient" />
            <button class="close-btn" @click="detailOpen = false">关闭</button>
            <div class="art-caption">
              <small>BIGSKIN ORIGINAL ART</small>
              <h2>{{ selectedHero.heroName }}</h2>
              <p>{{ selectedHero._role || '王者荣耀英雄' }} · 皮肤 {{ selectedSkin }}</p>
            </div>
          </div>

          <aside class="skin-panel">
            <div class="panel-title">
              <span>皮肤原画接口</span>
              <b>{{ selectedHero.heroId }}</b>
            </div>
            <div class="url-box">{{ currentArtUrl }}</div>
            <div class="skin-grid">
              <button
                v-for="skin in skinSlots"
                :key="skin"
                :class="{ active: selectedSkin === skin }"
                @click="selectSkin(skin)"
              >
                <img
                  :src="getHeroPoster(selectedHero, skin)"
                  :alt="`${selectedHero.heroName} 皮肤 ${skin}`"
                  @error="hideBrokenThumb($event, selectedHero, skin - 1)"
                >
                <span>{{ skin }}</span>
              </button>
            </div>
            <div class="detail-note">
              <b>展示规则</b>
              <span>列表使用 `mobileskin` 竖版长方形立绘；详情页使用 `bigskin` 横版原画。部分英雄皮肤编号不存在时会自动隐藏缩略图。</span>
            </div>
          </aside>
        </section>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { HERO_CATALOG } from '../data/heroData'
import { MANUAL_SKIN_DATA } from '../data/skinData'
import { getTheme, setTheme } from '../utils/theme'

const roleOptions = [
  { label: '全部', value: 'all' },
  { label: '对抗路', value: '对抗路' },
  { label: '打野', value: '打野' },
  { label: '中路', value: '中路' },
  { label: '发育路', value: '发育路' },
  { label: '游走', value: '游走' },
]

const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))
const loading = ref(false)
const heroes = ref([])
const keyword = ref('')
const activeRole = ref('all')
const roleNavRef = ref(null)
const roleBtnRefs = {}
const pillStyle = ref({})
const detailOpen = ref(false)
const selectedHero = ref(null)
const selectedSkin = ref(1)
const artFallback = ref('')
const artMissing = ref(false)

const showAllSkins = ref(true)

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
    left: (btnRect.left - navRect.left) + 'px',
    width: btnRect.width + 'px',
  }
}

const skinSlots = Array.from({ length: 12 }, (_, i) => i + 1)
const noArtHeroIds = new Set([188])
const maxSkins = 12
const heroSkinMap = ref(new Map())

const totalSkins = computed(() => filteredHeroes.value.reduce((s, h) => s + (h._skins?.length || 1), 0))

const filteredHeroes = computed(() => {
  const query = keyword.value.toLowerCase()
  return heroes.value.filter(hero => {
    const matchRole = activeRole.value === 'all' || hero._role === activeRole.value
    const matchKeyword = !query || hero.heroName.toLowerCase().includes(query)
    return matchRole && matchKeyword
  })
})

const currentArtUrl = computed(() => {
  if (!selectedHero.value) return ''
  if (artFallback.value) return artFallback.value
  return getHeroBigSkin(selectedHero.value, selectedSkin.value)
})

function getHeroImg(hero) {
  return hero?.heroIcon || (hero?.heroId ? `https://res.edata.qq.com/sgame/static/images/hero/${hero.heroId}.jpg` : '')
}

function getHeroPoster(hero, skin = 1) {
  return hero?.heroId ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-mobileskin-${skin}.jpg` : getHeroImg(hero)
}

function getHeroBigSkin(hero, skin = 1) {
  return hero?.heroId ? `https://game.gtimg.cn/images/yxzj/img201606/skin/hero-info/${hero.heroId}/${hero.heroId}-bigskin-${skin}.jpg` : getHeroImg(hero)
}

const posterFocus = {
  105: { x: 48, y: 17, scale: 1.06 },
  107: { x: 49, y: 15, scale: 1.05 },
  111: { x: 51, y: 13, scale: 1.03 },
  123: { x: 48, y: 16, scale: 1.05 },
  131: { x: 49, y: 13, scale: 1.03 },
  132: { x: 50, y: 14, scale: 1.04 },
  140: { x: 48, y: 16, scale: 1.05 },
  141: { x: 50, y: 13, scale: 1.03 },
  146: { x: 51, y: 13, scale: 1.03 },
  150: { x: 49, y: 15, scale: 1.04 },
  157: { x: 50, y: 13, scale: 1.03 },
  190: { x: 50, y: 13, scale: 1.03 },
  199: { x: 50, y: 12, scale: 1.02 },
  502: { x: 49, y: 16, scale: 1.05 },
  508: { x: 51, y: 13, scale: 1.03 },
  523: { x: 50, y: 12, scale: 1.02 },
  531: { x: 50, y: 14, scale: 1.03 },
  542: { x: 50, y: 15, scale: 1.05 },
}

const roleFocus = {
  '对抗路': { x: 50, y: 16, scale: 1.04 },
  '打野': { x: 50, y: 17, scale: 1.05 },
  '中路': { x: 50, y: 15, scale: 1.03 },
  '发育路': { x: 50, y: 14, scale: 1.03 },
  '游走': { x: 50, y: 17, scale: 1.05 },
}

function getPosterStyle(hero) {
  const focus = posterFocus[hero?.heroId] || roleFocus[hero?._role] || { x: 50, y: 15, scale: 1.04 }
  return {
    '--poster-x': `${focus.x}%`,
    '--poster-y': `${focus.y}%`,
    '--poster-scale': focus.scale,
  }
}

function onPosterError(event, hero) {
  const img = event.target
  if (img.dataset.fallback === 'bigskin') {
    img.closest('.poster-card')?.classList.add('poster-missing')
    return
  }
  img.dataset.fallback = 'bigskin'
  img.src = getHeroBigSkin(hero, 1)
  img.classList.add('use-bigskin')
}

function onPosterLoad(event) {
  const img = event.target
  if (img.naturalWidth < 260 || img.naturalHeight < 260) {
    img.closest('.poster-card')?.classList.add('poster-missing')
  }
}

function hideBrokenThumb(event, hero, skinIdx) {
  const img = event.target
  // 尝试 zlkdatasys 海报图作为 fallback
  const posterUrl = hero?._skinPosterUrls?.[skinIdx]
  if (posterUrl && !img.dataset.fallback) {
    img.dataset.fallback = 'zlk'
    img.src = 'https:' + posterUrl
    return
  }
  img.closest('button')?.classList.add('thumb-missing')
}

function onPosterGridError(event, hero, skinIdx) {
  const img = event.target
  // 尝试 zlkdatasys 海报图作为 fallback
  const posterUrl = hero?._skinPosterUrls?.[skinIdx]
  if (posterUrl && !img.dataset.fallback) {
    img.dataset.fallback = 'zlk'
    img.src = 'https:' + posterUrl
    return
  }
  img.closest('.poster-card')?.classList.add('poster-missing')
}

function selectSkin(skin) {
  selectedSkin.value = skin
  artFallback.value = ''
  artMissing.value = false
}

function onArtError(event) {
  if (!selectedHero.value) return
  if (!artFallback.value) {
    artFallback.value = getHeroPoster(selectedHero.value, selectedSkin.value)
    return
  }
  artMissing.value = true
  event.target.removeAttribute('src')
}

function openHero(hero) {
  selectedHero.value = hero
  selectedSkin.value = 1
  artFallback.value = ''
  artMissing.value = false
  detailOpen.value = true
}

const yuanLiuIds = new Set([581, 582, 583, 584, 585])

function mergeHeroes(apiList) {
  const roleMap = new Map(HERO_CATALOG.map(hero => [hero.heroId, hero.role]))
  const seen = new Set()
  // 收集元流之子各职业的角色
  const yuanLiuRoles = []
  for (const h of apiList) {
    if (yuanLiuIds.has(Number(h.heroId))) {
      const r = h._role || h.role || h.mainPosition || h.position || ''
      if (r) yuanLiuRoles.push(r)
    }
  }
  return [...apiList, ...HERO_CATALOG]
    .filter(hero => {
      const id = Number(hero.heroId)
      const key = hero.heroId || hero.heroName
      if (!key || seen.has(key) || noArtHeroIds.has(id)) return false
      // 元流之子：只保留 581，跳过 582-585
      if (id !== 581 && yuanLiuIds.has(id)) return false
      seen.add(key)
      return true
    })
    .map(hero => {
      const id = Number(hero.heroId)
      const skinInfo = heroSkinMap.value.get(id)
      const skinNames = skinInfo?.names || []
      const mapped = {
        ...hero,
        _role: hero._role || hero.role || hero.mainPosition || hero.position || roleMap.get(hero.heroId) || '',
        _skins: hero._skins || Array.from({ length: Math.max(skinNames.length, 1) }, (_, i) => i + 1),
        _skinNames: skinNames,
        _skinDates: skinInfo?.skinDates || [],
        _skinQualities: skinInfo?.skinQualities || [],
        _skinPosterUrls: skinInfo?.skinPosterUrls || [],
        _releaseTime: skinInfo?.time || '',
      }
      // 元流之子：合并名称和角色
      if (id === 581) {
        mapped.heroName = '元流之子'
        mapped._role = yuanLiuRoles.length ? [...new Set(yuanLiuRoles)].join('/') : mapped._role
      }
      return mapped
    })
    .sort((a, b) => {
      const getMaxDate = (hero) => {
        const dates = (hero._skinDates || []).filter(Boolean)
        return dates.length ? dates.sort().pop() : hero._releaseTime || ''
      }
      const da = getMaxDate(a)
      const db = getMaxDate(b)
      if (da && db) return db.localeCompare(da)
      if (da) return -1
      if (db) return 1
      return roleOptions.findIndex(role => role.value === (a._role || 'all')) - roleOptions.findIndex(role => role.value === (b._role || 'all')) || a.heroId - b.heroId
    })
}

async function loadHeroes() {
  loading.value = true
  let apiList = []
  try {
    const [heroRes, skinRes, detailRes] = await Promise.all([
      fetch('/api/query/hero/top?sort=pick&limit=200'),
      fetch('/skin-api/web201605/js/herolist.json'),
      fetch('/skin-api/zlkdatasys/data_zlk_xpflby.json'),
    ])
    const body = await heroRes.json()
    apiList = Array.isArray(body?.data?.data) ? body.data.data : Array.isArray(body?.data) ? body.data : []

    // herolist: 英雄皮肤名列表 + 英雄上线时间
    const skinData = await skinRes.json()
    const heroNameToId = new Map()
    const map = new Map()
    for (const item of skinData) {
      const id = Number(item.ename)
      const name = item.cname || ''
      const names = (item.skin_name || '').split('|').filter(Boolean)
      map.set(id, { names, time: item.time || '', skinDates: [], skinQualities: [], skinPosterUrls: [] })
      if (name) heroNameToId.set(name, id)
    }

    // 皮肤详情: 解析每款皮肤的上线日期
    try {
      const detailData = await detailRes.json()
      const skins = detailData.pcblzlby_c6 || []
      for (const s of skins) {
        const fullName = s.pcblzlbybt_d3 || ''
        const dateRaw = s.lbyrq_e5 || s.sxsj_24 || ''
        if (!fullName || !dateRaw) continue
        // 格式: "皮肤名-英雄名"，取最后一个 "-"
        const lastDash = fullName.lastIndexOf('-')
        if (lastDash < 0) continue
        const skinPart = fullName.substring(0, lastDash)
        const heroPart = fullName.substring(lastDash + 1)
        // 找到英雄
        const heroId = heroNameToId.get(heroPart)
        if (!heroId) continue
        const heroInfo = map.get(heroId)
        if (!heroInfo) continue
        // 在皮肤名列表中找到对应索引
        const skinIdx = heroInfo.names.indexOf(skinPart)
        if (skinIdx >= 0) {
          heroInfo.skinDates[skinIdx] = dateRaw.replace(/(\d{4})(\d{2})(\d{2})/, '$1-$2-$3')
        }
      }
    } catch { /* 皮肤详情接口失败不影响主流程 */ }

    // 手动数据: 补充 API 缺失的皮肤日期和品质信息
    for (const [key, info] of Object.entries(MANUAL_SKIN_DATA)) {
      const [heroName, skinName] = key.split('|')
      const heroId = heroNameToId.get(heroName)
      if (!heroId) continue
      const heroInfo = map.get(heroId)
      if (!heroInfo) continue
      const skinIdx = heroInfo.names.indexOf(skinName)
      if (skinIdx >= 0) {
        // 仅当 API 没有日期时才用手动数据补充
        if (!heroInfo.skinDates[skinIdx] && info.date) {
          heroInfo.skinDates[skinIdx] = info.date
        }
        // 存储品质信息
        if (!heroInfo.skinQualities) heroInfo.skinQualities = []
        if (info.quality) heroInfo.skinQualities[skinIdx] = info.quality
        // 存储 zlkdatasys 海报图 URL
        if (info.posterUrl) heroInfo.skinPosterUrls[skinIdx] = info.posterUrl
      }
    }

    heroSkinMap.value = map
  } catch {
    apiList = []
  } finally {
    heroes.value = mergeHeroes(apiList)
    loading.value = false
  }
}

onMounted(() => {
  loadHeroes()
  nextTick(updatePill)
  window.addEventListener('resize', updatePill)
})
onUnmounted(() => {
  window.removeEventListener('resize', updatePill)
})
</script>

<style scoped>
.gallery-broadcast {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-line: rgba(0, 0, 0, 0.18);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  position: relative;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 14px 18px calc(78px + env(safe-area-inset-bottom));
  overflow-x: hidden;
  color: var(--mono-ink);
  background:
    linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)),
    #f8f5ec;
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.gallery-broadcast,
.gallery-broadcast * {
  box-sizing: border-box;
}

.gallery-header,
.gallery-tools,
.gallery-grid,
.loading-panel {
  position: relative;
  z-index: 1;
  width: min(1680px, calc(100vw - 104px));
  min-width: 0;
  margin-inline: auto;
}

.gallery-header {
  min-height: 104px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 16px;
  border: 1px solid var(--mono-line);
  background: var(--mono-panel);
}

.brand-block {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.brand-block small {
  display: block;
  margin-bottom: 2px;
  color: var(--mono-dim);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 1.8px;
}

.brand-block h1 {
  margin: 0;
  font-size: clamp(24px, 2.6vw, 42px);
  font-weight: 950;
  letter-spacing: .5px;
}

.hero-count {
  min-width: 116px;
  padding: 10px 14px;
  text-align: center;
  border: 1px solid var(--mono-line);
  background: rgba(0, 0, 0, .03);
}

.hero-count span,
.hero-count b {
  display: block;
}

.hero-count span {
  color: var(--mono-ink);
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: 42px;
  line-height: .95;
}

.hero-count b {
  margin-top: 4px;
  color: var(--mono-dim);
  font-size: 10px;
  letter-spacing: 3px;
}

.gallery-tools {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 8px;
  align-items: stretch;
}

.search-box,
.loading-panel {
  border: 1px solid var(--mono-line);
  background: var(--mono-panel);
}

.role-tabs {
  background: transparent;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
}

.search-box span {
  color: var(--mono-dim);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
}

.search-box input {
  flex: 1;
  min-width: 0;
  height: 48px;
  border: 0;
  outline: 0;
  color: var(--mono-ink);
  background: transparent;
  font-size: 14px;
  font-weight: 800;
}

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
  height: calc(100% - 8px);
  background: #1a1a1a;
  border-radius: 10px;
  transition: left .36s cubic-bezier(.4,0,.2,1), width .36s cubic-bezier(.4,0,.2,1);
  z-index: 0;
  pointer-events: none;
}

.role-tabs button {
  position: relative;
  z-index: 1;
  flex: 1 0 auto;
  min-width: 86px;
  padding: 0 14px;
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

.skin-toggle {
  flex-shrink: 0;
  padding: 0 14px;
  height: 48px;
  border: 1px solid var(--mono-line);
  color: var(--mono-soft);
  background: rgba(255, 255, 255, .6);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  transition: all .15s;
}
.skin-toggle:hover { color: var(--mono-ink); background: rgba(255, 255, 255, .9); }
.skin-toggle.active { color: #f8f5ec; background: var(--mono-ink); border-color: var(--mono-ink); }

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
  margin-top: 8px;
  animation: grid-fade .3s ease both;
}

@keyframes grid-fade {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.poster-card {
  position: relative;
  min-width: 0;
  aspect-ratio: 9 / 16;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--mono-line);
  color: var(--mono-ink);
  background: rgba(0, 0, 0, .03);
  cursor: pointer;
}

.poster-card::before {
  content: "KPL";
  position: absolute;
  z-index: 2;
  top: 12px;
  left: 10px;
  padding: 2px 6px;
  border: 1px solid rgba(0, 0, 0, .12);
  color: rgba(255, 255, 255, .78);
  background: rgba(0, 0, 0, .22);
  font-size: 9px;
  font-weight: 950;
  letter-spacing: 2px;
}

.poster-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: var(--poster-x, 50%) var(--poster-y, 15%);
  filter: saturate(.94) contrast(1.05);
  transition: transform .35s ease, filter .35s ease;
}

.poster-card:hover img {
  filter: saturate(1.08) contrast(1.07);
  transform: scale(1.04);
}

.poster-card.poster-missing {
  display: none;
}

.poster-card img.use-bigskin {
  object-position: 50% 18%;
  transform: scale(1.2);
}

.poster-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 44%, rgba(26, 26, 26, .75) 100%);
  pointer-events: none;
}

.poster-meta {
  position: absolute;
  z-index: 2;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px;
  color: #fff;
  text-align: left;
}

.poster-meta span,
.poster-meta strong {
  display: block;
}

.poster-meta span {
  opacity: .72;
  font-size: 10px;
  font-weight: 800;
}

.poster-meta strong {
  margin-top: 3px;
  font-size: 17px;
  line-height: 1.2;
}
.poster-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}
.poster-sub .skin-num {
  min-width: 22px;
  padding: 1px 5px;
  border-radius: 3px;
  background: rgba(255, 255, 255, .22);
  color: #fff;
  font-size: 10px;
  font-weight: 900;
  text-align: center;
}
.poster-sub .skin-quality {
  padding: 1px 5px;
  border-radius: 3px;
  background: rgba(26, 26, 26, .35);
  color: #fff;
  font-size: 9px;
  font-weight: 800;
}
.poster-sub em {
  font-style: normal;
  opacity: .55;
  font-size: 10px;
  font-weight: 700;
}

.loading-panel {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 360px;
  margin-top: 8px;
  color: var(--mono-dim);
}

.loading-spinner {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(0, 0, 0, .1);
  border-top-color: var(--mono-ink);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}

.detail-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  min-height: min(72vh, 720px);
  overflow: hidden;
  background: var(--mono-panel);
}

.art-stage {
  position: relative;
  min-height: 520px;
  overflow: hidden;
  background: #101625;
}

.art-stage img {
  width: 100%;
  height: 100%;
  min-height: 520px;
  object-fit: contain;
}

.art-missing-state {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 28px;
  color: rgba(255, 255, 255, .86);
  text-align: center;
  background:
    radial-gradient(circle at 50% 34%, rgba(26, 26, 26, .32), transparent 32%),
    linear-gradient(180deg, #1a1a1a, #101625);
}

.art-missing-state strong {
  font-size: 28px;
  font-weight: 950;
}

.art-missing-state span {
  max-width: 420px;
  color: rgba(255, 255, 255, .58);
  font-size: 13px;
  line-height: 1.6;
}

.art-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(16, 22, 37, .1), rgba(16, 22, 37, .12) 44%, rgba(16, 22, 37, .86));
  pointer-events: none;
}

.close-btn {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 3;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, .42);
  color: #fff;
  background: rgba(16, 22, 37, .32);
  cursor: pointer;
}

.art-caption {
  position: absolute;
  left: 24px;
  right: 24px;
  bottom: 22px;
  z-index: 2;
  color: #fff;
}

.art-caption small {
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 3px;
  opacity: .72;
}

.art-caption h2 {
  margin: 5px 0 2px;
  font-size: clamp(34px, 5vw, 72px);
  line-height: .95;
}

.art-caption p {
  margin: 0;
  opacity: .72;
}

.skin-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-left: 1px solid var(--mono-line);
  background: var(--mono-panel);
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--mono-dim);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
}

.panel-title b {
  color: var(--mono-ink);
}

.url-box {
  padding: 10px;
  overflow-wrap: anywhere;
  color: var(--mono-soft);
  background: rgba(0, 0, 0, .04);
  font-family: Consolas, monospace;
  font-size: 11px;
  line-height: 1.45;
}

.skin-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  overflow-y: auto;
}

.skin-grid button {
  position: relative;
  min-width: 0;
  aspect-ratio: 9 / 16;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--mono-line);
  background: rgba(0, 0, 0, .03);
  cursor: pointer;
}

.skin-grid button.active {
  border-color: var(--mono-ink);
  box-shadow: 0 0 0 2px rgba(26, 26, 26, .15);
}

.skin-grid button.thumb-missing {
  display: none;
}

.skin-grid img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.skin-grid span {
  position: absolute;
  right: 5px;
  bottom: 5px;
  min-width: 20px;
  padding: 2px 5px;
  color: #fff;
  background: rgba(26, 26, 26, .45);
  font-size: 10px;
  font-weight: 900;
}

.detail-note {
  margin-top: auto;
  padding: 12px;
  border-left: 4px solid var(--mono-ink);
  background: rgba(0, 0, 0, .04);
}

.detail-note b,
.detail-note span {
  display: block;
}

.detail-note b {
  color: var(--mono-ink);
  font-size: 13px;
}

.detail-note span {
  margin-top: 5px;
  color: var(--mono-soft);
  font-size: 12px;
  line-height: 1.55;
}

:global(.gallery-dialog.el-dialog),
:global(.gallery-dialog .el-dialog) {
  padding: 0 !important;
  border: 1px solid var(--mono-line) !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: 0 28px 90px rgba(0, 0, 0, .18) !important;
}

:global(.gallery-dialog .el-dialog__header) {
  display: none !important;
}

:global(.gallery-dialog .el-dialog__body) {
  padding: 0 !important;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── theme toggle ── */
.theme-toggle {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 4px;
  border: 0;
  background: none;
  cursor: pointer;
}
.toggle-label {
  font-size: 8px;
  font-weight: 950;
  letter-spacing: 2px;
  color: var(--mono-dim);
}
.toggle-track {
  position: relative;
  width: 40px;
  height: 22px;
  border-radius: 12px;
  background: rgba(0, 0, 0, .12);
  transition: background .25s;
}
.toggle-track.on { background: var(--mono-ink); }
.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .2);
  transition: transform .25s;
}
.toggle-track.on .toggle-thumb { transform: translateX(18px); }

/* ── dark theme ── */
.gallery-broadcast.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-line: rgba(255, 255, 255, 0.12);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  color: var(--mono-ink);
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.gallery-broadcast.theme-dark .gallery-header {
  background: rgba(18, 18, 18, 0.92);
}

.gallery-broadcast.theme-dark .hero-count {
  background: rgba(255, 255, 255, 0.04);
}

.gallery-broadcast.theme-dark .search-box,
.gallery-broadcast.theme-dark .loading-panel {
  background: rgba(18, 18, 18, 0.92);
}

.gallery-broadcast.theme-dark .search-box input {
  color: var(--mono-ink);
}

.gallery-broadcast.theme-dark .search-box input::placeholder {
  color: var(--mono-dim);
}

.gallery-broadcast.theme-dark .role-tabs button {
  color: var(--mono-soft);
  background: transparent;
}

.gallery-broadcast.theme-dark .role-tabs button:hover {
  color: var(--mono-ink);
}

.gallery-broadcast.theme-dark .role-tabs button.active {
  color: #0a0a0a;
}

.gallery-broadcast.theme-dark .role-pill {
  background: var(--mono-ink);
}

.gallery-broadcast.theme-dark .skin-toggle {
  color: var(--mono-soft);
  background: rgba(255, 255, 255, 0.06);
}

.gallery-broadcast.theme-dark .skin-toggle:hover {
  color: var(--mono-ink);
  background: rgba(255, 255, 255, 0.12);
}

.gallery-broadcast.theme-dark .skin-toggle.active {
  color: #0a0a0a;
  background: var(--mono-ink);
  border-color: var(--mono-ink);
}

.gallery-broadcast.theme-dark .poster-card {
  background: rgba(255, 255, 255, 0.03);
}

.gallery-broadcast.theme-dark .poster-card::before {
  border-color: rgba(255, 255, 255, 0.15);
}

.gallery-broadcast.theme-dark .poster-shade {
  background: linear-gradient(180deg, transparent 44%, rgba(0, 0, 0, 0.8) 100%);
}

.gallery-broadcast.theme-dark .loading-spinner {
  border-color: rgba(255, 255, 255, 0.12);
  border-top-color: var(--mono-ink);
}

.gallery-broadcast.theme-dark .detail-shell {
  background: var(--mono-panel);
}

.gallery-broadcast.theme-dark .art-stage {
  background: #080c14;
}

.gallery-broadcast.theme-dark .art-missing-state {
  background:
    radial-gradient(circle at 50% 34%, rgba(26, 26, 26, 0.32), transparent 32%),
    linear-gradient(180deg, #0a0a0a, #080c14);
}

.gallery-broadcast.theme-dark .art-gradient {
  background: linear-gradient(180deg, rgba(8, 12, 20, 0.1), rgba(8, 12, 20, 0.12) 44%, rgba(8, 12, 20, 0.86));
}

.gallery-broadcast.theme-dark .close-btn {
  border-color: rgba(255, 255, 255, 0.3);
  background: rgba(8, 12, 20, 0.4);
}

.gallery-broadcast.theme-dark .skin-panel {
  background: var(--mono-panel);
}

.gallery-broadcast.theme-dark .url-box {
  color: var(--mono-soft);
  background: rgba(255, 255, 255, 0.04);
}

.gallery-broadcast.theme-dark .skin-grid button {
  background: rgba(255, 255, 255, 0.03);
}

.gallery-broadcast.theme-dark .skin-grid button.active {
  border-color: var(--mono-ink);
  box-shadow: 0 0 0 2px rgba(232, 232, 232, 0.15);
}

.gallery-broadcast.theme-dark .detail-note {
  background: rgba(255, 255, 255, 0.04);
}

/* ── el-select dark ── */
.gallery-broadcast.theme-dark :deep(.el-select .el-input__wrapper),
.gallery-broadcast.theme-dark :deep(.el-select .el-input__inner) {
  background: rgba(18, 18, 18, 0.92) !important;
  color: var(--mono-ink) !important;
  border-color: var(--mono-line) !important;
  box-shadow: none !important;
}

.gallery-broadcast.theme-dark :deep(.el-select .el-input__inner::placeholder) {
  color: var(--mono-dim) !important;
}

.gallery-broadcast.theme-dark :deep(.el-select-dropdown) {
  background: rgba(18, 18, 18, 0.96) !important;
  border-color: var(--mono-line) !important;
}

.gallery-broadcast.theme-dark :deep(.el-select-dropdown__item) {
  color: var(--mono-soft) !important;
}

.gallery-broadcast.theme-dark :deep(.el-select-dropdown__item.hover),
.gallery-broadcast.theme-dark :deep(.el-select-dropdown__item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: var(--mono-ink) !important;
}

.gallery-broadcast.theme-dark :deep(.el-select-dropdown__item.selected) {
  color: var(--mono-ink) !important;
  font-weight: 900 !important;
}

/* ── dialog dark ── */
.gallery-broadcast.theme-dark :global(.gallery-dialog.el-dialog) {
  background: rgba(18, 18, 18, 0.96) !important;
  border-color: var(--mono-line) !important;
  box-shadow: 0 28px 90px rgba(0, 0, 0, 0.5) !important;
}

@media (max-width: 1023px) {
  .gallery-tools {
    grid-template-columns: 1fr;
  }

  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  }

  .detail-shell {
    grid-template-columns: 1fr;
  }

  .skin-panel {
    border-left: 0;
    border-top: 1px solid var(--mono-line);
  }

  .skin-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .gallery-broadcast {
    min-height: 100vh;
    margin-left: 0;
    padding: 8px 8px calc(74px + env(safe-area-inset-bottom));
    overflow-y: auto;
  }

  .gallery-header,
  .gallery-tools,
  .gallery-grid,
  .loading-panel {
    width: 100%;
  }

  .gallery-header {
    min-height: 84px;
    padding: 8px;
  }

  .brand-block small {
    display: none;
  }

  .brand-block h1 {
    font-size: 20px;
  }

  .hero-count {
    min-width: 78px;
    padding: 8px 10px;
  }

  .hero-count span {
    font-size: 30px;
  }

  .search-box input {
    height: 42px;
  }

  .role-tabs button {
    flex: 0 0 76px;
    min-width: 76px;
    height: 42px;
    padding: 0 10px;
  }

  .gallery-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .poster-card::before {
    top: 8px;
    left: 8px;
    font-size: 8px;
  }

  .poster-meta {
    padding: 7px 8px;
  }

  .poster-meta span {
    font-size: 9px;
  }

  .poster-meta strong {
    font-size: 13px;
  }

  .art-stage,
  .art-stage img {
    min-height: 300px;
  }

  .art-caption {
    left: 14px;
    right: 14px;
    bottom: 14px;
  }

  .skin-panel {
    max-height: 44vh;
    padding: 12px;
    overflow-y: auto;
  }

  .skin-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
