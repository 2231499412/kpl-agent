package com.kpl.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置：统一超时时间，用于调用外部 KPL API
 */
@Configuration
public class RestTemplateConfig {

    static {
        // 让 QQ 系域名不走代理，避免挂 VPN 时赛事数据请求失败
        String bypass = "*.qq.com|*.tga.qq.com|*.smoba.qq.com";
        System.setProperty("http.nonProxyHosts", bypass);
        System.setProperty("https.nonProxyHosts", bypass);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
