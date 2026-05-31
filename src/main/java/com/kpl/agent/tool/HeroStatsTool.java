package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.HeroStats;
import com.kpl.agent.mapper.BattlePlayerMapper;
import com.kpl.agent.mapper.HeroStatsMapper;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 英雄数据查询工具：按英雄名查询胜率、ban率、pick率
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeroStatsTool {

    private final HeroStatsMapper heroStatsMapper;
    private final BattlePlayerMapper battlePlayerMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** 按英雄名查询 */
    public Map<String, Object> queryByName(String heroName, String leagueId) {
        String key = "kpl:%s:hero:name:%s".formatted(leagueId, heroName);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<HeroStats> list = heroStatsMapper.selectList(
                    new LambdaQueryWrapper<HeroStats>()
                            .eq(HeroStats::getLeagueId, leagueId)
                            .like(HeroStats::getHeroName, heroName));
            return buildResult("hero_by_name", heroName, list);
        });
    }

    /** 查询出场率TOP N */
    public Map<String, Object> queryTopPickRate(String leagueId, int topN) {
        String key = "kpl:%s:hero:top:pick:%d".formatted(leagueId, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<HeroStats> list = heroStatsMapper.selectList(
                    new LambdaQueryWrapper<HeroStats>()
                            .eq(HeroStats::getLeagueId, leagueId)
                            .orderByDesc(HeroStats::getPickRate)
                            .last("LIMIT " + topN));
            return buildResult("hero_top_pick", "pick率TOP" + topN, list);
        });
    }

    /** 查询被ban率TOP N */
    public Map<String, Object> queryTopBanRate(String leagueId, int topN) {
        String key = "kpl:%s:hero:top:ban:%d".formatted(leagueId, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<HeroStats> list = heroStatsMapper.selectList(
                    new LambdaQueryWrapper<HeroStats>()
                            .eq(HeroStats::getLeagueId, leagueId)
                            .orderByDesc(HeroStats::getBanRate)
                            .last("LIMIT " + topN));
            return buildResult("hero_top_ban", "ban率TOP" + topN, list);
        });
    }

    /** 查询胜率TOP N（至少出场N次） */
    public Map<String, Object> queryTopWinRate(String leagueId, int minBattles, int topN) {
        String key = "kpl:%s:hero:top:win:%d:%d".formatted(leagueId, minBattles, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<HeroStats> list = heroStatsMapper.selectList(
                    new LambdaQueryWrapper<HeroStats>()
                            .eq(HeroStats::getLeagueId, leagueId)
                            .ge(HeroStats::getBattleCount, minBattles)
                            .orderByDesc(HeroStats::getWinRate)
                            .last("LIMIT " + topN));
            return buildResult("hero_top_winrate", "胜率TOP" + topN, list);
        });
    }

    /** 查询某英雄的高胜率选手 */
    public Map<String, Object> queryHeroPlayers(String heroName, String leagueId, int limit) {
        List<Map<String, Object>> players = battlePlayerMapper.heroPlayerStats(heroName, leagueId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("type", "hero_players");
        result.put("keyword", heroName);
        result.put("count", players.size());
        result.put("data", players);
        return result;
    }

    private Map<String, Object> buildResult(String type, String keyword, List<HeroStats> list) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", list.size());
        result.put("data", list);
        return result;
    }
}
