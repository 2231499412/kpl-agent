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
import java.util.LinkedHashMap;
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
                            .eq(HeroStats::getLeagueId, leagueId));
            boolean hasPickData = hasPickData(list);
            list = list.stream()
                    .sorted((a, b) -> compareForPickRanking(a, b, hasPickData))
                    .limit(topN)
                    .toList();
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

    /** Aggregates the data blocks used by the hero detail page. */
    public Map<String, Object> queryHeroDetail(Integer heroId, String heroName, String leagueId, int limit) {
        HeroStats hero = findHero(heroId, heroName, leagueId);
        if (hero == null) {
            return Map.of(
                    "type", "hero_detail",
                    "keyword", heroName != null ? heroName : String.valueOf(heroId),
                    "error", "hero not found"
            );
        }

        int rowLimit = Math.max(1, Math.min(limit, 20));
        int minGames = 3;
        Integer resolvedHeroId = hero.getHeroId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "hero_detail");
        result.put("keyword", hero.getHeroName());
        result.put("hero", hero);
        result.put("userCount", nullToZero(battlePlayerMapper.countHeroUsers(resolvedHeroId, leagueId)));
        result.put("topPlayers", battlePlayerMapper.heroPlayerLeaderboard(resolvedHeroId, leagueId, minGames, rowLimit));
        result.put("featuredBattles", battlePlayerMapper.heroFeaturedBattles(resolvedHeroId, leagueId, rowLimit));
        result.put("synergyHeroes", battlePlayerMapper.heroSynergy(resolvedHeroId, leagueId, 2, rowLimit));
        result.put("favoredCounters", battlePlayerMapper.heroFavoredCounters(resolvedHeroId, leagueId, 2, rowLimit));
        result.put("toughCounters", battlePlayerMapper.heroToughCounters(resolvedHeroId, leagueId, 2, rowLimit));
        return result;
    }

    private HeroStats findHero(Integer heroId, String heroName, String leagueId) {
        LambdaQueryWrapper<HeroStats> wrapper = new LambdaQueryWrapper<HeroStats>()
                .eq(HeroStats::getLeagueId, leagueId);
        if (heroId != null) {
            wrapper.eq(HeroStats::getHeroId, heroId);
        } else if (heroName != null && !heroName.isBlank()) {
            wrapper.like(HeroStats::getHeroName, heroName);
        } else {
            return null;
        }
        HeroStats hero = heroStatsMapper.selectList(wrapper.orderByDesc(HeroStats::getPickRate).last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
        return hero;
    }

    private boolean hasPickData(List<HeroStats> list) {
        return list != null && list.stream()
                .anyMatch(hero -> nullToZero(hero.getPickRate()) > 0 || nullToZero(hero.getPickNum()) > 0);
    }

    private int compareForPickRanking(HeroStats a, HeroStats b, boolean hasPickData) {
        if (hasPickData) {
            int rateDiff = Double.compare(nullToZero(b.getPickRate()), nullToZero(a.getPickRate()));
            if (rateDiff != 0) {
                return rateDiff;
            }
        }
        return Integer.compare(nullToZero(b.getBattleCount()), nullToZero(a.getBattleCount()));
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double nullToZero(Double value) {
        return value == null ? 0 : value;
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
