package com.kpl.agent.service;

/**
 * Agent 结构化意图：把自然语言识别结果固定成可路由的数据结构。
 */
public record AgentIntent(
        KplAgentService.Intent intent,
        String subject,
        String position,
        String sort,
        Integer limit
) {

    public static AgentIntent of(KplAgentService.Intent intent, String subject) {
        return new AgentIntent(intent, subject, null, null, 10);
    }
}
