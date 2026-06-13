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
        result.put("heroPool", battlePlayerMapper.playerHeroStats(resolvedName, leagueId, rowLimit));
        result.put("stageStats", battlePlayerMapper.playerStageStats(resolvedName, leagueId));
        List<Map<String, Object>> leagueTimeline = battlePlayerMapper.playerLeagueTimeline(resolvedName, 12);
        enrichLeaguePerformanceIndex(leagueTimeline, battlePlayerMapper.playerLeaguePerformancePool(resolvedName, 12));
        result.put("leagueTimeline", leagueTimeline);
        result.put("leagueHeroMatrix", battlePlayerMapper.playerLeagueHeroMatrix(resolvedName, 1, 120));
        result.put("featuredBattles", battlePlayerMapper.playerFeaturedBattles(resolvedName, leagueId, rowLimit));
        result.put("positionComparison", buildPositionComparison(player, leagueId));
        return result;
    }

    private void enrichLeaguePerformanceIndex(List<Map<String, Object>> timeline, List<Map<String, Object>> performancePool) {
        if (timeline == null || timeline.isEmpty() || performancePool == null || performancePool.isEmpty()) {
            return;
        }

        Map<String, List<Map<String, Object>>> rowsByLeague = new LinkedHashMap<>();
        for (Map<String, Object> row : performancePool) {
            String leagueId = text(row.get("leagueId"));
            if (leagueId.isBlank()) continue;
            rowsByLeague.computeIfAbsent(leagueId, key -> new ArrayList<>()).add(row);
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
            Map<String, Object> breakdown = new LinkedHashMap<>();
            double score = 0;
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                double percentile = percentile(peers, target, entry.getKey());
                breakdown.put(entry.getKey(), round(percentile, 1));
                score += percentile * entry.getValue();
            }

            league.put("performanceIndex", round(score, 1));
            league.put("performancePositionDesc", target.get("positionDesc"));
            league.put("performancePeerCount", peers.size());
            league.put("performanceBreakdown", breakdown);
            league.put("performanceWeights", weights);
        }
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

    private double round(double value, int digits) {
        if (!Double.isFinite(value)) return 0;
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
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
