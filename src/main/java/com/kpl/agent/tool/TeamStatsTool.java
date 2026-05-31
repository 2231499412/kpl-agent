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

    /** 赛段优先级：决赛 > 季后赛 > 淘汰赛 > 常规赛 */
    private static final Map<String, Integer> STAGE_ORDER = Map.ofEntries(
            Map.entry("js", 1), Map.entry("zjs", 1),
            Map.entry("jhs", 2),
            Map.entry("sbttss", 3), Map.entry("dbtts", 3),
            Map.entry("cgs3", 4), Map.entry("cgs2", 5), Map.entry("cgs1", 6)
    );

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

        // 构建 KDA 映射
        Map<String, Double> teamKdaMap = new HashMap<>();
        for (TeamStats t : teams) {
            teamKdaMap.put(t.getTeamName(), t.getAvgKda() != null ? t.getAvgKda() : 0);
        }

        // 从比赛结果推算名次顺序（严格 1,2,3,4...）
        List<String> placementOrder = computePlacementOrder(matches, teamKdaMap);

        // 构建名次映射
        Map<String, Integer> placement = new HashMap<>();
        for (int i = 0; i < placementOrder.size(); i++) {
            placement.put(placementOrder.get(i), i + 1);
        }

        // 排序：有名次的按名次升序，无名次的在最后按胜率+KDA降序
        List<TeamStats> sorted = teams.stream()
                .sorted((a, b) -> {
                    int pa = placement.getOrDefault(a.getTeamName(), 999);
                    int pb = placement.getOrDefault(b.getTeamName(), 999);
                    if (pa != pb) return Integer.compare(pa, pb);
                    int cmp = Double.compare(
                            b.getWinRate() != null ? b.getWinRate() : 0,
                            a.getWinRate() != null ? a.getWinRate() : 0);
                    if (cmp != 0) return cmp;
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
            int p = placement.getOrDefault(t.getTeamName(), 0);
            row.put("placement", p);
            row.put("placementDesc", p > 0 ? "第" + p + "名" : "");
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
     * 从淘汰赛结果推算名次（严格 1,2,3,4... 不并列）：
     * 1) 决赛胜者=1，败者=2
     * 2) 统计每队赢了几轮，赢轮数越多名次越高
     * 3) 同轮败者按被淘汰时间排序（越晚越好），再按 KDA 排序
     * 4) 返回按名次排好的队伍名列表，供 buildRankingResult 使用
     */
    private List<String> computePlacementOrder(List<Match> matches, Map<String, Double> teamKdaMap) {
        List<String> order = new ArrayList<>();
        if (matches == null || matches.isEmpty()) return order;

        // 决赛
        Match finalMatch = matches.stream()
                .filter(m -> {
                    String s = m.getMatchStage();
                    return s != null && s.contains("js") && !s.equals("jhs");
                })
                .findFirst().orElse(null);
        if (finalMatch == null || finalMatch.getWinCamp() == null) return order;

        String champion = finalMatch.getWinCamp() == 1
                ? finalMatch.getCamp1TeamName() : finalMatch.getCamp2TeamName();
        String runnerUp = finalMatch.getWinCamp() == 1
                ? finalMatch.getCamp2TeamName() : finalMatch.getCamp1TeamName();
        order.add(champion);
        order.add(runnerUp);

        // 统计每队赢了几轮
        Map<String, Integer> winCount = new HashMap<>();
        // 记录每队最后一场淘汰赛的时间（越晚 = 走得越远）
        Map<String, String> lastLossTime = new HashMap<>();

        for (Match m : matches) {
            String stage = m.getMatchStage();
            if (stage == null) continue;
            if (stage.contains("js") && !stage.equals("jhs")) continue;
            if (m.getWinCamp() == null) continue;

            String winner = m.getWinCamp() == 1 ? m.getCamp1TeamName() : m.getCamp2TeamName();
            String loser = m.getWinCamp() == 1 ? m.getCamp2TeamName() : m.getCamp1TeamName();
            if (winner != null) winCount.merge(winner, 1, Integer::sum);
            if (loser != null && m.getStartTime() != null) {
                String time = m.getStartTime().toString();
                // 只保留最晚的时间（该队最后被淘汰的那场）
                if (!lastLossTime.containsKey(loser) || time.compareTo(lastLossTime.get(loser)) > 0) {
                    lastLossTime.put(loser, time);
                }
            }
        }

        int runnerUpWins = winCount.getOrDefault(runnerUp, 0);
        int maxWins = runnerUpWins + 1;

        // 按赢轮数分组
        Map<Integer, List<String>> byWins = new TreeMap<>(Comparator.reverseOrder()); // 轮数多的在前
        for (var entry : winCount.entrySet()) {
            String team = entry.getKey();
            if (order.contains(team)) continue;
            byWins.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(team);
        }

        // 每组内按最后淘汰时间降序 + KDA 降序
        for (var group : byWins.values()) {
            group.sort((a, b) -> {
                // 最后被淘汰的时间越晚 = 走得越远 = 名次越高
                String ta = lastLossTime.getOrDefault(a, "");
                String tb = lastLossTime.getOrDefault(b, "");
                int cmp = tb.compareTo(ta);
                if (cmp != 0) return cmp;
                // 同时间按 KDA 降序
                double ka = teamKdaMap.getOrDefault(a, 0.0);
                double kb = teamKdaMap.getOrDefault(b, 0.0);
                return Double.compare(kb, ka);
            });
            order.addAll(group);
        }

        return order;
    }

    private Map<String, String> computePlacementDesc(Map<String, Integer> placement) {
        Map<String, String> result = new HashMap<>();
        for (var entry : placement.entrySet()) {
            result.put(entry.getKey(), "第" + entry.getValue() + "名");
        }
        return result;
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
