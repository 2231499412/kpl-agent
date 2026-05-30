package com.kpl.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * KPL 官方 API 客户端
 * 封装 prod.comp.smoba.qq.com 和 tga-openapi.tga.qq.com 两套接口
 */
@Slf4j
@Component
public class KplApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kpl.api.comp-base-url}")
    private String compBaseUrl;

    @Value("${kpl.api.tga-base-url}")
    private String tgaBaseUrl;

    public KplApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== prod.comp.smoba.qq.com 接口 ====================

    /** 获取所有赛事列表（2019至今） */
    public JsonNode getLeagues() {
        return get(compBaseUrl + "/leaguesite/leagues/open");
    }

    /** 获取某赛事下的所有比赛 */
    public JsonNode getMatches(String leagueId) {
        return get(compBaseUrl + "/leaguesite/matches/open?league_id=" + leagueId);
    }

    /** 获取某比赛的所有对局（BO5=5局） */
    public JsonNode getMatchBattles(String matchId) {
        return get(compBaseUrl + "/leaguesite/match/battles/open?match_id=" + matchId);
    }

    /** 获取某局的详细数据（选手英雄、KDA、伤害等） */
    public JsonNode getBattleDetail(String battleId) {
        return get(compBaseUrl + "/leaguesite/battle/open?battle_id=" + battleId);
    }

    /** 获取某赛事的英雄数据榜（胜率、ban率、pick率） */
    public JsonNode getHeroStats(String leagueId) {
        return get(compBaseUrl + "/leaguesite/league/hero/settle_list/open?league_id=" + leagueId);
    }

    /** 获取某赛事的选手数据榜（KDA、伤害、参团率） */
    public JsonNode getPlayerStats(String leagueId) {
        return get(compBaseUrl + "/leaguesite/league/player/settle_list/open?league_id=" + leagueId);
    }

    /** 获取某赛事的战队数据榜（胜率、场均数据） */
    public JsonNode getTeamStats(String leagueId) {
        return get(compBaseUrl + "/leaguesite/league/team/settle_list/open?league_id=" + leagueId);
    }

    // ==================== tga-openapi.tga.qq.com 接口 ====================

    /** 获取赛程列表 */
    public JsonNode getSchedules(String seasonId) {
        return get(tgaBaseUrl + "/web/tgabank/getSchedules?seasonid=" + seasonId);
    }

    /** 获取战队积分榜 */
    public JsonNode getTeamRank(String seasonId, String stage) {
        return get(tgaBaseUrl + "/web/tgabank/getTeamRank?seasonid=" + seasonId + "&stage=" + stage);
    }

    /** 获取战队信息 */
    public JsonNode getTeamInfo(String seasonId) {
        return get(tgaBaseUrl + "/web/tgabank/getTeamInfo?seasonid=" + seasonId);
    }

    /** 获取首发名单 */
    public JsonNode getPlayerFirstList(String seasonId, String scheduleId) {
        return get(tgaBaseUrl + "/web/tgabank/getPlayerFirstList?seasonid=" + seasonId + "&scheduleid=" + scheduleId);
    }

    // ==================== 通用请求方法 ====================

    private JsonNode get(String url) {
        try {
            log.debug("请求 KPL API: {}", url);
            String json = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("请求 KPL API 失败: {}", url, e);
            return null;
        }
    }
}
