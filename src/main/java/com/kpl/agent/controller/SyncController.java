package com.kpl.agent.controller;

import com.kpl.agent.service.DataSyncService;
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
     * 深度增量同步最新赛季（补齐已结束比赛的对局和选手详情）
     * POST /api/sync/latest/deep-incremental?matchLimit=10
     */
    @PostMapping("/latest/deep-incremental")
    public ApiResponse<Map<String, String>> syncLatestDeepIncremental(
            @RequestParam(defaultValue = "10") int matchLimit) {
        String result = dataSyncService.syncLatestDeepIncremental(matchLimit);
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
