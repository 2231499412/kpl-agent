package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.kpl.agent.api.KplApiClient;
import com.kpl.agent.entity.*;
import com.kpl.agent.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据同步服务：从 KPL 官方 API 拉取数据并存入 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final KplApiClient apiClient;
    private final LeagueMapper leagueMapper;
    private final MatchMapper matchMapper;
    private final BattleMapper battleMapper;
    private final BattlePlayerMapper battlePlayerMapper;
    private final HeroStatsMapper heroStatsMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final TeamStatsMapper teamStatsMapper;
    private final SyncJobMapper syncJobMapper;
    private final SyncCursorMapper syncCursorMapper;
    private final QueryCacheService queryCacheService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 赛事列表同步 ====================

    /** 同步所有赛事列表 */
    public int syncLeagues() {
        SyncJob job = startJob("LEAGUES", "all", false);
        try {
            int count = doSyncLeagues();
            finishJob(job, "SUCCESS", "同步赛事列表完成，新增 " + count + " 条", null);
            return count;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    private int doSyncLeagues() {
        JsonNode root = apiClient.getLeagues();
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取赛事列表失败");
            return 0;
        }

        JsonNode results = root.get("results");
        int count = 0;
        for (JsonNode node : results) {
            String leagueId = node.get("league_id").asText();
            // 已存在则跳过
            if (leagueMapper.selectOne(
                    new LambdaQueryWrapper<League>().eq(League::getLeagueId, leagueId)) != null) {
                continue;
            }
            League league = new League();
            league.setLeagueId(leagueId);
            league.setLeagueName(node.get("league_name").asText());
            league.setLeagueType(node.get("league_type_name").asText());
            league.setYear(node.get("year").asInt());
            league.setSeason(node.get("season").asInt());
            league.setCcLeagueId(node.path("cc_league_id").asText(""));
            league.setStartTime(parseDateTime(node.path("start_time").asText()));
            league.setEndTime(parseDateTime(node.path("end_time").asText()));
            league.setStatus(node.get("status").asInt());
            league.setLeagueIcon(node.path("league_icon").asText(""));
            leagueMapper.insert(league);
            count++;
        }
        log.info("同步赛事列表完成，新增 {} 条", count);
        return count;
    }

    // ==================== 比赛同步 ====================

    /** 同步某赛事下的所有比赛 */
    public int syncMatches(String leagueId) {
        JsonNode root = apiClient.getMatches(leagueId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取比赛列表失败: leagueId={}", leagueId);
            return 0;
        }

        JsonNode results = root.get("results");
        int count = 0;
        for (JsonNode node : results) {
            String matchId = node.get("match_id").asText();
            Match match = matchMapper.selectOne(
                    new LambdaQueryWrapper<Match>().eq(Match::getMatchId, matchId));
            boolean exists = match != null;
            if (!exists) {
                match = new Match();
            }
            match.setMatchId(matchId);
            match.setLeagueId(leagueId);

            JsonNode camp1 = node.get("camp1");
            match.setCamp1TeamId(camp1.get("team_id").asText());
            match.setCamp1TeamName(camp1.get("team_name").asText());
            match.setCamp1Score(camp1.get("score").asInt());

            JsonNode camp2 = node.get("camp2");
            match.setCamp2TeamId(camp2.get("team_id").asText());
            match.setCamp2TeamName(camp2.get("team_name").asText());
            match.setCamp2Score(camp2.get("score").asInt());

            match.setBo(node.get("bo").asInt());
            match.setWinCamp(node.get("win_camp").asInt());
            match.setStatus(node.get("status").asInt());
            match.setMatchStage(node.path("match_stage_name").asText(""));
            match.setMatchStageDesc(node.path("match_stage_desc").asText(""));
            match.setStartTime(parseDateTime(node.path("start_time").asText()));
            match.setMatchAddress(node.path("match_address").asText(""));

            if (exists) {
                matchMapper.updateById(match);
            } else {
                matchMapper.insert(match);
            }
            count++;
        }
        log.info("同步比赛完成: leagueId={}, 写入 {} 条", leagueId, count);
        return count;
    }

    // ==================== 对局同步 ====================

    /** 同步某场比赛的所有对局 */
    public int syncBattles(String matchId) {
        JsonNode root = apiClient.getMatchBattles(matchId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取对局列表失败: matchId={}", matchId);
            return 0;
        }

        JsonNode results = root.get("results");
        int count = 0;
        for (JsonNode node : results) {
            String battleId = node.get("battle_id").asText();
            if (battleMapper.selectOne(
                    new LambdaQueryWrapper<Battle>().eq(Battle::getBattleId, battleId)) != null) {
                continue;
            }
            Battle battle = new Battle();
            battle.setBattleId(battleId);
            battle.setMatchId(matchId);
            battle.setBattleSeq(node.get("battle_seq").asInt());
            battle.setWinCamp(node.get("win_camp").asInt());
            battle.setGameDuration(node.get("game_duration").asLong());
            battle.setStatus(node.get("status").asInt());
            battleMapper.insert(battle);
            count++;
        }
        log.info("同步对局完成: matchId={}, 新增 {} 条", matchId, count);
        return count;
    }

    /** 同步某局的选手详细数据 */
    @Transactional
    public int syncBattleDetail(String battleId) {
        JsonNode root = apiClient.getBattleDetail(battleId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取对局详情失败: battleId={}", battleId);
            return 0;
        }

        JsonNode data = root.get("data");
        JsonNode players = data.get("battle_player_list");
        if (players == null || !players.isArray()) return 0;

        // 先删除旧数据（幂等）
        battlePlayerMapper.delete(
                new LambdaQueryWrapper<BattlePlayer>().eq(BattlePlayer::getBattleId, battleId));

        int count = 0;
        for (JsonNode p : players) {
            BattlePlayer bp = new BattlePlayer();
            bp.setBattleId(battleId);
            bp.setTeamId(p.path("team_id").asText(""));
            bp.setTeamName(p.path("team_name").asText(""));
            bp.setPlayerName(p.path("actual_player_name").asText(""));
            bp.setHeroId(p.path("hero_id").asInt(0));
            bp.setHeroName(p.path("hero_name").asText(""));
            bp.setCamp(p.path("camp").asInt(0));
            bp.setKillNum(p.path("kill_num").asInt(0));
            bp.setDeathNum(p.path("death_num").asInt(0));
            bp.setAssistNum(p.path("assist_num").asInt(0));
            bp.setGold(p.path("gold").asInt(0));
            bp.setHurtTotal(p.path("hurt_total").asLong(0));
            bp.setHurtToHero(p.path("hurt_total").asLong(0));
            bp.setBeHurtTotal(p.path("be_hurt_total").asLong(0));
            bp.setKda(p.path("kda").asDouble(0));
            bp.setMvpScore(p.path("mvp_score").asDouble(0));
            bp.setIsMvp(p.path("is_mvp").asInt(0));
            bp.setParticipationRate(p.path("participation_rate").asDouble(0));
            battlePlayerMapper.insert(bp);
            count++;
        }
        log.info("同步对局详情完成: battleId={}, 选手数 {}", battleId, count);
        return count;
    }

    // ==================== 统计榜同步 ====================

    /** 同步英雄赛季统计 */
    public int syncHeroStats(String leagueId) {
        JsonNode root = apiClient.getHeroStats(leagueId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取英雄榜失败: leagueId={}", leagueId);
            return 0;
        }

        JsonNode data = root.get("data");
        // 先清空该赛季旧数据
        heroStatsMapper.delete(
                new LambdaQueryWrapper<HeroStats>().eq(HeroStats::getLeagueId, leagueId));

        int count = 0;
        for (JsonNode node : data) {
            JsonNode heroInfo = node.get("hero_info");
            JsonNode stats = node.get("statistics_info");
            JsonNode bp = node.get("bp_statistics_info");

            HeroStats hs = new HeroStats();
            hs.setLeagueId(leagueId);
            hs.setHeroId(heroInfo.get("hero_id").asInt());
            hs.setHeroName(heroInfo.get("hero_name").asText());
            hs.setHeroIcon(heroInfo.path("hero_icon").asText(""));
            hs.setBattleCount(stats.path("battle_count").asInt(0));
            hs.setWinRate(stats.path("win_rate").asDouble(0));
            hs.setAvgKill(stats.path("avg_kill_num").asDouble(0));
            hs.setAvgDeath(stats.path("avg_death_num").asDouble(0));
            hs.setAvgAssist(stats.path("avg_assist_num").asDouble(0));
            hs.setAvgKda(stats.path("avg_kda").asDouble(0));
            hs.setAvgGold(stats.path("avg_gold").asDouble(0));
            hs.setBanNum(bp.path("ban_num").asInt(0));
            hs.setBanRate(bp.path("ban_rate").asDouble(0));
            hs.setPickNum(bp.path("pick_num").asInt(0));
            hs.setPickRate(bp.path("pick_rate").asDouble(0));

            heroStatsMapper.insert(hs);
            count++;
        }
        log.info("同步英雄榜完成: leagueId={}, 共 {} 条", leagueId, count);
        return count;
    }

    /** 同步选手赛季统计 */
    public int syncPlayerStats(String leagueId) {
        JsonNode root = apiClient.getPlayerStats(leagueId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取选手榜失败: leagueId={}", leagueId);
            return 0;
        }

        playerStatsMapper.delete(
                new LambdaQueryWrapper<PlayerStats>().eq(PlayerStats::getLeagueId, leagueId));

        JsonNode data = root.get("data");
        int count = 0;
        for (JsonNode node : data) {
            JsonNode pInfo = node.get("player_info");
            JsonNode stats = node.get("statistics_info");

            PlayerStats ps = new PlayerStats();
            ps.setLeagueId(leagueId);
            ps.setPlayerName(pInfo.get("player_name").asText());
            ps.setTeamName(pInfo.path("team_name").asText(""));
            ps.setPosition(pInfo.path("position").asInt(0));
            ps.setPositionDesc(pInfo.path("position_desc").asText(""));
            ps.setBattleCount(stats.path("battle_count").asInt(0));
            ps.setWinRate(stats.path("win_rate").asDouble(0));
            ps.setAvgKill(stats.path("avg_kill_num").asDouble(0));
            ps.setAvgDeath(stats.path("avg_death_num").asDouble(0));
            ps.setAvgAssist(stats.path("avg_assist_num").asDouble(0));
            ps.setAvgKda(stats.path("avg_kda").asDouble(0));
            ps.setAvgGold(stats.path("avg_gold").asDouble(0));
            ps.setAvgHurtToHero(stats.path("avg_hurt_to_hero_total").asDouble(0));
            ps.setAvgBeHurt(stats.path("avg_be_hurt_total").asDouble(0));
            ps.setAvgParticipationRate(stats.path("avg_participation_rate").asDouble(0));

            playerStatsMapper.insert(ps);
            count++;
        }
        log.info("同步选手榜完成: leagueId={}, 共 {} 条", leagueId, count);
        return count;
    }

    /** 同步战队赛季统计 */
    public int syncTeamStats(String leagueId) {
        JsonNode root = apiClient.getTeamStats(leagueId);
        if (root == null || root.get("code").asInt() != 200) {
            log.error("获取战队榜失败: leagueId={}", leagueId);
            return 0;
        }

        teamStatsMapper.delete(
                new LambdaQueryWrapper<TeamStats>().eq(TeamStats::getLeagueId, leagueId));

        JsonNode data = root.get("data");
        int count = 0;
        for (JsonNode node : data) {
            JsonNode tInfo = node.get("team_info");
            JsonNode stats = node.get("statistics_info");

            TeamStats ts = new TeamStats();
            ts.setLeagueId(leagueId);
            ts.setTeamId(tInfo.get("team_id").asText());
            ts.setTeamName(tInfo.get("team_name").asText());
            ts.setTeamIcon(tInfo.path("team_icon").asText(""));
            ts.setBattleCount(stats.path("battle_count").asInt(0));
            ts.setWinRate(stats.path("win_rate").asDouble(0));
            ts.setAvgKill(stats.path("avg_kill_num").asDouble(0));
            ts.setAvgDeath(stats.path("avg_death_num").asDouble(0));
            ts.setAvgKda(stats.path("avg_kda").asDouble(0));
            ts.setAvgGold(stats.path("avg_gold").asDouble(0));
            ts.setAvgFirstBlood(stats.path("avg_first_blood_cnt").asDouble(0));
            ts.setAvgPushTower(stats.path("avg_push_tower_num").asDouble(0));
            ts.setAvgDragonControlRate(stats.path("avg_kill_all_tyrant_control_rate").asDouble(0));

            teamStatsMapper.insert(ts);
            count++;
        }
        log.info("同步战队榜完成: leagueId={}, 共 {} 条", leagueId, count);
        return count;
    }

    // ==================== 一键同步入口 ====================

    /**
     * 同步指定赛季的全部数据（赛事→比赛→对局→详情→统计榜）
     * @param leagueId 赛事ID
     * @param deepSync 是否深度同步（含每局详情，耗时较长）
     */
    public String syncSeason(String leagueId, boolean deepSync) {
        SyncJob job = startJob("SEASON", leagueId, deepSync);
        try {
            String result = doSyncSeason(leagueId, deepSync);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    private String doSyncSeason(String leagueId, boolean deepSync) {
        log.info("开始同步赛季: leagueId={}, deepSync={}", leagueId, deepSync);

        // 1. 确保赛事存在
        doSyncLeagues();

        // 2. 同步比赛
        int matchCount = syncMatches(leagueId);

        // 3. 深度同步：对局 + 选手数据
        if (deepSync) {
            List<Match> matches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>().eq(Match::getLeagueId, leagueId));
            for (Match m : matches) {
                syncBattles(m.getMatchId());
            }
            if (!matches.isEmpty()) {
                List<Battle> battles = battleMapper.selectList(
                        new LambdaQueryWrapper<Battle>()
                                .in(Battle::getMatchId,
                                        matches.stream().map(Match::getMatchId).toList()));
                for (Battle b : battles) {
                    syncBattleDetail(b.getBattleId());
                }
            }
        }

        // 4. 同步统计榜
        int heroCount = syncHeroStats(leagueId);
        int playerCount = syncPlayerStats(leagueId);
        int teamCount = syncTeamStats(leagueId);

        String result = String.format("同步完成: 比赛%d, 英雄%d, 选手%d, 战队%d",
                matchCount, heroCount, playerCount, teamCount);
        log.info(result);
        queryCacheService.evictByPattern("kpl:" + leagueId + ":*");
        return result;
    }

    /** 同步最新赛季（取赛事列表最后一条KPL赛事） */
    public String syncLatestSeason() {
        SyncJob job = startJob("LATEST_SEASON", "latest", false);
        try {
            doSyncLeagues();
            League latest = leagueMapper.selectOne(
                    new LambdaQueryWrapper<League>()
                            .in(League::getLeagueType, "kpl", "winter_champion_cup", "world_champion_cup")
                            .orderByDesc(League::getStartTime)
                            .last("LIMIT 1"));
            if (latest == null) {
                String result = "未找到KPL赛事";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }
            String result = doSyncSeason(latest.getLeagueId(), false);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /** 增量同步最新赛季：刷新赛事、比赛状态和榜单，并记录游标。 */
    public String syncLatestIncremental() {
        SyncJob job = startJob("LATEST_INCREMENTAL", "latest", false);
        try {
            doSyncLeagues();
            League latest = leagueMapper.selectOne(
                    new LambdaQueryWrapper<League>()
                            .in(League::getLeagueType, "kpl", "winter_champion_cup", "world_champion_cup")
                            .orderByDesc(League::getStartTime)
                            .last("LIMIT 1"));
            if (latest == null) {
                String result = "未找到KPL赛事";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }

            int matchCount = syncMatches(latest.getLeagueId());
            int heroCount = syncHeroStats(latest.getLeagueId());
            int playerCount = syncPlayerStats(latest.getLeagueId());
            int teamCount = syncTeamStats(latest.getLeagueId());
            queryCacheService.evictByPattern("kpl:" + latest.getLeagueId() + ":*");
            upsertCursor("latest_season", latest.getLeagueId());

            String result = String.format("增量同步完成: 赛事%s, 比赛%d, 英雄%d, 选手%d, 战队%d",
                    latest.getLeagueId(), matchCount, heroCount, playerCount, teamCount);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /**
     * 深度增量同步：只为已结束且缺少选手详情的比赛补齐 battle / battle_player 数据。
     */
    public String syncLatestDeepIncremental(int matchLimit) {
        int safeLimit = Math.max(1, Math.min(matchLimit, 50));
        SyncJob job = startJob("LATEST_DEEP_INCREMENTAL", "latest:" + safeLimit, true);
        try {
            doSyncLeagues();
            League latest = leagueMapper.selectOne(
                    new LambdaQueryWrapper<League>()
                            .in(League::getLeagueType, "kpl", "winter_champion_cup", "world_champion_cup")
                            .orderByDesc(League::getStartTime)
                            .last("LIMIT 1"));
            if (latest == null) {
                String result = "未找到KPL赛事";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }

            syncMatches(latest.getLeagueId());
            List<Match> finishedMatches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>()
                            .eq(Match::getLeagueId, latest.getLeagueId())
                            .eq(Match::getStatus, 2)
                            .orderByDesc(Match::getStartTime)
                            .last("LIMIT " + safeLimit));

            int battleCount = 0;
            int detailCount = 0;
            int skipped = 0;
            for (Match match : finishedMatches) {
                battleCount += syncBattles(match.getMatchId());
                List<Battle> battles = battleMapper.selectList(
                        new LambdaQueryWrapper<Battle>()
                                .eq(Battle::getMatchId, match.getMatchId())
                                .orderByAsc(Battle::getBattleSeq));
                for (Battle battle : battles) {
                    Long playerRows = battlePlayerMapper.selectCount(
                            new LambdaQueryWrapper<BattlePlayer>()
                                    .eq(BattlePlayer::getBattleId, battle.getBattleId()));
                    if (playerRows != null && playerRows > 0) {
                        skipped++;
                        continue;
                    }
                    detailCount += syncBattleDetail(battle.getBattleId());
                }
            }

            queryCacheService.evictByPattern("kpl:" + latest.getLeagueId() + ":match:*");
            upsertCursor("latest_deep_incremental", latest.getLeagueId() + ":" + safeLimit);
            String result = String.format("深度增量同步完成: 扫描比赛%d, 新增对局%d, 补齐选手详情%d, 跳过已存在对局%d",
                    finishedMatches.size(), battleCount, detailCount, skipped);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /**
     * 同步所有 KPL 赛事（2019 至今，数据量大，耗时较长）
     */
    public String syncAll() {
        SyncJob job = startJob("ALL", "all", false);
        try {
            doSyncLeagues();
            List<League> leagues = leagueMapper.selectList(
                    new LambdaQueryWrapper<League>()
                            .in(League::getLeagueType, "kpl", "winter_champion_cup", "world_champion_cup")
                            .orderByAsc(League::getStartTime));
            if (leagues.isEmpty()) {
                String result = "未找到KPL赛事";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }

            int totalMatches = 0, totalHeroes = 0, totalPlayers = 0, totalTeams = 0;
            for (League league : leagues) {
                log.info("同步赛事: {} ({})", league.getLeagueName(), league.getLeagueId());
                totalMatches += syncMatches(league.getLeagueId());
                totalHeroes += syncHeroStats(league.getLeagueId());
                totalPlayers += syncPlayerStats(league.getLeagueId());
                totalTeams += syncTeamStats(league.getLeagueId());
                queryCacheService.evictByPattern("kpl:" + league.getLeagueId() + ":*");
            }

            String result = String.format("同步完成: 赛事%d, 比赛%d, 英雄%d, 选手%d, 战队%d",
                    leagues.size(), totalMatches, totalHeroes, totalPlayers, totalTeams);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /**
     * 同步指定年份的 KPL 赛事
     */
    public String syncByYear(int year) {
        SyncJob job = startJob("BY_YEAR", "year:" + year, false);
        try {
            doSyncLeagues();
            List<League> leagues = leagueMapper.selectList(
                    new LambdaQueryWrapper<League>()
                            .in(League::getLeagueType, "kpl", "winter_champion_cup", "world_champion_cup")
                            .eq(League::getYear, year)
                            .orderByAsc(League::getStartTime));
            if (leagues.isEmpty()) {
                String result = "未找到" + year + "年KPL赛事";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }

            int totalMatches = 0, totalHeroes = 0, totalPlayers = 0, totalTeams = 0;
            for (League league : leagues) {
                log.info("同步赛事: {} ({})", league.getLeagueName(), league.getLeagueId());
                totalMatches += syncMatches(league.getLeagueId());
                totalHeroes += syncHeroStats(league.getLeagueId());
                totalPlayers += syncPlayerStats(league.getLeagueId());
                totalTeams += syncTeamStats(league.getLeagueId());
                queryCacheService.evictByPattern("kpl:" + league.getLeagueId() + ":*");
            }

            String result = String.format("同步完成: 赛事%d, 比赛%d, 英雄%d, 选手%d, 战队%d",
                    leagues.size(), totalMatches, totalHeroes, totalPlayers, totalTeams);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /** 查询最新同步任务，方便接口展示同步状态。 */
    public List<SyncJob> latestJobs(int limit) {
        return syncJobMapper.selectList(
                new LambdaQueryWrapper<SyncJob>()
                        .orderByDesc(SyncJob::getStartedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    /** 查询单个同步任务。 */
    public SyncJob getJob(Long id) {
        SyncJob job = syncJobMapper.selectById(id);
        if (job == null) {
            throw new IllegalArgumentException("同步任务不存在: " + id);
        }
        return job;
    }

    /** 重试失败的同步任务。 */
    public String retryJob(Long id) {
        SyncJob job = getJob(id);
        if (!"FAILED".equals(job.getStatus())) {
            throw new IllegalArgumentException("只有失败任务可以重试");
        }
        return switch (job.getJobType()) {
            case "LEAGUES" -> "同步赛事列表完成，新增 " + syncLeagues() + " 条";
            case "SEASON" -> syncSeason(job.getTarget(), Boolean.TRUE.equals(job.getDeepSync()));
            case "LATEST_SEASON" -> syncLatestSeason();
            case "LATEST_INCREMENTAL" -> syncLatestIncremental();
            case "LATEST_DEEP_INCREMENTAL" -> syncLatestDeepIncremental(parseLimitFromTarget(job.getTarget(), 10));
            case "ALL" -> syncAll();
            case "BY_YEAR" -> syncByYear(parseYearFromTarget(job.getTarget()));
            default -> throw new IllegalArgumentException("不支持重试的任务类型: " + job.getJobType());
        };
    }

    /** 查询同步游标。 */
    public List<SyncCursor> listCursors() {
        return syncCursorMapper.selectList(
                new LambdaQueryWrapper<SyncCursor>().orderByDesc(SyncCursor::getUpdatedAt));
    }

    private SyncJob startJob(String jobType, String target, boolean deepSync) {
        SyncJob job = new SyncJob();
        job.setJobType(jobType);
        job.setTarget(target);
        job.setDeepSync(deepSync);
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        syncJobMapper.insert(job);
        return job;
    }

    private void finishJob(SyncJob job, String status, String resultMessage, String errorMessage) {
        job.setStatus(status);
        job.setResultMessage(resultMessage);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(LocalDateTime.now());
        syncJobMapper.updateById(job);
    }

    private void upsertCursor(String cursorKey, String cursorValue) {
        SyncCursor cursor = syncCursorMapper.selectOne(
                new LambdaQueryWrapper<SyncCursor>().eq(SyncCursor::getCursorKey, cursorKey));
        if (cursor == null) {
            cursor = new SyncCursor();
            cursor.setCursorKey(cursorKey);
            cursor.setCursorValue(cursorValue);
            cursor.setLastSuccessAt(LocalDateTime.now());
            syncCursorMapper.insert(cursor);
        } else {
            cursor.setCursorValue(cursorValue);
            cursor.setLastSuccessAt(LocalDateTime.now());
            syncCursorMapper.updateById(cursor);
        }
    }

    private int parseLimitFromTarget(String target, int fallback) {
        if (target == null || !target.contains(":")) {
            return fallback;
        }
        try {
            return Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseYearFromTarget(String target) {
        if (target != null && target.contains(":")) {
            try {
                return Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
            } catch (NumberFormatException ignored) {}
        }
        return Year.now().getValue();
    }

    // ==================== 工具方法 ====================

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return LocalDateTime.parse(text, DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
