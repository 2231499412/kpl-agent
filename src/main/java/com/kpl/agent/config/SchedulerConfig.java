package com.kpl.agent.config;

import com.kpl.agent.service.DataSyncService;
import com.kpl.agent.service.LeagueQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulerConfig {

    private final DataSyncService dataSyncService;
    private final LeagueQueryService leagueQueryService;

    /**
     * 每天凌晨3点增量同步最新赛季数据
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void autoSync() {
        log.info("[定时任务] 开始增量同步...");
        try {
            String result = dataSyncService.syncLatestIncremental();
            log.info("[定时任务] 同步完成: {}", result);
        } catch (Exception e) {
            log.error("[定时任务] 同步失败", e);
        }
    }
}
