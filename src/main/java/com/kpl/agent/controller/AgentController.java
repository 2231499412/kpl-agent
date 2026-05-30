package com.kpl.agent.controller;

import com.kpl.agent.service.KplAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 对话接口：接收自然语言问题，返回分析报告
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final KplAgentService agentService;

    /**
     * Agent 对话入口
     * POST /api/agent/chat
     * Body: {"message": "AG超玩会最近战绩怎么样"}
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
}
