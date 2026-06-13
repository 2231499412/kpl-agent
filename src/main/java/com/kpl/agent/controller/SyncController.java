package com.kpl.agent.controller;

import com.kpl.agent.service.DataSyncService;
import com.kpl.agent.service.EquipInfoSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据同步接口：手动触发从 KPL 官方 API 拉取数据
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final DataSyncService dataSyncService;
    private final EquipInfoSyncService equipInfoSyncService;

    /**
     * 同步最新赛季（不包含对局详情，速度快）
     * POST /api/sync/latest
     */
    @PostMapping("/latest")
    public ApiResponse<Map<String, String>> syncLatest() {
        String result = dataSyncService.syncLatestSeason();
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 同步所有 KPL 赛事（2019至今，数据量大）
     * POST /api/sync/all
     */
    @PostMapping("/all")
    public ApiResponse<Map<String, String>> syncAll() {
        String result = dataSyncService.syncAll();
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 同步指定年份的 KPL 赛事
     * POST /api/sync/year?year=2025
     */
    @PostMapping("/year")
    public ApiResponse<Map<String, String>> syncByYear(@RequestParam int year) {
        String result = dataSyncService.syncByYear(year);
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 增量同步最新赛季（刷新比赛状态和榜单）
     * POST /api/sync/latest/incremental
     */
    @PostMapping("/latest/incremental")
    public ApiResponse<Map<String, String>> syncLatestIncremental() {
        String result = dataSyncService.syncLatestIncremental();
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 深度增量同步（补齐已结束比赛的对局和选手详情，含装备数据）
     * POST /api/sync/latest/deep-incremental?matchLimit=10
     * POST /api/sync/latest/deep-incremental?matchLimit=10&leagueId=20260001
     */
    @PostMapping("/latest/deep-incremental")
    public ApiResponse<Map<String, String>> syncLatestDeepIncremental(
            @RequestParam(defaultValue = "10") int matchLimit,
            @RequestParam(required = false) String leagueId) {
        String result = dataSyncService.syncLatestDeepIncremental(matchLimit, leagueId);
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 同步指定赛季
     * POST /api/sync/season?leagueId=20260001&deep=false
     */
    @PostMapping("/season")
    public ApiResponse<Map<String, String>> syncSeason(
            @RequestParam String leagueId,
            @RequestParam(defaultValue = "false") boolean deep) {
        String result = dataSyncService.syncSeason(leagueId, deep);
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 同步赛事列表
     * POST /api/sync/leagues
     */
    @PostMapping("/leagues")
    public ApiResponse<Map<String, Object>> syncLeagues() {
        int count = dataSyncService.syncLeagues();
        return ApiResponse.ok(Map.of("synced", count));
    }

    /**
     * 同步装备基础信息（价格、属性等，从 pvp.qq.com 爬取）
     * POST /api/sync/equip-info
     */
    @PostMapping("/equip-info")
    public ApiResponse<Map<String, Object>> syncEquipInfo() {
        int count = equipInfoSyncService.syncEquipInfo();
        return ApiResponse.ok(Map.of("synced", count));
    }

    /**
     * 同步铭文基础信息（从 pvp.qq.com 爬取）
     * POST /api/sync/inscription-info
     */
    @PostMapping("/inscription-info")
    public ApiResponse<Map<String, Object>> syncInscriptionInfo() {
        int count = equipInfoSyncService.syncInscriptionInfo();
        return ApiResponse.ok(Map.of("synced", count));
    }

    /**
     * 修复装备名称编码（用 equip_info 表的正确名称修复 battle_player_equip 的乱码）
     * POST /api/sync/fix-equip-encoding
     */
    @PostMapping("/fix-equip-encoding")
    public ApiResponse<Map<String, Object>> fixEquipEncoding() {
        int count = dataSyncService.fixEquipNameEncoding();
        return ApiResponse.ok(Map.of("fixed", count));
    }

    /**
     * 修复比赛阶段描述（官方 API 把部分赛事的季后赛/决赛扁平化成「常规赛」）
     * POST /api/sync/fix-match-stage
     */
    @PostMapping("/fix-match-stage")
    public ApiResponse<Map<String, Object>> fixMatchStage() {
        int count = dataSyncService.fixMatchStageDescription();
        return ApiResponse.ok(Map.of("fixed", count));
    }

    /**
     * 重置指定赛季的分路数据并重新同步（修正英雄映射导致的分路错误）
     * POST /api/sync/reset-positions?leagueId=20200005
     */
    @PostMapping("/reset-positions")
    public ApiResponse<Map<String, String>> resetPositions(@RequestParam String leagueId) {
        String result = dataSyncService.resetPositionsAndResync(leagueId);
        return ApiResponse.ok(Map.of("result", result));
    }

    /**
     * 查看最近同步任务
     * GET /api/sync/jobs?limit=10
     */
    @GetMapping("/jobs")
    public ApiResponse<Object> latestJobs(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(dataSyncService.latestJobs(limit));
    }

    /**
     * 查看同步游标
     * GET /api/sync/cursors
     */
    @GetMapping("/cursors")
    public ApiResponse<Object> listCursors() {
        return ApiResponse.ok(dataSyncService.listCursors());
    }

    /**
     * 查看单个同步任务
     * GET /api/sync/jobs/{id}
     */
    @GetMapping("/jobs/{id}")
    public ApiResponse<Object> getJob(@PathVariable Long id) {
        return ApiResponse.ok(dataSyncService.getJob(id));
    }

    /**
     * 重试失败同步任务
     * POST /api/sync/jobs/{id}/retry
     */
    @PostMapping("/jobs/{id}/retry")
    public ApiResponse<Map<String, String>> retryJob(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("result", dataSyncService.retryJob(id)));
    }
}
