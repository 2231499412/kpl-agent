package com.kpl.agent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpl.agent.config.AiConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 报告生成器：调用 LLM API 将查询结果生成自然语言分析报告
 * 支持 OpenAI 兼容接口（OpenAI / DashScope / 本地模型）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private final AiConfig aiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** LLM 专用 RestTemplate，超时时间更长（推理模型需要 60s+） */
    private RestTemplate llmRestTemplate;
    private WebClient webClient;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        this.llmRestTemplate = new RestTemplate(factory);
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    private static final String SYSTEM_PROMPT = """
            你是一个专业的KPL（王者荣耀职业联赛）电竞数据分析师。
            根据用户的问题和查询到的数据，生成简洁、专业的分析报告。
            要求：
            1. 用中文回答
            2. 数据要准确，引用具体数字
            3. 给出有价值的分析见解
            4. 语言简洁，避免废话
            5. 优先使用资料库数据；如果 queryResult 中包含 webSearch，说明“资料库未命中/资料不足，以下结合联网搜索结果”，并引用网页标题或 URL
            6. 不要把联网搜索结果伪装成资料库数据；无法确认的内容要明确说“不确定”
            7. 如果资料库和联网搜索都为空或出错，礼貌告知用户，并建议换一种更具体的问法
            """;

    /**
     * 根据查询结果生成分析报告
     * @param userQuestion 用户原始问题
     * @param intent 识别到的意图
     * @param queryResult 工具查询结果
     */
    public String generate(String userQuestion, String intent, Map<String, Object> queryResult) {
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            log.info("未配置 AI_API_KEY，使用降级报告");
            return fallbackReport(userQuestion, queryResult);
        }

        // 预处理：将比赛数据转为 LLM 易读格式（明确胜负关系）
        Map<String, Object> processed = preprocessForLlm(queryResult);

        // 构建 prompt
        String dataStr;
        try {
            dataStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processed);
        } catch (Exception e) {
            dataStr = processed.toString();
        }

        String userPrompt = """
                用户问题：%s
                意图类型：%s
                查询数据：
                %s

                请根据以上数据生成分析报告。
                如果查询数据中有多个分区（例如 teamStats、players、recentMatches），请综合这些分区回答，不要只复述第一段数据。
                """.formatted(userQuestion, intent, dataStr);

        try {
            return callLlm(SYSTEM_PROMPT, userPrompt);
        } catch (Exception e) {
            log.error("调用 LLM 失败，降级为纯数据展示", e);
            return fallbackReport(userQuestion, queryResult);
        }
    }

    /** 调用 LLM API（OpenAI 兼容格式） */

    /**
     * 流式生成：返回 Flux<String>，每个元素是一个文本 token
     */
    public Flux<String> generateStream(String userQuestion, String intent, Map<String, Object> queryResult) {
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            return Flux.just(fallbackReport(userQuestion, queryResult));
        }

        Map<String, Object> processed = preprocessForLlm(queryResult);
        String dataStr;
        try {
            dataStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processed);
        } catch (Exception e) {
            dataStr = processed.toString();
        }

        String userPrompt = """
                用户问题：%s
                意图类型：%s
                查询数据：
                %s

                请根据以上数据生成分析报告。
                如果查询数据中有多个分区（例如 teamStats、players、recentMatches），请综合这些分区回答，不要只复述第一段数据。
                """.formatted(userQuestion, intent, dataStr);

        return callLlmStream(SYSTEM_PROMPT, userPrompt)
                .onErrorResume(e -> {
                    log.error("流式 LLM 调用失败，降级为纯数据", e);
                    return Flux.just(fallbackReport(userQuestion, queryResult));
                });
    }

    /** 流式调用 LLM API（使用 java.net.http.HttpClient 逐行读取 SSE） */
    private Flux<String> callLlmStream(String systemPrompt, String userPrompt) {
        String url = aiConfig.getBaseUrl() + "/chat/completions";
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(Map.of(
                    "model", aiConfig.getModel(),
                    "stream", true,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.7,
                    "max_tokens", 2048
            ));
        } catch (Exception e) {
            return Flux.error(e);
        }

        return Flux.create(sink -> {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + aiConfig.getApiKey())
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson, java.nio.charset.StandardCharsets.UTF_8))
                        .build();

                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());
                response.body().forEach(line -> {
                    if (sink.isCancelled()) return;
                    if (line.isBlank() || line.equals("data: [DONE]")) return;
                    String json = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (json.isEmpty() || json.equals("[DONE]")) return;
                    try {
                        var root = objectMapper.readTree(json);
                        var delta = root.path("choices").path(0).path("delta");
                        String content = delta.path("content").asText("");
                        if (!content.isEmpty()) {
                            sink.next("{\"t\":\"c\",\"d\":" + objectMapper.writeValueAsString(content) + "}");
                            return;
                        }
                        String reasoning = delta.path("reasoning_content").asText("");
                        if (!reasoning.isEmpty()) {
                            sink.next("{\"t\":\"r\",\"d\":" + objectMapper.writeValueAsString(reasoning) + "}");
                        }
                    } catch (Exception e) {
                        log.debug("解析流式响应跳过: {}", json);
                    }
                });
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * 预处理数据：将原始比赛数据转为 LLM 易读格式，明确胜负关系。
     * 原始数据的 camp1/camp2 结构容易让 LLM 混淆胜负。
     * 比分统一从查询对象（keyword）的视角显示。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> preprocessForLlm(Map<String, Object> queryResult) {
        Object items = queryResult.get("data");
        if (!(items instanceof List<?> list) || list.isEmpty()) return queryResult;

        // 检测是否为比赛数据
        Object first = list.get(0);
        Map<String, Object> firstMap = toMap(first);
        if (!firstMap.containsKey("camp1TeamName") || !firstMap.containsKey("winCamp")) return queryResult;

        // keyword 标识查询的战队，用于确定视角
        String keyword = String.valueOf(queryResult.getOrDefault("keyword", ""));

        // 转换为清晰的比赛结果列表
        List<Map<String, Object>> processed = new java.util.ArrayList<>();
        for (Object item : list) {
            Map<String, Object> m = toMap(item);
            String team1 = String.valueOf(m.getOrDefault("camp1TeamName", ""));
            String team2 = String.valueOf(m.getOrDefault("camp2TeamName", ""));
            int score1 = ((Number) m.getOrDefault("camp1Score", 0)).intValue();
            int score2 = ((Number) m.getOrDefault("camp2Score", 0)).intValue();
            int winCamp = ((Number) m.getOrDefault("winCamp", 0)).intValue();

            // 从查询对象视角排列：查询的队伍在前
            boolean camp1IsSubject = team1.contains(keyword);
            String leftTeam = camp1IsSubject ? team1 : team2;
            String rightTeam = camp1IsSubject ? team2 : team1;
            int leftScore = camp1IsSubject ? score1 : score2;
            int rightScore = camp1IsSubject ? score2 : score1;
            boolean leftWon = (winCamp == 1 && camp1IsSubject) || (winCamp == 2 && !camp1IsSubject);

            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("match", "%s %d:%d %s".formatted(leftTeam, leftScore, rightScore, rightTeam));
            entry.put("result", leftWon ? "胜" : "负");
            entry.put("stage", m.getOrDefault("matchStageDesc", ""));
            entry.put("time", fmtTime(m.get("startTime")));
            entry.put("address", m.getOrDefault("matchAddress", ""));
            processed.add(entry);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>(queryResult);
        result.put("data", processed);
        result.put("dataFormat", "每条记录: match=比分(视角在前的队伍), result=胜/负(视角队伍), stage=赛段, time=时间, address=地点");
        return result;
    }
    private String callLlm(String systemPrompt, String userPrompt) {
        String url = aiConfig.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiConfig.getApiKey());

        // MiMo v2.5 Pro 是推理模型，reasoning tokens 也算在 max_tokens 里，需要给大一些
        Map<String, Object> body = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.7,
                "max_tokens", 2048
        );

        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            log.info("请求 LLM: url={}, body={}", url, bodyJson);
            HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);
            ResponseEntity<String> response = llmRestTemplate.exchange(url, HttpMethod.POST, request, String.class);

            log.info("LLM 响应: status={}, body={}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var root = objectMapper.readTree(response.getBody());
                var message = root.path("choices").path(0).path("message");
                String content = message.path("content").asText("");
                if (content.isBlank()) {
                    content = message.path("reasoning_content").asText("生成报告失败");
                }
                return content;
            }
        } catch (Exception e) {
            log.error("LLM API 调用异常: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("LLM API 调用失败: " + e.getMessage(), e);
        }
        return "生成报告失败";
    }

    /** 降级报告：LLM 不可用时直接格式化数据 */
    @SuppressWarnings("unchecked")
    private String fallbackReport(String question, Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("**查询结果**\n\n");
        sb.append("问题：").append(question).append("\n\n");

        Object items = data.get("data");
        if (items instanceof List<?> list) {
            sb.append("共找到 ").append(list.size()).append(" 条数据：\n\n");
            for (int i = 0; i < Math.min(list.size(), 5); i++) {
                sb.append(i + 1).append(". ").append(formatItem(toMap(list.get(i)))).append("\n");
            }
            if (list.size() > 5) {
                sb.append("... 等共 ").append(list.size()).append(" 条\n");
            }
        } else if (items != null) {
            sb.append(formatItem(toMap(items)));
        } else {
            Object error = data.get("error");
            sb.append(error != null ? error.toString() : "未查询到相关数据。");
        }

        sb.append("\n\n*（AI 分析服务暂不可用，以上为原始数据）*");
        return sb.toString();
    }

    /** 将对象转为 Map（Entity → Map） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return objectMapper.convertValue(obj, Map.class);
    }

    /** 将单条数据格式化为可读文本 */
    private String formatItem(Map<String, Object> map) {
        // 比赛数据：蓝方 比分:比分 红方 | 赛段 | 时间 | 地点
        if (map.containsKey("camp1TeamName") && map.containsKey("camp2TeamName")) {
            return "%s %s:%s %s | %s | %s".formatted(
                    map.get("camp1TeamName"),
                    map.get("camp1Score"),
                    map.get("camp2Score"),
                    map.get("camp2TeamName"),
                    map.getOrDefault("matchStageDesc", ""),
                    fmtTime(map.get("startTime")));
        }
        // 选手数据
        if (map.containsKey("playerName") && map.containsKey("teamName")) {
            return "%s | %s | %s | 胜率:%s%% | KDA:%s".formatted(
                    map.get("playerName"),
                    map.get("teamName"),
                    map.getOrDefault("positionDesc", ""),
                    fmtPct(map.get("winRate")),
                    fmtNum(map.get("avgKda")));
        }
        // 英雄数据
        if (map.containsKey("heroName") && map.containsKey("pickRate")) {
            return "%s | 出场:%s | Pick:%s%% | Ban:%s%% | 胜率:%s%%".formatted(
                    map.get("heroName"),
                    map.get("battleCount"),
                    fmtPct(map.get("pickRate")),
                    fmtPct(map.get("banRate")),
                    fmtPct(map.get("winRate")));
        }
        // 战队数据
        if (map.containsKey("teamName") && map.containsKey("battleCount")) {
            return "%s | 局数:%s | 胜率:%s%% | KDA:%s".formatted(
                    map.get("teamName"),
                    map.get("battleCount"),
                    fmtPct(map.get("winRate")),
                    fmtNum(map.get("avgKda")));
        }
        // 通用格式
        StringBuilder sb = new StringBuilder();
        Set<String> skip = Set.of("id", "matchId", "leagueId", "camp1TeamId", "camp2TeamId",
                "teamId", "heroId", "playerId", "createdAt", "updatedAt", "status");
        for (var entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null || skip.contains(key)) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(val);
        }
        return sb.length() > 0 ? sb.toString() : map.toString();
    }

    private String fmtTime(Object val) {
        if (val == null) return "";
        return val.toString().replace("T", " ").substring(0, Math.min(16, val.toString().length()));
    }

    private String fmtPct(Object val) {
        if (val == null) return "0";
        double d = val instanceof Number ? ((Number) val).doubleValue() : 0;
        return String.format("%.1f", d * 100);
    }

    private String fmtNum(Object val) {
        if (val == null) return "0";
        double d = val instanceof Number ? ((Number) val).doubleValue() : 0;
        return String.format("%.2f", d);
    }
}
