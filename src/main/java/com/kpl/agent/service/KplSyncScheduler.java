package com.kpl.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * KPL 数据定时同步任务：默认关闭，通过 kpl.sync.enabled=true 开启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kpl.sync", name = "enabled", havingValue = "true")
public class KplSyncScheduler {

    private final DataSyncService dataSyncService;

    @Scheduled(cron = "${kpl.sync.latest-cron:0 0/30 * * * ?}")
    public void syncLatestSeason() {
        log.info("定时同步最新 KPL 赛季开始");
        try {
            String result = dataSyncService.syncLatestIncremental();
            log.info("定时同步最新 KPL 赛季完成: {}", result);
        } catch (Exception e) {
            log.error("定时同步最新 KPL 赛季失败", e);
        }
    }

    @Scheduled(cron = "${kpl.sync.deep-cron:0 10 3 * * ?}")
    public void syncLatestSeasonDetails() {
        log.info("定时深度增量同步最新 KPL 赛季开始");
        try {
            String result = dataSyncService.syncLatestDeepIncremental(10, null);
            log.info("定时深度增量同步最新 KPL 赛季完成: {}", result);
        } catch (Exception e) {
            log.error("定时深度增量同步最新 KPL 赛季失败", e);
        }
    }
}
