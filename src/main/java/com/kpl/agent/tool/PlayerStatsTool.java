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
        result.put("recentGames", battlePlayerMapper.playerRecentGames(resolvedName, leagueId, 10));
        result.put("heroPool", battlePlayerMapper.playerHeroStats(resolvedName, leagueId, rowLimit));
        result.put("stageStats", battlePlayerMapper.playerStageStats(resolvedName, leagueId));
        result.put("featuredBattles", battlePlayerMapper.playerFeaturedBattles(resolvedName, leagueId, rowLimit));
        result.put("positionComparison", buildPositionComparison(player, leagueId));
        return result;
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

    private Map<String, Object> buildResult(String type, String keyword, List<?> list) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("keyword", keyword);
        result.put("count", list.size());
        result.put("data", list);
        return result;
    }
}
