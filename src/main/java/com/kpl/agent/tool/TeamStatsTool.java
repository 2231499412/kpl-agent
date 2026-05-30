package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.League;
import com.kpl.agent.entity.Match;
import com.kpl.agent.entity.TeamStats;
import com.kpl.agent.mapper.LeagueMapper;
import com.kpl.agent.mapper.MatchMapper;
import com.kpl.agent.mapper.TeamStatsMapper;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 战队数据查询工具：按战队名查询战绩、胜率、场均数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamStatsTool {

    private final TeamStatsMapper teamStatsMapper;
    private final MatchMapper matchMapper;
    private final LeagueMapper leagueMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** 按战队名查询 */
    public Map<String, Object> queryByName(String teamName, String leagueId) {
        String key = "kpl:%s:team:name:%s".formatted(leagueId, teamName);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<TeamStats> list = teamStatsMapper.selectList(
                    new LambdaQueryWrapper<TeamStats>()
                            .eq(TeamStats::getLeagueId, leagueId)
                            .like(TeamStats::getTeamName, teamName));
            return buildResult("team_by_name", teamName, list);
        });
    }

    /** 查询排名（按赛事名次 + KDA 排序） */
    public Map<String, Object> queryRanking(String leagueId) {
        String key = "kpl:%s:team:ranking".formatted(leagueId);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<TeamStats> list = teamStatsMapper.selectList(
                    new LambdaQueryWrapper<TeamStats>()
                            .eq(TeamStats::getLeagueId, leagueId)
                            .orderByDesc(TeamStats::getWinRate));
            return buildRankingResult(leagueId, list);
        });
    }

    /** 查询场均击杀最高的战队 */
    public Map<String, Object> queryTopKill(String leagueId, int topN) {
        String key = "kpl:%s:team:top:kill:%d".formatted(leagueId, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<TeamStats> list = teamStatsMapper.selectList(
                    new LambdaQueryWrapper<TeamStats>()
                            .eq(TeamStats::getLeagueId, leagueId)
                            .orderByDesc(TeamStats::getAvgKill)
                            .last("LIMIT " + topN));
            return buildResult("team_top_kill", "场均击杀TOP" + topN, list);
        });
    }

    /**
     * 跨赛事荣誉汇总：统计所有赛事的冠亚季军次数
     * 历史数据来源：KPL 官方公开赛事结果（API 仅保留最近赛季数据，历史数据硬编码）
     */
    public Map<String, Object> queryHonors() {
        String key = "kpl:team:honors";
        return queryCacheService.getOrLoad(key, Duration.ofHours(1), () -> {
            // 统计每支队伍的冠亚季军次数
            Map<String, int[]> honors = new HashMap<>(); // [冠军, 亚军, 季军]
            Map<String, List<String>> championLeagues = new HashMap<>();
            Map<String, List<String>> runnerUpLeagues = new HashMap<>();

            // 使用硬编码历史赛事数据（覆盖 2016-2026 全部 KPL 赛事）
            for (var h : HISTORICAL_HONORS) {
                addHonor(honors, championLeagues, runnerUpLeagues, h[0], Integer.parseInt(h[1]), h[2]);
            }

            // 排序：冠军次数 > 亚军次数 > 季军次数
            List<Map<String, Object>> data = honors.entrySet().stream()
                    .filter(e -> e.getValue()[0] > 0 || e.getValue()[1] > 0)
                    .sorted((a, b) -> {
                        int[] va = a.getValue(), vb = b.getValue();
                        if (va[0] != vb[0]) return Integer.compare(vb[0], va[0]);
                        if (va[1] != vb[1]) return Integer.compare(vb[1], va[1]);
                        return Integer.compare(vb[2], va[2]);
                    })
                    .map(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("teamName", e.getKey());
                        row.put("champion", e.getValue()[0]);
                        row.put("runnerUp", e.getValue()[1]);
                        row.put("total", e.getValue()[0] + e.getValue()[1]);
                        row.put("championLeagues", championLeagues.getOrDefault(e.getKey(), List.of()));
                        row.put("runnerUpLeagues", runnerUpLeagues.getOrDefault(e.getKey(), List.of()));
                        return row;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("type", "team_honors");
            result.put("keyword", "荣誉总榜");
            result.put("count", data.size());
            result.put("data", data);
            return result;
        });
    }

    private void addHonor(Map<String, int[]> honors, Map<String, List<String>> championLeagues,
                          Map<String, List<String>> runnerUpLeagues, String team, int rank, String leagueName) {
        honors.putIfAbsent(team, new int[]{0, 0, 0});
        if (rank >= 1 && rank <= 3) {
            honors.get(team)[rank - 1]++;
        }
        if (rank == 1) {
            championLeagues.computeIfAbsent(team, k -> new ArrayList<>()).add(leagueName);
        } else if (rank == 2) {
            runnerUpLeagues.computeIfAbsent(team, k -> new ArrayList<>()).add(leagueName);
        }
    }

    /**
     * 历史赛事冠亚军数据（截至 2026 年 5 月 30 日，KPL 全部顶级赛事）
     * 格式: {队伍名, 名次(1=冠/2=亚), 赛事名}
     */
    private static final String[][] HISTORICAL_HONORS = {
            // ==================== 2026 ====================
            {"苏州KSG", "1", "2026年KPL春季赛"},
            {"重庆狼队", "2", "2026年KPL春季赛"},
            {"重庆狼队", "1", "2026年王者荣耀挑战者杯"},
            {"成都AG超玩会", "2", "2026年王者荣耀挑战者杯"},
            // ==================== 2025 ====================
            {"成都AG超玩会", "1", "2025年KPL春季赛"},
            {"佛山DRG", "2", "2025年KPL春季赛"},
            {"成都AG超玩会", "1", "2025年KPL夏季赛"},
            {"北京WB", "2", "2025年KPL夏季赛"},
            {"北京WB", "1", "2025年王者荣耀挑战者杯"},
            {"济南RW侠", "2", "2025年王者荣耀挑战者杯"},
            {"成都AG超玩会", "1", "2025年王者荣耀电竞世界杯"},
            {"广州TTG", "2", "2025年王者荣耀电竞世界杯"},
            {"成都AG超玩会", "1", "2025年王者荣耀年度总决赛"},
            {"重庆狼队", "2", "2025年王者荣耀年度总决赛"},
            // ==================== 2024 ====================
            {"重庆狼队", "1", "2024年KPL春季赛"},
            {"成都AG超玩会", "2", "2024年KPL春季赛"},
            {"成都AG超玩会", "1", "2024年KPL夏季赛"},
            {"苏州KSG", "2", "2024年KPL夏季赛"},
            {"成都AG超玩会", "1", "2024年王者荣耀挑战者杯"},
            {"重庆狼队", "2", "2024年王者荣耀挑战者杯"},
            {"成都AG超玩会", "1", "2024年王者荣耀年度总决赛"},
            {"重庆狼队", "2", "2024年王者荣耀年度总决赛"},
            // ==================== 2023 ====================
            {"重庆狼队", "1", "2023年KPL春季赛"},
            {"北京WB", "2", "2023年KPL春季赛"},
            {"重庆狼队", "1", "2023年王者荣耀挑战者杯"},
            {"成都AG超玩会", "2", "2023年王者荣耀挑战者杯"},
            {"广州TTG", "1", "2023年KPL夏季赛"},
            {"重庆狼队", "2", "2023年KPL夏季赛"},
            {"成都AG超玩会", "1", "2023年王者荣耀世界冠军杯"},
            {"武汉eStarPro", "2", "2023年王者荣耀世界冠军杯"},
            // ==================== 2022 ====================
            {"武汉eStarPro", "1", "2022年KPL春季赛"},
            {"重庆狼队", "2", "2022年KPL春季赛"},
            {"武汉eStarPro", "1", "2022年王者荣耀挑战者杯"},
            {"北京WB", "2", "2022年王者荣耀挑战者杯"},
            {"重庆狼队", "1", "2022年KPL夏季赛"},
            {"武汉eStarPro", "2", "2022年KPL夏季赛"},
            {"武汉eStarPro", "1", "2022年王者荣耀世界冠军杯"},
            {"佛山DRG", "2", "2022年王者荣耀世界冠军杯"},
            // ==================== 2021 ====================
            {"南京Hero久竞", "1", "2021年KPL春季赛"},
            {"广州TTG", "2", "2021年KPL春季赛"},
            {"重庆狼队", "1", "2021年王者荣耀世界冠军杯"},
            {"佛山DRG", "2", "2021年王者荣耀世界冠军杯"},
            {"武汉eStarPro", "1", "2021年KPL秋季赛"},
            {"重庆狼队", "2", "2021年KPL秋季赛"},
            {"武汉eStarPro", "1", "2021年王者荣耀挑战者杯"},
            {"广州TTG", "2", "2021年王者荣耀挑战者杯"},
            // ==================== 2020 ====================
            {"北京WB", "1", "2020年KPL春季赛"},
            {"成都AG超玩会", "2", "2020年KPL春季赛"},
            {"北京WB", "1", "2020年王者荣耀世界冠军杯"},
            {"深圳DYG", "2", "2020年王者荣耀世界冠军杯"},
            {"深圳DYG", "1", "2020年KPL秋季赛"},
            {"成都AG超玩会", "2", "2020年KPL秋季赛"},
            {"南京Hero久竞", "1", "2020年王者荣耀冬季冠军杯"},
            {"北京WB", "2", "2020年王者荣耀冬季冠军杯"},
            // ==================== 2019 ====================
            {"武汉eStarPro", "1", "2019年KPL春季赛"},
            {"RNG.M", "2", "2019年KPL春季赛"},
            {"武汉eStarPro", "1", "2019年王者荣耀世界冠军杯"},
            {"济南RW侠", "2", "2019年王者荣耀世界冠军杯"},
            {"成都AG超玩会", "1", "2019年KPL秋季赛"},
            {"重庆狼队", "2", "2019年KPL秋季赛"},
            {"重庆狼队", "1", "2019年王者荣耀冬季冠军杯"},
            {"武汉eStarPro", "2", "2019年王者荣耀冬季冠军杯"},
            // ==================== 2018 ====================
            {"南京Hero久竞", "1", "2018年KPL春季赛"},
            {"上海EDG.M", "2", "2018年KPL春季赛"},
            {"重庆狼队", "1", "2018年王者荣耀冠军杯"},
            {"武汉eStarPro", "2", "2018年王者荣耀冠军杯"},
            {"南京Hero久竞", "1", "2018年KPL秋季赛"},
            {"BA黑凤梨", "2", "2018年KPL秋季赛"},
            {"南京Hero久竞", "1", "2018年王者荣耀冬季冠军杯"},
            {"重庆狼队", "2", "2018年王者荣耀冬季冠军杯"},
            // ==================== 2017 ====================
            {"重庆狼队", "1", "2017年KPL春季赛"},
            {"成都AG超玩会", "2", "2017年KPL春季赛"},
            {"重庆狼队", "1", "2017年王者荣耀冠军杯"},
            {"武汉eStarPro", "2", "2017年王者荣耀冠军杯"},
            {"重庆狼队", "1", "2017年KPL秋季赛"},
            {"广州TTG", "2", "2017年KPL秋季赛"},
            // ==================== 2016 ====================
            {"武汉eStarPro", "1", "2016年王者荣耀冠军杯"},
            {"AS仙阁", "1", "2016年KPL秋季赛"},
            {"成都AG超玩会", "2", "2016年KPL秋季赛"},
    };

    /**
     * 按赛事名次排序：冠军→亚军→季军→其余按 KDA 排序
     * 名次从决赛/季后赛比赛结果中提取
     */
    private Map<String, Object> buildRankingResult(String leagueId, List<TeamStats> teams) {
        // 查该赛事所有已结束比赛
        List<Match> matches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>()
                        .eq(Match::getLeagueId, leagueId)
                        .eq(Match::getStatus, 2));

        // 计算名次（仅冠军=1、亚军=2、季军=3）
        Map<String, Integer> placement = computePlacement(matches);
        Map<String, String> placementDesc = computePlacementDesc(placement);

        // 排序：placement 1/2/3 的队伍在前（按名次升序），其余在后（按 KDA 降序）
        List<TeamStats> sorted = teams.stream()
                .sorted((a, b) -> {
                    int pa = placement.getOrDefault(a.getTeamName(), 999);
                    int pb = placement.getOrDefault(b.getTeamName(), 999);
                    // 有名次的（1/2/3）始终排在无名次的（999）前面
                    if (pa != pb) return Integer.compare(pa, pb);
                    // 同分组内按 KDA 降序
                    return Double.compare(
                            b.getAvgKda() != null ? b.getAvgKda() : 0,
                            a.getAvgKda() != null ? a.getAvgKda() : 0);
                })
                .collect(Collectors.toList());

        // 构建结果，附加名次信息
        List<Map<String, Object>> data = new ArrayList<>();
        for (TeamStats t : sorted) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teamName", t.getTeamName());
            row.put("teamIcon", t.getTeamIcon());
            row.put("battleCount", t.getBattleCount());
            row.put("winRate", t.getWinRate());
            row.put("avgKill", t.getAvgKill());
            row.put("avgDeath", t.getAvgDeath());
            row.put("avgKda", t.getAvgKda());
            row.put("avgGold", t.getAvgGold());
            row.put("avgFirstBlood", t.getAvgFirstBlood());
            row.put("avgPushTower", t.getAvgPushTower());
            row.put("avgDragonControlRate", t.getAvgDragonControlRate());
            row.put("placement", placement.getOrDefault(t.getTeamName(), 0));
            row.put("placementDesc", placementDesc.getOrDefault(t.getTeamName(), ""));
            data.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", "team_ranking");
        result.put("keyword", "赛事排名");
        result.put("count", data.size());
        result.put("data", data);
        return result;
    }

    /**
     * 从比赛数据计算冠亚季军：
     * - 决赛胜者 = 1（冠军）
     * - 决赛败者 = 2（亚军）
     * - 季后赛最后一轮败者（进入半决赛但未进决赛的队伍）中 KDA 最高者 = 3（季军）
     */
    private Map<String, Integer> computePlacement(List<Match> matches) {
        Map<String, Integer> result = new HashMap<>();
        if (matches == null || matches.isEmpty()) return result;

        // 按赛段分组
        Map<String, List<Match>> byStage = matches.stream()
                .collect(Collectors.groupingBy(m -> m.getMatchStage() != null ? m.getMatchStage() : ""));

        // 决赛：stage 为 "js" 或 "zjs"（排除 "jhs"）
        Match finalMatch = byStage.entrySet().stream()
                .filter(e -> e.getKey().contains("js") && !e.getKey().equals("jhs"))
                .flatMap(e -> e.getValue().stream())
                .findFirst().orElse(null);

        String champion = null, runnerUp = null;
        if (finalMatch != null) {
            if (finalMatch.getWinCamp() != null && finalMatch.getWinCamp() == 1) {
                champion = finalMatch.getCamp1TeamName();
                runnerUp = finalMatch.getCamp2TeamName();
            } else if (finalMatch.getWinCamp() != null && finalMatch.getWinCamp() == 2) {
                champion = finalMatch.getCamp2TeamName();
                runnerUp = finalMatch.getCamp1TeamName();
            }
            if (champion != null) result.put(champion, 1);
            if (runnerUp != null) result.put(runnerUp, 2);
        }

        // 季后赛：找与冠军/亚军交手的队伍（即进入后半段季后赛的队伍）
        List<Match> jhsMatches = byStage.getOrDefault("jhs", List.of());
        if (!jhsMatches.isEmpty() && champion != null) {
            // 找决赛双方在季后赛中击败的对手（最近一轮的败者 = 进入半决赛的队伍）
            Set<String> nearFinalists = new HashSet<>();
            // 按时间降序排列，找冠军和亚军各自在季后赛的最后一场胜利
            List<Match> sortedJhs = jhsMatches.stream()
                    .filter(m -> m.getStartTime() != null)
                    .sorted(Comparator.comparing(Match::getStartTime).reversed())
                    .collect(Collectors.toList());

            for (Match m : sortedJhs) {
                String winner = m.getWinCamp() == 1 ? m.getCamp1TeamName() : m.getCamp2TeamName();
                String loser = m.getWinCamp() == 1 ? m.getCamp2TeamName() : m.getCamp1TeamName();
                // 冠军或亚军赢的比赛 → 对面的败者就是半决赛选手
                if ((winner.equals(champion) || winner.equals(runnerUp)) && loser != null) {
                    if (!result.containsKey(loser)) {
                        nearFinalists.add(loser);
                    }
                }
            }

            // 季军：半决赛败者中 KDA 最高的
            if (!nearFinalists.isEmpty()) {
                String third = nearFinalists.stream()
                        .max(Comparator.comparingDouble(t -> findKda(matches, t)))
                        .orElse(null);
                if (third != null) result.put(third, 3);
            }
        }

        return result;
    }

    private Map<String, String> computePlacementDesc(Map<String, Integer> placement) {
        Map<String, String> result = new HashMap<>();
        for (var entry : placement.entrySet()) {
            result.put(entry.getKey(), switch (entry.getValue()) {
                case 1 -> "冠军";
                case 2 -> "亚军";
                case 3 -> "季军";
                default -> "";
            });
        }
        return result;
    }

    /** 从 team_stats 中查找某队的 KDA（用于半决赛败者排序） */
    private double findKda(List<Match> matches, String teamName) {
        // 优先从 team_stats 表取 KDA
        TeamStats stats = teamStatsMapper.selectOne(
                new LambdaQueryWrapper<TeamStats>()
                        .eq(TeamStats::getTeamName, teamName)
                        .last("LIMIT 1"));
        return stats != null && stats.getAvgKda() != null ? stats.getAvgKda() : 0;
    }

    private Map<String, Object> buildResult(String type, String keyword, List<TeamStats> list) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", list.size());
        result.put("data", list);
        return result;
    }
}
