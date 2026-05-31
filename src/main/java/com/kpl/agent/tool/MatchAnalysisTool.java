package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.Battle;
import com.kpl.agent.entity.BattlePlayer;
import com.kpl.agent.entity.BattlePlayerEquip;
import com.kpl.agent.entity.Match;
import com.kpl.agent.mapper.BattleMapper;
import com.kpl.agent.mapper.BattlePlayerEquipMapper;
import com.kpl.agent.mapper.BattlePlayerMapper;
import com.kpl.agent.mapper.MatchMapper;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 比赛分析工具：查询比赛复盘数据，包括对局详情和选手表现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchAnalysisTool {

    private final MatchMapper matchMapper;
    private final BattleMapper battleMapper;
    private final BattlePlayerMapper battlePlayerMapper;
    private final BattlePlayerEquipMapper battlePlayerEquipMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 赛段优先级：决赛 > 季后赛 > 淘汰赛 > 常规赛 */
    private static final Map<String, Integer> STAGE_ORDER = Map.ofEntries(
            Map.entry("js", 1), Map.entry("zjs", 1),     // 决赛/总决赛
            Map.entry("jhs", 2),                          // 季后赛
            Map.entry("sbttss", 3), Map.entry("dbtts", 3), // 双败/单败淘汰赛
            Map.entry("cgs3", 4),                          // 常规赛第三轮
            Map.entry("cgs2", 5),                          // 常规赛第二轮
            Map.entry("cgs1", 6)                           // 常规赛第一轮
    );

    /** 查询某赛事所有比赛，按赛程排序（决赛在前） */
    public Map<String, Object> queryBySchedule(String leagueId) {
        String key = "kpl:%s:match:schedule".formatted(leagueId);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<Match> matches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>()
                            .eq(Match::getLeagueId, leagueId)
                            .eq(Match::getStatus, 2)  // 已结束
                            .orderByDesc(Match::getStartTime));
            // 按赛段优先级排序，同赛段内按时间降序
            matches.sort((a, b) -> {
                int pa = STAGE_ORDER.getOrDefault(a.getMatchStage(), 99);
                int pb = STAGE_ORDER.getOrDefault(b.getMatchStage(), 99);
                if (pa != pb) return Integer.compare(pa, pb);
                // 同赛段内按时间降序（最近的在前）
                if (a.getStartTime() != null && b.getStartTime() != null) {
                    return b.getStartTime().compareTo(a.getStartTime());
                }
                return 0;
            });
            return buildResult("match_schedule", "赛程", matches);
        });
    }

    /** 按战队名查询最近比赛，限定在指定赛事内。 */
    public Map<String, Object> queryRecentMatches(String teamName, String leagueId, int limit) {
        String key = "kpl:%s:match:recent:%s:%d".formatted(leagueId, teamName, limit);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<Match> matches = matchMapper.selectList(
                    new LambdaQueryWrapper<Match>()
                            .eq(Match::getLeagueId, leagueId)
                            .and(w -> w
                                    .like(Match::getCamp1TeamName, teamName)
                                    .or()
                                    .like(Match::getCamp2TeamName, teamName))
                            .orderByDesc(Match::getStartTime)
                            .last("LIMIT " + limit));
            return buildResult("recent_matches", teamName, matches);
        });
    }

    /** 查询某场比赛的所有对局 */
    public Map<String, Object> queryMatchBattles(String matchId) {
        List<Battle> battles = battleMapper.selectList(
                new LambdaQueryWrapper<Battle>()
                        .eq(Battle::getMatchId, matchId)
                        .orderByAsc(Battle::getBattleSeq));
        return buildResult("match_battles", matchId, battles);
    }

    /** 查询某局的选手表现（含装备） */
    public Map<String, Object> queryBattlePlayers(String battleId) {
        List<BattlePlayer> players = battlePlayerMapper.selectList(
                new LambdaQueryWrapper<BattlePlayer>()
                        .eq(BattlePlayer::getBattleId, battleId));
        List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(
                new LambdaQueryWrapper<BattlePlayerEquip>()
                        .eq(BattlePlayerEquip::getBattleId, battleId));
        // 按选手名分组装备
        Map<String, List<BattlePlayerEquip>> equipMap = equips.stream()
                .collect(java.util.stream.Collectors.groupingBy(BattlePlayerEquip::getPlayerName));
        List<Map<String, Object>> data = new ArrayList<>();
        for (BattlePlayer p : players) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("player", p);
            row.put("equips", equipMap.getOrDefault(p.getPlayerName(), List.of()));
            data.add(row);
        }
        return buildResult("battle_players", battleId, data);
    }

    /** 查询选手的英雄数据（胜率、场次） */
    public Map<String, Object> queryPlayerHeroes(String playerName, String leagueId, int limit) {
        List<Map<String, Object>> heroes = battlePlayerMapper.playerHeroStats(playerName, leagueId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("type", "player_heroes");
        result.put("keyword", playerName);
        result.put("count", heroes.size());
        result.put("data", heroes);
        return result;
    }

    /** 深度复盘：查询某场比赛的完整数据（比赛信息+所有对局+每局选手） */
    public Map<String, Object> deepAnalysis(String matchId) {
        Match match = matchMapper.selectOne(
                new LambdaQueryWrapper<Match>().eq(Match::getMatchId, matchId));
        if (match == null) {
            return Map.of("error", "比赛不存在: " + matchId);
        }

        List<Battle> battles = battleMapper.selectList(
                new LambdaQueryWrapper<Battle>()
                        .eq(Battle::getMatchId, matchId)
                        .orderByAsc(Battle::getBattleSeq));

        List<Map<String, Object>> battleDetails = new ArrayList<>();
        for (Battle b : battles) {
            List<BattlePlayer> players = battlePlayerMapper.selectList(
                    new LambdaQueryWrapper<BattlePlayer>()
                            .eq(BattlePlayer::getBattleId, b.getBattleId()));
            List<BattlePlayerEquip> equips = battlePlayerEquipMapper.selectList(
                    new LambdaQueryWrapper<BattlePlayerEquip>()
                            .eq(BattlePlayerEquip::getBattleId, b.getBattleId()));
            Map<String, List<BattlePlayerEquip>> equipMap = equips.stream()
                    .collect(java.util.stream.Collectors.groupingBy(BattlePlayerEquip::getPlayerName));
            List<Map<String, Object>> playerData = new ArrayList<>();
            for (BattlePlayer p : players) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("player", p);
                row.put("equips", equipMap.getOrDefault(p.getPlayerName(), List.of()));
                playerData.add(row);
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("battle", b);
            detail.put("players", playerData);
            battleDetails.add(detail);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "deep_analysis");
        result.put("match", match);
        result.put("battles", battleDetails);
        return result;
    }

    private Map<String, Object> buildResult(String type, String keyword, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("data", data);
        return result;
    }
}
