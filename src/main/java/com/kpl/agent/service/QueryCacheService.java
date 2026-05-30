package com.kpl.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 查询缓存服务：封装 Redis 读写，让 Tool 层只关心 key、TTL 和数据加载逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return (T) cached;
            }
        } catch (Exception e) {
            log.warn("读取 Redis 缓存失败，直接查询数据库: key={}, error={}", key, e.getMessage());
        }

        T value = loader.get();
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("写入 Redis 缓存失败: key={}, error={}", key, e.getMessage());
        }
        return value;
    }

    public void evictByPattern(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理 Redis 缓存失败: pattern={}, error={}", pattern, e.getMessage());
        }
    }
}
