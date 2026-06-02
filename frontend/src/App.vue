<template>
  <LoadingScreen />
  <SideBar />
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
          <el-button :icon="Aim" :loading="loading.sync" @click="runSync(`/api/sync/latest/deep-incremental?matchLimit=10${selectedLeagueId ? '&leagueId=' + selectedLeagueId : ''}`)">对局详情</el-button>
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
              <strong>{{ queryMode === 'honors' ? '全历史' : (currentLeague?.leagueName || selectedLeagueId || 'AUTO') }}</strong>
            </div>
          </div>

          <div class="table-container" :class="tableState">
          <div v-if="loading.query" class="table-loading">
            <div class="table-spinner"></div>
          </div>
          <el-table class="data-table" :data="tableRows" height="360" empty-text=""
            row-class-name="clickable-row"
            @row-click="onRowClick($event)">
            <el-table-column label="#" width="60" align="center">
              <template #default="{ row, $index }">
                <span v-if="queryMode === 'ranking' && row.placement && row.placement > 0 && row.placement < 999" class="rank-badge">{{ row.placement }}</span>
                <span v-else class="rank-badge rank-n">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column v-for="col in tableColumns" :key="col.prop" :prop="col.prop" :label="col.label"
              min-width="120"
              :align="isNameCol(col.prop) ? 'left' : 'center'">
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
                  <span class="pos-label">{{ p.positionDesc || '未知' }}</span>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import LoadingScreen from './components/LoadingScreen.vue'
import SideBar from './components/SideBar.vue'
import {
  Aim,
  ChatLineRound,
  DataAnalysis,
  Download,
  Lightning,
  Position,
  Refresh,
  RefreshRight,
  Search
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const md = new MarkdownIt({ html: false, breaks: true })
function renderMd(text) { return md.render(text || '') }

const leagues = ref([])
const jobs = ref([])
const selectedLeagueId = ref('')
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
const detailVisible = ref(false)
const detailType = ref('')
const detailTitle = ref('')
const detailLoading = ref(false)
const detailData = ref(null)
const detailRow = ref(null)

const loading = ref({
  sync: false,
  query: false,
  agent: false
})

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

const autoLoadModes = ['ranking', 'playerTop', 'heroTop', 'honors']

const rawData = computed(() => {
  const data = queryResult.value?.data
  if (Array.isArray(data)) return data
  return Array.isArray(queryResult.value) ? queryResult.value : []
})

const tableRows = computed(() => {
  let rows = rawData.value.map(normalizeRow)
  // 比赛榜按日期降序（最近的排最前）
  if (queryMode.value === 'match') {
    rows = [...rows].sort((a, b) => {
      const da = a._rawStartTime || ''
      const db = b._rawStartTime || ''
      return db.localeCompare(da)
    })
  }
  return rows
})
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
    queryResult.value = await request(url)
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

  // 流式接收
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

function closeDetail() {
  detailVisible.value = false
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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const POS_ORDER = { 6: 1, 5: 2, 2: 3, 7: 4, 4: 5 }

function getWinnerTeamName(battle, camp1Name, camp2Name) {
  const winCamp = battle.battle?.winCamp
  if (!battle.players?.length) return winCamp === 1 ? camp1Name : camp2Name
  // 通过选手 teamName 判断 winCamp 对应哪支队伍
  for (const pd of battle.players) {
    if (pd.player?.camp === winCamp) return pd.player.teamName
  }
  return winCamp === 1 ? camp1Name : camp2Name
}

function getPlayerMatchCamp(player, camp1Name) {
  // 根据 teamName 归一化到比赛级别的 camp（1=蓝方, 2=红方）
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
  await refreshAll()
})
</script>
