<template>
  <main class="app-shell has-sidebar rankings-console" :class="`theme-${theme}`">
    <section class="command-strip">
      <div class="brand-block">
        <div class="brand-mark">K</div>
        <div>
          <p class="eyebrow">KPL DATA AGENT</p>
          <h1>赛场数据中控</h1>
        </div>
      </div>

      <div class="status-line">
        <button class="theme-toggle" :title="theme === 'light' ? '切换暗色' : '切换亮色'" @click="theme = theme === 'light' ? 'dark' : 'light'">
          <span class="toggle-track" :class="{ on: theme === 'dark' }">
            <span class="toggle-thumb" />
          </span>
          <small>{{ theme === 'light' ? 'LIGHT' : 'DARK' }}</small>
        </button>
      </div>
    </section>

    <section class="layout-grid">
      <aside class="side-rail panel">
        <div class="section-head">
          <span>赛事选择</span>
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
          <strong>暂无数据</strong>
          <small>请选择赛事</small>
        </div>

        <div class="league-stats" v-if="currentLeague">
          <div class="league-stat-item">
            <span class="ls-val">{{ leagueMeta.matchCount }}</span>
            <span class="ls-label">比赛</span>
          </div>
          <div class="league-stat-item">
            <span class="ls-val">{{ leagueMeta.teamCount }}</span>
            <span class="ls-label">战队</span>
          </div>
          <div class="league-stat-item">
            <span class="ls-val">{{ leagueMeta.playerCount }}</span>
            <span class="ls-label">选手</span>
          </div>
        </div>

        <div class="patch-notes">
          <div class="section-head">
            <span>版本动态</span>
          </div>
          <div class="patch-list" v-if="patchNotes.length">
            <div v-for="note in patchNotes" :key="note.id"
                 class="patch-item"
                 @click="openNote(note)">
              <span class="patch-date">{{ formatPatchDate(note.date) }}</span>
              <span class="patch-title">{{ note.title }}</span>
            </div>
          </div>
          <div v-else-if="patchLoading" class="patch-skeleton">
            <div class="skel-bar" v-for="i in 4" :key="i" />
          </div>
          <div v-else class="patch-empty">暂无版本动态</div>
        </div>

        <div class="sidebar-author">
          <span class="author-name">flylegends</span>
          <span class="author-contact">交流群 791050795</span>
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
              <strong>{{ queryMode === 'honors' ? '全历史' : (currentLeague?.leagueName || selectedLeagueId || 'AUTO') }}</strong>
            </div>
          </div>

          <div class="table-container" :class="tableState">
          <div v-if="loading.query" class="table-loading">
            <div class="table-spinner"></div>
          </div>
          <el-table :key="tableRenderKey" ref="mainTableRef" class="data-table" :data="tableRows" height="360" empty-text=""
            row-class-name="clickable-row"
            @row-click="onRowClick($event)">
            <el-table-column label="#" width="60" align="center">
              <template #default="{ row, $index }">
                <span v-if="queryMode === 'ranking' && row.placement && row.placement > 0 && row.placement < 999" class="rank-badge">{{ row.placement }}</span>
                <span v-else class="rank-badge rank-n">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column v-for="col in tableColumns" :key="`${tableRenderKey}-${col.prop}`" :prop="col.prop" :label="col.label"
              :min-width="isStickySubjectCol(col.prop) ? 130 : 100"
              :align="isNameCol(col.prop) ? 'left' : 'center'"
              :class-name="isStickySubjectCol(col.prop) ? 'sticky-col' : ''">
              <template #default="{ row }" v-if="isRateCol(col.prop)">
                <div class="rate-cell">
                  <span class="rate-num">{{ row[col.prop] }}</span>
                  <div class="rate-track">
                    <div class="rate-bar" :class="{ 'win-high': parseFloat(row[col.prop]) >= 60, 'win-low': parseFloat(row[col.prop]) < 40 }" :style="{ width: parseFloat(row[col.prop]) + '%' }"></div>
                  </div>
                </div>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'honors' && col.prop === 'champion'">
                <span class="honor-gold">{{ row.champion }}</span>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'honors' && col.prop === 'runnerUp'">
                <span class="honor-silver">{{ row.runnerUp }}</span>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'equip' && col.prop === 'equipName'">
                <div class="equip-cell">
                  <img v-if="row.equipIcon" :src="row.equipIcon" class="equip-icon" />
                  <span>{{ row.equipName }}</span>
                </div>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'match' && col.prop === 'matchStageDesc'">
                <span :class="['stage-tag', stageClass(row.matchStage)]">{{ row.matchStageDesc }}</span>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'match' && col.prop === 'camp1TeamName'">
                <div class="name-cell">
                  <img v-if="row.camp1TeamIcon" :src="row.camp1TeamIcon" class="cell-icon team-icon" />
                  <span>{{ row.camp1TeamName }}</span>
                </div>
              </template>
              <template #default="{ row }" v-else-if="queryMode === 'match' && col.prop === 'camp2TeamName'">
                <div class="name-cell">
                  <img v-if="row.camp2TeamIcon" :src="row.camp2TeamIcon" class="cell-icon team-icon" />
                  <span>{{ row.camp2TeamName }}</span>
                </div>
              </template>
              <template #default="{ row }" v-else-if="col.prop === 'teamName'">
                <div class="name-cell">
                  <img v-if="row.teamIcon" :src="row.teamIcon" class="cell-icon team-icon" />
                  <span>{{ row.teamName }}</span>
                </div>
              </template>
              <template #default="{ row }" v-else-if="col.prop === 'playerName'">
                <div class="name-cell">
                  <img v-if="row.playerIcon" :src="row.playerIcon" class="cell-icon player-icon" />
                  <span>{{ row.playerName }}</span>
                </div>
              </template>
              <template #default="{ row }" v-else-if="col.prop === 'heroName'">
                <div class="name-cell">
                  <img v-if="row.heroIcon" :src="row.heroIcon" class="cell-icon hero-icon" />
                  <span>{{ row.heroName }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          </div>

        </div>

        <!-- 统一详情弹窗 -->
        <el-dialog v-model="detailVisible" :title="detailTitle" width="720" class="detail-dialog" :modal-class="'detail-overlay'" destroy-on-close>
          <template v-if="detailLoading">
            <div class="skeleton-wrap">
              <div class="skeleton-row" style="width:40%"></div>
              <div class="skeleton-row" style="width:100%;height:80px;margin-top:16px"></div>
              <div class="skeleton-row" style="width:100%;height:80px;margin-top:12px"></div>
              <div class="skeleton-row" style="width:60%;margin-top:16px"></div>
              <div class="skeleton-row" style="width:80%;margin-top:8px"></div>
            </div>
          </template>

          <!-- 比赛详情 -->
          <template v-else-if="detailType === 'match' && detailData">
            <div class="match-meta">{{ detailRow?.matchStageDesc }} · {{ formatTime(detailRow?.startTime) }}</div>
            <div v-if="detailData.battles.length" class="battle-list">
              <div v-for="(battle, bi) in detailData.battles" :key="bi" class="battle-card">
                <div class="battle-title">
                  第{{ bi + 1 }}局
                  <span :class="getPlayerMatchCamp(battle.players?.find(p => p.player?.camp === battle.battle?.winCamp)?.player, detailRow?.camp1TeamName) === 1 ? 'win-blue' : 'win-red'">
                    {{ getWinnerTeamName(battle, detailRow?.camp1TeamName, detailRow?.camp2TeamName) }} 获胜
                  </span>
                </div>
                <div class="player-grid">
                  <div v-for="pd in sortedPlayers(battle.players, detailRow?.camp1TeamName)" :key="pd.player?.playerName" :class="['player-card', getPlayerMatchCamp(pd.player, detailRow?.camp1TeamName) === 1 ? 'camp-blue' : 'camp-red']">
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
                    <div class="player-damage">
                      <span>伤害 {{ formatDamage(pd.player?.hurtToHeroTotal) }}</span>
                      <span>承伤 {{ formatDamage(pd.player?.beHurtTotal) }}</span>
                      <span v-if="pd.player?.summonerAbilityName">技能 {{ pd.player?.summonerAbilityName }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="detail-empty">暂无对局数据</div>
          </template>

          <!-- 装备详情 -->
          <template v-else-if="detailType === 'equip' && detailData">
            <div class="equip-desc" v-if="detailData.equipDescGain || detailData.equipDescFunction">
              <p v-if="detailData.equipDescGain"><b>属性：</b>{{ stripHtml(detailData.equipDescGain) }}</p>
              <p v-if="detailData.equipDescFunction"><b>被动：</b>{{ stripHtml(detailData.equipDescFunction) }}</p>
            </div>
            <div class="equip-stats-row" v-if="detailData.summary">
              <div class="equip-stat-card"><span class="stat-val">{{ detailData.summary.totalPick }}</span><span class="stat-label">总出场</span></div>
              <div class="equip-stat-card"><span class="stat-val">{{ detailData.summary.heroCount }}</span><span class="stat-label">使用英雄数</span></div>
              <div class="equip-stat-card"><span class="stat-val">{{ detailData.summary.playerCount }}</span><span class="stat-label">使用选手数</span></div>
            </div>
            <div class="equip-section" v-if="detailData.positions?.length">
              <h4>分路偏好</h4>
              <div class="position-bars">
                <div v-for="p in detailData.positions" :key="p.positionDesc" class="position-bar-row">
                  <span class="pos-label">{{ p.positionDesc || '其他' }}</span>
                  <div class="pos-track"><div class="pos-fill" :style="{ width: positionPercent(p.cnt, detailData.positions) + '%' }"></div></div>
                  <span class="pos-cnt">{{ p.cnt }}</span>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.heroes?.length">
              <h4>热门英雄</h4>
              <div class="hero-equip-list">
                <div v-for="h in detailData.heroes" :key="h.heroId" class="hero-equip-item">
                  <img :src="'https://res.edata.qq.com/sgame/static/images/hero/' + h.heroId + '.jpg'" class="hero-equip-img" />
                  <span class="hero-equip-name">{{ h.heroName }}</span>
                  <span class="hero-equip-cnt">{{ h.cnt }}次</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 战队详情 -->
          <template v-else-if="detailType === 'team' && detailData">
            <div class="detail-profile" v-if="detailData.team">
              <div v-for="t in (detailData.team.data || detailData.team)" :key="t.teamName" class="profile-card">
                <div class="profile-header">
                  <img v-if="t.teamIcon" :src="t.teamIcon" class="profile-avatar" />
                  <div>
                    <b>{{ t.teamName }}</b>
                    <small>KPL职业战队</small>
                  </div>
                </div>
                <p class="intro-text">{{ t.teamName }}本赛季共出战{{ t.battleCount }}局，胜率{{ (t.winRate * 100).toFixed(1) }}%，场均击杀{{ t.avgKill?.toFixed(1) }}，KDA {{ t.avgKda?.toFixed(2) }}。一血率{{ (t.avgFirstBlood * 100).toFixed(1) }}%，场均推塔{{ t.avgPushTower?.toFixed(1) }}，控龙率{{ (t.avgDragonControlRate * 100).toFixed(1) }}%。</p>
                <div class="stat-grid">
                  <div class="stat-item"><span class="sv">{{ t.battleCount }}</span><span class="sl">局数</span></div>
                  <div class="stat-item"><span class="sv">{{ (t.winRate * 100).toFixed(1) }}%</span><span class="sl">胜率</span></div>
                  <div class="stat-item"><span class="sv">{{ t.avgKill?.toFixed(1) }}</span><span class="sl">场均击杀</span></div>
                  <div class="stat-item"><span class="sv">{{ t.avgKda?.toFixed(2) }}</span><span class="sl">KDA</span></div>
                  <div class="stat-item"><span class="sv">{{ formatDamage(t.avgGold) }}</span><span class="sl">场均经济</span></div>
                  <div class="stat-item"><span class="sv">{{ (t.avgFirstBlood * 100).toFixed(1) }}%</span><span class="sl">一血率</span></div>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.matches?.data?.length">
              <h4>最近比赛</h4>
              <div v-for="m in detailData.matches.data.slice(0, 5)" :key="m.matchId" class="recent-match-row">
                <span :class="['stage-tag', stageClass(m.matchStage)]">{{ m.matchStageDesc }}</span>
                <span class="rm-team">{{ m.camp1TeamName }}</span>
                <span class="rm-score">{{ m.camp1Score }} : {{ m.camp2Score }}</span>
                <span class="rm-team">{{ m.camp2TeamName }}</span>
              </div>
            </div>
          </template>

          <!-- 选手详情 -->
          <template v-else-if="detailType === 'player' && detailData">
            <div class="detail-profile" v-if="detailData.player">
              <div v-for="p in (detailData.player.data || detailData.player)" :key="p.playerName" class="profile-card">
                <div class="profile-header">
                  <img v-if="p.playerIcon" :src="p.playerIcon" class="profile-avatar" />
                  <div>
                    <b>{{ p.playerName }}</b>
                    <small>{{ p.teamName }} · {{ p.positionDesc }}</small>
                  </div>
                </div>
                <p class="intro-text">{{ p.playerName }}效力于{{ p.teamName }}，司职{{ p.positionDesc }}。本赛季出场{{ p.battleCount }}局，胜率{{ (p.winRate * 100).toFixed(1) }}%，KDA {{ p.avgKda?.toFixed(2) }}，场均{{ p.avgKill?.toFixed(1) }}杀{{ p.avgDeath?.toFixed(1) }}死{{ p.avgAssist?.toFixed(1) }}助，参团率{{ p.avgParticipationRate?.toFixed(1) }}%。</p>
                <div class="stat-grid">
                  <div class="stat-item"><span class="sv">{{ p.battleCount }}</span><span class="sl">局数</span></div>
                  <div class="stat-item"><span class="sv">{{ (p.winRate * 100).toFixed(1) }}%</span><span class="sl">胜率</span></div>
                  <div class="stat-item"><span class="sv">{{ p.avgKda?.toFixed(2) }}</span><span class="sl">KDA</span></div>
                  <div class="stat-item"><span class="sv">{{ p.avgKill?.toFixed(1) }}</span><span class="sl">场均击杀</span></div>
                  <div class="stat-item"><span class="sv">{{ p.avgDeath?.toFixed(1) }}</span><span class="sl">场均死亡</span></div>
                  <div class="stat-item"><span class="sv">{{ p.avgAssist?.toFixed(1) }}</span><span class="sl">场均助攻</span></div>
                  <div class="stat-item"><span class="sv">{{ formatDamage(p.avgHurtToHero) }}</span><span class="sl">场均伤害</span></div>
                  <div class="stat-item"><span class="sv">{{ p.avgParticipationRate?.toFixed(1) }}%</span><span class="sl">参团率</span></div>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.heroes?.data?.length">
              <h4>高出场英雄</h4>
              <div class="hero-table">
                <div class="hero-table-row hero-table-header">
                  <span>英雄</span><span>场次</span><span>胜率</span><span>KDA</span>
                </div>
                <div v-for="h in detailData.heroes.data" :key="h.heroId" class="hero-table-row">
                  <span class="ht-name">
                    <img :src="'https://res.edata.qq.com/sgame/static/images/hero/' + h.heroId + '.jpg'" class="ht-img" />
                    {{ h.heroName }}
                  </span>
                  <span>{{ h.games }}</span>
                  <span :class="h.winRate >= 60 ? 'wr-high' : h.winRate < 40 ? 'wr-low' : ''">{{ h.winRate }}%</span>
                  <span>{{ h.avgKda }}</span>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.equips?.data?.length">
              <h4>常用装备</h4>
              <div class="hero-equip-list">
                <div v-for="e in detailData.equips.data" :key="e.equipId" class="hero-equip-item">
                  <img v-if="e.equipIcon" :src="e.equipIcon" class="hero-equip-img" />
                  <span class="hero-equip-name">{{ e.equipName }}</span>
                  <span class="hero-equip-cnt">{{ e.pickCount }}次</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 英雄详情 -->
          <template v-else-if="detailType === 'hero' && detailData">
            <div class="detail-profile" v-if="detailData.hero">
              <div v-for="h in (detailData.hero.data || detailData.hero)" :key="h.heroName" class="profile-card">
                <div class="profile-header">
                  <img v-if="h.heroIcon" :src="h.heroIcon" class="profile-avatar" />
                  <div>
                    <b>{{ h.heroName }}</b>
                  </div>
                </div>
                <div class="stat-grid">
                  <div class="stat-item"><span class="sv">{{ h.battleCount }}</span><span class="sl">出场</span></div>
                  <div class="stat-item"><span class="sv">{{ (h.pickRate * 100).toFixed(1) }}%</span><span class="sl">Pick率</span></div>
                  <div class="stat-item"><span class="sv">{{ (h.banRate * 100).toFixed(1) }}%</span><span class="sl">Ban率</span></div>
                  <div class="stat-item"><span class="sv">{{ (h.winRate * 100).toFixed(1) }}%</span><span class="sl">胜率</span></div>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.players?.data?.length">
              <h4>高胜率选手</h4>
              <div class="hero-table">
                <div class="hero-table-row hero-table-header" style="grid-template-columns:1fr 50px 50px 50px">
                  <span>选手</span><span>场次</span><span>胜率</span><span>KDA</span>
                </div>
                <div v-for="p in detailData.players.data" :key="p.playerName" class="hero-table-row" style="grid-template-columns:1fr 50px 50px 50px">
                  <span class="ht-name">
                    <img v-if="p.playerIcon" :src="p.playerIcon" class="player-icon" />
                    {{ p.playerName?.split('.').pop() || p.playerName }}
                    <img v-if="p.teamIcon" :src="p.teamIcon" class="team-icon" style="margin-left:6px" />
                    <small style="color:var(--muted)">{{ p.teamName }}</small>
                  </span>
                  <span>{{ p.games }}</span>
                  <span :class="p.winRate >= 60 ? 'wr-high' : p.winRate < 40 ? 'wr-low' : ''">{{ p.winRate }}%</span>
                  <span>{{ p.avgKda }}</span>
                </div>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.equips?.data?.length">
              <h4>常用装备</h4>
              <div class="hero-equip-list">
                <div v-for="e in detailData.equips.data" :key="e.equipId" class="hero-equip-item">
                  <img v-if="e.equipIcon" :src="e.equipIcon" class="hero-equip-img" />
                  <span class="hero-equip-name">{{ e.equipName }}</span>
                  <span class="hero-equip-cnt">{{ e.pickCount }}次</span>
                </div>
              </div>
            </div>
          </template>

          <!-- 荣誉详情 -->
          <template v-else-if="detailType === 'honor' && detailData">
            <div class="honor-detail">
              <div class="honor-big">
                <span class="honor-gold">{{ detailData.honor.champion }}</span>
                <span class="sl">冠军</span>
              </div>
              <div class="honor-big">
                <span class="honor-silver">{{ detailData.honor.runnerUp }}</span>
                <span class="sl">亚军</span>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.honor.championLeagues?.length">
              <h4>冠军赛事</h4>
              <div class="tag-list">
                <span v-for="l in detailData.honor.championLeagues" :key="l" class="honor-tag gold">{{ l }}</span>
              </div>
            </div>
            <div class="equip-section" v-if="detailData.honor.runnerUpLeagues?.length">
              <h4>亚军赛事</h4>
              <div class="tag-list">
                <span v-for="l in detailData.honor.runnerUpLeagues" :key="l" class="honor-tag silver">{{ l }}</span>
              </div>
            </div>
          </template>

          <div v-else class="detail-empty">暂无数据</div>
        </el-dialog>

        <el-dialog v-model="patchDialogVisible" :title="patchDialogTitle" width="680" class="patch-dialog" :modal-class="'patch-dialog-overlay'" destroy-on-close>
          <div v-if="patchDialogLoading" class="patch-loading">加载中...</div>
          <div v-else class="patch-dialog-content" v-html="patchDialogContent" />
        </el-dialog>

        <div class="bottom-grid">
          <div class="panel agent-panel">
            <div class="section-head">
              <span>Agent 复盘</span>
              <el-button :icon="ChatLineRound" circle class="icon-ghost" @click="askAgent" />
            </div>
            <div class="agent-log" ref="agentLogRef">
              <div v-for="item in agentMessages" :key="item.id" :class="['bubble', item.role]">
                <template v-if="item.role === 'assistant'">
                  <div v-if="item.reasoning" class="reasoning-block">
                    <div class="reasoning-body" :class="{ collapsed: !item._expandReasoning && item.reasoning.length > 120 }">{{ item.reasoning }}</div>
                    <button v-if="item.reasoning.length > 120" class="reasoning-toggle" @click="item._expandReasoning = !item._expandReasoning">
                      {{ item._expandReasoning ? '收起' : '展开全部思考' }}
                    </button>
                  </div>
                  <div v-if="item.text" v-html="renderMd(item.text)" :class="['md-body', loading.agent && item.id === agentMessages[agentMessages.length - 1]?.id ? 'streaming' : '']"></div>
                  <span v-if="loading.agent && item.id === agentMessages[agentMessages.length - 1]?.id && !item.text && !item.reasoning" class="typing-dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></span>
                </template>
                <template v-else>{{ item.text }}</template>
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getTheme, setTheme } from '../utils/theme'
import MarkdownIt from 'markdown-it'
import {
  ChatLineRound,
  DataAnalysis,
  Position,
  Refresh,
  Search
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const md = new MarkdownIt({ html: false, breaks: true })
function renderMd(text) { return md.render(text || '') }

const leagues = ref(JSON.parse(localStorage.getItem('kpl_leagues') || '[]'))
const theme = ref(getTheme())
watch(theme, (v) => setTheme(v))

const selectedLeagueId = ref('')
const leagueMeta = ref({ matchCount: 0, teamCount: 0, playerCount: 0 })
const system = ref({})
const queryMode = ref('ranking')
const queryKeyword = ref('')
const heroSort = ref('pick')
const playerSort = ref('kda')
const queryResult = ref(null)
const agentLogRef = ref(null)
const agentQuestion = ref('AG 最近比赛表现怎么样')
const agentMessages = ref([
  { id: 1, role: 'assistant', text: '数据链路已待命。' }
])
const patchNotes = ref(JSON.parse(localStorage.getItem('kpl_patchNotes') || '[]'))
const patchLoading = ref(false)
const patchDialogVisible = ref(false)
const patchDialogTitle = ref('')
const patchDialogContent = ref('')
const patchDialogLoading = ref(false)
const detailVisible = ref(false)
const detailType = ref('')
const detailTitle = ref('')
const detailLoading = ref(false)
const detailData = ref(null)
const detailRow = ref(null)

const teamIconMap = ref(JSON.parse(localStorage.getItem('kpl_teamIcons') || '{}'))

const loading = ref({
  query: false,
  agent: false
})

const isMobileViewport = ref(false)
const mainTableRef = ref(null)

// 表格切换动效
const tableState = ref('')
let switchTimer = null
function triggerTableSwitch() {
  clearTimeout(switchTimer)
  tableState.value = 'switching'
  switchTimer = setTimeout(() => {
    tableState.value = 'entering'
    setTimeout(() => { tableState.value = '' }, 350)
  }, 150)
}

watch(queryMode, () => {
  queryResult.value = null
  queryKeyword.value = ''
  detailVisible.value = false
  triggerTableSwitch()
  if (queryMode.value === 'ranking') {
    loadTeamRanking()
  } else if (queryMode.value === 'honors') {
    loadHonors()
  } else if (queryMode.value === 'playerTop' || queryMode.value === 'heroTop' || queryMode.value === 'match' || queryMode.value === 'equip') {
    runQuery()
  }
})

const queryModes = [
  { label: '战队榜', value: 'ranking' },
  { label: '选手榜', value: 'playerTop' },
  { label: '英雄榜', value: 'heroTop' },
  { label: '荣誉榜', value: 'honors' },
  { label: '比赛', value: 'match' },
  { label: '装备', value: 'equip' }
]

const currentLeague = computed(() => leagues.value.find((item) => item.leagueId === selectedLeagueId.value))
const modeLabel = computed(() => queryModes.find((item) => item.value === queryMode.value)?.label || '-')
const placeholder = computed(() => {
  const map = {
    ranking: '搜索战队，例如 AG（留空显示榜单）',
    playerTop: '搜索选手，例如 一诺（留空显示榜单）',
    heroTop: '搜索英雄，例如 公孙离（留空显示榜单）',
    match: '输入战队名，例如 狼队',
    honors: '跨赛事荣誉总榜（无需输入）',
    equip: '输入装备名，例如 复活甲'
  }
  return map[queryMode.value] || '输入关键词'
})

const rawData = computed(() => {
  const data = queryResult.value?.data
  // 兼容 { data: [...] } 和 { data: { data: [...] } } 两种结构
  if (Array.isArray(data)) return data
  if (data?.data && Array.isArray(data.data)) return data.data
  return Array.isArray(queryResult.value) ? queryResult.value : []
})

const tableRows = computed(() => {
  let rows = rawData.value.map(normalizeRow)
  if (queryMode.value === 'match') {
    rows = [...rows].sort((a, b) => {
      const da = a._rawStartTime || ''
      const db = b._rawStartTime || ''
      return db.localeCompare(da)
    })
  }
  return rows
})
// 数据变化后重新绑定滚动监听
watch(tableRows, () => {
  nextTick(() => setupStickyScroll())
})

const resultCount = computed(() => queryResult.value?.data?.count ?? queryResult.value?.count ?? tableRows.value.length)
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
      ['matchStageDesc', '赛段'],
      ['camp1TeamName', '蓝方'],
      ['camp1Score', '比分'],
      ['camp2TeamName', '红方'],
      ['camp2Score', '比分'],
      ['startTime', '时间']
    ],
    honors: [
      ['teamName', '战队'],
      ['champion', '冠军'],
      ['runnerUp', '亚军'],
      ['total', '总计']
    ],
    equip: [
      ['equipName', '装备'],
      ['pickCount', '出场次数'],
      ['pickRate', '出场率'],
      ['heroCount', '使用英雄数'],
      ['playerCount', '使用选手数']
    ]
  }
  return (columns[queryMode.value] || columns.ranking).map(([prop, label]) => ({ prop, label }))
})

const stickySubjectProp = computed(() => {
  const map = {
    ranking: 'teamName',
    playerTop: 'playerName',
    heroTop: 'heroName',
    match: 'camp1TeamName',
    honors: 'teamName',
    equip: 'equipName'
  }
  return map[queryMode.value] || ''
})

const tableRenderKey = computed(() => {
  return `${queryMode.value}-${isMobileViewport.value ? 'mobile-sticky' : 'desktop'}-${stickySubjectProp.value}`
})

function updateMobileViewport() {
  isMobileViewport.value = window.innerWidth <= 767 || window.matchMedia('(max-width: 767px)').matches
  nextTick(() => {
    mainTableRef.value?.doLayout?.()
    setupStickyScroll()
  })
}

function isStickySubjectCol(prop) {
  return isMobileViewport.value && prop === stickySubjectProp.value
}

// 重置表格滚动到最左边
function resetTableScroll() {
  const tableEl = mainTableRef.value?.$el
  if (!tableEl) return
  const bodyWrapper = tableEl.querySelector('.el-table__body-wrapper')
  if (bodyWrapper) {
    bodyWrapper.scrollLeft = 0
    // 清除 sticky 列的 transform
    bodyWrapper.querySelectorAll('td.sticky-col .cell').forEach(cell => {
      cell.style.transform = 'translateX(0)'
    })
  }
}

// JS 滚动同步固定列
let stickyScrollCleanup = null
function setupStickyScroll() {
  if (stickyScrollCleanup) {
    stickyScrollCleanup()
    stickyScrollCleanup = null
  }
  if (!isMobileViewport.value || !mainTableRef.value) return

  nextTick(() => {
    const tableEl = mainTableRef.value?.$el
    if (!tableEl) return
    const bodyWrapper = tableEl.querySelector('.el-table__body-wrapper')
    if (!bodyWrapper) return

    const onScroll = () => {
      const sl = bodyWrapper.scrollLeft
      // 固定 body 中的 sticky 列（header 不滚动，无需处理）
      bodyWrapper.querySelectorAll('td.sticky-col .cell').forEach(cell => {
        cell.style.transform = `translateX(${sl}px)`
      })
    }

    bodyWrapper.addEventListener('scroll', onScroll, { passive: true })
    stickyScrollCleanup = () => bodyWrapper.removeEventListener('scroll', onScroll)
  })
}

watch(tableRenderKey, () => {
  nextTick(() => {
    mainTableRef.value?.doLayout?.()
    setupStickyScroll()
  })
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
  await Promise.allSettled([loadStatus(), loadLeagues(), loadTeamIcons()])
  await loadDashboard()
  await loadLeagueMeta()
  loadPatchNotes()
}

async function loadTeamIcons() {
  try {
    const res = await request('/api/query/team/ranking')
    const teams = res?.data?.data || res?.data || []
    const map = {}
    if (Array.isArray(teams)) teams.forEach(t => { if (t.teamName && t.teamIcon) map[t.teamName] = t.teamIcon })
    if (Object.keys(map).length) {
      teamIconMap.value = map
      localStorage.setItem('kpl_teamIcons', JSON.stringify(map))
    }
  } catch { /* 失败时保留缓存 */ }
}

function mergeTeamIcons(rows) {
  return rows.map(row => {
    if (row.teamIcon) return row
    // 比赛数据用 camp1TeamName/camp2TeamName
    if (row.camp1TeamName || row.camp2TeamName) {
      return {
        ...row,
        camp1TeamIcon: row.camp1TeamIcon || getTeamIcon(row.camp1TeamName),
        camp2TeamIcon: row.camp2TeamIcon || getTeamIcon(row.camp2TeamName),
      }
    }
    return { ...row, teamIcon: getTeamIcon(row.teamName) }
  })
}

function getTeamIcon(name) {
  if (!name) return ''
  return teamIconMap.value[name]
    || Object.entries(teamIconMap.value).find(([n]) => n.includes(name) || name.includes(n))?.[1]
    || fuzzyMatchIcon(name)
    || ''
}

async function loadStatus() {
  system.value = await request('/api/system/status')
}

async function loadLeagues() {
  try {
    const data = await request('/api/leagues?limit=30')
    if (data?.length) {
      leagues.value = data
      localStorage.setItem('kpl_leagues', JSON.stringify(data))
    }
  } catch { /* 失败时保留缓存 */ }
  if (!selectedLeagueId.value && leagues.value.length) {
    selectedLeagueId.value = leagues.value[0].leagueId
  }
}

async function loadDashboard() {
  if (selectedLeagueId.value) {
    await runQuery()
  }
  await loadLeagueMeta()
}

async function loadLeagueMeta() {
  if (!selectedLeagueId.value) return
  try {
    const lid = selectedLeagueId.value
    const [teams, players, matches] = await Promise.all([
      request(`/api/query/team/ranking?leagueId=${lid}`).catch(() => ({ data: [] })),
      request(`/api/query/player/top?sort=kda&leagueId=${lid}`).catch(() => ({ data: [] })),
      request(`/api/query/match/schedule?leagueId=${lid}`).catch(() => ({ data: [] }))
    ])
    leagueMeta.value = {
      matchCount: matches?.count ?? matches?.data?.length ?? 0,
      teamCount: teams?.count ?? teams?.data?.length ?? 0,
      playerCount: players?.count ?? players?.data?.length ?? 0
    }
  } catch { /* ignore */ }
}

async function loadTeamRanking() {
  queryMode.value = 'ranking'
  queryResult.value = await request(`/api/query/team/ranking${leagueParam()}`)
  nextTick(resetTableScroll)
}

async function loadHonors() {
  const res = await request('/api/query/team/honors')
  // 荣誉榜 API 返回 { data: { data: [...] } }
  const rows = (res?.data?.data || res?.data || res || []).filter(row => !['AS仙阁', 'BA黑凤梨'].includes(row.teamName))
  if (Array.isArray(rows)) {
    const merged = rows.map(row => {
      if (row.teamIcon) return row
      const icon = teamIconMap.value[row.teamName]
        || Object.entries(teamIconMap.value).find(([name]) => name.includes(row.teamName) || row.teamName.includes(name))?.[1]
        || fuzzyMatchIcon(row.teamName)
        || ''
      return { ...row, teamIcon: icon }
    })
    if (res?.data?.data) res.data.data = merged
    else if (res?.data) res.data = merged
  }
  queryResult.value = res
  nextTick(resetTableScroll)
}

// 关键词模糊匹配队标
function fuzzyMatchIcon(teamName) {
  const keywords = ['AG', '狼队', 'WB', 'JDG', 'TTG', 'Hero', 'eStar', 'DYG', 'DRG', 'KSG', 'LGD', 'TES', 'EDG', 'RNG', 'RW', 'WE', '情久', 'TCG']
  for (const kw of keywords) {
    if (teamName.includes(kw)) {
      const match = Object.entries(teamIconMap.value).find(([name]) => name.includes(kw))
      if (match) return match[1]
    }
  }
  return null
}

async function runQuery() {
  loading.value.query = true
  triggerTableSwitch()
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
        url = `/api/query/match/schedule${leagueParam()}`
      } else {
        url = `/api/query/match/recent?team=${keyword}${leagueJoin()}`
      }
    } else if (queryMode.value === 'honors') {
      await loadHonors()
      return
    } else if (queryMode.value === 'equip') {
      const lp = leagueParam()
      url = hasKeyword
        ? `/api/query/equip/search?name=${keyword}${leagueJoin()}&limit=10`
        : `/api/query/equip/top${lp ? lp + '&' : '?'}limit=10`
    }
    const res = await request(url)
    // 兼容 { data: [...] } 和 { data: { data: [...] } } 两种结构
    if (res?.data?.data && Array.isArray(res.data.data)) res.data.data = mergeTeamIcons(res.data.data)
    else if (res?.data && Array.isArray(res.data)) res.data = mergeTeamIcons(res.data)
    queryResult.value = res
    nextTick(resetTableScroll)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value.query = false
  }
}

function scrollAgentLog() {
  nextTick(() => { if (agentLogRef.value) agentLogRef.value.scrollTop = agentLogRef.value.scrollHeight })
}

async function askAgent() {
  const text = agentQuestion.value.trim()
  if (!text) return
  loading.value.agent = true
  const id = Date.now()
  agentMessages.value.push({ id, role: 'user', text })
  agentQuestion.value = ''
  scrollAgentLog()

  const assistantId = id + 1
  agentMessages.value.push({ id: assistantId, role: 'assistant', reasoning: '', text: '', _expandReasoning: false })

  try {
    const resp = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text })
    })
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (payload === '[DONE]') continue
        if (!payload) continue
        const msg = agentMessages.value.find(m => m.id === assistantId)
        if (!msg) continue
        try {
          const obj = JSON.parse(payload)
          if (obj.t === 'r') msg.reasoning += obj.d
          else if (obj.t === 'c') msg.text += obj.d
        } catch {
          msg.text += payload
        }
        scrollAgentLog()
      }
    }
  } catch (error) {
    const msg = agentMessages.value.find(m => m.id === assistantId)
    if (msg && !msg.text) msg.text = error.message
  } finally {
    loading.value.agent = false
    scrollAgentLog()
  }
}

async function onRowClick(row) {
  const mode = queryMode.value
  detailRow.value = row
  detailData.value = null
  detailLoading.value = true
  detailVisible.value = true

  try {
    if (mode === 'match') {
      detailType.value = 'match'
      detailTitle.value = `${row.camp1TeamName} vs ${row.camp2TeamName}`
      const data = await request(`/api/query/match/battle?matchId=${row.matchId}`)
      detailData.value = { battles: data.battles || [], match: row }
    } else if (mode === 'equip') {
      detailType.value = 'equip'
      detailTitle.value = row.equipName
      detailData.value = await request(`/api/query/equip/detail?equipId=${row.equipId}${leagueJoin()}&heroLimit=8`)
    } else if (mode === 'ranking') {
      detailType.value = 'team'
      detailTitle.value = row.teamName
      const [teamData, recentMatches] = await Promise.all([
        request(`/api/query/team?name=${row.teamName}${leagueJoin()}`),
        request(`/api/query/match/recent?team=${row.teamName}${leagueJoin()}`).catch(() => null)
      ])
      detailData.value = { team: teamData, matches: recentMatches }
    } else if (mode === 'playerTop') {
      detailType.value = 'player'
      detailTitle.value = row.playerName
      const [playerData, equipData, heroData] = await Promise.all([
        request(`/api/query/player?name=${row.playerName}${leagueJoin()}`),
        request(`/api/query/equip/player?name=${row.playerName}${leagueJoin()}&limit=6`).catch(() => null),
        request(`/api/query/player/heroes?name=${row.playerName}${leagueJoin()}&limit=8`).catch(() => null)
      ])
      detailData.value = { player: playerData, equips: equipData, heroes: heroData }
    } else if (mode === 'heroTop') {
      detailType.value = 'hero'
      detailTitle.value = row.heroName
      const [heroData, equipData, playerData] = await Promise.all([
        request(`/api/query/hero?name=${row.heroName}${leagueJoin()}`),
        request(`/api/query/equip/hero?heroName=${row.heroName}${leagueJoin()}&limit=6`).catch(() => null),
        request(`/api/query/hero/players?name=${row.heroName}${leagueJoin()}&limit=8`).catch(() => null)
      ])
      detailData.value = { hero: heroData, equips: equipData, players: playerData }
    } else if (mode === 'honors') {
      detailType.value = 'honor'
      detailTitle.value = row.teamName
      detailData.value = { honor: row }
    }
  } catch (e) {
    ElMessage.error('加载详情失败: ' + e.message)
  } finally {
    detailLoading.value = false
  }
}

function positionPercent(cnt, positions) {
  const max = Math.max(...positions.map(p => p.cnt))
  return max > 0 ? (cnt / max * 100).toFixed(1) : 0
}

function leagueParam() {
  return selectedLeagueId.value ? `?leagueId=${selectedLeagueId.value}` : ''
}

function leagueJoin() {
  return selectedLeagueId.value ? `&leagueId=${selectedLeagueId.value}` : ''
}

function normalizeRow(row) {
  const clone = { ...row }
  for (const key of ['winRate', 'pickRate', 'banRate']) {
    if (typeof clone[key] === 'number') clone[key] = (clone[key] * 100).toFixed(1) + '%'
  }
  for (const key of ['avgKda', 'avgKill']) {
    if (typeof clone[key] === 'number') clone[key] = Number(clone[key]).toFixed(2)
  }
  if (clone.startTime) {
    clone._rawStartTime = clone.startTime
    clone.startTime = formatTime(clone.startTime)
  }
  return clone
}

function stageClass(stage) {
  if (stage === 'js' || stage === 'zjs') return 'stage-final'
  if (stage === 'jhs') return 'stage-playoff'
  return 'stage-regular'
}

function isNameCol(prop) {
  return ['teamName', 'playerName', 'heroName', 'camp1TeamName', 'camp2TeamName'].includes(prop)
}

function isRateCol(prop) {
  return ['winRate', 'pickRate', 'banRate'].includes(prop)
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10)
}

function formatDamage(value) {
  if (!value) return '0'
  if (value >= 10000) return (value / 10000).toFixed(1) + '万'
  return String(value)
}

function stripHtml(html) {
  if (!html) return ''
  return html.replace(/<br\s*\/?>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
}

async function loadPatchNotes() {
  patchLoading.value = true
  try {
    const data = await request('/api/query/patch-notes?limit=20')
    if (data?.length) {
      patchNotes.value = data
      localStorage.setItem('kpl_patchNotes', JSON.stringify(data))
    }
  } catch {
    // 失败时保留缓存数据，不清空
  } finally {
    patchLoading.value = false
  }
}

async function openNote(note) {
  patchDialogTitle.value = note.title
  patchDialogVisible.value = true
  if (note.contentHtml) {
    patchDialogContent.value = note.contentHtml
    return
  }
  patchDialogLoading.value = true
  patchDialogContent.value = ''
  try {
    const detail = await request(`/api/query/patch-notes/detail?id=${note.id}`)
    note.contentHtml = detail.contentHtml || ''
    patchDialogContent.value = note.contentHtml
  } catch {
    patchDialogContent.value = '<p>加载失败</p>'
  } finally {
    patchDialogLoading.value = false
  }
}

function formatPatchDate(date) {
  if (!date) return ''
  const parts = date.split('-')
  return parts.length >= 3 ? `${parts[1]}-${parts[2]}` : date
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const POS_ORDER = { 6: 1, 5: 2, 2: 3, 7: 4, 4: 5 }

function getWinnerTeamName(battle, camp1Name, camp2Name) {
  const winCamp = battle.battle?.winCamp
  if (!battle.players?.length) return winCamp === 1 ? camp1Name : camp2Name
  for (const pd of battle.players) {
    if (pd.player?.camp === winCamp) return pd.player.teamName
  }
  return winCamp === 1 ? camp1Name : camp2Name
}

function getPlayerMatchCamp(player, camp1Name) {
  if (!player?.teamName || !camp1Name) return player?.camp || 1
  return player.teamName === camp1Name ? 1 : 2
}

function sortedPlayers(players, camp1Name) {
  if (!players) return []
  return [...players].sort((a, b) => {
    const pa = a.player, pb = b.player
    const ca = getPlayerMatchCamp(pa, camp1Name), cb = getPlayerMatchCamp(pb, camp1Name)
    if (ca !== cb) return ca - cb
    return (POS_ORDER[pa?.position] || 9) - (POS_ORDER[pb?.position] || 9)
  })
}

onMounted(async () => {
  updateMobileViewport()
  window.addEventListener('resize', updateMobileViewport)
  await refreshAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobileViewport)
  if (stickyScrollCleanup) stickyScrollCleanup()
})
</script>

<style scoped>
.rankings-console {
  --mono-bg: #f8f5ec;
  --mono-panel: rgba(255, 255, 255, 0.92);
  --mono-panel-soft: rgba(255, 255, 255, 0.82);
  --mono-line: rgba(0, 0, 0, 0.38);
  --mono-line-strong: rgba(0, 0, 0, 0.45);
  --mono-ink: #1a1a1a;
  --mono-soft: rgba(26, 26, 26, 0.65);
  --mono-dim: rgba(26, 26, 26, 0.4);
  --mono-accent: #1a1a1a;
  --el-segmented-item-selected-bg-color: rgba(0, 0, 0, 0.08);
  --el-segmented-item-selected-color: #1a1a1a;
  --el-seg-bg: rgba(0, 0, 0, 0.08);
  --el-seg-color: #1a1a1a;
  --el-seg-border: rgba(0, 0, 0, 0.12);
  --btn-bg: rgba(255, 255, 255, 0.6);
  --btn-hover-bg: rgba(255, 255, 255, 0.9);
  --primary-bg: #1a1a1a;
  --primary-color: #f8f5ec;
  --primary-hover-bg: #333;
  --input-bg: rgba(255, 255, 255, 0.7);
  --focus-ring: rgba(0, 0, 0, 0.06);
  --table-header-bg: rgba(0, 0, 0, 0.04);
  --table-hover-bg: rgba(0, 0, 0, 0.04);
  --table-container-bg: rgba(255, 255, 255, 0.5);
  --loading-bg: rgba(248, 245, 236, 0.7);
  --loading-border: rgba(0, 0, 0, 0.1);
  --loading-top: #1a1a1a;
  --card-bg: rgba(255, 255, 255, 0.7);
  --card-bg-alt: rgba(0, 0, 0, 0.03);
  --stat-bg: rgba(0, 0, 0, 0.03);
  --badge-color: #1a1a1a;
  --badge-dim: rgba(26, 26, 26, 0.4);
  --label-dim: rgba(26, 26, 26, 0.45);
  --text-dim: rgba(26, 26, 26, 0.5);
  --bar-bg: rgba(0, 0, 0, 0.08);
  --bar-fill: #1a1a1a;
  --dialog-bg: #fff;
  --corner-border: rgba(0, 0, 0, 0.12);
  max-width: none;
  min-height: 100vh;
  padding: 28px 32px 36px 87px;
  color: var(--mono-ink);
  background:
    linear-gradient(180deg, rgba(250, 248, 240, 0.98), rgba(245, 242, 232, 0.99)),
    #f8f5ec;
}

/* ── dark theme ── */
.rankings-console.theme-dark {
  --mono-bg: #0a0a0a;
  --mono-panel: rgba(18, 18, 18, 0.92);
  --mono-panel-soft: rgba(30, 30, 30, 0.82);
  --mono-line: rgba(255, 255, 255, 0.2);
  --mono-line-strong: rgba(255, 255, 255, 0.35);
  --mono-ink: #e8e8e8;
  --mono-soft: rgba(232, 232, 232, 0.65);
  --mono-dim: rgba(232, 232, 232, 0.38);
  --mono-accent: #e8e8e8;
  --el-seg-bg: rgba(255, 255, 255, 0.08);
  --el-seg-color: #e8e8e8;
  --el-seg-border: rgba(255, 255, 255, 0.15);
  --btn-bg: rgba(255, 255, 255, 0.08);
  --btn-hover-bg: rgba(255, 255, 255, 0.14);
  --primary-bg: #e8e8e8;
  --primary-color: #0a0a0a;
  --primary-hover-bg: #ccc;
  --input-bg: rgba(255, 255, 255, 0.06);
  --focus-ring: rgba(255, 255, 255, 0.08);
  --table-header-bg: rgba(255, 255, 255, 0.06);
  --table-hover-bg: rgba(255, 255, 255, 0.05);
  --table-container-bg: rgba(255, 255, 255, 0.04);
  --loading-bg: rgba(10, 10, 10, 0.7);
  --loading-border: rgba(255, 255, 255, 0.15);
  --loading-top: #e8e8e8;
  --card-bg: rgba(255, 255, 255, 0.06);
  --card-bg-alt: rgba(255, 255, 255, 0.03);
  --stat-bg: rgba(255, 255, 255, 0.04);
  --badge-color: #e8e8e8;
  --badge-dim: rgba(232, 232, 232, 0.4);
  --label-dim: rgba(232, 232, 232, 0.45);
  --text-dim: rgba(232, 232, 232, 0.5);
  --bar-bg: rgba(255, 255, 255, 0.1);
  --bar-fill: #e8e8e8;
  --dialog-bg: #1a1a1a;
  --corner-border: rgba(255, 255, 255, 0.18);
  background: linear-gradient(180deg, #0a0a0a, #141414);
}

.rankings-console .command-strip,
.rankings-console .panel {
  position: relative;
  border: 1px solid var(--mono-line);
  border-radius: 0;
  background: var(--mono-panel);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.rankings-console .command-strip::before,
.rankings-console .panel::before {
  content: "";
  position: absolute;
  left: -1px;
  top: -1px;
  width: 36px;
  height: 36px;
  border-left: 2px solid var(--corner-border);
  border-top: 2px solid var(--corner-border);
  pointer-events: none;
}

.rankings-console .brand-mark {
  border: 1px solid var(--mono-line-strong);
  border-radius: 0;
  color: var(--mono-ink);
  background: var(--stat-bg);
}

.rankings-console .eyebrow,
.rankings-console .section-head span,
.rankings-console .mini-title {
  color: var(--mono-soft);
  font-weight: 900;
  letter-spacing: 1.8px;
}

.rankings-console h1,
.rankings-console .metric-tile strong,
.rankings-console .league-plate strong,
.rankings-console .stat-val,
.rankings-console .sv {
  color: var(--mono-ink);
}

.rankings-console .status-pill,
.rankings-console .league-plate,
.rankings-console .metric-tile,
.rankings-console .battle-card,
.rankings-console .player-card,
.rankings-console .profile-card,
.rankings-console .equip-stat-card,
.rankings-console .hero-equip-item,
.rankings-console .bubble,
.rankings-console .reasoning-block {
  border-color: var(--mono-line);
  border-radius: 0;
  background: var(--card-bg);
  color: var(--mono-soft);
}

.rankings-console .status-pill.online {
  color: var(--mono-ink);
  border-color: var(--mono-line-strong);
}

.rankings-console .status-pill.online i {
  background: var(--mono-accent);
  box-shadow: 0 0 6px rgba(0, 0, 0, 0.2);
}

.rankings-console .layout-grid {
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.rankings-console .league-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 12px;
}
.rankings-console .league-stat-item {
  text-align: center;
  padding: 10px 0;
  border: 1px solid var(--mono-line);
  background: var(--stat-bg);
}
.rankings-console .ls-val {
  display: block;
  font-size: 18px;
  font-weight: 800;
  color: var(--mono-ink);
  line-height: 1.2;
}
.rankings-console .ls-label {
  display: block;
  font-size: 11px;
  color: var(--mono-dim);
  margin-top: 2px;
  letter-spacing: 1px;
}

/* ── 版本动态 ── */
.rankings-console .patch-notes {
  margin-top: 12px;
}
.rankings-console .patch-notes .section-head {
  padding-bottom: 8px;
  margin-bottom: 0;
}
.rankings-console .patch-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--mono-line);
  cursor: pointer;
  transition: background 0.15s;
}
.rankings-console .patch-item:last-child {
  border-bottom: none;
}
.rankings-console .patch-item:hover {
  background: var(--stat-bg);
}
.rankings-console .patch-date {
  display: block;
  font-size: 11px;
  color: var(--mono-dim);
  font-weight: 800;
  letter-spacing: 0.5px;
  margin-bottom: 2px;
}
.rankings-console .patch-title {
  display: block;
  font-size: 13px;
  color: var(--mono-ink);
  font-weight: 800;
  line-height: 1.4;
}

/* ── 版本动态弹窗 ── */
.rankings-console .patch-dialog-content {
  font-size: 14px;
  color: var(--mono-ink);
  line-height: 1.7;
  word-break: break-word;
  overflow-wrap: break-word;
}
.rankings-console .patch-dialog-content img {
  max-width: 100%;
  height: auto;
  margin: 6px 0;
  border-radius: 4px;
  display: block;
}
.rankings-console .patch-dialog-content h1,
.rankings-console .patch-dialog-content h2,
.rankings-console .patch-dialog-content h3 {
  font-size: 16px;
  font-weight: 900;
  margin: 12px 0 6px;
}
.rankings-console .patch-dialog-content p {
  margin: 6px 0;
}
.rankings-console .patch-loading {
  padding: 24px 0;
  text-align: center;
  color: var(--mono-dim);
  font-size: 13px;
}
.rankings-console .patch-empty {
  padding: 12px 0;
  font-size: 12px;
  color: var(--mono-dim);
  text-align: center;
}
.rankings-console .patch-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 0;
}
.rankings-console .skel-bar {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--mono-line) 25%, rgba(128,128,128,0.1) 50%, var(--mono-line) 75%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.5s ease-in-out infinite;
}
.rankings-console .skel-bar:nth-child(2) { width: 85%; }
.rankings-console .skel-bar:nth-child(3) { width: 70%; }
.rankings-console .skel-bar:nth-child(4) { width: 60%; }
@keyframes skel-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.rankings-console .sidebar-author {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--mono-line);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rankings-console .sidebar-author .author-name {
  font-size: 12px;
  font-weight: 800;
  color: var(--mono-ink);
  letter-spacing: 1px;
}
.rankings-console .sidebar-author .author-contact {
  font-size: 11px;
  color: var(--mono-dim);
}

.rankings-console .side-rail,
.rankings-console .query-panel,
.rankings-console .agent-panel {
  padding: 20px;
}

.rankings-console .section-head {
  border-bottom: 1px solid var(--mono-line);
  padding-bottom: 12px;
}

.rankings-console :deep(.el-button) {
  border-radius: 0;
}

.rankings-console :deep(.el-button:not(.el-button--primary)) {
  --el-button-bg-color: var(--btn-bg);
  --el-button-border-color: var(--mono-line);
  --el-button-text-color: var(--mono-soft);
  --el-button-hover-bg-color: var(--btn-hover-bg);
  --el-button-hover-border-color: var(--mono-line-strong);
  --el-button-hover-text-color: var(--mono-ink);
}

.rankings-console .primary-action,
.rankings-console .sync-all-btn {
  --el-button-bg-color: var(--primary-bg);
  --el-button-border-color: var(--primary-bg);
  --el-button-text-color: var(--primary-color);
  --el-button-hover-bg-color: var(--primary-hover-bg);
  --el-button-hover-border-color: var(--primary-hover-bg);
  border-radius: 0;
}

.rankings-console :deep(.el-input__wrapper),
.rankings-console :deep(.el-select__wrapper),
.rankings-console :deep(.el-segmented) {
  border-radius: 0 !important;
  background: var(--input-bg) !important;
  box-shadow: 0 0 0 1px var(--mono-line) inset !important;
  --el-segmented-item-selected-bg-color: var(--el-seg-bg);
  --el-segmented-item-selected-color: var(--el-seg-color);
  --el-segmented-item-color: var(--mono-soft);
}

.rankings-console :deep(.el-input__wrapper.is-focus),
.rankings-console :deep(.el-select__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--mono-accent) inset, 0 0 0 3px var(--focus-ring) !important;
}

.rankings-console :deep(.el-select__placeholder),
.rankings-console :deep(.el-select__selected-item .el-select__placeholder) {
  color: var(--mono-ink) !important;
}
.rankings-console :deep(.el-select__placeholder.is-transparent) {
  color: var(--mono-dim) !important;
}
.rankings-console :deep(.el-select__caret) {
  color: var(--mono-soft) !important;
}

.rankings-console :deep(.el-segmented__item-selected) {
  border-radius: 0 !important;
  background-color: var(--el-seg-bg) !important;
  color: var(--el-seg-color) !important;
  font-weight: 700;
  border: 1px solid var(--el-seg-border);
}
.rankings-console :deep(.el-segmented__item-selected .el-segmented__item-label) {
  color: var(--el-seg-color) !important;
}
.rankings-console :deep(.el-segmented__item:not(.el-segmented__item-selected)) {
  color: var(--mono-soft) !important;
}

.rankings-console .table-container {
  border: 1px solid var(--mono-line);
  background: var(--table-container-bg);
  transition: opacity 0.15s ease;
}
.rankings-console .table-container.switching {
  opacity: 0.4;
}
.rankings-console .table-container.entering {
  opacity: 1;
}
.rankings-console :deep(.table-loading) {
  background: var(--loading-bg) !important;
}
.rankings-console :deep(.table-spinner) {
  border-color: var(--loading-border);
  border-top-color: var(--loading-top);
}

.rankings-console :deep(.data-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--table-header-bg);
  --el-table-row-hover-bg-color: var(--table-hover-bg);
  --el-table-border-color: var(--mono-line);
  --el-table-text-color: var(--mono-ink);
  --el-table-header-text-color: var(--mono-soft);
}

.rankings-console .rate-track,
.rankings-console .pos-track {
  height: 3px;
  border-radius: 0;
  background: var(--bar-bg);
}

.rankings-console .rate-bar,
.rankings-console .pos-fill {
  border-radius: 0;
  background: var(--bar-fill);
}

.rankings-console :deep(.el-dialog) {
  border: 1px solid var(--mono-line-strong) !important;
  border-radius: 0 !important;
  background: var(--dialog-bg) !important;
}

.rankings-console :deep(.el-dialog__title) {
  color: var(--mono-ink) !important;
  font-weight: 900 !important;
}

/* ── 详情弹窗内部组件覆写为白色主题 ── */
.rankings-console :deep(.detail-dialog .el-dialog__header),
.rankings-console :deep(.patch-dialog .el-dialog__header) {
  border-bottom-color: var(--mono-line) !important;
}
.rankings-console :deep(.patch-dialog) {
  width: min(92vw, 960px) !important;
  height: auto !important;
  max-height: none !important;
  margin: 4vh auto !important;
}
.rankings-console :deep(.patch-dialog .el-dialog__header) {
  flex-shrink: 0 !important;
  padding: 16px 20px 12px !important;
}
.rankings-console :deep(.patch-dialog .el-dialog__body) {
  overflow-y: auto !important;
  max-height: none !important;
  padding: 0 20px 16px !important;
}
.rankings-console :deep(.match-meta) {
  color: var(--mono-dim) !important;
}
.rankings-console :deep(.battle-card) {
  background: var(--card-bg-alt) !important;
  border-color: var(--mono-line) !important;
}
.rankings-console :deep(.player-card) {
  background: var(--card-bg-alt) !important;
  border-color: var(--mono-line) !important;
}
.rankings-console :deep(.player-card:hover) {
  background: var(--btn-hover-bg) !important;
  border-color: var(--mono-line-strong) !important;
}
.rankings-console :deep(.player-card.camp-blue) { border-left-color: var(--mono-ink) !important; }
.rankings-console :deep(.player-card.camp-red) { border-left-color: var(--mono-dim) !important; }
.rankings-console :deep(.player-header b) { color: var(--mono-ink) !important; }
.rankings-console :deep(.player-header small) { color: var(--text-dim) !important; }
.rankings-console :deep(.player-stats) { color: var(--mono-ink) !important; }
.rankings-console :deep(.player-damage) { color: var(--label-dim) !important; }
.rankings-console :deep(.equip-chip) {
  background: var(--stat-bg) !important;
  border-color: var(--mono-line) !important;
  color: var(--mono-soft) !important;
}
.rankings-console :deep(.profile-card) {
  background: var(--card-bg-alt) !important;
  border-color: var(--mono-line) !important;
}
.rankings-console :deep(.profile-header b) { color: var(--mono-ink) !important; }
.rankings-console :deep(.profile-header small) { color: var(--text-dim) !important; }
.rankings-console :deep(.intro-text) { color: var(--mono-soft) !important; }
.rankings-console :deep(.stat-label) { color: var(--label-dim) !important; }
.rankings-console :deep(.battle-title) { color: var(--mono-ink) !important; }
.rankings-console :deep(.detail-empty) { color: var(--mono-dim) !important; }
.rankings-console :deep(.equip-desc) { color: var(--mono-ink) !important; }
.rankings-console :deep(.equip-desc b) { color: var(--mono-ink) !important; }
.rankings-console :deep(.equip-section h4) { color: var(--mono-ink) !important; }
.rankings-console :deep(.hero-equip-name) { color: var(--mono-ink) !important; }
.rankings-console :deep(.hero-equip-cnt) { color: var(--label-dim) !important; }
.rankings-console :deep(.pos-label) { color: var(--mono-ink) !important; }
.rankings-console :deep(.pos-cnt) { color: var(--text-dim) !important; }
.rankings-console :deep(.skeleton-row) {
  background: linear-gradient(90deg, var(--stat-bg) 25%, var(--btn-hover-bg) 50%, var(--stat-bg) 75%) !important;
}
.rankings-console :deep(.stage-final) { color: var(--mono-ink) !important; background: var(--stat-bg) !important; }
.rankings-console :deep(.stage-playoff) { color: var(--mono-ink) !important; background: var(--stat-bg) !important; }
.rankings-console :deep(.stage-regular) { color: var(--mono-soft) !important; background: var(--stat-bg) !important; }
.rankings-console :deep(.win-blue) { color: var(--mono-ink) !important; }
.rankings-console :deep(.win-red) { color: var(--mono-dim) !important; }
.rankings-console :deep(.rank-badge) { color: var(--mono-ink) !important; }
.rankings-console :deep(.rank-n) { color: var(--mono-dim) !important; }

/* ── theme toggle ── */
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  color: var(--mono-soft);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}
.theme-toggle:hover { color: var(--mono-ink); }
.toggle-track {
  position: relative;
  width: 32px;
  height: 16px;
  border-radius: 8px;
  background: var(--mono-line-strong);
  transition: background 0.2s;
}
.toggle-track.on { background: var(--mono-ink); }
.toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--mono-bg);
  transition: transform 0.2s;
}
.toggle-track.on .toggle-thumb { transform: translateX(16px); }
.rankings-console :deep(.stat-grid) { gap: 8px !important; }
.rankings-console :deep(.stat-item) {
  background: var(--stat-bg) !important;
  border-color: var(--mono-line) !important;
}
.rankings-console :deep(.sv) { color: var(--mono-ink) !important; }
.rankings-console :deep(.sl) { color: var(--label-dim) !important; }

/* ── 荣誉详情弹窗 ── */
.rankings-console :deep(.honor-detail) {
  display: flex; gap: 16px; margin-bottom: 16px;
}
.rankings-console :deep(.honor-big) {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 24px 16px; border: 1px solid var(--mono-line); background: var(--stat-bg);
}
.rankings-console :deep(.honor-big .honor-gold) {
  font-size: 42px; font-weight: 900; color: #d4a017;
  text-shadow: 0 2px 8px rgba(212, 160, 23, 0.2);
}
.rankings-console :deep(.honor-big .honor-silver) {
  font-size: 42px; font-weight: 900; color: #8a8a8a;
  text-shadow: 0 2px 8px rgba(138, 138, 138, 0.2);
}
.rankings-console :deep(.honor-big .sl) {
  font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase;
  color: var(--mono-dim);
}
.rankings-console :deep(.tag-list) {
  display: flex; flex-wrap: wrap; gap: 8px;
}
.rankings-console :deep(.honor-tag) {
  display: inline-block; padding: 5px 12px;
  font-size: 12px; font-weight: 600; border-radius: 0;
}
.rankings-console :deep(.honor-tag.gold) {
  background: rgba(212, 160, 23, 0.1); color: #b8860b;
  border: 1px solid rgba(212, 160, 23, 0.3);
}
.rankings-console :deep(.honor-tag.silver) {
  background: rgba(138, 138, 138, 0.1); color: #666;
  border: 1px solid rgba(138, 138, 138, 0.3);
}
.theme-dark.rankings-console :deep(.honor-tag.gold) {
  background: rgba(212, 160, 23, 0.15); color: #d4a017;
  border-color: rgba(212, 160, 23, 0.35);
}
.theme-dark.rankings-console :deep(.honor-tag.silver) {
  background: rgba(138, 138, 138, 0.12); color: #aaa;
  border-color: rgba(138, 138, 138, 0.3);
}

@media (max-width: 767px) {
  .rankings-console {
    min-height: 100vh;
    padding: 12px 12px calc(78px + env(safe-area-inset-bottom)) !important;
    overflow-x: hidden;
  }

  .rankings-console .command-strip {
    min-height: 56px;
    padding: 10px 12px;
  }

  .rankings-console .brand-mark {
    width: 34px;
    height: 34px;
    font-size: 16px;
  }

  .rankings-console h1 {
    font-size: 17px;
  }

  .rankings-console .theme-toggle small {
    display: none;
  }

  .rankings-console .layout-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
    margin-top: 10px;
  }

  .rankings-console .side-rail,
  .rankings-console .query-panel,
  .rankings-console .agent-panel {
    min-width: 0;
    padding: 12px;
  }

  .rankings-console .league-stats,
  .rankings-console .metric-row,
  .rankings-console .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rankings-console .patch-date {
    display: inline;
    margin-bottom: 0;
    margin-right: 6px;
  }
  .rankings-console .patch-title {
    display: inline;
  }
  .rankings-console .patch-list {
    max-height: 260px;
    overflow-y: auto;
  }

  .rankings-console .table-container {
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .rankings-console :deep(.data-table) {
    min-width: 700px;
  }

  /* 粘性首列 - JS transform 实现 */
  .rankings-console :deep(td.sticky-col) {
    position: relative !important;
    z-index: 2 !important;
    background: var(--mono-panel) !important;
  }
  .rankings-console :deep(td.sticky-col .cell) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    will-change: transform;
  }
  .rankings-console :deep(td.sticky-col::after) {
    content: "";
    position: absolute;
    top: 0; right: -6px; bottom: 0;
    width: 6px;
    background: linear-gradient(90deg, rgba(0,0,0,0.06), transparent);
    pointer-events: none;
    z-index: 1;
  }
  .theme-dark.rankings-console :deep(td.sticky-col::after) {
    background: linear-gradient(90deg, rgba(0,0,0,0.2), transparent);
  }

  .rankings-console :deep(.name-cell) {
    min-width: 0;
  }
  .rankings-console :deep(.name-cell span),
  .rankings-console :deep(.equip-cell span) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rankings-console :deep(.el-segmented) {
    max-width: 100%;
    overflow-x: auto;
  }

  .rankings-console :deep(.el-segmented__group) {
    min-width: max-content;
  }

  .rankings-console :deep(.el-dialog) {
    width: calc(100vw - 16px) !important;
    margin-top: 7vh !important;
  }

  .rankings-console :deep(.detail-dialog .el-dialog__body) {
    max-height: calc(86dvh - 64px);
    padding: 12px;
  }
  .rankings-console :deep(.patch-dialog .el-dialog__body) {
    padding: 0 12px 12px !important;
    max-height: calc(100dvh - 14vh - 80px) !important;
    overflow-y: auto !important;
  }
  .rankings-console .patch-dialog-content img {
    width: 100% !important;
    max-width: 100% !important;
    height: auto !important;
    object-fit: contain !important;
  }

  .rankings-console :deep(.stat-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rankings-console :deep(.hero-table-row) {
    min-width: 340px;
    padding: 7px 8px;
  }
}

.rankings-console :deep(.hero-table) {
  border-color: var(--mono-line);
}
.rankings-console :deep(.hero-table-row) {
  border-bottom-color: var(--mono-line);
}
.rankings-console :deep(.hero-table-header) {
  background: rgba(0, 0, 0, 0.04);
  color: var(--mono-soft);
}

.rankings-console.theme-dark :deep(.hero-table-header) {
  background: var(--bg-raised);
  color: var(--text-secondary);
}
</style>

<style>
/* 强制覆盖 Element Plus segmented 选中文字颜色 */
.rankings-console .el-select__placeholder,
.rankings-console .el-select__selected-item .el-select__placeholder {
  color: var(--mono-ink) !important;
}
.rankings-console .el-select__placeholder.is-transparent {
  color: var(--mono-dim) !important;
}
.rankings-console .el-select__caret { color: var(--mono-soft) !important; }

.rankings-console .el-segmented__item.is-selected,
.rankings-console .el-segmented__item.is-selected .el-segmented__item-label {
  color: var(--mono-ink) !important;
}

/* 下拉面板全局样式 */
.el-select__popper {
  border: 1px solid rgba(0,0,0,0.3) !important;
  border-radius: 0 !important;
  box-shadow: 4px 4px 0 rgba(0,0,0,0.08) !important;
  background: #fff !important;
}
.el-select-dropdown__list { padding: 4px 0 !important; }
.el-select-dropdown__item {
  padding: 8px 16px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  color: #1a1a1a !important;
  border-radius: 0 !important;
  transition: background 0.1s, padding-left 0.15s;
}
.el-select-dropdown__item.is-hovering {
  background: rgba(0,0,0,0.04) !important;
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
  border-color: rgba(255,255,255,0.25) !important;
  background: #1a1a1a !important;
  box-shadow: 4px 4px 0 rgba(0,0,0,0.3) !important;
}
.theme-dark .el-select-dropdown__item {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-hovering {
  background: rgba(255,255,255,0.06) !important;
}
.theme-dark .el-select-dropdown__item.is-selected {
  color: #e8e8e8 !important;
}
.theme-dark .el-select-dropdown__item.is-selected::after {
  background: #e8e8e8;
}

/* 版本动态弹窗：遮罩层可滚动，弹窗完整展示 */
.patch-dialog-overlay {
  overflow-y: auto !important;
  align-items: flex-start !important;
}
.patch-dialog-overlay .el-overlay-dialog {
  margin: 4vh auto !important;
}

/* 弹窗内容图片：强制缩放，覆盖腾讯 HTML 内联 width/height 属性 */
.patch-dialog-content img {
  max-width: 100% !important;
  width: auto !important;
  height: auto !important;
  display: block;
  margin: 6px 0;
  border-radius: 4px;
}
/* 小头像（width<=100 的方图）保持小尺寸 */
.patch-dialog-content img[width="86"],
.patch-dialog-content img[width="64"],
.patch-dialog-content img[width="48"] {
  width: 36px !important;
  height: 36px !important;
  display: inline-block !important;
  vertical-align: middle;
  border-radius: 50%;
  margin: 2px 4px 2px 0;
}
@media (max-width: 767px) {
  .patch-dialog-content img {
    width: 100% !important;
    max-width: 100% !important;
    height: auto !important;
    object-fit: contain !important;
  }
  .patch-dialog-content img[width="86"],
  .patch-dialog-content img[width="64"],
  .patch-dialog-content img[width="48"] {
    width: 28px !important;
    height: 28px !important;
  }
}
</style>
