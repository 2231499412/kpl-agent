package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.Battle;
import com.kpl.agent.entity.BattlePlayer;
import com.kpl.agent.entity.Match;
import com.kpl.agent.mapper.BattleMapper;
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
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

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

    /** 查询某局的选手表现 */
    public Map<String, Object> queryBattlePlayers(String battleId) {
        List<BattlePlayer> players = battlePlayerMapper.selectList(
                new LambdaQueryWrapper<BattlePlayer>()
                        .eq(BattlePlayer::getBattleId, battleId));
        return buildResult("battle_players", battleId, players);
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
            Map<String, Object> detail = new HashMap<>();
            detail.put("battle", b);
            detail.put("players", players);
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
