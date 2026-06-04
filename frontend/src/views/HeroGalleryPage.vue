<template>
  <main class="gallery-broadcast">
    <div class="aurora aurora-left" />
    <div class="aurora aurora-right" />

    <header class="gallery-header">
      <section class="brand-block">
        <div>
          <small>KPL HERO ARCHIVE</small>
          <h1>英雄原画画廊</h1>
        </div>
      </section>
      <section class="hero-count">
        <span>{{ filteredHeroes.length }}</span>
        <b>HEROES</b>
      </section>
    </header>

    <section class="gallery-tools">
      <div class="search-box">
        <span>SEARCH</span>
        <input v-model.trim="keyword" type="text" placeholder="搜索英雄名称">
      </div>
      <nav class="role-tabs" aria-label="英雄分路筛选">
        <button
          v-for="role in roleOptions"
          :key="role.value"
          :class="{ active: activeRole === role.value }"
          @click="activeRole = role.value"
        >
          {{ role.label }}
        </button>
      </nav>
    </section>

    <section v-if="loading" class="loading-panel">
      <div class="loading-spinner" />
      <span>正在整理英雄立绘...</span>
    </section>

    <section v-else class="gallery-grid">
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
          <strong>{{ hero.heroName }}</strong>
        </div>
      </button>
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
                  @error="hideBrokenThumb"
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
import { computed, onMounted, ref } from 'vue'
import { HERO_CATALOG } from '../data/heroData'

const roleOptions = [
  { label: '全部', value: 'all' },
  { label: '对抗路', value: '对抗路' },
  { label: '打野', value: '打野' },
  { label: '中路', value: '中路' },
  { label: '发育路', value: '发育路' },
  { label: '游走', value: '游走' },
]

const loading = ref(false)
const heroes = ref([])
const keyword = ref('')
const activeRole = ref('all')
const detailOpen = ref(false)
const selectedHero = ref(null)
const selectedSkin = ref(1)
const artFallback = ref('')
const artMissing = ref(false)

const skinSlots = Array.from({ length: 12 }, (_, i) => i + 1)
const noArtHeroIds = new Set([188])

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

function hideBrokenThumb(event) {
  event.target.closest('button')?.classList.add('thumb-missing')
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
      const mapped = {
        ...hero,
        _role: hero._role || hero.role || hero.mainPosition || hero.position || roleMap.get(hero.heroId) || '',
      }
      // 元流之子：合并名称和角色
      if (id === 581) {
        mapped.heroName = '元流之子'
        mapped._role = yuanLiuRoles.length ? [...new Set(yuanLiuRoles)].join('/') : mapped._role
      }
      return mapped
    })
    .sort((a, b) => roleOptions.findIndex(role => role.value === (a._role || 'all')) - roleOptions.findIndex(role => role.value === (b._role || 'all')) || a.heroId - b.heroId)
}

async function loadHeroes() {
  loading.value = true
  let apiList = []
  try {
    const response = await fetch('/api/query/hero/top?sort=pick&limit=200')
    const body = await response.json()
    apiList = Array.isArray(body?.data?.data) ? body.data.data : Array.isArray(body?.data) ? body.data : []
  } catch {
    apiList = []
  } finally {
    heroes.value = mergeHeroes(apiList)
    loading.value = false
  }
}

onMounted(loadHeroes)
</script>

<style scoped>
.gallery-broadcast {
  --blue: #6752d7;
  --blue-bright: #8e7cf3;
  --red: #d25a78;
  --cyan: #35e1d3;
  --ink: #16202c;
  position: relative;
  min-height: 100vh;
  margin-left: 67.5px;
  padding: 14px 18px calc(78px + env(safe-area-inset-bottom));
  overflow-x: hidden;
  color: var(--ink);
  background:
    linear-gradient(120deg, rgba(133, 110, 255, .18), transparent 28%),
    linear-gradient(240deg, rgba(53, 225, 211, .22), transparent 30%),
    linear-gradient(180deg, #dce6f4 0%, #f4f0fa 58%, #d5c7ee 100%);
  font-family: "Microsoft YaHei UI", "PingFang SC", sans-serif;
}

.gallery-broadcast,
.gallery-broadcast * {
  box-sizing: border-box;
}

.gallery-broadcast::before {
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
  border: 1px solid rgba(255, 255, 255, .62);
  background: rgba(238, 245, 252, .7);
  backdrop-filter: blur(18px);
  box-shadow: 0 18px 55px rgba(49, 57, 92, .14), inset 0 0 46px rgba(255, 255, 255, .38);
}

.brand-block {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}


.brand-block small {
  display: block;
  margin-bottom: 2px;
  color: rgba(22, 32, 44, .46);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
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
  background: rgba(255, 255, 255, .34);
  border: 1px solid rgba(255, 255, 255, .48);
}

.hero-count span,
.hero-count b {
  display: block;
}

.hero-count span {
  color: var(--blue);
  font-family: Impact, Haettenschweiler, sans-serif;
  font-size: 42px;
  line-height: .95;
}

.hero-count b {
  margin-top: 4px;
  color: rgba(22, 32, 44, .48);
  font-size: 10px;
  letter-spacing: 3px;
}

.gallery-tools {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.search-box,
.role-tabs,
.loading-panel {
  border: 1px solid rgba(255, 255, 255, .62);
  background: rgba(245, 247, 252, .68);
  backdrop-filter: blur(14px);
  box-shadow: inset 0 0 34px rgba(255, 255, 255, .22);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
}

.search-box span {
  color: rgba(22, 32, 44, .42);
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
  color: var(--ink);
  background: transparent;
  font-size: 14px;
  font-weight: 800;
}

.role-tabs {
  display: flex;
  min-width: 0;
  overflow-x: auto;
}

.role-tabs button {
  flex: 1 0 auto;
  min-width: 86px;
  padding: 0 14px;
  border: 0;
  border-right: 1px solid rgba(61, 70, 100, .1);
  color: rgba(22, 32, 44, .62);
  background: transparent;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
}

.role-tabs button.active {
  color: #fff;
  background: var(--blue);
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.poster-card {
  position: relative;
  min-width: 0;
  aspect-ratio: 9 / 16;
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, .64);
  color: var(--ink);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .25), rgba(103, 82, 215, .18)),
    linear-gradient(135deg, transparent 50%, rgba(255, 255, 255, .3));
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .22);
  cursor: pointer;
}

.poster-card::before {
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
  background: linear-gradient(180deg, transparent 44%, rgba(70, 48, 132, .8) 100%);
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

.loading-panel {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 360px;
  margin-top: 8px;
  color: rgba(22, 32, 44, .44);
}

.loading-spinner {
  width: 34px;
  height: 34px;
  border: 3px solid rgba(103, 82, 215, .16);
  border-top-color: var(--blue);
  border-radius: 50%;
  animation: spin .8s linear infinite;
}

.detail-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  min-height: min(72vh, 720px);
  overflow: hidden;
  background: rgba(232, 238, 249, .96);
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
    radial-gradient(circle at 50% 34%, rgba(103, 82, 215, .32), transparent 32%),
    linear-gradient(180deg, #1a2136, #101625);
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
  backdrop-filter: blur(12px);
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
  border-left: 1px solid rgba(62, 72, 106, .16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, .52), rgba(230, 235, 249, .84)),
    rgba(239, 243, 251, .82);
}

.panel-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: rgba(22, 32, 44, .48);
  font-size: 10px;
  font-weight: 950;
  letter-spacing: 2px;
}

.panel-title b {
  color: var(--ink);
}

.url-box {
  padding: 10px;
  overflow-wrap: anywhere;
  color: rgba(22, 32, 44, .62);
  background: rgba(255, 255, 255, .42);
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
  border: 1px solid rgba(22, 32, 44, .12);
  background: rgba(255, 255, 255, .48);
  cursor: pointer;
}

.skin-grid button.active {
  border-color: var(--blue);
  box-shadow: 0 0 0 2px rgba(103, 82, 215, .2);
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
  background: rgba(22, 32, 44, .45);
  font-size: 10px;
  font-weight: 900;
}

.detail-note {
  margin-top: auto;
  padding: 12px;
  border-left: 4px solid var(--blue);
  background: rgba(103, 82, 215, .1);
}

.detail-note b,
.detail-note span {
  display: block;
}

.detail-note b {
  color: var(--ink);
  font-size: 13px;
}

.detail-note span {
  margin-top: 5px;
  color: rgba(22, 32, 44, .58);
  font-size: 12px;
  line-height: 1.55;
}

:global(.gallery-dialog.el-dialog),
:global(.gallery-dialog .el-dialog) {
  padding: 0 !important;
  border: 1px solid rgba(255, 255, 255, .7) !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: 0 28px 90px rgba(38, 42, 78, .28) !important;
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
    border-top: 1px solid rgba(62, 72, 106, .16);
  }

  .skin-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .gallery-broadcast {
    min-height: 100dvh;
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
