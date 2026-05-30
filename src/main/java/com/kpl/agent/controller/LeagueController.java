package com.kpl.agent.controller;

import com.kpl.agent.entity.League;
import com.kpl.agent.service.LeagueQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 赛事查询接口：用于前端选择赛季或获取默认最新 KPL 赛季。
 */
@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueQueryService leagueQueryService;

    /** 赛事列表：GET /api/leagues?limit=20 */
    @GetMapping
    public ApiResponse<List<League>> list(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(leagueQueryService.listLeagues(limit));
    }

    /** 最新 KPL 赛事：GET /api/leagues/latest */
    @GetMapping("/latest")
    public ApiResponse<League> latest() {
        League latest = leagueQueryService.latestKplLeague();
        if (latest == null) {
            throw new IllegalArgumentException("暂无赛事数据，请先同步赛事列表");
        }
        return ApiResponse.ok(latest);
    }
}
