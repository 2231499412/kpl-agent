package com.kpl.agent.controller;

import com.kpl.agent.service.LeagueQueryService;
import com.kpl.agent.tool.*;
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
    private final EquipStatsTool equipStatsTool;
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

    /** 英雄高胜率选手：GET /api/query/hero/players?name=公孙离 */
    @GetMapping("/hero/players")
    public ApiResponse<Map<String, Object>> queryHeroPlayers(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(heroStatsTool.queryHeroPlayers(name, leagueQueryService.requireLeagueId(leagueId), limit));
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

    /** 赛程列表（按赛段排序，决赛在前）：GET /api/query/match/schedule */
    @GetMapping("/match/schedule")
    public ApiResponse<Map<String, Object>> queryMatchSchedule(
            @RequestParam(required = false) String leagueId) {
        return ApiResponse.ok(matchAnalysisTool.queryBySchedule(leagueQueryService.requireLeagueId(leagueId)));
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

    /** 对局选手详情（含装备）：GET /api/query/battle/players?battleId=xxx */
    @GetMapping("/battle/players")
    public ApiResponse<Map<String, Object>> queryBattlePlayers(@RequestParam String battleId) {
        return ApiResponse.ok(matchAnalysisTool.queryBattlePlayers(battleId));
    }

    /** 选手英雄数据：GET /api/query/player/heroes?name=一诺 */
    @GetMapping("/player/heroes")
    public ApiResponse<Map<String, Object>> queryPlayerHeroes(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(matchAnalysisTool.queryPlayerHeroes(name, leagueQueryService.requireLeagueId(leagueId), limit));
    }

    /** 装备出场排行：GET /api/query/equip/top */
    @GetMapping("/equip/top")
    public ApiResponse<Map<String, Object>> queryEquipTop(
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(equipStatsTool.queryTopGlobal(leagueQueryService.requireLeagueId(leagueId), limit));
    }

    /** 按装备名搜索（支持外号）：GET /api/query/equip/search?name=黑切 */
    @GetMapping("/equip/search")
    public ApiResponse<Map<String, Object>> queryEquipSearch(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(equipStatsTool.queryByName(name, leagueQueryService.requireLeagueId(leagueId), limit));
    }

    /** 装备详情（分路/英雄分布）：GET /api/query/equip/detail?equipId=1422 */
    @GetMapping("/equip/detail")
    public ApiResponse<Map<String, Object>> queryEquipDetail(
            @RequestParam int equipId,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int heroLimit) {
        return ApiResponse.ok(equipStatsTool.queryDetail(equipId, leagueQueryService.requireLeagueId(leagueId), heroLimit));
    }

    /** 英雄常用装备：GET /api/query/equip/hero?heroId=111 */
    @GetMapping("/equip/hero")
    public ApiResponse<Map<String, Object>> queryEquipByHero(
            @RequestParam(required = false) Integer heroId,
            @RequestParam(required = false) String heroName,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "6") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        if (heroId != null) {
            return ApiResponse.ok(equipStatsTool.queryByHeroId(heroId, lid, limit));
        }
        if (heroName != null && !heroName.isBlank()) {
            return ApiResponse.ok(equipStatsTool.queryByHero(heroName, lid, limit));
        }
        return ApiResponse.ok(Map.of("error", "请提供 heroId 或 heroName"));
    }

    /** 选手常用装备：GET /api/query/equip/player?name=一诺 */
    @GetMapping("/equip/player")
    public ApiResponse<Map<String, Object>> queryEquipByPlayer(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(equipStatsTool.queryByPlayer(name, leagueQueryService.requireLeagueId(leagueId), limit));
    }
}
