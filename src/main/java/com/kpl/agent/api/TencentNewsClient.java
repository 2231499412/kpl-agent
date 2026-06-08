package com.kpl.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 腾讯王者荣耀公告 API 客户端
 * 用于获取版本更新、英雄平衡性调整等公告内容
 */
@Slf4j
@Component
public class TencentNewsClient {

    private static final String LIST_URL = "https://apps.game.qq.com/cmc/cross";
    private static final String DETAIL_URL = "https://apps.game.qq.com/wmp/v3.1/public/searchNews.php";
    private static final String TOKEN = "234ce0aef3020cb83887883877b64869";
    private static final int SERVICE_ID = 18;
    private static final String SOURCE = "web_pc";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TencentNewsClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取版本相关公告列表（版本更新 + 英雄平衡性调整）
     */
    public List<Map<String, Object>> getNewsList(int limit) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String sign = md5(TOKEN + SOURCE + SERVICE_ID + timestamp);

            String url = LIST_URL + "?serviceId=" + SERVICE_ID
                    + "&filter=channel&sortby=sIdxTime&source=" + SOURCE
                    + "&limit=" + (limit * 3) + "&logic=or&typeids=1&withtop=no"
                    + "&chanid=1762&start=0"
                    + "&exclusiveChannel=4&exclusiveChannelSign=" + sign + "&time=" + timestamp;

            String json = restTemplate.getForObject(url, String.class);
            if (json == null) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) return Collections.emptyList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : items) {
                String title = item.path("sTitle").asText("");
                if (!isVersionRelated(title)) continue;

                Map<String, Object> note = new LinkedHashMap<>();
                note.put("id", item.path("iId").asText(""));
                note.put("title", title);
                note.put("date", item.path("sCreated").asText("").split(" ")[0]);

                // 摘要：取标签信息中的分类
                String tagInfo = item.path("sTagInfo").asText("");
                note.put("tags", parseTags(tagInfo));

                result.add(note);
                if (result.size() >= limit) break;
            }
            return result;
        } catch (Exception e) {
            log.warn("获取公告列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取公告详情（HTML 内容）
     */
    public Map<String, Object> getNewsDetail(String id) {
        try {
            String url = DETAIL_URL + "?p0=" + SERVICE_ID + "&source=" + SOURCE + "&id=" + id;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return Collections.emptyMap();

            // JSONP 格式：var searchObj={...}
            String json = response.trim();
            if (json.startsWith("var searchObj=")) {
                json = json.substring("var searchObj=".length());
            }
            if (json.endsWith(";")) {
                json = json.substring(0, json.length() - 1);
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode msg = root.path("msg");
            if (!msg.isObject()) return Collections.emptyMap();

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("contentHtml", msg.path("sContent").asText(""));
            detail.put("title", msg.path("sTitle").asText(""));
            detail.put("date", msg.path("sCreated").asText("").split(" ")[0]);
            return detail;
        } catch (Exception e) {
            log.warn("获取公告详情失败 (id={}): {}", id, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private boolean isVersionRelated(String title) {
        return title.contains("版本更新")
                || title.contains("英雄平衡")
                || title.contains("平衡性调整");
    }

    private List<String> parseTags(String tagInfo) {
        List<String> tags = new ArrayList<>();
        if (tagInfo == null || tagInfo.isEmpty()) return tags;
        for (String part : tagInfo.split(",")) {
            String[] kv = part.split("\\|", 2);
            if (kv.length == 2 && !kv[1].isEmpty()) {
                tags.add(kv[1]);
            }
        }
        return tags;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
