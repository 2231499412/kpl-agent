package com.kpl.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kpl.agent.entity.PlayerStats;
import com.kpl.agent.mapper.BattlePlayerMapper;
import com.kpl.agent.mapper.PlayerStatsMapper;
import com.kpl.agent.service.QueryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 选手数据查询工具：按选手名、位置、战队查询赛季统计数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerStatsTool {

    private final PlayerStatsMapper playerStatsMapper;
    private final BattlePlayerMapper battlePlayerMapper;
    private final QueryCacheService queryCacheService;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /** 按选手名查询 */
    public Map<String, Object> queryByName(String playerName, String leagueId) {
        String key = "kpl:v3:%s:player:name:%s".formatted(leagueId, playerName);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> list = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(leagueId != null && !leagueId.isBlank(), PlayerStats::getLeagueId, leagueId)
                            .like(PlayerStats::getPlayerName, playerName));
            if (!list.isEmpty()) {
                return buildResult("player_by_name", playerName, list);
            }
            List<Map<String, Object>> aggregated = battlePlayerMapper.aggregatePlayerStats(playerName, leagueId);
            String type = "player_by_name_from_battles";
            if (aggregated.isEmpty() && leagueId != null && !leagueId.isBlank()) {
                aggregated = battlePlayerMapper.aggregatePlayerStats(playerName, null);
                type = "player_by_name_from_all_battles";
            }
            return buildResult(type, playerName, aggregated);
        });
    }

    /** 按位置查询（如"中路"、"打野"） */
    public Map<String, Object> queryByPosition(String positionDesc, String leagueId) {
        String key = "kpl:%s:player:position:%s".formatted(leagueId, positionDesc);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> list = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(PlayerStats::getLeagueId, leagueId)
                            .like(PlayerStats::getPositionDesc, positionDesc)
                            .orderByDesc(PlayerStats::getAvgKda));
            return buildResult("player_by_position", positionDesc, list);
        });
    }

    /** 按战队查询所有选手 */
    public Map<String, Object> queryByTeam(String teamName, String leagueId) {
        String key = "kpl:%s:player:team:%s".formatted(leagueId, teamName);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> list = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(PlayerStats::getLeagueId, leagueId)
                            .like(PlayerStats::getTeamName, teamName));
            return buildResult("player_by_team", teamName, list);
        });
    }

    /** 查询 KDA 排行榜（至少出场 minBattles 次） */
    public Map<String, Object> queryTopKda(String leagueId, int minBattles, int topN) {
        String key = "kpl:%s:player:top:kda:%d:%d".formatted(leagueId, minBattles, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> list = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(PlayerStats::getLeagueId, leagueId)
                            .ge(PlayerStats::getBattleCount, minBattles)
                            .orderByDesc(PlayerStats::getAvgKda)
                            .last("LIMIT " + topN));
            return buildResult("player_top_kda", "KDA排行", list);
        });
    }

    /** 查询胜率排行榜（至少出场 minBattles 次） */
    public Map<String, Object> queryTopWinRate(String leagueId, int minBattles, int topN) {
        String key = "kpl:%s:player:top:win:%d:%d".formatted(leagueId, minBattles, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> list = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(PlayerStats::getLeagueId, leagueId)
                            .ge(PlayerStats::getBattleCount, minBattles)
                            .orderByDesc(PlayerStats::getWinRate)
                            .last("LIMIT " + topN));
            return buildResult("player_top_winrate", "胜率排行", list);
        });
    }

    /** 综合状态榜：场次+赛段进程定基底，个人表现和队伍实力微调。 */
    public Map<String, Object> queryTopStatus(String leagueId, int minBattles, int topN) {
        String key = "kpl:v3:%s:player:top:status:%d:%d".formatted(leagueId, minBattles, topN);
        return queryCacheService.getOrLoad(key, CACHE_TTL, () -> {
            List<PlayerStats> players = playerStatsMapper.selectList(
                    new LambdaQueryWrapper<PlayerStats>()
                            .eq(PlayerStats::getLeagueId, leagueId)
                            .ge(PlayerStats::getBattleCount, minBattles));

            List<Map<String, Object>> teams = battlePlayerMapper.leagueTeamResults(leagueId);
            Map<String, Map<String, Object>> teamsByName = new HashMap<>();
            for (Map<String, Object> team : teams) {
                teamsByName.put(text(team.get("teamName")), team);
            }

            Map<String, Object> timespan = battlePlayerMapper.leagueTimespan(leagueId);

            Map<String, Map<String, Object>> recentByPlayer = new HashMap<>();
            for (Map<String, Object> recent : battlePlayerMapper.playerRecent5Summaries(leagueId)) {
                recentByPlayer.put(text(recent.get("playerName")), recent);
            }

            Map<Integer, List<PlayerStats>> peersByPosition = new HashMap<>();
            for (PlayerStats player : players) {
                peersByPosition.computeIfAbsent(player.getPosition(), keyPos -> new ArrayList<>()).add(player);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (PlayerStats player : players) {
                Map<String, Object> team = teamsByName.getOrDefault(player.getTeamName(), Map.of());
                List<PlayerStats> peers = peersByPosition.getOrDefault(player.getPosition(), List.of());
                double baseScore = team.isEmpty() ? 30 : progressBaseScore(team, timespan);
                double personalAdj = personalAdjustment(peers, player) * 8;
                double teamAdj = team.isEmpty() ? 0 : teamAdjustment(teams, team) * 3;
                double statusScore = clamp(baseScore + personalAdj + teamAdj, 0, 100);
                Map<String, Object> recent = recentByPlayer.getOrDefault(shortPlayerName(player.getPlayerName()), Map.of());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("playerName", player.getPlayerName());
                row.put("playerIcon", player.getPlayerIcon());
                row.put("teamName", player.getTeamName());
                row.put("position", player.getPosition());
                row.put("positionDesc", player.getPositionDesc());
                row.put("battleCount", player.getBattleCount());
                row.put("winRate", player.getWinRate());
                row.put("avgKill", player.getAvgKill());
                row.put("avgDeath", player.getAvgDeath());
                row.put("avgAssist", player.getAvgAssist());
                row.put("avgKda", player.getAvgKda());
                row.put("avgGold", player.getAvgGold());
                row.put("avgHurtToHero", player.getAvgHurtToHero());
                row.put("avgBeHurt", player.getAvgBeHurt());
                row.put("avgParticipationRate", player.getAvgParticipationRate());
                row.put("recent5WinRate", recent.getOrDefault("recent5WinRate", 0));
                row.put("recent5BattleWinRate", recent.getOrDefault("recent5BattleWinRate", 0));
                row.put("recent5Matches", recent.getOrDefault("recent5Matches", 0));
                row.put("statusScore", round(statusScore, 1));
                row.put("statusBaseScore", round(baseScore, 1));
                row.put("statusPersonalAdj", round(personalAdj, 1));
                row.put("statusTeamAdj", round(teamAdj, 1));
                row.put("teamResultLabel", team.isEmpty() ? "进程待识别" : progressLabel(team));
                row.put("teamLastStageDesc", team.get("lastStageDesc"));
                row.put("teamMatchWinRate", team.get("matchWinRate"));
                row.put("teamBattleWinRate", team.get("battleWinRate"));
                row.put("teamGameDiff", team.get("gameDiff"));
                rows.add(row);
            }

            rows.sort(Comparator
                    .comparing((Map<String, Object> row) -> number(row.get("statusScore"))).reversed()
                    .thenComparing(row -> number(row.get("statusBaseScore")), Comparator.reverseOrder())
                    .thenComparing(row -> number(row.get("battleCount")), Comparator.reverseOrder())
                    .thenComparing(row -> number(row.get("avgKda")), Comparator.reverseOrder()));

            List<Map<String, Object>> limited = rows.stream().limit(topN).toList();
            return buildResult("player_top_status", "综合状态排行", limited);
        });
    }

    /** Aggregates the data blocks used by the player detail page. */
    public Map<String, Object> queryPlayerDetail(String playerName, String leagueId, int limit) {
        PlayerStats player = findPlayer(playerName, leagueId);
        if (player == null) {
            return Map.of(
                    "type", "player_detail",
                    "keyword", playerName,
                    "error", "player not found"
            );
        }

        int rowLimit = Math.max(1, Math.min(limit, 20));
        String resolvedName = player.getPlayerName();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "player_detail");
        result.put("keyword", resolvedName);
        result.put("player", player);
        result.put("recentGames", battlePlayerMapper.playerRecentMatches(resolvedName, leagueId, 10));
        result.put("recentGamesByBattle", battlePlayerMapper.playerRecentGames(resolvedName, leagueId, 10));
        result.put("seasonTrendSummary", battlePlayerMapper.playerSeasonMatchAverages(resolvedName, leagueId));
        result.put("heroPool", battlePlayerMapper.playerHeroStats(resolvedName, leagueId, rowLimit));
        result.put("stageStats", battlePlayerMapper.playerStageStats(resolvedName, leagueId));
        List<Map<String, Object>> leagueTimeline = battlePlayerMapper.playerLeagueTimeline(resolvedName, 12);
        enrichLeaguePerformanceIndex(
                leagueTimeline,
                battlePlayerMapper.playerLeaguePerformancePool(resolvedName, 12),
                battlePlayerMapper.playerLeagueTeamResults(resolvedName, 12));
        result.put("leagueTimeline", leagueTimeline);
        result.put("leagueHeroMatrix", battlePlayerMapper.playerLeagueHeroMatrix(resolvedName, 1, 120));
        result.put("featuredBattles", battlePlayerMapper.playerFeaturedBattles(resolvedName, leagueId, rowLimit));
        result.put("positionComparison", buildPositionComparison(player, leagueId));
        return result;
    }

    private void enrichLeaguePerformanceIndex(List<Map<String, Object>> timeline,
                                              List<Map<String, Object>> performancePool,
                                              List<Map<String, Object>> teamResults) {
        if (timeline == null || timeline.isEmpty() || performancePool == null || performancePool.isEmpty()) {
            return;
        }

        Map<String, List<Map<String, Object>>> rowsByLeague = new LinkedHashMap<>();
        for (Map<String, Object> row : performancePool) {
            String leagueId = text(row.get("leagueId"));
            if (leagueId.isBlank()) continue;
            rowsByLeague.computeIfAbsent(leagueId, key -> new ArrayList<>()).add(row);
        }

        Map<String, List<Map<String, Object>>> teamsByLeague = new LinkedHashMap<>();
        if (teamResults != null) {
            for (Map<String, Object> row : teamResults) {
                String leagueId = text(row.get("leagueId"));
                if (leagueId.isBlank()) continue;
                teamsByLeague.computeIfAbsent(leagueId, key -> new ArrayList<>()).add(row);
            }
        }

        for (Map<String, Object> league : timeline) {
            String leagueId = text(league.get("leagueId"));
            List<Map<String, Object>> peers = rowsByLeague.get(leagueId);
            if (peers == null || peers.isEmpty()) continue;

            Map<String, Object> target = peers.stream()
                    .filter(row -> number(row.get("targetPlayer")) > 0)
                    .findFirst()
                    .orElse(null);
            if (target == null) continue;

            Map<String, Double> weights = positionWeights(text(target.get("positionDesc")));
            Map<String, Object> personalBreakdown = new LinkedHashMap<>();
            double playerScore = 0;
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                double percentile = percentile(peers, target, entry.getKey());
                personalBreakdown.put(entry.getKey(), round(percentile, 1));
                playerScore += percentile * entry.getValue();
            }

            List<Map<String, Object>> teamPeers = teamsByLeague.getOrDefault(leagueId, List.of());
            Map<String, Object> targetTeam = teamPeers.stream()
                    .filter(row -> number(row.get("targetTeam")) > 0)
                    .findFirst()
                    .orElse(null);

            double leagueMatches = number(league.get("matches"));
            double progressScore = targetTeam == null
                    ? Math.min(95, Math.max(25, (Double.isFinite(leagueMatches) ? leagueMatches : 1) * 6 + 30))
                    : progressBaseScore(targetTeam);
            double teamAdj = targetTeam == null ? 0 : teamAdjustment(teamPeers, targetTeam);
            double teamScore = 50 + teamAdj * 50;
            double finalScore = progressScore * 0.65 + teamScore * 0.20 + playerScore * 0.15;

            Map<String, Object> breakdown = new LinkedHashMap<>();
            breakdown.put("progressScore", round(progressScore, 1));
            breakdown.put("teamScore", round(teamScore, 1));
            breakdown.put("playerScore", round(playerScore, 1));

            league.put("performanceIndex", round(finalScore, 1));
            league.put("performancePositionDesc", target.get("positionDesc"));
            league.put("performancePeerCount", peers.size());
            league.put("performanceBreakdown", breakdown);
            league.put("performancePersonalBreakdown", personalBreakdown);
            league.put("performanceWeights", weights);
            league.put("performanceFormula", "表现指数 = 赛事进程 65% + 队伍表现 20% + 同分路个人表现 15%");
            if (targetTeam != null) {
                league.put("teamName", targetTeam.get("teamName"));
                league.put("teamMatches", targetTeam.get("matches"));
                league.put("teamMatchWins", targetTeam.get("matchWins"));
                league.put("teamMatchWinRate", targetTeam.get("matchWinRate"));
                league.put("teamGames", targetTeam.get("games"));
                league.put("teamBattleWins", targetTeam.get("battleWins"));
                league.put("teamBattleWinRate", targetTeam.get("battleWinRate"));
                league.put("teamGameDiff", targetTeam.get("gameDiff"));
                league.put("teamGameDiffPerMatch", targetTeam.get("gameDiffPerMatch"));
                league.put("teamLastStageDesc", targetTeam.get("lastStageDesc"));
                league.put("teamLastWon", targetTeam.get("lastWon"));
                league.put("teamResultLabel", progressLabel(targetTeam));
            }
        }
    }

    /** 队伍实力微调：百分位排名映射到 [-1, 1]。 */
    private double teamAdjustment(List<Map<String, Object>> teams, Map<String, Object> targetTeam) {
        if (teams == null || teams.size() <= 1 || targetTeam == null) return 0;
        double percentile = percentile(teams, targetTeam, "matchWinRate") * 0.4
                + percentile(teams, targetTeam, "battleWinRate") * 0.35
                + percentile(teams, targetTeam, "gameDiffPerMatch") * 0.25;
        return (percentile - 50) / 50;
    }

    /** 个人表现微调：同位置百分位排名映射到 [-1, 1]，样本量少时衰减。 */
    private double personalAdjustment(List<PlayerStats> peers, PlayerStats target) {
        if (peers == null || peers.size() <= 1 || target == null) return 0;
        double percentile = playerPercentile(peers, target, PlayerStats::getAvgKda) * 0.28
                + playerPercentile(peers, target, p -> normalizePercent(p.getWinRate())) * 0.22
                + playerPercentile(peers, target, PlayerStats::getAvgGold) * 0.14
                + playerPercentile(peers, target, p -> normalizePercent(p.getAvgParticipationRate())) * 0.14
                + playerPercentile(peers, target, PlayerStats::getAvgHurtToHero) * 0.14
                + playerPercentile(peers, target, p -> p.getBattleCount() == null ? null : p.getBattleCount().doubleValue()) * 0.08;
        double normalized = (percentile - 50) / 50;
        int battles = target.getBattleCount() == null ? 0 : target.getBattleCount();
        double dampen = Math.min(1, battles / 12.0);
        return normalized * dampen;
    }

    private double playerPercentile(List<PlayerStats> peers, PlayerStats target, java.util.function.Function<PlayerStats, Double> getter) {
        Double targetRaw = getter.apply(target);
        if (targetRaw == null || !Double.isFinite(targetRaw)) return 50;
        List<Double> values = peers.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .toList();
        if (values.size() <= 1) return 50;
        long lower = values.stream().filter(value -> value < targetRaw).count();
        long equal = values.stream().filter(value -> Double.compare(value, targetRaw) == 0).count();
        return Math.max(0, Math.min(100, (lower + Math.max(0, equal - 1) * 0.5) * 100.0 / (values.size() - 1)));
    }

    private double normalizePercent(Double value) {
        if (value == null || !Double.isFinite(value)) return Double.NaN;
        return Math.abs(value) <= 1 ? value * 100 : value;
    }

    private double progressBaseScore(Map<String, Object> team, Map<String, Object> timespan) {
        String stage = text(team.get("lastStageDesc"));
        boolean won = number(team.get("lastWon")) > 0;
        double matches = number(team.get("matches"));
        double battleWinRate = number(team.get("battleWinRate"));

        double lastTime = timeMillis(timespan.get("lastTime"));
        double firstTime = timeMillis(timespan.get("firstTime"));
        double teamLastTime = timeMillis(team.get("lastMatchTime"));
        double span = lastTime - firstTime;
        double timeProgress = (span > 0 && teamLastTime > 0)
                ? clamp((teamLastTime - firstTime) / span, 0, 1) : 0.5;
        double matchBonus = Math.min(1, matches / 18);
        double marginBonus = clamp((battleWinRate - 50) * 0.06, -3, 3);
        double totalMatches = number(timespan.get("totalMatches"));
        double matchRatio = totalMatches > 0 ? clamp(matches / totalMatches, 0, 1) : 0.5;

        return computeStageScore(stage, won, matchBonus, marginBonus, timeProgress, matchRatio);
    }

    /** 无时间跨度的版本（用于历史赛事时间线），默认 timeProgress=0.5。 */
    private double progressBaseScore(Map<String, Object> team) {
        return progressBaseScore(team, Map.of());
    }

    private double computeStageScore(String stage, boolean won, double matchBonus, double marginBonus, double timeProgress, double matchRatio) {

        if (isFinalStage(stage)) {
            double base = won ? 88 : 80;
            return clamp(base + matchBonus * 8 + marginBonus, won ? 90 : 80, won ? 98 : 88);
        }
        if (containsAny(stage, "半决赛", "半决", "四强")) {
            return clamp(68 + matchBonus * 8 + marginBonus, 68, 84);
        }
        if (containsAny(stage, "四分之一", "1/4", "八强", "8强")) {
            return clamp(55 + matchBonus * 8 + marginBonus, 55, 72);
        }
        if (containsAny(stage, "季后赛", "淘汰赛", "败者组", "胜者组")) {
            return clamp(44 + matchBonus * 10 + marginBonus, 44, 62);
        }
        if (containsAny(stage, "常规赛", "循环赛")) {
            double progressBonus = timeProgress * 22 + matchRatio * 12;
            return clamp(32 + matchBonus * 8 + progressBonus, 32, 74);
        }
        if (containsAny(stage, "小组赛")) {
            double progressBonus = timeProgress * 16 + matchRatio * 10;
            return clamp(25 + matchBonus * 8 + progressBonus, 25, 58);
        }
        if (containsAny(stage, "选拔", "资格", "预选")) {
            return clamp(20 + matchBonus * 8 + timeProgress * 10, 20, 42);
        }
        return clamp(20 + matchBonus * 12 + timeProgress * 22 + matchRatio * 10, 20, 62);
    }

    private String progressLabel(Map<String, Object> team) {
        String stage = text(team.get("lastStageDesc"));
        boolean won = number(team.get("lastWon")) > 0;
        if (isFinalStage(stage)) return won ? "冠军" : "亚军";
        if (containsAny(stage, "半决赛", "半决", "四强")) return "四强";
        if (containsAny(stage, "四分之一", "1/4", "八强", "8强")) return "八强";
        if (containsAny(stage, "季后赛", "淘汰赛", "败者组", "胜者组")) return won ? "淘汰赛晋级" : "淘汰赛出局";
        if (containsAny(stage, "常规赛", "循环赛")) return "常规赛";
        if (containsAny(stage, "小组赛")) return "小组赛";
        if (containsAny(stage, "选拔", "资格", "预选")) return "选拔赛";
        return stage.isBlank() ? "赛事进程待识别" : stage;
    }

    private boolean isFinalStage(String stage) {
        return containsAny(stage, "总决赛", "决赛")
                && !containsAny(stage, "半决赛", "半决", "四分之一", "1/4");
    }

    private boolean containsAny(String value, String... patterns) {
        if (value == null || value.isBlank()) return false;
        for (String pattern : patterns) {
            if (value.contains(pattern)) return true;
        }
        return false;
    }

    private Map<String, Double> positionWeights(String positionDesc) {
        Map<String, Double> weights = new LinkedHashMap<>();
        String position = positionDesc == null ? "" : positionDesc;
        if (position.contains("对抗")) {
            weights.put("winRate", 0.12);
            weights.put("avgKda", 0.13);
            weights.put("avgGoldPerMinute", 0.12);
            weights.put("avgParticipationRate", 0.13);
            weights.put("avgHurtToHeroPerMinute", 0.20);
            weights.put("avgBeHurtPerMinute", 0.25);
            weights.put("games", 0.05);
        } else if (position.contains("打野")) {
            weights.put("winRate", 0.12);
            weights.put("avgKda", 0.16);
            weights.put("avgGoldPerMinute", 0.18);
            weights.put("avgParticipationRate", 0.22);
            weights.put("avgHurtToHeroPerMinute", 0.20);
            weights.put("avgBeHurtPerMinute", 0.07);
            weights.put("games", 0.05);
        } else if (position.contains("中路")) {
            weights.put("winRate", 0.12);
            weights.put("avgKda", 0.16);
            weights.put("avgGoldPerMinute", 0.14);
            weights.put("avgParticipationRate", 0.16);
            weights.put("avgHurtToHeroPerMinute", 0.32);
            weights.put("avgBeHurtPerMinute", 0.05);
            weights.put("games", 0.05);
        } else if (position.contains("发育")) {
            weights.put("winRate", 0.12);
            weights.put("avgKda", 0.18);
            weights.put("avgGoldPerMinute", 0.22);
            weights.put("avgParticipationRate", 0.12);
            weights.put("avgHurtToHeroPerMinute", 0.30);
            weights.put("avgBeHurtPerMinute", 0.01);
            weights.put("games", 0.05);
        } else if (position.contains("游走")) {
            weights.put("winRate", 0.14);
            weights.put("avgKda", 0.12);
            weights.put("avgGoldPerMinute", 0.05);
            weights.put("avgParticipationRate", 0.27);
            weights.put("avgHurtToHeroPerMinute", 0.29);
            weights.put("avgBeHurtPerMinute", 0.08);
            weights.put("games", 0.05);
        } else {
            weights.put("winRate", 0.15);
            weights.put("avgKda", 0.18);
            weights.put("avgGoldPerMinute", 0.15);
            weights.put("avgParticipationRate", 0.18);
            weights.put("avgHurtToHeroPerMinute", 0.18);
            weights.put("avgBeHurtPerMinute", 0.11);
            weights.put("games", 0.05);
        }
        return weights;
    }

    private double percentile(List<Map<String, Object>> peers, Map<String, Object> target, String metric) {
        double targetValue = number(target.get(metric));
        if (!Double.isFinite(targetValue)) return 50;

        List<Double> values = peers.stream()
                .map(row -> number(row.get(metric)))
                .filter(Double::isFinite)
                .toList();
        if (values.size() <= 1) return 50;

        long lower = values.stream().filter(value -> value < targetValue).count();
        long equal = values.stream().filter(value -> Double.compare(value, targetValue) == 0).count();
        return Math.max(0, Math.min(100, (lower + Math.max(0, equal - 1) * 0.5) * 100.0 / (values.size() - 1)));
    }

    private PlayerStats findPlayer(String playerName, String leagueId) {
        if (playerName == null || playerName.isBlank()) return null;
        List<PlayerStats> list = playerStatsMapper.selectList(
                new LambdaQueryWrapper<PlayerStats>()
                        .eq(leagueId != null && !leagueId.isBlank(), PlayerStats::getLeagueId, leagueId)
                        .and(w -> w.like(PlayerStats::getPlayerName, playerName)
                                .or()
                                .eq(PlayerStats::getPlayerName, playerName))
                        .orderByDesc(PlayerStats::getBattleCount)
                        .last("LIMIT 1"));
        return list.stream().findFirst().orElse(null);
    }

    private Map<String, Object> buildPositionComparison(PlayerStats player, String leagueId) {
        if (player.getPosition() == null) {
            return Map.of();
        }
        List<PlayerStats> peers = playerStatsMapper.selectList(
                new LambdaQueryWrapper<PlayerStats>()
                        .eq(PlayerStats::getLeagueId, leagueId)
                        .eq(PlayerStats::getPosition, player.getPosition())
                        .ge(PlayerStats::getBattleCount, 5));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("peerCount", peers.size());
        result.put("avg", Map.of(
                "winRate", avg(peers, PlayerStats::getWinRate),
                "avgKda", avg(peers, PlayerStats::getAvgKda),
                "avgKill", avg(peers, PlayerStats::getAvgKill),
                "avgDeath", avg(peers, PlayerStats::getAvgDeath),
                "avgAssist", avg(peers, PlayerStats::getAvgAssist),
                "avgGold", avg(peers, PlayerStats::getAvgGold),
                "avgParticipationRate", avg(peers, PlayerStats::getAvgParticipationRate)
        ));
        result.put("rank", Map.of(
                "winRate", rank(peers, player.getPlayerName(), Comparator.comparing(PlayerStats::getWinRate, Comparator.nullsLast(Double::compareTo)).reversed()),
                "avgKda", rank(peers, player.getPlayerName(), Comparator.comparing(PlayerStats::getAvgKda, Comparator.nullsLast(Double::compareTo)).reversed()),
                "avgKill", rank(peers, player.getPlayerName(), Comparator.comparing(PlayerStats::getAvgKill, Comparator.nullsLast(Double::compareTo)).reversed()),
                "avgGold", rank(peers, player.getPlayerName(), Comparator.comparing(PlayerStats::getAvgGold, Comparator.nullsLast(Double::compareTo)).reversed()),
                "avgParticipationRate", rank(peers, player.getPlayerName(), Comparator.comparing(PlayerStats::getAvgParticipationRate, Comparator.nullsLast(Double::compareTo)).reversed())
        ));
        return result;
    }

    private double avg(List<PlayerStats> peers, java.util.function.Function<PlayerStats, Double> getter) {
        return peers.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    private int rank(List<PlayerStats> peers, String playerName, Comparator<PlayerStats> comparator) {
        List<PlayerStats> sorted = peers.stream().sorted(comparator).toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (Objects.equals(sorted.get(i).getPlayerName(), playerName)) {
                return i + 1;
            }
        }
        return 0;
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return Double.NaN;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double timeMillis(Object value) {
        if (value == null) return 0;
        if (value instanceof java.time.temporal.TemporalAccessor ta) {
            return java.time.LocalDateTime.from(ta)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();
        }
        if (value instanceof java.util.Date d) return d.getTime();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private String shortPlayerName(String value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        int index = text.lastIndexOf('.');
        return index >= 0 ? text.substring(index + 1) : text;
    }

    private double round(double value, int digits) {
        if (!Double.isFinite(value)) return 0;
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Map<String, Object> buildResult(String type, String keyword, List<?> list) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", list.size());
        result.put("data", list);
        return result;
    }
}
