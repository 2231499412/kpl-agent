package com.kpl.agent.controller;

import com.kpl.agent.api.TencentNewsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpl.agent.service.LeagueQueryService;
import com.kpl.agent.service.LaneRadarService;
import com.kpl.agent.service.QueryCacheService;
import com.kpl.agent.tool.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final LaneRadarService laneRadarService;
    private final QueryCacheService queryCacheService;
    private final TencentNewsClient tencentNewsClient;
    private final ObjectMapper objectMapper;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Map<String, CoverImage> VIDEO_COVER_IMAGE_CACHE = new ConcurrentHashMap<>();

    @Value("${query.cache.ttl-seconds:600}")
    private long queryCacheTtlSeconds;

    /** 选手查询：GET /api/query/player?name=听悦 */
    @GetMapping("/player")
    public ApiResponse<Map<String, Object>> queryPlayer(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("player", () -> playerStatsTool.queryByName(name, lid), lid, name));
    }

    @GetMapping("/player/detail")
    public ApiResponse<Map<String, Object>> queryPlayerDetail(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "8") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("playerDetailV4",
                () -> playerStatsTool.queryPlayerDetail(name, lid, limit),
                lid, name, limit));
    }

    /** 英雄查询：GET /api/query/hero?name=公孙离 */
    @GetMapping("/hero")
    public ApiResponse<Map<String, Object>> queryHero(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("heroV2", () -> heroStatsTool.queryByName(name, lid), lid, name));
    }

    /** 英雄高胜率选手：GET /api/query/hero/players?name=公孙离 */
    @GetMapping("/hero/players")
    public ApiResponse<Map<String, Object>> queryHeroPlayers(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "8") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("heroPlayers", () -> heroStatsTool.queryHeroPlayers(name, lid, limit), lid, name, limit));
    }

    @GetMapping("/hero/detail")
    public ApiResponse<Map<String, Object>> queryHeroDetail(
            @RequestParam(required = false) Integer heroId,
            @RequestParam(required = false) String heroName,
            @RequestParam(required = false, name = "name") String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "8") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        String resolvedName = heroName != null && !heroName.isBlank() ? heroName : name;
        return ApiResponse.ok(cached("heroDetailV2",
                () -> heroStatsTool.queryHeroDetail(heroId, resolvedName, lid, limit),
                lid, heroId, resolvedName, limit));
    }

    /** 选手排行：GET /api/query/player/top?sort=kda */
    @GetMapping("/player/top")
    public ApiResponse<Map<String, Object>> queryPlayerTop(
            @RequestParam(defaultValue = "kda") String sort,
            @RequestParam(required = false) String leagueId) {
        String resolvedLeagueId = leagueQueryService.requireLeagueId(leagueId);
        Map<String, Object> data = cached("playerTop", () -> switch (sort) {
            case "win" -> playerStatsTool.queryTopWinRate(resolvedLeagueId, 5, 9999);
            default -> playerStatsTool.queryTopKda(resolvedLeagueId, 5, 9999);
        }, resolvedLeagueId, sort);
        return ApiResponse.ok(data);
    }

    /** 英雄排行：GET /api/query/hero/top?sort=pick */
    @GetMapping("/hero/top")
    public ApiResponse<Map<String, Object>> queryHeroTop(
            @RequestParam(defaultValue = "pick") String sort,
            @RequestParam(required = false) String leagueId) {
        String resolvedLeagueId = leagueQueryService.requireLeagueId(leagueId);
        Map<String, Object> data = cached("heroTopV2", () -> switch (sort) {
            case "ban" -> heroStatsTool.queryTopBanRate(resolvedLeagueId, 9999);
            case "win" -> heroStatsTool.queryTopWinRate(resolvedLeagueId, 5, 9999);
            default -> heroStatsTool.queryTopPickRate(resolvedLeagueId, 9999);
        }, resolvedLeagueId, sort);
        return ApiResponse.ok(data);
    }

    /** 战队查询：GET /api/query/team?name=AG */
    @GetMapping("/team")
    public ApiResponse<Map<String, Object>> queryTeam(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("team", () -> teamStatsTool.queryByName(name, lid), lid, name));
    }

    /** 战队排名：GET /api/query/team/ranking */
    @GetMapping("/team/ranking")
    public ApiResponse<Map<String, Object>> queryTeamRanking(
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("teamRanking", () -> teamStatsTool.queryRanking(lid), lid));
    }

    /** 荣誉总榜：GET /api/query/team/honors */
    @GetMapping("/team/honors")
    public ApiResponse<Map<String, Object>> queryTeamHonors() {
        return ApiResponse.ok(cached("teamHonors", () -> teamStatsTool.queryHonors()));
    }

    /** 赛程列表（按赛段排序，决赛在前）：GET /api/query/match/schedule */
    @GetMapping("/match/schedule")
    public ApiResponse<Map<String, Object>> queryMatchSchedule(
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("matchSchedule", () -> matchAnalysisTool.queryBySchedule(lid), lid));
    }

    /** 比赛复盘：GET /api/query/match/recent?team=AG */
    @GetMapping("/match/recent")
    public ApiResponse<Map<String, Object>> queryRecentMatch(
            @RequestParam String team,
            @RequestParam(required = false) String leagueId) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("recentMatch", () -> matchAnalysisTool.queryRecentMatches(team, lid, 5), lid, team));
    }

    /** 对局详情：GET /api/query/match/battle?matchId=2026011401 */
    @GetMapping("/match/battle")
    public ApiResponse<Map<String, Object>> queryBattleDetail(@RequestParam String matchId) {
        return ApiResponse.ok(cached("battleDetail", () -> matchAnalysisTool.deepAnalysis(matchId), matchId));
    }

    /** 对局选手详情（含装备）：GET /api/query/battle/players?battleId=xxx */
    @GetMapping("/battle/players")
    public ApiResponse<Map<String, Object>> queryBattlePlayers(@RequestParam String battleId) {
        return ApiResponse.ok(cached("battlePlayers", () -> matchAnalysisTool.queryBattlePlayers(battleId), battleId));
    }

    /** 视频封面：GET /api/query/video/cover?bvid=BVxxx */
    @GetMapping("/video/cover")
    public ApiResponse<Map<String, Object>> queryVideoCover(@RequestParam String bvid) {
        String normalized = bvid == null ? "" : bvid.trim();
        return ApiResponse.ok(cached("videoCoverV1", () -> fetchBilibiliCover(normalized), normalized));
    }

    /** 视频封面图片代理：GET /api/query/video/cover-image?bvid=BVxxx */
    @GetMapping("/video/cover-image")
    public ResponseEntity<byte[]> queryVideoCoverImage(@RequestParam String bvid) {
        String normalized = bvid == null ? "" : bvid.trim();
        CoverImage cachedImage = VIDEO_COVER_IMAGE_CACHE.get(normalized);
        if (cachedImage != null) {
            return coverImageResponse(cachedImage);
        }
        Map<String, Object> meta = cached("videoCoverV1", () -> fetchBilibiliCover(normalized), normalized);
        String coverUrl = String.valueOf(meta.getOrDefault("coverUrl", ""));
        if (coverUrl.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(coverUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) {
                return ResponseEntity.notFound().build();
            }
            String contentType = response.headers().firstValue("content-type").orElse(MediaType.IMAGE_JPEG_VALUE);
            CoverImage image = new CoverImage(response.body(), contentType);
            VIDEO_COVER_IMAGE_CACHE.put(normalized, image);
            return coverImageResponse(image);
        } catch (Exception ignored) {
            return ResponseEntity.notFound().build();
        }
    }

    /** 对位雷达图：GET /api/query/radar/lane?leagueId=xxx&matchId=xxx&battleId=xxx&role=打野 */
    @GetMapping("/radar/lane")
    public ApiResponse<Map<String, Object>> queryLaneRadar(
            @RequestParam(required = false) String leagueId,
            @RequestParam String matchId,
            @RequestParam String battleId,
            @RequestParam(defaultValue = "对抗路") String role) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("laneRadarV2",
                () -> laneRadarService.buildRadar(lid, matchId, battleId, role),
                lid, matchId, battleId, role));
    }

    /** 选手英雄数据：GET /api/query/player/heroes?name=一诺 */
    @GetMapping("/player/heroes")
    public ApiResponse<Map<String, Object>> queryPlayerHeroes(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("playerHeroes", () -> matchAnalysisTool.queryPlayerHeroes(name, lid, limit), lid, name, limit));
    }

    /** 装备出场排行：GET /api/query/equip/top */
    @GetMapping("/equip/top")
    public ApiResponse<Map<String, Object>> queryEquipTop(
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("equipTop", () -> equipStatsTool.queryTopGlobal(lid, limit), lid, limit));
    }

    /** 按装备名搜索（支持外号）：GET /api/query/equip/search?name=黑切 */
    @GetMapping("/equip/search")
    public ApiResponse<Map<String, Object>> queryEquipSearch(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("equipSearch", () -> equipStatsTool.queryByName(name, lid, limit), lid, name, limit));
    }

    /** 装备详情（分路/英雄分布）：GET /api/query/equip/detail?equipId=1422 */
    @GetMapping("/equip/detail")
    public ApiResponse<Map<String, Object>> queryEquipDetail(
            @RequestParam int equipId,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "10") int heroLimit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("equipDetail", () -> equipStatsTool.queryDetail(equipId, lid, heroLimit), lid, equipId, heroLimit));
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
            return ApiResponse.ok(cached("equipHeroId", () -> equipStatsTool.queryByHeroId(heroId, lid, limit), lid, heroId, limit));
        }
        if (heroName != null && !heroName.isBlank()) {
            return ApiResponse.ok(cached("equipHeroName", () -> equipStatsTool.queryByHero(heroName, lid, limit), lid, heroName, limit));
        }
        return ApiResponse.ok(Map.of("error", "请提供 heroId 或 heroName"));
    }

    /** 选手常用装备：GET /api/query/equip/player?name=一诺 */
    @GetMapping("/equip/player")
    public ApiResponse<Map<String, Object>> queryEquipByPlayer(
            @RequestParam String name,
            @RequestParam(required = false) String leagueId,
            @RequestParam(defaultValue = "6") int limit) {
        String lid = leagueQueryService.requireLeagueId(leagueId);
        return ApiResponse.ok(cached("equipPlayer", () -> equipStatsTool.queryByPlayer(name, lid, limit), lid, name, limit));
    }

    /** 版本动态列表：GET /api/query/patch-notes?limit=8 */
    @GetMapping("/patch-notes")
    public ApiResponse<List<Map<String, Object>>> queryPatchNotes(
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(tencentNewsClient.getNewsList(limit));
    }

    /** 版本动态详情：GET /api/query/patch-notes/detail?id=801930 */
    @GetMapping("/patch-notes/detail")
    public ApiResponse<Map<String, Object>> queryPatchNoteDetail(
            @RequestParam String id) {
        return ApiResponse.ok(tencentNewsClient.getNewsDetail(id));
    }

    private Map<String, Object> cached(String namespace, java.util.function.Supplier<Map<String, Object>> loader, Object... keyParts) {
        String key = "kpl:query:" + namespace + ":" + java.util.Arrays.stream(keyParts)
                .map(part -> String.valueOf(part).replaceAll("[\\s:]+", "_"))
                .collect(java.util.stream.Collectors.joining(":"));
        return queryCacheService.getOrLoad(key, Duration.ofSeconds(queryCacheTtlSeconds), loader);
    }

    private Map<String, Object> fetchBilibiliCover(String bvid) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "video_cover");
        result.put("bvid", bvid);
        if (bvid == null || !bvid.startsWith("BV")) {
            result.put("coverUrl", "");
            result.put("title", "");
            return result;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.bilibili.com")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                result.put("coverUrl", "");
                result.put("title", "");
                return result;
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            String coverUrl = data.path("pic").asText("");
            if (coverUrl.startsWith("http://")) {
                coverUrl = "https://" + coverUrl.substring("http://".length());
            }
            result.put("coverUrl", coverUrl);
            result.put("title", data.path("title").asText(""));
            return result;
        } catch (Exception ignored) {
            result.put("coverUrl", "");
            result.put("title", "");
            return result;
        }
    }

    private ResponseEntity<byte[]> coverImageResponse(CoverImage image) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("Cache-Control", "public, max-age=86400")
                .body(image.bytes());
    }

    private record CoverImage(byte[] bytes, String contentType) {}
}
