package com.kpl.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.ai.ReportGenerator;
import com.kpl.agent.tool.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 核心服务：意图识别 + 工具路由 + 报告生成
 *
 * 流程：用户输入 → 意图识别 → 参数提取 → 调用工具 → LLM 生成报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KplAgentService {

    private final PlayerStatsTool playerStatsTool;
    private final HeroStatsTool heroStatsTool;
    private final TeamStatsTool teamStatsTool;
    private final MatchAnalysisTool matchAnalysisTool;
    private final EquipStatsTool equipStatsTool;
    private final ReportGenerator reportGenerator;
    private final AgentIntentRecognizer agentIntentRecognizer;
    private final LeagueQueryService leagueQueryService;

    /** 意图类型枚举 */
    public enum Intent {
        PLAYER_QUERY,    // 选手查询："XX选手数据怎么样"
        HERO_QUERY,      // 英雄查询："XX英雄胜率多少"
        TEAM_QUERY,      // 战队查询："AG超玩会战绩"
        MATCH_QUERY,     // 比赛查询："AG最近比赛复盘"
        HERO_TOP,        // 英雄排行："ban率最高的英雄"
        TEAM_RANKING,    // 战队排名："积分榜"
        TEAM_HONORS,     // 荣誉总榜："荣誉最多的队伍"
        EQUIP_QUERY,     // 装备查询："XX出什么装备"
        UNKNOWN
    }

    /**
     * Agent 主入口：接收自然语言，返回分析报告
     */
    public String chat(String userMessage) {
        log.info("Agent 收到消息: {}", userMessage);

        // 1. 意图识别：优先 LLM 结构化识别，失败时使用本地规则兜底。
        AgentIntent agentIntent = agentIntentRecognizer
                .recognize(userMessage)
                .orElseGet(() -> recognizeIntentByRule(userMessage));
        log.info("识别意图: {}", agentIntent);

        // 2. 获取最新赛季
        String leagueId = leagueQueryService.requireLeagueId(null);

        // 3. 提取参数 + 调用对应工具
        Map<String, Object> queryResult = routeToTool(agentIntent, userMessage, leagueId);

        // 4. LLM 生成分析报告
        return reportGenerator.generate(userMessage, agentIntent.intent().name(), queryResult);
    }

    /** 基于关键词的意图识别 */
    private AgentIntent recognizeIntentByRule(String message) {
        // 装备查询
        if (containsAny(message, "装备", "出装", "出什么装", "怎么出", "铭文")) {
            String heroName = extractKeyword(message, heroPatterns());
            if (heroName == null) {
                // 尝试从消息中提取英雄名（装备查询时通常前面是英雄名）
                heroName = extractBeforeKeyword(message, "装备", "出装", "出什么装");
            }
            return new AgentIntent(Intent.EQUIP_QUERY, heroName, null, null, 6);
        }
        // 比赛复盘（优先匹配，避免"表现"被选手规则抢走）
        if (containsAny(message, "比赛", "复盘", "对局")) {
            return new AgentIntent(Intent.MATCH_QUERY, extractTeamName(message), null, "recent", 5);
        }
        // 英雄排行
        if (containsAny(message, "ban率", "被ban", "禁用率", "ban最高", "最热门")) {
            return new AgentIntent(Intent.HERO_TOP, null, null, "ban", 10);
        }
        if (containsAny(message, "胜率最高", "胜率排行")) {
            return new AgentIntent(Intent.HERO_TOP, null, null, "win", 10);
        }
        // 英雄查询
        if (containsAny(message, "英雄", "胜率", "出场率", "pick率")) {
            return new AgentIntent(Intent.HERO_QUERY, extractKeyword(message, heroPatterns()), null, "pick", 10);
        }
        // 荣誉总榜（跨赛事）
        if (containsAny(message, "荣誉", "冠军最多", "冠军次数", "历史排名", "总冠军")) {
            return new AgentIntent(Intent.TEAM_HONORS, null, null, "honors", 10);
        }
        // 战队排名
        if (containsAny(message, "排名", "积分榜", "排行")) {
            return new AgentIntent(Intent.TEAM_RANKING, null, null, "ranking", 10);
        }
        // 选手相关
        if (containsAny(message, "选手", "队员", "数据怎么样", "表现")) {
            String name = extractKeyword(message, playerPatterns());
            return new AgentIntent(Intent.PLAYER_QUERY, name, extractPosition(message), null, 10);
        }
        // 战队查询
        if (containsAny(message, "战队", "队伍") || containsTeamName(message)) {
            return new AgentIntent(Intent.TEAM_QUERY, extractTeamName(message), null, null, 10);
        }
        // 最近比赛（兜底：包含"最近"且有战队名）
        if (containsAny(message, "最近") && containsTeamName(message)) {
            return new AgentIntent(Intent.MATCH_QUERY, extractTeamName(message), null, "recent", 5);
        }

        return new AgentIntent(Intent.UNKNOWN, null, null, null, 10);
    }

    /** 根据意图路由到对应工具 */
    private Map<String, Object> routeToTool(AgentIntent agentIntent, String message, String leagueId) {
        String subject = agentIntent.subject();
        int limit = normalizeLimit(agentIntent.limit());
        return switch (agentIntent.intent()) {
            case PLAYER_QUERY -> {
                if (subject != null) {
                    yield playerStatsTool.queryByName(subject, leagueId);
                }
                String pos = agentIntent.position() != null ? agentIntent.position() : extractPosition(message);
                if (pos != null) {
                    yield playerStatsTool.queryByPosition(pos, leagueId);
                }
                yield Map.of("error", "未识别到选手名或位置");
            }
            case HERO_QUERY -> {
                if (subject != null) {
                    yield heroStatsTool.queryByName(subject, leagueId);
                }
                yield heroStatsTool.queryTopPickRate(leagueId, limit);
            }
            case HERO_TOP -> {
                if ("ban".equals(agentIntent.sort()) || containsAny(message, "ban")) {
                    yield heroStatsTool.queryTopBanRate(leagueId, limit);
                }
                if ("win".equals(agentIntent.sort()) || containsAny(message, "胜率")) {
                    yield heroStatsTool.queryTopWinRate(leagueId, 5, limit);
                }
                yield heroStatsTool.queryTopPickRate(leagueId, limit);
            }
            case TEAM_QUERY -> {
                String name = subject != null ? subject : extractTeamName(message);
                if (name != null) {
                    yield teamStatsTool.queryByName(name, leagueId);
                }
                yield teamStatsTool.queryRanking(leagueId);
            }
            case TEAM_RANKING -> teamStatsTool.queryRanking(leagueId);
            case TEAM_HONORS -> teamStatsTool.queryHonors();
            case MATCH_QUERY -> {
                String name = subject != null ? subject : extractTeamName(message);
                if (name != null) {
                    yield matchAnalysisTool.queryRecentMatches(name, leagueId, limit);
                }
                yield Map.of("error", "请指定战队名");
            }
            case EQUIP_QUERY -> {
                if (subject != null) {
                    yield equipStatsTool.queryByHero(subject, leagueId, limit);
                }
                yield Map.of("error", "请指定英雄名，例如：公孙离出什么装备");
            }
            default -> Map.of("error", "抱歉，我没理解您的问题。可以试试：XX选手数据、XX英雄胜率、AG战绩、积分榜");
        };
    }

    // ==================== 参数提取工具方法 ====================

    /** 常见战队名列表 */
    private static final String[] TEAM_NAMES = {
            "AG超玩会", "AG", "狼队", "重庆狼队", "WB", "北京WB",
            "JDG", "北京JDG", "TTG", "广州TTG", "Hero", "南通Hero",
            "eStarPro", "武汉eStar", "DYG", "深圳DYG", "DRG", "佛山DRG",
            "KSG", "WE", "西安WE", "LGD", "杭州LGD", "TES", "长沙TES",
            "EDG", "上海EDG", "RNG", "上海RNG", "RW侠", "济南RW",
            "情久", "桐乡情久", "TCG", "无锡TCG"
    };

    private boolean containsTeamName(String message) {
        for (String name : TEAM_NAMES) {
            if (message.contains(name)) return true;
        }
        return false;
    }

    private String extractTeamName(String message) {
        // 优先匹配长名称（避免 "AG" 误匹配）
        for (String name : TEAM_NAMES) {
            if (message.contains(name)) return name;
        }
        return null;
    }

    private String extractKeyword(String message, String[] patterns) {
        for (String p : patterns) {
            Pattern pattern = Pattern.compile(p);
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String[] playerPatterns() {
        return new String[]{
                "([\\u4e00-\\u9fa5a-zA-Z0-9]+)选手",
                "选手([\\u4e00-\\u9fa5a-zA-Z0-9]+)",
                "([\\u4e00-\\u9fa5a-zA-Z0-9]+)的数据",
                "([\\u4e00-\\u9fa5a-zA-Z0-9]+)表现"
        };
    }

    private String[] heroPatterns() {
        return new String[]{
                "([\\u4e00-\\u9fa5]+)胜率",
                "([\\u4e00-\\u9fa5]+)出场",
                "([\\u4e00-\\u9fa5]+)英雄",
                "英雄([\\u4e00-\\u9fa5]+)"
        };
    }

    private String extractPosition(String message) {
        if (message.contains("中路") || message.contains("中单")) return "中路";
        if (message.contains("打野")) return "打野";
        if (message.contains("对抗路") || message.contains("边路")) return "对抗路";
        if (message.contains("发育路") || message.contains("射手")) return "发育路";
        if (message.contains("游走") || message.contains("辅助")) return "游走";
        return null;
    }

    /** 提取关键词前面的实体名，如"公孙离出装" → "公孙离" */
    private String extractBeforeKeyword(String message, String... keywords) {
        for (String kw : keywords) {
            int idx = message.indexOf(kw);
            if (idx > 0) {
                // 取关键词前面的连续中文/字母/数字
                String before = message.substring(0, idx).trim();
                // 从末尾向前扫描，提取最后一个实体名
                StringBuilder sb = new StringBuilder();
                for (int i = before.length() - 1; i >= 0; i--) {
                    char c = before.charAt(i);
                    if (c >= 0x4e00 && c <= 0x9fa5 || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                        sb.insert(0, c);
                    } else {
                        break;
                    }
                }
                if (sb.length() > 0) return sb.toString();
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 10;
        }
        return Math.max(1, Math.min(limit, 50));
    }

}
