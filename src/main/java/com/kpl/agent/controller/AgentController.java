package com.kpl.agent.controller;

import com.kpl.agent.service.KplAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 对话接口：接收自然语言问题，返回分析报告（支持流式）
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final KplAgentService agentService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /**
     * Agent 对话入口（同步）
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            throw new IllegalArgumentException("请输入问题");
        }
        String reply = agentService.chat(message);
        return ApiResponse.ok(Map.of("reply", reply));
    }

    /**
     * Agent 流式对话（SSE）
     * POST /api/agent/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        SseEmitter emitter = new SseEmitter(120_000L);

        sseExecutor.execute(() -> {
            try {
                agentService.chatStream(message).subscribe(
                        token -> {
                            try {
                                emitter.send(SseEmitter.event().data(token));
                            } catch (Exception e) {
                                log.debug("SSE send failed: {}", e.getMessage());
                            }
                        },
                        error -> {
                            log.error("SSE stream error", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.complete();
                            }
                        }
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> { /* cleanup */ });
        return emitter;
    }
}
