package com.kpl.agent.service;

import com.kpl.agent.config.AiConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统状态服务：聚合 DB、Redis、AI 配置等基础运行状态，便于部署后自检。
 */
@Service
@RequiredArgsConstructor
public class SystemStatusService {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AiConfig aiConfig;
    private final LeagueQueryService leagueQueryService;

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("database", databaseStatus());
        status.put("redis", redisStatus());
        status.put("aiConfigured", aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank());
        status.put("aiModel", aiConfig.getModel());
        status.put("latestLeague", leagueQueryService.latestKplLeague());
        return status;
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    private String redisStatus() {
        try {
            redisTemplate.opsForValue().set("kpl:healthcheck", "ok");
            Object value = redisTemplate.opsForValue().get("kpl:healthcheck");
            return "ok".equals(value) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}
