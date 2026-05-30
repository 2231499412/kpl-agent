package com.kpl.agent.controller;

import com.kpl.agent.service.LeagueQueryService;
import com.kpl.agent.tool.HeroStatsTool;
import com.kpl.agent.tool.MatchAnalysisTool;
import com.kpl.agent.tool.PlayerStatsTool;
import com.kpl.agent.tool.TeamStatsTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 直接查询接口：不经过 Agent，直接调用 Tool 查询数据
 */
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final PlayerStatsTool playerStatsTool;
    private final HeroStatsTool heroStatsTool;
    private final TeamStatsTool teamStatsTool;
    private final MatchAnalysisTool matchAnalysisTool;
    private final LeagueQueryService leagueQueryService;

    /** 选手查询：GET /api/query/player?name=听悦 */
    @GetMapping("/player")
    public ApiResponse<Map<String, Object>> queryPlayer(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(playerStatsTool.queryByName(name, leagueQueryService.requireLeagueId(leagueId)));
    }

    /** 英雄查询：GET /api/query/hero?name=公孙离 */
    @GetMapping("/hero")
    public ApiResponse<Map<String, Object>> queryHero(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(heroStatsTool.queryByName(name, leagueQueryService.requireLeagueId(leagueId)));
    }

    /** 选手排行：GET /api/query/player/top?sort=kda */
    @GetMapping("/player/top")
    public ApiResponse<Map<String, Object>> queryPlayerTop(
            @RequestParam(defaultValue = "kda") String sort,
            @RequestParam(required = false) String leagueId) {
        String resolvedLeagueId = leagueQueryService.requireLeagueId(leagueId);
        Map<String, Object> data = switch (sort) {
            case "win" -> playerStatsTool.queryTopWinRate(resolvedLeagueId, 5, 20);
            default -> playerStatsTool.queryTopKda(resolvedLeagueId, 5, 20);
        };
        return ApiResponse.ok(data);
    }

    /** 英雄排行：GET /api/query/hero/top?sort=pick */
    @GetMapping("/hero/top")
    public ApiResponse<Map<String, Object>> queryHeroTop(
            @RequestParam(defaultValue = "pick") String sort,
            @RequestParam(required = false) String leagueId) {
        String resolvedLeagueId = leagueQueryService.requireLeagueId(leagueId);
        Map<String, Object> data = switch (sort) {
            case "ban" -> heroStatsTool.queryTopBanRate(resolvedLeagueId, 10);
            case "win" -> heroStatsTool.queryTopWinRate(resolvedLeagueId, 5, 10);
            default -> heroStatsTool.queryTopPickRate(resolvedLeagueId, 10);
        };
        return ApiResponse.ok(data);
    }

    /** 战队查询：GET /api/query/team?name=AG */
    @GetMapping("/team")
    public ApiResponse<Map<String, Object>> queryTeam(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(teamStatsTool.queryByName(name, leagueQueryService.requireLeagueId(leagueId)));
    }

    /** 战队排名：GET /api/query/team/ranking */
    @GetMapping("/team/ranking")
    public ApiResponse<Map<String, Object>> queryTeamRanking(
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(teamStatsTool.queryRanking(leagueQueryService.requireLeagueId(leagueId)));
    }

    /** 荣誉总榜：GET /api/query/team/honors */
    @GetMapping("/team/honors")
    public ApiResponse<Map<String, Object>> queryTeamHonors() {
        return ApiResponse.ok(teamStatsTool.queryHonors());
    }

    /** 比赛复盘：GET /api/query/match/recent?team=AG */
    @GetMapping("/match/recent")
    public ApiResponse<Map<String, Object>> queryRecentMatch(
            @RequestParam String team,
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(matchAnalysisTool.queryRecentMatches(team, leagueQueryService.requireLeagueId(leagueId), 5));
    }

    /** 对局详情：GET /api/query/match/battle?matchId=2026011401 */
    @GetMapping("/match/battle")
    public ApiResponse<Map<String, Object>> queryBattleDetail(@RequestParam String matchId) {
        return ApiResponse.ok(matchAnalysisTool.deepAnalysis(matchId));
    }
}
