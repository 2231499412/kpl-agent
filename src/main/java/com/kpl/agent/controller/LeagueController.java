package com.kpl.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kpl.agent.entity.Battle;
import com.kpl.agent.entity.HeroStats;
import com.kpl.agent.entity.League;
import com.kpl.agent.entity.Match;
import com.kpl.agent.entity.TeamStats;
import com.kpl.agent.mapper.*;
import com.kpl.agent.service.LeagueQueryService;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueQueryService leagueQueryService;
    private final QueryCacheService queryCacheService;
    private final TeamStatsMapper teamStatsMapper;
    private final MatchMapper matchMapper;
    private final HeroStatsMapper heroStatsMapper;
    private final BattleMapper battleMapper;

    @Value("${query.cache.ttl-seconds:600}")
    private long queryCacheTtlSeconds;

    @GetMapping
    public ApiResponse<List<League>> list(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(queryCacheService.getOrLoad(
                "kpl:query:leagues:v2:" + limit,
                Duration.ofSeconds(queryCacheTtlSeconds),
                () -> leagueQueryService.listLeagues(limit)));
    }

    @GetMapping("/latest")
    public ApiResponse<League> latest() {
        League latest = queryCacheService.getOrLoad(
                "kpl:query:leagues:latest",
                Duration.ofSeconds(queryCacheTtlSeconds),
                leagueQueryService::latestKplLeague);
        if (latest == null) {
            throw new IllegalArgumentException("No league data. Please sync leagues first.");
        }
        return ApiResponse.ok(latest);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> globalStats() {
        return ApiResponse.ok(queryCacheService.getOrLoad(
                "kpl:global:stats:v2:noWp",
                Duration.ofSeconds(queryCacheTtlSeconds),
                () -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("teamCount", teamStatsMapper.selectCount(
                            new QueryWrapper<TeamStats>().select("DISTINCT team_id").notLikeRight("league_id", "WP")));
                    stats.put("matchCount", matchMapper.selectCount(
                            new QueryWrapper<Match>().notLikeRight("league_id", "WP")));
                    stats.put("heroCount", heroStatsMapper.selectCount(
                            new QueryWrapper<HeroStats>().select("DISTINCT hero_id").notLikeRight("league_id", "WP")));
                    stats.put("battleCount", battleMapper.selectCount(
                            new QueryWrapper<Battle>().notLikeRight("match_id", "WP")));
                    return stats;
                }));
    }
}
