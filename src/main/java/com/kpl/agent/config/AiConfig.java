package com.kpl.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置：LLM API 地址和密钥
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
    private WebSearch webSearch = new WebSearch();

    @Data
    public static class WebSearch {
        /** 是否在资料库无结果或意图无法识别时自动联网搜索 */
        private boolean enabled = true;
        /** 搜索结果数量上限 */
        private int maxResults = 5;
        /** 搜索请求超时时间，避免外部搜索拖慢 Agent 回复 */
        private int timeoutMillis = 2500;
        /** 给搜索请求附加的领域关键词，降低泛化搜索噪音 */
        private String queryPrefix = "KPL 王者荣耀职业联赛";
    }
}
