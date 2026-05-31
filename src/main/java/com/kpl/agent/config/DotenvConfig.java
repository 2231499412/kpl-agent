package com.kpl.agent.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 在 Spring 容器启动前加载 .env 文件，确保 @ConfigurationProperties 能读到值
 */
@Slf4j
public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(ctx.getEnvironment().getProperty("user.dir", System.getProperty("user.dir")))
                    .ignoreIfMissing()
                    .load();

            ConfigurableEnvironment env = ctx.getEnvironment();
            Map<String, Object> props = new HashMap<>();
            dotenv.entries().forEach(e -> {
                String key = e.getKey();
                String val = e.getValue();
                if (val != null && !val.isBlank()) {
                    // 设置到 Spring Environment（优先级低于环境变量和系统属性）
                    props.put(key, val);
                    // 也设置为系统属性（兼容 ${VAR:default} 占位符）
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, val);
                    }
                }
            });
            env.getPropertySources().addLast(new MapPropertySource("dotenv", props));
            log.info("已加载 .env 文件，共 {} 个变量", props.size());
        } catch (Exception e) {
            log.warn("加载 .env 文件失败: {}", e.getMessage());
        }
    }
}
