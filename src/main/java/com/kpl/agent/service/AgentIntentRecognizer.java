package com.kpl.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpl.agent.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 意图识别器：将用户问题转为结构化 JSON；失败时由 KplAgentService 的规则识别兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentIntentRecognizer {

    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    public Optional<AgentIntent> recognize(String message) {
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            return Optional.empty();
        }

        try {
            String prompt = """
                    你是 KPL 数据 Agent 的意图识别器。请只返回 JSON，不要解释。

                    intent 类型及含义：
                    - PLAYER_QUERY：查询某个选手的数据（关键词：选手、队员、XX的数据）
                    - HERO_QUERY：查询某个英雄的数据（关键词：英雄、XX胜率）
                    - TEAM_QUERY：查询某个战队的数据（关键词：战队、XX战绩）
                    - MATCH_QUERY：查询比赛/对局/复盘信息（关键词：比赛、复盘、对局、最近比赛、表现怎么样、打得怎么样）
                    - HERO_TOP：英雄排行榜（关键词：ban率最高、最热门英雄、胜率排行）
                    - TEAM_RANKING：战队排名/积分榜（关键词：排名、积分榜、排行）
                    - TEAM_HONORS：跨赛事荣誉总榜（关键词：荣誉、冠军最多、冠军次数、历史排名、总冠军）
                    - UNKNOWN：无法识别

                    重要规则：
                    1. 包含"比赛""复盘""对局"或"最近...表现/打得"的问句，intent 一定是 MATCH_QUERY
                    2. "表现怎么样"在有战队名时是 MATCH_QUERY，有选手名时是 PLAYER_QUERY
                    3. subject 提取实体名（选手名、英雄名、战队名），提取不到就填 null
                    4. sort: MATCH_QUERY 用 recent，HERO_TOP 用 ban/pick/win
                    5. limit 默认 10，MATCH_QUERY 默认 5

                    示例：
                    "AG 最近比赛表现怎么样" → {"intent":"MATCH_QUERY","subject":"AG","position":null,"sort":"recent","limit":5}
                    "一诺选手数据" → {"intent":"PLAYER_QUERY","subject":"一诺","position":null,"sort":null,"limit":10}
                    "公孙离胜率多少" → {"intent":"HERO_QUERY","subject":"公孙离","position":null,"sort":null,"limit":10}
                    "ban率最高的英雄" → {"intent":"HERO_TOP","subject":null,"position":null,"sort":"ban","limit":10}
                    "积分榜" → {"intent":"TEAM_RANKING","subject":null,"position":null,"sort":"ranking","limit":10}
                    "AG超玩会战绩" → {"intent":"TEAM_QUERY","subject":"AG超玩会","position":null,"sort":null,"limit":10}

                    用户问题：%s

                    返回 JSON：
                    """.formatted(message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(aiConfig.getApiKey());

            Map<String, Object> body = Map.of(
                    "model", aiConfig.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", "你只输出可解析 JSON。"),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0,
                    "max_tokens", 256
            );

            RestTemplate restTemplate = new RestTemplate();
            String url = aiConfig.getBaseUrl() + "/chat/completions";
            String requestJson = objectMapper.writeValueAsString(body);
            var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(requestJson, headers), String.class);
            var root = objectMapper.readTree(response.getBody());
            var msgNode = root.path("choices").path(0).path("message");
            String content = msgNode.path("content").asText("");
            if (content.isBlank()) {
                content = msgNode.path("reasoning_content").asText("{}");
            }
            String json = extractJson(content);
            log.info("LLM 意图识别原始返回: content={}, extractedJson={}", content, json);
            var parsed = objectMapper.readTree(json);

            KplAgentService.Intent intent = KplAgentService.Intent.valueOf(parsed.path("intent").asText("UNKNOWN"));
            if (intent == KplAgentService.Intent.UNKNOWN) {
                log.info("LLM 返回 UNKNOWN，降级到规则识别");
                return Optional.empty();
            }
            String subject = blankToNull(parsed.path("subject").asText(null));
            String position = blankToNull(parsed.path("position").asText(null));
            String sort = blankToNull(parsed.path("sort").asText(null));
            int limit = parsed.path("limit").asInt(10);
            return Optional.of(new AgentIntent(intent, subject, position, sort, limit));
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，降级到规则识别: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    String extractJson(String content) {
        String text = content == null ? "{}" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json\\s*", "").replaceFirst("^```\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
