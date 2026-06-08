package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.kpl.agent.api.KplApiClient;
import com.kpl.agent.entity.*;
import com.kpl.agent.mapper.*;
import com.kpl.agent.mapper.BattlePlayerEquipMapper;
import com.kpl.agent.mapper.EquipBaseInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final BattlePlayerEquipMapper battlePlayerEquipMapper;
    private final HeroStatsMapper heroStatsMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final TeamStatsMapper teamStatsMapper;
    private final SyncJobMapper syncJobMapper;
    private final SyncCursorMapper syncCursorMapper;
    private final QueryCacheService queryCacheService;
    private final EquipBaseInfoMapper equipBaseInfoMapper;

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
    public int syncBattleDetail(String battleId, String leagueId) {
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

        // 先删除旧装备数据（幂等）
        battlePlayerEquipMapper.delete(
                new LambdaQueryWrapper<BattlePlayerEquip>().eq(BattlePlayerEquip::getBattleId, battleId));

        // 归一化 camp：API 每局的 camp 分配可能和比赛级别相反
        // 通过 match 的 camp1TeamId/camp2TeamId 来映射
        int apiWinCamp = data.path("win_camp").asInt(0);
        Battle battle = battleMapper.selectOne(
                new LambdaQueryWrapper<Battle>().eq(Battle::getBattleId, battleId));
        Match match = battle != null ? matchMapper.selectOne(
                new LambdaQueryWrapper<Match>().eq(Match::getMatchId, battle.getMatchId())) : null;

        // 从选手数据推断 API camp -> match camp 的映射
        // 选手 team_id 为 "10027"(成都AG) 在 API 中 camp=2，但在 match 中是 camp1
        // 所以需要翻转: match_camp = (api_camp == apiCamp1TeamIs ? 1 : 2)
        int campFlip = 0; // 0=不翻转, 1=翻转(1<->2)
        if (match != null && match.getCamp1TeamId() != null) {
            for (JsonNode p : players) {
                String tid = p.path("team_id").asText("");
                int apiCamp = p.path("camp").asInt(0);
                if (tid.equals(match.getCamp1TeamId())) {
                    campFlip = (apiCamp == 1) ? 0 : 1;
                    break;
                } else if (tid.equals(match.getCamp2TeamId())) {
                    campFlip = (apiCamp == 2) ? 0 : 1;
                    break;
                }
            }
            // 修正 battle 的 win_camp
            if (campFlip == 1 && battle != null) {
                int normalizedWinCamp = (apiWinCamp == 1) ? 2 : 1;
                battle.setWinCamp(normalizedWinCamp);
                battleMapper.updateById(battle);
            }
        }

        int count = 0;
        for (JsonNode p : players) {
            BattlePlayer bp = new BattlePlayer();
            bp.setBattleId(battleId);
            bp.setTeamId(p.path("team_id").asText(""));
            bp.setTeamName(p.path("team_name").asText(""));
            bp.setPlayerName(p.path("actual_player_name").asText(""));
            bp.setHeroId(p.path("hero_id").asInt(0));
            bp.setHeroName(p.path("hero_name").asText(""));
            // 归一化 camp 值
            int apiCamp = p.path("camp").asInt(0);
            bp.setCamp(campFlip == 1 ? (apiCamp == 1 ? 2 : 1) : apiCamp);
            bp.setKillNum(p.path("kill_num").asInt(0));
            bp.setDeathNum(p.path("death_num").asInt(0));
            bp.setAssistNum(p.path("assist_num").asInt(0));
            bp.setGold(p.path("gold").asInt(0));
            bp.setHurtTotal(p.path("hurt_total").asLong(0));
            bp.setHurtToHero(p.path("hurt_to_hero_total").asLong(0));
            bp.setBeHurtTotal(p.path("be_hurt_total").asLong(0));
            bp.setKda(p.path("kda").asDouble(0));
            bp.setMvpScore(p.path("mvp_score").asDouble(0));
            bp.setIsMvp(p.path("is_mvp").asInt(0));
            bp.setParticipationRate(p.path("participation_rate").asDouble(0));
            // 新增字段
            JsonNode summoner = p.path("SummonerAbilityInfo");
            bp.setSummonerAbilityId(summoner.path("summoner_ability_id").asInt(0));
            bp.setSummonerAbilityName(summoner.path("summoner_ability_name").asText(""));
            bp.setHurtTotalRate(p.path("hurt_total_rate").asDouble(0));
            bp.setBeHurtTotalRate(p.path("be_hurt_total_rate").asDouble(0));
            bp.setHurtToHeroTotal(p.path("hurt_to_hero_total").asLong(0));
            bp.setBeHurtByHeroTotal(p.path("be_hurt_by_hero_total").asLong(0));
            bp.setHurtToHeroTotalRate(p.path("hurt_to_hero_total_rate").asDouble(0));
            bp.setBeHurtByHeroTotalRate(p.path("be_hurt_by_hero_total_rate").asDouble(0));
            int pos = p.path("position").asInt(0);
            String posDesc = p.path("position_desc").asText("");
            int heroId = bp.getHeroId();
            // 仅当 API 未返回 position 时，才用英雄映射兜底
            if (pos == 0) {
                Integer heroPos = HERO_POSITION.get(heroId);
                if (heroPos != null) {
                    pos = heroPos;
                } else if (!posDesc.isEmpty()) {
                    pos = inferPosition(posDesc);
                }
            }
            bp.setPosition(pos);
            bp.setPositionDesc(posDesc);
            bp.setIsLoseMvp(p.path("is_lose_mvp").asInt(0));
            bp.setSymbolIds(p.path("symbol_ids").asText(""));
            battlePlayerMapper.insert(bp);

            // 解析装备数据
            JsonNode equipList = p.path("BriefEquipList");
            if (equipList.isArray()) {
                for (int i = 0; i < equipList.size(); i++) {
                    JsonNode e = equipList.get(i);
                    BattlePlayerEquip equip = new BattlePlayerEquip();
                    equip.setBattleId(battleId);
                    equip.setLeagueId(leagueId);
                    equip.setPlayerName(bp.getPlayerName());
                    equip.setHeroId(bp.getHeroId());
                    equip.setEquipId(e.path("equip_id").asInt(0));
                    equip.setEquipName(e.path("equip_name").asText(""));
                    equip.setEquipIcon(e.path("equip_icon").asText(""));
                    equip.setEquipDescGain(e.path("equip_desc_gain").asText(""));
                    equip.setEquipDescFunction(e.path("equip_desc_function").asText(""));
                    equip.setEquipOrder(i + 1);
                    battlePlayerEquipMapper.insert(equip);
                }
            }
            count++;
        }
        // 阵营内 position 去重修正：当同一阵营有多个选手被标记为同一 position 时，用英雄映射修正
        fixDuplicatePositions(battleId);

        log.info("同步对局详情完成: battleId={}, 选手数 {}", battleId, count);
        return count;
    }

    /** 英雄→分路映射（heroId → position），用于修正 API 错误数据 */
    private static final Map<Integer, Integer> HERO_POSITION = Map.ofEntries(
            Map.entry(140,6),Map.entry(126,6),Map.entry(123,6),Map.entry(128,6),Map.entry(154,6),Map.entry(503,6),Map.entry(518,6),Map.entry(527,6),Map.entry(120,6),Map.entry(511,6),Map.entry(536,6),Map.entry(537,6),Map.entry(507,6),Map.entry(121,6),Map.entry(149,6),Map.entry(180,6),Map.entry(510,6),Map.entry(514,6),Map.entry(564,6),Map.entry(563,6),Map.entry(139,6),Map.entry(144,6),Map.entry(134,6),Map.entry(117,6),Map.entry(178,6),Map.entry(135,6),Map.entry(166,6),Map.entry(172,6),Map.entry(581,6),
            Map.entry(107,5),Map.entry(150,5),Map.entry(146,5),Map.entry(531,5),Map.entry(528,5),Map.entry(538,5),Map.entry(153,5),Map.entry(131,5),Map.entry(116,5),Map.entry(502,5),Map.entry(195,5),Map.entry(163,5),Map.entry(162,5),Map.entry(522,5),Map.entry(529,5),Map.entry(170,5),Map.entry(183,5),Map.entry(130,5),Map.entry(193,5),Map.entry(198,5),Map.entry(137,5),Map.entry(542,5),Map.entry(517,5),Map.entry(506,5),Map.entry(167,5),Map.entry(129,5),Map.entry(544,5),Map.entry(533,5),Map.entry(125,5),Map.entry(558,5),Map.entry(583,5),
            Map.entry(190,2),Map.entry(182,2),Map.entry(141,2),Map.entry(523,2),Map.entry(152,2),Map.entry(156,2),Map.entry(110,2),Map.entry(106,2),Map.entry(127,2),Map.entry(142,2),Map.entry(109,2),Map.entry(115,2),Map.entry(176,2),Map.entry(179,2),Map.entry(312,2),Map.entry(513,2),Map.entry(521,2),Map.entry(540,2),Map.entry(148,2),Map.entry(124,2),Map.entry(157,2),Map.entry(119,2),Map.entry(136,2),Map.entry(197,2),Map.entry(504,2),Map.entry(515,2),Map.entry(108,2),Map.entry(582,2),
            Map.entry(199,7),Map.entry(132,7),Map.entry(508,7),Map.entry(169,7),Map.entry(112,7),Map.entry(192,7),Map.entry(111,7),Map.entry(173,7),Map.entry(133,7),Map.entry(174,7),Map.entry(196,7),Map.entry(155,7),Map.entry(548,7),Map.entry(545,7),Map.entry(524,7),Map.entry(519,7),Map.entry(177,7),Map.entry(151,7),Map.entry(584,7),
            Map.entry(171,4),Map.entry(168,4),Map.entry(189,4),Map.entry(186,4),Map.entry(505,4),Map.entry(184,4),Map.entry(191,4),Map.entry(114,4),Map.entry(113,4),Map.entry(105,4),Map.entry(194,4),Map.entry(509,4),Map.entry(525,4),Map.entry(118,4),Map.entry(501,4),Map.entry(187,4),Map.entry(175,4),Map.entry(534,4),Map.entry(577,4),Map.entry(159,4),Map.entry(188,4),Map.entry(585,4)
    );

    /**
     * 阵营内 position 去重修正：
     * 策略1：当同一阵营有多个选手被标记为同一 position 时，用 HERO_POSITION 映射修正。
     * 策略2：当某个 position 缺失时，从重复 position 中找 HERO_POSITION 指向缺失 position 的选手，强制修正。
     */
    private void fixDuplicatePositions(String battleId) {
        List<BattlePlayer> players = battlePlayerMapper.selectList(
                new LambdaQueryWrapper<BattlePlayer>().eq(BattlePlayer::getBattleId, battleId));
        if (players == null || players.size() < 5) return;

        Set<Integer> allPositions = Set.of(2, 4, 5, 6, 7);

        Map<Integer, List<BattlePlayer>> byCamp = players.stream()
                .filter(p -> p.getCamp() != null)
                .collect(Collectors.groupingBy(BattlePlayer::getCamp));

        for (Map.Entry<Integer, List<BattlePlayer>> entry : byCamp.entrySet()) {
            List<BattlePlayer> campPlayers = entry.getValue();

            for (int round = 0; round < 5; round++) {
                Set<Integer> occupied = campPlayers.stream()
                        .map(BattlePlayer::getPosition)
                        .filter(p -> p != null && p > 0)
                        .collect(Collectors.toSet());
                Set<Integer> missing = new java.util.HashSet<>(allPositions);
                missing.removeAll(occupied);

                Map<Integer, Long> posCount = campPlayers.stream()
                        .filter(p -> p.getPosition() != null && p.getPosition() > 0)
                        .collect(Collectors.groupingBy(BattlePlayer::getPosition, Collectors.counting()));

                boolean fixed = false;

                // 策略1：修正重复 position（HERO_POSITION 不同于当前位置）
                for (Map.Entry<Integer, Long> pc : posCount.entrySet()) {
                    if (pc.getValue() <= 1) continue;
                    int dupPos = pc.getKey();
                    List<BattlePlayer> dupPlayers = campPlayers.stream()
                            .filter(p -> Integer.valueOf(dupPos).equals(p.getPosition()))
                            .toList();

                    for (BattlePlayer bp : dupPlayers) {
                        Integer heroPos = HERO_POSITION.get(bp.getHeroId());
                        if (heroPos != null && heroPos != dupPos && !occupied.contains(heroPos)) {
                            bp.setPosition(heroPos);
                            battlePlayerMapper.updateById(bp);
                            log.info("修正分路(去重): battleId={} {} heroId={} {} -> {}",
                                    battleId, bp.getPlayerName(), bp.getHeroId(), dupPos, heroPos);
                            fixed = true;
                            break;
                        }
                    }
                    if (fixed) break;
                }

                // 策略2：缺失 position 从重复 position 中补位
                if (!fixed && !missing.isEmpty()) {
                    for (int missPos : missing) {
                        for (Map.Entry<Integer, Long> pc : posCount.entrySet()) {
                            if (pc.getValue() <= 1) continue;
                            int dupPos = pc.getKey();
                            List<BattlePlayer> dupPlayers = campPlayers.stream()
                                    .filter(p -> Integer.valueOf(dupPos).equals(p.getPosition()))
                                    .toList();
                            for (BattlePlayer bp : dupPlayers) {
                                Integer heroPos = HERO_POSITION.get(bp.getHeroId());
                                if (heroPos != null && heroPos == missPos) {
                                    bp.setPosition(missPos);
                                    battlePlayerMapper.updateById(bp);
                                    log.info("修正分路(补位): battleId={} {} heroId={} {} -> {}",
                                            battleId, bp.getPlayerName(), bp.getHeroId(), dupPos, missPos);
                                    fixed = true;
                                    break;
                                }
                            }
                            if (fixed) break;
                        }
                        if (fixed) break;
                    }
                }

                // 策略3：用 positionDesc 解决重复分路（API 返回 position 与 positionDesc 矛盾的情况）
                if (!fixed && !missing.isEmpty()) {
                    for (Map.Entry<Integer, Long> pc : posCount.entrySet()) {
                        if (pc.getValue() <= 1) continue;
                        int dupPos = pc.getKey();
                        List<BattlePlayer> dupPlayers = campPlayers.stream()
                                .filter(p -> Integer.valueOf(dupPos).equals(p.getPosition()))
                                .toList();
                        for (BattlePlayer bp : dupPlayers) {
                            String desc = bp.getPositionDesc();
                            if (desc != null && !desc.isEmpty()) {
                                int descPos = inferPosition(desc);
                                if (descPos > 0 && descPos != dupPos && missing.contains(descPos)) {
                                    bp.setPosition(descPos);
                                    battlePlayerMapper.updateById(bp);
                                    log.info("修正分路(positionDesc): battleId={} {} heroId={} {} -> {} (desc={})",
                                            battleId, bp.getPlayerName(), bp.getHeroId(), dupPos, descPos, desc);
                                    fixed = true;
                                    break;
                                }
                            }
                        }
                        if (fixed) break;
                    }
                }

                // 策略4：强制补位——将重复分路的多余选手分配到缺失分路（最后手段）
                if (!fixed && !missing.isEmpty()) {
                    int missPos = missing.iterator().next();
                    for (Map.Entry<Integer, Long> pc : posCount.entrySet()) {
                        if (pc.getValue() <= 1) continue;
                        int dupPos = pc.getKey();
                        // 取第二个重复选手（保留第一个不动）
                        List<BattlePlayer> dupPlayers = campPlayers.stream()
                                .filter(p -> Integer.valueOf(dupPos).equals(p.getPosition()))
                                .toList();
                        if (dupPlayers.size() >= 2) {
                            BattlePlayer bp = dupPlayers.get(1);
                            bp.setPosition(missPos);
                            battlePlayerMapper.updateById(bp);
                            log.info("修正分路(强制补位): battleId={} {} heroId={} {} -> {}",
                                    battleId, bp.getPlayerName(), bp.getHeroId(), dupPos, missPos);
                            fixed = true;
                            break;
                        }
                    }
                }

                if (!fixed) break;
            }
        }
    }

    /** 根据 positionDesc 推断分路编号（兜底，处理 API 返回 position=0 的情况） */
    private int inferPosition(String desc) {
        if (desc == null || desc.isEmpty()) return 0;
        if (desc.contains("对抗")) return 5;
        if (desc.contains("打野")) return 7;
        if (desc.contains("中路") || desc.contains("中单")) return 2;
        if (desc.contains("发育") || desc.contains("射手")) return 4;
        if (desc.contains("游走") || desc.contains("辅助")) return 6;
        return 0;
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
            ps.setPlayerIcon(pInfo.path("player_icon").asText(""));
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
                    syncBattleDetail(b.getBattleId(), leagueId);
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
        evictQueryCache();
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
            evictQueryCache();
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
     * 重置指定赛季所有 battle_player 的 position 为 0，然后深度重新同步。
     * 用于修正之前英雄映射逻辑导致的分路数据错误。
     */
    public String resetPositionsAndResync(String leagueId) {
        SyncJob job = startJob("RESET_POSITIONS", leagueId, true);
        try {
            // 1. 先同步赛事列表确保存在
            doSyncLeagues();

            // 2. 查找该赛季所有 battleId
            List<Match> matches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>().eq(Match::getLeagueId, leagueId));
            if (matches.isEmpty()) {
                String result = "未找到该赛季的比赛";
                finishJob(job, "SUCCESS", result, null);
                return result;
            }

            List<String> matchIds = matches.stream().map(Match::getMatchId).toList();
            List<Battle> battles = battleMapper.selectList(
                    new LambdaQueryWrapper<Battle>().in(Battle::getMatchId, matchIds));
            List<String> battleIds = battles.stream().map(Battle::getBattleId).toList();

            // 3. 重置所有 battle_player 的 position 为 0
            int resetCount = 0;
            for (String battleId : battleIds) {
                BattlePlayer update = new BattlePlayer();
                update.setPosition(0);
                int affected = battlePlayerMapper.update(update,
                        new LambdaQueryWrapper<BattlePlayer>()
                                .eq(BattlePlayer::getBattleId, battleId)
                                .ne(BattlePlayer::getPosition, 0));
                resetCount += affected;
            }
            log.info("重置 position 完成: 共 {} 条记录", resetCount);

            // 4. 深度重新同步所有对局详情
            int detailCount = 0;
            for (Battle b : battles) {
                detailCount += syncBattleDetail(b.getBattleId(), leagueId);
            }

            // 5. 清除缓存
            evictQueryCache();

            String result = String.format("重置分路数据并重新同步完成: 重置%d条, 重新同步%d个对局", resetCount, detailCount);
            finishJob(job, "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            finishJob(job, "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /**
     * 深度增量同步：只为已结束且缺少选手详情的比赛补齐 battle / battle_player 数据。
     * @param matchLimit 每次最多扫描的比赛数
     * @param leagueIdOverride 指定赛事ID，为空则同步最新赛季
     */
    public String syncLatestDeepIncremental(int matchLimit, String leagueIdOverride) {
        int safeLimit = Math.max(1, Math.min(matchLimit, 50));
        String targetLabel = leagueIdOverride != null ? leagueIdOverride : "latest";
        SyncJob job = startJob("LATEST_DEEP_INCREMENTAL", targetLabel + ":" + safeLimit, true);
        try {
            doSyncLeagues();
            String leagueId;
            if (leagueIdOverride != null && !leagueIdOverride.isBlank()) {
                leagueId = leagueIdOverride;
            } else {
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
                leagueId = latest.getLeagueId();
            }

            syncMatches(leagueId);
            List<Match> finishedMatches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>()
                            .eq(Match::getLeagueId, leagueId)
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
                    // 检查是否有装备顺序数据和有效分路数据，没有则重新同步
                    Long orderedEquipRows = battlePlayerEquipMapper.selectCount(
                            new LambdaQueryWrapper<BattlePlayerEquip>()
                                    .eq(BattlePlayerEquip::getBattleId, battle.getBattleId())
                                    .gt(BattlePlayerEquip::getEquipOrder, 0));
                    Long playerRows = battlePlayerMapper.selectCount(
                            new LambdaQueryWrapper<BattlePlayer>()
                                    .eq(BattlePlayer::getBattleId, battle.getBattleId()));
                    Long zeroPosRows = battlePlayerMapper.selectCount(
                            new LambdaQueryWrapper<BattlePlayer>()
                                    .eq(BattlePlayer::getBattleId, battle.getBattleId())
                                    .eq(BattlePlayer::getPosition, 0));
                    boolean hasZeroPos = zeroPosRows != null && zeroPosRows > 0;
                    if (playerRows != null && playerRows > 0 && orderedEquipRows != null && orderedEquipRows > 0 && !hasZeroPos) {
                        skipped++;
                        continue;
                    }
                    detailCount += syncBattleDetail(battle.getBattleId(), leagueId);
                }
            }

            evictQueryCache();
            upsertCursor("latest_deep_incremental", leagueId + ":" + safeLimit);
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
                evictQueryCache();
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
                evictQueryCache();
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
            case "LATEST_DEEP_INCREMENTAL" -> syncLatestDeepIncremental(parseLimitFromTarget(job.getTarget(), 10), null);
            case "ALL" -> syncAll();
            case "BY_YEAR" -> syncByYear(parseYearFromTarget(job.getTarget()));
            case "RESET_POSITIONS" -> resetPositionsAndResync(job.getTarget());
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

    // ==================== 装备名称编码修复 ====================

    /**
     * 用 equip_info 表的正确名称修复 battle_player_equip 表中的乱码装备名。
     * equip_info 来自 pvp.qq.com（编码正确），battle_player_equip 来自 KPL API（部分名称有 UTF-16 代理对乱码）。
     */
    public int fixEquipNameEncoding() {
        log.info("开始修复装备名称编码...");
        List<EquipBaseInfo> allInfo = equipBaseInfoMapper.selectList(null);
        Map<Integer, String> nameMap = allInfo.stream()
                .filter(e -> e.getItemId() != null && e.getItemName() != null && !e.getItemName().isEmpty())
                .collect(Collectors.toMap(EquipBaseInfo::getItemId, EquipBaseInfo::getItemName, (a, b) -> a));
        log.info("equip_info 表加载 {} 条装备名称", nameMap.size());
        // Debug: 打印几个装备的名称
        for (Map.Entry<Integer, String> e : nameMap.entrySet()) {
            if (e.getKey() == 1126 || e.getKey() == 1335 || e.getKey() == 1135) {
                log.info("equip_info[{}]: '{}' (hasSurrogates={})", e.getKey(), e.getValue(),
                        e.getValue().chars().anyMatch(c -> Character.isSurrogate((char) c)));
            }
        }

        // 查出所有记录中不同的 equipId + equipName 组合
        List<BattlePlayerEquip> allEquips = battlePlayerEquipMapper.selectList(
                new LambdaQueryWrapper<BattlePlayerEquip>()
                        .select(BattlePlayerEquip::getEquipId, BattlePlayerEquip::getEquipName));
        Map<Integer, String> existingNames = new HashMap<>();
        for (BattlePlayerEquip e : allEquips) {
            existingNames.putIfAbsent(e.getEquipId(), e.getEquipName());
        }
        log.info("battle_player_equip 表有 {} 个不同装备", existingNames.size());
        // Debug: 打印几个装备的名称
        for (int id : new int[]{1126, 1335, 1135}) {
            String n = existingNames.get(id);
            if (n != null) {
                log.info("battle_equip[{}]: '{}' (hasSurrogates={})", id, n,
                        n.chars().anyMatch(c -> Character.isSurrogate((char) c)));
            }
        }

        int fixed = 0;
        for (Map.Entry<Integer, String> entry : existingNames.entrySet()) {
            int equipId = entry.getKey();
            String oldName = entry.getValue();
            String correctName = nameMap.get(equipId);
            if (correctName == null) continue;
            if (correctName.equals(oldName)) continue;

            // 名称不同，直接更新（不论是否有代理对字符）
            BattlePlayerEquip updateEntity = new BattlePlayerEquip();
            updateEntity.setEquipName(correctName);
            int updated = battlePlayerEquipMapper.update(updateEntity,
                    new LambdaQueryWrapper<BattlePlayerEquip>()
                            .eq(BattlePlayerEquip::getEquipId, equipId));
            log.info("修复装备 {}: '{}' -> '{}' (更新 {} 条)", equipId, oldName, correctName, updated);
            fixed++;
        }

        log.info("装备名称编码修复完成: 修复 {} 个装备", fixed);
        // 清除相关缓存
        evictQueryCache();
        return fixed;
    }

    private void evictQueryCache() {
        queryCacheService.evictByPattern("kpl:query:*");
    }
}
