package com.kpl.agent.service;

import com.kpl.agent.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量联网搜索服务。
 *
 * 资料库没有命中时使用，不替代本地 KPL 数据。当前走 DuckDuckGo HTML 搜索，
 * 不需要额外 API Key；如果后续要接 SerpAPI/Bing/Tavily，可在这里替换实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final Pattern RESULT_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?(?:<a[^>]+class=\"result__snippet\"[^>]*>|<div[^>]+class=\"result__snippet\"[^>]*>)(.*?)</(?:a|div)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final AiConfig aiConfig;

    public Map<String, Object> search(String question) {
        if (!aiConfig.getWebSearch().isEnabled()) {
            return Map.of("enabled", false, "data", List.of());
        }

        int limit = Math.max(1, Math.min(aiConfig.getWebSearch().getMaxResults(), 8));
        int timeoutMillis = Math.max(500, Math.min(aiConfig.getWebSearch().getTimeoutMillis(), 10_000));
        String prefix = aiConfig.getWebSearch().getQueryPrefix();
        String query = (prefix == null || prefix.isBlank()) ? question : prefix + " " + question;
        String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        try {
            String html = fetchHtml(url, timeoutMillis);
            List<Map<String, Object>> results = parseResults(html, limit);
            return Map.of(
                    "enabled", true,
                    "provider", "DuckDuckGo HTML",
                    "query", query,
                    "timeoutMillis", timeoutMillis,
                    "count", results.size(),
                    "data", results
            );
        } catch (Exception e) {
            log.warn("联网搜索失败: {}", e.getMessage());
            return Map.of(
                    "enabled", true,
                    "query", query,
                    "timeoutMillis", timeoutMillis,
                    "error", "联网搜索失败或超时：" + e.getMessage(),
                    "data", List.of()
            );
        }
    }

    private String fetchHtml(String url, int timeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);

        RestTemplate searchRestTemplate = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 KPL-Data-Agent/1.0");
        headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");

        ResponseEntity<String> response = searchRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        return response.getBody();
    }

    private List<Map<String, Object>> parseResults(String html, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return results;
        }

        Matcher matcher = RESULT_PATTERN.matcher(html);
        while (matcher.find() && results.size() < limit) {
            String link = cleanUrl(decodeHtml(matcher.group(1)));
            String title = stripTags(decodeHtml(matcher.group(2)));
            String snippet = stripTags(decodeHtml(matcher.group(3)));
            if (!title.isBlank() && !link.isBlank()) {
                results.add(Map.of(
                        "title", title,
                        "url", link,
                        "snippet", snippet
                ));
            }
        }
        return results;
    }

    private String cleanUrl(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        int uddg = url.indexOf("uddg=");
        if (uddg >= 0) {
            String encoded = url.substring(uddg + 5);
            int amp = encoded.indexOf('&');
            if (amp >= 0) {
                encoded = encoded.substring(0, amp);
            }
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        }
        return url;
    }

    private String stripTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String decodeHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }
}
