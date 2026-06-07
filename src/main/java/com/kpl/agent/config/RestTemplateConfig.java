package com.kpl.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
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
        // 强制 JVM 默认编码为 UTF-8，解决 Windows 平台 GBK 默认编码导致的中文乱码
        System.setProperty("file.encoding", "UTF-8");
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate rt = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        // 强制所有 StringHttpMessageConverter 使用 UTF-8
        rt.getMessageConverters().stream()
                .filter(c -> c instanceof StringHttpMessageConverter)
                .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));
        return rt;
    }
}
