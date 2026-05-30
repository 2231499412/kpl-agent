package com.kpl.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kpl.agent.config.AiConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntentRecognizerTest {

    private final AgentIntentRecognizer recognizer = new AgentIntentRecognizer(new AiConfig(), new ObjectMapper());

    @Test
    void extractJsonFromMarkdownFence() {
        String content = """
                ```json
                {"intent":"TEAM_QUERY","subject":"AG","sort":null,"limit":10}
                ```
                """;

        assertThat(recognizer.extractJson(content))
                .isEqualTo("{\"intent\":\"TEAM_QUERY\",\"subject\":\"AG\",\"sort\":null,\"limit\":10}");
    }

    @Test
    void extractJsonFromPlainTextWrapper() {
        String content = "结果如下：{\"intent\":\"HERO_TOP\",\"subject\":null,\"sort\":\"ban\",\"limit\":10}";

        assertThat(recognizer.extractJson(content))
                .isEqualTo("{\"intent\":\"HERO_TOP\",\"subject\":null,\"sort\":\"ban\",\"limit\":10}");
    }
}
